(function (RA3) { 'use strict';
  // RA3 Texture Studio v2 — панелі: панель інструментів, пензель (матеріал, альфи, динаміка), стек шарів
  // і властивості вибраного шару з маскою. Форми параметрів будуються з метаданих generators/maskgen.catalog.
  const util = RA3.util, layers = RA3.layers, brush = RA3.brush, materials = RA3.materials;
  const generators = RA3.generators, maskgen = RA3.maskgen;
  const A = RA3.App, S = A.S, C = A.C, $ = A.$, el = A.el;

  // Генератори, чий візерунок залежить від seed, і маски-генератори з шумом.
  const SEEDED = { base: 1, camo: 1, panels: 1, rivets: 1, dirt: 1, rust: 1, scratches: 1 };
  const MASK_SEEDED = { edges: 1, cavity: 1, noise: 1 };
  // Параметри, що діють лише в певних режимах (інші ховаються).
  const notLuma = p => p.mode !== 'luma', stripes = p => p.mode === 'stripes';
  const PARAM_VISIBLE = {
    team: { x: notLuma, y: notLuma, w: notLuma, h: notLuma, angle: stripes, count: stripes, lumaMin: p => p.mode === 'luma' },
    image: { channel: p => p.target !== 'color' }
  };
  const EYE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z"/><circle cx="12" cy="12" r="2.8"/></svg>';
  const EYE_OFF = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z"/><path d="M4 20 20 4"/></svg>';

  // ---------- Поля форм ----------

  function fmtParam(p, v) {
    if (p.max <= 1 && p.step < 1) return Math.round(v * 100) + '%';
    const dec = p.step >= 1 ? 0 : (String(p.step).split('.')[1] || '').length;
    return (+v).toFixed(dec);
  }

  function field(label, control, id, out) {
    const lab = el('label', { for: id }, [el('span', { text: label })]);
    if (out) lab.append(out);
    return el('div', { class: 'field' }, [lab, control]);
  }

  function rangeField(o) {
    const input = el('input', { type: 'range', id: o.id, min: o.min, max: o.max, step: o.step, title: o.title });
    input.value = o.value;
    const out = el('output', { text: o.fmt(+input.value) });
    input.addEventListener('input', () => { out.textContent = o.fmt(+input.value); o.onInput(+input.value); });
    const f = field(o.label, input, o.id, out);
    f.sync = v => { input.value = v; out.textContent = o.fmt(+input.value); };
    return f;
  }

  function checkField(o) {
    const input = el('input', { type: 'checkbox', id: o.id, checked: !!o.checked });
    input.addEventListener('change', () => o.onChange(input.checked));
    return el('label', { class: 'check', title: o.title }, [input, el('span', { text: o.label })]);
  }

  function selectField(o) {
    const input = el('select', { id: o.id });
    for (const opt of o.options) input.append(el('option', { value: opt.value, text: opt.label }));
    input.value = o.value;
    input.addEventListener('change', () => o.onChange(input.value));
    return field(o.label, input, o.id);
  }

  function seedField(id, holder, onChange) {
    const input = el('input', { type: 'number', id, min: 0, max: 4294967295, step: 1, inputmode: 'numeric' });
    input.value = holder.seed;
    const dice = el('button', { type: 'button', id: id + '-dice', class: 'dice', title: 'Новий випадковий варіант', 'aria-label': 'Новий випадковий варіант', text: '🎲' });
    input.addEventListener('change', () => {
      const v = Math.floor(+input.value);
      if (!Number.isFinite(v) || v < 0) { input.value = holder.seed; return; }
      holder.seed = v % 4294967296;
      input.value = holder.seed;
      onChange();
    });
    dice.addEventListener('click', () => { holder.seed = Math.floor(Math.random() * 4294967296); input.value = holder.seed; onChange(); });
    return field('Варіант (seed)', el('div', { class: 'row' }, [input, dice]), id);
  }

  // Варіанти imageId: зображення документа з назвою першого шару, що на нього посилається («Роль · файл»).
  function imageOptions(current) {
    const names = {};
    for (const l of S.doc.layers) {
      const id = l.kind === 'proc' && l.proc && l.proc.type === 'image' ? l.proc.params.imageId : null;
      if (id && !(id in names)) names[id] = String(A.layerTitle(l)).split(' · ').pop();
    }
    const opts = Object.keys(S.doc.images).map((id, i) => ({ value: id, label: names[id] || 'Зображення ' + (i + 1) }));
    if (!(current in S.doc.images)) opts.unshift({ value: current, label: '— немає файлу —' });
    return opts;
  }

  // Форма параметрів за метаданими каталогу (generators.catalog і maskgen.catalog мають однаковий формат).
  function paramForm(def, params, prefix, onChange, visible) {
    const box = el('div', { class: 'params' });
    const values = () => { const v = {}; for (const p of def.params) v[p.key] = params[p.key] === undefined ? p.default : params[p.key]; return v; };
    const updateVisibility = () => {
      if (!visible) return;
      const v = values();
      for (const f of box.querySelectorAll('[data-key]')) { const rule = visible[f.dataset.key]; f.hidden = rule ? !rule(v) : false; }
    };
    const cur = values();
    for (const p of def.params) {
      const id = prefix + p.key;
      const set = v => { params[p.key] = v; onChange(p, v); updateVisibility(); };
      let f;
      if (p.kind === 'range') f = rangeField({ id, label: p.label, min: p.min, max: p.max, step: p.step, value: cur[p.key], fmt: v => fmtParam(p, v), onInput: set });
      else if (p.kind === 'color') {
        const input = el('input', { type: 'color', id, value: cur[p.key] });
        const code = el('output', { text: String(cur[p.key]).toUpperCase() });
        input.addEventListener('input', () => { code.textContent = input.value.toUpperCase(); set(input.value); });
        f = field(p.label, input, id, code);
      } else if (p.kind === 'select') {
        const opts = p.source === 'images' ? imageOptions(cur[p.key]) : p.options;
        f = selectField({ id, label: p.label, options: opts, value: cur[p.key], onChange: set });
      } else f = checkField({ id, label: p.label, checked: cur[p.key], onChange: set });
      f.dataset.key = p.key;
      box.append(f);
    }
    updateVisibility();
    return box;
  }

  // ---------- Редактор матеріалу (пензель і шар-заливка) ----------

  function heightWord(v) { return v < 0.47 ? 'шов' : (v > 0.53 ? 'опуклість' : 'рівно'); }

  // b = { mat, ch, preset, onChange(kind) } — mat і ch змінюються на місці; ids: select, prefix.
  function materialEditor(b, selectId, prefix) {
    const box = el('div', { class: 'material' });
    const sel = el('select', { id: selectId, 'aria-label': 'Пресет матеріалу' });
    sel.append(el('option', { value: '', text: '— свій матеріал —' }));
    for (const m of materials.list) sel.append(el('option', { value: m.id, text: m.label }));
    sel.value = b.preset || '';
    box.append(field('Пресет', sel, selectId));
    const rows = {};
    const row = (c, control, out) => {
      const on = el('input', { type: 'checkbox', id: prefix + '-on-' + c, checked: !!b.ch[c], title: 'Канал «' + C.CH_LABELS[c] + '»: увімкнений — записується' });
      on.addEventListener('change', () => { b.ch[c] = on.checked; b.preset = ''; sel.value = ''; b.onChange('channels'); });
      const r = el('div', { class: 'mat-row' + (b.ch[c] ? '' : ' off') }, [
        el('label', { class: 'check', for: prefix + '-on-' + c }, [on, el('span', { text: C.CH_LABELS[c] })]), control, out]);
      rows[c] = { r, on };
      box.append(r);
    };
    const color = el('input', { type: 'color', id: prefix + '-color', 'aria-label': 'Колір' });
    const colorOut = el('output');
    color.addEventListener('input', () => { b.mat.color = util.hexToRgb(color.value); colorOut.textContent = color.value.toUpperCase(); b.preset = ''; sel.value = ''; b.onChange('value'); });
    row('color', color, colorOut);
    const ranges = {};
    const range = (c, min, max, step, fmt) => {
      const input = el('input', { type: 'range', id: prefix + '-' + c, min, max, step, 'aria-label': C.CH_LABELS[c] });
      const out = el('output');
      input.addEventListener('input', () => { b.mat[c] = +input.value; out.textContent = fmt(+input.value); b.preset = ''; sel.value = ''; b.onChange('value'); });
      ranges[c] = { input, out, fmt };
      row(c, input, out);
    };
    range('alpha', 0, 255, 1, v => String(v));
    range('height', 0, 1, 0.01, v => v.toFixed(2) + ' ' + heightWord(v));
    range('spec', 0, 255, 1, v => String(v));
    range('team', 0, 255, 1, v => String(v));
    const sync = () => {
      const hex = util.rgbToHex(b.mat.color);
      color.value = hex; colorOut.textContent = hex.toUpperCase();
      for (const c of Object.keys(ranges)) { const r = ranges[c]; r.input.value = b.mat[c]; r.out.textContent = r.fmt(+b.mat[c]); }
      for (const c of layers.CHANNELS) { rows[c].on.checked = !!b.ch[c]; rows[c].r.classList.toggle('off', !b.ch[c]); }
      sel.value = b.preset || '';
    };
    sel.addEventListener('change', () => {
      if (!sel.value) { b.preset = ''; return; }
      const m = materials.get(sel.value);
      Object.assign(b.mat, m.material);
      for (const c of layers.CHANNELS) b.ch[c] = !!m.channels[c];
      b.preset = m.id;
      sync();
      b.onChange('preset');
    });
    box.addEventListener('change', e => { if (e.target.type === 'checkbox') for (const c of layers.CHANNELS) rows[c].r.classList.toggle('off', !b.ch[c]); });
    sync();
    box.sync = sync;
    return box;
  }

  // ---------- Пензель ----------

  let brushMatEditor = null;
  const brushBinding = {
    get mat() { return S.material; }, get ch() { return S.matChannels; },
    get preset() { return S.matPreset; }, set preset(v) { S.matPreset = v; },
    onChange() { if (S.tool === 'eraser' || S.tool === 'picker') A.setTool('brush'); }
  };
  A.syncBrushMaterial = function () { if (brushMatEditor) brushMatEditor.sync(); };

  function drawAlphaThumb(cv, ref) {
    const W = cv.width, dab = brush.getDab(ref, W - 6, 0, ref === 'round' ? S.brush.hardness : 0.8);
    const ctx = cv.getContext('2d'), img = ctx.createImageData(W, W), ox = (W - dab.w) >> 1, oy = (W - dab.h) >> 1;
    for (let y = 0, o = 0; y < W; y++) {
      for (let x = 0; x < W; x++, o += 4) {
        const dx = x - ox, dy = y - oy;
        const v = dx >= 0 && dy >= 0 && dx < dab.w && dy < dab.h ? dab.data[dy * dab.w + dx] : 0;
        const g = 24 + (235 - 24) * v;
        img.data[o] = g; img.data[o + 1] = g; img.data[o + 2] = g; img.data[o + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
  }

  A.renderAlphaGrid = function () {
    const grid = $('alpha-grid');
    grid.textContent = '';
    const all = brush.ALPHAS.map(a => ({ id: a.id, label: a.label, ref: a.id }))
      .concat(S.customAlphas.map((a, i) => ({ id: a.id, label: 'Своя ' + (i + 1), ref: a })));
    for (const a of all) {
      const on = S.brush.alpha === a.id;
      const b = el('button', { type: 'button', class: 'alpha' + (on ? ' active' : ''), role: 'radio', 'aria-checked': on ? 'true' : 'false', title: a.label, dataset: { alpha: a.id } });
      const cv = el('canvas', { width: 40, height: 40 });
      try { drawAlphaThumb(cv, a.ref); } catch (e) { /* мініатюра не критична */ }
      b.append(cv, el('span', { text: a.label }));
      grid.append(b);
    }
  };

  A.setAlpha = function (id) {
    S.brush.alpha = id;
    for (const b of $('alpha-grid').children) {
      const on = b.dataset.alpha === id;
      b.classList.toggle('active', on);
      b.setAttribute('aria-checked', on ? 'true' : 'false');
    }
    if (S.tool !== 'brush' && S.tool !== 'eraser') A.setTool('brush');
  };

  A.addCustomAlpha = function (img, name) {
    const a = brush.alphaFromImage(img.data, img.width, img.height);
    if (!S.customAlphas.some(x => x.id === a.id)) S.customAlphas.push(a);
    A.renderAlphaGrid();
    A.setAlpha(a.id);
    A.setStatus('Альфа з «' + name + '» додана й вибрана (діє до закриття сторінки)', 'ok');
  };

  const BRUSH_DEFAULTS = { spacing: 0.12, sizeJitter: 0, angle: 0, angleJitter: 0, scatter: 0, followPath: false };

  function renderDynamics() {
    const box = $('brush-dynamics'), b = S.brush;
    box.textContent = '';
    const pct = v => Math.round(v) + '%';
    box.append(
      rangeField({ id: 'brush-spacing', label: 'Інтервал', min: 2, max: 200, step: 1, value: Math.round(b.spacing * 100), fmt: pct, onInput: v => { b.spacing = v / 100; } }),
      rangeField({ id: 'brush-size-jitter', label: 'Розкид розміру', min: 0, max: 100, step: 1, value: Math.round(b.sizeJitter * 100), fmt: pct, onInput: v => { b.sizeJitter = v / 100; } }),
      rangeField({ id: 'brush-angle', label: 'Кут', min: -180, max: 180, step: 1, value: b.angle, fmt: v => v + '°', onInput: v => { b.angle = v; } }),
      rangeField({ id: 'brush-angle-jitter', label: 'Розкид кута', min: 0, max: 100, step: 1, value: Math.round(b.angleJitter * 100), fmt: pct, onInput: v => { b.angleJitter = v / 100; } }),
      rangeField({ id: 'brush-scatter', label: 'Розсіювання', min: 0, max: 100, step: 1, value: Math.round(b.scatter * 100), fmt: pct, onInput: v => { b.scatter = v / 100; } }),
      checkField({ id: 'brush-follow', label: 'Повертати за напрямком мазка', checked: b.followPath, onChange: v => { b.followPath = v; } })
    );
    const reset = el('button', { type: 'button', id: 'brush-dyn-reset', text: 'Скинути динаміку' });
    reset.addEventListener('click', () => { Object.assign(S.brush, BRUSH_DEFAULTS); renderDynamics(); });
    box.append(reset);
  }

  // ---------- Панель інструментів ----------

  function bindSlider(id, get, set, fmt) {
    const input = $(id), out = $(id + '-out');
    const sync = () => { input.value = get(); out.textContent = fmt(+input.value); };
    input.addEventListener('input', () => { set(+input.value); out.textContent = fmt(+input.value); });
    sync();
    return sync;
  }

  let syncSize = null;
  A.setBrushSize = function (v) {
    S.brush.size = Math.max(1, Math.min(512, Math.round(v)));
    if (syncSize) syncSize();
    A.drawCursor();
  };
  A.nudgeBrushSize = function (dir) {
    const v = S.brush.size;
    A.setBrushSize(dir > 0 ? Math.max(v + 1, v * 1.2) : Math.min(v - 1, v / 1.2));
  };

  A.syncTarget = function () {
    const t = $('target-toggle');
    if (!t) return;
    for (const s of t.querySelectorAll('[data-target]')) s.classList.toggle('active', s.dataset.target === S.target);
    t.dataset.target = S.target;
    t.setAttribute('aria-label', 'Ціль пензля: ' + (S.target === 'mask' ? 'маска' : 'шар'));
    for (const b of document.querySelectorAll('#layer-list [data-act="mask"]')) b.classList.toggle('target', S.target === 'mask' && b.closest('li').dataset.id === S.selectedId);
  };

  function bindToolbar() {
    syncSize = bindSlider('brush-size', () => S.brush.size, v => { S.brush.size = v; A.drawCursor(); }, v => v + ' px');
    bindSlider('brush-flow', () => Math.round(S.brush.flow * 100), v => { S.brush.flow = v / 100; }, v => v + '%');
    bindSlider('brush-hardness', () => Math.round(S.brush.hardness * 100), v => {
      S.brush.hardness = v / 100;
      const round = $('alpha-grid').querySelector('[data-alpha="round"] canvas');
      if (round) drawAlphaThumb(round, 'round');
    }, v => v + '%');
    bindSlider('fill-tolerance', () => S.fill.tolerance, v => { S.fill.tolerance = v; }, v => String(v));
    $('fill-whole').checked = S.fill.whole;
    $('fill-whole').addEventListener('change', e => { S.fill.whole = e.target.checked; });
    $('sym-x').addEventListener('change', e => { S.brush.symX = e.target.checked; A.drawCursor(); });
    $('sym-y').addEventListener('change', e => { S.brush.symY = e.target.checked; A.drawCursor(); });
    $('target-toggle').addEventListener('click', e => {
      const seg = e.target.closest('[data-target]');
      A.setTarget(seg ? seg.dataset.target : (S.target === 'mask' ? 'paint' : 'mask'));
    });
    $('btn-undo').addEventListener('click', A.undo);
    $('btn-redo').addEventListener('click', A.redo);
  }

  function bindBrushPanel() {
    brushMatEditor = materialEditor(brushBinding, 'material-select', 'mat');
    $('brush-material').append(brushMatEditor);
    A.renderAlphaGrid();
    $('alpha-grid').addEventListener('click', e => { const b = e.target.closest('[data-alpha]'); if (b) A.setAlpha(b.dataset.alpha); });
    $('btn-add-alpha').addEventListener('click', () => $('alpha-input').click());
    renderDynamics();
  }

  // ---------- Бічні вкладки ----------

  A.setTab = function (tab) {
    for (const b of document.querySelectorAll('#side-tabs [data-tab]')) {
      const on = b.dataset.tab === tab;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    }
    for (const p of document.querySelectorAll('.tab-body[data-panel]')) p.hidden = p.dataset.panel !== tab;
  };

  // ---------- Стек шарів ----------

  function genLabel(type) { const d = generators.catalog.find(c => c.type === type); return d ? d.label : type; }
  function kindText(layer) { return layer.kind === 'proc' ? 'Генератор: ' + genLabel(layer.proc.type) : C.KIND_LABELS[layer.kind]; }

  A.renderLayers = function () {
    const list = $('layer-list'), L = S.doc.layers;
    list.textContent = '';
    if (!L.length && !S.doc.nrmImport) list.append(el('li', { class: 'empty', text: 'Шарів немає. Додайте шар кнопками вище або перетягніть зображення на сторінку.' }));
    let prevGroup = '';
    for (let i = L.length - 1; i >= 0; i--) {
      const layer = L[i], group = A.layerGroup(layer), selected = layer.id === S.selectedId;
      if (group && group !== prevGroup) list.append(el('li', { class: 'group-head', text: group, title: 'Набір шарів розумного матеріалу' }));
      prevGroup = group;
      const li = el('li', {
        class: 'layer' + (selected ? ' selected' : '') + (layer.visible ? '' : ' hidden-layer') + (group ? ' grouped' : ''),
        dataset: { id: layer.id, kind: layer.kind, type: layer.kind === 'proc' ? layer.proc.type : layer.kind },
        draggable: true, 'aria-selected': selected ? 'true' : 'false'
      });
      const eye = el('button', { type: 'button', class: 'eye', dataset: { act: 'eye' }, title: layer.visible ? 'Сховати шар' : 'Показати шар', 'aria-label': (layer.visible ? 'Сховати' : 'Показати') + ' шар «' + A.layerTitle(layer) + '»', 'aria-pressed': layer.visible ? 'true' : 'false' });
      eye.innerHTML = layer.visible ? EYE : EYE_OFF;
      const name = el('div', { class: 'lname' }, [
        el('span', { class: 'ltitle', text: A.layerTitle(layer) || '(без назви)' }),
        el('span', { class: 'lkind', text: kindText(layer) + (layer.opacity < 1 ? ' · ' + Math.round(layer.opacity * 100) + '%' : '') })
      ]);
      li.append(eye, name);
      if (layer.mask) {
        const m = layer.mask;
        const mb = el('button', {
          type: 'button', class: 'mask-badge' + (m.enabled === false ? ' off' : '') + (S.target === 'mask' && selected ? ' target' : ''),
          dataset: { act: 'mask' }, text: m.gen ? 'M+' : 'M',
          title: 'Маска' + (m.gen ? ' з генератором' : '') + (m.enabled === false ? ' (вимкнена)' : '') + (m.invert ? ', інвертована' : '') + '. Клік — малювати маску'
        });
        li.append(mb);
      }
      list.append(li);
    }
    if (S.doc.nrmImport) {
      const li = el('li', { class: 'layer pseudo', dataset: { type: 'nrm' } });
      const drop = el('button', { type: 'button', class: 'mini', dataset: { act: 'drop-nrm' }, text: '✕', title: 'Прибрати імпортовану NRM' });
      li.append(el('span', { class: 'eye' }), el('div', { class: 'lname' }, [
        el('span', { class: 'ltitle', text: S.nrmImportName || 'Імпортована NRM' }),
        el('span', { class: 'lkind', text: 'Карта нормалей: змішується з рельєфом шарів' })]), drop);
      list.append(li);
    }
    syncLayerFoot();
  };

  function syncLayerFoot() {
    const layer = A.selectedLayer(), L = S.doc.layers, i = layer ? L.indexOf(layer) : -1;
    const op = $('layer-opacity');
    op.disabled = !layer;
    op.value = layer ? Math.round(layer.opacity * 100) : 100;
    $('layer-opacity-out').textContent = op.value + '%';
    $('btn-layer-up').disabled = !layer || i >= L.length - 1;
    $('btn-layer-down').disabled = !layer || i <= 0;
    $('btn-layer-dup').disabled = !layer;
    $('btn-layer-del').disabled = !layer;
  }

  // Нові шари стають над вибраним (або нагору), останній вибирається.
  function insertLayers(list, label) {
    const d = S.doc, entry = A.stackEntry(label);
    const sel = A.selectedLayer(), at = sel ? d.layers.indexOf(sel) + 1 : d.layers.length;
    d.layers.splice(at, 0, ...list);
    A.pushUndo(entry);
    S.selectedId = list[list.length - 1].id;
    A.syncTargetForLayer();
    A.renderLayers();
    A.renderProps();
    A.scheduleRebuild();
    const li = $('layer-list').querySelector('li[data-id="' + S.selectedId + '"]');
    if (li && li.scrollIntoView) li.scrollIntoView({ block: 'nearest' });
  }
  A.insertLayers = insertLayers;

  function uniqueName(base) {
    const names = new Set(S.doc.layers.map(l => l.name));
    if (!names.has(base)) return base;
    for (let k = 2; ; k++) if (!names.has(base + ' ' + k)) return base + ' ' + k;
  }

  function addPaint() {
    const l = layers.newLayer('paint', { name: uniqueName('Малювання') });
    insertLayers([l], 'додавання шару');
    S.target = 'paint'; A.syncTarget();
    if (S.tool === 'picker') A.setTool('brush');
    A.setStatus('Додано шар «' + l.name + '»: малюйте пензлем (B)', 'ok');
  }

  function addFill() {
    const preset = S.matPreset ? materials.list.find(m => m.id === S.matPreset) : null;
    const l = layers.newLayer('fill', {
      name: uniqueName(preset ? preset.label : 'Заливка'),
      material: Object.assign({ color: S.material.color.slice(0, 3), alpha: S.material.alpha, height: S.material.height, spec: S.material.spec, team: S.material.team }, preset ? { id: preset.id } : {}),
      channels: Object.assign({}, S.matChannels)
    });
    insertLayers([l], 'додавання шару');
    A.setTab('layer');
    A.setStatus('Додано заливку «' + l.name + '» матеріалом пензля. Додайте маску, щоб обмежити її', 'ok');
  }

  function addGenerator(type) {
    const l = layers.newLayer('proc', { proc: { type } });
    l.name = uniqueName(l.name);
    insertLayers([l], 'додавання шару');
    A.setTab('layer');
    A.setStatus('Додано генератор «' + l.name + '»', 'ok');
  }

  function addSmart(id) {
    const def = materials.smart.find(s => s.id === id);
    const list = materials.smartLayers(id).map(spec => {
      const l = layers.layerFromSpec(spec, S.doc.size);
      l.name = def.label + C.GROUP_SEP + l.name;
      return l;
    });
    insertLayers(list, 'розумний матеріал');
    A.setStatus('Додано розумний матеріал «' + def.label + '»: ' + list.length + ' шари з масками. ' + def.description, 'ok');
  }

  A.moveLayerTo = function (id, to) {
    const L = S.doc.layers, from = L.findIndex(l => l.id === id);
    if (from < 0) return;
    const j = Math.max(0, Math.min(L.length - 1, to));
    if (j === from) return;
    const entry = A.stackEntry('порядок шарів');
    const [l] = L.splice(from, 1);
    L.splice(j, 0, l);
    A.pushUndo(entry);
    A.renderLayers();
    A.scheduleRebuild();
  };

  function duplicateLayer() {
    const layer = A.selectedLayer();
    if (!layer) return;
    const copy = layers.cloneLayer(layer);
    insertLayers([copy], 'копія шару');
    A.setStatus('Шар скопійовано: «' + A.layerTitle(copy) + '»', 'ok');
  }

  function deleteLayer() {
    const layer = A.selectedLayer();
    if (!layer) return;
    const L = S.doc.layers, i = L.indexOf(layer), entry = A.stackEntry('видалення шару');
    L.splice(i, 1);
    A.pushUndo(entry);
    S.selectedId = L.length ? L[Math.min(i, L.length - 1)].id : null;
    A.syncTargetForLayer();
    A.renderLayers();
    A.renderProps();
    A.scheduleRebuild();
    A.setStatus('Шар «' + A.layerTitle(layer) + '» видалено (Ctrl+Z поверне)', 'ok');
  }

  function onLayerClick(e) {
    const li = e.target.closest('li');
    if (!li) return;
    const btn = e.target.closest('[data-act]'), act = btn ? btn.dataset.act : 'select';
    if (act === 'drop-nrm') { A.confirmThen(btn, 'Натисніть ✕ ще раз, щоб прибрати імпортовану NRM', A.dropNrmImport); return; }
    const id = li.dataset.id, layer = id ? A.findLayer(id) : null;
    if (!layer) return;
    if (act === 'eye') {
      layer.visible = !layer.visible;
      A.renderLayers();
      A.scheduleRebuild(layer);
      return;
    }
    if (act === 'mask') {
      A.selectLayer(id);
      A.setTarget('mask');
      if (S.tool === 'picker') A.setTool('brush');
      A.setTab('layer');
      return;
    }
    A.selectLayer(id);
  }

  function bindLayerList() {
    const list = $('layer-list');
    list.addEventListener('click', onLayerClick);
    list.addEventListener('dblclick', e => {
      const li = e.target.closest('li[data-id]');
      if (!li || e.target.closest('[data-act]')) return;
      A.setTab('layer');
      const name = $('layer-name');
      if (name) { name.focus(); name.select(); }
    });
    // Перетягування рядків (миша). На сенсорних екранах — кнопки ↑ ↓.
    let dragId = null;
    const clear = () => { for (const x of list.querySelectorAll('.drop-above, .drop-below')) x.classList.remove('drop-above', 'drop-below'); };
    list.addEventListener('dragstart', e => {
      const li = e.target.closest('li[data-id]');
      if (!li) return;
      dragId = li.dataset.id;
      e.dataTransfer.effectAllowed = 'move';
      try { e.dataTransfer.setData('text/plain', dragId); } catch (err) { /* не критично */ }
      li.classList.add('dragging');
    });
    list.addEventListener('dragover', e => {
      if (!dragId) return;
      const li = e.target.closest('li[data-id]');
      if (!li) return;
      e.preventDefault();
      clear();
      const r = li.getBoundingClientRect();
      li.classList.add(e.clientY < r.top + r.height / 2 ? 'drop-above' : 'drop-below');
    });
    list.addEventListener('drop', e => {
      if (!dragId) return;
      e.preventDefault();
      const li = e.target.closest('li[data-id]'), id = dragId;
      dragId = null;
      clear();
      if (!li || li.dataset.id === id) return;
      const L = S.doc.layers, target = L.findIndex(l => l.id === li.dataset.id), from = L.findIndex(l => l.id === id);
      const r = li.getBoundingClientRect(), above = e.clientY < r.top + r.height / 2;
      // Список перевернутий: «над» рядком = вище в стеку = більший індекс.
      let to = above ? target + 1 : target;
      if (from < to) to--;
      A.moveLayerTo(id, to);
    });
    list.addEventListener('dragend', () => { dragId = null; clear(); for (const x of list.querySelectorAll('.dragging')) x.classList.remove('dragging'); });

    $('layer-opacity').addEventListener('input', e => {
      const layer = A.selectedLayer();
      if (!layer) return;
      layer.opacity = +e.target.value / 100;
      $('layer-opacity-out').textContent = e.target.value + '%';
      const row = list.querySelector('li[data-id="' + layer.id + '"] .lkind');
      if (row) row.textContent = kindText(layer) + (layer.opacity < 1 ? ' · ' + Math.round(layer.opacity * 100) + '%' : '');
      A.scheduleRebuild(layer);
    });
    $('btn-layer-up').addEventListener('click', () => { const l = A.selectedLayer(); if (l) A.moveLayerTo(l.id, S.doc.layers.indexOf(l) + 1); });
    $('btn-layer-down').addEventListener('click', () => { const l = A.selectedLayer(); if (l) A.moveLayerTo(l.id, S.doc.layers.indexOf(l) - 1); });
    $('btn-layer-dup').addEventListener('click', duplicateLayer);
    $('btn-layer-del').addEventListener('click', e => {
      const l = A.selectedLayer();
      if (l) A.confirmThen(e.currentTarget, 'Натисніть «Видалити» ще раз, щоб видалити шар «' + A.layerTitle(l) + '»', deleteLayer);
    });

    $('btn-add-paint').addEventListener('click', addPaint);
    $('btn-add-fill').addEventListener('click', addFill);
    const gen = $('gen-select');
    gen.append(el('option', { value: '', text: '+ Генератор…' }));
    for (const c of generators.catalog) if (c.type !== 'image') gen.append(el('option', { value: c.type, text: c.label }));
    gen.addEventListener('change', () => { const t = gen.value; gen.value = ''; if (t) addGenerator(t); });
    const smart = $('smart-select');
    smart.append(el('option', { value: '', text: '+ Розумний матеріал…' }));
    for (const s of materials.smart) smart.append(el('option', { value: s.id, text: s.label, title: s.description }));
    smart.addEventListener('change', () => { const id = smart.value; smart.value = ''; if (id) addSmart(id); });
    $('btn-add-image').addEventListener('click', () => $('image-input').click());
  }

  // ---------- Властивості шару ----------

  function section(title) { return el('h3', { text: title }); }

  // Зміна буферів вибраного шару (очищення, заливка маски) як один крок undo.
  function editBuffers(layer, target, label, fn) {
    A.flushRebuild();
    const snap = layers.snapshot(layer, target);
    try { fn(); } catch (e) { layers.restore(layer, snap); A.setStatus(A.errText(e), 'error'); return; }
    A.pushUndo({ type: 'buf', layerId: layer.id, snap, label });
    A.markDirty();
    if (A.validBelow(layer)) { A.compositeActive(layer); A.mapsChanged(true); A.schedulePreview(0); } else A.rebuildNow();
  }

  function setMask(layer, mask, label) {
    const entry = { type: 'mask', layerId: layer.id, mask: layer.mask, label };
    layer.mask = mask;
    A.pushUndo(entry);
    A.renderLayers();
    A.renderProps();
    A.scheduleRebuild(layer);
  }

  function maskSection(layer) {
    const box = el('div', { class: 'mask-section' }, [section('Маска')]);
    const size = S.doc.size, m = layer.mask;
    if (!m) {
      const white = el('button', { type: 'button', id: 'btn-mask-white', text: 'Додати білу', title: 'Шар видно всюди; гумкою на масці ховайте частини' });
      const black = el('button', { type: 'button', id: 'btn-mask-black', text: 'Додати чорну', title: 'Шар сховано; пензлем на масці відкривайте потрібне' });
      const add = fill => {
        const tmp = { mask: null };
        layers.addMask(tmp, size, fill);
        setMask(layer, tmp.mask, 'додавання маски');
        A.setTarget('mask');
        if (S.tool === 'picker' || S.tool === 'fill') A.setTool('brush');
        A.setStatus(fill === 'black' ? 'Додано чорну маску: малюйте пензлем, щоб показати шар (гумка ховає)' : 'Додано білу маску: гумкою ховайте частини шару', 'ok');
      };
      white.addEventListener('click', () => add('white'));
      black.addEventListener('click', () => add('black'));
      box.append(el('div', { class: 'row' }, [white, black]),
        el('p', { class: 'hint', text: 'Маска обмежує шар: біле — видно, чорне — ні. Її можна малювати пензлем і доповнити генератором (зношені краї, западини, гранж…).' }));
      return box;
    }
    box.append(el('div', { class: 'row' }, [
      checkField({ id: 'mask-enabled', label: 'Увімкнена', checked: m.enabled !== false, onChange: v => { m.enabled = v; A.renderLayers(); A.scheduleRebuild(layer); } }),
      checkField({ id: 'mask-invert', label: 'Інвертувати', checked: !!m.invert, onChange: v => { m.invert = v; A.renderLayers(); A.scheduleRebuild(layer); } })
    ]));
    const paintBtn = el('button', { type: 'button', id: 'btn-mask-paint', class: S.target === 'mask' ? 'active' : '', text: 'Малювати маску', title: 'Ціль пензля — ця маска (M)' });
    paintBtn.addEventListener('click', () => { A.setTarget(S.target === 'mask' ? 'paint' : 'mask'); if (S.tool === 'picker') A.setTool('brush'); A.renderProps(); });
    const full = new Uint8Array(size * size).fill(255);
    const fillW = el('button', { type: 'button', id: 'btn-mask-fill-white', text: 'Залити білим', title: 'Намальована частина маски — уся біла' });
    const fillB = el('button', { type: 'button', id: 'btn-mask-fill-black', text: 'Залити чорним', title: 'Намальована частина маски — уся чорна' });
    fillW.addEventListener('click', () => editBuffers(layer, 'mask', 'заливка маски', () => layers.fillRegion(S.doc, layer, 'mask', full, {})));
    fillB.addEventListener('click', () => editBuffers(layer, 'mask', 'заливка маски', () => layers.fillRegion(S.doc, layer, 'mask', full, { erase: true })));
    const remove = el('button', { type: 'button', id: 'btn-mask-remove', class: 'danger', text: 'Прибрати', title: 'Прибрати маску (Ctrl+Z поверне)' });
    remove.addEventListener('click', () => {
      setMask(layer, null, 'прибирання маски');
      A.syncTargetForLayer();
      A.setStatus('Маску прибрано (Ctrl+Z поверне)', 'ok');
    });
    box.append(el('div', { class: 'row wrap' }, [paintBtn, fillW, fillB, remove]));

    const opts = [{ value: '', label: 'без генератора' }].concat(maskgen.catalog.map(c => ({ value: c.type, label: c.label })));
    box.append(selectField({
      id: 'mask-gen-type', label: 'Генератор маски', options: opts, value: m.gen ? m.gen.type : '',
      onChange: v => { m.gen = v ? maskgen.defaultGen(v) : null; A.renderLayers(); A.renderProps(); A.scheduleRebuild(layer); }
    }));
    if (m.gen) {
      const def = maskgen.catalog.find(c => c.type === m.gen.type);
      if (def) {
        if (!m.gen.params || typeof m.gen.params !== 'object') m.gen.params = {};
        const rebuild = () => A.scheduleRebuild(layer);
        box.append(paramForm(def, m.gen.params, 'mask-', rebuild, null));
        if (MASK_SEEDED[m.gen.type]) box.append(seedField('mask-seed', m.gen, rebuild));
      } else box.append(el('p', { class: 'hint warn', text: 'Невідомий генератор маски: ' + m.gen.type }));
      box.append(el('p', { class: 'hint', text: 'Генератор рахується з шарів під цим і множиться на намальоване на масці.' }));
    }
    return box;
  }

  function channelChips(layer) {
    const row = el('div', { class: 'chips' });
    for (const c of layers.CHANNELS) {
      row.append(checkField({
        id: 'layer-ch-' + c, label: C.CH_LABELS[c], checked: !!layer.channels[c],
        title: 'Шар змінює канал «' + C.CH_LABELS[c] + '»',
        onChange: v => { layer.channels[c] = v; A.scheduleRebuild(layer); }
      }));
    }
    return row;
  }

  A.renderProps = function () {
    const box = $('layer-props'), layer = A.selectedLayer();
    box.textContent = '';
    syncLayerFoot();
    if (!layer) { box.append(el('p', { class: 'empty', text: 'Виберіть шар у списку ліворуч.' })); return; }
    box.append(el('div', { class: 'props-kind', text: kindText(layer) + (A.layerGroup(layer) ? ' · ' + A.layerGroup(layer) : '') }));
    const name = el('input', { type: 'text', id: 'layer-name', maxlength: 80, spellcheck: 'false', autocomplete: 'off' });
    name.value = A.layerTitle(layer);
    name.addEventListener('input', () => {
      const g = A.layerGroup(layer);
      layer.name = g ? g + C.GROUP_SEP + name.value : name.value;
      A.markDirty();
      const t = $('layer-list').querySelector('li[data-id="' + layer.id + '"] .ltitle');
      if (t) t.textContent = name.value || '(без назви)';
    });
    box.append(field('Назва шару', name, 'layer-name'));

    if (layer.kind === 'fill') {
      box.append(section('Матеріал заливки'));
      const binding = {
        mat: layer.material, ch: layer.channels,
        preset: layer.material.id || '',
        onChange() { if (binding.preset) layer.material.id = binding.preset; else delete layer.material.id; A.scheduleRebuild(layer); }
      };
      const ed = materialEditor(binding, 'fill-material-select', 'fill-mat');
      const take = el('button', { type: 'button', id: 'btn-fill-from-brush', text: 'Взяти з пензля', title: 'Скопіювати матеріал і канали пензля в цю заливку' });
      take.addEventListener('click', () => {
        Object.assign(layer.material, { color: S.material.color.slice(0, 3), alpha: S.material.alpha, height: S.material.height, spec: S.material.spec, team: S.material.team });
        for (const c of layers.CHANNELS) layer.channels[c] = !!S.matChannels[c];
        binding.preset = S.matPreset; binding.onChange();
        ed.sync();
      });
      box.append(ed, take);
    } else {
      box.append(section('Канали шару'), channelChips(layer));
    }
    if (layer.kind === 'fill' || layer.kind === 'paint') {
      box.append(selectField({
        id: 'layer-height-mode', label: 'Режим висоти', value: layer.heightMode === 'replace' ? 'replace' : 'add',
        options: [{ value: 'add', label: 'Додавати (рельєф поверх)' }, { value: 'replace', label: 'Замінювати' }],
        onChange: v => { layer.heightMode = v; A.scheduleRebuild(layer); }
      }));
    }
    if (layer.kind === 'paint') {
      const clear = el('button', { type: 'button', id: 'btn-layer-clear', text: 'Очистити шар', title: 'Стерти всі мазки шару (Ctrl+Z поверне)' });
      clear.addEventListener('click', () => editBuffers(layer, 'paint', 'очищення шару', () => { layer.paint = {}; }));
      box.append(el('p', { class: 'hint', text: 'Малюйте пензлем (B), гумкою (E) чи заливкою (G). Що записує пензель, задає матеріал пензля на вкладці «Пензель».' }), clear);
    }
    if (layer.kind === 'proc') {
      const def = generators.catalog.find(c => c.type === layer.proc.type);
      box.append(section('Параметри генератора'));
      if (!def) box.append(el('p', { class: 'hint warn', text: 'Невідомий генератор: ' + layer.proc.type }));
      else {
        const params = layer.proc.params;
        box.append(paramForm(def, params, 'gen-', (p, v) => {
          // Пресет фракції показує свої кольори; власний колір означає пресет «Свої кольори».
          if (def.type === 'base' && p.key === 'preset' && generators.FACTION_PRESETS[v]) {
            params.color1 = generators.FACTION_PRESETS[v][0]; params.color2 = generators.FACTION_PRESETS[v][1];
            A.scheduleRebuild(layer); A.renderProps(); return;
          }
          if (def.type === 'base' && p.kind === 'color' && params.preset !== 'custom') {
            params.preset = 'custom';
            const sel = $('gen-preset');
            if (sel) sel.value = 'custom';
          }
          A.scheduleRebuild(layer);
        }, PARAM_VISIBLE[def.type]));
        if (SEEDED[def.type]) box.append(seedField('gen-seed', layer.proc, () => A.scheduleRebuild(layer)));
        const reset = el('button', { type: 'button', id: 'btn-gen-reset', text: 'Типові значення' });
        reset.addEventListener('click', () => {
          const keep = def.type === 'image' ? { imageId: params.imageId, target: params.target, channel: params.channel } : {};
          layer.proc.params = Object.assign(generators.defaultLayer(def.type, 0).params, keep);
          A.renderProps(); A.scheduleRebuild(layer);
        });
        box.append(reset);
      }
    }
    box.append(maskSection(layer));
  };

  A.initPanels = function () {
    bindToolbar();
    bindBrushPanel();
    bindLayerList();
    $('side-tabs').addEventListener('click', e => { const b = e.target.closest('[data-tab]'); if (b) A.setTab(b.dataset.tab); });
    A.setTab('brush');
  };
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
