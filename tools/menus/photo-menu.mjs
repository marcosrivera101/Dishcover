// Reads a restaurant's menu from photos of it (for places whose menu is only posted as pictures)
// and appends the dishes to a town sheet in the hand-gathered format.
//
//   PLACE="American & Mexican Diner" ADDRESS="243 Zion Street, Hartford, CT" CUISINE=mexican \
//   SOURCE=https://... URLS="$(cat urls.txt)" GEMINI_API_KEY=... node photo-menu.mjs handoffs/hartford.jsonl
//
// Photos that aren't menus (food, the room) come back empty and are skipped.

import fs from "node:fs";

const [out] = process.argv.slice(2);
const { PLACE, ADDRESS = "", CUISINE = "", SOURCE = "", URLS = "", GEMINI_API_KEY: KEY } = process.env;
const MODEL = process.env.GEMINI_MODEL || "gemini-3.5-flash-lite";
if (!out || !PLACE || !KEY) { console.error("Needs an output sheet, PLACE and GEMINI_API_KEY."); process.exit(1); }
const urls = URLS.split(/\s+/).filter((u) => /^https?:\/\//.test(u));

const prompt = "Only if this photo shows a printed or written restaurant menu (text listing dishes), list every food and drink item " +
  "on it with the price exactly as printed (empty when none) and the menu heading it sits under as section. Keep descriptions short. " +
  "If it is a photo of food, people or the room, or the text is unreadable, return an empty list. Never name dishes from how food looks.";
const schema = {
  type: "ARRAY",
  items: { type: "OBJECT", properties: { name: { type: "STRING" }, description: { type: "STRING" }, price: { type: "STRING" }, section: { type: "STRING" } }, required: ["name"] },
};

async function read(url) {
  const img = await fetch(url, { headers: { "User-Agent": "Mozilla/5.0" } });
  if (!img.ok) throw new Error(`photo HTTP ${img.status}`);
  const mime = img.headers.get("content-type")?.split(";")[0] || "image/jpeg";
  const data = Buffer.from(await img.arrayBuffer()).toString("base64");
  const body = { contents: [{ parts: [{ inline_data: { mime_type: mime, data } }, { text: prompt }] }], generationConfig: { responseMimeType: "application/json", responseSchema: schema } };
  for (let attempt = 0; attempt < 4; attempt++) {
    const r = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${KEY}`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
    });
    const j = await r.json();
    if (r.status === 429) { await new Promise((res) => setTimeout(res, 20000 * (attempt + 1))); continue; }
    if (j.error) throw new Error(j.error.message);
    return JSON.parse(j.candidates?.[0]?.content?.parts?.[0]?.text ?? "[]");
  }
  throw new Error("Gemini kept saying slow down");
}

const seen = new Set();
const rows = [];
for (const [i, url] of urls.entries()) {
  try {
    const items = await read(url);
    let added = 0;
    for (const it of items) {
      const name = (it.name ?? "").trim();
      if (!name || seen.has(name.toLowerCase())) continue;
      seen.add(name.toLowerCase());
      rows.push([PLACE, ADDRESS, "", CUISINE, (it.section ?? "").trim(), name, (it.description ?? "").trim().slice(0, 240), (it.price ?? "").trim(), "", SOURCE || url]);
      added++;
    }
    console.log(`[${i + 1}/${urls.length}] ${added} items`);
  } catch (e) {
    console.log(`[${i + 1}/${urls.length}] skipped: ${e.message}`);
  }
  await new Promise((res) => setTimeout(res, 4000)); // free tier pacing
}
if (!rows.length) { console.error("No menu items found in the photos."); process.exit(1); }

// Replace this place's earlier rows, keep everyone else's.
const lines = fs.existsSync(out) ? fs.readFileSync(out, "utf8").split("\n").filter(Boolean) : [];
const header = lines.length ? lines[0] : JSON.stringify(["Restaurant", "Address", "Phone", "Cuisine/Type", "Category", "Dish", "Description", "Price", "Photo URL", "Source URL"]);
const others = lines.slice(1).filter((l) => { try { return JSON.parse(l)[0] !== PLACE; } catch { return true; } });
fs.writeFileSync(out, [header, ...others, ...rows.map((r) => JSON.stringify(r))].join("\n") + "\n");
console.log(`${PLACE}: ${rows.length} dishes written to ${out}`);
