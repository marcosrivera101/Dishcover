// Removes menu-check places that landed outside the US by mistake (a same-named town abroad),
// with their dishes, as long as nobody has rated them.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node stray-places.mjs
import admin from "firebase-admin";
import fs from "node:fs";

admin.initializeApp();
const db = admin.firestore();
const places = await db.collection("places").where("createdBy", "==", "dishcover-menu-check").get();
let removed = 0;
for (const p of places.docs) {
  const lng = p.get("lng"), lat = p.get("lat");
  if (!(lng > -30 || lat < 15) || (p.get("ratingCount") ?? 0) > 0) continue;
  const ds = await db.collection("dishes").where("placeId", "==", p.id).get();
  if (ds.docs.some((d) => (d.get("ratingCount") ?? 0) > 0)) continue;
  const feed = await db.collection("feed").where("placeId", "==", p.id).get();
  const refs = [...ds.docs, ...feed.docs].map((d) => d.ref).concat(p.ref);
  for (let i = 0; i < refs.length; i += 400) {
    const b = db.batch();
    refs.slice(i, i + 400).forEach((r) => b.delete(r));
    await b.commit();
  }
  console.log(`removed ${p.get("name")} (${lat}, ${lng}) with ${ds.size} dishes`);
  removed++;
}
console.log(`${removed} stray places removed`);

// Earlier reruns of a town file posted the same places again; keep each place's newest bot post.
let dupes = 0;
for (const f of process.argv.slice(2)) {
  for (const p of JSON.parse(fs.readFileSync(f)).places) {
    const posts = (await db.collection("feed").where("placeId", "==", p.id).get()).docs
      .filter((d) => d.get("userId") === "dishcover-menu-check").sort((a, b) => b.get("createdAt") - a.get("createdAt"));
    for (const d of posts.slice(1)) { await d.ref.delete(); dupes++; }
  }
}
if (dupes) console.log(`${dupes} repeated feed posts removed`);
