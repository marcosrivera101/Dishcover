// Recomputes the automatic diet tags (healthy, vegetarian, spicy...) on every Dishcover-added dish,
// so a fix to the rules in taxonomy.mjs reaches dishes loaded before it.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node retag.mjs

import admin from "firebase-admin";
import { tagsFor } from "./taxonomy.mjs";

admin.initializeApp();
const db = admin.firestore();
const cuisine = new Map((await db.collection("places").select("cuisine").get()).docs.map((d) => [d.id, d.get("cuisine") ?? ""]));
const dishes = await db.collection("dishes").where("createdBy", "==", "dishcover-menu-check").select("name", "description", "placeId", "tags").get();
const changed = [];
for (const d of dishes.docs) {
  const tags = tagsFor(d.get("name") ?? "", d.get("description") ?? "", cuisine.get(d.get("placeId")) ?? "");
  const old = d.get("tags") ?? [];
  if (tags.length !== old.length || tags.some((t) => !old.includes(t))) changed.push([d.ref, tags]);
}
for (let i = 0; i < changed.length; i += 400) {
  const b = db.batch();
  changed.slice(i, i + 400).forEach(([ref, tags]) => b.update(ref, { tags }));
  await b.commit();
}
console.log(`${dishes.size} dishes checked, ${changed.length} retagged`);
