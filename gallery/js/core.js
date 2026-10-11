'use strict';
// ---------- helpers ----------
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const h = html => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const EASE = 'cubic-bezier(.32,.72,0,1)';
const SPRING = 'cubic-bezier(.34,1.4,.64,1)';
const SOFT_SPRING = 'cubic-bezier(.25,1.18,.45,1)';
const RM = matchMedia('(prefers-reduced-motion: reduce)').matches;
const IN_FRAME = (() => { try { return window.self !== window.top; } catch (e) { return true; } })();
const NATIVE = window.AndroidGallery || null; // set by the Android app (see android/)
const haptic = (ms = 8) => { try { navigator.vibrate && navigator.vibrate(ms); } catch (e) {} };
const LS = {
  get(k, d) { try { const v = localStorage.getItem('rg:' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('rg:' + k, JSON.stringify(v)); } catch (e) {} },
};
const SAFE = { top: 0, bot: 0 };
function measureSafe() {
  const p = h('<div style="position:fixed;left:0;top:env(safe-area-inset-top,0px);bottom:env(safe-area-inset-bottom,0px);width:1px;visibility:hidden;pointer-events:none"></div>');
  document.body.append(p);
  const cs = getComputedStyle(p);
  SAFE.top = parseFloat(cs.top) || 0; SAFE.bot = parseFloat(cs.bottom) || 0;
  p.remove();
}

// Commits the last keyframe as inline style so stacked animations don't fight.
function anim(el, frames, opts = {}) {
  const a = el.animate(frames, { duration: RM ? 1 : (opts.duration || 450), easing: opts.easing || EASE, fill: 'forwards', delay: opts.delay || 0 });
  return a.finished.then(() => {
    const last = frames[frames.length - 1];
    for (const k in last) if (k !== 'offset' && k !== 'easing') el.style[k] = last[k];
    a.cancel();
  }, () => {});
}

const MONTHS = ['Січень', 'Лютий', 'Березень', 'Квітень', 'Травень', 'Червень', 'Липень', 'Серпень', 'Вересень', 'Жовтень', 'Листопад', 'Грудень'];
const MONTHS_GEN = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
const WEEKDAYS = ['неділя', 'понеділок', 'вівторок', 'середа', 'четвер', 'пʼятниця', 'субота'];
const plural = (n, one, few, many) => { const a = n % 10, b = n % 100; if (a === 1 && b !== 11) return one; if (a >= 2 && a <= 4 && (b < 12 || b > 14)) return few; return many; };
const fmtDate = ts => { const d = new Date(ts); return `${d.getDate()} ${MONTHS_GEN[d.getMonth()]} ${d.getFullYear()}`; };
const fmtTime = ts => { const d = new Date(ts); return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); };
const fmtSize = b => b < 1024 ? b + ' Б' : b < 1048576 ? Math.round(b / 1024) + ' КБ' : b < 1073741824 ? (b / 1048576).toFixed(1).replace('.', ',') + ' МБ' : (b / 1073741824).toFixed(2).replace('.', ',') + ' ГБ';
const fmtDur = s => { s = Math.max(0, Math.round(s || 0)); const m = Math.floor(s / 60); return m + ':' + String(s % 60).padStart(2, '0'); };
const ext = n => { const i = n.lastIndexOf('.'); return i > 0 ? n.slice(i + 1) : ''; };
const monthKey = ts => { const d = new Date(ts); return d.getFullYear() + '-' + d.getMonth(); };
const nItems = n => `${n} ${plural(n, "об'єкт", "об'єкти", "об'єктів")}`;
function countText(items) {
  const v = items.filter(i => i.kind === 'video').length, p = items.length - v;
  const parts = [];
  if (p || !v) parts.push(`${p} фото`);
  if (v) parts.push(`${v} відео`);
  return parts.join(', ');
}

// ---------- icons ----------
const svg = (p, sw = 2) => `<svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${sw}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${p}</svg>`;
const HEART = 'M12 20.3s-7.6-4.5-9.4-9.1C1.4 7.9 3.5 4.4 7 4.4c2.1 0 3.7 1.1 5 3 1.3-1.9 2.9-3 5-3 3.5 0 5.6 3.5 4.4 6.8-1.8 4.6-9.4 9.1-9.4 9.1z';
const FOLDER = 'M3 7.5a2 2 0 0 1 2-2h4.2l2 2.2H19a2 2 0 0 1 2 2V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z';
const I = {
  back: svg('<path d="M15 4.5L7.5 12l7.5 7.5"/>', 2.6),
  chev: svg('<path d="M9.5 5.5L16 12l-6.5 6.5"/>', 2.4).replace('class="ico"', 'class="ico chev"'),
  plus: svg('<path d="M12 5v14M5 12h14"/>', 2.2),
  more: svg('<circle cx="6" cy="12" r="1.3" fill="currentColor"/><circle cx="12" cy="12" r="1.3" fill="currentColor"/><circle cx="18" cy="12" r="1.3" fill="currentColor"/>', 1.6),
  heart: svg(`<path d="${HEART}"/>`, 1.9),
  heartFill: svg(`<path d="${HEART}" fill="currentColor"/>`, 1.9),
  share: svg('<path d="M12 3.5v11M8 7.5l4-4 4 4"/><path d="M8.5 10.5H7a1.5 1.5 0 0 0-1.5 1.5v7A1.5 1.5 0 0 0 7 20.5h10a1.5 1.5 0 0 0 1.5-1.5v-7a1.5 1.5 0 0 0-1.5-1.5h-1.5"/>', 1.9),
  trash: svg('<path d="M4 6.5h16M9.5 6.5v-2h5v2M6.5 6.5l1 13.5h9l1-13.5M10 10.5v6M14 10.5v6"/>', 1.9),
  info: svg('<circle cx="12" cy="12" r="9.5"/><path d="M12 11v6M12 7.6v.1"/>', 1.9),
  check: svg('<path d="M6 12.5l4 4 8-9"/>', 3),
  folder: svg(`<path d="${FOLDER}"/>`, 1.9),
  photo: svg('<rect x="3" y="4.5" width="18" height="15" rx="2.5"/><circle cx="8.5" cy="9.5" r="1.8"/><path d="M3.5 17l5.2-5 4.3 4 2.7-2.6 4.8 4.4"/>', 1.9),
  library: svg('<rect x="3" y="3" width="7.5" height="7.5" rx="1.6"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="1.6"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="1.6"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="1.6"/>', 1.9),
  albums: svg('<rect x="3.5" y="8" width="17" height="12.5" rx="2.2"/><path d="M6 5h12M8.5 2.5h7"/>', 1.9),
  search: svg('<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5L21 21"/>', 2.1),
  video: svg('<rect x="2.5" y="6" width="13" height="12" rx="2.5"/><path d="M15.5 10.5l6-3.5v10l-6-3.5"/>', 1.9),
  clock: svg('<circle cx="12" cy="12" r="9.5"/><path d="M12 7v5l3.5 2"/>', 1.9),
  restore: svg('<path d="M4 12a8 8 0 1 0 2.4-5.7M4 4v4.5h4.5"/>', 1.9),
  play: '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 5.2v13.6L19 12z" fill="currentColor"/></svg>',
  pause: svg('<path d="M8.5 5.5v13M15.5 5.5v13"/>', 3),
  x: svg('<path d="M6 6l12 12M18 6L6 18"/>', 2.4),
  albumAdd: svg('<rect x="3" y="7" width="14" height="13" rx="2"/><path d="M10 10.5v6M7 13.5h6M7 4h11a3 3 0 0 1 3 3v10"/>', 1.9),
  move: svg(`<path d="${FOLDER}"/><path d="M9.5 13.5h6M13 11l2.5 2.5L13 16"/>`, 1.9),
  slides: svg('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M10 9v6l5-3z"/>', 1.9),
  pencil: svg('<path d="M4 20h4L19 9l-4-4L4 16z"/>', 1.9),
  select: svg('<circle cx="12" cy="12" r="9.5"/><path d="M7.5 12.5l3 3 6-6.5"/>', 1.9),
  import: svg('<path d="M12 3v12M7.5 10.5L12 15l4.5-4.5M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>', 1.9),
  download: svg('<path d="M12 4v11M7.5 10.5L12 15l4.5-4.5M5 20h14"/>', 1.9),
  eye: svg('<path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7z"/><circle cx="12" cy="12" r="3"/>', 1.9),
  eyeOff: svg('<path d="M3.5 3.5l17 17"/><path d="M10.6 5.1A10 10 0 0 1 12 5c6 0 9.5 7 9.5 7a17 17 0 0 1-3.2 4.1M6.6 6.6C3.9 8.4 2.5 12 2.5 12s3.5 7 9.5 7c1.8 0 3.4-.6 4.7-1.4"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>', 1.9),
};

// ---------- storage (IndexedDB) ----------
let quotaWarned = false;
function quotaWarn() { if (quotaWarned) return; quotaWarned = true; toast('Пам’ять браузера заповнена — частину файлів не збережено', 'info'); }
const DB = {
  d: null,
  open() {
    return new Promise(res => {
      let r, done = false; const fin = v => { if (!done) { done = true; res(v); } };
      try { r = indexedDB.open('reya-gallery', 1); } catch (e) { return fin(false); }
      r.onupgradeneeded = () => {
        const d = r.result;
        ['items', 'blobs', 'albums', 'folders'].forEach(s => { if (!d.objectStoreNames.contains(s)) d.createObjectStore(s, { keyPath: 'id' }); });
        if (!d.objectStoreNames.contains('meta')) d.createObjectStore('meta');
      };
      r.onsuccess = () => { this.d = r.result; fin(true); };
      r.onerror = r.onblocked = () => fin(false);
      setTimeout(() => fin(!!this.d), 5000);
    });
  },
  req(store, mode, fn) {
    return new Promise(res => {
      if (!this.d) return res(null);
      try {
        const t = this.d.transaction(store, mode);
        const q = fn(t.objectStore(store));
        t.oncomplete = () => res(q && 'result' in q ? q.result : true);
        t.onerror = t.onabort = () => { if (mode === 'readwrite') quotaWarn(); res(null); };
      } catch (e) { res(null); }
    });
  },
  all(s) { return this.req(s, 'readonly', os => os.getAll()).then(r => r || []); },
  get(s, k) { return this.req(s, 'readonly', os => os.get(k)); },
  put(s, vals) { vals = [].concat(vals).filter(Boolean); if (!vals.length) return Promise.resolve(); return this.req(s, 'readwrite', os => { vals.forEach(v => os.put(v)); }); },
  del(s, keys) { keys = [].concat(keys); if (!keys.length) return Promise.resolve(); return this.req(s, 'readwrite', os => { keys.forEach(k => os.delete(k)); }); },
  meta(k) { return this.req('meta', 'readonly', os => os.get(k)); },
  setMeta(k, v) { return this.req('meta', 'readwrite', os => os.put(v, k)); },
};

// ---------- state ----------
const S = { items: [], byId: new Map(), albums: [], folders: [], persist: true };
const META_KEYS = ['id', 'name', 'kind', 'mime', 'size', 'w', 'h', 'duration', 'date', 'added', 'path', 'fav', 'deleted', 'sample', 'fkey', 'thumb'];
const rec = it => { const o = {}; for (const k of META_KEYS) o[k] = it[k]; return o; };
const saveItems = its => DB.put('items', [].concat(its).map(rec));
const saveAlbums = as => DB.put('albums', [].concat(as));
const saveFolders = fs => DB.put('folders', [].concat(fs));
// Photos that sit in a hidden album (or an album inside a hidden folder) stay out of the
// library, search and smart albums. The set is rebuilt after every data change.
let HIDDEN = null;
function folderHidden(fid) { let f = folderById(fid); while (f) { if (f.hidden) return true; f = folderById(f.parent); } return false; }
const albumHidden = a => !!a.hidden || folderHidden(a.parent);
function hiddenIds() {
  if (!HIDDEN) { HIDDEN = new Set(); for (const a of S.albums) if (albumHidden(a)) a.items.forEach(id => HIDDEN.add(id)); }
  return HIDDEN;
}
const live = () => { const hid = hiddenIds(); return S.items.filter(i => !i.deleted && !hid.has(i.id)); };
function indexItems() {
  HIDDEN = null;
  S.items.sort((a, b) => a.date - b.date || a.added - b.added);
  S.byId = new Map(S.items.map(i => [i.id, i]));
}
const THUMBS = new Map();
function turl(it) {
  if (!it || !it.thumb) return '';
  let u = THUMBS.get(it.id);
  if (!u) { u = URL.createObjectURL(it.thumb); THUMBS.set(it.id, u); }
  return u;
}
const FULL = new Map();
async function getBlob(it) {
  if (it._blob) return it._blob;
  const r = await DB.get('blobs', it.id);
  return r ? r.blob : null;
}
async function fullURL(it) {
  if (!it) return null;
  if (FULL.has(it.id)) { const u = FULL.get(it.id); FULL.delete(it.id); FULL.set(it.id, u); return u; }
  const b = await getBlob(it);
  if (!b) return null;
  if (FULL.has(it.id)) return FULL.get(it.id);
  const u = URL.createObjectURL(b);
  FULL.set(it.id, u);
  if (FULL.size > 18) { const [k, v] = FULL.entries().next().value; FULL.delete(k); URL.revokeObjectURL(v); }
  return u;
}
function forgetURLs(id) {
  if (THUMBS.has(id)) { URL.revokeObjectURL(THUMBS.get(id)); THUMBS.delete(id); }
  if (FULL.has(id)) { URL.revokeObjectURL(FULL.get(id)); FULL.delete(id); }
}

const albumById = id => S.albums.find(a => a.id === id);
const folderById = id => S.folders.find(f => f.id === id);
const byCreated = (a, b) => a.created - b.created;
const albumItems = a => a.items.map(id => S.byId.get(id)).filter(i => i && !i.deleted).sort((x, y) => x.date - y.date);
function folderDescAlbums(fid) {
  const out = [];
  const walk = f => { S.albums.filter(a => a.parent === f).sort(byCreated).forEach(a => out.push(a)); S.folders.filter(x => x.parent === f).sort(byCreated).forEach(x => walk(x.id)); };
  walk(fid);
  return out;
}
function folderCovers(fid) {
  const covers = [];
  for (const a of folderDescAlbums(fid)) { const its = albumItems(a); if (its.length) covers.push(its[its.length - 1]); if (covers.length >= 4) break; }
  return covers;
}
function isInside(fid, ancestor) { let cur = fid; while (cur) { if (cur === ancestor) return true; cur = folderById(cur)?.parent ?? null; } return false; }
function folderPath(fid) { const parts = []; let f = folderById(fid); while (f) { parts.unshift(f.name); f = folderById(f.parent); } return parts.join(' › '); }

const SMART = {
  favorites: { name: 'Улюблені', icon: 'heart', get: () => live().filter(i => i.fav) },
  videos: { name: 'Відео', icon: 'video', get: () => live().filter(i => i.kind === 'video') },
  recent: { name: 'Нещодавно додані', icon: 'clock', get: () => { const cut = Date.now() - 30 * 864e5; return live().filter(i => i.added >= cut); } },
  deleted: { name: 'Нещодавно видалені', icon: 'trash', get: () => S.items.filter(i => i.deleted) },
};

// ---------- mutations ----------
function createAlbum(name, parent = null, sample = false) { const a = { id: uid(), name, parent, items: [], created: Date.now(), sample }; S.albums.push(a); saveAlbums(a); return a; }
function createFolder(name, parent = null, sample = false) { const f = { id: uid(), name, parent, created: Date.now(), sample }; S.folders.push(f); saveFolders(f); return f; }
const itemsOf = ids => ids.map(id => S.byId.get(id)).filter(Boolean);
async function setFav(ids, val) { const its = itemsOf(ids); its.forEach(i => i.fav = val); await saveItems(its); invalidate(); }
async function trashItems(ids) { const now = Date.now(); const its = itemsOf(ids); its.forEach(i => i.deleted = now); await saveItems(its); }
async function restoreItems(ids) { const its = itemsOf(ids); its.forEach(i => i.deleted = null); await saveItems(its); }
async function purgeItems(ids) {
  const set = new Set(ids);
  S.items = S.items.filter(i => !set.has(i.id));
  ids.forEach(forgetURLs);
  const changed = [];
  S.albums.forEach(a => { const n = a.items.filter(id => !set.has(id)); if (n.length !== a.items.length) { a.items = n; changed.push(a); } });
  indexItems();
  await Promise.all([DB.del('items', ids), DB.del('blobs', ids), saveAlbums(changed)]);
}
async function addToAlbum(a, ids) {
  const set = new Set(a.items); let n = 0;
  ids.forEach(id => { if (!set.has(id)) { a.items.push(id); set.add(id); n++; } });
  await saveAlbums(a); invalidate();
  return n;
}
async function removeFromAlbum(a, ids) { const set = new Set(ids); a.items = a.items.filter(id => !set.has(id)); await saveAlbums(a); }
async function deleteAlbum(a) { S.albums = S.albums.filter(x => x !== a); await DB.del('albums', a.id); invalidate(); }
async function deleteFolder(f) {
  const fids = S.folders.filter(x => isInside(x.id, f.id)).map(x => x.id);
  const aids = S.albums.filter(a => fids.includes(a.parent)).map(a => a.id);
  S.folders = S.folders.filter(x => !fids.includes(x.id));
  S.albums = S.albums.filter(a => !aids.includes(a.id));
  await Promise.all([DB.del('folders', fids), DB.del('albums', aids)]);
  invalidate();
}

// ---------- particles ----------
const FX = (() => {
  const cv = $('#fx'), g = cv.getContext('2d');
  let P = [], raf = 0, dpr = 1, last = 0;
  const size = () => { dpr = Math.min(2, devicePixelRatio || 1); cv.width = Math.round(innerWidth * dpr); cv.height = Math.round(innerHeight * dpr); };
  size(); addEventListener('resize', size);
  const HP = new Path2D('M0 4C-3 1.5-6-.5-6-3-6-5-4.5-6.5-3-6.5-1.8-6.5-.7-5.8 0-4.6.7-5.8 1.8-6.5 3-6.5 4.5-6.5 6-5 6-3 6-.5 3 1.5 0 4Z');
  function frame(now) {
    const f = Math.min(3, (now - last) / 16.67 || 1); last = now;
    g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cv.width, cv.height);
    let dead = 0;
    for (const p of P) {
      const t = now - p.t0 - p.delay;
      if (t > p.life) { p.dead = true; dead++; continue; }
      if (t > 0) { const d = Math.pow(p.drag, f); p.vx *= d; p.vy = p.vy * d + p.g * f; p.x += p.vx * f; p.y += p.vy * f; p.r += p.vr * f; }
      const k = Math.max(0, t) / p.life;
      g.globalAlpha = t <= 0 ? 1 : Math.max(0, Math.pow(1 - k, p.fade));
      g.fillStyle = p.c;
      const c = Math.cos(p.r) * dpr, s = Math.sin(p.r) * dpr;
      g.setTransform(c, s, -s, c, p.x * dpr, p.y * dpr);
      if (p.shape === 'sq') g.fillRect(-p.s / 2, -p.s / 2, p.s, p.s);
      else if (p.shape === 'heart') { const q = p.s / 12; g.scale(q, q); g.fill(HP); }
      else if (p.shape === 'star') { const r = p.s * (1 - k * .4); g.beginPath(); g.moveTo(0, -r); g.quadraticCurveTo(0, 0, r, 0); g.quadraticCurveTo(0, 0, 0, r); g.quadraticCurveTo(0, 0, -r, 0); g.quadraticCurveTo(0, 0, 0, -r); g.fill(); }
      else { g.beginPath(); g.arc(0, 0, p.s / 2, 0, 6.2832); g.fill(); }
    }
    if (dead) P = P.filter(p => !p.dead);
    g.globalAlpha = 1;
    if (P.length) raf = requestAnimationFrame(frame);
    else { raf = 0; g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, cv.width, cv.height); }
  }
  const add = o => P.push(Object.assign({ t0: performance.now(), delay: 0, vx: 0, vy: 0, g: 0, drag: .98, r: 0, vr: 0, life: 900, s: 4, shape: 'dot', c: '#fff', fade: 1.5 }, o));
  const go = () => { if (!raf) { last = performance.now(); raf = requestAnimationFrame(frame); } };
  const rnd = (a, b) => a + Math.random() * (b - a);
  return {
    hearts(x, y) {
      if (RM) return;
      const cols = ['#ff2d55', '#ff375f', '#ff6482', '#ff9f0a', '#ffd60a'];
      for (let i = 0; i < 20; i++) {
        const a = -Math.PI / 2 + rnd(-.8, .8) * Math.PI, v = rnd(2.5, 7);
        add({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v, g: .16, drag: .96, s: rnd(8, 18), shape: i % 4 ? 'heart' : 'star', c: cols[i % cols.length], life: rnd(700, 1200), r: rnd(-.4, .4), vr: rnd(-.06, .06), fade: 1.2 });
      }
      go();
    },
    sparkle(x, y, n = 50, cols = ['#0a84ff', '#5ac8fa', '#34c759', '#ffd60a', '#ff9f0a', '#ff375f', '#bf5af2']) {
      if (RM) return;
      for (let i = 0; i < n; i++) {
        const a = rnd(0, Math.PI * 2), v = rnd(2, 8);
        add({ x, y, vx: Math.cos(a) * v, vy: Math.sin(a) * v - 2, g: .13, drag: .955, s: i % 2 ? rnd(3, 6) : rnd(5, 10), shape: i % 2 ? 'dot' : 'star', c: cols[i % cols.length], life: rnd(900, 1600), vr: rnd(-.12, .12), fade: 1.3 });
      }
      go();
    },
    // Breaks an element into coloured squares that drift away, left to right.
    dissolve(el, src, { budget = 1600, cover = false } = {}) {
      if (RM || !el) return;
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2 || r.bottom < 0 || r.top > innerHeight) return;
      const step = Math.max(3, Math.ceil(Math.sqrt(r.width * r.height / budget)));
      const cols = Math.ceil(r.width / step), rows = Math.ceil(r.height / step);
      let data = null;
      try {
        const nw = src && (src.naturalWidth || src.videoWidth), nh = src && (src.naturalHeight || src.videoHeight);
        if (nw && nh) {
          const c = document.createElement('canvas'); c.width = cols; c.height = rows;
          const cg = c.getContext('2d');
          if (cover) {
            const ba = r.width / r.height, ia = nw / nh; let sx = 0, sy = 0, sw = nw, sh = nh;
            if (ia > ba) { sw = nh * ba; sx = (nw - sw) / 2; } else { sh = nw / ba; sy = (nh - sh) / 2; }
            cg.drawImage(src, sx, sy, sw, sh, 0, 0, cols, rows);
          } else cg.drawImage(src, 0, 0, cols, rows);
          data = cg.getImageData(0, 0, cols, rows).data;
        }
      } catch (e) { data = null; }
      for (let y = 0; y < rows; y++) for (let x = 0; x < cols; x++) {
        let c = 'rgb(142,142,147)';
        if (data) { const k = (y * cols + x) * 4; if (data[k + 3] < 20) continue; c = `rgb(${data[k]},${data[k + 1]},${data[k + 2]})`; }
        add({ x: r.left + x * step + step / 2, y: r.top + y * step + step / 2, s: step + .6, shape: 'sq', c, delay: (x / cols) * 380 + (1 - y / rows) * 120 + rnd(0, 160), vx: rnd(.6, 3), vy: -rnd(.4, 2), g: -.015, drag: .985, life: rnd(650, 1200), vr: rnd(-.08, .08), fade: 1.1 });
      }
      go();
    },
  };
})();

// ---------- overlays: back stack, toast, sheets, menus, alerts ----------
const OV = [];
const ovRemove = fn => { const i = OV.indexOf(fn); if (i >= 0) OV.splice(i, 1); };

function toast(msg, icon = 'check') {
  const t = h(`<div class="toast" role="status">${I[icon] || ''}<span>${esc(msg)}</span></div>`);
  document.body.append(t);
  t.animate([{ opacity: 0, transform: 'translate(-50%,14px) scale(.92)' }, { opacity: 1, transform: 'translate(-50%,0) scale(1)' }], { duration: RM ? 1 : 380, easing: SOFT_SPRING });
  setTimeout(() => t.animate([{ opacity: 1 }, { opacity: 0, transform: 'translate(-50%,10px)' }], { duration: 250, fill: 'forwards' }).finished.then(() => t.remove()), 2000);
}

function openSheet({ title, cls = '' }) {
  Back.arm();
  const w = h(`<div class="sheet-wrap"><div class="sheet-back"></div><div class="sheet ${cls}" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="sheet-grab"><span></span></div><div class="sheet-head"><div class="sh-side sh-l"></div><div class="sheet-title">${esc(title)}</div><div class="sh-side sh-r"></div></div><div class="sheet-body"></div></div></div>`);
  $('#overlays').append(w);
  const sheet = $('.sheet', w);
  const side = (sel, label, fn, strong, disabled) => {
    const box = $(sel, w); box.innerHTML = '';
    if (!label) return;
    const b = h(`<button class="sh-btn${strong ? ' strong' : ''}" ${disabled ? 'disabled' : ''}>${esc(label)}</button>`);
    b.addEventListener('click', fn); box.append(b);
  };
  const api = {
    el: w, sheet, body: $('.sheet-body', w), onClose: null, closed: false,
    close() {
      if (api.closed) return; api.closed = true; ovRemove(cf);
      w.classList.remove('open'); sheet.style.transform = '';
      setTimeout(() => w.remove(), 520);
      api.onClose && api.onClose();
    },
    setTitle(t) { $('.sheet-title', w).textContent = t; },
    setLeft: (l, fn, s, d) => side('.sh-l', l, fn, s, d),
    setRight: (l, fn, s, d) => side('.sh-r', l, fn, s, d),
  };
  const cf = () => api.close(); OV.push(cf);
  $('.sheet-back', w).addEventListener('click', () => api.close());
  let d = null;
  [$('.sheet-grab', w), $('.sheet-head', w)].forEach(el => {
    el.addEventListener('pointerdown', e => {
      if (e.target.closest('button')) return;
      d = { y: e.clientY, t: performance.now() }; sheet.style.transition = 'none';
      try { el.setPointerCapture(e.pointerId); } catch (_) {}
    });
    el.addEventListener('pointermove', e => { if (d) sheet.style.transform = `translateY(${Math.max(0, e.clientY - d.y)}px)`; });
    const up = e => {
      if (!d) return;
      const dy = e.clientY - d.y, v = dy / Math.max(1, performance.now() - d.t); d = null;
      sheet.style.transition = '';
      if (dy > 120 || v > .6) api.close(); else sheet.style.transform = '';
    };
    el.addEventListener('pointerup', up); el.addEventListener('pointercancel', up);
  });
  requestAnimationFrame(() => requestAnimationFrame(() => w.classList.add('open')));
  return api;
}

function actionSheet({ title, actions }) {
  return new Promise(res => {
    Back.arm();
    const w = h(`<div class="as-wrap"><div class="sheet-back"></div><div class="as" role="dialog" aria-modal="true"><div class="as-group">${title ? `<div class="as-title">${esc(title)}</div>` : ''}${actions.map((a, i) => `<button class="as-btn${a.destructive ? ' destructive' : ''}" data-i="${i}">${esc(a.label)}</button>`).join('')}</div><button class="as-btn as-cancel" data-i="-1">Скасувати</button></div></div>`);
    $('#overlays').append(w);
    requestAnimationFrame(() => requestAnimationFrame(() => w.classList.add('open')));
    let done = false;
    const close = v => { if (done) return; done = true; ovRemove(cf); w.classList.remove('open'); setTimeout(() => w.remove(), 480); res(v); };
    const cf = () => close(null); OV.push(cf);
    w.addEventListener('click', e => {
      const b = e.target.closest('[data-i]');
      if (b) { const i = +b.dataset.i; close(i < 0 ? null : (actions[i].value ?? true)); }
      else if (e.target.classList.contains('sheet-back')) close(null);
    });
  });
}

function askText({ title, message = '', value = '', placeholder = '', ok = 'Зберегти' }) {
  return new Promise(res => {
    Back.arm();
    const w = h(`<div class="alert-wrap"><form class="alert" role="dialog" aria-modal="true"><h3>${esc(title)}</h3>${message ? `<p>${esc(message)}</p>` : ''}<input id="ask-text" type="text" maxlength="80" autocomplete="off" placeholder="${esc(placeholder)}" value="${esc(value)}"><div class="alert-btns"><button type="button" data-a="cancel">Скасувати</button><button type="submit" data-a="ok">${esc(ok)}</button></div></form></div>`);
    $('#overlays').append(w);
    const input = $('input', w), okb = $('[data-a="ok"]', w);
    const sync = () => { okb.disabled = !input.value.trim(); };
    sync(); input.addEventListener('input', sync);
    requestAnimationFrame(() => { w.classList.add('open'); input.focus(); input.select(); });
    let done = false;
    const close = v => { if (done) return; done = true; ovRemove(cf); input.blur(); w.classList.remove('open'); setTimeout(() => w.remove(), 250); res(v); };
    const cf = () => close(null); OV.push(cf);
    $('form', w).addEventListener('submit', e => { e.preventDefault(); const v = input.value.trim(); if (v) close(v); });
    $('[data-a="cancel"]', w).addEventListener('click', () => close(null));
  });
}

const CTX = { open: false };
// iOS-style context menu: lifts a preview of the anchor and shows a blurred menu under it.
function ctxMenu(anchor, { previewItem = null, previewEl = null, items }) {
  if (CTX.open || !items.length) return;
  CTX.open = true; Back.arm(); haptic(10);
  const W = innerWidth, H = innerHeight, r = anchor.getBoundingClientRect();
  const wrap = h('<div class="ctx-wrap"><div class="ctx-back"></div></div>');
  let prev = null, pr = null;
  if (previewItem || previewEl) {
    prev = h('<div class="ctx-prev"></div>');
    if (previewItem) { const u = turl(previewItem); if (u) prev.innerHTML = `<img src="${u}" alt="">`; }
    else prev.append(previewEl);
    const aspect = previewItem && previewItem.w && previewItem.h ? previewItem.w / previewItem.h : r.width / r.height;
    let pw = previewItem ? Math.min(W - 32, 360) : Math.min(r.width * 1.12, W - 32), ph = pw / aspect;
    if (ph > H * .5) { ph = H * .5; pw = ph * aspect; }
    pr = { w: pw, h: ph, x: clamp(r.left + r.width / 2 - pw / 2, 16, W - 16 - pw), y: 0 };
  }
  const menu = h(`<div class="ctx-menu" role="menu">${items.map((it, i) => `<button class="ctx-item${it.destructive ? ' destructive' : ''}${it.sep && i ? ' sep' : ''}" data-i="${i}" role="menuitem"><span>${esc(it.label)}</span>${it.icon ? I[it.icon] : ''}</button>`).join('')}</div>`);
  if (prev) wrap.append(prev);
  wrap.append(menu); $('#overlays').append(wrap);
  const mh = menu.offsetHeight, mw = 250;
  const topMin = SAFE.top + 12, botMax = H - SAFE.bot - 12;
  let mx, my;
  if (prev) {
    if (pr.h + 10 + mh > botMax - topMin) { const s = Math.max(.3, (botMax - topMin - 10 - mh) / pr.h); pr.w *= s; pr.h *= s; pr.x = clamp(r.left + r.width / 2 - pr.w / 2, 16, W - 16 - pr.w); }
    let y = clamp(r.top + r.height / 2 - pr.h / 2, topMin, H);
    if (y + pr.h + 10 + mh > botMax) y = Math.max(topMin, botMax - mh - 10 - pr.h);
    pr.y = y; my = y + pr.h + 10;
    mx = clamp(r.left + r.width / 2 > W / 2 ? pr.x + pr.w - mw : pr.x, 16, W - 16 - mw);
    Object.assign(prev.style, { left: pr.x + 'px', top: pr.y + 'px', width: pr.w + 'px', height: pr.h + 'px' });
  } else {
    my = r.bottom + 8; if (my + mh > botMax) my = Math.max(topMin, r.top - 8 - mh);
    mx = r.left + r.width / 2 > W / 2 ? clamp(r.right - mw, 12, W - 12 - mw) : clamp(r.left, 12, W - 12 - mw);
  }
  Object.assign(menu.style, { left: mx + 'px', top: my + 'px', transformOrigin: `${clamp(r.left + r.width / 2 - mx, 0, mw)}px ${my > r.top ? 0 : mh}px` });
  const fromRect = () => { const a = anchor.isConnected ? anchor.getBoundingClientRect() : r; return { left: a.left + 'px', top: a.top + 'px', width: a.width + 'px', height: a.height + 'px', borderRadius: '4px' }; };
  const toRect = () => ({ left: pr.x + 'px', top: pr.y + 'px', width: pr.w + 'px', height: pr.h + 'px', borderRadius: '14px' });
  requestAnimationFrame(() => wrap.classList.add('open'));
  if (!RM) {
    if (prev) prev.animate([fromRect(), toRect()], { duration: 460, easing: SOFT_SPRING });
    menu.animate([{ opacity: 0, transform: 'scale(.35)' }, { opacity: 1, transform: 'scale(1)' }], { duration: 420, easing: SOFT_SPRING });
  }
  let closed = false;
  const close = (back = true) => {
    if (closed) return; closed = true; CTX.open = false; ovRemove(cf);
    wrap.classList.remove('open');
    menu.animate([{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: 'scale(.55)' }], { duration: RM ? 1 : 220, easing: 'ease-in', fill: 'forwards' });
    if (prev) prev.animate(back ? [toRect(), fromRect()] : [{ opacity: 1 }, { opacity: 0 }], { duration: RM ? 1 : 320, easing: EASE, fill: 'forwards' });
    setTimeout(() => wrap.remove(), 340);
  };
  const cf = () => close(true); OV.push(cf);
  $('.ctx-back', wrap).addEventListener('click', () => close(true));
  if (prev) prev.addEventListener('click', () => close(true));
  menu.addEventListener('click', e => {
    const b = e.target.closest('.ctx-item'); if (!b) return;
    const it = items[+b.dataset.i]; close(false);
    setTimeout(() => it.run && it.run(), 80);
  });
}
const popMenu = (anchor, items) => ctxMenu(anchor, { items });

// Long-press (touch) + right-click (mouse) on elements matching a selector.
function longPress(root, selector, cb) {
  let t = null, st = null, fired = false;
  const fire = el => { if (CTX.open) return; fired = true; cb(el); };
  root.addEventListener('pointerdown', e => {
    const el = e.target.closest(selector);
    if (!el || (e.pointerType === 'mouse' && e.button !== 0)) return;
    fired = false; st = { x: e.clientX, y: e.clientY }; clearTimeout(t);
    el.classList.add('press');
    t = setTimeout(() => { t = null; el.classList.remove('press'); fire(el); }, 480);
    st.el = el;
  });
  const cancel = () => { clearTimeout(t); t = null; st && st.el && st.el.classList.remove('press'); };
  root.addEventListener('pointermove', e => { if (t && st && Math.hypot(e.clientX - st.x, e.clientY - st.y) > 8) cancel(); });
  root.addEventListener('pointerup', cancel); root.addEventListener('pointercancel', cancel);
  root.addEventListener('scroll', cancel, true);
  root.addEventListener('click', e => { if (fired && e.target.closest(selector)) { e.stopImmediatePropagation(); e.preventDefault(); fired = false; } }, true);
  root.addEventListener('contextmenu', e => { const el = e.target.closest(selector); if (!el) return; e.preventDefault(); cancel(); if (!fired) fire(el); });
}

// Android back button / gesture: keep one guard history entry and close the topmost thing.
const Back = {
  on: !IN_FRAME, armed: false,
  init() { if (!this.on) return; this.arm(); addEventListener('popstate', () => { this.armed = false; if (handleBack()) this.arm(); }); },
  arm() { if (!this.on || this.armed) return; try { history.pushState({ g: 1 }, ''); this.armed = true; } catch (e) {} },
};
function handleBack() {
  if (OV.length) { OV[OV.length - 1](); return true; }
  if (V.on) { V.close(); return true; }
  const p = topPage();
  if (p && p.selecting) { setSelecting(p, false); return true; }
  if (Tabs.stacks[Tabs.cur].length > 1) { pop(); return true; }
  if (Tabs.cur !== 'library') { switchTab('library'); return true; }
  return false;
}
