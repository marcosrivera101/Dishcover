// Queue of areas (geohash 5, about 5 km square) that someone opened the app in.
// The app creates areas/{cell} with status "waiting"; the Area menus workflow runs the menu
// reader there and writes the dishes into the live app.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs next          -> prints "cell s,w,n,e" or nothing
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs done <cell> <menus.json>
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json node areas.mjs failed <cell>

import admin from "firebase-admin";
import fs from "node:fs";

// Keeps the menu reader's cost small: at most this many new areas a day.
const DAILY_MAX = 15;
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
  const next = waiting.docs.sort((a, b) => (a.get("requestedAt") ?? 0) - (b.get("requestedAt") ?? 0))[0];
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
} else if (cmd === "failed") {
  await db.collection("areas").doc(cell).update({ status: "failed", doneAt: now });
} else {
  console.error("usage: areas.mjs next | done <cell> <file> | failed <cell>");
  process.exit(1);
}
