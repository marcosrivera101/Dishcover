/**
 * Dishcover server code.
 *  - readMenu: turns a menu photo or a menu link into draft dishes with Gemini. Nothing is saved
 *    here; the app shows the draft and the user saves what they confirm. Photos are never stored.
 *  - cleanupGoogleCoords: Google Places coordinates may be cached for 30 days, so older ones are cleared.
 */
import { initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { lookup } from "node:dns/promises";
import { isIP } from "node:net";

initializeApp();
const db = getFirestore();
const GEMINI_API_KEY = defineSecret("GEMINI_API_KEY");

const MODEL = "gemini-2.5-flash-lite";
const PER_USER_PER_DAY = 10;
const APP_PER_DAY = 30; // closed-test cap; raise after launch
const MAX_IMAGE_BYTES = 1_100_000;
const MAX_PAGE_BYTES = 1_000_000;

const CATEGORIES = [
  "burger", "pizza", "tacos", "sushi", "salad", "sandwich", "pasta",
  "chicken", "seafood", "breakfast", "dessert", "coffee", "other",
];
const ALLERGENS = ["Peanuts", "Tree nuts", "Shellfish", "Fish", "Milk", "Eggs", "Wheat / gluten", "Soy", "Sesame"];

const PROMPT = `You read restaurant menus. List every dish or drink on this menu.
For each item return:
- name: the item's name as printed
- categories: one or more of ${CATEGORIES.join(", ")} (use "other" if none fit)
- price: the price exactly as printed, like "$12" or "$12.50"; empty string if no price is printed
- allergens: only allergens the menu itself prints for this item, from: ${ALLERGENS.join(", ")}
- ingredientsUnknown: true when the menu doesn't describe the ingredients
Do not guess prices or allergens. Skip section headings, taxes, and anything that isn't food or drink.`;

const SCHEMA = {
  type: "OBJECT",
  properties: {
    dishes: {
      type: "ARRAY",
      items: {
        type: "OBJECT",
        properties: {
          name: { type: "STRING" },
          categories: { type: "ARRAY", items: { type: "STRING", enum: CATEGORIES } },
          price: { type: "STRING" },
          allergens: { type: "ARRAY", items: { type: "STRING", enum: ALLERGENS } },
          ingredientsUnknown: { type: "BOOLEAN" },
        },
        required: ["name", "categories", "price", "allergens", "ingredientsUnknown"],
      },
    },
  },
  required: ["dishes"],
};

type Draft = { name: string; categories: string[]; price: string; allergens: string[]; ingredientsUnknown: boolean };

export const readMenu = onCall(
  { secrets: [GEMINI_API_KEY], region: "us-central1", timeoutSeconds: 60, memory: "512MiB", enforceAppCheck: false },
  async (req) => {
    const uid = req.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");
    const image = typeof req.data?.image === "string" ? (req.data.image as string) : undefined;
    const url = typeof req.data?.url === "string" ? (req.data.url as string) : undefined;
    if (!image && !url) throw new HttpsError("invalid-argument", "Send a photo or a link.");

    await takeQuota(uid);

    let parts: unknown[];
    if (image) {
      const bytes = Buffer.from(image, "base64");
      if (bytes.length > MAX_IMAGE_BYTES) throw new HttpsError("invalid-argument", "That photo is too big.");
      parts = [{ inlineData: { mimeType: "image/jpeg", data: image } }, { text: PROMPT }];
    } else {
      const text = await fetchMenuText(url!);
      parts = [{ text: PROMPT + "\n\nMenu page text:\n" + text }];
    }

    const res = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": GEMINI_API_KEY.value() },
        body: JSON.stringify({
          contents: [{ role: "user", parts }],
          generationConfig: { responseMimeType: "application/json", responseSchema: SCHEMA, temperature: 0 },
        }),
      },
    );
    if (!res.ok) {
      console.error("Gemini error", res.status, await res.text());
      throw new HttpsError("unavailable", "The menu reader is busy. Try again in a minute.");
    }
    const body = (await res.json()) as { candidates?: { content?: { parts?: { text?: string }[] } }[] };
    const raw = body.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("") ?? "";
    let dishes: Draft[] = [];
    try {
      dishes = (JSON.parse(raw).dishes ?? []) as Draft[];
    } catch {
      throw new HttpsError("internal", "Couldn't read that menu. Try a clearer photo.");
    }
    dishes = dishes
      .filter((d) => typeof d.name === "string" && d.name.trim().length > 0)
      .slice(0, 150)
      .map((d) => ({
        name: d.name.trim().slice(0, 80),
        categories: (d.categories ?? []).filter((c) => CATEGORIES.includes(c)),
        price: (d.price ?? "").trim().slice(0, 20),
        allergens: (d.allergens ?? []).filter((a) => ALLERGENS.includes(a)),
        ingredientsUnknown: !!d.ingredientsUnknown,
      }));
    return { dishes };
  },
);

/** 10 reads per user per day and 30 app-wide during the closed test. */
async function takeQuota(uid: string): Promise<void> {
  const day = new Date().toISOString().slice(0, 10);
  const ref = db.collection("usage").doc(day);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const total = (snap.get("total") as number | undefined) ?? 0;
    const mine = (snap.get(`users.${uid}`) as number | undefined) ?? 0;
    if (mine >= PER_USER_PER_DAY) {
      throw new HttpsError("resource-exhausted", "You've used today's 10 menu reads. You can still type dishes.");
    }
    if (total >= APP_PER_DAY) {
      throw new HttpsError("resource-exhausted", "The menu reader is full for today. You can still type dishes.");
    }
    tx.set(ref, { total: FieldValue.increment(1), users: { [uid]: FieldValue.increment(1) } }, { merge: true });
  });
}

function isPrivateAddress(ip: string): boolean {
  if (isIP(ip) === 4) {
    const [a, b] = ip.split(".").map(Number);
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
  }
  const v6 = ip.toLowerCase();
  return v6 === "::1" || v6 === "::" || v6.startsWith("fc") || v6.startsWith("fd") || v6.startsWith("fe80") ||
    v6.startsWith("::ffff:");
}

async function checkHost(u: URL): Promise<void> {
  if (u.protocol !== "https:") throw new HttpsError("invalid-argument", "Use a link that starts with https://");
  const addrs = await lookup(u.hostname, { all: true }).catch(() => []);
  if (addrs.length === 0 || addrs.some((a) => isPrivateAddress(a.address))) {
    throw new HttpsError("invalid-argument", "That link can't be opened.");
  }
}

async function robotsAllows(u: URL): Promise<boolean> {
  try {
    const r = await fetch(`${u.origin}/robots.txt`, { signal: AbortSignal.timeout(4000), redirect: "manual" });
    if (!r.ok) return true;
    const lines = (await r.text()).split(/\r?\n/);
    let applies = false;
    for (const line of lines) {
      const [k, ...rest] = line.split(":");
      const key = k.trim().toLowerCase();
      const val = rest.join(":").trim();
      if (key === "user-agent") applies = val === "*" || val.toLowerCase().includes("dishcover");
      else if (applies && key === "disallow" && val && u.pathname.startsWith(val)) return false;
    }
    return true;
  } catch {
    return true;
  }
}

async function readCapped(res: Response): Promise<string> {
  const reader = res.body?.getReader();
  if (!reader) return "";
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done || !value) break;
    size += value.length;
    if (size > MAX_PAGE_BYTES) { await reader.cancel(); break; }
    chunks.push(value);
  }
  return Buffer.concat(chunks).toString("utf8");
}

/** Best-effort fetch: https only, no private addresses, robots.txt, one same-site redirect, about 1 MB. */
async function fetchMenuText(raw: string): Promise<string> {
  let u: URL;
  try { u = new URL(raw); } catch { throw new HttpsError("invalid-argument", "That doesn't look like a link."); }
  await checkHost(u);
  if (!(await robotsAllows(u))) throw new HttpsError("failed-precondition", "That site doesn't allow reading its menu. Take a photo instead.");
  const opts = { signal: AbortSignal.timeout(8000), redirect: "manual" as const, headers: { "User-Agent": "DishcoverMenuReader/1.0" } };
  let res = await fetch(u, opts);
  if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
    const next = new URL(res.headers.get("location")!, u);
    if (next.hostname !== u.hostname) throw new HttpsError("failed-precondition", "That link goes to another site. Take a photo instead.");
    await checkHost(next);
    res = await fetch(next, opts);
  }
  if (!res.ok) throw new HttpsError("failed-precondition", "We couldn't open that page. Take a photo instead.");
  const html = await readCapped(res);
  const text = html
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/\s+/g, " ")
    .trim();
  if (text.length < 200) throw new HttpsError("failed-precondition", "That page is mostly empty to us. Take a photo instead.");
  return text.slice(0, 60_000);
}

/** Google lets apps cache place coordinates for 30 days. Older ones are cleared and refresh on next use. */
export const cleanupGoogleCoords = onSchedule({ schedule: "every day 04:00", region: "us-central1" }, async () => {
  const cutoff = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const stale = await db.collection("places").where("coordsCachedAt", ">", 0).where("coordsCachedAt", "<", cutoff).get();
  for (const doc of stale.docs) {
    const batch = db.batch();
    batch.update(doc.ref, { lat: 0, lng: 0, gh4: "", coordsCachedAt: 0 });
    const dishes = await db.collection("dishes").where("placeId", "==", doc.id).get();
    dishes.docs.forEach((d) => batch.update(d.ref, { gh4: "" }));
    await batch.commit();
  }
  console.log(`Cleared coordinates for ${stale.size} places`);
});
