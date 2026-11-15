/**
 * Points, levels and badges. Everything is counted here on the server from what people
 * actually saved, so nobody can give themselves points. Each award has a key and is only
 * ever given once; deleting the thing that earned it takes the points back.
 *
 * Kept in stats/{uid} (public, read-only to the app) with a private log in stats/{uid}/log.
 */
import { getFirestore } from "firebase-admin/firestore";
import { onDocumentWritten, onDocumentCreated, onDocumentDeleted, onDocumentUpdated } from "firebase-functions/v2/firestore";

const db = getFirestore();
const REGION = "us-central1";

export const POINTS = {
  rate: 10, review: 5, photo: 10, first: 10, dish: 5, place: 25, fullMenu: 25, like: 2,
};
const LIKE_DAY_CAP = 20;
const REVIEW_MIN_CHARS = 40;

export const LEVELS: [number, string][] = [
  [0, "Nibbler"], [50, "Taster"], [150, "Regular"], [400, "Foodie"],
  [800, "Connoisseur"], [1500, "Critic"], [3000, "Tastemaker"], [6000, "Legend"],
];
export function levelFor(points: number) {
  let i = 0;
  while (i + 1 < LEVELS.length && points >= LEVELS[i + 1][0]) i++;
  return i + 1;
}

/** Badge id -> [counter, how many]. */
const BADGES: Record<string, [string, number]> = {
  first_bite: ["ratings", 1],
  regular_rater: ["ratings", 25],
  explorer: ["places", 10],
  globetrotter: ["places", 50],
  spice_seeker: ["spicy", 10],
  photographer: ["photos", 10],
  early_bird: ["firsts", 5],
  night_owl: ["late", 5],
  menu_maker: ["menus", 1],
  trailblazer: ["newPlaces", 3],
  wordsmith: ["reviews", 10],
  crowd_pleaser: ["likes", 50],
};

/** Hartford time, so "today" and "late night" match the person's day. */
function eastern(ts: number) {
  const s = new Date(ts).toLocaleString("en-US", { timeZone: "America/New_York", hour12: false });
  const [d, t] = s.split(", ");
  return { day: d, hour: Number(t.split(":")[0]) % 24 };
}

interface Award { key: string; points: number; kind: string; counters?: Record<string, number>; src: string }

async function award(uid: string, awards: Award[]) {
  if (!uid || uid.startsWith("dishcover-") || awards.length === 0) return;
  const ref = db.collection("stats").doc(uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const logs = await Promise.all(awards.map((a) => tx.get(ref.collection("log").doc(a.key))));
    const s = snap.data() ?? {};
    const today = eastern(Date.now()).day;
    let dayPoints = s.day === today ? (s.dayPoints as number) || 0 : 0;
    let dayLikes = s.day === today ? (s.dayLikes as number) || 0 : 0;
    let points = (s.points as number) || 0;
    const counts: Record<string, number> = { ...(s.counts ?? {}) };
    let changed = false;
    awards.forEach((a, i) => {
      if (logs[i].exists) return;
      let pts = Math.max(0, a.points);
      if (a.kind === "like") { pts = Math.min(pts, LIKE_DAY_CAP - dayLikes); dayLikes += pts; }
      dayPoints += pts;
      points += pts;
      for (const [k, v] of Object.entries(a.counters ?? {})) counts[k] = (counts[k] || 0) + v;
      tx.set(ref.collection("log").doc(a.key), { points: pts, kind: a.kind, counters: a.counters ?? {}, src: a.src, at: Date.now() });
      changed = true;
    });
    if (!changed) return;
    const badges = Object.entries(BADGES).filter(([, [c, n]]) => (counts[c] || 0) >= n).map(([id]) => id);
    tx.set(ref, { points, level: levelFor(points), counts, badges, day: today, dayPoints, dayLikes, updatedAt: Date.now() }, { merge: true });
  });
}

/** Takes back every award that came from [src]. Counters go down; badges already earned stay. */
async function revoke(uid: string, src: string) {
  if (!uid) return;
  const ref = db.collection("stats").doc(uid);
  const logs = await ref.collection("log").where("src", "==", src).get();
  if (logs.empty) return;
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const s = snap.data() ?? {};
    let points = (s.points as number) || 0;
    const counts: Record<string, number> = { ...(s.counts ?? {}) };
    logs.docs.forEach((d) => {
      const l = d.data();
      points -= (l.points as number) || 0;
      for (const [k, v] of Object.entries((l.counters as Record<string, number>) ?? {})) counts[k] = Math.max(0, (counts[k] || 0) - v);
      tx.delete(d.ref);
    });
    points = Math.max(0, points);
    tx.set(ref, { points, level: levelFor(points), counts, updatedAt: Date.now() }, { merge: true });
  });
}

async function onRating(kind: "dish" | "place", path: string, before: FirebaseFirestore.DocumentData | undefined, after: FirebaseFirestore.DocumentData | undefined) {
  if (!after) { if (before) await revoke(before.userId as string, path); return; }
  const uid = after.userId as string;
  const targetId = after.dishId as string; // the place id for place ratings
  const awards: Award[] = [];
  if (!before) {
    const others = await db.collection(kind === "dish" ? "ratings" : "placeRatings").where("dishId", "==", targetId).limit(2).get();
    const isFirst = others.docs.filter((d) => d.ref.path !== path).length === 0;
    let placeId = targetId;
    let spicy = false;
    if (kind === "dish") {
      const dish = (await db.collection("dishes").doc(targetId).get()).data();
      placeId = (dish?.placeId as string) || "";
      spicy = ((dish?.tags as string[]) || []).includes("spicy");
    }
    const late = eastern((after.updatedAt as number) || Date.now()).hour;
    const counters: Record<string, number> = { ratings: 1 };
    if (spicy) counters.spicy = 1;
    if (late >= 22 || late < 4) counters.late = 1;
    awards.push({ key: `rate_${kind}_${targetId}`, points: POINTS.rate, kind: "rate", counters, src: path });
    if (isFirst) awards.push({ key: `first_${kind}_${targetId}`, points: POINTS.first, kind: "first", counters: { firsts: 1 }, src: path });
    if (placeId) awards.push({ key: `visit_${placeId}`, points: 0, kind: "visit", counters: { places: 1 }, src: path });
  }
  if (((after.text as string) || "").trim().length >= REVIEW_MIN_CHARS) {
    awards.push({ key: `review_${kind}_${targetId}`, points: POINTS.review, kind: "review", counters: { reviews: 1 }, src: path });
  }
  await award(uid, awards);
}

export const pointsForDishRating = onDocumentWritten({ document: "ratings/{id}", region: REGION }, (e) =>
  onRating("dish", e.data!.after.ref.path, e.data?.before.data(), e.data?.after.data()));

export const pointsForPlaceRating = onDocumentWritten({ document: "placeRatings/{id}", region: REGION }, (e) =>
  onRating("place", e.data!.after.ref.path, e.data?.before.data(), e.data?.after.data()));

export const pointsForPost = onDocumentCreated({ document: "feed/{id}", region: REGION }, async (e) => {
  const item = e.data?.data();
  if (!item || !item.photoUrl) return;
  await award(item.userId as string, [{ key: `photo_${e.params.id}`, points: POINTS.photo, kind: "photo", counters: { photos: 1 }, src: e.data!.ref.path }]);
});

export const pointsForLikes = onDocumentUpdated({ document: "feed/{id}", region: REGION }, async (e) => {
  const before = (e.data?.before.data()?.likeCount as number) || 0;
  const after = (e.data?.after.data()?.likeCount as number) || 0;
  if (after <= before) return;
  const uid = e.data!.after.data()!.userId as string;
  // One award per like count reached, so unlike-and-like-again can't farm points.
  const awards: Award[] = [];
  for (let n = before + 1; n <= after; n++) {
    awards.push({ key: `like_${e.params.id}_${n}`, points: POINTS.like, kind: "like", counters: { likes: 1 }, src: e.data!.after.ref.path });
  }
  await award(uid, awards);
});

export const pointsForDish = onDocumentCreated({ document: "dishes/{id}", region: REGION }, async (e) => {
  const d = e.data?.data();
  if (!d) return;
  const uid = d.createdBy as string;
  const placeId = d.placeId as string;
  const mine = await db.collection("dishes").where("placeId", "==", placeId).where("createdBy", "==", uid).limit(5).get();
  const awards: Award[] = [{ key: `dish_${e.params.id}`, points: POINTS.dish, kind: "dish", src: e.data!.ref.path }];
  if (mine.size >= 5) awards.push({ key: `menu_${placeId}`, points: POINTS.fullMenu, kind: "menu", counters: { menus: 1 }, src: `places/${placeId}` });
  await award(uid, awards);
});

export const pointsForPlace = onDocumentCreated({ document: "places/{id}", region: REGION }, async (e) => {
  const p = e.data?.data();
  if (!p) return;
  await award(p.createdBy as string, [{ key: `place_${e.params.id}`, points: POINTS.place, kind: "place", counters: { newPlaces: 1 }, src: e.data!.ref.path }]);
});

export const pointsTakenBack = onDocumentDeleted({ document: "{col}/{id}", region: REGION }, async (e) => {
  const col = e.params.col;
  if (!["feed", "dishes", "places"].includes(col)) return;
  const d = e.data?.data();
  if (!d) return;
  await revoke(((d.userId ?? d.createdBy) as string) || "", e.data!.ref.path);
});

