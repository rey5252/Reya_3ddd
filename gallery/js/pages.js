'use strict';
// ---------- navigation ----------
const Tabs = { cur: 'library', stacks: { library: [], albums: [], search: [] }, views: {} };
const topPage = (t = Tabs.cur) => { const s = Tabs.stacks[t]; return s[s.length - 1]; };
const ensureFresh = p => { if (p && p.dirty) p.refresh(); };

function invalidate({ flip = false } = {}) {
  for (const t in Tabs.stacks) for (const p of Tabs.stacks[t]) p.dirty = true;
  const p = topPage();
  if (p) { if (flip && p.hasGrid) flipCells(p, () => p.refresh()); else p.refresh(); }
  if (V.on) V.markHole();
}

function push(page, { animate = true } = {}) {
  const t = Tabs.cur, stack = Tabs.stacks[t], prev = stack[stack.length - 1];
  exitSelecting(prev);
  page.tab = t; stack.push(page);
  if (prev) page.backLabel = prev.title;
  Tabs.views[t].appendChild(page.el);
  page.refresh(); page.renderNav();
  if (prev) {
    Back.arm();
    if (animate && !RM) {
      page.el.classList.add('pushing');
      anim(page.el, [{ transform: 'translateX(100%)' }, { transform: 'translateX(0)' }]).then(() => page.el.classList.remove('pushing'));
      anim(prev.el, [{ transform: 'translateX(0)' }, { transform: 'translateX(-30%)' }]).then(() => { if (topPage(t) !== prev) prev.el.classList.add('under'); });
    } else { prev.el.style.transform = 'translateX(-30%)'; prev.el.classList.add('under'); }
  }
}

function pop({ animate = true } = {}) {
  const stack = Tabs.stacks[Tabs.cur];
  if (stack.length < 2) return;
  const page = stack.pop(), prev = stack[stack.length - 1];
  exitSelecting(page);
  prev.el.classList.remove('under'); ensureFresh(prev); prev.renderNav();
  const done = () => page.el.remove();
  if (animate && !RM) {
    page.el.classList.add('pushing');
    anim(page.el, [{ transform: page.el.style.transform || 'translateX(0)' }, { transform: 'translateX(100%)' }], { duration: 400 }).then(done);
    anim(prev.el, [{ transform: prev.el.style.transform || 'translateX(-30%)' }, { transform: 'translateX(0)' }], { duration: 400 });
  } else { done(); prev.el.style.transform = 'translateX(0)'; }
}

function switchTab(t) {
  if (t === Tabs.cur) {
    const st = Tabs.stacks[t];
    if (st.length > 1) { while (st.length > 2) st.splice(st.length - 2, 1)[0].el.remove(); pop(); }
    else st[0].scrollHome();
    return;
  }
  exitSelecting(topPage());
  Tabs.views[Tabs.cur].hidden = true;
  const nu = Tabs.views[t]; nu.hidden = false; Tabs.cur = t;
  ensureFresh(topPage(t));
  if (!RM) nu.animate([{ opacity: 0, transform: 'scale(.985)' }, { opacity: 1, transform: 'none' }], { duration: 220, easing: 'ease-out' });
  $$('#tabbar .tab').forEach(b => { const on = b.dataset.tab === t; b.classList.toggle('active', on); b.setAttribute('aria-selected', on); });
  topPage(t).onShow();
}

// Swipe from the left edge to go back, like iOS.
function initEdgeSwipe() {
  const app = $('#app'); let sw = null;
  app.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse' || e.clientX > 24 || V.on || OV.length) return;
    const st = Tabs.stacks[Tabs.cur]; if (st.length < 2) return;
    const page = st[st.length - 1]; if (page.selecting) return;
    sw = { x0: e.clientX, y0: e.clientY, id: e.pointerId, page, prev: st[st.length - 2], active: false, lx: e.clientX, lt: performance.now(), v: 0 };
  }, true);
  app.addEventListener('pointermove', e => {
    if (!sw || e.pointerId !== sw.id) return;
    const dx = e.clientX - sw.x0, dy = e.clientY - sw.y0;
    if (!sw.active) {
      if (Math.abs(dy) > 12 && Math.abs(dy) > Math.abs(dx)) { sw = null; return; }
      if (dx < 10) return;
      sw.active = true; sw.prev.el.classList.remove('under'); ensureFresh(sw.prev); sw.page.el.classList.add('pushing');
      try { app.setPointerCapture(e.pointerId); } catch (_) {}
    }
    const W = innerWidth, x = Math.max(0, dx);
    sw.page.el.style.transform = `translateX(${x}px)`;
    sw.prev.el.style.transform = `translateX(${-30 + (x / W) * 30}%)`;
    const now = performance.now(); sw.v = (e.clientX - sw.lx) / Math.max(1, now - sw.lt); sw.lx = e.clientX; sw.lt = now;
  }, true);
  const end = e => {
    if (!sw || e.pointerId !== sw.id) return;
    const s = sw; sw = null; if (!s.active) return;
    if (e.clientX - s.x0 > innerWidth * .4 || s.v > .35) pop();
    else {
      anim(s.page.el, [{ transform: s.page.el.style.transform }, { transform: 'translateX(0)' }], { duration: 320 }).then(() => s.page.el.classList.remove('pushing'));
      anim(s.prev.el, [{ transform: s.prev.el.style.transform }, { transform: 'translateX(-30%)' }], { duration: 320 }).then(() => s.prev.el.classList.add('under'));
    }
  };
  app.addEventListener('pointerup', end, true); app.addEventListener('pointercancel', end, true);
}

// ---------- page base ----------
function navBtn(html, fn, cls = '', label = '') {
  const b = h(`<button class="nav-btn ${cls}" ${label ? `aria-label="${esc(label)}"` : ''}>${html}</button>`);
  b.addEventListener('click', fn);
  return b;
}
class Page {
  constructor({ title = '', large = true, cls = '' } = {}) {
    this.title = title; this.large = large; this.sel = new Set(); this.selecting = false; this.dirty = true;
    this.el = h(`<section class="page ${cls}${large ? '' : ' small'}"><header class="navbar"><div class="nav-l"></div><div class="nav-title"></div><div class="nav-r"></div></header><div class="scroller"><div class="lt"><h1 class="large-title"></h1><div class="lt-sub"></div></div><div class="content"></div></div></section>`);
    this.navL = $('.nav-l', this.el); this.navR = $('.nav-r', this.el); this.navTitle = $('.nav-title', this.el);
    this.scroller = $('.scroller', this.el); this.content = $('.content', this.el);
    this.scroller.addEventListener('scroll', () => this.onScroll(), { passive: true });
    this.setTitle(title);
  }
  onScroll() { this.el.classList.toggle('scrolled', this.scroller.scrollTop > (this.large ? 44 : 1)); }
  setTitle(t, sub) {
    this.title = t; $('.large-title', this.el).textContent = t;
    if (sub !== undefined) $('.lt-sub', this.el).textContent = sub;
    this.updateSelTitle();
  }
  renderNav() {
    this.navL.innerHTML = ''; this.navR.innerHTML = '';
    if (this.backLabel) this.navL.append(navBtn(`${I.back}<span>${esc(this.backLabel)}</span>`, () => pop(), 'back', 'Назад'));
    else this.leftButtons().forEach(b => this.navL.append(b));
    if (this.selecting) this.navR.append(navBtn('Скасувати', () => setSelecting(this, false), 'pill strong'));
    else this.rightButtons().forEach(b => this.navR.append(b));
    this.updateSelTitle();
  }
  leftButtons() { return []; }
  rightButtons() { return []; }
  updateSelTitle() { this.navTitle.textContent = this.selecting ? (this.sel.size ? `Вибрано: ${this.sel.size}` : 'Виберіть об’єкти') : this.title; }
  refresh() { this.dirty = false; this.update(); }
  update() {}
  onShow() {}
  scrollHome() { this.scroller.scrollTo({ top: 0, behavior: RM ? 'auto' : 'smooth' }); }
}

// ---------- grid ----------
const COLS = [2, 3, 4, 5, 7, 9, 12];
const defaultCols = () => { const w = innerWidth; return w < 500 ? 3 : w < 800 ? 5 : w < 1200 ? 7 : 9; };
function cellHTML(it, sel, opts = {}) {
  const t = turl(it);
  const days = it.deleted ? Math.max(0, 30 - Math.floor((Date.now() - it.deleted) / 864e5)) : 0;
  return `<div class="cell${sel && sel.has(it.id) ? ' sel' : ''}${opts.in && opts.in.has(it.id) ? ' in' : ''}" data-id="${it.id}" role="button" tabindex="-1" aria-label="${esc(it.name)}">`
    + (t ? `<img src="${t}" loading="lazy" decoding="async" alt="">` : `<span class="noprev">${esc(ext(it.name) || it.kind)}</span>`)
    + (it.kind === 'video' ? `<span class="dur">${fmtDur(it.duration)}</span>` : '')
    + (it.fav && !it.deleted ? `<span class="fav">${I.heartFill}</span>` : '')
    + (it.deleted ? `<span class="days">${days} дн.</span>` : '')
    + `<span class="check">${I.check}</span></div>`;
}
function gridHTML(items, { months = false, sel } = {}) {
  if (!items.length) return '';
  if (!months) return `<div class="grid">${items.map(i => cellHTML(i, sel)).join('')}</div>`;
  let out = '', cur = null;
  for (const it of items) {
    const k = monthKey(it.date);
    if (k !== cur) {
      if (cur !== null) out += '</div>';
      const d = new Date(it.date);
      out += `<div class="month-h" data-month="${k}">${MONTHS[d.getMonth()]} <span>${d.getFullYear()}</span></div><div class="grid">`;
      cur = k;
    }
    out += cellHTML(it, sel);
  }
  return out + '</div>';
}
const emptyHTML = (icon, title, text, btn, act) => `<div class="empty">${icon}<b>${esc(title)}</b>${text ? `<p>${esc(text)}</p>` : ''}${btn ? `<button class="pill" data-act="${act}">${esc(btn)}</button>` : ''}</div>`;
const cellOf = (page, id) => page.content.querySelector(`.cell[data-id="${id}"]`);

// Animate every visible cell from its old box to its new one (FLIP).
function flipCells(page, mutate) {
  if (RM) return mutate();
  const before = new Map();
  for (const c of $$('.cell', page.content)) { const r = c.getBoundingClientRect(); if (r.bottom > -60 && r.top < innerHeight + 60) before.set(c.dataset.id, r); }
  mutate();
  for (const c of $$('.cell', page.content)) {
    const a = c.getBoundingClientRect();
    if (!a.width || a.bottom < -60 || a.top > innerHeight + 60) continue;
    const b = before.get(c.dataset.id);
    if (b) {
      const dx = b.left - a.left, dy = b.top - a.top, s = b.width / a.width;
      if (Math.abs(dx) < 1 && Math.abs(dy) < 1 && Math.abs(s - 1) < .01) continue;
      c.animate([{ transform: `translate(${dx}px,${dy}px) scale(${s})` }, { transform: 'none' }], { duration: 480, easing: EASE });
    } else c.animate([{ opacity: 0, transform: 'scale(.85)' }, { opacity: 1, transform: 'none' }], { duration: 380, easing: EASE });
  }
}

function setSelecting(page, on) {
  if (!page || page.selecting === on) return;
  page.selecting = on; page.el.classList.toggle('selecting', on);
  if (!on) { page.sel.clear(); $$('.cell.sel', page.el).forEach(c => c.classList.remove('sel')); }
  document.body.classList.toggle('selecting', on);
  page.renderNav(); renderSelbar();
}
const exitSelecting = p => { if (p && p.selecting) setSelecting(p, false); };
function toggleSel(page, cell, val) {
  const id = cell.dataset.id, on = val ?? !page.sel.has(id);
  on ? page.sel.add(id) : page.sel.delete(id);
  cell.classList.toggle('sel', on);
}
function selChanged(page) { page.updateSelTitle(); renderSelbar(); }

function attachGrid(page) {
  page.hasGrid = true;
  const root = page.content;
  longPress(root, '.cell', cell => { if (!page.selecting) photoMenu(page, cell); });
  root.addEventListener('click', e => {
    const cell = e.target.closest('.cell'); if (!cell) return;
    if (page.dragJustEnded) return;
    if (page.selecting) { toggleSel(page, cell); selChanged(page); haptic(5); }
    else { const ids = $$('.cell', root).map(c => c.dataset.id); V.open(page, ids, ids.indexOf(cell.dataset.id), cell); }
  });
  // drag across cells to select a range (start the drag sideways, then move freely)
  let ds = null, raf = 0, pt = null;
  root.addEventListener('pointerdown', e => {
    if (!page.selecting) return;
    const cell = e.target.closest('.cell'); if (!cell) return;
    ds = { x: e.clientX, y: e.clientY, id: e.pointerId, start: cell, active: false };
  });
  root.addEventListener('pointermove', e => {
    if (!ds || e.pointerId !== ds.id) return;
    pt = { x: e.clientX, y: e.clientY };
    if (ds.active) return;
    const dx = e.clientX - ds.x, dy = e.clientY - ds.y;
    if (Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.2) {
      ds.active = true; try { root.setPointerCapture(e.pointerId); } catch (_) {}
      ds.cells = $$('.cell', root); ds.index = new Map(ds.cells.map((c, i) => [c, i]));
      ds.from = ds.index.get(ds.start); ds.mode = !page.sel.has(ds.start.dataset.id); ds.orig = new Set(page.sel); ds.last = -1;
      haptic(6);
      const loop = () => { if (!ds || !ds.active) return; autoScroll(); dragAt(pt.x, pt.y); raf = requestAnimationFrame(loop); };
      raf = requestAnimationFrame(loop);
    } else if (Math.abs(dy) > 10) ds = null;
  });
  function dragAt(x, y) {
    const el = document.elementFromPoint(x, y), cell = el && el.closest && el.closest('.cell');
    if (!cell || !ds.index.has(cell)) return;
    const i = ds.index.get(cell); if (i === ds.last) return; ds.last = i;
    const a = Math.min(ds.from, i), b = Math.max(ds.from, i);
    ds.cells.forEach((c, k) => { const id = c.dataset.id, want = k >= a && k <= b ? ds.mode : ds.orig.has(id); if (want !== page.sel.has(id)) toggleSel(page, c, want); });
    selChanged(page);
  }
  function autoScroll() {
    const r = page.scroller.getBoundingClientRect(), top = r.top + SAFE.top + 90, bot = r.bottom - SAFE.bot - 120;
    let v = 0; if (pt.y < top) v = -Math.min(20, (top - pt.y) / 3); else if (pt.y > bot) v = Math.min(20, (pt.y - bot) / 3);
    if (v) page.scroller.scrollTop += v;
  }
  const end = e => {
    if (!ds || e.pointerId !== ds.id) return;
    if (ds.active) { cancelAnimationFrame(raf); page.dragJustEnded = true; setTimeout(() => { page.dragJustEnded = false; }, 60); }
    ds = null;
  };
  root.addEventListener('pointerup', end); root.addEventListener('pointercancel', end);
}

// Pinch (touch) or ctrl+wheel (trackpad) to change how many photos fit in a row.
function attachPinch(page) {
  const sc = page.scroller, pts = new Map(); let base = 0, acc = 0;
  sc.addEventListener('pointerdown', e => {
    if (e.pointerType !== 'touch') return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pts.size === 2) { const [a, b] = [...pts.values()]; base = Math.hypot(a.x - b.x, a.y - b.y); }
  });
  sc.addEventListener('pointermove', e => {
    if (!pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pts.size !== 2 || !base) return;
    const [a, b] = [...pts.values()], d = Math.hypot(a.x - b.x, a.y - b.y), c = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
    if (d / base > 1.35) { stepCols(page, -1, c); base = d; } else if (d / base < .72) { stepCols(page, 1, c); base = d; }
  });
  const up = e => { pts.delete(e.pointerId); if (pts.size < 2) base = 0; };
  sc.addEventListener('pointerup', up); sc.addEventListener('pointercancel', up);
  sc.addEventListener('wheel', e => {
    if (!e.ctrlKey) return; e.preventDefault(); acc += e.deltaY;
    if (acc < -40) { stepCols(page, -1, { x: e.clientX, y: e.clientY }); acc = 0; } else if (acc > 40) { stepCols(page, 1, { x: e.clientX, y: e.clientY }); acc = 0; }
  }, { passive: false });
}
function stepCols(page, dir, c) {
  if (page.mode && page.mode !== 'all') {
    const seq = ['years', 'months', 'all'], i = seq.indexOf(page.mode) - dir;
    if (i >= 0 && i < 3) page.setMode(seq[i]);
    return;
  }
  let i = COLS.indexOf(page.cols); if (i < 0) i = 1;
  const ni = clamp(i + dir, 0, COLS.length - 1);
  if (ni === i) { if (dir > 0 && page.mode === 'all') page.setMode('months'); return; }
  setCols(page, COLS[ni], c);
}
function setCols(page, n, c) {
  const sc = page.scroller;
  let anchor = c && document.elementFromPoint(c.x, c.y);
  anchor = anchor && anchor.closest ? anchor.closest('.cell') : null;
  if (anchor && !page.content.contains(anchor)) anchor = null;
  const off = anchor ? anchor.getBoundingClientRect().top : 0;
  haptic(6);
  flipCells(page, () => {
    applyCols(page, n); LS.set('cols', n);
    if (anchor) sc.scrollTop += anchor.getBoundingClientRect().top - off;
  });
}
function applyCols(page, n) { page.cols = n; page.content.style.setProperty('--cols', n); page.content.classList.toggle('dense', n >= 7); }

// ---------- selection bar ----------
function renderSelbar() {
  const p = topPage(), bar = $('#selbar');
  if (!p || !p.selecting) return;
  const ids = [...p.sel], n = ids.length;
  const label = n ? countText(itemsOf(ids)) : 'Виберіть об’єкти';
  if (p.smart === 'deleted') {
    bar.innerHTML = `<button class="sb-text" data-a="restore">${n ? 'Відновити' : 'Відновити все'}</button><div class="sb-label">${n ? esc(label) : ''}</div><button class="sb-text danger" data-a="purge">${n ? 'Видалити' : 'Видалити все'}</button>`;
  } else {
    const allFav = n && ids.every(id => S.byId.get(id)?.fav), dis = n ? '' : 'disabled';
    bar.innerHTML = `<button class="sb-btn" data-a="share" ${dis} aria-label="Поділитися">${I.share}</button><div class="sb-label">${esc(label)}</div><button class="sb-btn" data-a="fav" ${dis} aria-label="Улюблене">${allFav ? I.heartFill : I.heart}</button><button class="sb-btn" data-a="album" ${dis} aria-label="Додати в альбом">${I.albumAdd}</button><button class="sb-btn" data-a="del" ${dis} aria-label="Видалити">${I.trash}</button>`;
  }
}
async function onSelbar(e) {
  const b = e.target.closest('button'); if (!b || b.disabled) return;
  const p = topPage(), ids = [...p.sel], a = b.dataset.a;
  if (a === 'share') shareItems(ids);
  else if (a === 'fav') {
    const val = !ids.every(id => S.byId.get(id)?.fav);
    if (val) { const r = b.getBoundingClientRect(); FX.hearts(r.left + r.width / 2, r.top + r.height / 2); }
    setSelecting(p, false); await setFav(ids, val);
    toast(val ? 'Додано до улюблених' : 'Прибрано з улюблених', val ? 'heartFill' : 'heart');
  } else if (a === 'album') addToAlbumSheet(ids, () => setSelecting(p, false));
  else if (a === 'del') deleteFlow(p, ids);
  else if (a === 'restore') {
    const list = ids.length ? ids : SMART.deleted.get().map(i => i.id); if (!list.length) return;
    dissolveCells(p, list); await sleep(RM ? 0 : 140);
    await restoreItems(list); setSelecting(p, false); invalidate({ flip: true }); toast(`Відновлено: ${list.length}`, 'restore');
  } else if (a === 'purge') purgeFlow(p, ids.length ? ids : SMART.deleted.get().map(i => i.id));
}

function dissolveCells(page, ids) {
  const set = new Set(ids);
  const cells = $$('.cell', page.content).filter(c => set.has(c.dataset.id));
  const vis = cells.filter(c => { const r = c.getBoundingClientRect(); return r.width && r.bottom > 0 && r.top < innerHeight; }).slice(0, 80);
  const budget = Math.max(60, Math.floor(2600 / Math.max(1, vis.length)));
  vis.forEach(c => FX.dissolve(c, c.querySelector('img'), { budget, cover: true }));
  cells.forEach(c => { c.style.opacity = '0'; });
}

async function deleteFlow(page, ids, { fromLibrary = false } = {}) {
  if (!ids.length) return;
  const n = ids.length, inAlbum = page.album && !fromLibrary;
  const choice = inAlbum
    ? await actionSheet({ actions: [{ label: 'Прибрати з альбому', value: 'remove' }, { label: n === 1 ? 'Видалити з медіатеки' : `Видалити ${nItems(n)} з медіатеки`, destructive: true, value: 'trash' }] })
    : await actionSheet({ title: `${n === 1 ? 'Цей об’єкт' : nItems(n)} буде переміщено в «Нещодавно видалені». Там вони зберігаються 30 днів.`, actions: [{ label: n === 1 ? 'Видалити' : `Видалити ${nItems(n)}`, destructive: true, value: 'trash' }] });
  if (!choice) return;
  dissolveCells(page, ids);
  await sleep(RM ? 0 : 140);
  if (choice === 'remove') await removeFromAlbum(page.album, ids); else await trashItems(ids);
  setSelecting(page, false);
  invalidate({ flip: true });
  toast(choice === 'remove' ? 'Прибрано з альбому' : 'Переміщено в «Нещодавно видалені»', 'trash');
}
async function purgeFlow(page, ids) {
  if (!ids.length) return;
  const ok = await actionSheet({ title: `${ids.length === 1 ? 'Цей об’єкт' : nItems(ids.length)} буде видалено назавжди. Цю дію не можна скасувати.`, actions: [{ label: 'Видалити назавжди', destructive: true }] });
  if (!ok) return;
  dissolveCells(page, ids); await sleep(RM ? 0 : 140);
  await purgeItems(ids); setSelecting(page, false); invalidate({ flip: true });
}

function photoMenu(page, cell) {
  const it = S.byId.get(cell.dataset.id); if (!it) return;
  let items;
  if (it.deleted) items = [
    { label: 'Відновити', icon: 'restore', run: async () => { await restoreItems([it.id]); invalidate({ flip: true }); toast('Відновлено', 'restore'); } },
    { label: 'Видалити назавжди', icon: 'trash', destructive: true, run: () => purgeFlow(page, [it.id]) },
  ];
  else items = [
    { label: 'Поділитися', icon: 'share', run: () => shareItems([it.id]) },
    ...(NATIVE ? [{ label: 'Зберегти в галерею телефона', icon: 'download', run: () => downloadItem(it) }] : []),
    { label: it.fav ? 'Прибрати з улюблених' : 'Додати до улюблених', icon: it.fav ? 'heart' : 'heartFill', run: () => { if (!it.fav) { const c = cellOf(page, it.id); if (c) { const r = c.getBoundingClientRect(); FX.hearts(r.left + r.width / 2, r.top + r.height / 2); } } setFav([it.id], !it.fav); } },
    { label: 'Додати в альбом', icon: 'albumAdd', run: () => addToAlbumSheet([it.id]) },
    { label: 'Вибрати', icon: 'select', run: () => { setSelecting(page, true); const c = cellOf(page, it.id); if (c) { toggleSel(page, c, true); selChanged(page); } } },
    ...(page.album ? [{ sep: true, label: 'Прибрати з альбому', icon: 'x', run: async () => { dissolveCells(page, [it.id]); await sleep(140); await removeFromAlbum(page.album, [it.id]); invalidate({ flip: true }); } }] : []),
    { sep: !page.album, label: page.album ? 'Видалити з медіатеки' : 'Видалити', icon: 'trash', destructive: true, run: () => deleteFlow(page, [it.id], { fromLibrary: true }) },
  ];
  ctxMenu(cell, { previewItem: it, items });
}

// Android app: hand files to the native side in base64 chunks, then share or save them there.
async function nativeExport(its, action) {
  const ids = [], CH = 3 * 256 * 1024;
  if (its.length > 1 || its.some(i => i.size > 8e6)) toast(action === 'share' ? 'Готуємо файли…' : 'Зберігаємо…', 'import');
  for (const it of its) {
    const b = await getBlob(it); if (!b) continue;
    const id = NATIVE.beginFile(it.name, it.mime || b.type || ''); if (!id) continue;
    for (let o = 0; o < b.size; o += CH) {
      const b64 = await new Promise((res, rej) => { const r = new FileReader(); r.onload = () => res(String(r.result).split(',')[1] || ''); r.onerror = rej; r.readAsDataURL(b.slice(o, o + CH)); });
      NATIVE.appendChunk(id, b64);
    }
    NATIVE.endFile(id); ids.push(id);
  }
  if (ids.length) action === 'share' ? NATIVE.share(JSON.stringify(ids)) : NATIVE.save(JSON.stringify(ids));
}
async function shareItems(ids) {
  if (NATIVE) { nativeExport(itemsOf(ids), 'share').catch(() => toast('Не вдалося поділитися', 'info')); return; }
  try {
    const files = [];
    for (const it of itemsOf(ids)) { const b = await getBlob(it); if (b) files.push(new File([b], it.name, { type: it.mime || b.type })); }
    if (files.length && navigator.canShare && navigator.canShare({ files })) await navigator.share({ files });
    else toast('Цей браузер не вміє ділитися файлами', 'info');
  } catch (e) { if (e && e.name !== 'AbortError') toast('Не вдалося поділитися', 'info'); }
}
async function downloadItem(it) {
  if (NATIVE) { nativeExport([it], 'save').catch(() => toast('Не вдалося зберегти', 'info')); return; }
  const u = await fullURL(it); if (!u) return;
  const a = document.createElement('a'); a.href = u; a.download = it.name; document.body.append(a); a.click(); a.remove();
}

// ---------- album / folder flows ----------
function addToAlbumSheet(ids, after) {
  const sh = openSheet({ title: ids.length > 1 ? `Додати ${nItems(ids.length)} в альбом` : 'Додати в альбом' });
  sh.setLeft('Скасувати', () => sh.close());
  const list = S.albums.slice().sort((a, b) => b.created - a.created);
  sh.body.innerHTML = `<div class="rows"><button class="row big" data-new><span class="r-cover">${I.plus}</span><span class="r-label"><b>Новий альбом…</b></span></button>${list.map(a => {
    const its = albumItems(a), u = turl(its[its.length - 1]), path = folderPath(a.parent);
    return `<button class="row big" data-id="${a.id}"><span class="r-cover">${u ? `<img src="${u}" alt="">` : I.photo}</span><span class="r-label"><b>${esc(a.name)}</b><small>${path ? esc(path) + ' · ' : ''}${its.length}</small></span></button>`;
  }).join('')}</div>`;
  sh.body.addEventListener('click', async e => {
    const b = e.target.closest('.row'); if (!b) return;
    let a;
    if (b.hasAttribute('data-new')) { const name = await askText({ title: 'Новий альбом', message: 'Введіть назву альбому.', placeholder: 'Назва' }); if (!name) return; a = createAlbum(name); }
    else a = albumById(b.dataset.id);
    if (!a) return;
    const n = await addToAlbum(a, ids);
    sh.close(); after && after();
    toast(n ? `Додано в «${a.name}»` : `Усе вже є в «${a.name}»`, 'check');
  });
}
function pickFromLibrary(a) {
  const its = live(); if (!its.length) { openImport({ album: a.id }); return; }
  const sh = openSheet({ title: 'Виберіть фото', cls: 'tall' });
  const sel = new Set(), inAlb = new Set(a.items);
  sh.setLeft('Скасувати', () => sh.close());
  const upd = () => sh.setRight(sel.size ? `Додати (${sel.size})` : 'Додати', async () => { if (!sel.size) return; const n = await addToAlbum(a, [...sel]); sh.close(); toast(`Додано в «${a.name}»: ${n}`, 'check'); }, true, !sel.size);
  upd();
  sh.body.classList.add('selecting');
  sh.body.innerHTML = `<div class="pick-grid">${its.map(i => cellHTML(i, sel, { in: inAlb })).join('')}</div>`;
  requestAnimationFrame(() => { sh.body.scrollTop = sh.body.scrollHeight; });
  sh.body.addEventListener('click', e => {
    const c = e.target.closest('.cell'); if (!c) return;
    const id = c.dataset.id; sel.has(id) ? sel.delete(id) : sel.add(id);
    c.classList.toggle('sel', sel.has(id)); haptic(5); upd();
  });
}
async function renameNode(kind, obj) {
  const name = await askText({ title: kind === 'album' ? 'Перейменувати альбом' : 'Перейменувати папку', value: obj.name });
  if (!name || name === obj.name) return;
  obj.name = name; kind === 'album' ? saveAlbums(obj) : saveFolders(obj);
  invalidate();
}
async function deleteAlbumFlow(a) {
  const ok = await actionSheet({ title: `Видалити альбом «${a.name}»? Фото залишаться в медіатеці.`, actions: [{ label: 'Видалити альбом', destructive: true }] });
  if (ok) { await deleteAlbum(a); toast('Альбом видалено', 'trash'); }
}
async function deleteFolderFlow(f) {
  const ok = await actionSheet({ title: `Видалити папку «${f.name}» разом з альбомами всередині? Фото залишаться в медіатеці.`, actions: [{ label: 'Видалити папку', destructive: true }] });
  if (ok) { await deleteFolder(f); toast('Папку видалено', 'trash'); }
}
function pickFolder({ exclude = null } = {}) {
  return new Promise(res => {
    const sh = openSheet({ title: 'Перемістити в…' });
    let result; sh.onClose = () => res(result);
    sh.setLeft('Скасувати', () => sh.close());
    const rows = [];
    const walk = (parent, depth) => S.folders.filter(f => f.parent === parent).sort((a, b) => a.name.localeCompare(b.name, 'uk')).forEach(f => { if (exclude && isInside(f.id, exclude)) return; rows.push({ f, depth }); walk(f.id, depth + 1); });
    walk(null, 0);
    sh.body.innerHTML = `<div class="rows"><button class="row" data-id="">${I.albums}<span class="r-label">Мої альбоми (верхній рівень)</span></button>${rows.map(({ f, depth }) => `<button class="row" data-id="${f.id}" style="padding-left:${14 + depth * 22}px">${I.folder}<span class="r-label">${esc(f.name)}</span></button>`).join('')}</div>`;
    sh.body.addEventListener('click', e => { const b = e.target.closest('.row'); if (!b) return; result = b.dataset.id || null; sh.close(); });
  });
}
async function moveNode(kind, obj) {
  const target = await pickFolder({ exclude: kind === 'folder' ? obj.id : null });
  if (target === undefined || target === obj.parent) return;
  obj.parent = target; kind === 'album' ? saveAlbums(obj) : saveFolders(obj);
  invalidate();
  toast(`Переміщено в «${target ? folderById(target).name : 'Мої альбоми'}»`, 'move');
}
async function newAlbumFlow(parent) {
  const n = await askText({ title: 'Новий альбом', message: 'Введіть назву альбому.', placeholder: 'Назва' });
  if (!n) return;
  const a = createAlbum(n, parent); invalidate(); pickFromLibrary(a);
}
async function newFolderFlow(parent) {
  const n = await askText({ title: 'Нова папка', message: 'Введіть назву папки.', placeholder: 'Назва' });
  if (n) { createFolder(n, parent); invalidate(); }
}
function tileMenu(tile) {
  const kind = tile.dataset.kind, obj = kind === 'album' ? albumById(tile.dataset.id) : folderById(tile.dataset.id);
  if (!obj) return;
  const cover = tile.querySelector('.cover'), clone = cover.cloneNode(true); clone.classList.add('ctx-clone');
  const its = kind === 'album' ? albumItems(obj) : [];
  ctxMenu(cover, { previewEl: clone, items: [
    ...(its.some(i => i.kind === 'image') ? [{ label: 'Слайд-шоу', icon: 'slides', run: () => slideshow(its) }] : []),
    { label: 'Перейменувати', icon: 'pencil', run: () => renameNode(kind, obj) },
    { label: 'Перемістити в папку', icon: 'move', run: () => moveNode(kind, obj) },
    { sep: true, label: kind === 'album' ? 'Видалити альбом' : 'Видалити папку', icon: 'trash', destructive: true, run: () => kind === 'album' ? deleteAlbumFlow(obj) : deleteFolderFlow(obj) },
  ] });
}

function albumTile(a) {
  const its = albumItems(a), u = turl(its[its.length - 1]);
  return `<button class="tile" data-kind="album" data-id="${a.id}"><span class="cover">${u ? `<img src="${u}" alt="" loading="lazy">` : `<span class="cover-empty">${I.photo}</span>`}</span><span class="t-name">${esc(a.name)}</span><span class="t-count">${its.length}${a.sample ? ' · приклад' : ''}</span></button>`;
}
function folderTile(f) {
  const cov = folderCovers(f.id);
  const n = S.folders.filter(x => x.parent === f.id).length + S.albums.filter(x => x.parent === f.id).length;
  return `<button class="tile" data-kind="folder" data-id="${f.id}"><span class="cover folder">${[0, 1, 2, 3].map(i => turl(cov[i]) ? `<img src="${turl(cov[i])}" alt="" loading="lazy">` : '<span></span>').join('')}<span class="f-badge">${I.folder}</span></span><span class="t-name">${esc(f.name)}</span><span class="t-count">${n} ${plural(n, 'елемент', 'елементи', 'елементів')}${f.sample ? ' · приклад' : ''}</span></button>`;
}
function smartRow(k) {
  const s = SMART[k], n = s.get().length;
  return `<button class="row${k === 'deleted' ? '' : ''}" data-smart="${k}">${I[s.icon]}<span class="r-label">${s.name}</span><span class="r-val">${n}</span>${I.chev}</button>`;
}
// Clicks on album tiles / smart rows inside any page.
function navClick(e) {
  const t = e.target.closest('.tile');
  if (t) { if (t.dataset.kind === 'album') push(new AlbumPage({ album: t.dataset.id })); else push(new AlbumsPage(t.dataset.id)); return true; }
  const s = e.target.closest('[data-smart]');
  if (s) { push(new AlbumPage({ smart: s.dataset.smart })); return true; }
  return false;
}

// ---------- Library ----------
class LibraryPage extends Page {
  constructor() {
    super({ title: 'Медіатека', cls: 'lib' });
    this.mode = 'all'; applyCols(this, LS.get('cols', defaultCols())); this.first = true;
    this.seg = h(`<div class="seg" role="tablist"><span class="knob"></span><button data-m="years" role="tab">Роки</button><button data-m="months" role="tab">Місяці</button><button data-m="all" role="tab" class="on">Усі фото</button></div>`);
    this.el.append(this.seg);
    this.seg.addEventListener('click', e => { const b = e.target.closest('button'); if (b) this.setMode(b.dataset.m); });
    attachGrid(this); attachPinch(this);
    this.content.addEventListener('click', e => {
      const c = e.target.closest('[data-year],[data-mon]'); if (!c) return;
      if (c.dataset.year) this.setMode('months', { year: c.dataset.year }); else this.setMode('all', { month: c.dataset.mon });
    });
  }
  rightButtons() { return [navBtn('Вибрати', () => { if (this.mode !== 'all') this.setMode('all'); setSelecting(this, true); }, 'pill'), navBtn(I.plus, () => openImport(), 'circle', 'Додати фото')]; }
  update() {
    const items = live(), sc = this.scroller;
    const atEnd = sc.scrollHeight - sc.scrollTop - sc.clientHeight < 80;
    this.setTitle('Медіатека', items.length ? countText(items) : '');
    this.content.innerHTML = this.render(items);
    if (this.first || atEnd) { this.toEnd(); this.first = false; }
    this.updateHint(); this.moveKnob(); this.renderNav();
  }
  render(items) {
    if (!items.length) return emptyHTML(I.photo, 'Немає фото', 'Додайте фото, відео або цілі папки з телефона.', 'Додати фото', 'import');
    if (this.mode === 'all') return gridHTML(items, { months: true, sel: this.sel });
    const groups = new Map();
    for (const it of items) { const k = this.mode === 'years' ? String(new Date(it.date).getFullYear()) : monthKey(it.date); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(it); }
    const key = its => its.find(i => i.fav && i.thumb) || its.filter(i => i.thumb)[Math.floor(its.filter(i => i.thumb).length / 2)];
    let out = '<div class="cards">', lastY = null;
    for (const [k, its] of groups) {
      if (this.mode === 'years') {
        const u = turl(key(its));
        out += `<button class="ycard" data-year="${k}">${u ? `<img src="${u}" alt="" loading="lazy">` : ''}<span class="cap"><b>${k}</b><small>${countText(its)}</small></span></button>`;
      } else {
        const [y, m] = k.split('-').map(Number);
        if (y !== lastY) { out += `<div class="year-h" data-yh="${y}">${y}</div>`; lastY = y; }
        const withT = its.filter(i => i.thumb), main = key(its), rest = withT.filter(i => i !== main).slice(0, 2);
        const pics = rest.length === 2 ? `<span class="mosaic">${[main, ...rest].map(i => `<img src="${turl(i)}" alt="" loading="lazy">`).join('')}</span>` : (turl(main) ? `<img src="${turl(main)}" alt="" loading="lazy">` : '');
        out += `<button class="ycard m" data-mon="${k}">${pics}<span class="cap"><b>${MONTHS[m]}</b><small>${y} · ${countText(its)}</small></span></button>`;
      }
    }
    return out + '</div>';
  }
  async setMode(m, { year, month } = {}) {
    if (m === this.mode && !year && !month) { this.toEnd(true); return; }
    const order = { years: 0, months: 1, all: 2 }, deeper = order[m] >= order[this.mode];
    exitSelecting(this); haptic(6);
    const sc = this.scroller;
    let a1 = null;
    if (!RM) { a1 = sc.animate([{ opacity: 1, transform: 'scale(1)' }, { opacity: 0, transform: `scale(${deeper ? .94 : 1.06})` }], { duration: 150, easing: 'ease-in', fill: 'forwards' }); await a1.finished.catch(() => {}); }
    this.mode = m;
    $$('button', this.seg).forEach(b => b.classList.toggle('on', b.dataset.m === m));
    this.content.innerHTML = this.render(live()); this.dirty = false;
    const navH = $('.navbar', this.el).offsetHeight;
    const target = year ? $(`[data-yh="${year}"]`, this.content) : month ? $(`.month-h[data-month="${month}"]`, this.content) : null;
    if (target) sc.scrollTop = target.offsetTop - navH - 6; else this.toEnd();
    this.moveKnob();
    if (!RM) { sc.animate([{ opacity: 0, transform: `scale(${deeper ? 1.06 : .94})` }, { opacity: 1, transform: 'scale(1)' }], { duration: 340, easing: EASE }); a1 && a1.cancel(); }
  }
  toEnd(smooth) { this.scroller.scrollTo({ top: this.scroller.scrollHeight, behavior: smooth && !RM ? 'smooth' : 'auto' }); }
  scrollHome() { this.toEnd(true); }
  moveKnob() {
    const b = $(`button[data-m="${this.mode}"]`, this.seg), k = $('.knob', this.seg);
    $$('button', this.seg).forEach(x => { x.classList.toggle('on', x === b); x.setAttribute('aria-selected', x === b); });
    if (b && b.offsetWidth) { k.style.left = b.offsetLeft + 'px'; k.style.width = b.offsetWidth + 'px'; }
  }
  onShow() { this.moveKnob(); }
  updateHint() {
    const has = S.items.some(i => i.sample) && !this.hintClosed;
    this.el.classList.toggle('has-hint', has);
    if (has && !this.hint) {
      this.hint = h(`<div class="hint"><div class="hint-txt"><b>Це приклади</b><span>Додайте свої фото, відео чи цілі папки.</span></div><div class="hint-btns"><button class="pill" data-h="add">${I.plus}Додати свої</button><button class="pill ghost" data-h="rm">Прибрати</button></div><button class="hint-x" aria-label="Сховати підказку">${I.x}</button></div>`);
      this.hint.addEventListener('click', async e => {
        const b = e.target.closest('button'); if (!b) return;
        if (b.dataset.h === 'add') openImport();
        else if (b.dataset.h === 'rm') removeSamples();
        else { this.hintClosed = true; anim(this.hint, [{ opacity: 1, transform: 'none' }, { opacity: 0, transform: 'translateY(20px)' }], { duration: 250 }).then(() => this.updateHint()); }
      });
      this.el.append(this.hint);
    } else if (!has && this.hint) { this.hint.remove(); this.hint = null; }
  }
}

async function removeSamples() {
  const ok = await actionSheet({ title: 'Прибрати всі приклади? Ваші власні фото залишаться.', actions: [{ label: 'Прибрати приклади', destructive: true }] });
  if (!ok) return;
  const ids = S.items.filter(i => i.sample).map(i => i.id);
  const p = topPage(); if (p.hasGrid) dissolveCells(p, ids);
  await sleep(RM ? 0 : 200);
  await purgeItems(ids);
  const goneA = [], keptA = [];
  S.albums.forEach(a => { if (!a.sample) return; if (a.items.length) { a.sample = false; keptA.push(a); } else goneA.push(a.id); });
  S.albums = S.albums.filter(a => !goneA.includes(a.id));
  const goneF = [];
  let changed = true;
  while (changed) { changed = false; for (const f of S.folders) { if (f.sample && !goneF.includes(f.id) && !S.albums.some(a => a.parent === f.id) && !S.folders.some(x => x.parent === f.id && !goneF.includes(x.id))) { goneF.push(f.id); changed = true; } } }
  S.folders = S.folders.filter(f => !goneF.includes(f.id));
  await Promise.all([DB.del('albums', goneA), DB.del('folders', goneF), saveAlbums(keptA)]);
  invalidate({ flip: true });
  toast('Приклади прибрано', 'trash');
}

// ---------- Album (real or smart) ----------
class AlbumPage extends Page {
  constructor({ album = null, smart = null }) {
    super({ title: smart ? SMART[smart].name : (albumById(album)?.name || '') });
    this.albumId = album; this.smart = smart;
    applyCols(this, LS.get('cols', defaultCols()));
    attachGrid(this); attachPinch(this);
  }
  get album() { return this.albumId ? albumById(this.albumId) : null; }
  items() { return this.smart ? SMART[this.smart].get() : this.album ? albumItems(this.album) : []; }
  update() {
    if (this.albumId && !this.album) { this.content.innerHTML = emptyHTML(I.albums, 'Альбом видалено', ''); setTimeout(() => { if (topPage(this.tab) === this && Tabs.cur === this.tab) pop(); }, 60); return; }
    const its = this.items();
    let sub = its.length ? countText(its) : '';
    if (this.smart === 'deleted') sub = 'Фото й відео зберігаються тут 30 днів, потім видаляються назавжди.';
    else if (this.album && this.album.parent) sub = folderPath(this.album.parent) + (sub ? ' · ' + sub : '');
    this.setTitle(this.smart ? SMART[this.smart].name : this.album.name, sub);
    this.content.innerHTML = its.length ? gridHTML(its, { sel: this.sel })
      : this.smart === 'deleted' ? emptyHTML(I.trash, 'Тут порожньо', 'Видалені фото й відео з’являться тут.')
      : this.smart ? emptyHTML(I[SMART[this.smart].icon], 'Тут порожньо', this.smart === 'favorites' ? 'Торкніться серця на фото, щоб додати його сюди.' : '')
      : emptyHTML(I.photo, 'Альбом порожній', 'Додайте фото з медіатеки або імпортуйте нові з телефона.', 'Додати фото', 'album-add');
    if (!this.selecting) this.renderNav();
  }
  rightButtons() {
    const out = [];
    if (this.items().length) out.push(navBtn('Вибрати', () => setSelecting(this, true), 'pill'));
    out.push(navBtn(I.more, e => this.menu(e.currentTarget), 'circle', 'Ще'));
    return out;
  }
  menu(anchor) {
    const a = this.album, its = this.items(), items = [];
    if (a) items.push({ label: 'Додати з медіатеки', icon: 'albumAdd', run: () => pickFromLibrary(a) }, { label: 'Імпортувати з пристрою', icon: 'import', run: () => openImport({ album: a.id }) });
    if (this.smart !== 'deleted' && its.some(i => i.kind === 'image')) items.push({ label: 'Слайд-шоу', icon: 'slides', run: () => slideshow(its) });
    if (a) items.push({ sep: true, label: 'Перейменувати', icon: 'pencil', run: () => renameNode('album', a) }, { label: 'Перемістити в папку', icon: 'move', run: () => moveNode('album', a) }, { label: 'Видалити альбом', icon: 'trash', destructive: true, run: () => deleteAlbumFlow(a) });
    if (this.smart === 'deleted' && its.length) items.push(
      { label: 'Відновити все', icon: 'restore', run: async () => { const ids = its.map(i => i.id); dissolveCells(this, ids); await sleep(140); await restoreItems(ids); invalidate({ flip: true }); toast(`Відновлено: ${ids.length}`, 'restore'); } },
      { label: 'Видалити все', icon: 'trash', destructive: true, run: () => purgeFlow(this, its.map(i => i.id)) });
    if (items.length) popMenu(anchor, items);
  }
}

// ---------- Albums root / folder ----------
class AlbumsPage extends Page {
  constructor(folderId = null) {
    super({ title: folderId ? (folderById(folderId)?.name || '') : 'Альбоми', cls: 'grouped' });
    this.folderId = folderId;
    this.content.addEventListener('click', e => navClick(e));
    longPress(this.content, '.tile', tileMenu);
  }
  get folder() { return this.folderId ? folderById(this.folderId) : null; }
  leftButtons() { return this.folderId ? [] : [navBtn(I.plus, e => this.addMenu(e.currentTarget), 'circle', 'Створити')]; }
  rightButtons() { return this.folderId ? [navBtn(I.plus, e => this.addMenu(e.currentTarget), 'circle', 'Створити'), navBtn(I.more, e => this.folderMenu(e.currentTarget), 'circle', 'Ще')] : []; }
  addMenu(anchor) {
    popMenu(anchor, [
      { label: 'Новий альбом', icon: 'albums', run: () => newAlbumFlow(this.folderId) },
      { label: 'Нова папка', icon: 'folder', run: () => newFolderFlow(this.folderId) },
      { sep: true, label: 'Імпортувати папки й файли', icon: 'import', run: () => openImport({ folder: this.folderId }) },
    ]);
  }
  folderMenu(anchor) {
    const f = this.folder; if (!f) return;
    popMenu(anchor, [
      { label: 'Перейменувати', icon: 'pencil', run: () => renameNode('folder', f) },
      { label: 'Перемістити в папку', icon: 'move', run: () => moveNode('folder', f) },
      { sep: true, label: 'Видалити папку', icon: 'trash', destructive: true, run: () => deleteFolderFlow(f) },
    ]);
  }
  update() {
    if (this.folderId && !this.folder) { setTimeout(() => { if (topPage(this.tab) === this && Tabs.cur === this.tab) pop(); }, 60); return; }
    const fs = S.folders.filter(f => f.parent === this.folderId).sort(byCreated), as = S.albums.filter(a => a.parent === this.folderId).sort(byCreated);
    const n = fs.length + as.length;
    this.setTitle(this.folderId ? this.folder.name : 'Альбоми', this.folderId ? `${n} ${plural(n, 'елемент', 'елементи', 'елементів')}` : '');
    let html = this.folderId ? '' : `<div class="sec-h">Мої альбоми <small>${n || ''}</small></div>`;
    html += n ? `<div class="tiles">${fs.map(folderTile).join('')}${as.map(albumTile).join('')}</div>`
      : `<div class="tiles-empty">${this.folderId ? 'Папка порожня. Створіть тут альбом чи підпапку або імпортуйте папку з телефона.' : 'Альбомів ще немає. Створіть альбом або імпортуйте папку з телефона — підпапки стануть альбомами.'}<button class="pill" data-act="import">${I.folder}Імпортувати папку</button></div>`;
    if (!this.folderId) {
      html += `<div class="sec-h">Типи медіа</div><div class="rows">${smartRow('videos')}${smartRow('favorites')}</div>`;
      html += `<div class="sec-h">Інше</div><div class="rows">${smartRow('recent')}<button class="row" data-act="import">${I.import}<span class="r-label">Імпорт фото й папок</span>${I.chev}</button>${smartRow('deleted')}</div><div style="height:12px"></div>`;
    }
    this.content.innerHTML = html;
    if (!this.selecting) this.renderNav();
  }
}

// ---------- Search ----------
class SearchPage extends Page {
  constructor() {
    super({ title: 'Пошук' });
    this.q = '';
    this.box = h(`<label class="search-box">${I.search}<input id="search-q" type="search" placeholder="Альбоми, місяці, роки, назви" autocomplete="off" enterkeyhint="search" aria-label="Пошук"><button class="search-clear" type="button" hidden aria-label="Очистити">${I.x}</button></label>`);
    $('.lt', this.el).append(this.box);
    const input = $('input', this.box), clear = $('.search-clear', this.box);
    input.addEventListener('input', () => { this.q = input.value; clear.hidden = !this.q; this.refresh(); });
    clear.addEventListener('click', () => { input.value = ''; this.q = ''; clear.hidden = true; this.refresh(); input.focus(); });
    attachGrid(this);
    this.content.addEventListener('click', e => {
      const ch = e.target.closest('[data-chip]');
      if (ch) { input.value = ch.dataset.chip; this.q = input.value; clear.hidden = false; this.refresh(); return; }
      navClick(e);
    });
  }
  update() {
    const q = this.q.trim().toLowerCase(), all = live();
    if (!q) {
      const years = [...new Set(all.map(i => new Date(i.date).getFullYear()))].sort((a, b) => b - a);
      const months = [...new Set(all.map(i => monthKey(i.date)))].slice(-8).reverse();
      const albums = S.albums.slice().sort((a, b) => b.created - a.created).slice(0, 12);
      this.content.innerHTML = `<div class="sec-h">Швидкий доступ</div><div class="chips">${['favorites', 'videos', 'recent'].map(k => `<button class="chip" data-smart="${k}">${I[SMART[k].icon]}${SMART[k].name} <small>${SMART[k].get().length}</small></button>`).join('')}</div>`
        + (years.length ? `<div class="sec-h">Роки</div><div class="chips">${years.map(y => `<button class="chip" data-chip="${y}">${y}</button>`).join('')}</div>` : '')
        + (months.length ? `<div class="sec-h">Місяці</div><div class="chips">${months.map(k => { const [y, m] = k.split('-').map(Number); return `<button class="chip" data-chip="${MONTHS[m].toLowerCase()} ${y}">${MONTHS[m]} ${y}</button>`; }).join('')}</div>` : '')
        + (albums.length ? `<div class="sec-h">Альбоми</div><div class="tiles row-scroll">${albums.map(albumTile).join('')}</div>` : '');
      return;
    }
    const words = q.split(/\s+/).filter(Boolean);
    const match = s => { s = s.toLowerCase(); return words.every(w => s.includes(w)); };
    const albums = S.albums.filter(a => match(a.name + ' ' + folderPath(a.parent)));
    const folders = S.folders.filter(f => match(f.name));
    const smart = Object.keys(SMART).filter(k => k !== 'deleted' && match(SMART[k].name));
    const items = all.filter(it => {
      const d = new Date(it.date), m = d.getMonth();
      return match(`${it.name} ${it.path} ${MONTHS[m]} ${MONTHS_GEN[m]} ${d.getFullYear()} ${fmtDate(it.date)} ${it.kind === 'video' ? 'відео video' : 'фото photo'} ${it.fav ? 'улюблені' : ''}`);
    });
    if (!albums.length && !folders.length && !smart.length && !items.length) { this.content.innerHTML = emptyHTML(I.search, 'Нічого не знайдено', `За запитом «${this.q.trim()}» нічого немає. Спробуйте назву місяця, рік або альбому.`); return; }
    this.content.innerHTML = (smart.length ? `<div class="sec-h">Розділи</div><div class="chips">${smart.map(k => `<button class="chip" data-smart="${k}">${I[SMART[k].icon]}${SMART[k].name}</button>`).join('')}</div>` : '')
      + (albums.length || folders.length ? `<div class="sec-h">Альбоми й папки <small>${albums.length + folders.length}</small></div><div class="tiles row-scroll">${folders.map(folderTile).join('')}${albums.map(albumTile).join('')}</div>` : '')
      + (items.length ? `<div class="sec-h">Фото й відео <small>${items.length}</small></div>${gridHTML(items, { months: true, sel: this.sel })}` : '');
  }
}
