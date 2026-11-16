// Dishcover on the web, for people without Android (iPhone, computers).
// Same Firebase project and rules as the app: browse restaurants and dishes near you,
// rate dishes, and read the feed.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import { getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, onAuthStateChanged, signOut }
  from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore, collection, query, where, getDocs, getDoc, doc, orderBy, limit,
  runTransaction, increment, setDoc,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);
const ANDROID = "https://github.com/marcosrivera101/Dishcover/releases/tag/test-builds";
const HARTFORD = { lat: 41.7658, lng: -72.6734 };

// ---------- food words ----------

const DISHES = ["burger", "pizza", "tacos", "burrito", "sandwich", "wings", "chicken", "bbq", "steak", "seafood", "sushi", "ramen", "noodles", "pho", "dumplings", "rice bowl", "salad", "soup", "pasta", "hot dogs", "breakfast", "bagels", "fries and sides"];
const CUISINES = ["american", "italian", "mexican", "chinese", "japanese", "korean", "thai", "vietnamese", "indian", "mediterranean", "middle eastern", "greek", "caribbean", "jamaican", "puerto rican", "latin", "soul food", "cajun", "african", "brazilian", "peruvian", "french", "halal", "vegan"];
const SWEETS = ["dessert", "bakery", "donuts", "ice cream", "coffee", "bubble tea", "smoothies and juice"];
const CATS = [...DISHES, ...CUISINES, ...SWEETS];
const label = (c) => c === "bbq" ? "BBQ" : c.replace(/(^|\s)\S/g, (m) => m.toUpperCase());
const ART = { burger: "🍔", pizza: "🍕", tacos: "🌮", burrito: "🌯", sandwich: "🥪", wings: "🍗", chicken: "🍗", bbq: "🍖", steak: "🥩", seafood: "🦐", sushi: "🍣", ramen: "🍜", noodles: "🍝", pho: "🍜", dumplings: "🥟", "rice bowl": "🍚", salad: "🥗", soup: "🍲", pasta: "🍝", "hot dogs": "🌭", breakfast: "🍳", bagels: "🥯", "fries and sides": "🍟", american: "🍔", italian: "🍝", mexican: "🌮", chinese: "🥡", japanese: "🍱", korean: "🍲", thai: "🍛", vietnamese: "🍜", indian: "🍛", mediterranean: "🧆", "middle eastern": "🧆", greek: "🥙", caribbean: "🍛", jamaican: "🍛", "puerto rican": "🍛", latin: "🫓", "soul food": "🍗", cajun: "🦞", african: "🍛", brazilian: "🥩", peruvian: "🍋", french: "🥐", halal: "🥙", vegan: "🥦", dessert: "🍰", bakery: "🥐", donuts: "🍩", "ice cream": "🍨", coffee: "☕", "bubble tea": "🧋", "smoothies and juice": "🥤" };
const WORDS = [[/burger/, "🍔"], [/pizza|calzone/, "🍕"], [/taco/, "🌮"], [/burrito|wrap/, "🌯"], [/sushi|roll\b|nigiri/, "🍣"], [/ramen|pho\b/, "🍜"], [/wing/, "🍗"], [/fries/, "🍟"], [/salad/, "🥗"], [/soup|chowder|stew/, "🍲"], [/shrimp/, "🍤"], [/lobster|crab/, "🦞"], [/salmon|fish|tuna/, "🐟"], [/steak|ribeye/, "🥩"], [/\begg|omelet/, "🍳"], [/pancake|waffle|french toast/, "🥞"], [/bagel/, "🥯"], [/cake|tiramisu/, "🍰"], [/cookie/, "🍪"], [/donut/, "🍩"], [/ice cream|gelato|sundae/, "🍨"], [/pie\b/, "🥧"], [/coffee|latte|espresso|cappuccino/, "☕"], [/boba|bubble/, "🧋"], [/smoothie|shake|juice/, "🥤"], [/dumpling|gyoza/, "🥟"], [/curry/, "🍛"], [/rice/, "🍚"], [/pasta|spaghetti|penne|lasagna|ravioli/, "🍝"], [/sandwich|sub\b|panini|melt/, "🥪"], [/falafel|gyro|shawarma/, "🥙"], [/chicken|tender/, "🍗"]];
const dishArt = (d) => WORDS.find(([r]) => r.test(d.name.toLowerCase()))?.[1] ?? (d.categories ?? []).map((c) => ART[c]).find(Boolean) ?? "🍽️";
const placeArt = (p, cats = []) => [...String(p.cuisine ?? "").toLowerCase().split(/[;,]/).map((s) => s.trim().replace(/_/g, " ")), ...cats].map((c) => ART[c] ?? ART[c.replace(/ shop$/, "")]).find(Boolean) ?? "🍽️";

// ---------- places ----------

const B32 = "0123456789bcdefghjkmnpqrstuvwxyz";
function geohash(lat, lng, precision) {
  let la = [-90, 90], lo = [-180, 180], h = "", bit = 0, ch = 0, even = true;
  while (h.length < precision) {
    const r = even ? lo : la, v = even ? lng : lat, m = (r[0] + r[1]) / 2;
    if (v >= m) { ch = (ch << 1) | 1; r[0] = m; } else { ch <<= 1; r[1] = m; }
    even = !even;
    if (++bit === 5) { h += B32[ch]; bit = 0; ch = 0; }
  }
  return h;
}
function cellsAround(p, km) {
  const dLat = 180 / 1024, dLng = 360 / 1024, cells = new Set();
  const ls = Math.min(6, Math.max(1, Math.ceil(km / 111 / dLat)));
  const gs = Math.min(6, Math.max(1, Math.ceil(km / Math.max(10, 111 * Math.cos(p.lat * Math.PI / 180)) / dLng)));
  for (let i = -ls; i <= ls; i++) for (let j = -gs; j <= gs; j++) cells.add(geohash(p.lat + i * dLat, p.lng + j * dLng, 4));
  return [...cells];
}
function feedCells(p) {
  const cells = new Set();
  for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) cells.add(geohash(p.lat + i * 180 / 128, p.lng + j * 360 / 256, 3));
  return [...cells];
}
function km(a, b) {
  const R = 6371, d = Math.PI / 180;
  const x = Math.sin((b.lat - a.lat) * d / 2) ** 2 + Math.cos(a.lat * d) * Math.cos(b.lat * d) * Math.sin((b.lng - a.lng) * d / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}
const miles = (k) => k == null ? "" : `${(k * 0.621371).toFixed(k < 16 ? 1 : 0)} mi`;
const avg = (x) => x.ratingCount > 0 ? x.ratingSum / x.ratingCount : 0;
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// ---------- state ----------

const S = { user: null, here: null, real: false, places: [], dishes: [], loaded: false, mine: {}, sort: "close", cat: "", q: "" };

function toast(msg) {
  const t = document.getElementById("toast");
  t.textContent = msg; t.classList.add("show");
  clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove("show"), 2200);
}

function locate() {
  return new Promise((res) => {
    if (!navigator.geolocation) return res(HARTFORD);
    navigator.geolocation.getCurrentPosition(
      (p) => { S.real = true; res({ lat: p.coords.latitude, lng: p.coords.longitude }); },
      () => res(HARTFORD), { enableHighAccuracy: false, timeout: 8000, maximumAge: 600000 });
  });
}

async function load() {
  S.here = await locate();
  const cells = cellsAround(S.here, 16);
  const ps = [], ds = new Map();
  for (let i = 0; i < cells.length; i += 30) {
    (await getDocs(query(collection(db, "places"), where("gh4", "in", cells.slice(i, i + 30))))).forEach((d) => ps.push({ id: d.id, ...d.data() }));
  }
  // A whole area can hold tens of thousands of dishes: load the menus of the nearest places that
  // have one (up to a budget) plus every rated dish nearby. Other menus load when a place is opened.
  const near = ps.map((p) => [p, km(S.here, p)]).filter(([, k]) => k <= 16).sort((a, b) => a[1] - b[1]).map(([p]) => p);
  const withMenus = near.filter((p) => p.dishCount > 0);
  const pick = (withMenus.length >= 10 ? withMenus : near).map((p) => p.id);
  for (let i = 0; i < pick.length && ds.size < 4000; i += 30) {
    (await getDocs(query(collection(db, "dishes"), where("placeId", "in", pick.slice(i, i + 30))))).forEach((d) => ds.set(d.id, { id: d.id, ...d.data() }));
  }
  for (let i = 0; i < cells.length; i += 30) {
    const r = await getDocs(query(collection(db, "dishes"), where("gh4", "in", cells.slice(i, i + 30)), where("ratingCount", ">", 0), limit(500))).catch(() => null);
    r?.forEach((d) => ds.set(d.id, { id: d.id, ...d.data() }));
  }
  S.places = ps; S.dishes = [...ds.values()]; S.loaded = true;
  // Same as the app: opening Dishcover somewhere new asks for that area's menus.
  if (S.real) {
    const cell = geohash(S.here.lat, S.here.lng, 5);
    const ref = doc(db, "areas", cell);
    getDoc(ref).then((d) => d.exists() || setDoc(ref, { status: "waiting", requestedAt: Date.now(), requestedBy: S.user.uid })).catch(() => {});
  }
  const mine = await getDocs(query(collection(db, "ratings"), where("userId", "==", S.user.uid))).catch(() => null);
  mine?.forEach((d) => { S.mine[d.data().dishId] = d.data(); });
}

// ---------- views ----------

const view = document.getElementById("view");
const skeleton = () => `<div class="list">${'<div class="skeleton"></div>'.repeat(5)}</div>`;
const chips = (items, on, attr) => `<div class="chips">${items.map(([v, t]) => `<button class="chip ${v === on ? "on" : ""}" data-${attr}="${esc(v)}">${esc(t)}</button>`).join("")}</div>`;
const thumb = (art, logo) => `<div class="thumb">${logo ? `<img src="${esc(logo)}" alt="" onerror="this.replaceWith(document.createTextNode('${art}'))">` : art}</div>`;
function logoFor(p) {
  if (!p.website) return "";
  try { const h = new URL(p.website.startsWith("http") ? p.website : "https://" + p.website).hostname.replace(/^www\./, ""); return `https://www.google.com/s2/favicons?domain=${h}&sz=128`; } catch { return ""; }
}

function signedOut() {
  document.getElementById("tabs").hidden = true;
  document.getElementById("signout").hidden = true;
  view.innerHTML = `<section class="hero">
    <img src="icon-512.png" alt="">
    <h1>Find the best dish, not just the best restaurant.</h1>
    <p>Dishcover rates food dish by dish, with your ratings and everyone else's, so you know exactly what to order.</p>
    <button class="btn block" id="google">Continue with Google</button>
    <a class="get" href="${ANDROID}">On Android? Get the app</a>
  </section>`;
  document.getElementById("google").onclick = async () => {
    const provider = new GoogleAuthProvider();
    try { await signInWithPopup(auth, provider); } catch (e) {
      if (String(e.code).includes("popup")) await signInWithRedirect(auth, provider); else toast("Couldn't sign in. Try again.");
    }
  };
}

function restaurants() {
  if (!S.loaded) { view.innerHTML = skeleton(); return; }
  const byPlace = {};
  for (const d of S.dishes) (byPlace[d.placeId] ??= []).push(d);
  const q = S.q.trim().toLowerCase();
  let list = S.places
    .filter((p) => !S.cat || (byPlace[p.id] ?? []).some((d) => (d.categories ?? []).includes(S.cat)) || String(p.cuisine).toLowerCase().includes(S.cat))
    .filter((p) => !q || p.name.toLowerCase().includes(q) || (byPlace[p.id] ?? []).some((d) => d.name.toLowerCase().includes(q)))
    .map((p) => ({ p, k: km(S.here, p), n: (byPlace[p.id] ?? []).length || p.dishCount || 0 }));
  const by = { close: (a, b) => a.k - b.k, top: (a, b) => avg(b.p) - avg(a.p) || a.k - b.k, menu: (a, b) => b.n - a.n || a.k - b.k, az: (a, b) => a.p.name.localeCompare(b.p.name) };
  list.sort(by[S.sort]);
  view.innerHTML = `<h1>Restaurants near you</h1>
    ${S.real ? "" : `<p class="muted">Showing Hartford. Allow location to see places near you.</p>`}
    <input type="search" id="q" placeholder="Search restaurants or dishes" value="${esc(S.q)}">
    <div style="height:10px"></div>
    ${chips([["close", "Closest"], ["top", "Top rated"], ["menu", "Biggest menu"], ["az", "A to Z"]], S.sort, "sort")}
    ${chips([["", "All food"], ...CATS.map((c) => [c, label(c)])], S.cat, "cat")}
    <p class="muted">${list.length} restaurants</p>
    <div class="list">${list.slice(0, 150).map(({ p, k, n }) => `
      <a class="card row" href="#/place/${encodeURIComponent(p.id)}">
        ${thumb(placeArt(p, (byPlace[p.id] ?? []).flatMap((d) => d.categories ?? [])), logoFor(p))}
        <div class="grow"><div class="title">${esc(p.name)}</div>
        <div class="muted">${[miles(k), p.ratingCount > 0 ? `★ ${avg(p).toFixed(1)} (${p.ratingCount})` : "", n ? `${n} dishes` : "No menu yet"].filter(Boolean).join(" · ")}</div></div>
      </a>`).join("") || `<div class="empty">No restaurants match. We're adding menus around you now, so check back soon.</div>`}</div>`;
  const qi = document.getElementById("q");
  qi.oninput = () => { S.q = qi.value; clearTimeout(restaurants.t); restaurants.t = setTimeout(() => { restaurants(); const n = document.getElementById("q"); n.focus(); n.setSelectionRange(n.value.length, n.value.length); }, 250); };
  view.querySelectorAll("[data-sort]").forEach((b) => b.onclick = () => { S.sort = b.dataset.sort; restaurants(); });
  view.querySelectorAll("[data-cat]").forEach((b) => b.onclick = () => { S.cat = b.dataset.cat; restaurants(); });
}

function dishRow(d, showPlace = true) {
  const mine = S.mine[d.id]?.stars;
  return `<a class="card row" href="#/dish/${encodeURIComponent(d.id)}">
    ${thumb(dishArt(d))}
    <div class="grow"><div class="title">${esc(d.name)}</div>
    <div class="muted">${[showPlace ? esc(d.placeName) : "", d.price ? esc(d.price) : "", d.ratingCount > 0 ? `<span class="stars">★ ${avg(d).toFixed(1)}</span> (${d.ratingCount})` : "Not rated yet", mine ? `You: ${mine}★` : ""].filter(Boolean).join(" · ")}</div></div>
  </a>`;
}

function dishes() {
  if (!S.loaded) { view.innerHTML = skeleton(); return; }
  const list = S.dishes.filter((d) => !S.cat || (d.categories ?? []).includes(S.cat))
    .map((d) => ({ d, k: km(S.here, S.places.find((p) => p.id === d.placeId) ?? S.here) }))
    .sort((a, b) => avg(b.d) - avg(a.d) || b.d.ratingCount - a.d.ratingCount || a.k - b.k);
  view.innerHTML = `<h1>Best dishes near you</h1>
    ${chips([["", "All food"], ...CATS.map((c) => [c, label(c)])], S.cat, "cat")}
    <div class="list">${list.slice(0, 150).map(({ d }) => dishRow(d)).join("") || `<div class="empty">No dishes here yet.</div>`}</div>`;
  view.querySelectorAll("[data-cat]").forEach((b) => b.onclick = () => { S.cat = b.dataset.cat; dishes(); });
}

async function place(id) {
  view.innerHTML = skeleton();
  const snap = await getDoc(doc(db, "places", id));
  if (!snap.exists()) { view.innerHTML = `<div class="empty">That place isn't on Dishcover.</div>`; return; }
  const p = { id, ...snap.data() };
  const ds = (await getDocs(query(collection(db, "dishes"), where("placeId", "==", id)))).docs.map((d) => ({ id: d.id, ...d.data() }));
  ds.forEach((d) => { if (!S.dishes.some((x) => x.id === d.id)) S.dishes.push(d); });
  ds.sort((a, b) => avg(b) - avg(a) || b.ratingCount - a.ratingCount);
  const maps = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(p.name + " " + (p.address || ""))}`;
  view.innerHTML = `<div class="row" style="margin-top:8px">${thumb(placeArt(p), logoFor(p))}<div class="grow"><h1 style="margin:0">${esc(p.name)}</h1>
      <div class="muted">${[esc(p.address), S.here ? miles(km(S.here, p)) : ""].filter(Boolean).join(" · ")}</div></div></div>
    <p>${p.ratingCount > 0 ? `<span class="stars">★ ${avg(p).toFixed(1)}</span> from ${p.ratingCount} ratings · ` : ""}<a href="${maps}" target="_blank" rel="noopener" style="color:var(--wine);font-weight:600">Directions</a>${p.website ? ` · <a href="${esc(p.website)}" target="_blank" rel="noopener" style="color:var(--wine);font-weight:600">Website</a>` : ""}</p>
    <h2>Menu</h2>
    <div class="list">${ds.map((d) => dishRow(d, false)).join("") || `<div class="empty">No menu yet. Add one from the Android app with a photo of the menu.</div>`}</div>`;
}

async function dish(id) {
  view.innerHTML = skeleton();
  const snap = await getDoc(doc(db, "dishes", id));
  if (!snap.exists()) { view.innerHTML = `<div class="empty">That dish isn't on Dishcover.</div>`; return; }
  const d = { id, ...snap.data() };
  const reviews = (await getDocs(query(collection(db, "ratings"), where("dishId", "==", id), limit(50)))).docs.map((x) => x.data())
    .sort((a, b) => b.updatedAt - a.updatedAt);
  const mine = reviews.find((r) => r.userId === S.user.uid);
  let stars = mine?.stars ?? 0;
  view.innerHTML = `<div class="row" style="margin-top:8px">${thumb(dishArt(d))}<div class="grow"><h1 style="margin:0">${esc(d.name)}</h1>
      <a class="muted" href="#/place/${encodeURIComponent(d.placeId)}">${esc(d.placeName)}</a></div></div>
    ${d.description ? `<p>${esc(d.description)}</p>` : ""}
    <p>${(d.tags ?? []).map((t) => `<span class="tag">${esc(label(t))}</span>`).join("")}${d.price ? ` <b>${esc(d.price)}</b>` : ""}</p>
    ${d.allergens?.length ? `<p class="muted">Menu lists: ${esc(d.allergens.join(", "))}. Check with the restaurant.</p>` : ""}
    <p>${d.ratingCount > 0 ? `<span class="stars">★ ${avg(d).toFixed(1)}</span> from ${d.ratingCount} ratings` : "Be the first to rate it."}</p>
    <div class="card"><b>Your rating</b>
      <div class="rate" id="rate">${[1, 2, 3, 4, 5].map((n) => `<button data-n="${n}" aria-label="${n} stars" class="${n <= stars ? "on" : ""}">★</button>`).join("")}</div>
      <textarea id="txt" maxlength="500" placeholder="What did you think? (optional)">${esc(mine?.text ?? "")}</textarea>
      <div style="height:10px"></div><button class="btn block" id="save">Save rating</button></div>
    <h2>Reviews</h2>
    <div class="list">${reviews.filter((r) => r.text).map((r) => `<div class="card"><b>${esc(r.userName || "Someone")}</b> <span class="stars">${"★".repeat(r.stars)}</span><div>${esc(r.text)}</div></div>`).join("") || `<p class="muted">No written reviews yet.</p>`}</div>`;
  const paint = () => view.querySelectorAll("#rate button").forEach((b) => b.classList.toggle("on", +b.dataset.n <= stars));
  view.querySelectorAll("#rate button").forEach((b) => b.onclick = () => { stars = +b.dataset.n; paint(); });
  document.getElementById("save").onclick = async () => {
    if (!stars) return toast("Pick 1 to 5 stars first");
    const text = document.getElementById("txt").value.slice(0, 500);
    const me = S.user.uid, now = Date.now();
    try {
      await runTransaction(db, async (tx) => {
        const ref = doc(db, "ratings", `${id}_${me}`);
        const old = await tx.get(ref);
        const prev = old.exists() ? old.data().stars : 0;
        tx.set(ref, { dishId: id, userId: me, userName: S.user.displayName ?? "", stars, text, updatedAt: now });
        tx.update(doc(db, "dishes", id), { ratingSum: increment(stars - prev), ratingCount: increment(old.exists() ? 0 : 1), lastRatedAt: now });
      });
      S.mine[id] = { dishId: id, stars, text };
      toast("Saved");
      dish(id);
    } catch (e) { console.error(e); toast("Couldn't save. Try again."); }
  };
}

const ago = (ms) => { const m = (Date.now() - ms) / 60000; return m < 1 ? "just now" : m < 60 ? `${m | 0}m ago` : m < 1440 ? `${(m / 60) | 0}h ago` : `${(m / 1440) | 0}d ago`; };

async function feed() {
  view.innerHTML = `<h1>What's new</h1>` + skeleton();
  const here = S.here ?? HARTFORD;
  const snap = await getDocs(query(collection(db, "feed"), where("gh3", "in", feedCells(here)), orderBy("createdAt", "desc"), limit(40)));
  const items = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const who = (i) => esc(i.userName || "Someone");
  view.innerHTML = `<h1>What's new</h1><div class="list">${items.map((i) => `
    <a class="card" href="${i.type === "review" && i.dishId ? `#/dish/${encodeURIComponent(i.dishId)}` : `#/place/${encodeURIComponent(i.placeId)}`}">
      <div class="muted">${i.userId === "dishcover-menu-check" ? (i.type === "place" ? "New on Dishcover" : "New on the menu") : i.type === "menu" ? `${who(i)} added to a menu` : i.type === "place" ? `${who(i)} added a new place` : i.type === "review" ? `${who(i)} reviewed` : `${who(i)} was here`} · ${ago(i.createdAt)}</div>
      <div class="title" style="color:var(--wine)">${esc(i.placeName)}</div>
      ${i.type === "menu" ? `<div>${i.dishCount} new ${i.dishCount === 1 ? "dish" : "dishes"}: ${esc((i.dishNames ?? []).join(", "))}${i.dishCount > (i.dishNames ?? []).length ? " and more" : ""}</div>` : ""}
      ${i.dishName ? `<div><b>${esc(i.dishName)}</b> ${i.stars ? `<span class="stars">${"★".repeat(i.stars)}</span>` : ""}</div>` : ""}
      ${i.text ? `<div>${esc(i.text)}</div>` : ""}
      ${i.photoUrl ? `<img class="photo" loading="lazy" src="${esc(i.photoUrl)}" alt="">` : ""}
      <div class="muted" style="margin-top:6px">${i.likeCount ? `♥ ${i.likeCount}` : ""} ${i.commentCount ? `· 💬 ${i.commentCount}` : ""}</div>
    </a>`).join("") || `<div class="empty">Nothing new around you yet.</div>`}</div>`;
}

// ---------- routing ----------

function route() {
  if (!S.user) return signedOut();
  const [, page, arg] = (location.hash || "#/").split("/");
  document.querySelectorAll(".tabs a").forEach((a) => a.classList.toggle("on",
    (a.dataset.tab === "near" && !page) || a.dataset.tab === page));
  window.scrollTo(0, 0);
  if (page === "place") return place(decodeURIComponent(arg));
  if (page === "dish") return dish(decodeURIComponent(arg));
  if (page === "dishes") return dishes();
  if (page === "feed") return feed();
  return restaurants();
}
window.addEventListener("hashchange", route);
document.getElementById("signout").onclick = () => signOut(auth);

onAuthStateChanged(auth, async (u) => {
  S.user = u;
  document.getElementById("tabs").hidden = !u;
  document.getElementById("signout").hidden = !u;
  if (!u) return signedOut();
  route();
  try { await load(); } catch (e) { console.error(e); toast("Couldn't load places"); }
  route();
});
