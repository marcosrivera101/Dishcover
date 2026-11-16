// Turns a hand-gathered town sheet (Muse's / Grok's format) into the menu check's seed format,
// so sync-firestore.mjs can load it into the live app.
//
//   node import-sheet.mjs handoffs/manchester.jsonl area-manchester.json
//
// Input: one JSON array per line: Restaurant, Address, Phone, Cuisine/Type, Category, Dish,
// Description, Price, Photo URL, Source (first line is the header).
// Each restaurant is matched to its OpenStreetMap place (same ids as the automatic job, so the
// two never double up); places OSM doesn't know are placed by their address.

import fs from "node:fs";
import { cleanItem } from "./parse.mjs";
import { categorize, tagsFor, allergensFor } from "./taxonomy.mjs";

const [inp, out] = process.argv.slice(2);
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const slug = (s) => s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60);
const norm = (s) => s.toLowerCase().replace(/['’`]/g, "").replace(/\b(the|restaurant|cafe|café|bar|grill|and|&|llc|inc)\b/g, "").replace(/[^a-z0-9]+/g, "");
const UA = { "User-Agent": "Dishcover menu import (godishcover.com)" };

const lines = fs.readFileSync(inp, "utf8").split("\n").filter(Boolean).map((l) => JSON.parse(l));
const head = lines.shift().map((h) => String(h).toLowerCase());
const col = (name) => head.findIndex((h) => h.startsWith(name));
const C = { r: col("restaurant"), a: col("address"), cu: col("cuisine"), cat: col("category"), d: col("dish"), desc: col("description"), p: col("price"), src: col("source") };

// Group rows by restaurant + address.
const byPlace = new Map();
for (const row of lines) {
  const name = String(row[C.r] ?? "").trim(), dish = String(row[C.d] ?? "").trim();
  if (!name || !dish) continue;
  const key = name + "|" + String(row[C.a] ?? "").trim();
  if (!byPlace.has(key)) byPlace.set(key, { name, address: String(row[C.a] ?? "").trim(), cuisine: String(row[C.cu] ?? "").trim(), rows: [] });
  byPlace.get(key).rows.push(row);
}
log(`${lines.length} rows, ${byPlace.size} restaurants`);

// Town from the first address ("..., Manchester, CT 06042").
const town = [...byPlace.values()][0]?.address.match(/,\s*([^,]+),\s*CT\b/)?.[1]?.trim() ?? "";

async function overpassTown() {
  const q = `[out:json][timeout:90];area["name"="${town}"]["boundary"="administrative"]["is_in:state_code"!="NH"]->.t;
(nwr["amenity"~"^(restaurant|fast_food|cafe|ice_cream|food_court|pub|bar)$"]["name"](area.t);
 nwr["shop"~"^(bakery|deli|pastry|coffee)$"]["name"](area.t););out center tags;`;
  for (const host of ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://overpass-api.de/api/interpreter", "https://overpass.private.coffee/api/interpreter"]) {
    try {
      const r = await fetch(host, { method: "POST", body: new URLSearchParams({ data: q }), headers: UA });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      return (await r.json()).elements;
    } catch (e) { log(`overpass ${host}: ${e.message}`); await new Promise((r) => setTimeout(r, 20000)); }
  }
  // Without OpenStreetMap the places would get new ids and double up with the automatic job's.
  throw new Error("OpenStreetMap is unreachable; try again later");
}

async function geocode(address) {
  await new Promise((r) => setTimeout(r, 1100)); // Nominatim allows one request a second.
  const u = `https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=us&q=${encodeURIComponent(address)}`;
  try {
    const j = await (await fetch(u, { headers: UA })).json();
    return j[0] ? { lat: Number(j[0].lat), lng: Number(j[0].lon) } : null;
  } catch { return null; }
}

// The town search also finds same-named towns elsewhere (Manchester, England), so keep Connecticut only.
const inCT = (o) => o.lat > 40.9 && o.lat < 42.1 && o.lng > -73.8 && o.lng < -71.7;
const osmAll = (await overpassTown()).filter((e) => e.tags?.name).map((e) => ({
  id: `osm-${e.type[0]}${e.id}`, n: norm(e.tags.name), lat: e.lat ?? e.center?.lat, lng: e.lon ?? e.center?.lon,
  street: (e.tags["addr:housenumber"] ?? "") + " " + (e.tags["addr:street"] ?? ""), website: e.tags.website ?? "",
}));
const osm = osmAll.filter(inCT);
log(`${town}: ${osm.length} places in OpenStreetMap`);

const places = [], dishes = [], retire = [];
let matched = 0, geocoded = 0, missed = 0;
for (const p of byPlace.values()) {
  const n = norm(p.name);
  const num = p.address.match(/^\d+/)?.[0];
  const cands = osm.filter((o) => o.n && (o.n === n || o.n.includes(n) || n.includes(o.n)));
  const hit = cands.find((o) => num && o.street.trim().startsWith(num)) ?? (cands.length === 1 ? cands[0] : null);
  let place;
  // Earlier imports searched without the Connecticut filter and could pick a same-named place in
  // another Manchester; retire whatever that old match would have been.
  const oldCands = osmAll.filter((o) => o.n && (o.n === n || o.n.includes(n) || n.includes(o.n)));
  const oldHit = oldCands.find((o) => num && o.street.trim().startsWith(num)) ?? (oldCands.length === 1 ? oldCands[0] : null);
  if (oldHit && !inCT(oldHit)) retire.push(oldHit.id);
  if (hit) { matched++; place = { id: hit.id, lat: hit.lat, lng: hit.lng, website: hit.website }; retire.push(`x-${slug(p.name)}-${slug(town)}`); }
  else {
    const g = p.address && (await geocode(p.address));
    if (!g) { missed++; log(`no location for ${p.name} (${p.address})`); continue; }
    geocoded++;
    place = { id: `x-${slug(p.name)}-${slug(town)}`, ...g, website: "" };
  }
  const own = p.rows.map((r) => String(r[C.src] ?? "")).find((s) => /^https?:/.test(s) && !/doordash|ubereats|grubhub|allmenus|postmates|seamless|menupix|yelp/.test(s));
  places.push({
    id: place.id, name: p.name, address: p.address.replace(/\s+\d{5}(-\d{4})?$/, ""), lat: place.lat, lng: place.lng,
    cuisine: p.cuisine.toLowerCase().replace(/\s*\/\s*/g, ";"), website: place.website || own || "", menuSource: "hand-gathered",
  });
  const seen = new Set();
  for (const r of p.rows) {
    const c = cleanItem({ name: String(r[C.d]).trim(), description: String(r[C.desc] ?? "").trim(), price: String(r[C.p] ?? "").trim(), section: String(r[C.cat] ?? "").trim() });
    if (!c) continue;
    const price = c.price && /\d/.test(c.price) && Number(String(c.price).replace(/[^0-9.]/g, "")) > 0 ? `$${Number(String(c.price).replace(/[^0-9.]/g, "")).toFixed(2)}` : "";
    const id = `${place.id}-${slug(c.name)}`;
    if (seen.has(id)) continue;
    seen.add(id);
    dishes.push({
      id, placeId: place.id, name: c.name, description: c.description ?? "", price, section: c.section ?? "",
      categories: categorize(c.name, c.description, p.cuisine + " " + p.name, c.section),
      tags: tagsFor(c.name, c.description, p.cuisine), allergens: allergensFor(c.name, c.description),
    });
  }
}
for (const p of places) p.dishCount = dishes.filter((d) => d.placeId === p.id).length;
fs.writeFileSync(out, JSON.stringify({ area: `${town}, CT (hand-gathered)`, generatedAt: new Date().toISOString(), places, dishes, retire }));
log(`wrote ${out}: ${places.length} places (${matched} matched, ${geocoded} by address, ${missed} skipped), ${dishes.length} dishes`);
