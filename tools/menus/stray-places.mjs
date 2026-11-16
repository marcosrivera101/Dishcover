// Removes menu-check places that landed outside the US by mistake (a same-named town abroad),
// with their dishes, as long as nobody has rated them.
//
// Given town files, also removes same-named copies of their places that sit far from the town
// (an earlier match to Manchester, Vermont and the like).
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node stray-places.mjs [hand-<town>.json ...]
import admin from "firebase-admin";
import fs from "node:fs";

admin.initializeApp();
const db = admin.firestore();
const places = await db.collection("places").where("createdBy", "==", "dishcover-menu-check").get();
const km = (a, b, c, d) => Math.hypot((a - c) * 111, (b - d) * 111 * Math.cos((a * Math.PI) / 180));
const near = new Map(); // name -> [lat, lng] of the hand-gathered copies
for (const f of process.argv.slice(2)) for (const p of JSON.parse(fs.readFileSync(f)).places) (near.get(p.name) ?? near.set(p.name, []).get(p.name)).push([p.lat, p.lng]);
let removed = 0;
for (const p of places.docs) {
  const lng = p.get("lng"), lat = p.get("lat");
  const abroad = lng > -30 || lat < 15;
  const astray = near.has(p.get("name")) && near.get(p.get("name")).every(([a, b]) => km(a, b, lat, lng) > 40);
  if (!(abroad || astray) || (p.get("ratingCount") ?? 0) > 0) continue;
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
