(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');
  const generators = RA3.generators || require('./generators.js');

  // Документ v2: стек шарів (знизу вгору) з масками; doc.maps — результат композиції (як у maps.js),
  // тож buildDiffuse/buildNRM/buildSPM/viewRGBA із maps.js працюють із ним без змін.

  const CHANNELS = ['color', 'alpha', 'height', 'spec', 'team'];
  const KINDS = ['fill', 'paint', 'proc'];
  const KIND_NAMES = { fill: 'Заливка', paint: 'Малювання' };
  const DEFAULT_CHANNELS = {
    fill: { color: true, alpha: false, height: false, spec: true, team: false },
    paint: { color: true, alpha: false, height: true, spec: true, team: true },
    proc: { color: true, alpha: true, height: true, spec: true, team: true }
  };
  const DEFAULT_MATERIAL = Object.freeze({ color: Object.freeze([128, 128, 128]), alpha: 255, height: 0.5, spec: 40, team: 0 });
  const has = (o, k) => Object.prototype.hasOwnProperty.call(o, k);

  // ---------- Допоміжне ----------

  function checkSize(size) {
    if (!util.isPow2(size)) throw new Error('Некоректний розмір документа: ' + size + ' (очікується степінь двійки)');
  }

  let idCounter = 0;
  function freshId(prefix) {
    return prefix + Date.now().toString(36) + '_' + (idCounter++).toString(36) + '_' + Math.floor(Math.random() * 1679616).toString(36);
  }

  // Глибока копія: типізовані масиви — slice, масиви й прості об'єкти — рекурсивно, решта як є.
  function deepCopy(v) {
    if (v === null || typeof v !== 'object') return v;
    if (ArrayBuffer.isView(v)) return v.slice();
    if (Array.isArray(v)) return v.map(deepCopy);
    const out = {};
    for (const k of Object.keys(v)) out[k] = deepCopy(v[k]);
    return out;
  }

  function mod(a, n) { const r = a % n; return r < 0 ? r + n : r; }

  // ---------- Карти ----------

  // Базовий стан: колір 128,128,128,255; висота 0.5; блиск 0; колір гравця 0.
  function resetMaps(maps) {
    maps.color.fill(128);
    for (let i = 3; i < maps.color.length; i += 4) maps.color[i] = 255;
    maps.height.fill(0.5);
    maps.spec.fill(0);
    maps.team.fill(0);
  }

  function allocMaps(size) {
    const n = size * size;
    const maps = { color: new Uint8ClampedArray(n * 4), height: new Float32Array(n), spec: new Uint8ClampedArray(n), team: new Uint8ClampedArray(n) };
    resetMaps(maps);
    return maps;
  }

  function cloneMaps(maps) {
    return { color: maps.color.slice(), height: maps.height.slice(), spec: maps.spec.slice(), team: maps.team.slice() };
  }

  function checkMaps(maps, n, what) {
    if (!maps || !maps.color || maps.color.length !== n * 4 || !maps.height || maps.height.length !== n ||
        !maps.spec || maps.spec.length !== n || !maps.team || maps.team.length !== n) {
      throw new Error((what || 'Карти') + ' не відповідають розміру документа');
    }
  }

  function ensureMaps(doc) {
    checkSize(doc.size);
    const n = doc.size * doc.size, m = doc.maps;
    if (!m || !m.height || m.height.length !== n || !m.color || m.color.length !== n * 4) doc.maps = allocMaps(doc.size);
    return doc.maps;
  }

  // ---------- Документ ----------

  function createDocument(opts) {
    const o = opts || {};
    const name = o.name === undefined ? 'AUMyTank' : o.name;
    const size = o.size === undefined ? 512 : o.size;
    checkSize(size);
    return {
      version: 2, name, size,
      settings: { nrmStrength: 2, invertY: false },
      nrmImport: null,
      images: {},
      layers: [],
      maps: allocMaps(size)
    };
  }

  // ---------- Шари ----------

  function mergeMaterial(base, over) {
    const src = over || {}, out = { color: (base.color || DEFAULT_MATERIAL.color).slice(0, 3), alpha: base.alpha, height: base.height, spec: base.spec, team: base.team };
    if (Array.isArray(src.color) || ArrayBuffer.isView(src.color)) out.color = [+src.color[0], +src.color[1], +src.color[2]];
    for (const k of ['alpha', 'height', 'spec', 'team']) if (src[k] !== undefined) out[k] = +src[k];
    for (const k of Object.keys(src)) if (!has(out, k)) out[k] = deepCopy(src[k]);   // додаткові поля (id матеріалу тощо)
    return out;
  }

  function allocPaint(n, isColor) {
    const value = new Uint8ClampedArray(isColor ? n * 4 : n);
    if (isColor) for (let i = 3; i < value.length; i += 4) value[i] = 255;
    return { value, cov: new Uint8ClampedArray(n) };
  }

  function makeProc(o) {
    const src = o.proc || { type: o.type, seed: o.seed, params: o.params };
    if (src.type === undefined || src.type === null || src.type === '') throw new Error('Для шару-генератора потрібен тип (proc.type)');
    const def = generators.defaultLayer(src.type, src.seed === undefined ? undefined : src.seed);   // невідомий тип -> виняток
    return { proc: { type: def.type, seed: def.seed, params: Object.assign(def.params, deepCopy(src.params || {})) }, label: def.name };
  }

  // kind: 'fill' | 'paint' | 'proc'. opts може перевизначити будь-яке поле; channels і material зливаються з типовими.
  // Для 'proc': opts.proc = { type, seed, params } або opts.type / opts.seed / opts.params.
  function newLayer(kind, opts) {
    if (KINDS.indexOf(kind) < 0) throw new Error('Невідомий вид шару: ' + kind);
    const o = opts || {};
    const layer = {
      id: freshId('lay'), name: KIND_NAMES[kind] || '', kind,
      visible: true, opacity: 1,
      channels: Object.assign({}, DEFAULT_CHANNELS[kind]),
      heightMode: 'add'
    };
    if (kind === 'fill') layer.material = mergeMaterial(DEFAULT_MATERIAL, null);
    if (kind === 'paint') layer.paint = {};
    if (kind === 'proc') { const p = makeProc(o); layer.proc = p.proc; layer.name = p.label; }
    layer.mask = null;
    for (const k of Object.keys(o)) {
      const v = o[k];
      if (v === undefined) continue;
      switch (k) {
        case 'kind': case 'type': case 'seed': case 'params': case 'proc': break;
        case 'channels':
          if (v && typeof v === 'object') for (const c of CHANNELS) if (has(v, c)) layer.channels[c] = !!v[c];
          break;
        case 'material': layer.material = mergeMaterial(layer.material || DEFAULT_MATERIAL, v); break;
        default: layer[k] = deepCopy(v);
      }
    }
    return layer;
  }

  // Опис шару з materials.js («розумні матеріали»): { kind, name?, material?, channels?, heightMode?, opacity?, proc?,
  // mask?: { invert?, fill?: 'white'|'black', gen? } }. size потрібен, щоб одразу виділити чорну маску.
  function layerFromSpec(spec, size) {
    if (!spec || typeof spec !== 'object') throw new Error('Некоректний опис шару');
    const opts = {};
    for (const k of ['name', 'material', 'channels', 'heightMode', 'opacity', 'visible', 'proc', 'type', 'seed', 'params']) {
      if (spec[k] !== undefined) opts[k] = spec[k];
    }
    const layer = newLayer(spec.kind, opts);
    if (spec.mask) {
      const m = spec.mask, fill = m.fill === undefined ? 'white' : m.fill;
      if (fill !== 'white' && fill !== 'black') throw new Error('Невідоме заповнення маски: ' + fill + ' (очікується white або black)');
      let paint = null;
      if (fill === 'black') { checkSize(size); paint = new Uint8ClampedArray(size * size); }
      layer.mask = { enabled: true, invert: !!m.invert, paint, gen: m.gen ? deepCopy(m.gen) : null };
    }
    return layer;
  }

  function addMask(layer, size, fill) {
    const f = fill === undefined ? 'white' : fill;
    if (f !== 'white' && f !== 'black') throw new Error('Невідоме заповнення маски: ' + f + ' (очікується white або black)');
    checkSize(size);
    const paint = new Uint8ClampedArray(size * size);
    if (f === 'white') paint.fill(255);
    layer.mask = { enabled: true, invert: false, paint, gen: null };
    return layer.mask;
  }

  function removeMask(layer) { layer.mask = null; }

  function cloneLayer(layer) {
    const out = deepCopy(layer);
    out.id = freshId('lay');
    out.name = (layer.name || '') + ' (копія)';
    return out;
  }

  // ---------- Маски-генератори ----------

  // Тестовий гачок: setMaskgen(obj) підміняє RA3.maskgen; setMaskgen(null) повертає звичайний пошук.
  let maskgenOverride = null;
  function setMaskgen(obj) { maskgenOverride = obj || null; }

  // Пошук під час виклику: у браузері maskgen.js може завантажитися після цього файлу.
  function resolveMaskgen() {
    if (maskgenOverride) return maskgenOverride;
    if (RA3.maskgen) return RA3.maskgen;
    if (typeof require !== 'undefined') {
      try { return require('./maskgen.js'); } catch (e) { if (e && e.code === 'MODULE_NOT_FOUND') return null; throw e; }
    }
    return null;
  }

  // ---------- Композиція ----------

  // Робочі буфери перевикористовуються між викликами (без виділення пам'яті на кожну композицію).
  let scratchT = null, scratchMaps = null;
  function getT(n) {
    if (!scratchT || scratchT.length !== n) scratchT = new Float32Array(n);
    return scratchT;
  }
  function getScratchMaps(size) {
    const n = size * size;
    if (!scratchMaps || scratchMaps.height.length !== n) scratchMaps = allocMaps(size);
    return scratchMaps;
  }

  function opacityOf(layer) {
    const op = layer.opacity;
    return typeof op === 'number' && Number.isFinite(op) ? util.clamp(op, 0, 1) : 1;
  }

  function layerLabel(layer) { return '«' + (layer.name || layer.id) + '»'; }

  // Сила шару: { T: null, tc } — рівномірна (без маски), або T[i] = opacity · M[i]. max = 0 -> шар нічого не змінює.
  // M = gen (по поточному результату maps) × paint/255, invert -> 1−M.
  function strength(layer, op, maps, size) {
    const n = size * size, mask = layer.mask;
    if (!mask || mask.enabled === false) return { T: null, tc: op, max: op };
    let G = null;
    if (mask.gen) {
      const mg = resolveMaskgen();
      if (!mg || typeof mg.generate !== 'function') {
        throw new Error('Генератор масок (maskgen.js) не завантажено, а шар ' + layerLabel(layer) + ' має маску-генератор');
      }
      G = mg.generate(mask.gen, maps, size);
      if (!G || G.length !== n) throw new Error('Генератор маски шару ' + layerLabel(layer) + ' повернув ' + (G ? G.length : 0) + ' значень, очікується ' + n);
    }
    const P = mask.paint || null;
    if (P && P.length !== n) throw new Error('Малюнок маски шару ' + layerLabel(layer) + ' не відповідає розміру документа');
    const inv = !!mask.invert;
    if (!G && !P) { const t = inv ? 0 : op; return { T: null, tc: t, max: t }; }
    // t = op · (invert ? 1 − m : m) = A + B·m; окремі цикли без розгалужень для кожного поєднання gen/paint.
    const T = getT(n), A = inv ? op : 0, B = inv ? -op : op, B8 = B / 255;
    let max = 0;
    if (G && P) {
      for (let i = 0; i < n; i++) {
        const g = G[i], t = A + B8 * (g > 0 ? (g < 1 ? g : 1) : 0) * P[i];   // NaN у gen -> 0
        T[i] = t;
        if (t > max) max = t;
      }
    } else if (G) {
      for (let i = 0; i < n; i++) {
        const g = G[i], t = A + B * (g > 0 ? (g < 1 ? g : 1) : 0);
        T[i] = t;
        if (t > max) max = t;
      }
    } else {
      for (let i = 0; i < n; i++) {
        const t = A + B8 * P[i];
        T[i] = t;
        if (t > max) max = t;
      }
    }
    return { T, tc: 0, max };
  }

  // Таблиці lerp(x, v, t) для x = 0..255: за рівномірної сили результат залежить лише від x (точно як попіксельно).
  const LUTS = [new Uint8ClampedArray(256), new Uint8ClampedArray(256), new Uint8ClampedArray(256)];
  function lerpLut(k, v, t) {
    const lut = LUTS[k];
    for (let x = 0; x < 256; x++) lut[x] = x + (v - x) * t;
    return lut;
  }
  const COV_T = new Float64Array(256);   // tc · cov/255 для шару малювання з рівномірною силою

  function blendColorConst(col, n, T, tc, r, g, b) {
    if (!T) {
      const lr = lerpLut(0, r, tc), lg = lerpLut(1, g, tc), lb = lerpLut(2, b, tc);
      for (let o = 0, e = n * 4; o < e; o += 4) { col[o] = lr[col[o]]; col[o + 1] = lg[col[o + 1]]; col[o + 2] = lb[col[o + 2]]; }
      return;
    }
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const t = T[i];
      if (t <= 0) continue;
      col[o] += (r - col[o]) * t; col[o + 1] += (g - col[o + 1]) * t; col[o + 2] += (b - col[o + 2]) * t;
    }
  }

  function blendByteConst(buf, stride, off, n, T, tc, v) {
    if (!T) {
      const lut = lerpLut(0, v, tc);
      for (let o = off, e = n * stride; o < e; o += stride) buf[o] = lut[buf[o]];
      return;
    }
    for (let i = 0, o = off; i < n; i++, o += stride) {
      const t = T[i];
      if (t > 0) buf[o] += (v - buf[o]) * t;
    }
  }

  // 'add': h += (v − 0.5)·t (обмежено 0..1, як у генераторах); 'replace': h = lerp(h, v, t).
  function blendHeightConst(h, n, T, tc, v, mode) {
    if (mode === 'replace') {
      if (!T) { for (let i = 0; i < n; i++) h[i] += (v - h[i]) * tc; return; }
      for (let i = 0; i < n; i++) { const t = T[i]; if (t > 0) h[i] += (v - h[i]) * t; }
      return;
    }
    const d = v - 0.5;
    if (!T) {
      const dt = d * tc;
      for (let i = 0; i < n; i++) { const x = h[i] + dt; h[i] = x > 0 ? (x < 1 ? x : 1) : 0; }
      return;
    }
    for (let i = 0; i < n; i++) {
      const t = T[i];
      if (t <= 0) continue;
      const x = h[i] + d * t;
      h[i] = x > 0 ? (x < 1 ? x : 1) : 0;
    }
  }

  function blendFill(layer, maps, n, T, tc) {
    const m = layer.material || DEFAULT_MATERIAL, ch = layer.channels, col = maps.color;
    const c = m.color || DEFAULT_MATERIAL.color;
    const val = (k) => { const v = m[k]; return typeof v === 'number' && Number.isFinite(v) ? v : DEFAULT_MATERIAL[k]; };
    if (ch.color) blendColorConst(col, n, T, tc, +c[0], +c[1], +c[2]);
    if (ch.alpha) blendByteConst(col, 4, 3, n, T, tc, val('alpha'));
    if (ch.height) blendHeightConst(maps.height, n, T, tc, val('height'), layer.heightMode);
    if (ch.spec) blendByteConst(maps.spec, 1, 0, n, T, tc, val('spec'));
    if (ch.team) blendByteConst(maps.team, 1, 0, n, T, tc, val('team'));
  }

  function paintChannel(layer, c, n) {
    const P = layer.paint && layer.paint[c];
    if (!P) return null;
    if (!P.cov || P.cov.length !== n || !P.value || P.value.length !== (c === 'color' ? n * 4 : n)) {
      throw new Error('Буфер малювання «' + c + '» шару ' + layerLabel(layer) + ' не відповідає розміру документа');
    }
    return P;
  }

  // Як заливка, але значення й покриття з paint[ch]: t = opacity · M · cov/255.
  function blendPaint(layer, maps, n, T, tc) {
    const ch = layer.channels, col = maps.color, CT = COV_T;
    if (!T) for (let c = 0; c < 256; c++) CT[c] = tc * c / 255;
    let P = ch.color ? paintChannel(layer, 'color', n) : null;
    if (P) {
      const v = P.value, cv = P.cov;
      for (let i = 0, o = 0; i < n; i++, o += 4) {
        const c = cv[i];
        if (c === 0) continue;
        const t = T ? T[i] * c / 255 : CT[c];
        if (t <= 0) continue;
        col[o] += (v[o] - col[o]) * t; col[o + 1] += (v[o + 1] - col[o + 1]) * t; col[o + 2] += (v[o + 2] - col[o + 2]) * t;
      }
    }
    P = ch.alpha ? paintChannel(layer, 'alpha', n) : null;
    if (P) {
      const v = P.value, cv = P.cov;
      for (let i = 0; i < n; i++) {
        const c = cv[i];
        if (c === 0) continue;
        const t = T ? T[i] * c / 255 : CT[c], o = i * 4 + 3;
        if (t > 0) col[o] += (v[i] - col[o]) * t;
      }
    }
    P = ch.height ? paintChannel(layer, 'height', n) : null;
    if (P) {
      const v = P.value, cv = P.cov, h = maps.height, replace = layer.heightMode === 'replace';
      for (let i = 0; i < n; i++) {
        const c = cv[i];
        if (c === 0) continue;
        const t = T ? T[i] * c / 255 : CT[c];
        if (t <= 0) continue;
        if (replace) { h[i] += (v[i] / 255 - h[i]) * t; continue; }
        const x = h[i] + (v[i] / 255 - 0.5) * t;
        h[i] = x > 0 ? (x < 1 ? x : 1) : 0;
      }
    }
    for (const k of ['spec', 'team']) {
      P = ch[k] ? paintChannel(layer, k, n) : null;
      if (!P) continue;
      const v = P.value, cv = P.cov, m = maps[k];
      for (let i = 0; i < n; i++) {
        const c = cv[i];
        if (c === 0) continue;
        const t = T ? T[i] * c / 255 : CT[c];
        if (t > 0) m[i] += (v[i] - m[i]) * t;
      }
    }
  }

  // Генератор працює на копії C, потім для ввімкнених каналів C = lerp(C, копія, t).
  function blendProc(doc, layer, maps, size, T, tc) {
    const n = size * size, S = getScratchMaps(size);
    S.color.set(maps.color); S.height.set(maps.height); S.spec.set(maps.spec); S.team.set(maps.team);
    generators.apply(layer.proc, S, size, doc.images);
    const ch = layer.channels, col = maps.color, sc = S.color;
    if (ch.color) {
      for (let i = 0, o = 0; i < n; i++, o += 4) {
        const t = T ? T[i] : tc;
        if (t <= 0) continue;
        col[o] += (sc[o] - col[o]) * t; col[o + 1] += (sc[o + 1] - col[o + 1]) * t; col[o + 2] += (sc[o + 2] - col[o + 2]) * t;
      }
    }
    if (ch.alpha) for (let i = 0, o = 3; i < n; i++, o += 4) { const t = T ? T[i] : tc; if (t > 0) col[o] += (sc[o] - col[o]) * t; }
    for (const k of ['height', 'spec', 'team']) {
      if (!ch[k]) continue;
      const m = maps[k], s = S[k];
      for (let i = 0; i < n; i++) { const t = T ? T[i] : tc; if (t > 0) m[i] += (s[i] - m[i]) * t; }
    }
  }

  // Накладає один шар на maps (поточний результат C) на місці.
  function applyLayer(doc, layer, maps) {
    if (!layer || layer.visible === false) return;
    const op = opacityOf(layer);
    if (!(op > 0)) return;
    const ch = layer.channels || {};
    let any = false, all = true;
    for (const c of CHANNELS) { if (ch[c]) any = true; else all = false; }
    if (!any) return;
    if (KINDS.indexOf(layer.kind) < 0) throw new Error('Невідомий вид шару: ' + layer.kind);
    const size = doc.size;
    const masked = !!(layer.mask && layer.mask.enabled !== false);
    if (layer.kind === 'proc' && !masked && op === 1 && all) {    // те саме, що копія + lerp з t = 1, але без копії
      generators.apply(layer.proc, maps, size, doc.images);
      return;
    }
    const s = strength(layer, op, maps, size);
    if (!(s.max > 0)) return;
    const n = size * size;
    if (layer.kind === 'fill') blendFill(layer, maps, n, s.T, s.tc);
    else if (layer.kind === 'paint') blendPaint(layer, maps, n, s.T, s.tc);
    else blendProc(doc, layer, maps, size, s.T, s.tc);
  }

  // Повний перерахунок doc.maps: база -> усі видимі шари знизу вгору.
  function composite(doc) {
    const maps = ensureMaps(doc);
    resetMaps(maps);
    for (const layer of doc.layers) applyLayer(doc, layer, maps);
    return maps;
  }

  // Композиція шарів [0, index) у нові буфери; doc.maps не змінюється.
  function snapshotBelow(doc, index) {
    checkSize(doc.size);
    const maps = allocMaps(doc.size), end = Math.min(Math.max(index | 0, 0), doc.layers.length);
    for (let k = 0; k < end; k++) applyLayer(doc, doc.layers[k], maps);
    return maps;
  }

  // doc.maps = копія below, потім шари [index, кінець). below не змінюється (його можна використовувати повторно).
  function compositeFrom(doc, index, below) {
    const maps = ensureMaps(doc), n = doc.size * doc.size;
    checkMaps(below, n, 'Знімок карт');
    maps.color.set(below.color); maps.height.set(below.height); maps.spec.set(below.spec); maps.team.set(below.team);
    for (let k = Math.max(index | 0, 0); k < doc.layers.length; k++) applyLayer(doc, doc.layers[k], maps);
    return maps;
  }

  // ---------- Пензель і заливка ----------

  function flowOf(o) {
    const f = o.flow === undefined ? 1 : +o.flow;
    return Number.isFinite(f) ? util.clamp(f, 0, 1) : 1;
  }

  // Цілі мазка: масив записів { buf } для маски або { val, cov, isColor, r, g, b, v } для каналів малювання.
  function brushTargets(layer, target, o, n) {
    const erase = !!o.erase;
    if (target === 'mask') {
      const mask = layer.mask;
      if (!mask) throw new Error('Шар ' + layerLabel(layer) + ' не має маски: спершу додайте маску');
      if (!mask.paint) mask.paint = new Uint8ClampedArray(n).fill(255);   // paint = null означає «уся маска 1»
      else if (mask.paint.length !== n) throw new Error('Малюнок маски шару ' + layerLabel(layer) + ' не відповідає розміру документа');
      return [{ mask: true, buf: mask.paint }];
    }
    if (target !== 'paint') throw new Error('Невідома ціль: ' + target + ' (очікується paint або mask)');
    if (layer.kind !== 'paint') throw new Error('Пензлем можна малювати лише на шарі «Малювання» (або на масці шару)');
    let mat = null;
    if (!erase) {
      if (!o.material || typeof o.material !== 'object') throw new Error('Не задано матеріал пензля');
      mat = mergeMaterial(DEFAULT_MATERIAL, o.material);
    }
    const want = o.channels, lch = layer.channels || {};
    if (!layer.paint) layer.paint = {};
    const out = [];
    for (const c of CHANNELS) {
      if (!lch[c] || (want && !want[c])) continue;
      let P = layer.paint[c];
      if (!P) {
        if (erase) continue;                                           // стирати нічого
        P = layer.paint[c] = allocPaint(n, c === 'color');
      } else {
        paintChannel(layer, c, n);
      }
      const e = { mask: false, isColor: c === 'color', val: P.value, cov: P.cov, r: 0, g: 0, b: 0, v: 0 };
      if (mat) {
        if (c === 'color') { e.r = mat.color[0]; e.g = mat.color[1]; e.b = mat.color[2]; }
        else e.v = c === 'height' ? mat.height * 255 : mat[c];
      }
      out.push(e);
    }
    return out;
  }

  // Як maps.applyBrush («over»): cov' = cov + (255−cov)·a, value зміщується до цілі з вагою a / (cov'/255),
  // тож свіжий піксель бере ціль точно. Гумка: cov −= cov·a. Маска: до 255 (або до 0 гумкою) з вагою a.
  function stamp(tg, i, a, erase) {
    for (let k = 0; k < tg.length; k++) {
      const e = tg[k];
      if (e.mask) {
        const v = e.buf[i];
        e.buf[i] = erase ? v - v * a : v + (255 - v) * a;
        continue;
      }
      const c = e.cov[i];
      if (erase) { e.cov[i] = c - c * a; continue; }
      const c0 = c / 255, cn = c0 + (1 - c0) * a, w = a / cn, val = e.val;
      if (e.isColor) {
        const q = i * 4;
        val[q] += (e.r - val[q]) * w; val[q + 1] += (e.g - val[q + 1]) * w; val[q + 2] += (e.b - val[q + 2]) * w;
      } else {
        val[i] += (e.v - val[i]) * w;
      }
      e.cov[i] = cn * 255;
    }
  }

  let colScratch = null;

  // dab = { w, h, data: Float32Array(w·h) }, центр у (cx, cy) текселях, лівий верхній кут = round(c − розмір/2).
  // Координати загортаються (текстура тайлиться); відбиток, більший за текстуру, торкається кожного текселя один раз.
  // opts = { flow = 1, material, channels (які канали малює пензель), erase = false }.
  function applyDab(doc, layer, target, dab, cx, cy, opts) {
    const o = opts || {};
    checkSize(doc.size);
    const size = doc.size, n = size * size;
    const W = dab && dab.w, H = dab && dab.h, data = dab && dab.data;
    if (!(Number.isInteger(W) && Number.isInteger(H) && W > 0 && H > 0) || !data || data.length < W * H) throw new Error('Некоректний відбиток пензля');
    if (!Number.isFinite(cx) || !Number.isFinite(cy)) throw new Error('Некоректна позиція мазка');
    const tg = brushTargets(layer, target, o, n);
    const flow = flowOf(o), erase = !!o.erase;
    if (!tg.length || !(flow > 0)) return;
    let sx = 0, cw = W, sy = 0, chh = H;
    if (W > size) { sx = (W - size) >> 1; cw = size; }
    if (H > size) { sy = (H - size) >> 1; chh = size; }
    const x0 = Math.round(cx - W / 2) + sx, y0 = Math.round(cy - H / 2) + sy;
    if (!colScratch || colScratch.length < cw) colScratch = new Int32Array(Math.max(cw, 64));
    const cols = colScratch;
    for (let k = 0; k < cw; k++) cols[k] = mod(x0 + k, size);
    for (let j = 0; j < chh; j++) {
      const row = mod(y0 + j, size) * size, d0 = (sy + j) * W + sx;
      for (let k = 0; k < cw; k++) {
        let a = data[d0 + k] * flow;
        if (!(a > 0)) continue;
        if (a > 1) a = 1;
        stamp(tg, row + cols[k], a, erase);
      }
    }
  }

  // region = U8(n) ваги 0..255; та сама математика, що й у мазка, з a = region/255 · flow по всій текстурі.
  function fillRegion(doc, layer, target, region, opts) {
    const o = opts || {};
    checkSize(doc.size);
    const n = doc.size * doc.size;
    if (!region || region.length !== n) throw new Error('Розмір області (' + (region ? region.length : 0) + ') не відповідає документу (' + n + ')');
    const tg = brushTargets(layer, target, o, n);
    const flow = flowOf(o), erase = !!o.erase;
    if (!tg.length || !(flow > 0)) return;
    const k = flow / 255;
    for (let i = 0; i < n; i++) {
      const r = region[i];
      if (!(r > 0)) continue;
      let a = r * k;
      if (a > 1) a = 1;
      stamp(tg, i, a, erase);
    }
  }

  // ---------- Undo ----------

  function snapshot(layer, target) {
    if (target === 'paint') {
      const src = layer.paint || {}, paint = {};
      for (const c of CHANNELS) if (src[c]) paint[c] = { value: src[c].value.slice(), cov: src[c].cov.slice() };
      return { target, layerId: layer.id, paint };
    }
    if (target === 'mask') {
      const m = layer.mask;
      return { target, layerId: layer.id, hasMask: !!m, paint: m && m.paint ? m.paint.slice() : null };
    }
    throw new Error('Невідома ціль: ' + target + ' (очікується paint або mask)');
  }

  // Знімок копіюється, тож його можна відновлювати повторно (undo/redo).
  function restore(layer, snap) {
    if (!snap || typeof snap !== 'object') throw new Error('Некоректний знімок шару');
    if (snap.target === 'paint') {
      const paint = {};
      for (const c of CHANNELS) if (snap.paint[c]) paint[c] = { value: snap.paint[c].value.slice(), cov: snap.paint[c].cov.slice() };
      layer.paint = paint;
      return;
    }
    if (snap.target === 'mask') {
      if (!snap.hasMask) { layer.mask = null; return; }
      const p = snap.paint ? snap.paint.slice() : null;
      if (layer.mask) layer.mask.paint = p;
      else layer.mask = { enabled: true, invert: false, paint: p, gen: null };
      return;
    }
    throw new Error('Невідома ціль знімка: ' + snap.target);
  }

  // ---------- Зображення ----------

  function addImage(doc, image) {
    checkSize(doc.size);
    const w = image && image.width, h = image && image.height, size = doc.size;
    if (!(w > 0 && h > 0) || !image.data || image.data.length !== w * h * 4) {
      throw new Error('Розмір буфера не відповідає розмірам зображення ' + w + '×' + h);
    }
    const data = (w === size && h === size) ? new Uint8ClampedArray(image.data) : util.resampleRGBA(image.data, w, h, size, size);
    if (!doc.images) doc.images = {};
    let id;
    do { id = freshId('img'); } while (has(doc.images, id));
    doc.images[id] = data;
    return id;
  }

  // ---------- Зміна розміру ----------

  function nearest(buf, stride, old, size, src, what) {
    if (!buf || buf.length !== old * old * stride) throw new Error(what + ' не відповідає розміру документа');
    const out = new Uint8ClampedArray(size * size * stride);
    for (let y = 0; y < size; y++) {
      const fr = src[y] * old, tr = y * size;
      for (let x = 0; x < size; x++) {
        const fi = (fr + src[x]) * stride, ti = (tr + x) * stride;
        for (let c = 0; c < stride; c++) out[ti + c] = buf[fi + c];
      }
    }
    return out;
  }

  // Буфери малювання й маски — найближчий сусід, images і nrmImport — білінійно, maps перевиділяються в базовому
  // стані (далі потрібен composite). Усе обчислюється наперед, тож помилка не залишає документ напівзміненим.
  function resize(doc, newSize) {
    checkSize(newSize);
    const old = doc.size;
    if (newSize === old) return;
    const images = {};
    for (const id of Object.keys(doc.images || {})) images[id] = util.resampleRGBA(doc.images[id], old, old, newSize, newSize);
    const nrmImport = doc.nrmImport ? util.resampleRGBA(doc.nrmImport, old, old, newSize, newSize) : null;
    const src = new Int32Array(newSize);
    for (let x = 0; x < newSize; x++) src[x] = Math.min(old - 1, Math.floor((x + 0.5) * old / newSize));
    const plans = doc.layers.map(layer => {
      let paint;
      if (layer.paint) {
        paint = {};
        for (const c of Object.keys(layer.paint)) {
          const P = layer.paint[c], what = 'Буфер малювання «' + c + '» шару ' + layerLabel(layer);
          paint[c] = { value: nearest(P.value, c === 'color' ? 4 : 1, old, newSize, src, what), cov: nearest(P.cov, 1, old, newSize, src, what) };
        }
      }
      const mp = layer.mask && layer.mask.paint ? nearest(layer.mask.paint, 1, old, newSize, src, 'Малюнок маски шару ' + layerLabel(layer)) : null;
      return { paint, mp };
    });
    doc.layers.forEach((layer, k) => {
      if (plans[k].paint) layer.paint = plans[k].paint;
      if (layer.mask && layer.mask.paint) layer.mask.paint = plans[k].mp;
    });
    doc.size = newSize; doc.images = images; doc.nrmImport = nrmImport; doc.maps = allocMaps(newSize);
  }

  const api = {
    CHANNELS, KINDS, DEFAULT_MATERIAL,
    createDocument, newLayer, layerFromSpec, addMask, removeMask, cloneLayer,
    composite, snapshotBelow, compositeFrom, cloneMaps, allocMaps,
    applyDab, fillRegion, snapshot, restore, addImage, resize, setMaskgen
  };
  RA3.layers = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
