// Prints links to a restaurant's Google photos (people often post the menu there), one per line.
//
//   MAPS_API_KEY=... node google-photos.mjs "Chaves Restaurant" "1819 Park Street, Hartford, CT" [max]
//
// Each photo link counts against the Places photo allowance (1,000 free a month), so keep max small.

const [name, address, max = "10"] = process.argv.slice(2);
const KEY = process.env.MAPS_API_KEY;
if (!KEY || !name) { console.error("Needs MAPS_API_KEY and a name."); process.exit(1); }

const res = await fetch("https://places.googleapis.com/v1/places:searchText", {
  method: "POST",
  headers: { "Content-Type": "application/json", "X-Goog-Api-Key": KEY, "X-Goog-FieldMask": "places.id,places.displayName,places.photos" },
  body: JSON.stringify({ textQuery: [name, address].filter(Boolean).join(", "), maxResultCount: 1 }),
});
const json = await res.json();
if (!res.ok) { console.error(json.error?.message ?? `HTTP ${res.status}`); process.exit(1); }
const place = json.places?.[0];
if (!place) { console.error("Not found on Google."); process.exit(1); }
console.error(`${place.displayName?.text}: ${place.photos?.length ?? 0} photos`);
for (const p of (place.photos ?? []).slice(0, Number(max))) {
  const m = await fetch(`https://places.googleapis.com/v1/${p.name}/media?maxWidthPx=1600&skipHttpRedirect=true&key=${KEY}`);
  const j = await m.json();
  if (j.photoUri) console.log(j.photoUri);
}
