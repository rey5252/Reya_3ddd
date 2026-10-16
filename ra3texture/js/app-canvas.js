(function (RA3) { 'use strict';
  // RA3 Texture Studio v2 — полотно: вигляд (maps.viewRGBA) з оновленням лише змінених ділянок під час мазка,
  // масштаб і рух, тайлінг 3×3, курсор пензля, інструменти (пензель, гумка, лінія, заливка, піпетка).
  const layers = RA3.layers, maps = RA3.maps, brush = RA3.brush;
  const A = RA3.App, S = A.S, $ = A.$;

  const TILE = 32;                 // клітинка сітки «брудних» ділянок вигляду, px текстури
  const APRON = 16;                // запас навколо ділянки (NRM бере сусідів; кратне 16 — шахівниця прозорості не зсувається)
  const ZMIN = 0.05, ZMAX = 32;
  const BG = '#1a1d20';
  const DEG = Math.PI / 180;

  // Стан полотна (V): розмір сцени в CSS px, масштаб (CSS px на тексель), зсув початку текстури.
  const V = A.V = {
    canvas: null, ctx: null, overlay: null, octx: null,
    viewCanvas: null, viewCtx: null, viewImg: null, viewSize: 0,
    W: 1, H: 1, dpr: 1,
    zoom: 1, panX: 0, panY: 0, fitted: true,
    tiles: null, tileN: 0, full: true, key: '',
    raf: 0, hover: null, space: false, pan: null
  };

  // ---------- Вигляд ----------

  A.resetView = function () {
    const size = S.doc.size;
    if (V.viewSize !== size) {
      V.viewCanvas = document.createElement('canvas');
      V.viewCanvas.width = size; V.viewCanvas.height = size;
      V.viewCtx = V.viewCanvas.getContext('2d');
      V.viewImg = V.viewCtx.createImageData(size, size);
      V.viewSize = size;
      V.tileN = Math.max(1, size / TILE);
      V.tiles = new Uint8Array(V.tileN * V.tileN);
    }
    V.full = true;
    A.fit();
  };

  A.invalidateView = function (full) {
    if (full) V.full = true;
    requestFrame();
  };

  // Прямокутник у текселях (можна за межами текстури — загортається) позначає клітинки сітки як змінені.
  A.markDirtyRect = function (x, y, w, h) {
    const size = S.doc.size, g = V.tileN;
    if (w >= size || h >= size) { V.full = true; return; }
    const tx0 = Math.floor(x / TILE), tx1 = Math.floor((x + w) / TILE), ty0 = Math.floor(y / TILE), ty1 = Math.floor((y + h) / TILE);
    for (let ty = ty0; ty <= ty1; ty++) {
      const row = A.mod(ty, g) * g;
      for (let tx = tx0; tx <= tx1; tx++) V.tiles[row + A.mod(tx, g)] = 1;
    }
  };

  function teamRgb() { return maps.TEAM_COLORS[S.team].rgb; }

  function fullView() {
    const rgba = maps.viewRGBA(S.doc, S.view, teamRgb());
    V.viewImg.data.set(rgba);
    V.viewCtx.putImageData(V.viewImg, 0, 0);
    V.tiles.fill(0);
    V.full = false;
  }

  // Квадратний виріз карт (із загортанням) — маленький «документ», який розуміє maps.viewRGBA.
  function cropDoc(ox, oy, s) {
    const d = S.doc, size = d.size, m = d.maps, msk = size - 1, n = s * s;
    const color = new Uint8ClampedArray(n * 4), height = new Float32Array(n), spec = new Uint8ClampedArray(n), team = new Uint8ClampedArray(n);
    const imp = d.nrmImport, nimp = imp ? new Uint8ClampedArray(n * 4) : null;
    for (let y = 0, j = 0; y < s; y++) {
      const row = ((oy + y) & msk) * size;
      for (let x = 0; x < s; x++, j++) {
        const i = row + ((ox + x) & msk), o = i * 4, q = j * 4;
        color[q] = m.color[o]; color[q + 1] = m.color[o + 1]; color[q + 2] = m.color[o + 2]; color[q + 3] = m.color[o + 3];
        height[j] = m.height[i]; spec[j] = m.spec[i]; team[j] = m.team[i];
        if (nimp) { nimp[q] = imp[o]; nimp[q + 1] = imp[o + 1]; nimp[q + 2] = imp[o + 2]; nimp[q + 3] = imp[o + 3]; }
      }
    }
    return { size: s, settings: d.settings, nrmImport: nimp, maps: { color, height, spec, team } };
  }

  function regionView(px, py, w, h) {
    const size = S.doc.size, s = Math.max(w, h) + 2 * APRON;
    const rgba = maps.viewRGBA(cropDoc(px - APRON, py - APRON, s), S.view, teamRgb());
    const data = V.viewImg.data;
    for (let y = 0; y < h; y++) {
      const src = ((APRON + y) * s + APRON) * 4, dst = ((py + y) * size + px) * 4;
      data.set(rgba.subarray(src, src + w * 4), dst);
    }
    V.viewCtx.putImageData(V.viewImg, 0, 0, px, py, w, h);
  }

  // Змінені клітинки -> зв'язні групи (8-сусідство, без загортання) -> по одному квадратному вирізу на групу.
  function updateTiles() {
    const g = V.tileN, tiles = V.tiles, size = S.doc.size;
    const boxes = [], stack = [];
    for (let start = 0; start < tiles.length; start++) {
      if (tiles[start] !== 1) continue;
      let x0 = g, y0 = g, x1 = -1, y1 = -1;
      tiles[start] = 2; stack.push(start);
      while (stack.length) {
        const t = stack.pop(), tx = t % g, ty = (t - tx) / g;
        if (tx < x0) x0 = tx; if (tx > x1) x1 = tx; if (ty < y0) y0 = ty; if (ty > y1) y1 = ty;
        for (let dy = -1; dy <= 1; dy++) {
          const ny = ty + dy;
          if (ny < 0 || ny >= g) continue;
          for (let dx = -1; dx <= 1; dx++) {
            const nx = tx + dx;
            if (nx < 0 || nx >= g) continue;
            const u = ny * g + nx;
            if (tiles[u] === 1) { tiles[u] = 2; stack.push(u); }
          }
        }
      }
      boxes.push([x0 * TILE, y0 * TILE, (x1 - x0 + 1) * TILE, (y1 - y0 + 1) * TILE]);
    }
    tiles.fill(0);
    if (!boxes.length) return;
    let area = 0;
    for (const b of boxes) { const s = Math.max(b[2], b[3]) + 2 * APRON; area += s * s; }
    if (area >= size * size * 0.6) { fullView(); return; }
    for (const b of boxes) regionView(b[0], b[1], b[2], b[3]);
  }

  function updateView() {
    const key = S.view + ':' + (S.view === 'game' ? S.team : '') + ':' + S.doc.size;
    if (V.full || key !== V.key) { V.key = key; fullView(); }
    else updateTiles();
    V.canvas.dataset.view = S.view;
  }

  function requestFrame() { if (!V.raf) V.raf = requestAnimationFrame(frame); }

  // Один кадр: композиція мазка (якщо є нові відбитки) -> оновлення вигляду -> малювання сцени.
  function frame() {
    V.raf = 0;
    if (!S.doc || !V.viewImg) return;
    const t0 = performance.now();
    const s = S.stroke;
    if (s && s.pending) {
      s.pending = false;
      try { A.compositeActive(s.layer); S.mapsVer++; }
      catch (e) { A.setStatus('Не вдалося перебудувати текстуру: ' + A.errText(e), 'error'); }
    }
    const t1 = performance.now();
    try { updateView(); } catch (e) { A.setStatus('Не вдалося показати вигляд: ' + A.errText(e), 'error'); }
    const t2 = performance.now();
    drawStage();
    if (s) {
      const f = S.perf.frames;
      f.push({ size: S.doc.size, view: S.view, composite: t1 - t0, view_ms: t2 - t1, draw: performance.now() - t2 });
      if (f.length > 240) f.splice(0, f.length - 240);
    }
  }
  A.renderNow = function () { if (V.raf) { cancelAnimationFrame(V.raf); V.raf = 0; } frame(); };

  function drawStage() {
    const c = V.canvas, ctx = V.ctx, dpr = V.dpr, size = S.doc.size, z = V.zoom;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.fillStyle = BG;
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.imageSmoothingEnabled = z < 2;
    ctx.setTransform(dpr * z, 0, 0, dpr * z, dpr * V.panX, dpr * V.panY);
    if (S.tiling) {
      // Візерунок, а не 9 drawImage: на межах копій немає світлих ліній від згладжування країв.
      const pat = ctx.createPattern(V.viewCanvas, 'repeat');
      ctx.fillStyle = pat;
      ctx.fillRect(-size, -size, 3 * size, 3 * size);
    } else {
      ctx.drawImage(V.viewCanvas, 0, 0);
    }
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    const x0 = Math.round(dpr * V.panX), y0 = Math.round(dpr * V.panY), w = Math.round(dpr * size * z);
    ctx.strokeStyle = S.tiling ? 'rgba(255,255,255,0.55)' : 'rgba(255,255,255,0.16)';
    ctx.lineWidth = 1;
    if (S.tiling) {           // лише кутові позначки: лінія по межі закрила б шов, який ми шукаємо
      const k = Math.min(12 * dpr, w / 6);
      ctx.beginPath();
      for (const [cx, cy, sx, sy] of [[x0, y0, 1, 1], [x0 + w, y0, -1, 1], [x0, y0 + w, 1, -1], [x0 + w, y0 + w, -1, -1]]) {
        ctx.moveTo(cx + 0.5, cy + 0.5 + sy * k); ctx.lineTo(cx + 0.5, cy + 0.5); ctx.lineTo(cx + 0.5 + sx * k, cy + 0.5);
      }
      ctx.stroke();
    } else {
      ctx.strokeRect(x0 - 0.5, y0 - 0.5, w + 1, w + 1);
    }
  }

  A.setView = function (view) {
    S.view = view;
    for (const b of document.querySelectorAll('#view-tabs button[data-view]')) {
      const on = b.dataset.view === view;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    }
    A.invalidateView(true);
  };

  A.setTeam = function (i) {
    S.team = i;
    for (const b of $('team-colors').children) b.setAttribute('aria-checked', +b.dataset.team === i ? 'true' : 'false');
    if (A.preview && A.preview.ok) { A.preview.setTeamColor(maps.TEAM_COLORS[i].rgb); A.preview.render(); }
    if (S.view === 'game') A.invalidateView(true);
  };

  // ---------- Масштаб і рух ----------

  function updateZoomLabel() { const b = $('zoom-reset'); if (b) b.textContent = Math.round(V.zoom * 100) + '%'; }
  function drawLater() { requestFrame(); drawCursor(); }

  A.fit = function () {
    if (!S.doc) return;
    const size = S.doc.size, span = S.tiling ? 2 * size : size;
    V.zoom = Math.min(ZMAX, Math.max(ZMIN, Math.min(V.W, V.H) * 0.94 / span));
    V.panX = (V.W - size * V.zoom) / 2;
    V.panY = (V.H - size * V.zoom) / 2;
    V.fitted = true;
    updateZoomLabel();
    drawLater();
  };

  // Масштаб навколо точки (sx, sy) у CSS px сцени.
  function zoomAt(z, sx, sy) {
    const nz = Math.min(ZMAX, Math.max(ZMIN, z));
    V.panX = sx - (sx - V.panX) * nz / V.zoom;
    V.panY = sy - (sy - V.panY) * nz / V.zoom;
    V.zoom = nz;
    V.fitted = false;
    updateZoomLabel();
    drawLater();
  }
  A.zoomBy = f => zoomAt(V.zoom * f, V.W / 2, V.H / 2);
  A.zoomTo = z => zoomAt(z, V.W / 2, V.H / 2);

  A.setTiling = function (on) {
    S.tiling = !!on;
    const b = $('tile-toggle');
    b.setAttribute('aria-pressed', S.tiling ? 'true' : 'false');
    b.classList.toggle('active', S.tiling);
    if (V.fitted) A.fit(); else drawLater();
  };

  function resizeStage() {
    const st = $('stage'), w = Math.max(1, st.clientWidth), h = Math.max(1, st.clientHeight);
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const cx = V.W / 2, cy = V.H / 2;
    V.W = w; V.H = h; V.dpr = dpr;
    for (const c of [V.canvas, V.overlay]) {
      const bw = Math.round(w * dpr), bh = Math.round(h * dpr);
      if (c.width !== bw || c.height !== bh) { c.width = bw; c.height = bh; }
    }
    if (!S.doc) return;
    if (V.fitted) { A.fit(); return; }
    V.panX += w / 2 - cx; V.panY += h / 2 - cy;     // центр сцени лишається на місці
    drawLater();
  }

  function stagePoint(e) {
    const r = V.canvas.getBoundingClientRect();
    return { sx: e.clientX - r.left, sy: e.clientY - r.top };
  }
  function texelAt(e) {
    const p = stagePoint(e);
    return { x: (p.sx - V.panX) / V.zoom, y: (p.sy - V.panY) / V.zoom };
  }
  // Для тестів і скриптів: клієнтські координати центру текселя (x, y) основної копії текстури.
  A.toClient = function (x, y) {
    const r = V.canvas.getBoundingClientRect();
    return { x: r.left + V.panX + (x + 0.5) * V.zoom, y: r.top + V.panY + (y + 0.5) * V.zoom };
  };

  A.setSpace = function (on) {
    V.space = !!on;
    V.canvas.classList.toggle('grab', V.space);
  };

  // ---------- Курсор пензля ----------

  function circle(o, x, y, r, dashed) {
    o.setLineDash(dashed ? [4, 4] : []);
    o.beginPath(); o.arc(x, y, r, 0, Math.PI * 2);
    o.strokeStyle = 'rgba(0,0,0,0.75)'; o.lineWidth = 3; o.stroke();
    o.strokeStyle = 'rgba(255,255,255,0.95)'; o.lineWidth = 1.2; o.stroke();
  }
  function cross(o, x, y, k) {
    o.setLineDash([]);
    o.beginPath(); o.moveTo(x - k, y); o.lineTo(x + k, y); o.moveTo(x, y - k); o.lineTo(x, y + k);
    o.strokeStyle = 'rgba(0,0,0,0.75)'; o.lineWidth = 3; o.stroke();
    o.strokeStyle = 'rgba(255,255,255,0.95)'; o.lineWidth = 1.2; o.stroke();
  }

  function drawCursor() {
    const o = V.octx, c = V.overlay;
    if (!o) return;
    o.setTransform(1, 0, 0, 1, 0, 0);
    o.clearRect(0, 0, c.width, c.height);
    const h = V.hover;
    if (!h || V.pan || V.space || !S.doc) return;
    o.setTransform(V.dpr, 0, 0, V.dpr, 0, 0);
    if (S.tool === 'fill' || S.tool === 'picker') {
      cross(o, h.sx, h.sy, 8);
      if (S.tool === 'picker') { o.strokeStyle = '#fff'; o.lineWidth = 1; o.strokeRect(h.sx + 6.5, h.sy + 6.5, 9, 9); }
      return;
    }
    const size = S.doc.size, z = V.zoom, r = Math.max(2, S.brush.size / 2 * z);
    const erase = S.tool === 'eraser';
    circle(o, h.sx, h.sy, r, erase);
    if (r > 8) cross(o, h.sx, h.sy, 3);
    const tx = (h.sx - V.panX) / z, ty = (h.sy - V.panY) / z, rt = S.brush.size / 2;
    // Без тайлінгу показуємо, куди перейде мазок за краєм (текстура загортається).
    if (!S.tiling) {
      const offs = [];
      const ox = tx - rt < 0 ? size : (tx + rt > size ? -size : 0), oy = ty - rt < 0 ? size : (ty + rt > size ? -size : 0);
      if (ox) offs.push([ox, 0]);
      if (oy) offs.push([0, oy]);
      if (ox && oy) offs.push([ox, oy]);
      for (const [dx, dy] of offs) circle(o, h.sx + dx * z, h.sy + dy * z, r, true);
    }
    // Дзеркальні точки симетрії (відносно центру текстури, в основній копії).
    const b = S.brush;
    if (b.symX || b.symY) {
      const wx = A.mod(tx, size), wy = A.mod(ty, size), pts = [];
      if (b.symX) pts.push([size - wx, wy]);
      if (b.symY) pts.push([wx, size - wy]);
      if (b.symX && b.symY) pts.push([size - wx, size - wy]);
      o.globalAlpha = 0.55;
      for (const [x, y] of pts) circle(o, V.panX + x * z, V.panY + y * z, r, true);
      o.globalAlpha = 1;
    }
  }
  A.drawCursor = drawCursor;

  function showCursorPos(e) {
    const t = texelAt(e), size = S.doc.size, out = $('cursor-pos');
    const inside = S.tiling ? (t.x >= -size && t.y >= -size && t.x < 2 * size && t.y < 2 * size) : (t.x >= 0 && t.y >= 0 && t.x < size && t.y < size);
    out.textContent = inside ? 'X ' + A.mod(Math.floor(t.x), size) + ' · Y ' + A.mod(Math.floor(t.y), size) : '';
  }

  // ---------- Інструменти ----------

  A.setTool = function (tool) {
    if (tool !== S.tool && tool === 'picker') S.prevTool = S.tool === 'picker' ? S.prevTool : S.tool;
    S.tool = tool;
    for (const b of document.querySelectorAll('#toolbar [data-tool]')) {
      const on = b.dataset.tool === tool;
      b.classList.toggle('active', on);
      b.setAttribute('aria-pressed', on ? 'true' : 'false');
    }
    $('fill-options').hidden = tool !== 'fill';
    V.canvas.dataset.tool = tool;
    drawCursor();
  };

  // Куди малює пензель зараз: { layer, target } або виняток з поясненням українською.
  A.paintTarget = function () {
    const layer = A.selectedLayer();
    if (!layer) throw new Error('Немає вибраного шару: додайте шар «Малювання» (+ Малювання)');
    if (S.target === 'mask') {
      if (!layer.mask) throw new Error('Шар «' + A.layerTitle(layer) + '» не має маски: додайте її (вкладка «Шар» → Маска) або перемкніть ціль на «Шар» (M)');
      return { layer, target: 'mask' };
    }
    if (layer.kind !== 'paint') {
      throw new Error('Пензель малює на шарі «Малювання», а «' + A.layerTitle(layer) + '» — ' + (layer.kind === 'fill' ? 'заливка' : 'генератор') +
        '. Виберіть шар «Малювання» (або + Малювання) чи малюйте маску цього шару (M)');
    }
    return { layer, target: 'paint' };
  };

  function currentAlpha() {
    const id = S.brush.alpha;
    if (typeof id === 'string' && id.indexOf('custom-') === 0) {
      const a = S.customAlphas.find(x => x.id === id);
      if (a) return a;
      S.brush.alpha = 'round';
    }
    return S.brush.alpha;
  }

  function strokeOptions() {
    const b = S.brush;
    return {
      size: b.size, spacing: b.spacing, flow: b.flow, sizeJitter: b.sizeJitter,
      angle: b.angle * DEG, angleJitter: b.angleJitter, scatter: b.scatter, followPath: b.followPath,
      symmetryX: b.symX, symmetryY: b.symY, docSize: S.doc.size, pressureFlow: true,
      seed: Math.floor(Math.random() * 4294967296)
    };
  }

  function copyMaterial(m) { return { color: m.color.slice(0, 3), alpha: m.alpha, height: m.height, spec: m.spec, team: m.team }; }

  // Пояснення, якщо мазок буде невидимим чи нічого не змінить (не помилка: малювати все одно можна).
  function strokeWarning(layer, target) {
    if (!layer.visible) return 'Шар «' + A.layerTitle(layer) + '» прихований — зміни не видно';
    if (target === 'mask' && layer.mask && layer.mask.enabled === false) return 'Маска шару вимкнена — намальоване на ній не діє';
    if (target === 'paint' && S.tool !== 'eraser') {
      const off = [];
      for (const c of layers.CHANNELS) if (S.matChannels[c] && !layer.channels[c]) off.push(A.C.CH_LABELS[c]);
      const any = layers.CHANNELS.some(c => S.matChannels[c] && layer.channels[c]);
      if (!any) return 'Жоден канал пензля не ввімкнено в шарі — увімкніть канали у властивостях шару';
      if (off.length) return 'Канали пензля, вимкнені в шарі: ' + off.join(', ');
    }
    return '';
  }

  function beginEdit(e) {
    let t;
    try { t = A.paintTarget(); } catch (err) { A.setStatus(A.errText(err), 'error'); return null; }
    try { A.ensureBelow(t.layer); } catch (err) { A.setStatus('Не вдалося підготувати шар: ' + A.errText(err), 'error'); return null; }
    return t;
  }

  function startStroke(e, p) {
    const t = beginEdit(e);
    if (!t) return;
    const erase = S.tool === 'eraser';
    const s = S.stroke = {
      id: e.pointerId, layer: t.layer, target: t.target, erase,
      snap: layers.snapshot(t.layer, t.target),
      alpha: currentAlpha(), hardness: S.brush.hardness,
      material: copyMaterial(S.material),
      channels: t.target === 'paint' && !erase ? Object.assign({}, S.matChannels) : undefined,
      count: 0, pending: false, last: p, engine: null
    };
    const warn = strokeWarning(t.layer, t.target);
    if (warn) A.setStatus(warn, '');
    try { V.canvas.setPointerCapture(e.pointerId); } catch (err) { /* не критично */ }
    const opts = strokeOptions();
    const lp = S.lastPoint;
    if (e.shiftKey && lp && lp.layerId === t.layer.id && lp.target === t.target) {
      applyDabs(brush.line(opts, lp.x, lp.y, p.x, p.y));
      finishStroke();
      return;
    }
    s.engine = brush.createStroke(opts);
    applyDabs(s.engine.begin(p.x, p.y, pressureOf(e)));
    requestFrame();
  }

  function pressureOf(e) { return e.pointerType === 'pen' && e.pressure > 0 ? e.pressure : 1; }

  function applyDabs(list) {
    const s = S.stroke, d = S.doc;
    if (!list.length) return;
    for (const q of list) {
      const dab = brush.getDab(s.alpha, q.size, q.angle, s.hardness);
      layers.applyDab(d, s.layer, s.target, dab, q.x, q.y, { flow: q.flow, material: s.material, channels: s.channels, erase: s.erase });
      A.markDirtyRect(q.x - dab.w / 2 - 1, q.y - dab.h / 2 - 1, dab.w + 2, dab.h + 2);
    }
    s.count += list.length;
    s.pending = true;
  }

  function finishStroke() {
    const s = S.stroke;
    if (!s) return;
    if (s.engine) { try { applyDabs(s.engine.end()); } catch (e) { /* end() нічого не малює */ } }
    S.stroke = null;
    if (s.pending) {
      s.pending = false;
      try { A.compositeActive(s.layer); S.mapsVer++; } catch (e) { A.setStatus('Не вдалося перебудувати текстуру: ' + A.errText(e), 'error'); }
    }
    if (s.count) {
      const label = s.target === 'mask' ? (s.erase ? 'гумка на масці' : 'малювання маски') : (s.erase ? 'гумка' : 'мазок');
      A.pushUndo({ type: 'buf', layerId: s.layer.id, snap: s.snap, label });
      A.markDirty();
      A.schedulePreview(120);
      if (A.statusKind() === 'error') A.setStatus('Готово', 'ok');
    }
    S.lastPoint = { x: s.last.x, y: s.last.y, layerId: s.layer.id, target: s.target };
    requestFrame();
  }

  // Заливка: область схожого кольору композиції (з допуском) або весь шар / вся маска.
  function bucket(e, p) {
    const t = beginEdit(e);
    if (!t) return;
    const d = S.doc, size = d.size, n = size * size;
    const x = A.mod(Math.floor(p.x), size), y = A.mod(Math.floor(p.y), size);
    let region;
    try { region = S.fill.whole ? new Uint8Array(n).fill(255) : brush.floodRegion(d.maps.color, size, x, y, S.fill.tolerance); }
    catch (err) { A.setStatus('Не вдалося знайти область: ' + A.errText(err), 'error'); return; }
    let count = 0;
    for (let i = 0; i < n; i++) if (region[i]) count++;
    const snap = layers.snapshot(t.layer, t.target);
    try {
      layers.fillRegion(d, t.layer, t.target, region, {
        flow: S.brush.flow, material: copyMaterial(S.material),
        channels: t.target === 'paint' ? Object.assign({}, S.matChannels) : undefined, erase: false
      });
    } catch (err) { layers.restore(t.layer, snap); A.setStatus('Не вдалося залити: ' + A.errText(err), 'error'); return; }
    A.pushUndo({ type: 'buf', layerId: t.layer.id, snap, label: t.target === 'mask' ? 'заливка маски' : 'заливка' });
    A.compositeActive(t.layer);
    A.mapsChanged(true);
    A.markDirty();
    A.schedulePreview(0);
    const warn = strokeWarning(t.layer, t.target);
    A.setStatus((S.fill.whole ? 'Залито весь ' + (t.target === 'mask' ? 'малюнок маски' : 'шар') : 'Залито ' + count + ' пікс. (' + Math.round(count / n * 100) + '%)') + (warn ? '. ' + warn : ''), 'ok');
    S.lastPoint = { x: p.x, y: p.y, layerId: t.layer.id, target: t.target };
  }

  // Піпетка: усі значення композиції під курсором -> матеріал пензля (перемикачі каналів не змінюються).
  function pick(p) {
    const d = S.doc, size = d.size, m = d.maps;
    const x = A.mod(Math.floor(p.x), size), y = A.mod(Math.floor(p.y), size), i = y * size + x, o = i * 4;
    S.material = { color: [m.color[o], m.color[o + 1], m.color[o + 2]], alpha: m.color[o + 3], height: Math.round(m.height[i] * 100) / 100, spec: m.spec[i], team: m.team[i] };
    S.matPreset = '';
    A.syncBrushMaterial();
    A.setStatus('Піпетка: колір ' + RA3.util.rgbToHex(S.material.color).toUpperCase() + ', альфа ' + S.material.alpha + ', висота ' + S.material.height.toFixed(2) +
      ', блиск ' + S.material.spec + ', гравець ' + S.material.team, 'ok');
    if (S.tool === 'picker') A.setTool(S.prevTool || 'brush');
  }

  // ---------- Події полотна ----------

  function onPointerDown(e) {
    if (V.pan || S.stroke) return;
    const sp = stagePoint(e);
    V.hover = e.pointerType === 'touch' ? null : sp;
    if (e.button === 1 || (e.button === 0 && V.space)) {
      e.preventDefault();
      V.pan = { id: e.pointerId, sx: sp.sx, sy: sp.sy, px: V.panX, py: V.panY };
      try { V.canvas.setPointerCapture(e.pointerId); } catch (err) { /* не критично */ }
      V.canvas.classList.add('panning');
      drawCursor();
      return;
    }
    if (e.pointerType === 'mouse' && e.button !== 0) return;
    e.preventDefault();
    if (document.activeElement && document.activeElement !== document.body && document.activeElement.blur) document.activeElement.blur();
    const p = texelAt(e), size = S.doc.size, r = S.brush.size / 2;
    if (!S.tiling && (p.x < -r || p.y < -r || p.x > size + r || p.y > size + r)) return;   // поза текстурою
    if (S.tool === 'picker' || e.altKey) { pick(p); return; }
    if (S.tool === 'fill') { bucket(e, p); return; }
    startStroke(e, p);
  }

  function onPointerMove(e) {
    const sp = stagePoint(e);
    if (V.pan && e.pointerId === V.pan.id) {
      V.panX = V.pan.px + sp.sx - V.pan.sx;
      V.panY = V.pan.py + sp.sy - V.pan.sy;
      V.fitted = false;
      requestFrame();
      return;
    }
    if (e.pointerType !== 'touch') V.hover = sp;
    showCursorPos(e);
    const s = S.stroke;
    if (s && e.pointerId === s.id && s.engine) {
      const list = typeof e.getCoalescedEvents === 'function' ? e.getCoalescedEvents() : [];
      for (const ce of (list.length ? list : [e])) {
        const p = texelAt(ce);
        s.last = p;
        applyDabs(s.engine.move(p.x, p.y, pressureOf(ce)));
      }
      requestFrame();
    }
    drawCursor();
  }

  function onPointerUp(e) {
    if (V.pan && e.pointerId === V.pan.id) {
      V.pan = null;
      V.canvas.classList.remove('panning');
      drawCursor();
      return;
    }
    const s = S.stroke;
    if (s && e.pointerId === s.id) finishStroke();
  }

  function onLeave() {
    V.hover = null;
    $('cursor-pos').textContent = '';
    drawCursor();
  }

  function onWheel(e) {
    e.preventDefault();
    const sp = stagePoint(e), k = e.deltaMode === 1 ? 0.05 : 0.0015;
    zoomAt(V.zoom * Math.exp(-e.deltaY * k), sp.sx, sp.sy);
  }

  A.initCanvas = function () {
    V.canvas = $('canvas-main'); V.ctx = V.canvas.getContext('2d');
    V.overlay = $('canvas-overlay'); V.octx = V.overlay.getContext('2d');
    const c = V.canvas;
    c.addEventListener('pointerdown', onPointerDown);
    c.addEventListener('pointermove', onPointerMove);
    c.addEventListener('pointerup', onPointerUp);
    c.addEventListener('pointercancel', onPointerUp);
    c.addEventListener('lostpointercapture', onPointerUp);
    c.addEventListener('pointerleave', onLeave);
    c.addEventListener('wheel', onWheel, { passive: false });
    c.addEventListener('contextmenu', e => e.preventDefault());
    c.addEventListener('auxclick', e => { if (e.button === 1) e.preventDefault(); });
    $('view-tabs').addEventListener('click', e => { const b = e.target.closest('button[data-view]'); if (b) A.setView(b.dataset.view); });
    $('tile-toggle').addEventListener('click', () => A.setTiling(!S.tiling));
    $('zoom-in').addEventListener('click', () => A.zoomBy(1.25));
    $('zoom-out').addEventListener('click', () => A.zoomBy(0.8));
    $('zoom-reset').addEventListener('click', () => A.zoomTo(1));
    $('zoom-fit').addEventListener('click', () => A.fit());
    for (const b of document.querySelectorAll('#toolbar [data-tool]')) b.addEventListener('click', () => A.setTool(b.dataset.tool));
    if (typeof ResizeObserver === 'function') new ResizeObserver(resizeStage).observe($('stage'));
    window.addEventListener('resize', resizeStage);
    resizeStage();
  };
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
