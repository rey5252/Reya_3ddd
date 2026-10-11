'use strict';
// ---------- full-screen viewer ----------
const GAP = 20;
const V = {
  on: false, closing: false, busy: false, ids: [], i: 0, page: null, st: { s: 1, tx: 0, ty: 0 }, chrome: true, ptrs: new Map(), g: null,
  init() {
    this.el = $('#viewer'); this.bg = $('.v-bg', this.el); this.track = $('.v-track', this.el); this.slides = $$('.v-slide', this.el);
    this.titleB = $('.v-title b', this.el); this.titleS = $('.v-title small', this.el); this.strip = $('.v-strip', this.el);
    this.vrow = $('.v-video', this.el); this.seek = $('#v-seek'); this.vtime = $('.v-time', this.el);
    this.el.addEventListener('click', e => { const b = e.target.closest('[data-v]'); if (b) this.action(b.dataset.v, b); });
    const t = this.track;
    t.addEventListener('pointerdown', e => this.down(e));
    t.addEventListener('pointermove', e => this.move(e));
    t.addEventListener('pointerup', e => this.up(e));
    t.addEventListener('pointercancel', e => this.up(e));
    t.addEventListener('wheel', e => {
      if (!this.on || !e.ctrlKey || this.item?.kind === 'video') return;
      e.preventDefault();
      const b = this.base(this.item), s0 = this.st.s, s = clamp(s0 * Math.exp(-e.deltaY / 200), 1, 8);
      const px = (e.clientX - b.x - this.st.tx) / s0, py = (e.clientY - b.y - this.st.ty) / s0;
      this.st = this.clampSt({ s, tx: e.clientX - b.x - px * s, ty: e.clientY - b.y - py * s }); this.apply(false);
    }, { passive: false });
    this.strip.addEventListener('click', e => { const b = e.target.closest('[data-i]'); if (b) this.jump(+b.dataset.i); });
    this.seek.addEventListener('input', () => { const v = this.video(); if (v && v.duration) v.currentTime = this.seek.value / 1000 * v.duration; });
    addEventListener('resize', () => { if (this.on) this.relayout(); });
    addEventListener('keydown', e => {
      if (!this.on || OV.length) return;
      if (e.key === 'ArrowRight') this.go(1);
      else if (e.key === 'ArrowLeft') this.go(-1);
      else if (e.key === ' ') { e.preventDefault(); this.item?.kind === 'video' ? this.togglePlay() : this.setChrome(!this.chrome); }
    });
  },
  get item() { return S.byId.get(this.ids[this.i]); },
  cur() { return this.slides[1]; },
  media() { return $('.v-media', this.cur()); },
  video() { const m = this.media(); return m && m.tagName === 'VIDEO' ? m : null; },
  base(it) {
    const W = innerWidth, H = innerHeight, w = (it && it.w) || 1, hh = (it && it.h) || 1, s = Math.min(W / w, H / hh);
    return { x: (W - w * s) / 2, y: (H - hh * s) / 2, w: w * s, h: hh * s };
  },
  clampSt(st) {
    const b = this.base(this.item), W = innerWidth, H = innerHeight, cw = b.w * st.s, ch = b.h * st.s;
    let x = b.x + st.tx, y = b.y + st.ty;
    x = cw <= W ? (W - cw) / 2 : clamp(x, W - cw, 0);
    y = ch <= H ? (H - ch) / 2 : clamp(y, H - ch, 0);
    return { s: st.s, tx: x - b.x, ty: y - b.y };
  },
  fill(slide, idx) {
    slide.innerHTML = ''; slide.classList.remove('playing', 'broken', 'adult');
    const token = slide._tok = (slide._tok || 0) + 1;
    const it = S.byId.get(this.ids[idx]); slide._id = it ? it.id : null;
    if (!it) return;
    const t = turl(it); let m;
    const isCur = () => slide === this.cur();
    if (it.kind === 'video') {
      m = document.createElement('video'); m.playsInline = true; m.setAttribute('playsinline', ''); m.preload = 'metadata'; if (t) m.poster = t;
      m.addEventListener('play', () => { slide.classList.add('playing'); if (isCur()) { this.setChrome(false); this.syncSeek(); } });
      m.addEventListener('pause', () => { slide.classList.remove('playing'); if (isCur()) this.syncSeek(); });
      m.addEventListener('ended', () => { slide.classList.remove('playing'); if (isCur()) this.setChrome(true); });
      m.addEventListener('timeupdate', () => { if (isCur()) this.syncSeek(); });
      m.addEventListener('loadedmetadata', () => { if (!it.w && m.videoWidth) { it.w = m.videoWidth; it.h = m.videoHeight; isCur() ? this.apply(false) : this.place(slide, it); } if (isCur()) this.syncSeek(); });
      let retried = false;
      m.addEventListener('error', async () => {
        if (slide._tok !== token) return;
        if (it.src && !retried) { retried = true; const b = await getBlob(it); if (b && slide._tok === token) { m.src = URL.createObjectURL(b); return; } }
        if (!t) slide.classList.add('broken');
      });
      fullURL(it).then(u => { if (u && slide._tok === token) m.src = u; });
      slide.append(h(`<div class="v-play">${I.play}</div>`));
    } else {
      m = new Image(); m.alt = it.name; m.draggable = false; m.decoding = 'async';
      if (t) m.src = t;
      fullURL(it).then(u => {
        if (!u || slide._tok !== token) { if (!u && !t && slide._tok === token) slide.classList.add('broken'); return; }
        const f = new Image(); f.src = u;
        f.decode().then(() => {
          if (slide._tok !== token) return;
          m.src = u;
          if (!it.w) { it.w = f.naturalWidth; it.h = f.naturalHeight; isCur() ? this.apply(false) : this.place(slide, it); if (it.src) saveItems(it); }
        }, () => { if (slide._tok === token && !t) slide.classList.add('broken'); });
      });
    }
    m.className = 'v-media';
    slide.prepend(m);
    if (blurred(it)) { slide.classList.add('adult'); slide.append(h(`<div class="v-adult">${I.eyeOff}<b>${isAdult(it) ? 'Вміст 18+' : 'Ще не перевірено'}</b><span>Торкніться, щоб показати</span></div>`)); }
    slide.append(h(`<div class="v-broken">${I.photo}<span>Цей формат не відкривається в браузері</span><small>${esc(it.name)}</small></div>`));
    this.place(slide, it);
  },
  place(slide, it) {
    const m = $('.v-media', slide); if (!m) return;
    const b = this.base(it);
    m.style.width = b.w + 'px'; m.style.height = b.h + 'px'; m.style.transition = 'none';
    m.style.transform = `translate(${b.x}px,${b.y}px) scale(1)`; m.style.opacity = '';
  },
  apply(animate) {
    const m = this.media(), it = this.item; if (!m || !it) return;
    const b = this.base(it), { s, tx, ty } = this.st;
    m.style.width = b.w + 'px'; m.style.height = b.h + 'px';
    m.style.transition = animate && !RM ? `transform .4s ${EASE}` : 'none';
    m.style.transform = `translate(${b.x + tx}px,${b.y + ty}px) scale(${s})`;
  },
  layout() { const W = innerWidth; this.slides.forEach((s, k) => { s.style.transform = `translateX(${(k - 1) * (W + GAP)}px)`; }); },
  relayout() { this.layout(); this.slides.forEach(s => { const it = S.byId.get(s._id); if (it) this.place(s, it); }); this.st = { s: 1, tx: 0, ty: 0 }; this.apply(false); this.renderStrip(); },
  renderAll() {
    this.st = { s: 1, tx: 0, ty: 0 }; this.layout();
    [-1, 0, 1].forEach((d, k) => this.fill(this.slides[k], this.i + d));
    this.track.style.transition = 'none'; this.track.style.transform = 'translateX(0)';
    this.afterChange();
  },
  afterChange() { this.updateBars(); this.renderStrip(); this.markHole(); },
  markHole(on = this.on && !this.closing) {
    $$('.cell.hole').forEach(c => c.classList.remove('hole'));
    if (!on || !this.page) return;
    const c = cellOf(this.page, this.ids[this.i]); if (c) c.classList.add('hole');
  },
  updateBars() {
    const it = this.item; if (!it) return;
    this.titleB.textContent = fmtDate(it.date); this.titleS.textContent = fmtTime(it.date);
    const fb = $('[data-v="fav"]', this.el); fb.innerHTML = it.fav ? I.heartFill : I.heart; fb.classList.toggle('on', !!it.fav);
    fb.setAttribute('aria-label', it.fav ? 'Прибрати з улюблених' : 'Додати до улюблених');
    $('.main-tools', this.el).hidden = !!it.deleted; $('.del-tools', this.el).hidden = !it.deleted;
    this.vrow.hidden = it.kind !== 'video';
    this.syncSeek();
  },
  renderStrip() {
    const n = this.ids.length;
    if (n < 2) { this.strip.innerHTML = ''; return; }
    const a = Math.max(0, this.i - 30), b = Math.min(n, this.i + 31);
    let html = '<span class="v-sp"></span>';
    for (let k = a; k < b; k++) { const u = turl(S.byId.get(this.ids[k])); const sit = S.byId.get(this.ids[k]); html += `<button class="v-th${k === this.i ? ' cur' : ''}" data-i="${k}" aria-label="Фото ${k + 1}">${u && !blurred(sit) ? `<img src="${u}" alt="">` : ''}</button>`; }
    this.strip.innerHTML = html + '<span class="v-sp"></span>';
    const c = $('.v-th.cur', this.strip);
    if (c) this.strip.scrollLeft = c.offsetLeft - this.strip.clientWidth / 2 + 20;
  },
  jump(k) {
    if (k === this.i || k < 0 || k >= this.ids.length) return;
    this.pauseVideo(); this.i = k; this.renderAll();
    if (!RM) this.cur().animate([{ opacity: .3 }, { opacity: 1 }], { duration: 220 });
  },
  go(dir, v = 0) {
    if (this.busy) return;
    const ni = this.i + dir;
    if (ni < 0 || ni >= this.ids.length) { this.snapTrack(); return; }
    this.pauseVideo(); this.busy = true;
    const W = innerWidth, target = -dir * (W + GAP);
    const cur = new DOMMatrixReadOnly(getComputedStyle(this.track).transform).m41 || 0;
    const left = Math.abs(target - cur), speed = Math.abs(v);
    // follow the finger's speed, like iOS: a fast flick finishes quickly, a slow drag glides
    const dur = RM ? 0 : Math.round(clamp(speed > .3 ? left / speed * 1.6 : 360 * left / (W + GAP) + 140, 170, 420));
    this.track.style.transition = dur ? `transform ${dur}ms cubic-bezier(.25,.9,.3,1)` : 'none';
    this.track.style.transform = `translateX(${target}px)`;
    let finished = false;
    const finish = () => {
      if (finished) return; finished = true;
      this.track.removeEventListener('transitionend', finish);
      this.track.style.transition = 'none';
      const old = this.cur();
      this.i = ni;
      if (dir > 0) { this.slides.push(this.slides.shift()); this.fill(this.slides[2], ni + 1); }
      else { this.slides.unshift(this.slides.pop()); this.fill(this.slides[0], ni - 1); }
      const oit = S.byId.get(old._id); if (oit) this.place(old, oit);
      this.layout(); this.track.style.transform = 'translateX(0)';
      this.st = { s: 1, tx: 0, ty: 0 }; this.busy = false; this.afterChange();
      this.preload(ni + 2 * dir);
    };
    if (!dur) finish();
    else { this.track.addEventListener('transitionend', finish); setTimeout(finish, dur + 80); }
  },
  // decode the photo after next ahead of time so the next swipe never waits
  preload(k) {
    const it = S.byId.get(this.ids[k]);
    if (!it || it.kind !== 'image') return;
    fullURL(it).then(u => { if (!u) return; const im = new Image(); im.src = u; im.decode().catch(() => {}); });
  },
  snapTrack() { this.track.style.transition = `transform .45s ${SOFT_SPRING}`; this.track.style.transform = 'translateX(0)'; },

  // ----- gestures -----
  down(e) {
    if (this.busy || this.closing) return;
    try { this.track.setPointerCapture(e.pointerId); } catch (_) {}
    this.ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = performance.now();
    if (this.ptrs.size === 1) this.g = { type: null, x0: e.clientX, y0: e.clientY, t0: now, st0: { ...this.st }, lx: e.clientX, ly: e.clientY, lt: now, vx: 0, vy: 0 };
    else if (this.ptrs.size === 2 && this.item && this.item.kind !== 'video') {
      if (this.g && this.g.type === 'page') this.snapTrack();
      if (this.g && this.g.type === 'dismiss') { this.el.classList.remove('dragging'); this.bg.style.opacity = '1'; }
      const [a, b] = [...this.ptrs.values()];
      this.g = { type: 'pinch', d0: Math.hypot(a.x - b.x, a.y - b.y), c0: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }, st0: { ...this.st } };
    }
  },
  move(e) {
    if (!this.ptrs.has(e.pointerId)) return;
    this.ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const g = this.g; if (!g || g.type === 'wait') return;
    if (g.type === 'pinch') {
      if (this.ptrs.size < 2) return;
      const [a, b] = [...this.ptrs.values()], d = Math.hypot(a.x - b.x, a.y - b.y), c = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
      const s = clamp(g.st0.s * d / g.d0, .5, 8), base = this.base(this.item);
      const px = (g.c0.x - base.x - g.st0.tx) / g.st0.s, py = (g.c0.y - base.y - g.st0.ty) / g.st0.s;
      this.st = { s, tx: c.x - base.x - px * s, ty: c.y - base.y - py * s }; this.apply(false);
      return;
    }
    const now = performance.now(), dt = now - g.lt;
    if (dt > 0) { g.vx = (e.clientX - g.lx) / dt; g.vy = (e.clientY - g.ly) / dt; }
    g.lx = e.clientX; g.ly = e.clientY; g.lt = now;
    const dx = e.clientX - g.x0, dy = e.clientY - g.y0;
    if (!g.type) {
      if (Math.hypot(dx, dy) < 8) return;
      if (this.st.s > 1.02) g.type = 'pan';
      else if (Math.abs(dx) > Math.abs(dy)) g.type = 'page';
      else g.type = dy > 0 ? 'dismiss' : 'up';
      if (g.type === 'dismiss') { this.el.classList.add('dragging'); this.pauseVideo(); }
    }
    if (g.type === 'pan') {
      const raw = { s: this.st.s, tx: g.st0.tx + dx, ty: g.st0.ty + dy }, c = this.clampSt(raw);
      this.st = { s: raw.s, tx: c.tx + (raw.tx - c.tx) * .35, ty: c.ty + (raw.ty - c.ty) * .35 }; this.apply(false);
    } else if (g.type === 'page') {
      const edge = (dx > 0 && this.i === 0) || (dx < 0 && this.i === this.ids.length - 1);
      this.track.style.transition = 'none'; this.track.style.transform = `translateX(${edge ? dx * .3 : dx}px)`;
    } else if (g.type === 'dismiss') {
      const H = innerHeight, p = Math.max(0, dy) / H, s = Math.max(.5, 1 - p * .7), b = this.base(this.item), m = this.media();
      if (!m) return;
      const cx = b.x + b.w / 2 + dx, cy = b.y + b.h / 2 + Math.max(dy, dy * .2);
      m.style.transition = 'none'; m.style.transform = `translate(${cx - b.w * s / 2}px,${cy - b.h * s / 2}px) scale(${s})`;
      this.bg.style.opacity = String(Math.max(0, 1 - p * 2.4));
    } else if (g.type === 'up') {
      const m = this.media(), b = this.base(this.item);
      if (m) { m.style.transition = 'none'; m.style.transform = `translate(${b.x}px,${b.y + dy * .25}px) scale(1)`; }
    }
  },
  up(e) {
    if (!this.ptrs.has(e.pointerId)) return;
    this.ptrs.delete(e.pointerId);
    const g = this.g; if (!g) return;
    if (g.type === 'pinch' || g.type === 'wait') {
      if (g.type === 'pinch') {
        if (this.st.s < 1) this.st = { s: 1, tx: 0, ty: 0 };
        else this.st = this.clampSt(this.st);
        this.apply(true);
        if (this.st.s > 1) this.setChrome(false);
      }
      this.g = this.ptrs.size ? { type: 'wait' } : null;
      return;
    }
    if (this.ptrs.size) return;
    this.g = null;
    const dx = e.clientX - g.x0, dy = e.clientY - g.y0;
    if (e.type === 'pointercancel' && !g.type) return;
    switch (g.type) {
      case null: if (performance.now() - g.t0 < 350) this.tap(e.clientX, e.clientY); break;
      case 'pan': this.st = this.clampSt({ s: this.st.s, tx: this.st.tx + g.vx * 160, ty: this.st.ty + g.vy * 160 }); this.apply(true); break;
      case 'page':
        if ((dx < -innerWidth * .2 || g.vx < -.35) && this.i < this.ids.length - 1) this.go(1, g.vx);
        else if ((dx > innerWidth * .2 || g.vx > .35) && this.i > 0) this.go(-1, g.vx);
        else this.snapTrack();
        break;
      case 'dismiss':
        if (dy > 110 || g.vy > .6) this.close();
        else { this.el.classList.remove('dragging'); anim(this.bg, [{ opacity: this.bg.style.opacity || '1' }, { opacity: '1' }], { duration: 300 }); this.apply(true); }
        break;
      case 'up':
        this.apply(true);
        if (dy < -60 || g.vy < -.5) this.info();
        break;
    }
  },
  tap(x, y) {
    const now = performance.now(), lt = this.lastTap;
    if (lt && now - lt.t < 300 && Math.hypot(x - lt.x, y - lt.y) < 30) {
      clearTimeout(this.tapTimer); this.lastTap = null;
      if (this.item.kind !== 'video') this.zoomAt(x, y);
      return;
    }
    if (this.cur().classList.contains('adult')) { this.cur().classList.remove('adult'); haptic(8); return; }
    this.lastTap = { t: now, x, y };
    this.tapTimer = setTimeout(() => { this.lastTap = null; if (this.item?.kind === 'video') this.togglePlay(); else this.setChrome(!this.chrome); }, 260);
  },
  zoomAt(x, y) {
    const b = this.base(this.item);
    if (this.st.s > 1.05) this.st = { s: 1, tx: 0, ty: 0 };
    else {
      const fill = Math.max(innerWidth / b.w, innerHeight / b.h), ns = fill > 1.3 ? Math.min(fill, 4) : 2.5;
      const px = (x - b.x - this.st.tx) / this.st.s, py = (y - b.y - this.st.ty) / this.st.s;
      this.st = this.clampSt({ s: ns, tx: x - b.x - px * ns, ty: y - b.y - py * ns });
      this.setChrome(false);
    }
    this.apply(true); haptic(5);
  },
  setChrome(on) { this.chrome = on; this.el.classList.toggle('bare', !on); },
  togglePlay() { const v = this.video(); if (!v) return; if (v.paused) { const p = v.play(); p && p.catch(() => {}); } else v.pause(); },
  pauseVideo() { $$('video', this.el).forEach(v => { try { v.pause(); } catch (e) {} }); },
  syncSeek() {
    const v = this.video(); if (!v) return;
    const d = isFinite(v.duration) ? v.duration : (this.item?.duration || 0);
    this.seek.value = d ? Math.round(v.currentTime / d * 1000) : 0;
    this.vtime.textContent = `${fmtDur(v.currentTime)} / ${fmtDur(d)}`;
    $('[data-v="play"]', this.el).innerHTML = v.paused ? I.play : I.pause;
  },

  // ----- open / close with a shared-element flight from the thumbnail -----
  fly(src, it, A, aMode, B, bMode, dur) {
    const fl = h('<div class="flyer"><img alt=""></div>'), img = fl.firstChild; img.src = src;
    if (blurred(it) && this.cur().classList.contains('adult')) img.className = 'b18';
    this.el.insertBefore(fl, this.track.nextSibling);
    const ar = it && it.w && it.h ? it.w / it.h : 1;
    const R = r => ({ left: r.left ?? r.x, top: r.top ?? r.y, width: r.width ?? r.w, height: r.height ?? r.h });
    const inner = (box, mode) => {
      if (mode === 'fill') return { x: 0, y: 0, w: box.width, h: box.height };
      let w, hh; if (ar > box.width / box.height) { hh = box.height; w = hh * ar; } else { w = box.width; hh = w / ar; }
      return { x: (box.width - w) / 2, y: (box.height - hh) / 2, w, h: hh };
    };
    const a = R(A), b = R(B), ia = inner(a, aMode), ib = inner(b, bMode);
    const bx = r => ({ left: r.left + 'px', top: r.top + 'px', width: r.width + 'px', height: r.height + 'px' });
    const ix = i => ({ left: i.x + 'px', top: i.y + 'px', width: i.w + 'px', height: i.h + 'px' });
    const o = { duration: dur, easing: EASE, fill: 'forwards' };
    return Promise.all([fl.animate([bx(a), bx(b)], o).finished, img.animate([ix(ia), ix(ib)], o).finished]).catch(() => {}).then(() => fl.remove());
  },
  async open(page, ids, i, fromEl) {
    if (this.on || !ids.length) return;
    this.page = page; this.ids = ids; this.i = clamp(i, 0, ids.length - 1); this.on = true; Back.arm();
    this.el.hidden = false; this.el.classList.remove('bare', 'closing', 'dragging'); this.chrome = true;
    this.bg.style.opacity = '0'; this.track.style.visibility = 'hidden';
    this.renderAll();
    const it = this.item, u = turl(it), from = fromEl && fromEl.getBoundingClientRect();
    if (from && u && !RM) {
      this.busy = true;
      await Promise.all([this.fly(u, it, from, 'cover', this.base(it), 'fill', 440), anim(this.bg, [{ opacity: '0' }, { opacity: '1' }], { duration: 320, easing: 'ease-out' })]);
      this.busy = false;
      this.track.style.visibility = '';
    } else {
      this.track.style.visibility = '';
      await anim(this.bg, [{ opacity: '0' }, { opacity: '1' }], { duration: 220 });
    }
    this.bg.style.opacity = '1';
  },
  findCell(id) {
    const p = this.page;
    if (!p || !p.el.isConnected || topPage(p.tab) !== p || Tabs.cur !== p.tab) return null;
    const c = cellOf(p, id); if (!c) return null;
    const sr = p.scroller.getBoundingClientRect(), cr = c.getBoundingClientRect();
    if (cr.top < sr.top + SAFE.top + 50 || cr.bottom > sr.bottom - SAFE.bot - 60) p.scroller.scrollTop += (cr.top + cr.height / 2) - (sr.top + sr.height / 2);
    return c;
  },
  async close() {
    if (!this.on || this.closing) return;
    this.closing = true; this.pauseVideo(); clearTimeout(this.tapTimer);
    const it = this.item, m = this.media(), r = m && m.getBoundingClientRect();
    const cell = it && this.findCell(it.id);
    this.el.classList.add('closing');
    const bgFrom = getComputedStyle(this.bg).opacity;
    if (cell && r && r.width && turl(it) && !RM) {
      this.track.style.visibility = 'hidden';
      const src = (m.tagName === 'IMG' && m.currentSrc) || turl(it);
      await Promise.all([this.fly(src, it, r, 'fill', cell.getBoundingClientRect(), 'cover', 380), anim(this.bg, [{ opacity: bgFrom }, { opacity: '0' }], { duration: 300 })]);
    } else {
      await anim(this.el, [{ opacity: '1' }, { opacity: '0' }], { duration: 220 });
      this.el.style.opacity = '';
    }
    this.el.hidden = true; this.el.classList.remove('closing', 'dragging', 'bare');
    this.track.style.visibility = '';
    this.slides.forEach(s => { s.innerHTML = ''; s._id = null; s._tok = (s._tok || 0) + 1; });
    this.on = false; this.closing = false; this.markHole(false);
  },
  removeCurrent() {
    this.ids.splice(this.i, 1);
    if (!this.ids.length) { this.close(); return; }
    if (this.i >= this.ids.length) this.i = this.ids.length - 1;
    this.renderAll();
    if (!RM) this.cur().animate([{ opacity: 0, transform: 'scale(.94)' }, { opacity: 1, transform: 'none' }], { duration: 360, easing: EASE });
  },

  // ----- toolbar actions -----
  async action(a, btn) {
    const it = this.item; if (!it) return;
    if (a === 'close') this.close();
    else if (a === 'fav') {
      const val = !it.fav;
      if (val) { const r = btn.getBoundingClientRect(); FX.hearts(r.left + r.width / 2, r.top + r.height / 2); }
      haptic(10);
      await setFav([it.id], val); this.updateBars();
      $('[data-v="fav"]', this.el).animate([{ transform: 'scale(1)' }, { transform: 'scale(1.4)' }, { transform: 'scale(1)' }], { duration: 380, easing: SPRING });
    }
    else if (a === 'share') shareItems([it.id]);
    else if (a === 'info') this.info();
    else if (a === 'del') this.del(false);
    else if (a === 'purge') this.del(true);
    else if (a === 'restore') { await restoreItems([it.id]); invalidate(); toast('Відновлено', 'restore'); if (this.page && this.page.smart === 'deleted') this.removeCurrent(); else this.updateBars(); }
    else if (a === 'play') this.togglePlay();
    else if (a === 'more') {
      const rest = this.ids.slice(this.i).concat(this.ids.slice(0, this.i)).map(id => S.byId.get(id)).filter(Boolean);
      popMenu(btn, [
        { label: 'Додати в альбом', icon: 'albumAdd', run: () => addToAlbumSheet([it.id]) },
        isAdult(it) ? { label: 'Це не 18+', icon: 'eye', run: async () => { await markAdult([it.id], false); this.renderAll(); } } : { label: 'Позначити 18+', icon: 'eyeOff', run: async () => { await markAdult([it.id], true); this.renderAll(); } },
        ...(rest.some(x => x.kind === 'image') ? [{ label: 'Слайд-шоу', icon: 'slides', run: () => slideshow(rest) }] : []),
        ...(!IN_FRAME && !it.src ? [{ label: 'Зберегти файл', icon: 'download', run: () => downloadItem(it) }] : []),
        { label: 'Інформація', icon: 'info', run: () => this.info() },
      ]);
    }
  },
  async del(permanent) {
    const it = this.item, w = it.kind === 'video' ? 'відео' : 'фото';
    const ok = await actionSheet(permanent
      ? { title: `Це ${w} буде видалено назавжди. Цю дію не можна скасувати.`, actions: [{ label: `Видалити ${w}`, destructive: true }] }
      : { title: `${w[0].toUpperCase() + w.slice(1)} буде переміщено в «Нещодавно видалені» на 30 днів.`, actions: [{ label: `Видалити ${w}`, destructive: true }] });
    if (!ok) return;
    const m = this.media();
    if (m) { if (!this.cur().classList.contains('adult')) FX.dissolve(m, m, { budget: 2800 }); m.style.opacity = '0'; }
    await sleep(RM ? 0 : 260);
    if (permanent) await purgeItems([it.id]); else await trashItems([it.id]);
    invalidate();
    this.removeCurrent();
  },
  info() {
    const it = this.item; if (!it) return;
    const sh = openSheet({ title: 'Інформація' });
    sh.setRight('Готово', () => sh.close(), true);
    const albums = S.albums.filter(a => a.items.includes(it.id));
    const fmt = (ext(it.name) || (it.mime || '').split('/')[1] || '').toUpperCase();
    sh.body.innerHTML = `<div class="info-head"><b>${fmtDate(it.date)}</b><span>${WEEKDAYS[new Date(it.date).getDay()]} · ${fmtTime(it.date)}</span></div>
      <div class="rows info-rows">
        <div class="row"><span class="r-label"><b>${esc(it.name)}</b></span></div>
        <div class="row"><span class="r-k">Розмір</span><span class="r-val">${fmtSize(it.size || 0)}</span></div>
        ${it.w ? `<div class="row"><span class="r-k">Роздільність</span><span class="r-val">${it.w} × ${it.h} · ${(it.w * it.h / 1e6).toFixed(1).replace('.', ',')} Мп</span></div>` : ''}
        ${it.kind === 'video' ? `<div class="row"><span class="r-k">Тривалість</span><span class="r-val">${fmtDur(it.duration)}</span></div>` : ''}
        ${fmt ? `<div class="row"><span class="r-k">Формат</span><span class="r-val">${esc(fmt)}</span></div>` : ''}
        ${it.path ? `<div class="row"><span class="r-k">Папка</span><span class="r-val">${esc(it.path)}</span></div>` : ''}
        <div class="row"><span class="r-k">Додано</span><span class="r-val">${fmtDate(it.added)}</span></div>
      </div>
      ${albums.length ? `<div class="group-h">В альбомах</div><div class="chips">${albums.map(a => `<span class="chip">${I.albums}${esc(a.name)}</span>`).join('')}</div>` : ''}`;
  },
};

// ---------- slideshow (crossfade + slow zoom) ----------
async function slideshow(items) {
  const its = items.filter(i => i.kind === 'image' && !blurred(i));
  if (!its.length) return;
  Back.arm();
  const w = h(`<div class="ss" role="dialog" aria-label="Слайд-шоу"><div class="ss-layer"></div><div class="ss-layer"></div><div class="ss-cap"></div><button class="ss-x" aria-label="Закрити">${I.x}</button></div>`);
  $('#overlays').append(w);
  w.animate([{ opacity: 0 }, { opacity: 1 }], { duration: RM ? 1 : 400 });
  const layers = $$('.ss-layer', w), cap = $('.ss-cap', w);
  let k = 0, i = 0, alive = true, timer;
  const close = () => {
    if (!alive) return; alive = false; clearTimeout(timer); ovRemove(close);
    w.animate([{ opacity: 1 }, { opacity: 0 }], { duration: RM ? 1 : 300, fill: 'forwards' }).finished.then(() => w.remove());
  };
  OV.push(close); w.addEventListener('click', close);
  const show = async () => {
    if (!alive) return;
    const it = its[i % its.length], u = (await fullURL(it)) || turl(it);
    const img = new Image(); img.src = u; img.alt = ''; img.className = 'ss-img';
    try { await img.decode(); } catch (e) {}
    if (!alive) return;
    const ar = (it.w || 1) / (it.h || 1), sr = innerWidth / innerHeight;
    img.style.objectFit = Math.abs(Math.log(ar / sr)) < .45 ? 'cover' : 'contain';
    const L = layers[k % 2], O = layers[(k + 1) % 2]; k++;
    L.innerHTML = ''; L.append(img); L.style.zIndex = 2; O.style.zIndex = 1;
    if (!RM) img.animate([{ transform: 'scale(1.03)' }, { transform: `scale(1.12) translate(${(Math.random() - .5) * 4}%,${(Math.random() - .5) * 4}%)` }], { duration: 7000, easing: 'cubic-bezier(.3,0,.7,1)', fill: 'forwards' });
    L.animate([{ opacity: 0 }, { opacity: 1 }], { duration: RM ? 1 : 1600, easing: 'cubic-bezier(.45,0,.25,1)', fill: 'forwards' });
    const nx = its[(i + 1) % its.length]; fullURL(nx).then(u => { if (u) { const p = new Image(); p.src = u; p.decode().catch(() => {}); } });
    cap.textContent = fmtDate(it.date);
    i++; timer = setTimeout(show, 4600);
  };
  show();
}
