// Re-applies the item filter and tagging to a seed file without visiting any websites.
//   node reprocess.mjs in.json out.json
import fs from "node:fs";
import { cleanItem } from "./parse.mjs";
import { categorize, tagsFor, allergensFor } from "./taxonomy.mjs";
const [inp, out] = process.argv.slice(2);
const seed = JSON.parse(fs.readFileSync(inp));
const byId = new Map(seed.places.map((p) => [p.id, p]));
const seen = new Set();
seed.dishes = seed.dishes.flatMap((d) => {
  const c = cleanItem(d);
  const p = byId.get(d.placeId);
  if (!c || !p || seen.has(d.id)) return [];
  seen.add(d.id);
  return [{ ...d, ...c, categories: categorize(c.name, c.description, (p.cuisine ?? "") + " " + p.name, c.section), tags: tagsFor(c.name, c.description, p.cuisine ?? ""), allergens: allergensFor(c.name, c.description) }];
});
for (const p of seed.places) p.dishCount = seed.dishes.filter((d) => d.placeId === p.id).length;
fs.writeFileSync(out, JSON.stringify(seed));
console.log(`${seed.places.length} places, ${seed.places.filter((p) => p.dishCount).length} with menus, ${seed.dishes.length} dishes`);
