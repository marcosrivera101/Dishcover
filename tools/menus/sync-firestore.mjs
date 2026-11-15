// Writes the menu check's results into Firestore and posts menu changes to the feed.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node sync-firestore.mjs hartford-menus.json [menu-changes.json]
//
// Places and dishes are only added or refreshed, never deleted, so ratings and reviews stay put.
// Without a changes file (the first run) every place with a menu gets one "New on menus" post.

import admin from "firebase-admin";
import fs from "node:fs";

const [seedPath, changesPath] = process.argv.slice(2);
const seed = JSON.parse(fs.readFileSync(seedPath));
const changes = changesPath && fs.existsSync(changesPath) ? JSON.parse(fs.readFileSync(changesPath)) : null;
const BOT = { id: "dishcover-menu-check", name: "Dishcover menu check" };
const MAX_POSTS = Number(process.env.MAX_POSTS ?? 80);

const B32 = "0123456789bcdefghjkmnpqrstuvwxyz";
function geohash(lat, lng, precision = 4) {
  let latR = [-90, 90], lngR = [-180, 180], hash = "", bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    const r = even ? lngR : latR, v = even ? lng : lat, mid = (r[0] + r[1]) / 2;
    if (v >= mid) { ch = (ch << 1) | 1; r[0] = mid; } else { ch <<= 1; r[1] = mid; }
    even = !even;
    if (++bit === 5) { hash += B32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}

admin.initializeApp();
const db = admin.firestore();

async function commitAll(ops) {
  for (let i = 0; i < ops.length; i += 400) {
    const batch = db.batch();
    ops.slice(i, i + 400).forEach((op) => op(batch));
    await batch.commit();
  }
}

async function main() {
  const now = Date.now();
  const placeById = new Map(seed.places.map((p) => [p.id, p]));
  const existingPlaces = new Set((await db.collection("places").select().get()).docs.map((d) => d.id));
  const existingDishes = new Set((await db.collection("dishes").where("createdBy", "==", BOT.id).select().get()).docs.map((d) => d.id));
  const ops = [];

  for (const p of seed.places) {
    const gh4 = geohash(p.lat, p.lng);
    const fields = { name: p.name.slice(0, 60), address: p.address ?? "", lat: p.lat, lng: p.lng, gh4, cuisine: p.cuisine ?? "", website: p.website ?? "" };
    const ref = db.collection("places").doc(p.id);
    if (existingPlaces.has(p.id)) ops.push((b) => b.set(ref, fields, { merge: true }));
    else ops.push((b) => b.set(ref, { ...fields, createdBy: BOT.id, createdByName: "Dishcover", createdAt: now, ratingSum: 0, ratingCount: 0, coordsCachedAt: 0 }));
  }
  let added = 0;
  for (const d of seed.dishes) {
    if (existingDishes.has(d.id)) {
      // Keep ratings; refresh only what the menu reader decides.
      ops.push((b) => b.set(db.collection("dishes").doc(d.id), { categories: d.categories, tags: d.tags ?? [], allergens: d.allergens ?? [] }, { merge: true }));
      continue;
    }
    const p = placeById.get(d.placeId);
    if (!p) continue;
    added++;
    ops.push((b) =>
      b.set(db.collection("dishes").doc(d.id), {
        placeId: d.placeId, placeName: p.name, gh4: geohash(p.lat, p.lng), name: d.name.slice(0, 80), description: d.description ?? "",
        categories: d.categories, price: (d.price ?? "").slice(0, 20), allergens: d.allergens ?? [], tags: d.tags ?? [],
        createdBy: BOT.id, createdByName: "Dishcover", createdAt: now, confirmedAt: now, lastRatedAt: 0, ratingSum: 0, ratingCount: 0,
      }),
    );
  }

  // Feed posts: one per place whose menu changed.
  const dishName = new Map(seed.dishes.map((d) => [d.id, d.name]));
  const firstRun = existingDishes.size === 0;
  const posts = ((firstRun ? null : changes) ?? seed.places.filter((p) => p.dishCount > 0).map((p) => ({
    placeId: p.id, placeName: p.name, newPlace: true, added: seed.dishes.filter((d) => d.placeId === p.id).map((d) => d.id),
  }))).slice(0, MAX_POSTS);
  posts.forEach((c, i) => {
    const p = placeById.get(c.placeId);
    if (!p) return;
    const gh4 = geohash(p.lat, p.lng);
    const ref = db.collection("feed").doc();
    const base = {
      gh3: gh4.slice(0, 3), gh4, lat: p.lat, lng: p.lng, createdAt: now - i * 1000, userId: BOT.id, userName: BOT.name,
      placeId: p.id, placeName: p.name.slice(0, 80), text: "", likeCount: 0, photoUrl: "", stars: 0, dishName: "",
    };
    if (c.added.length > 0) {
      ops.push((b) => b.set(ref, { ...base, type: "menu", dishId: c.added[0], dishNames: c.added.slice(0, 4).map((id) => dishName.get(id) ?? ""), dishCount: c.added.length }));
    } else if (c.newPlace) {
      ops.push((b) => b.set(ref, { ...base, type: "place", dishId: "", dishNames: [], dishCount: 0 }));
    }
  });

  await commitAll(ops);
  console.log(`Firestore: ${seed.places.length} places refreshed, ${added} new dishes, ${posts.length} feed posts`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
