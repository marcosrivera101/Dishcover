// Counts the most common dish names across Dishcover, to pick which dishes get a shared photo.
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node ../photos/top-dishes.mjs > top-dishes.txt
import admin from "firebase-admin";
admin.initializeApp();
const db = admin.firestore();
const norm = (s) => s.toLowerCase().replace(/\(.*?\)/g, " ").replace(/\b(small|medium|large|lg|sm|reg|regular|half|whole|full|order|of|\d+\s*(pc|pcs|piece|pieces|oz|in|inch)|\d+)\b/g, " ")
  .replace(/[^a-z&' ]+/g, " ").replace(/\s+/g, " ").trim();
const counts = new Map();
const snap = await db.collection("dishes").select("name", "categories").get();
for (const d of snap.docs) {
  const n = norm(d.get("name") ?? "");
  if (n.length < 3) continue;
  const c = counts.get(n) ?? { n: 0, cat: (d.get("categories") ?? [])[0] ?? "" };
  c.n++; counts.set(n, c);
}
console.log(`# ${snap.size} dishes, ${counts.size} distinct names`);
[...counts].sort((a, b) => b[1].n - a[1].n).slice(0, 600).forEach(([n, c]) => console.log(`${c.n}\t${n}\t${c.cat}`));
