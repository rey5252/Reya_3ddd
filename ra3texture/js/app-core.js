(function (RA3) { 'use strict';
  // RA3 Texture Studio v2 — ядро інтерфейсу: константи, DOM-помічники, стан, статус, перебудова композиції,
  // кеш «під шаром» для швидкого малювання, undo/redo, заміна документа й демо-скін.
  // Файли застосунку (app-core → app-canvas → app-panels → app) додають функції в спільний простір RA3.App
  // і викликають одне одного через нього лише під час роботи, тож порядок оголошення між файлами не важливий.
  const util = RA3.util, layers = RA3.layers, maps = RA3.maps, materials = RA3.materials, maskgen = RA3.maskgen;
  const A = RA3.App = RA3.App || {};

  // ---------- Константи ----------

  const C = A.C = {
    SIZES: [256, 512, 1024, 2048],
    REBUILD_DELAY: 60,                   // мс debounce після зміни параметра
    UNDO_MAX: 60,                        // не більше 60 кроків…
    UNDO_BYTES: 256 * 1024 * 1024,       // …і не більше 256 МБ на всю історію
    CONFIRM_MS: 2500,                    // скільки кнопка чекає другого кліку
    DROP_CONFIRM_MS: 8000,
    DEFAULT_TEAM: 1,                     // Red
    TEAM_NAMES: { Gold: 'Золотий', Red: 'Червоний', Blue: 'Синій', Green: 'Зелений', Orange: 'Помаранчевий', SkyBlue: 'Блакитний', Purple: 'Фіолетовий', Pink: 'Рожевий' },
    CH_LABELS: { color: 'Колір', alpha: 'Альфа', height: 'Висота', spec: 'Блиск', team: 'Гравець' },
    KIND_LABELS: { fill: 'Заливка', paint: 'Малювання', proc: 'Генератор' },
    GROUP_SEP: ' / '                     // «Розумний матеріал / Шар» — шари одного набору
  };

  // ---------- DOM ----------

  const $ = A.$ = id => document.getElementById(id);
  const PROPS = { value: 1, checked: 1, disabled: 1, hidden: 1, selected: 1, draggable: 1 };

  A.el = function el(tag, props, children) {
    const e = document.createElement(tag);
    for (const k of Object.keys(props || {})) {
      const v = props[k];
      if (v === undefined || v === null) continue;
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k === 'dataset') Object.assign(e.dataset, v);
      else if (PROPS[k]) e[k] = v;
      else e.setAttribute(k, v);
    }
    if (children) for (const c of children) if (c !== null && c !== undefined) e.append(c);
    return e;
  };
  A.errText = e => (e && e.message) || String(e);
  A.sentence = t => (/[.!?…]$/.test(t) ? t : t + '.');
  A.sleep = ms => new Promise(r => setTimeout(r, ms));
  A.mod = (a, n) => ((a % n) + n) % n;

  // ---------- Стан ----------

  const S = A.S = {
    doc: null,
    selectedId: null,
    view: 'game',
    team: C.DEFAULT_TEAM,
    tool: 'brush', prevTool: 'brush',
    target: 'paint',                     // 'paint' — канали шару «Малювання», 'mask' — маска вибраного шару
    tiling: false,
    brush: { alpha: 'round', size: 24, flow: 1, hardness: 0.7, spacing: 0.12, sizeJitter: 0, angle: 0, angleJitter: 0, scatter: 0, followPath: false, symX: false, symY: false },
    customAlphas: [],                    // результати brush.alphaFromImage (лише на сеанс)
    material: null, matChannels: null, matPreset: '',
    fill: { tolerance: 32, whole: false },
    undo: [], redo: [],
    stroke: null,
    lastPoint: null,                     // кінець попереднього мазка (для Shift+клік)
    below: null,                         // { layerId, k, maps, memo } — композиція під вибраним шаром
    dirty: false, version: 0,            // незбережені зміни
    mapsVer: 0,                          // зростає після кожної зміни doc.maps
    nrmImportName: '',
    timers: { rebuild: 0, preview: 0, prefetch: 0 },
    rebuildFailed: false,
    perf: { rebuild: 0, frames: [] }
  };
  {
    const m = materials.get('allied-dark');
    S.material = m.material; S.matChannels = m.channels; S.matPreset = m.id;
  }

  // ---------- Статус ----------

  // kind: '' | 'ok' | 'busy' | 'error'
  A.setStatus = function (text, kind) {
    const s = $('status');
    if (!s) return;
    s.textContent = text;
    s.className = kind || '';
  };
  A.statusKind = () => { const s = $('status'); return s ? s.className : ''; };

  // Дії з підтвердженням другим кліком (без модальних вікон: window.confirm блокується в деяких оболонках).
  const armed = new Map();               // кнопка -> { title, timer }
  A.armConfirm = function (btn, message, ms) {
    let a = armed.get(btn);
    if (a) clearTimeout(a.timer);
    else {
      a = { title: btn.title };
      armed.set(btn, a);
      btn.classList.add('confirm');
      btn.title = 'Натисніть ще раз, щоб підтвердити';
    }
    a.timer = setTimeout(() => A.disarm(btn), ms || C.CONFIRM_MS);
    A.setStatus(message, '');
  };
  A.disarm = function (btn) {
    const a = armed.get(btn);
    if (!a) return;
    clearTimeout(a.timer);
    armed.delete(btn);
    btn.classList.remove('confirm');
    btn.title = a.title;
  };
  A.isArmed = btn => armed.has(btn);
  A.confirmThen = function (btn, message, fn) {
    if (armed.has(btn)) { A.disarm(btn); fn(); return; }
    A.armConfirm(btn, message);
  };

  // ---------- Шари ----------

  A.findLayer = id => (S.doc ? S.doc.layers.find(l => l.id === id) || null : null);
  A.selectedLayer = () => (S.selectedId ? A.findLayer(S.selectedId) : null);
  A.layerTitle = layer => {
    const name = layer.name || '', i = name.lastIndexOf(C.GROUP_SEP);
    return i >= 0 ? name.slice(i + C.GROUP_SEP.length) : name;
  };
  A.layerGroup = layer => {
    const name = layer.name || '', i = name.lastIndexOf(C.GROUP_SEP);
    return i >= 0 ? name.slice(0, i) : '';
  };

  A.markDirty = function () { S.dirty = true; S.version++; };

  // ---------- Перебудова ----------
  // S.below — композиція шарів під вибраним шаром k (layers.snapshotBelow). Поки вона дійсна, зміна самого шару k
  // (мазок, параметри, маска, непрозорість) перераховує лише шари k..кінець (layers.compositeFrom). Будь-яка інша
  // зміна (порядок, видимість чи параметри іншого шару, розмір) скидає S.below і вимагає повної перебудови.

  // Повна перебудова: snapshotBelow(k) + compositeFrom(k) — той самий результат, що layers.composite, але
  // знімок «під шаром» лишається для наступних змін вибраного шару.
  function rebuildFull() {
    const d = S.doc, layer = A.selectedLayer();
    if (layer) {
      const k = d.layers.indexOf(layer);
      S.below = { layerId: layer.id, k, maps: layers.snapshotBelow(d, k), memo: null };
      compositeFromBelow(layer, S.below);
    } else {
      S.below = null;
      layers.composite(d);
    }
  }

  A.rebuildNow = function () {
    clearTimeout(S.timers.rebuild); S.timers.rebuild = 0;
    const t0 = performance.now(), wasBusy = A.statusKind() === 'busy', layer = A.selectedLayer();
    let ok = true;
    try {
      if (layer && A.validBelow(layer)) compositeFromBelow(layer, S.below);
      else rebuildFull();
    } catch (e) {
      ok = false; S.below = null;
      A.setStatus('Не вдалося перебудувати текстуру: ' + A.errText(e), 'error');
    }
    S.perf.rebuild = performance.now() - t0;
    A.mapsChanged(true);
    A.schedulePreview();
    if (ok && (wasBusy || S.rebuildFailed)) A.setStatus('Готово', 'ok');
    S.rebuildFailed = !ok;
    return ok;
  };

  // Зміна, що впливає на композицію: перебудова через REBUILD_DELAY мс (повзунки не гальмують).
  // layer — шар, який змінився: якщо це вибраний шар, нижні шари не перераховуються.
  A.scheduleRebuild = function (layer) {
    A.markDirty();
    if (!(layer && layer.id === S.selectedId && A.validBelow(layer))) S.below = null;
    clearTimeout(S.timers.rebuild);
    S.timers.rebuild = setTimeout(() => {
      if (S.doc.size < 1024) { A.rebuildNow(); return; }
      A.setStatus('Перебудова…', 'busy');      // на великих текстурах помітно: даємо статусу намалюватися
      S.timers.rebuild = setTimeout(A.rebuildNow, 20);
    }, C.REBUILD_DELAY);
  };
  A.flushRebuild = function () { if (S.timers.rebuild) A.rebuildNow(); };

  // Знімок «під шаром» для щойно вибраного шару — заздалегідь, щоб перший мазок чи повзунок не чекав.
  A.prefetchBelow = function () {
    clearTimeout(S.timers.prefetch);
    S.timers.prefetch = setTimeout(() => {
      S.timers.prefetch = 0;
      if (S.stroke || S.timers.rebuild) return;
      const layer = A.selectedLayer();
      if (layer && !A.validBelow(layer)) {
        try { A.ensureBelow(layer); } catch (e) { /* покаже мазок чи перебудова */ }
      }
    }, 150);
  };

  A.validBelow = function (layer) {
    const b = S.below, d = S.doc;
    return !!(b && layer && b.layerId === layer.id && d.layers[b.k] === layer && b.maps.height.length === d.size * d.size);
  };

  A.ensureBelow = function (layer) {
    A.flushRebuild();
    if (!A.validBelow(layer)) {
      const k = S.doc.layers.indexOf(layer);
      S.below = { layerId: layer.id, k, maps: layers.snapshotBelow(S.doc, k), memo: null };
    }
    return S.below;
  };

  // Композиція після зміни буферів вибраного шару (мазок, заливка, undo мазка): нижні шари не перераховуються.
  A.compositeActive = function (layer) {
    if (S.timers.rebuild || !A.validBelow(layer)) { A.rebuildNow(); return; }
    compositeFromBelow(layer, S.below);
  };

  // Маска-генератор шару k залежить лише від композиції під ним, тож її результат запам'ятовується разом із
  // S.below (ключ — параметри генератора): кадр мазка не перераховує, наприклад, «Зношені краї».
  function compositeFromBelow(layer, b) {
    const m = layer.mask, gen = m && m.enabled !== false ? m.gen : null;
    if (!gen) { layers.compositeFrom(S.doc, b.k, b.maps); return; }
    const json = JSON.stringify(gen);
    if (!b.memo || b.memo.json !== json) b.memo = { json, result: null };
    const memo = b.memo;
    layers.setMaskgen({
      catalog: maskgen.catalog,
      generate(g, mp, size) {
        if (g !== gen) return maskgen.generate(g, mp, size);
        if (!memo.result || memo.result.length !== size * size) memo.result = maskgen.generate(g, mp, size);
        return memo.result;
      }
    });
    try { layers.compositeFrom(S.doc, b.k, b.maps); } finally { layers.setMaskgen(null); }
  }

  A.mapsChanged = function (full) {
    S.mapsVer++;
    if (A.invalidateView) A.invalidateView(full !== false);
  };

  // ---------- Undo / Redo ----------
  // Записи: { type: 'buf', layerId, snap } — буфери шару або маски (layers.snapshot/restore);
  //         { type: 'stack', layers, selectedId } — порядок і склад стеку (самі об'єкти шарів);
  //         { type: 'mask', layerId, mask } — об'єкт маски шару (додати/прибрати).
  // Застосування запису повертає зворотний запис для протилежного стеку.

  function bytesOfSnap(snap) {
    const p = snap && snap.paint;
    if (!p) return 0;
    if (ArrayBuffer.isView(p)) return p.byteLength;
    let b = 0;
    for (const c of Object.keys(p)) b += p[c].value.byteLength + p[c].cov.byteLength;
    return b;
  }
  function layerBytes(l) {
    let b = 0;
    if (l.paint) for (const c of Object.keys(l.paint)) b += l.paint[c].value.byteLength + l.paint[c].cov.byteLength;
    if (l.mask && l.mask.paint) b += l.mask.paint.byteLength;
    return b;
  }
  function entryBytes(e) {
    if (e.type === 'buf') return bytesOfSnap(e.snap);
    if (e.type === 'mask') return e.mask && e.mask.paint ? e.mask.paint.byteLength : 0;
    if (e.type === 'stack') { let b = 0; for (const l of e.layers) if (S.doc.layers.indexOf(l) < 0) b += layerBytes(l); return b; }
    return 0;
  }
  function historyBytes() {
    let b = 0;
    for (const e of S.undo) b += e.bytes || 0;
    for (const e of S.redo) b += e.bytes || 0;
    return b;
  }
  function trimHistory() {
    while (S.undo.length > C.UNDO_MAX || (S.undo.length > 1 && historyBytes() > C.UNDO_BYTES)) S.undo.shift();
    while (S.redo.length > C.UNDO_MAX || (S.redo.length > 0 && historyBytes() > C.UNDO_BYTES)) S.redo.shift();
  }

  // Знімок стеку до зміни: викликати до мутації, pushUndo — після (тоді враховано пам'ять видалених шарів).
  A.stackEntry = label => ({ type: 'stack', layers: S.doc.layers.slice(), selectedId: S.selectedId, label });

  A.pushUndo = function (entry) {
    entry.bytes = entryBytes(entry);
    S.undo.push(entry);
    S.redo.length = 0;
    trimHistory();
    A.updateUndoButtons();
  };

  A.clearHistory = function () { S.undo.length = 0; S.redo.length = 0; A.updateUndoButtons(); };

  A.updateUndoButtons = function () {
    const u = $('btn-undo'), r = $('btn-redo');
    if (u) { u.disabled = !S.undo.length; u.title = S.undo.length ? 'Скасувати: ' + S.undo[S.undo.length - 1].label + ' (Ctrl+Z)' : 'Скасувати (Ctrl+Z)'; }
    if (r) { r.disabled = !S.redo.length; r.title = S.redo.length ? 'Повернути: ' + S.redo[S.redo.length - 1].label + ' (Ctrl+Y)' : 'Повернути (Ctrl+Y)'; }
  };

  function applyEntry(e) {
    const d = S.doc;
    if (e.type === 'buf') {
      const layer = A.findLayer(e.layerId);
      if (!layer) return null;
      const cur = layers.snapshot(layer, e.snap.target);
      layers.restore(layer, e.snap);
      return { type: 'buf', layerId: e.layerId, snap: cur, label: e.label };
    }
    if (e.type === 'mask') {
      const layer = A.findLayer(e.layerId);
      if (!layer) return null;
      const cur = { type: 'mask', layerId: e.layerId, mask: layer.mask, label: e.label };
      layer.mask = e.mask;
      return cur;
    }
    if (e.type === 'stack') {
      const cur = { type: 'stack', layers: d.layers.slice(), selectedId: S.selectedId, label: e.label };
      d.layers = e.layers.slice();
      S.selectedId = A.findLayer(e.selectedId) ? e.selectedId : (d.layers.length ? d.layers[d.layers.length - 1].id : null);
      return cur;
    }
    return null;
  }

  function stepHistory(from, to, verb) {
    if (S.stroke || !from.length) return;
    A.flushRebuild();
    const e = from.pop();
    const sel = A.selectedLayer(), fast = e.type === 'buf' && sel && sel.id === e.layerId && A.validBelow(sel);
    const inv = applyEntry(e);
    if (!inv) {
      A.updateUndoButtons();
      A.setStatus('Не вдалося ' + (verb === 'Скасовано' ? 'скасувати' : 'повернути') + ' «' + e.label + '»: шару вже немає', 'error');
      return;
    }
    inv.bytes = entryBytes(inv);
    to.push(inv);
    trimHistory();
    A.markDirty();
    if (fast) { A.compositeActive(sel); A.mapsChanged(true); A.schedulePreview(0); }
    else { S.below = null; A.rebuildNow(); }       // стек чи інший шар змінились: знімок «під шаром» застарів
    A.syncTargetForLayer();
    A.renderLayers();
    A.renderProps();
    A.updateUndoButtons();
    A.setStatus(verb + ': ' + e.label, 'ok');
  }
  A.undo = () => stepHistory(S.undo, S.redo, 'Скасовано');
  A.redo = () => stepHistory(S.redo, S.undo, 'Повернуто');

  // ---------- Вибір шару й ціль ----------

  A.selectLayer = function (id, opts) {
    if (S.selectedId === id && !(opts && opts.force)) return;
    S.selectedId = id;
    A.syncTargetForLayer();
    A.renderLayers();
    A.renderProps();
    A.prefetchBelow();
  };

  // Ціль «Маска» без маски у вибраного шару нічого не дасть — тоді лишається «Шар» (і навпаки для не-малювання).
  A.syncTargetForLayer = function () {
    const layer = A.selectedLayer();
    if (layer && S.target === 'mask' && !layer.mask && layer.kind === 'paint') S.target = 'paint';
    if (layer && S.target === 'paint' && layer.kind !== 'paint' && layer.mask) S.target = 'mask';
    if (A.syncTarget) A.syncTarget();
  };

  A.setTarget = function (t) {
    S.target = t === 'mask' ? 'mask' : 'paint';
    if (A.syncTarget) A.syncTarget();
    const layer = A.selectedLayer();
    if (!layer) return;
    if (S.target === 'mask' && !layer.mask) A.setStatus('Шар «' + A.layerTitle(layer) + '» не має маски: додайте її на вкладці «Шар» → Маска', '');
    else if (S.target === 'paint' && layer.kind !== 'paint') A.setStatus('Пензель малює на шарі «Малювання». Виберіть такий шар або малюйте маску (M)', '');
    else A.setStatus(S.target === 'mask' ? 'Ціль: маска шару «' + A.layerTitle(layer) + '» — пензель відкриває шар, гумка ховає' : 'Ціль: шар «' + A.layerTitle(layer) + '»', 'ok');
  };

  // ---------- Документ ----------

  // Єдина точка заміни документа: новий, відкритий проєкт, демо.
  A.setDocument = function (doc, opts) {
    const o = opts || {};
    S.doc = doc;
    S.below = null;
    S.lastPoint = null;
    S.nrmImportName = o.nrmName || '';
    clearTimeout(S.timers.rebuild); S.timers.rebuild = 0;
    A.clearHistory();
    let sel = null;
    for (let i = doc.layers.length - 1; i >= 0 && !sel; i--) if (doc.layers[i].kind === 'paint') sel = doc.layers[i];
    if (!sel && doc.layers.length) sel = doc.layers[doc.layers.length - 1];
    S.selectedId = sel ? sel.id : null;
    S.target = 'paint';
    A.syncTargetForLayer();
    A.syncDocControls();
    A.renderLayers();
    A.renderProps();
    A.resetView();
    A.rebuildNow();
    S.dirty = false;
  };

  // Порожній документ: заливка-основа (світла фарба Альянсу) і шар «Малювання».
  A.blankDocument = function (name, size) {
    const d = layers.createDocument({ name, size });
    const base = materials.get('allied-light');
    d.layers.push(layers.newLayer('fill', { name: 'Основа: ' + base.label, material: base.material, channels: base.channels }));
    d.layers.push(layers.newLayer('paint', { name: 'Малювання' }));
    return d;
  };

  // Демо: панелі з заклепками (рельєф), розумний матеріал «Альянс: фарба зі зношеними краями», смуга кольору
  // гравця (шар «Малювання» з інвертованою маскою «Зношені краї» — смуга стерта на ребрах) і порожній шар
  // «Малювання», вибраний для пензля.
  A.createDemo = function () {
    const size = 512, n = size * size;
    const d = layers.createDocument({ name: 'AUMyTank', size });
    const L = d.layers;
    L.push(layers.newLayer('proc', { proc: { type: 'panels', seed: 7, params: { countX: 4, countY: 3, jitter: 0.45, seam: 2.5, depth: 0.25 } }, name: 'Панелі' }));
    L.push(layers.newLayer('proc', { proc: { type: 'rivets', seed: 11, params: { mode: 'seams', spacing: 22, size: 2.25 } }, name: 'Заклепки' }));
    const smart = materials.smart.find(s => s.id === 'allied-worn');
    for (const spec of materials.smartLayers('allied-worn', 2024)) {
      const l = layers.layerFromSpec(spec, size);
      l.name = smart.label + C.GROUP_SEP + l.name;
      L.push(l);
    }
    const tp = materials.get('team-paint');
    const team = layers.newLayer('paint', { name: 'Колір гравця', channels: { color: true, alpha: false, height: false, spec: true, team: true } });
    const region = new Uint8Array(n);
    const band = (y0, y1) => { for (let y = Math.round(y0 * size); y < Math.round(y1 * size); y++) region.fill(255, y * size, (y + 1) * size); };
    band(0.30, 0.40); band(0.43, 0.455);
    // Менше блиску, ніж у пресета: у вигляді «У грі» смуга насиченіша.
    layers.fillRegion(d, team, 'paint', region, { material: Object.assign({}, tp.material, { spec: 24 }), channels: tp.channels });
    team.mask = { enabled: true, invert: true, paint: null, gen: { type: 'edges', seed: 5, params: { width: 3, contrast: 0.6, breakup: 0.6, scale: 1 } } };
    L.push(team);
    L.push(layers.newLayer('paint', { name: 'Малювання' }));
    return d;
  };
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
