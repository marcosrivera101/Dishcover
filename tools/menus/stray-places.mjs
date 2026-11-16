// Removes menu-check places that landed outside the US by mistake (a same-named town abroad),
// with their dishes, as long as nobody has rated them.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node stray-places.mjs
import admin from "firebase-admin";

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
