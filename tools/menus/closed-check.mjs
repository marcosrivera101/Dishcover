// Finds restaurants Google lists as permanently closed and takes them out of Dishcover.
//
//   GOOGLE_APPLICATION_CREDENTIALS=sa.json MAPS_API_KEY=... node closed-check.mjs [report.json]
//
// Each place is looked up once with Places Text Search (name + address, near its pin) and stamped
// with statusCheckedAt, so later runs only check new places and ones not checked in RECHECK_DAYS.
// MAX_CHECKS caps the lookups per run to stay inside the free monthly Places allowance.
// A closed place nobody has rated is deleted with its dishes and feed posts; a rated one is hidden
// (closed: true) so its reviews aren't lost. DRY=1 only reports.

import admin from "firebase-admin";
import fs from "node:fs";

const KEY = process.env.MAPS_API_KEY;
const MAX_CHECKS = Number(process.env.MAX_CHECKS ?? 2500);
const RECHECK_DAYS = Number(process.env.RECHECK_DAYS ?? 120);
const DRY = process.env.DRY === "1";
// ONLY_NEW=1 (after a menu import) checks just places never checked before, so the total stays bounded.
const ONLY_NEW = process.env.ONLY_NEW === "1";
const reportPath = process.argv[2] ?? "closed-report.json";
if (!KEY) { console.error("No MAPS_API_KEY."); process.exit(1); }

admin.initializeApp();
const db = admin.firestore();

const words = (s) => new Set(s.toLowerCase().replace(/['’`]/g, "").replace(/&/g, " and ").split(/[^a-z0-9]+/).filter((w) => w.length > 1 && !["the", "and", "of", "restaurant", "cafe", "bar", "grill", "llc", "inc"].includes(w)));
function sameName(a, b) {
  const A = words(a), B = words(b);
  if (!A.size || !B.size) return false;
  const shared = [...A].filter((w) => B.has(w)).length;
  return shared / Math.min(A.size, B.size) >= 0.5;
}
function meters(a, b) {
  const R = 6371000, r = Math.PI / 180;
  const x = Math.sin(((b.lat - a.lat) * r) / 2) ** 2 + Math.cos(a.lat * r) * Math.cos(b.lat * r) * Math.sin(((b.lng - a.lng) * r) / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}

async function lookup(p) {
  const body = {
    textQuery: [p.name, p.address].filter(Boolean).join(", "),
    maxResultCount: 3,
    ...(p.lat || p.lng ? { locationBias: { circle: { center: { latitude: p.lat, longitude: p.lng }, radius: 500 } } } : {}),
  };
  for (let attempt = 1; attempt <= 3; attempt++) {
    const res = await fetch("https://places.googleapis.com/v1/places:searchText", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-Goog-Api-Key": KEY, "X-Goog-FieldMask": "places.id,places.displayName,places.businessStatus,places.location" },
      body: JSON.stringify(body),
    });
    if (res.status === 429 || res.status >= 500) { await new Promise((r) => setTimeout(r, 5000 * attempt)); continue; }
    const json = await res.json();
    if (!res.ok) throw Object.assign(new Error(json.error?.message ?? `HTTP ${res.status}`), { fatal: res.status === 403 || res.status === 400 });
    const near = (json.places ?? []).filter((g) => g.location && sameName(p.name, g.displayName?.text ?? "") &&
      (!(p.lat || p.lng) || meters(p, { lat: g.location.latitude, lng: g.location.longitude }) < 300));
    // When Google has both a closed and an open listing for the same name here (a move or a new owner), it's open.
    return near.find((g) => g.businessStatus === "OPERATIONAL") ?? near[0] ?? null;
  }
  throw new Error("Places kept failing");
}

async function remove(p) {
  const ref = db.collection("places").doc(p.id);
  const ds = await db.collection("dishes").where("placeId", "==", p.id).get();
  const rated = (p.ratingCount ?? 0) > 0 || ds.docs.some((d) => (d.get("ratingCount") ?? 0) > 0);
  if (rated) { await ref.set({ closed: true }, { merge: true }); return "hidden"; }
  const feed = await db.collection("feed").where("placeId", "==", p.id).get();
  const refs = [...ds.docs, ...feed.docs].map((d) => d.ref).concat(ref);
  for (let i = 0; i < refs.length; i += 400) {
    const b = db.batch();
    refs.slice(i, i + 400).forEach((r) => b.delete(r));
    await b.commit();
  }
  return "deleted";
}

const now = Date.now();
const all = (await db.collection("places").get()).docs.map((d) => ({ id: d.id, ...d.data() }));
const due = all
  .filter((p) => !p.closed && (ONLY_NEW ? !p.statusCheckedAt : now - (p.statusCheckedAt ?? 0) > RECHECK_DAYS * 864e5))
  // Never-checked places with menus first: those are the ones people see.
  .sort((a, b) => (a.statusCheckedAt ?? 0) - (b.statusCheckedAt ?? 0) || (b.dishCount ?? 0) - (a.dishCount ?? 0))
  .slice(0, MAX_CHECKS);
console.log(`${all.length} places, checking ${due.length}${DRY ? " (dry run)" : ""}`);

const report = { checkedAt: new Date().toISOString(), checked: 0, closed: [], notFound: [] };
const queue = [...due];
let fatal = null;
async function worker() {
  while (queue.length && !fatal) {
    const p = queue.shift();
    let g;
    try { g = await lookup(p); } catch (e) { if (e.fatal) fatal = e; else console.log(`skip ${p.name}: ${e.message}`); continue; }
    report.checked++;
    const status = g?.businessStatus ?? "NOT_FOUND";
    if (status === "CLOSED_PERMANENTLY") {
      const action = DRY ? "would remove" : await remove(p);
      report.closed.push({ id: p.id, name: p.name, address: p.address, action });
      console.log(`CLOSED ${p.name} (${p.address}): ${action}`);
    } else if (status === "NOT_FOUND") {
      report.notFound.push({ id: p.id, name: p.name, address: p.address, dishCount: p.dishCount ?? 0 });
    }
    if (!DRY && status !== "CLOSED_PERMANENTLY") {
      await db.collection("places").doc(p.id).set({ statusCheckedAt: now, googleStatus: status }, { merge: true });
    }
  }
}
await Promise.all(Array.from({ length: 5 }, worker));
fs.writeFileSync(reportPath, JSON.stringify(report, null, 1));
console.log(`checked ${report.checked}, closed ${report.closed.length}, not found on Google ${report.notFound.length}`);
if (fatal) { console.error(`Places API refused: ${fatal.message}`); process.exit(1); }
