// Finds restaurants in an area, reads each one's menu from its own website, and writes
// a seed file the app and the daily menu check use.
//
//   node run.mjs --out hartford-menus.json [--prev previous.json] [--limit 50]
//
// Menu pages are read by a text parser and a heading reader; with GEMINI_API_KEY set, Gemini reads
// only the pages those two can't.

import { chromium } from "playwright";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import { parseMenuText, cleanItem } from "./parse.mjs";
import { categorize, tagsFor, allergensFor } from "./taxonomy.mjs";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith("--") ? [...acc, [a.slice(2), all[i + 1]]] : acc), []),
);
const OUT = args.out ?? "hartford-menus.json";
const LIMIT = Number(args.limit ?? 1000);
// Hartford, CT city limits (plus a small margin).
// Another area can be passed as --bbox s,w,n,e --area "Name" (used for areas people open the app in).
const BBOX = args.bbox
  ? Object.fromEntries(["s", "w", "n", "e"].map((k, i) => [k, Number(args.bbox.split(",")[i])]))
  : { s: 41.7237, w: -72.7186, n: 41.8074, e: -72.651 };
const AREA_NAME = args.area ?? "Hartford, CT";
const HOME = !args.bbox;

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const slug = (s) => s.toLowerCase().normalize("NFKD").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 60);
const norm = (s) => s.toLowerCase().replace(/['’`]/g, "").replace(/\b(the|restaurant|cafe|café|bar|grill|and|&|llc|inc|hartford|ct)\b/g, "").replace(/[^a-z0-9]+/g, "");

function km(a, b) {
  const R = 6371, d = Math.PI / 180;
  const x = Math.sin(((b.lat - a.lat) * d) / 2) ** 2 + Math.cos(a.lat * d) * Math.cos(b.lat * d) * Math.sin(((b.lng - a.lng) * d) / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}

// ---------- places ----------

async function overpass() {
  const q = `[out:json][timeout:90];
(nwr["amenity"~"^(restaurant|fast_food|cafe|ice_cream|food_court|pub)$"]["name"](${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e});
 nwr["amenity"="bar"]["name"]["cuisine"](${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e});
 nwr["shop"~"^(bakery|deli|pastry|coffee)$"]["name"](${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e}););
out center tags;`;
  const hosts = ["https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://maps.mail.ru/osm/tools/overpass/api/interpreter"];
  // The public servers are often busy; go round them twice with a pause before giving up.
  for (const [i, host] of [...hosts, ...hosts].entries()) {
    if (i === hosts.length) await new Promise((r) => setTimeout(r, 60000));
    try {
      const r = await fetch(host, { method: "POST", body: new URLSearchParams({ data: q }), headers: { "User-Agent": "Dishcover menu check (godishcover.com)" } });
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const j = await r.json();
      log(`overpass ${host}: ${j.elements.length} elements`);
      return j.elements;
    } catch (e) {
      log(`overpass ${host} failed: ${e.message}`);
    }
  }
  return [];
}

function fromOsm(el) {
  const t = el.tags ?? {};
  const street = [t["addr:housenumber"], t["addr:street"]].filter(Boolean).join(" ");
  return {
    id: `osm-${el.type[0]}${el.id}`,
    name: t.name,
    address: street ? [street, t["addr:city"] ?? (HOME ? "Hartford" : ""), t["addr:state"] ?? (HOME ? "CT" : "")].filter(Boolean).join(", ") : "",
    lat: el.lat ?? el.center?.lat,
    lng: el.lon ?? el.center?.lon,
    cuisine: [t.cuisine, t.amenity === "cafe" ? "coffee_shop" : "", t.shop ?? ""].filter(Boolean).join(";"),
    website: t.website ?? t["contact:website"] ?? t.url ?? "",
    brand: t.brand ?? "",
  };
}

async function loadPlaces() {
  const osm = (await overpass()).map(fromOsm).filter((p) => p.name && p.lat && p.lng);
  const extra = HOME ? JSON.parse(fs.readFileSync(new URL("./extra-places.json", import.meta.url))) : [];
  const places = [...osm];
  for (const x of extra) {
    const match = places.find((p) => (norm(p.name).includes(norm(x.name)) || norm(x.name).includes(norm(p.name))) && km(p, x) < 0.4);
    if (match) {
      match.website ||= x.website;
      match.cuisine ||= x.cuisine;
      match.address ||= x.address;
      if (x.website && /menu/.test(x.website)) match.website = x.website;
    } else places.push({ id: `x-${slug(x.name)}`, brand: "", ...x });
  }
  const links = JSON.parse(fs.readFileSync(new URL("./menu-links.json", import.meta.url)));
  let linked = 0;
  for (const p of places) {
    const url = links[p.name] ?? Object.entries(links).find(([k]) => !k.startsWith("_") && norm(k) === norm(p.name))?.[1];
    if (url) { p.website = url; linked++; }
  }
  if (osm.length === 0) throw new Error("OpenStreetMap returned no places; not publishing a partial run");
  log(`places: ${osm.length} from OpenStreetMap, ${places.length} after adding the extra list, ${linked} with a hand-found menu link`);
  return places;
}

// ---------- chains ----------

const CHAINS = JSON.parse(fs.readFileSync(new URL("./chains.json", import.meta.url)));
/** A chain's menu as items, keeping its sections. */
function chainItems(chain) {
  const menu = CHAINS[chain];
  if (Array.isArray(menu)) return menu.map((name) => ({ name, description: "", price: "", section: "" }));
  return Object.entries(menu).flatMap(([section, names]) => names.map((name) => ({ name, description: "", price: "", section })));
}

function chainFor(p) {
  const n = norm(p.brand || p.name);
  return Object.keys(CHAINS).find((k) => !k.startsWith("_") && n.length >= 3 && (n === norm(k) || n.startsWith(norm(k))));
}

// ---------- menu pages ----------

const ORDER_HOSTS = /toasttab\.com|square\.site|menufy\.com|clover\.com|chownow\.com|slicelife\.com|order\.online|popmenu|bentobox|spoton|menupages|grubhub\.com\/restaurant|beyondmenu|allmenus|menulog|olo\.com|tryotter|owner\.com|gloriafood|foodbooking|order\.toasttab/;
const SKIP_SITES = /facebook\.com|instagram\.com|twitter\.com|x\.com\/|yelp\.com|tripadvisor|hilton\.com|marriott\.com|hartford\.edu|google\.com/;

async function pageText(page) {
  let text = "";
  for (const frame of page.frames()) {
    try {
      text += "\n" + (await frame.evaluate(() => document.body?.innerText ?? ""));
    } catch {}
  }
  return text;
}

// Menus without prices are often laid out as a heading per dish with its description under it
// (WordPress/Elementor sites). Read those straight from the page's headings.
async function headingItems(page) {
  try {
    return await page.evaluate(() => {
      const seq = [];
      const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
      let node;
      while ((node = walker.nextNode())) {
        const el = node.parentElement;
        if (!el || /^(SCRIPT|STYLE|NOSCRIPT|BUTTON)$/.test(el.tagName) || el.closest("nav,header,footer")) continue;
        const t = node.textContent.replace(/\s+/g, " ").trim();
        if (!t) continue;
        const h = el.closest("h1,h2,h3,h4,h5,h6");
        const last = seq[seq.length - 1];
        if (h) {
          if (last && last.h === h) last.name += " " + t;
          else seq.push({ h, level: Number(h.tagName[1]), name: t, description: "" });
        } else if (last && last.description.length < 240) last.description = (last.description + " " + t).trim();
      }
      const counts = {};
      seq.forEach((x) => (counts[x.level] = (counts[x.level] ?? 0) + 1));
      const level = Number(Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0]);
      if (!level || counts[level] < 8) return [];
      const out = [];
      let section = "";
      for (const x of seq) {
        if (x.level < level) { section = x.name; continue; }
        if (x.level !== level) continue;
        const m = x.description.match(/^\$?\s?(\d{1,3}(?:\.\d{2})?)\b\s*(.*)$/);
        out.push({ name: x.name.replace(/^[*\s]+/, ""), description: m ? m[2] : x.description, price: m ? "$" + m[1] : "", section });
      }
      return out;
    });
  } catch { return []; }
}

async function scrollToEnd(page) {
  try {
    await page.evaluate(async () => {
      for (let i = 0; i < 12; i++) {
        window.scrollBy(0, window.innerHeight);
        await new Promise((r) => setTimeout(r, 250));
      }
    });
  } catch {}
}

function pdfText(buf) {
  fs.writeFileSync("/tmp/menu.pdf", buf);
  try {
    return execFileSync("pdftotext", ["-layout", "/tmp/menu.pdf", "-"], { maxBuffer: 20e6 }).toString();
  } catch {
    return "";
  }
}

async function readSite(ctx, url) {
  const page = await ctx.newPage();
  const texts = [];
  const visited = new Set();
  const sources = [];
  const headed = [];
  try {
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30000 });
    await page.waitForTimeout(2500);
    await scrollToEnd(page);
    texts.push(await pageText(page));
    headed.push(...(await headingItems(page)));
    sources.push(page.url());
    visited.add(page.url());
    const home = new URL(page.url());
    const links = await page.$$eval("a[href]", (as) => as.map((a) => ({ href: a.href, text: (a.innerText || a.getAttribute("aria-label") || "").trim() })));
    const candidates = links
      .filter((l) => /^https?:/.test(l.href) && !SKIP_SITES.test(l.href))
      .filter((l) => {
        const h = new URL(l.href);
        const same = h.hostname.replace(/^www\./, "") === home.hostname.replace(/^www\./, "");
        const menuish = /menu|food|dinner|lunch|brunch|breakfast|eat|drinks|order/i.test(l.text + " " + h.pathname);
        return menuish && (same || ORDER_HOSTS.test(h.hostname + h.pathname));
      })
      .sort((a, b) => Number(/menu/i.test(b.text + b.href)) - Number(/menu/i.test(a.text + a.href)));
    const unique = [...new Map(candidates.map((c) => [c.href.split("#")[0], c])).values()].slice(0, 6);
    for (const c of unique) {
      const href = c.href.split("#")[0];
      if (visited.has(href)) continue;
      visited.add(href);
      try {
        if (/\.pdf($|\?)/i.test(href)) {
          const r = await ctx.request.get(href, { timeout: 30000 });
          if (r.ok()) {
            texts.push(pdfText(await r.body()));
            sources.push(href);
          }
          continue;
        }
        await page.goto(href, { waitUntil: "domcontentloaded", timeout: 30000 });
        await page.waitForTimeout(2500);
        await scrollToEnd(page);
        texts.push(await pageText(page));
        headed.push(...(await headingItems(page)));
        sources.push(href);
      } catch (e) {
        log(`  ${href}: ${e.message.split("\n")[0]}`);
      }
    }
  } catch (e) {
    log(`  ${url}: ${e.message.split("\n")[0]}`);
  } finally {
    await page.close();
  }
  return { texts, sources, headed };
}

// ---------- Gemini (optional) ----------

async function gemini(text) {
  const key = process.env.GEMINI_API_KEY;
  if (!key) return null;
  const body = {
    contents: [{ parts: [{ text: "List every food and drink item on this restaurant menu. Use the names and prices exactly as printed; leave price empty when none is printed. Skip headings, add-ons, hours and anything that isn't a dish or drink.\n\n" + text.slice(0, 60000) }] }],
    generationConfig: {
      responseMimeType: "application/json",
      responseSchema: {
        type: "ARRAY",
        items: { type: "OBJECT", properties: { name: { type: "STRING" }, description: { type: "STRING" }, price: { type: "STRING" }, section: { type: "STRING" } }, required: ["name"] },
      },
    },
  };
  try {
    const model = process.env.GEMINI_MODEL || "gemini-3.5-flash-lite";
    let j;
    for (let attempt = 0; attempt < 4; attempt++) {
      const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      j = await r.json();
      if (r.status !== 429) break;
      await new Promise((res) => setTimeout(res, 20000 * (attempt + 1))); // free tier: a few requests a minute
    }
    if (j.error || !j.candidates?.[0]?.content) { log(`  gemini: ${j.error?.message ?? j.candidates?.[0]?.finishReason ?? j.promptFeedback?.blockReason ?? "no answer"}`); return null; }
    const items = JSON.parse(j.candidates?.[0]?.content?.parts?.[0]?.text ?? "[]");
    return items.filter((i) => i.name).map((i) => ({ name: i.name.trim(), description: (i.description ?? "").trim().slice(0, 240), price: (i.price ?? "").trim(), section: i.section ?? "" }));
  } catch (e) {
    log(`  gemini failed: ${e.message}`);
    return null;
  }
}

// ---------- main ----------

async function main() {
  const places = (await loadPlaces()).slice(0, LIMIT);
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36",
    viewport: { width: 412, height: 915 },
  });
  const dishes = [];
  let i = 0;
  const queue = [...places];
  async function worker() {
    while (queue.length) {
      const p = queue.shift();
      const n = ++i;
      const chain = chainFor(p);
      let items = [];
      if (chain) {
        items = chainItems(chain);
        p.menuSource = "chain";
        p.chain = chain;
      } else if (p.website && !SKIP_SITES.test(p.website)) {
        const { texts, sources, headed } = await readSite(ctx, p.website);
        const all = texts.join("\n");
        // Free readers first; Gemini (paid) only when they find next to nothing.
        const seen = new Set();
        items = texts.flatMap(parseMenuText).filter((it) => !seen.has(it.name.toLowerCase()) && seen.add(it.name.toLowerCase()));
        const seenH = new Set();
        const byHeading = headed.filter((it) => !seenH.has(it.name.toLowerCase()) && seenH.add(it.name.toLowerCase()));
        if (byHeading.length > Math.max(items.length, 7)) items = byHeading;
        if (items.length < 5 && all.length > 200) {
          const ai = (await gemini(all)) ?? [];
          if (ai.length > items.length) items = ai;
        }
        p.menuSource = items.length ? sources.find((s) => /menu/i.test(s)) ?? sources[0] : "";
      }
      items = items.map(cleanItem).filter(Boolean);
      for (const it of items.slice(0, 200)) {
        dishes.push({
          id: `${p.id}-${slug(it.name)}`,
          placeId: p.id,
          name: it.name,
          description: it.description ?? "",
          price: it.price ?? "",
          section: it.section ?? "",
          categories: categorize(it.name, it.description, p.cuisine + " " + p.name, it.section),
          tags: tagsFor(it.name, it.description, p.cuisine),
          allergens: allergensFor(it.name, it.description),
        });
      }
      p.dishCount = items.length;
      log(`[${n}/${places.length}] ${p.name}: ${items.length} items${chain ? " (chain)" : ""}`);
    }
  }
  await Promise.all([worker(), worker(), worker(), worker()]);
  await browser.close();

  // Ids must be unique even when a menu repeats a name in two sections.
  const ids = new Set();
  const unique = dishes.filter((d) => !ids.has(d.id) && ids.add(d.id));
  const seed = { area: AREA_NAME, generatedAt: new Date().toISOString(), places, dishes: unique };
  fs.writeFileSync(OUT, JSON.stringify(seed));
  log(`wrote ${OUT}: ${places.length} places, ${places.filter((p) => p.dishCount).length} with menus, ${unique.length} dishes`);

  // What changed since the last run, for the "New on menus" feed.
  if (args.prev && fs.existsSync(args.prev)) {
    const prev = JSON.parse(fs.readFileSync(args.prev));
    const before = new Set(prev.dishes.map((d) => d.id));
    const prevPlaces = new Set(prev.places.map((p) => p.id));
    const changes = places
      .map((p) => ({
        placeId: p.id, placeName: p.name, newPlace: !prevPlaces.has(p.id),
        added: unique.filter((d) => d.placeId === p.id && !before.has(d.id)).map((d) => d.id),
      }))
      .filter((c) => c.added.length > 0 || c.newPlace);
    fs.writeFileSync("menu-changes.json", JSON.stringify(changes, null, 1));
    log(`changes since last run: ${changes.length} places, ${changes.reduce((a, c) => a + c.added.length, 0)} new dishes`);
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
