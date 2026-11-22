// Gives every chain restaurant already in Firestore the chain's full, sectioned menu from chains.json.
// Only places with a sparse menu are touched, so a hand-gathered chain menu (with prices) is left alone.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node chain-menus.mjs chain-seed.json
//   then: MAX_POSTS=0 node sync-firestore.mjs chain-seed.json

import admin from "firebase-admin";
import fs from "node:fs";
import { categorize, tagsFor, allergensFor } from "./taxonomy.mjs";

const out = process.argv[2] ?? "chain-seed.json";
const CHAINS = JSON.parse(fs.readFileSync(new URL("./chains.json", import.meta.url)));
const SPARSE = 30;
const slug = (s) => s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60);
const norm = (s) => s.toLowerCase().replace(/['’`]/g, "").replace(/\b(the|restaurant|cafe|café|bar|grill|and|&|llc|inc|hartford|ct)\b/g, "").replace(/[^a-z0-9]+/g, "");
const chainFor = (name) => Object.keys(CHAINS).find((k) => !k.startsWith("_") && norm(name).length >= 3 && (norm(name) === norm(k) || norm(name).startsWith(norm(k))));

admin.initializeApp();
const db = admin.firestore();

const places = [];
const dishes = [];
for (const doc of (await db.collection("places").get()).docs) {
  const p = { id: doc.id, ...doc.data() };
  const chain = chainFor(p.name ?? "");
  if (!chain || (p.dishCount ?? 0) >= SPARSE) continue;
  places.push({ id: p.id, name: p.name, address: p.address ?? "", lat: p.lat, lng: p.lng, cuisine: p.cuisine ?? "", website: p.website ?? "" });
  for (const [section, names] of Object.entries(CHAINS[chain])) {
    for (const name of names) {
      dishes.push({
        id: `${p.id}-${slug(name)}`, placeId: p.id, name, description: "", price: "", section,
        categories: categorize(name, "", (p.cuisine ?? "") + " " + p.name, section),
        tags: tagsFor(name, "", p.cuisine ?? ""), allergens: allergensFor(name, ""),
      });
    }
  }
}
const ids = new Set();
const unique = dishes.filter((d) => !ids.has(d.id) && ids.add(d.id));
fs.writeFileSync(out, JSON.stringify({ area: "chains", generatedAt: new Date().toISOString(), places, dishes: unique, replaceMenus: true }));
console.log(`${places.length} chain places with sparse menus, ${unique.length} dishes -> ${out}`);
