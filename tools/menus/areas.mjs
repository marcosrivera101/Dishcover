// Queue of areas (geohash 5, about 5 km square) that someone opened the app in.
// The app creates areas/{cell} with status "waiting"; the Area menus workflow runs the menu
// reader there and writes the dishes into the live app.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs next          -> prints "cell s,w,n,e" or nothing
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs done <cell> <menus.json>
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs failed <cell>
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs seed-ct       -> queues all of Connecticut

import admin from "firebase-admin";
import fs from "node:fs";

// Keeps the menu reader's cost small: at most this many areas a day.
const DAILY_MAX = 48;
const B32 = "0123456789bcdefghjkmnpqrstuvwxyz";

function bounds(cell) {
  let lat = [-90, 90], lng = [-180, 180], even = true;
  for (const c of cell) {
    const v = B32.indexOf(c);
    for (let b = 4; b >= 0; b--) {
      const r = even ? lng : lat, mid = (r[0] + r[1]) / 2;
      if ((v >> b) & 1) r[0] = mid; else r[1] = mid;
      even = !even;
    }
  }
  return { s: lat[0], w: lng[0], n: lat[1], e: lng[1] };
}

function encode(lat, lng, precision) {
  let latR = [-90, 90], lngR = [-180, 180], hash = "", bit = 0, ch = 0, even = true;
  while (hash.length < precision) {
    const r = even ? lngR : latR, v = even ? lng : lat, m = (r[0] + r[1]) / 2;
    if (v >= m) { ch = (ch << 1) | 1; r[0] = m; } else { ch <<= 1; r[1] = m; }
    even = !even;
    if (++bit === 5) { hash += B32[ch]; bit = 0; ch = 0; }
  }
  return hash;
}
const mid = (c) => { const b = bounds(c); return { lat: (b.s + b.n) / 2, lng: (b.w + b.e) / 2 }; };
const dist = (a, b) => Math.hypot(a.lat - b.lat, (a.lng - b.lng) * Math.cos((a.lat * Math.PI) / 180));

admin.initializeApp();
const db = admin.firestore();
const [cmd, cell, file] = process.argv.slice(2);
const now = Date.now();

if (cmd === "next") {
  // A run that died part way leaves "working"; give it another go after three hours.
  const stuck = await db.collection("areas").where("status", "==", "working").get();
  for (const d of stuck.docs) if (now - (d.get("startedAt") ?? 0) > 3 * 3600e3) await d.ref.update({ status: "waiting" });

  const today = await db.collection("areas").where("startedAt", ">", now - 24 * 3600e3).get();
  if (today.size >= DAILY_MAX) { console.error(`daily limit reached (${today.size})`); process.exit(0); }
  const waiting = await db.collection("areas").where("status", "==", "waiting").get();
  // People who opened the app somewhere new go before the Connecticut queue.
  const rank = (d) => (d.get("requestedBy") === "seed-ct" ? 1 : 0);
  const next = waiting.docs.sort((a, b) => rank(a) - rank(b) || (a.get("requestedAt") ?? 0) - (b.get("requestedAt") ?? 0))[0];
  if (!next) process.exit(0);
  await next.ref.update({ status: "working", startedAt: now });
  const b = bounds(next.id);
  console.log(`${next.id} ${[b.s, b.w, b.n, b.e].map((x) => x.toFixed(5)).join(",")}`);
} else if (cmd === "done") {
  const seed = JSON.parse(fs.readFileSync(file));
  await db.collection("areas").doc(cell).update({
    status: "done", doneAt: now,
    places: seed.places.length,
    withMenus: seed.places.filter((p) => p.dishCount).length,
    dishes: seed.dishes.length,
  });
  console.log(`${cell}: ${seed.places.length} places, ${seed.dishes.length} dishes`);
} else if (cmd === "seed-ct") {
  // Queues every ~5 km area of Connecticut that has at least 4 places to eat, nearest to Hartford first.
  const q = `[out:json][timeout:180];area["ISO3166-2"="US-CT"]->.ct;
(nwr["amenity"~"^(restaurant|fast_food|cafe|ice_cream|pub)$"]["name"](area.ct););out center;`;
  let els = [];
  for (const host of ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter"]) {
    try {
      const r = await fetch(host, { method: "POST", body: new URLSearchParams({ data: q }), headers: { "User-Agent": "Dishcover menu check (godishcover.com)" } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      els = (await r.json()).elements; break;
    } catch (e) { console.error(host, e.message); }
  }
  const counts = new Map();
  for (const el of els) {
    const lat = el.lat ?? el.center?.lat, lng = el.lon ?? el.center?.lon;
    if (lat == null) continue;
    const c = encode(lat, lng, 5);
    counts.set(c, (counts.get(c) ?? 0) + 1);
  }
  const home = { lat: 41.7658, lng: -72.6734 };
  const cells = [...counts].filter(([, n]) => n >= 4).map(([c]) => c)
    .sort((a, b) => dist(mid(a), home) - dist(mid(b), home));
  const existing = new Set((await db.collection("areas").select().get()).docs.map((d) => d.id));
  let added = 0;
  for (const [i, c] of cells.entries()) {
    if (existing.has(c)) continue;
    // Older requestedAt runs first, so nearer areas go first.
    await db.collection("areas").doc(c).set({ status: "waiting", requestedAt: now - 1e9 + i * 1000, requestedBy: "seed-ct" });
    added++;
  }
  console.log(`${els.length} places in CT, ${cells.length} areas with 4+, ${added} queued`);
} else if (cmd === "failed") {
  await db.collection("areas").doc(cell).update({ status: "failed", doneAt: now });
} else {
  console.error("usage: areas.mjs next | done <cell> <file> | failed <cell>");
  process.exit(1);
}
