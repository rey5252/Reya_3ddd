(function (RA3) { 'use strict';
  // Зв'язок інтерфейсу з ядром. Єдиний модуль, що працює з DOM (окрім preview3d.js).
  // Розділи: константи · DOM · стан · статус · перебудова · полотно · пензель і undo · шари · параметри шару ·
  // документ · 3D-прев'ю · імпорт · експорт · проєкт · клавіатура й перетягування · запуск.
  const util = RA3.util || require('./util.js');
  const dds = RA3.dds || require('./dds.js');
  const tga = RA3.tga || require('./tga.js');
  const maps = RA3.maps || require('./maps.js');
  const generators = RA3.generators || require('./generators.js');
  const project = RA3.project || require('./project.js');

  // ---------- Константи ----------

  const SIZES = [256, 512, 1024, 2048];
  const REBUILD_DELAY = 60;                    // мс debounce після зміни параметра
  const UNDO_MAX = 30;                         // не більше 30 знімків
  const UNDO_BYTES = 256 * 1024 * 1024;        // і не більше 256 МБ на всю історію
  const DOWNLOAD_GAP = 250;                    // мс між завантаженнями
  const DEFAULT_TEAM = 1;                      // Red

  const VIEW_INFO = {
    color: '<b>Колір</b> (DiffuseTexture): фарба скіна без освітлення.',
    height: '<b>Висота</b>: світле — опукле, темне — заглиблене. З неї будується карта нормалей.',
    nrm: '<b>NRM</b> (NormalMap): рельєф для освітлення. Силу задає «Сила рельєфу» в налаштуваннях документа.',
    spm: '<b>SPM</b> (SpecMap): червоний канал — блиск, синій — маска кольору гравця.',
    alpha: '<b>Прозорість</b>: пікселі, темніші за 96 із 255, гра не малює (alpha-test).',
    game: '<b>У грі</b>: скін з кольором гравця й освітленням, як його показує шейдер RA3.'
  };
  // Вкладки, на яких видно мазок по цілі (перша — куди перемкнути, якщо поточна його не показує).
  const VIEWS_FOR_TARGET = { color: ['color', 'game'], alpha: ['alpha', 'game'], height: ['height', 'nrm', 'game'], spec: ['spm', 'game'], team: ['game', 'spm'] };
  const BRUSH_VALUE = {
    alpha: { label: 'Непрозорість', hint: '0 — дірка (гра не малює), 255 — суцільне', def: 0 },
    height: { label: 'Висота', hint: '128 — рівно, більше — опукло, менше — заглиблення', def: 190 },
    spec: { label: 'Блиск', hint: '0 — матове, 255 — дзеркальне', def: 220 },
    team: { label: 'Колір гравця', hint: '0 — без кольору гравця, 255 — повний колір гравця', def: 255 }
  };
  const TEAM_NAMES = { Gold: 'Золотий', Red: 'Червоний', Blue: 'Синій', Green: 'Зелений', Orange: 'Помаранчевий', SkyBlue: 'Блакитний', Purple: 'Фіолетовий', Pink: 'Рожевий' };
  // Шари, чий візерунок залежить від seed.
  const SEEDED = { base: 1, camo: 1, panels: 1, rivets: 1, dirt: 1, rust: 1, scratches: 1 };
  // Параметри, що діють лише в певних режимах (інші ховаються, щоб не плутати).
  const notLuma = p => p.mode !== 'luma', stripes = p => p.mode === 'stripes';
  const PARAM_VISIBLE = {
    team: { x: notLuma, y: notLuma, w: notLuma, h: notLuma, angle: stripes, count: stripes, lumaMin: p => p.mode === 'luma' },
    image: { channel: p => p.target !== 'color' }
  };
  const ROLE_LABEL = { diffuse: 'колір + прозорість', spm: 'блиск + колір гравця', nrm: 'нормалі' };
  const ROLE_ORDER = { diffuse: 0, spm: 1, nrm: 2 };

  const ICONS = {
    plus: '<path d="M12 5v14M5 12h14"/>',
    up: '<path d="m6 15 6-6 6 6"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    close: '<path d="M7 7l10 10M17 7 7 17"/>',
    undo: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>',
    redo: '<path d="m15 14 5-5-5-5"/><path d="M20 9H9.5a5.5 5.5 0 0 0 0 11H13"/>',
    download: '<path d="M12 4v11m-5-5 5 5 5-5M5 20h14"/>',
    upload: '<path d="M12 20V9m-5 5 5-5 5 5M5 4h14"/>',
    image: '<rect x="3.5" y="5" width="17" height="14" rx="1.5"/><path d="m4 17 5-5 4 4 2.5-2.5L20 18"/><circle cx="15.5" cy="9.5" r="1.5"/>',
    save: '<path d="M5 4h11l3 3v13H5z"/><path d="M8 4v5h7V4M8 20v-6h8v6"/>',
    folder: '<path d="M3 6.5h6l2 2h10V19H3z"/>',
    file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4M12 11v6M9 14h6"/>',
    dice: '<rect x="4" y="4" width="16" height="16" rx="3"/><g fill="currentColor" stroke="none"><circle cx="8.5" cy="8.5" r="1.4"/><circle cx="15.5" cy="15.5" r="1.4"/><circle cx="15.5" cy="8.5" r="1.4"/><circle cx="8.5" cy="15.5" r="1.4"/><circle cx="12" cy="12" r="1.4"/></g>',
    reset: '<path d="M4 12a8 8 0 1 0 2.4-5.7"/><path d="M4 4v4h4"/>'
  };

  // ---------- DOM ----------

  const $ = id => document.getElementById(id);
  const PROPS = { value: 1, checked: 1, disabled: 1, hidden: 1, selected: 1 };

  function el(tag, props) {
    const e = document.createElement(tag);
    for (const k of Object.keys(props || {})) {
      const v = props[k];
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k === 'dataset') Object.assign(e.dataset, v);
      else if (PROPS[k]) e[k] = v;
      else e.setAttribute(k, v);
    }
    return e;
  }
  function iconSvg(name) { return '<svg class="ic" viewBox="0 0 24 24" aria-hidden="true">' + ICONS[name] + '</svg>'; }
  function iconButton(name, title, act, disabled) {
    const b = el('button', { type: 'button', class: 'btn icon', title, 'aria-label': title, dataset: { act }, disabled: !!disabled });
    b.innerHTML = iconSvg(name);
    return b;
  }
  function errText(e) { return (e && e.message) || String(e); }
  function sentence(text) { return /[.!?…]$/.test(text) ? text : text + '.'; }
  function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
  function isObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

  // Заливка доріжки повзунка до поточного значення (для WebKit; Firefox малює її сам).
  function fillRange(input) {
    const min = +input.min, max = +input.max, t = max > min ? (+input.value - min) / (max - min) : 0;
    input.style.setProperty('--fill', (t * 100).toFixed(1) + '%');
  }
  function rangeOutput(input) { const f = input.closest('.field'); return f && f.querySelector('output'); }

  // ---------- Стан ----------

  const S = {
    doc: null,
    layerCache: null,       // карти після останнього ввімкненого шару, до мазків: {size, color, height, spec, team}
    version: 0,             // зростає після кожної зміни карт чи налаштувань, що впливають на вигляд
    painted: '',            // ключ останнього намальованого вигляду
    selectedId: null,
    view: 'game',
    team: DEFAULT_TEAM,
    undo: [], redo: [],
    stroke: null,
    brushValues: {},
    nrmImportName: '',
    dirty: false,
    timers: { rebuild: 0, preview: 0 },
    raf: { paint: 0, stroke: 0 }
  };
  for (const t of Object.keys(BRUSH_VALUE)) S.brushValues[t] = BRUSH_VALUE[t].def;
  let preview = null;

  function markDirty() { S.dirty = true; }

  // ---------- Статус ----------

  // kind: '' | 'ok' | 'busy' | 'error'
  function setStatus(text, kind) {
    const s = $('status');
    s.textContent = text;
    s.className = kind || '';
  }

  // ---------- Перебудова ----------

  // Повна перебудова doc (шари + мазки). Реєстр знімає копію карт одразу після останнього ввімкненого шару,
  // тож під час мазка перекомпоновуються лише оверлеї, без повторного запуску генераторів.
  function rebuildLayers(doc, reuse) {
    let last = null;
    for (const l of doc.layers) if (l.enabled) last = l;
    let cache = null;
    maps.rebuild(doc, {
      apply(layer, m, size, images) {
        generators.apply(layer, m, size, images);
        if (layer === last) cache = copyMaps(m, size, reuse);
      }
    });
    return cache;
  }

  function copyMaps(m, size, reuse) {
    if (reuse && reuse.size === size) {
      reuse.color.set(m.color); reuse.height.set(m.height); reuse.spec.set(m.spec); reuse.team.set(m.team);
      return reuse;
    }
    return { size, color: m.color.slice(), height: m.height.slice(), spec: m.spec.slice(), team: m.team.slice() };
  }

  // Кеш шарів + оверлеї пензля через той самий maps.rebuild (один «шар», що копіює кеш).
  const CACHED_LAYER = { enabled: true };
  function composite() {
    const c = S.layerCache;
    maps.rebuild(Object.assign({}, S.doc, { layers: c ? [CACHED_LAYER] : [] }), {
      apply(layer, m) { m.color.set(c.color); m.height.set(c.height); m.spec.set(c.spec); m.team.set(c.team); }
    });
    changed();
  }

  function changed() { S.version++; requestPaint(); }

  function rebuildNow() {
    clearTimeout(S.timers.rebuild); S.timers.rebuild = 0;
    let ok = true;
    try { S.layerCache = rebuildLayers(S.doc, S.layerCache); }
    catch (e) { ok = false; setStatus('Не вдалося перебудувати текстуру: ' + errText(e), 'error'); }
    changed();
    schedulePreview();
    if (ok) setStatus('Готово', 'ok');
    return ok;
  }

  function scheduleRebuild() {
    markDirty();
    clearTimeout(S.timers.rebuild);
    S.timers.rebuild = setTimeout(() => {
      if (S.doc.size < 1024) { rebuildNow(); return; }
      setStatus('Перебудова…', 'busy');           // на великих текстурах перебудова помітна: даємо статусу намалюватися
      S.timers.rebuild = setTimeout(rebuildNow, 30);
    }, REBUILD_DELAY);
  }

  function flushRebuild() { if (S.timers.rebuild) rebuildNow(); }

  // ---------- Полотно ----------

  function requestPaint() { if (!S.raf.paint) S.raf.paint = requestAnimationFrame(paintNow); }

  // Вигляд перераховується лише після зміни карт, вкладки чи кольору гравця (для «У грі»), не на рух миші.
  function paintNow() {
    if (S.raf.paint) { cancelAnimationFrame(S.raf.paint); S.raf.paint = 0; }
    const d = S.doc, canvas = $('canvas-main');
    const key = S.version + ':' + S.view + ':' + (S.view === 'game' ? S.team : '');
    if (key === S.painted) return;
    let rgba;
    try { rgba = maps.viewRGBA(d, S.view, maps.TEAM_COLORS[S.team].rgb); }
    catch (e) { setStatus('Не вдалося показати вигляд: ' + errText(e), 'error'); return; }
    if (canvas.width !== d.size || canvas.height !== d.size) { canvas.width = d.size; canvas.height = d.size; }
    canvas.getContext('2d').putImageData(new ImageData(rgba, d.size, d.size), 0, 0);
    S.painted = key;
    canvas.dataset.view = S.view;
  }

  function setView(view) {
    S.view = view;
    for (const b of document.querySelectorAll('#view-tabs button[data-view]')) {
      const on = b.dataset.view === view;
      b.classList.toggle('active', on);
      b.setAttribute('aria-selected', on ? 'true' : 'false');
    }
    $('view-caption').innerHTML = VIEW_INFO[view];
    requestPaint();
  }

  // Полотно — квадрат, вписаний у сцену; на вузькому екрані — на всю ширину.
  function fitCanvas() {
    const stage = $('stage'), cs = getComputedStyle(stage);
    const w = stage.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
    const h = stage.clientHeight - parseFloat(cs.paddingTop) - parseFloat(cs.paddingBottom);
    // На вузькому екрані сцена росте за вмістом: обмежуємо полотно висотою вікна, щоб малювати без прокрутки.
    const narrow = window.matchMedia('(max-width: 899px)').matches;
    const side = Math.max(96, Math.floor(narrow ? Math.min(w, Math.max(240, window.innerHeight - 200)) : Math.min(w, h)));
    const frame = $('canvas-frame');
    if (frame.style.width !== side + 'px') { frame.style.width = side + 'px'; frame.style.height = side + 'px'; }
  }

  // ---------- Пензель і undo ----------

  function brushSettings() {
    const target = $('brush-target').value, erase = $('brush-erase').checked;
    const opts = { radius: +$('brush-size').value / 2, hardness: +$('brush-hardness').value / 100, strength: +$('brush-strength').value / 100, erase };
    if (!erase) opts.value = target === 'color' ? util.hexToRgb($('brush-color').value) : +$('brush-value').value;
    return { target, opts };
  }

  // Позиція в текселях (дробова); мазок ставиться в Math.floor, бо піксель (x, y) лежить у точці (x, y).
  function texelAt(e) {
    const r = $('canvas-main').getBoundingClientRect(), n = S.doc.size;
    return { x: (e.clientX - r.left) / r.width * n, y: (e.clientY - r.top) / r.height * n };
  }

  function dab(p) { maps.applyBrush(S.doc, S.stroke.target, Math.floor(p.x), Math.floor(p.y), S.stroke.opts); }

  // Відбитки з кроком ≈ радіус/4 між подіями миші, тож швидкий мазок суцільний.
  function strokeTo(p) {
    const s = S.stroke, dx = p.x - s.last.x, dy = p.y - s.last.y, dist = Math.sqrt(dx * dx + dy * dy);
    if (dist < s.spacing) return;
    const ux = dx / dist, uy = dy / dist;
    let t = s.spacing;
    for (; t <= dist; t += s.spacing) dab({ x: s.last.x + ux * t, y: s.last.y + uy * t });
    t -= s.spacing;
    s.last = { x: s.last.x + ux * t, y: s.last.y + uy * t };
  }

  function strokeFrame() {
    S.raf.stroke = 0;
    composite();
    paintNow();
  }

  function onPointerDown(e) {
    if ((e.pointerType === 'mouse' && e.button !== 0) || S.stroke) return;
    e.preventDefault();
    let b;
    try { b = brushSettings(); } catch (err) { setStatus(errText(err), 'error'); return; }
    pushUndo(maps.cloneOverlay(S.doc, b.target));
    S.stroke = { id: e.pointerId, target: b.target, opts: b.opts, last: texelAt(e), spacing: Math.max(1, b.opts.radius / 4) };
    try { $('canvas-main').setPointerCapture(e.pointerId); } catch (err) { /* не критично */ }
    dab(S.stroke.last);
    markDirty();
    if (!S.raf.stroke) S.raf.stroke = requestAnimationFrame(strokeFrame);
  }

  function onPointerMove(e) {
    moveCursor(e);
    const s = S.stroke;
    if (!s || e.pointerId !== s.id) return;
    const list = typeof e.getCoalescedEvents === 'function' ? e.getCoalescedEvents() : [];
    for (const ce of (list.length ? list : [e])) strokeTo(texelAt(ce));
    if (!S.raf.stroke) S.raf.stroke = requestAnimationFrame(strokeFrame);
  }

  function endStroke(e) {
    const s = S.stroke;
    if (!s || (e && e.pointerId !== s.id)) return;
    S.stroke = null;
    if (S.raf.stroke) { cancelAnimationFrame(S.raf.stroke); S.raf.stroke = 0; }
    composite();
    paintNow();
    schedulePreview(0);
    updateHistoryButtons();
  }

  function moveCursor(e) {
    const cur = $('brush-cursor');
    if (e.pointerType === 'touch') { cur.hidden = true; return; }
    const r = $('canvas-frame').getBoundingClientRect(), n = S.doc.size;
    const d = Math.max(4, +$('brush-size').value * r.width / n);
    cur.style.width = d + 'px'; cur.style.height = d + 'px';
    cur.style.transform = 'translate(' + (e.clientX - r.left - d / 2) + 'px,' + (e.clientY - r.top - d / 2) + 'px)';
    cur.classList.toggle('erase', $('brush-erase').checked);
    cur.hidden = false;
    const t = texelAt(e), x = Math.floor(t.x), y = Math.floor(t.y);
    $('cursor-pos').textContent = x >= 0 && y >= 0 && x < n && y < n ? 'X ' + x + ' · Y ' + y + ' · ' + n + '×' + n : '';
  }

  const SHORTCUTS = 'Ctrl+Z — скасувати мазок · [ ] — розмір пензля';
  function hideCursor() { $('brush-cursor').hidden = true; $('cursor-pos').textContent = SHORTCUTS; }

  function snapBytes(s) { return s.value.length + s.cov.length; }
  function historyBytes() {
    let b = 0;
    for (const s of S.undo) b += snapBytes(s);
    for (const s of S.redo) b += snapBytes(s);
    return b;
  }
  // Не більше min(30, 256 МБ / розмір знімка): на 2048² це ≈12 знімків кольору.
  function trimHistory() {
    while (S.undo.length > UNDO_MAX || (S.undo.length > 1 && historyBytes() > UNDO_BYTES)) S.undo.shift();
    while (S.redo.length > UNDO_MAX || (S.redo.length > 0 && historyBytes() > UNDO_BYTES)) S.redo.shift();
  }

  function pushUndo(snap) {
    S.undo.push(snap);
    S.redo.length = 0;
    trimHistory();
    updateHistoryButtons();
  }

  function clearHistory() { S.undo.length = 0; S.redo.length = 0; updateHistoryButtons(); }

  function stepHistory(from, to, message) {
    if (S.stroke || !from.length) return;
    const snap = from.pop();
    to.push(maps.cloneOverlay(S.doc, snap.target));
    maps.restoreOverlay(S.doc, snap);
    trimHistory();
    composite();
    schedulePreview(0);
    updateHistoryButtons();
    markDirty();
    setStatus(message, 'ok');
  }
  function undo() { stepHistory(S.undo, S.redo, 'Мазок скасовано'); }
  function redo() { stepHistory(S.redo, S.undo, 'Мазок повернуто'); }

  function updateHistoryButtons() {
    $('btn-undo').disabled = !S.undo.length;
    $('btn-redo').disabled = !S.redo.length;
  }

  // Ціль пензля: показати потрібне поле значення й вкладку, на якій мазок видно.
  function onBrushTarget() {
    const target = $('brush-target').value, isColor = target === 'color';
    $('field-brush-color').hidden = !isColor;
    $('field-brush-value').hidden = isColor;
    if (!isColor) {
      const v = $('brush-value');
      $('brush-value-label').textContent = BRUSH_VALUE[target].label;
      $('field-brush-value').title = BRUSH_VALUE[target].hint;
      v.value = S.brushValues[target];
      syncRange(v);
    }
    const views = VIEWS_FOR_TARGET[target];
    if (views.indexOf(S.view) < 0) setView(views[0]);
  }

  function syncRange(input) {
    fillRange(input);
    const out = rangeOutput(input);
    if (!out) return;
    const v = +input.value;
    switch (input.id) {
      case 'brush-size': out.textContent = v + ' px'; break;
      case 'brush-hardness': case 'brush-strength': out.textContent = v + '%'; break;
      case 'nrm-strength': out.textContent = v.toFixed(1); break;
      default: out.textContent = String(v);
    }
  }

  function bindBrush() {
    const canvas = $('canvas-main');
    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerup', endStroke);
    canvas.addEventListener('pointercancel', endStroke);
    canvas.addEventListener('lostpointercapture', endStroke);
    canvas.addEventListener('pointerleave', hideCursor);
    canvas.addEventListener('contextmenu', e => e.preventDefault());
    for (const id of ['brush-size', 'brush-hardness', 'brush-strength', 'brush-value']) {
      const input = $(id);
      syncRange(input);
      input.addEventListener('input', () => {
        syncRange(input);
        if (id === 'brush-value') S.brushValues[$('brush-target').value] = +input.value;
      });
    }
    const color = $('brush-color'), hex = $('brush-color-hex');
    const syncColor = () => { hex.textContent = color.value.toUpperCase(); };
    color.addEventListener('input', syncColor);
    syncColor();
    $('brush-target').addEventListener('change', onBrushTarget);
    $('btn-undo').addEventListener('click', undo);
    $('btn-redo').addEventListener('click', redo);
    onBrushTarget();
    updateHistoryButtons();
  }

  function nudgeBrushSize(dir) {
    const input = $('brush-size'), v = +input.value;
    input.value = Math.round(dir > 0 ? Math.max(v + 1, v * 1.2) : Math.min(v - 1, v / 1.2));
    syncRange(input);
  }

  // ---------- Шари ----------

  function layerDef(type) {
    for (const c of generators.catalog) if (c.type === type) return c;
    return null;
  }
  function findLayer(id) { return S.doc.layers.find(l => l.id === id) || null; }
  function selectedLayer() { return S.selectedId ? findLayer(S.selectedId) : null; }

  function renderLayers() {
    const list = $('layer-list'), layers = S.doc.layers;
    list.textContent = '';
    if (!layers.length && !S.doc.nrmImport) {
      list.append(el('li', { class: 'empty', text: 'Шарів ще немає. Додайте шар нижче або перетягніть зображення на сторінку.' }));
    }
    layers.forEach((layer, i) => {
      const def = layerDef(layer.type), typeLabel = def ? def.label : layer.type;
      const li = el('li', {
        class: 'layer' + (layer.id === S.selectedId ? ' selected' : '') + (layer.enabled ? '' : ' off'),
        dataset: { id: layer.id, type: layer.type }
      });
      const cb = el('input', { type: 'checkbox', checked: !!layer.enabled, title: 'Увімкнути або вимкнути шар', 'aria-label': 'Увімкнути шар «' + layer.name + '»', dataset: { act: 'toggle' } });
      const name = el('button', { type: 'button', class: 'layer-name', dataset: { act: 'select' }, 'aria-pressed': layer.id === S.selectedId ? 'true' : 'false' });
      name.append(el('span', { class: 'layer-title', text: layer.name || typeLabel }));
      if (layer.name !== typeLabel) name.append(el('span', { class: 'layer-type', text: typeLabel }));
      const tools = el('div', { class: 'layer-tools' });
      tools.append(
        iconButton('up', 'Вище (раніше в порядку)', 'up', i === 0),
        iconButton('down', 'Нижче (пізніше в порядку)', 'down', i === layers.length - 1),
        iconButton('close', 'Видалити шар', 'delete'));
      li.append(cb, name, tools);
      list.append(li);
    });
    if (S.doc.nrmImport) {
      const li = el('li', { class: 'layer pseudo', dataset: { type: 'nrm' } });
      const name = el('div', { class: 'layer-name' });
      name.append(el('span', { class: 'layer-title', text: S.nrmImportName || 'Імпортована NRM' }),
        el('span', { class: 'layer-type', text: 'Карта нормалей, змішується з рельєфом шарів' }));
      const tools = el('div', { class: 'layer-tools' });
      tools.append(iconButton('close', 'Прибрати імпортовану NRM', 'drop-nrm'));
      li.append(el('span'), name, tools);
      list.append(li);
    }
  }

  // Видалення з підтвердженням другим кліком (без модальних вікон).
  function confirmThen(btn, message, fn) {
    if (btn.classList.contains('confirm')) { fn(); return; }
    const title = btn.title;
    btn.classList.add('confirm');
    btn.title = 'Натисніть ще раз, щоб підтвердити';
    setStatus(message, '');
    setTimeout(() => { btn.classList.remove('confirm'); btn.title = title; }, 2500);
  }

  function onLayerClick(e) {
    const li = e.target.closest('li');
    if (!li) return;
    const btn = e.target.closest('[data-act]'), id = li.dataset.id, act = btn ? btn.dataset.act : 'select';
    if (act === 'toggle') return;
    if (act === 'drop-nrm') { confirmThen(btn, 'Натисніть ✕ ще раз, щоб прибрати імпортовану NRM', dropNrmImport); return; }
    if (!id) return;
    if (act === 'select') selectLayer(id);
    else if (act === 'up' || act === 'down') moveLayer(id, act === 'up' ? -1 : 1);
    else if (act === 'delete') {
      const layer = findLayer(id);
      confirmThen(btn, 'Натисніть ✕ ще раз, щоб видалити шар «' + (layer ? layer.name : '') + '»', () => deleteLayer(id));
    }
  }

  function onLayerToggle(e) {
    if (!e.target.matches('[data-act="toggle"]')) return;
    const li = e.target.closest('li'), layer = findLayer(li.dataset.id);
    if (!layer) return;
    layer.enabled = e.target.checked;
    li.classList.toggle('off', !layer.enabled);
    scheduleRebuild();
  }

  function selectLayer(id) {
    S.selectedId = id;
    for (const li of $('layer-list').querySelectorAll('li[data-id]')) {
      const on = li.dataset.id === id;
      li.classList.toggle('selected', on);
      const name = li.querySelector('.layer-name');
      if (name) name.setAttribute('aria-pressed', on ? 'true' : 'false');
    }
    renderParams();
  }

  function moveLayer(id, dir) {
    const layers = S.doc.layers, i = layers.findIndex(l => l.id === id), j = i + dir;
    if (i < 0 || j < 0 || j >= layers.length) return;
    const t = layers[i]; layers[i] = layers[j]; layers[j] = t;
    renderLayers();
    const btn = $('layer-list').querySelector('li[data-id="' + id + '"] [data-act="' + (dir < 0 ? 'up' : 'down') + '"]');
    if (btn && !btn.disabled) btn.focus();
    scheduleRebuild();
  }

  function deleteLayer(id) {
    const layers = S.doc.layers, i = layers.findIndex(l => l.id === id);
    if (i < 0) return;
    const [removed] = layers.splice(i, 1);
    // Зображення без жодного шару більше не потрібне ні в пам'яті, ні у файлі проєкту.
    const img = removed.type === 'image' && removed.params ? removed.params.imageId : null;
    if (img && !layers.some(l => l.type === 'image' && l.params && l.params.imageId === img)) delete S.doc.images[img];
    if (S.selectedId === id) S.selectedId = layers.length ? layers[Math.min(i, layers.length - 1)].id : null;
    renderLayers();
    renderParams();
    scheduleRebuild();
  }

  function addLayer() {
    const type = $('add-layer-type').value, layers = S.doc.layers;
    const layer = generators.defaultLayer(type);
    const at = layers.findIndex(l => l.id === S.selectedId);
    layers.splice(at < 0 ? layers.length : at + 1, 0, layer);
    S.selectedId = layer.id;
    renderLayers();
    renderParams();
    const li = $('layer-list').querySelector('li[data-id="' + layer.id + '"]');
    if (li) li.scrollIntoView({ block: 'nearest' });
    scheduleRebuild();
  }

  function dropNrmImport() {
    S.doc.nrmImport = null;
    S.nrmImportName = '';
    renderLayers();
    markDirty();
    changed();
    schedulePreview(0);
    setStatus('Імпортовану NRM прибрано', 'ok');
  }

  function fillAddLayerSelect() {
    const sel = $('add-layer-type');
    for (const c of generators.catalog) if (c.type !== 'image') sel.append(el('option', { value: c.type, text: c.label }));
  }

  // ---------- Параметри шару ----------

  function paramDefaults(def) {
    const out = {};
    for (const p of def.params) out[p.key] = p.default;
    return out;
  }

  function formatParam(p, v) {
    if (p.max <= 1 && p.step < 1) return Math.round(v * 100) + '%';
    const dec = p.step >= 1 ? 0 : (String(p.step).split('.')[1] || '').length;
    return (+v).toFixed(dec);
  }

  function field(labelText, control, key, forId) {
    const wrap = el('div', { class: 'field' });
    if (key) wrap.dataset.key = key;
    const lab = el('label', { class: 'field-label', for: forId || control.id });
    lab.append(el('span', { text: labelText }));
    wrap.append(lab, control);
    return wrap;
  }

  // Варіанти для imageId: усі зображення документа з іменем файлу з назви першого шару, що на них посилається
  // (шари імпорту називаються «Роль · файл»).
  function imageOptions(current) {
    const names = {};
    for (const l of S.doc.layers) {
      if (l.type === 'image' && l.params && !(l.params.imageId in names)) names[l.params.imageId] = String(l.name).split(' · ').pop();
    }
    const opts = Object.keys(S.doc.images).map((id, i) => ({ value: id, label: names[id] || 'Зображення ' + (i + 1) }));
    if (!(current in S.doc.images)) opts.unshift({ value: current, label: '— немає файлу —' });
    return opts;
  }

  function paramControl(layer, p, value) {
    const id = 'param-' + p.key, params = layer.params;
    const set = v => { params[p.key] = v; scheduleRebuild(); };
    if (p.kind === 'range') {
      const input = el('input', { type: 'range', id, min: p.min, max: p.max, step: p.step });
      input.value = value;
      const wrap = field(p.label, input, p.key), out = el('output', { text: formatParam(p, +input.value) });
      wrap.querySelector('.field-label').append(out);
      fillRange(input);
      input.addEventListener('input', () => { out.textContent = formatParam(p, +input.value); fillRange(input); set(+input.value); });
      return wrap;
    }
    if (p.kind === 'color') {
      const input = el('input', { type: 'color', id, value });
      const code = el('span', { class: 'mono', text: String(value).toUpperCase() });
      const row = el('div', { class: 'color-row' });
      row.append(input, code);
      const wrap = field(p.label, row, p.key, id);
      input.addEventListener('input', () => {
        code.textContent = input.value.toUpperCase();
        // Свій колір на основі фракції означає власний пресет.
        if (layer.type === 'base' && params.preset !== 'custom') {
          params.preset = 'custom';
          const preset = $('param-preset');
          if (preset) preset.value = 'custom';
        }
        set(input.value);
      });
      return wrap;
    }
    if (p.kind === 'select') {
      const input = el('select', { id });
      for (const o of (p.source === 'images' ? imageOptions(value) : p.options)) input.append(el('option', { value: o.value, text: o.label }));
      input.value = value;
      input.addEventListener('change', () => {
        set(input.value);
        // Пресет фракції показує свої кольори в полях «Основний / Другий колір».
        const pair = layer.type === 'base' && p.key === 'preset' ? generators.FACTION_PRESETS[input.value] : null;
        if (pair) { params.color1 = pair[0]; params.color2 = pair[1]; renderParams(); return; }
        updateParamVisibility(layer);
      });
      return field(p.label, input, p.key);
    }
    const input = el('input', { type: 'checkbox', id, checked: !!value });
    const wrap = el('label', { class: 'toggle', dataset: { key: p.key } });
    wrap.append(input, el('span', { text: p.label }));
    input.addEventListener('change', () => set(input.checked));
    return wrap;
  }

  function seedControl(layer) {
    const input = el('input', { type: 'number', id: 'param-seed', min: 0, max: 4294967295, step: 1, inputmode: 'numeric' });
    input.value = layer.seed;
    const dice = el('button', { type: 'button', class: 'btn icon', title: 'Новий випадковий варіант', 'aria-label': 'Новий випадковий варіант' });
    dice.innerHTML = iconSvg('dice');
    const row = el('div', { class: 'input-row' });
    row.append(input, dice);
    const wrap = field('Варіант візерунка (seed)', row, null, 'param-seed');
    input.addEventListener('change', () => {
      const v = Math.floor(+input.value);
      if (!Number.isFinite(v) || v < 0) { input.value = layer.seed; return; }
      layer.seed = v % 4294967296;
      input.value = layer.seed;
      scheduleRebuild();
    });
    dice.addEventListener('click', () => {
      layer.seed = Math.floor(Math.random() * 4294967296);
      input.value = layer.seed;
      scheduleRebuild();
    });
    return wrap;
  }

  function renderParams() {
    const box = $('layer-params'), layer = selectedLayer();
    box.textContent = '';
    if (!layer) { box.append(el('p', { class: 'empty', text: 'Виберіть шар у списку, щоб змінити його параметри.' })); return; }
    const def = layerDef(layer.type);
    if (!def) { box.append(el('p', { class: 'empty', text: 'Невідомий тип шару: ' + layer.type })); return; }
    if (!isObject(layer.params)) layer.params = {};

    const head = el('div', { class: 'params-head', dataset: { type: layer.type } });
    head.append(el('span', { class: 'badge', text: def.label }));
    box.append(head);

    const name = el('input', { type: 'text', id: 'param-name', maxlength: 80, spellcheck: 'false', autocomplete: 'off' });
    name.value = layer.name;
    name.addEventListener('input', () => {
      layer.name = name.value;
      markDirty();
      const title = $('layer-list').querySelector('li[data-id="' + layer.id + '"] .layer-title');
      if (title) title.textContent = layer.name || def.label;
    });
    box.append(field('Назва шару', name));

    const values = Object.assign(paramDefaults(def), layer.params);
    for (const p of def.params) box.append(paramControl(layer, p, values[p.key]));
    if (SEEDED[layer.type]) box.append(seedControl(layer));
    if (layer.type !== 'image') {
      const reset = el('button', { type: 'button', class: 'btn ghost', title: 'Повернути типові значення параметрів цього шару' });
      reset.innerHTML = iconSvg('reset') + 'Типові значення';
      reset.addEventListener('click', () => { layer.params = paramDefaults(def); renderParams(); scheduleRebuild(); });
      const foot = el('div', { class: 'params-foot' });
      foot.append(reset);
      box.append(foot);
    }
    updateParamVisibility(layer);
  }

  function updateParamVisibility(layer) {
    const rules = PARAM_VISIBLE[layer.type];
    if (!rules) return;
    const def = layerDef(layer.type), values = Object.assign(paramDefaults(def), layer.params);
    for (const f of $('layer-params').querySelectorAll('[data-key]')) {
      const rule = rules[f.dataset.key];
      f.hidden = rule ? !rule(values) : false;
    }
  }

  // ---------- Документ ----------

  function updateDocInfo() {
    const d = S.doc, raw = $('doc-name').value, base = maps.sanitizeName(raw), files = $('doc-files');
    files.textContent = '';
    files.append('Файли: ', el('span', { class: 'mono', text: base + '.dds, ' + base + '_NRM.dds, ' + base + '_SPM.dds' }));
    const bad = raw !== base;
    files.classList.toggle('warn', bad);
    if (bad) files.append(el('br'), 'Лише латинські літери, цифри та «_» — інші символи буде відкинуто.');
    const chip = $('doc-chip');
    chip.textContent = '';
    chip.append(el('b', { text: maps.sanitizeName(d.name) }), ' · ' + d.size + '×' + d.size);
  }

  function commitName() {
    const input = $('doc-name'), name = maps.sanitizeName(input.value);
    if (name !== S.doc.name) markDirty();
    S.doc.name = name;
    input.value = name;
    updateDocInfo();
  }

  function syncDocControls() {
    const d = S.doc;
    $('doc-name').value = d.name;
    $('doc-size').value = String(d.size);
    const nrm = $('nrm-strength');
    nrm.value = d.settings.nrmStrength;
    syncRange(nrm);
    $('nrm-invert-y').checked = !!d.settings.invertY;
    updateDocInfo();
  }

  function changeSize() {
    const sel = $('doc-size'), n = +sel.value, d = S.doc, old = d.size;
    if (n === old || SIZES.indexOf(n) < 0) return;
    flushRebuild();
    setStatus('Зміна розміру на ' + n + '×' + n + '…', 'busy');
    sel.disabled = true;
    setTimeout(() => {
      sel.disabled = false;
      if (S.doc !== d) return;                 // за цей час відкрили інший документ
      try { maps.resizeDocument(d, n); }
      catch (e) { sel.value = String(old); setStatus('Не вдалося змінити розмір: ' + errText(e), 'error'); return; }
      S.layerCache = null;
      clearHistory();                          // знімки старого розміру вже не підходять
      markDirty();
      updateDocInfo();
      if (rebuildNow()) {
        const shrunk = n < old && (Object.keys(d.images).length || d.nrmImport);
        setStatus('Розмір документа: ' + n + '×' + n + (shrunk ? '. Імпортовані зображення зменшено: при збільшенні назад деталі не повернуться.' : ''), 'ok');
      }
    }, 30);
  }

  function bindDocument() {
    const name = $('doc-name');
    name.addEventListener('input', updateDocInfo);
    name.addEventListener('change', commitName);
    $('doc-size').addEventListener('change', changeSize);
    const nrm = $('nrm-strength');
    nrm.addEventListener('input', () => {
      syncRange(nrm);
      S.doc.settings.nrmStrength = +nrm.value;
      markDirty();
      changed();
      schedulePreview(150);
    });
    $('nrm-invert-y').addEventListener('change', e => {
      S.doc.settings.invertY = e.target.checked;
      markDirty();
      changed();
      schedulePreview(0);
    });
  }

  // Новий документ, поточна робота й т. ін. — усе через одну точку заміни документа.
  function setDocument(doc, cache) {
    S.doc = doc;
    S.layerCache = cache;
    S.nrmImportName = '';
    S.painted = '';
    clearHistory();
    S.selectedId = doc.layers.length ? doc.layers[0].id : null;
    syncDocControls();
    renderLayers();
    renderParams();
    fitCanvas();
    changed();
    schedulePreview(0);
  }

  function newDocument() {
    const d = maps.createDocument({ name: S.doc.name, size: S.doc.size });
    d.layers.push(generators.defaultLayer('base'));
    let cache = null;
    try { cache = rebuildLayers(d, null); } catch (e) { setStatus('Не вдалося створити документ: ' + errText(e), 'error'); return; }
    setDocument(d, cache);
    S.dirty = false;
    setStatus('Новий документ: лише «Основа фракції». Додайте шари або імпортуйте зображення.', 'ok');
  }

  // ---------- 3D-прев'ю ----------

  function initPreview() {
    const canvas = $('canvas-3d');
    try { preview = new RA3.Preview3D(canvas); } catch (e) { preview = { ok: false, error: errText(e) }; }
    if (!preview.ok) {
      canvas.hidden = true;
      $('model-select').disabled = true;
      const box = $('preview-error');
      box.append(el('strong', { text: preview.error || 'WebGL недоступний' }),
        el('span', { text: '3D-прев\'ю вимкнено, решта програми працює як звичайно. Як скін виглядає в грі, показує вкладка «У грі».' }));
      box.hidden = false;
      return;
    }
    preview.setModel($('model-select').value);
    preview.setTeamColor(maps.TEAM_COLORS[S.team].rgb);
    $('model-select').addEventListener('change', e => { preview.setModel(e.target.value); preview.render(); });
    // Прев'ю поза екраном (на телефоні нижче полотна) не малюється.
    if (typeof IntersectionObserver === 'function') {
      new IntersectionObserver(entries => { if (entries[entries.length - 1].isIntersecting) preview.start(); else preview.stop(); }).observe(canvas);
    } else preview.start();
  }

  function schedulePreview(delay) {
    if (!preview || !preview.ok) return;
    clearTimeout(S.timers.preview);
    S.timers.preview = setTimeout(updatePreview, delay === undefined ? 100 : delay);
  }

  function updatePreview() {
    S.timers.preview = 0;
    if (S.stroke || !preview || !preview.ok) return;  // після мазка прев'ю оновить endStroke
    const d = S.doc;
    try {
      preview.setTextures(maps.buildDiffuse(d), maps.buildNRM(d), maps.buildSPM(d), d.size, { invertY: d.settings.invertY });
      preview.render();   // кадр і тоді, коли цикл стоїть (прев'ю поза екраном)
    } catch (e) { setStatus('3D-прев\'ю: ' + errText(e), 'error'); }
  }

  function buildTeamSwatches() {
    const box = $('team-colors');
    maps.TEAM_COLORS.forEach((t, i) => {
      const name = TEAM_NAMES[t.id] || t.id;
      const b = el('button', { type: 'button', class: 'swatch', role: 'radio', title: name, 'aria-label': name, dataset: { team: i } });
      b.style.setProperty('--c', 'rgb(' + t.rgb.join(',') + ')');
      box.append(b);
    });
    box.addEventListener('click', e => { const b = e.target.closest('[data-team]'); if (b) setTeam(+b.dataset.team); });
  }

  function setTeam(i) {
    S.team = i;
    for (const b of $('team-colors').children) b.setAttribute('aria-checked', +b.dataset.team === i ? 'true' : 'false');
    const t = maps.TEAM_COLORS[i];
    $('team-name').textContent = TEAM_NAMES[t.id] || t.id;
    if (preview && preview.ok) { preview.setTeamColor(t.rgb); preview.render(); }
    if (S.view === 'game') requestPaint();
  }

  // ---------- Імпорт ----------

  function extOf(name) { const m = /\.([^.\\/]+)$/.exec(name); return m ? m[1].toLowerCase() : ''; }

  async function decodeImage(file) {
    const ext = extOf(file.name);
    if (ext === 'dds') return dds.decodeDDS(await file.arrayBuffer());
    if (ext === 'tga') return tga.decodeTGA(await file.arrayBuffer());
    let bmp;
    try { bmp = await createImageBitmap(file, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' }); }
    catch (e) { throw new Error('формат не підтримується (потрібен DDS, TGA, PNG, JPG або WebP)'); }
    try {
      const c = el('canvas');
      c.width = bmp.width; c.height = bmp.height;
      const ctx = c.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(bmp, 0, 0);
      return { width: bmp.width, height: bmp.height, data: ctx.getImageData(0, 0, bmp.width, bmp.height).data };
    } finally { bmp.close(); }
  }

  // Зображення під розмір документа (білінійно), один раз на файл.
  function fitImage(img, size) {
    if (img.width === size && img.height === size) return img;
    return { width: size, height: size, data: util.resampleRGBA(img.data, img.width, img.height, size, size) };
  }

  // Два шари-зображення над одним буфером (колір + прозорість чи блиск + колір гравця).
  function addImagePair(img, first, second) {
    const a = maps.addImageLayer(S.doc, img, first), b = maps.addImageLayer(S.doc, img, second);
    delete S.doc.images[b.params.imageId];
    b.params.imageId = a.params.imageId;
    return a;
  }

  function applyImport(file, img, role) {
    const d = S.doc, fitted = fitImage(img, d.size);
    if (role === 'nrm') {
      d.nrmImport = new Uint8ClampedArray(fitted.data);
      S.nrmImportName = file.name;
      return null;
    }
    // Роль першою: у вузькому списку шарів обрізається ім'я файлу, а не роль.
    if (role === 'spm') {
      return addImagePair(fitted, { target: 'spec', channel: 'r', name: 'Блиск (R) · ' + file.name },
        { target: 'team', channel: 'b', name: 'Колір гравця (B) · ' + file.name });
    }
    return addImagePair(fitted, { target: 'color', name: 'Колір · ' + file.name },
      { target: 'alpha', channel: 'a', name: 'Прозорість (A) · ' + file.name });
  }

  async function importFiles(files) {
    const items = files.map(file => ({ file, info: maps.classifyTextureName(file.name) }))
      .sort((a, b) => ROLE_ORDER[a.info.role] - ROLE_ORDER[b.info.role]);
    const done = [], errors = [];
    let scaled = 0;
    let base = null, firstLayer = null;
    for (let i = 0; i < items.length; i++) {
      const { file, info } = items[i];
      setStatus('Імпорт ' + (i + 1) + ' з ' + items.length + ': «' + file.name + '»…', 'busy');
      try {
        const img = await decodeImage(file);
        const layer = applyImport(file, img, info.role);
        firstLayer = firstLayer || layer;
        if (base === null) base = info.base;   // файли впорядковано «колір» першим, тож назву дає він
        done.push({ name: file.name, role: info.role });
        if (img.width !== S.doc.size || img.height !== S.doc.size) scaled++;
      } catch (e) {
        errors.push('«' + file.name + '»: ' + errText(e));
      }
    }
    if (done.length) {
      S.doc.name = maps.sanitizeName(base);
      $('doc-name').value = S.doc.name;
      updateDocInfo();
      if (firstLayer) S.selectedId = firstLayer.id;
      renderLayers();
      renderParams();
      markDirty();
      rebuildNow();
    }
    let msg = done.length ? 'Імпортовано: ' + done.map(x => x.name + ' (' + ROLE_LABEL[x.role] + ')').join(', ') + '.' : '';
    if (scaled) msg += ' Масштабовано до ' + S.doc.size + '×' + S.doc.size + '.';
    const procedural = S.doc.layers.some(l => l.enabled && l.type !== 'image');
    if (done.some(x => x.role !== 'diffuse') && procedural) msg += ' Порада: щоб лише перефарбувати текстуру з гри, вимкніть процедурні шари.';
    if (errors.length) msg = (msg ? msg + ' ' : '') + 'Не вдалося імпортувати ' + errors.join('; ');
    setStatus(msg.trim(), errors.length ? 'error' : 'ok');
  }

  // ---------- Експорт ----------

  function download(blob, name) {
    const url = URL.createObjectURL(blob), a = el('a', { href: url, download: name });
    a.style.display = 'none';
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  async function downloadAll(files) {
    for (let i = 0; i < files.length; i++) {
      if (i) await sleep(DOWNLOAD_GAP);
      download(files[i].blob, files[i].name);
    }
  }

  // Кнопки групи вимкнені, поки триває робота; помилка йде в рядок стану.
  async function runJob(ids, failText, fn) {
    const btns = ids.map($);
    if (btns.some(b => b.disabled)) return;
    btns.forEach(b => { b.disabled = true; });
    try { await fn(); }
    catch (e) { setStatus(failText + ': ' + errText(e), 'error'); }
    finally { btns.forEach(b => { b.disabled = false; }); }
  }
  const EXPORT_BUTTONS = ['btn-export-dds', 'btn-export-png'];

  // Знімок трьох карт на момент натискання: подальші зміни документа на файли не впливають.
  function exportSources() {
    commitName();
    flushRebuild();
    const d = S.doc;
    return { base: maps.sanitizeName(d.name), size: d.size, diffuse: maps.buildDiffuse(d), nrm: maps.buildNRM(d), spm: maps.buildSPM(d) };
  }

  async function exportDDS() {
    const src = exportSources(), raw = $('uncompressed').checked;
    const jobs = [
      { name: src.base + '.dds', data: src.diffuse, fmt: raw ? 'A8R8G8B8' : (dds.hasAlpha(src.diffuse) ? 'DXT5' : 'DXT1'), normalMap: false },
      { name: src.base + '_NRM.dds', data: src.nrm, fmt: raw ? 'A8R8G8B8' : 'DXT1', normalMap: true },
      { name: src.base + '_SPM.dds', data: src.spm, fmt: raw ? 'A8R8G8B8' : 'DXT1', normalMap: false }
    ];
    const files = [];
    for (let i = 0; i < jobs.length; i++) {
      const j = jobs[i];
      const report = p => setStatus('Кодування DDS… ' + Math.floor((i + p) / jobs.length * 100) + '% — ' + j.name + ' (' + j.fmt + ')', 'busy');
      report(0);
      await sleep(0);
      const levels = dds.buildMips(j.data, src.size, { normalMap: j.normalMap });
      const bytes = await dds.encodeDDSAsync(levels, j.fmt, report);
      files.push({ name: j.name, blob: new Blob([bytes], { type: 'application/octet-stream' }) });
    }
    setStatus('Кодування DDS… 100%', 'busy');
    await downloadAll(files);
    setStatus('Експортовано: ' + jobs.map(j => j.name + ' (' + j.fmt + ')').join(', '), 'ok');
  }

  async function exportPNG() {
    const src = exportSources();
    const items = [[src.base + '.png', src.diffuse], [src.base + '_NRM.png', src.nrm], [src.base + '_SPM.png', src.spm]];
    const c = el('canvas');
    c.width = src.size; c.height = src.size;
    const ctx = c.getContext('2d');
    const files = [];
    for (const [name, data] of items) {
      setStatus('Підготовка PNG: ' + name + '…', 'busy');
      ctx.putImageData(new ImageData(data, src.size, src.size), 0, 0);
      const blob = await new Promise((resolve, reject) => c.toBlob(b => (b ? resolve(b) : reject(new Error('браузер не зміг створити PNG'))), 'image/png'));
      files.push({ name, blob });
    }
    await downloadAll(files);
    setStatus('Експортовано PNG: ' + items.map(x => x[0]).join(', '), 'ok');
  }

  // ---------- Проєкт ----------

  async function saveProject() {
    commitName();
    setStatus('Збереження проєкту…', 'busy');
    const json = await project.serialize(S.doc);
    const name = maps.sanitizeName(S.doc.name) + '.ra3tex.json';
    download(new Blob([json], { type: 'application/json' }), name);
    S.dirty = false;
    setStatus('Проєкт збережено: ' + name, 'ok');
  }

  let idCounter = 0;
  // Шари з файлу: унікальні id, назва й параметри на місці (форма шару на них спирається).
  function normalizeLayers(doc) {
    const seen = {};
    for (const l of doc.layers) {
      const def = layerDef(l.type);
      if (!def) throw new Error('невідомий тип шару «' + l.type + '»');
      if (typeof l.id !== 'string' || !l.id || seen[l.id]) l.id = 'lay' + Date.now().toString(36) + (idCounter++).toString(36);
      seen[l.id] = true;
      if (typeof l.name !== 'string' || !l.name) l.name = def.label;
      if (!isObject(l.params)) l.params = {};
      if (!Number.isFinite(l.seed)) l.seed = 0;
      l.enabled = l.enabled !== false;
    }
  }

  // Новий документ готується повністю (розбір + перебудова) і лише тоді замінює поточний.
  async function openProject(file) {
    setStatus('Відкриття проєкту «' + file.name + '»…', 'busy');
    let doc, cache;
    try {
      doc = await project.deserialize(await file.text());
      if (SIZES.indexOf(doc.size) < 0) throw new Error('розмір ' + doc.size + '×' + doc.size + ' не підтримується (потрібно 256, 512, 1024 або 2048)');
      normalizeLayers(doc);
      cache = rebuildLayers(doc, null);
    } catch (e) {
      setStatus(sentence('Не вдалося відкрити проєкт «' + file.name + '»: ' + errText(e)) + ' Поточна робота не змінилася.', 'error');
      return;
    }
    setDocument(doc, cache);
    S.dirty = false;
    setStatus('Проєкт відкрито: ' + file.name, 'ok');
  }

  const LOAD_BUTTONS = ['btn-import', 'btn-open-project', 'btn-new'];

  function handleFiles(files) {
    if (!files.length) return;
    const proj = files.find(f => extOf(f.name) === 'json');
    if (proj) runJob(LOAD_BUTTONS, 'Не вдалося відкрити проєкт', () => openProject(proj));
    else runJob(LOAD_BUTTONS, 'Не вдалося імпортувати', () => importFiles(files));
  }

  function bindFiles() {
    const fileInput = $('file-input'), projectInput = $('project-input');
    $('btn-import').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => { const files = Array.from(fileInput.files); fileInput.value = ''; handleFiles(files); });
    $('btn-open-project').addEventListener('click', () => projectInput.click());
    projectInput.addEventListener('change', () => {
      const file = projectInput.files[0];
      projectInput.value = '';
      if (file) runJob(LOAD_BUTTONS, 'Не вдалося відкрити проєкт', () => openProject(file));
    });
    $('btn-save-project').addEventListener('click', () => runJob(['btn-save-project'], 'Не вдалося зберегти проєкт', saveProject));
    $('btn-export-dds').addEventListener('click', () => runJob(EXPORT_BUTTONS, 'Не вдалося експортувати DDS', exportDDS));
    $('btn-export-png').addEventListener('click', () => runJob(EXPORT_BUTTONS, 'Не вдалося експортувати PNG', exportPNG));
    $('btn-new').addEventListener('click', e => confirmThen(e.currentTarget, 'Натисніть «Новий документ» ще раз: поточні шари й мазки зникнуть', newDocument));
  }

  // ---------- Клавіатура й перетягування ----------

  function onKeyDown(e) {
    const t = e.target, typing = t && t.matches && t.matches('input[type="text"], input[type="number"], textarea');
    if ((e.ctrlKey || e.metaKey) && !e.altKey) {
      // e.code, а не e.key: на українській розкладці Z дає «я».
      if (e.code === 'KeyS') { e.preventDefault(); runJob(['btn-save-project'], 'Не вдалося зберегти проєкт', saveProject); return; }
      if (typing) return;
      if (e.code === 'KeyZ') { e.preventDefault(); if (e.shiftKey) redo(); else undo(); }
      else if (e.code === 'KeyY') { e.preventDefault(); redo(); }
      return;
    }
    if (typing || e.altKey) return;
    if (e.code === 'BracketLeft') nudgeBrushSize(-1);
    else if (e.code === 'BracketRight') nudgeBrushSize(1);
  }

  function bindDragDrop() {
    const overlay = $('drop-overlay');
    let depth = 0;
    const hasFiles = e => !!e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') >= 0;
    window.addEventListener('dragenter', e => { if (!hasFiles(e)) return; e.preventDefault(); depth++; overlay.hidden = false; });
    window.addEventListener('dragover', e => { if (!hasFiles(e)) return; e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; });
    window.addEventListener('dragleave', e => { if (!hasFiles(e)) return; depth = Math.max(0, depth - 1); if (!depth) overlay.hidden = true; });
    window.addEventListener('drop', e => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      depth = 0;
      overlay.hidden = true;
      handleFiles(Array.from(e.dataTransfer.files));
    });
  }

  // ---------- Запуск ----------

  // Демо: основа Альянсу, панелі, заклепки, смуги кольору гравця, бруд (колір гравця до бруду, щоб бруд не фарбувався).
  function createDemo() {
    const d = maps.createDocument({ name: 'AUMyTank', size: 512 });
    const add = (type, seed, params) => { const l = generators.defaultLayer(type, seed); Object.assign(l.params, params || {}); d.layers.push(l); };
    add('base', 1, { preset: 'allied' });
    add('panels', 2);
    add('rivets', 3);
    add('team', 4, { mode: 'stripes' });
    add('dirt', 5);
    return d;
  }

  function init() {
    for (const i of document.querySelectorAll('i[data-icon]')) i.innerHTML = iconSvg(i.dataset.icon);
    fillAddLayerSelect();
    buildTeamSwatches();
    bindBrush();
    bindDocument();
    bindFiles();
    bindDragDrop();
    $('layer-list').addEventListener('click', onLayerClick);
    $('layer-list').addEventListener('change', onLayerToggle);
    $('btn-add-layer').addEventListener('click', addLayer);
    $('view-tabs').addEventListener('click', e => { const b = e.target.closest('button[data-view]'); if (b) setView(b.dataset.view); });
    document.addEventListener('keydown', onKeyDown);
    window.addEventListener('beforeunload', e => { if (S.dirty) { e.preventDefault(); e.returnValue = ''; } });
    if (typeof ResizeObserver === 'function') new ResizeObserver(fitCanvas).observe($('stage'));
    window.addEventListener('resize', fitCanvas);

    hideCursor();                              // підказка про клавіші в рядку стану
    initPreview();
    setTeam(DEFAULT_TEAM);
    setView(S.view);
    const demo = createDemo();
    let cache = null, error = null;
    try { cache = rebuildLayers(demo, null); } catch (e) { error = e; }
    setDocument(demo, cache);
    S.dirty = false;
    if (error) setStatus('Не вдалося побудувати демо-скін: ' + errText(error), 'error');
    else setStatus('Готово', 'ok');
  }

  const api = { get doc() { return S.doc; } };
  RA3.app = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
  }
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
