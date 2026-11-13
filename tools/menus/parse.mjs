// Pulls menu items out of the plain text of a restaurant's menu page, without an AI model.
// Handles the common layouts: "Name ..... $12", "Name $12" with a description below, and
// "Name / description / $12" stacked on separate lines.

const PRICE_ONLY = /^\$?\s?(\d{1,3}(?:[.,]\d{2})?)(?:\s*[\/|]\s*\$?\d{1,3}(?:[.,]\d{2})?)*\s*\+?$/;
const INLINE = /^(.{2,80}?)\s*(?:\.{2,}|…+|\s-\s|\s–\s|\s\|\s|\s{2,}|\s)\s*\$\s?(\d{1,3}(?:[.,]\d{2})?)(?:\s*[\/|]\s*\$?\d{1,3}(?:[.,]\d{2})?)*\s*\+?$/;
const INLINE_NO_DOLLAR = /^(.{2,60}?)\s*(?:\.{2,}|…+|\s-\s|\s–\s|\s\|\s|\s{2,})\s*(\d{1,3}[.,]\d{2})\s*$/;
const JUNK = /copyright|©|all rights|reserved|\bhours\b|\bcall\b|phone|address|order (now|online|here)|delivery|gift ?card|catering|follow us|subscribe|cookie|privacy|terms|sign ?in|log ?in|cart|checkout|newsletter|reservations?|\bjobs\b|careers|directions|^menu$|^menus$|^home$|^about|^contact|gratuity|\btax\b|surcharge|consuming raw|undercooked|please (inform|notify|let)|allergies|prices (are )?subject|^add\b|^extra\b|^substitute|^sub\b|^choice of|^choose|^served with|^comes with|^includes?\b|^with\b|^w\/|^\+|^side of|^upgrade|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\b(am|pm)\b|http|www\.|\.com|@/i;

const clean = (s) => s.replace(/\s+/g, " ").replace(/^[•·*\-–—\s]+/, "").replace(/[\s:–—-]+$/, "").trim();

function looksLikeName(s) {
  if (s.length < 3 || s.length > 64) return false;
  if (JUNK.test(s)) return false;
  if (!/[a-zA-Z]{2}/.test(s)) return false;
  if (s.split(" ").length > 10) return false;
  if (/[.!?]$/.test(s) && s.split(" ").length > 5) return false; // a sentence, not a dish name
  return true;
}

function looksLikeHeading(s) {
  return s.length <= 32 && s === s.toUpperCase() && /[A-Z]{3}/.test(s) && !/\d/.test(s);
}

const titleCase = (s) =>
  s === s.toUpperCase() ? s.toLowerCase().replace(/\b([a-z])/g, (m) => m.toUpperCase()).replace(/\b(And|Or|Of|With|The|In|On|A)\b/g, (m) => m.toLowerCase()).replace(/^./, (m) => m.toUpperCase()) : s;

const fmtPrice = (p) => {
  const n = Number(p.replace(",", "."));
  if (!isFinite(n) || n <= 0 || n > 300) return null;
  return "$" + (Number.isInteger(n) ? n : n.toFixed(2));
};

export function parseMenuText(text) {
  const lines = text.split(/\r?\n/).map(clean).filter((l) => l.length > 0);
  const items = [];
  let buffer = [];
  let section = "";
  let last = null; // the item a following description line belongs to

  const push = (name, price, description, sec) => {
    name = titleCase(clean(name));
    if (!looksLikeName(name)) return null;
    const it = { name, price: price ?? "", description: clean(description ?? "").slice(0, 240), section: sec };
    items.push(it);
    return it;
  };

  for (const line of lines) {
    let m;
    if (PRICE_ONLY.test(line)) {
      const price = fmtPrice(line.match(PRICE_ONLY)[1]);
      if (price && buffer.length > 0 && buffer.length <= 4) {
        const [name, ...rest] = buffer;
        if (last && !last.price && last.name === name) last.price = price;
        else last = push(name, price, rest.join(" "), section);
      }
      buffer = [];
      continue;
    }
    if ((m = line.match(INLINE)) || (m = line.match(INLINE_NO_DOLLAR))) {
      const price = fmtPrice(m[2]);
      if (price) {
        last = push(m[1], price, "", section);
        buffer = [];
        continue;
      }
    }
    if (looksLikeHeading(line)) {
      section = titleCase(line);
      buffer = [];
      last = null;
      continue;
    }
    // A longer line straight after an inline-priced item is that item's description.
    if (last && last.price && buffer.length === 0 && line.length > 25 && !last.description && /[a-z]/.test(line)) {
      last.description = line.slice(0, 240);
      continue;
    }
    buffer.push(line);
    if (buffer.length > 6) buffer.shift();
  }

  // Keep one of each dish, and give up on pages that clearly aren't menus.
  const seen = new Set();
  const out = items.filter((it) => {
    const k = it.name.toLowerCase();
    if (seen.has(k)) return false;
    seen.add(k);
    return true;
  });
  return out.length >= 5 ? out.slice(0, 200) : [];
}
