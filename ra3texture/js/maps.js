(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');

  const TARGETS = ['color', 'alpha', 'height', 'spec', 'team'];
  const CHANNELS = ['lum', 'r', 'g', 'b', 'a'];
  const TEAM_COLORS = [
    { id: 'Gold', rgb: [240, 215, 25] }, { id: 'Red', rgb: [230, 20, 20] },
    { id: 'Blue', rgb: [50, 75, 200] }, { id: 'Green', rgb: [20, 105, 60] },
    { id: 'Orange', rgb: [225, 115, 20] }, { id: 'SkyBlue', rgb: [102, 207, 248] },
    { id: 'Purple', rgb: [125, 25, 200] }, { id: 'Pink', rgb: [248, 152, 248] }
  ];

  // Шейдер RA3 і спрощене освітлення для вигляду «У грі».
  const GAMMA = 2.2;
  const BUMP_SCALE = 1.5;
  const SPECULAR = 0.8;
  const ALPHA_TEST = 96;
  const AMBIENT = 0.35;
  const LIGHT = (function () { // y донизу по зображенню: сонце зверху-зліва
    const x = -0.5, y = -0.6, z = 0.62, l = Math.sqrt(x * x + y * y + z * z);
    return [x / l, y / l, z / l];
  })();
  const CHECKER_CELL = 8, CHECKER_LIGHT = 150, CHECKER_DARK = 110;
  const TO_LINEAR = new Float64Array(256); // gamma-байт -> лінійне значення
  for (let i = 0; i < 256; i++) TO_LINEAR[i] = Math.pow(i / 255, GAMMA);
  const GAMMA_STEPS = 65535; // лінійне 0..1 -> gamma-байт; 16 біт, щоб темна ділянка не сходинкувала
  const TO_GAMMA = new Uint8Array(GAMMA_STEPS + 1);
  for (let i = 0; i <= GAMMA_STEPS; i++) TO_GAMMA[i] = Math.round(Math.pow(i / GAMMA_STEPS, 1 / GAMMA) * 255);

  // ---------- Допоміжне ----------

  function checkSize(size) {
    if (!util.isPow2(size)) throw new Error('Некоректний розмір документа: ' + size + ' (очікується степінь двійки)');
  }

  function checkTarget(target) {
    if (TARGETS.indexOf(target) < 0) throw new Error('Невідома ціль: ' + target);
  }

  let idCounter = 0;
  function freshId(prefix, taken) {
    let id;
    do { id = prefix + Date.now().toString(36) + (idCounter++).toString(36) + Math.floor(Math.random() * 1296).toString(36); } while (taken(id));
    return id;
  }

  // Базовий стан карт: колір 128,128,128,255; висота 0.5; блиск 0; колір гравця 0.
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

  function allocOverlay(n, isColor) {
    const value = new Uint8ClampedArray(isColor ? n * 4 : n);
    if (isColor) for (let i = 3; i < value.length; i += 4) value[i] = 255;
    return { value, cov: new Uint8ClampedArray(n) };
  }

  function allocOverlays(size) {
    const n = size * size, overlays = {};
    for (const t of TARGETS) overlays[t] = allocOverlay(n, t === 'color');
    return overlays;
  }

  // ---------- Документ ----------

  function createDocument(opts) {
    const o = opts || {};
    const name = o.name === undefined ? 'AUMyTank' : o.name;
    const size = o.size === undefined ? 512 : o.size;
    checkSize(size);
    return {
      name, size,
      layers: [], images: {}, nrmImport: null,
      settings: { nrmStrength: 2, invertY: false },
      overlays: allocOverlays(size),
      maps: allocMaps(size)
    };
  }

  // Накладає оверлеї пензля на maps: результат = lerp(шари, оверлей, покриття / 255).
  function applyOverlays(doc) {
    const n = doc.size * doc.size, maps = doc.maps, ov = doc.overlays;
    let cov = ov.color.cov, val = ov.color.value, col = maps.color;
    for (let i = 0; i < n; i++) {
      const c = cov[i]; if (c === 0) continue;
      const t = c / 255, o = i * 4;
      col[o] = util.lerp(col[o], val[o], t); col[o + 1] = util.lerp(col[o + 1], val[o + 1], t); col[o + 2] = util.lerp(col[o + 2], val[o + 2], t);
    }
    cov = ov.alpha.cov; val = ov.alpha.value;
    for (let i = 0; i < n; i++) {
      const c = cov[i]; if (c === 0) continue;
      col[i * 4 + 3] = util.lerp(col[i * 4 + 3], val[i], c / 255);
    }
    cov = ov.height.cov; val = ov.height.value; const h = maps.height;
    for (let i = 0; i < n; i++) {
      const c = cov[i]; if (c === 0) continue;
      h[i] = util.lerp(h[i], val[i] / 255, c / 255);
    }
    for (const t of ['spec', 'team']) {
      cov = ov[t].cov; val = ov[t].value; const m = maps[t];
      for (let i = 0; i < n; i++) {
        const c = cov[i]; if (c === 0) continue;
        m[i] = util.lerp(m[i], val[i], c / 255);
      }
    }
  }

  // База -> ввімкнені шари (registry.apply) -> оверлеї пензля. Мазки переживають зміну шарів.
  function rebuild(doc, registry) {
    resetMaps(doc.maps);
    for (const layer of doc.layers) {
      if (layer.enabled) registry.apply(layer, doc.maps, doc.size, doc.images);
    }
    applyOverlays(doc);
  }

  // ---------- Пензель ----------

  // Піксель (x, y) має позицію (x, y), тож (cx, cy) цілі потрапляють точно в центр відбитка.
  // Відстань береться по найкоротшому шляху з загортанням (текстура тайлиться).
  // Покриття росте як при накладанні шару: cov += (255 - cov) * falloff * strength, а value зміщується
  // до заданого з вагою a / cov_нове, тож на порожньому покритті value стає рівно заданим.
  function applyBrush(doc, target, cx, cy, opts) {
    checkTarget(target);
    const o = opts || {};
    const erase = !!o.erase;
    let tr = 0, tg = 0, tb = 0, tv = 0;
    if (!erase) {
      if (target === 'color') {
        const v = o.value;
        if (!v || !(v.length >= 3)) throw new Error('Для кольору потрібне значення [r, g, b]');
        tr = +v[0]; tg = +v[1]; tb = +v[2];
      } else {
        tv = +o.value;
        if (!isFinite(tv)) throw new Error('Не задано значення пензля (число 0..255)');
      }
    }
    const radius = +o.radius;
    if (!(radius > 0)) return;
    const hardness = util.clamp(o.hardness === undefined ? 0.5 : +o.hardness, 0, 1);
    const strength = util.clamp(o.strength === undefined ? 1 : +o.strength, 0, 1);
    const size = doc.size, e0 = hardness * radius, r2 = radius * radius;
    const ov = doc.overlays[target], cov = ov.cov, val = ov.value, isColor = target === 'color';

    // Перелік пікселів по осі: не більше size, кожен піксель один раз (радіус може перевищувати текстуру).
    function axis(c) {
      const first = Math.ceil(c - radius), count = Math.min(Math.floor(c + radius) - first + 1, size);
      const idx = new Int32Array(Math.max(count, 0)), d2 = new Float64Array(idx.length);
      for (let k = 0; k < idx.length; k++) {
        const p = first + k;
        let d = p - c; d -= size * Math.round(d / size);
        idx[k] = ((p % size) + size) % size; d2[k] = d * d;
      }
      return { idx, d2 };
    }
    const ax = axis(cx), ay = axis(cy);

    for (let j = 0; j < ay.idx.length; j++) {
      const row = ay.idx[j] * size, dy2 = ay.d2[j];
      for (let k = 0; k < ax.idx.length; k++) {
        const dd = ax.d2[k] + dy2;
        if (dd >= r2) continue;
        const a = (1 - util.smoothstep(e0, radius, Math.sqrt(dd))) * strength;
        if (a <= 0) continue;
        const i = row + ax.idx[k], c = cov[i];
        if (erase) { cov[i] = c - c * a; continue; }
        const c0 = c / 255, cn = c0 + (1 - c0) * a, t = a / cn;
        if (isColor) {
          const q = i * 4;
          val[q] = util.lerp(val[q], tr, t); val[q + 1] = util.lerp(val[q + 1], tg, t); val[q + 2] = util.lerp(val[q + 2], tb, t);
        } else {
          val[i] = util.lerp(val[i], tv, t);
        }
        cov[i] = cn * 255;
      }
    }
  }

  // ---------- Undo ----------

  function cloneOverlay(doc, target) {
    checkTarget(target);
    const ov = doc.overlays[target];
    return { target, value: ov.value.slice(), cov: ov.cov.slice() };
  }

  function restoreOverlay(doc, snap) {
    checkTarget(snap.target);
    const ov = doc.overlays[snap.target];
    if (snap.cov.length !== ov.cov.length || snap.value.length !== ov.value.length) {
      throw new Error('Знімок оверлею не відповідає розміру документа');
    }
    ov.value.set(snap.value); ov.cov.set(snap.cov);
  }

  // ---------- Шар-зображення ----------

  function addImageLayer(doc, image, opts) {
    const o = opts || {};
    const target = o.target === undefined ? 'color' : o.target;
    const channel = o.channel === undefined ? 'lum' : o.channel;
    checkTarget(target);
    if (CHANNELS.indexOf(channel) < 0) throw new Error('Невідомий канал: ' + channel);
    const w = image.width, h = image.height, size = doc.size;
    if (!(w > 0 && h > 0) || !image.data || image.data.length !== w * h * 4) {
      throw new Error('Розмір буфера не відповідає розмірам зображення ' + w + '×' + h);
    }
    const data = (w === size && h === size) ? new Uint8ClampedArray(image.data) : util.resampleRGBA(image.data, w, h, size, size);
    const imageId = freshId('img', id => id in doc.images);
    const layerId = freshId('lay', id => doc.layers.some(l => l.id === id));
    const layer = {
      id: layerId, type: 'image', name: o.name || 'Зображення', enabled: true, seed: 0,
      params: { imageId, target, channel, opacity: 1 }
    };
    doc.images[imageId] = data;
    doc.layers.push(layer);
    return layer;
  }

  // ---------- Збирання буферів для експорту ----------

  function buildDiffuse(doc) { return new Uint8ClampedArray(doc.maps.color); }

  function buildSPM(doc) {
    const n = doc.size * doc.size, spec = doc.maps.spec, team = doc.maps.team, out = new Uint8ClampedArray(n * 4);
    for (let i = 0, o = 0; i < n; i++, o += 4) { out[o] = spec[i]; out[o + 2] = team[i]; out[o + 3] = 255; }
    return out;
  }

  // Собель по висоті із загортанням, нормований на 8, тож dx, dy — похідна висоти на піксель.
  // n = normalize(-dx*s, -dy*s, 1). Імпортована NRM змішується за whiteout: normalize(a.xy + b.xy, a.z*b.z).
  // Обидва вектори беруться в закодованій конвенції (G = ±n.y), а whiteout лінійний по xy, тому окремо
  // перемикати знак для імпорту не потрібно: інверсія Y діє на весь ланцюжок однаково.
  function buildNRM(doc) {
    const size = doc.size, n = size * size, hgt = doc.maps.height;
    const k = doc.settings.nrmStrength / 8, sgn = doc.settings.invertY ? -1 : 1;
    const imp = doc.nrmImport;
    if (imp && imp.length !== n * 4) throw new Error('Імпортована NRM має розмір ' + imp.length + ' байт, очікується ' + n * 4);
    const out = new Uint8ClampedArray(n * 4);
    for (let y = 0; y < size; y++) {
      const rm = (y === 0 ? size - 1 : y - 1) * size, r0 = y * size, rp = (y === size - 1 ? 0 : y + 1) * size;
      for (let x = 0; x < size; x++) {
        const xm = x === 0 ? size - 1 : x - 1, xp = x === size - 1 ? 0 : x + 1;
        const a = hgt[rm + xm], b = hgt[rm + x], c = hgt[rm + xp];
        const d = hgt[r0 + xm], f = hgt[r0 + xp];
        const g = hgt[rp + xm], h = hgt[rp + x], i = hgt[rp + xp];
        const nx = -((c + 2 * f + i) - (a + 2 * d + g)) * k, ny = -((g + 2 * h + i) - (a + 2 * b + c)) * k;
        const inv = 1 / Math.sqrt(nx * nx + ny * ny + 1);
        let ex = nx * inv, ey = sgn * ny * inv, ez = inv;
        const p = (r0 + x) * 4;
        if (imp) {
          ex += imp[p] / 127.5 - 1; ey += imp[p + 1] / 127.5 - 1; ez *= imp[p + 2] / 127.5 - 1;
          const l2 = ex * ex + ey * ey + ez * ez;
          if (l2 < 1e-12) { ex = 0; ey = 0; ez = 1; } else { const s = 1 / Math.sqrt(l2); ex *= s; ey *= s; ez *= s; }
        }
        out[p] = (ex * 0.5 + 0.5) * 255; out[p + 1] = (ey * 0.5 + 0.5) * 255; out[p + 2] = (ez * 0.5 + 0.5) * 255; out[p + 3] = 255;
      }
    }
    return out;
  }

  // ---------- Вигляди ----------

  function viewGame(doc, teamRgb) {
    const n = doc.size * doc.size, size = doc.size;
    const T = teamRgb || TEAM_COLORS[0].rgb;
    const t0 = T[0] / 255, t1 = T[1] / 255, t2 = T[2] / 255;
    const col = doc.maps.color, team = doc.maps.team, spec = doc.maps.spec;
    const nrm = buildNRM(doc), sgn = doc.settings.invertY ? -1 : 1;
    const out = new Uint8ClampedArray(n * 4);
    const lx = LIGHT[0], ly = LIGHT[1], lz = LIGHT[2];
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        const i = y * size + x, o = i * 4;
        if (col[o + 3] < ALPHA_TEST) { // alpha-test: прозорі пікселі показуються шахівницею
          const v = (((x / CHECKER_CELL) | 0) ^ ((y / CHECKER_CELL) | 0)) & 1 ? CHECKER_DARK : CHECKER_LIGHT;
          out[o] = v; out[o + 1] = v; out[o + 2] = v; out[o + 3] = 255;
          continue;
        }
        // нормаль у просторі зображення (y донизу), BumpScale діє на xy
        let nx = (nrm[o] / 127.5 - 1) * BUMP_SCALE, ny = sgn * (nrm[o + 1] / 127.5 - 1) * BUMP_SCALE, nz = nrm[o + 2] / 127.5 - 1;
        const len = Math.sqrt(nx * nx + ny * ny + nz * nz), s = len > 1e-6 ? 1 / len : 0;
        nx *= s; ny *= s; nz = len > 1e-6 ? nz * s : 1;
        const ndl = nx * lx + ny * ly + nz * lz;
        const light = AMBIENT + (ndl > 0 ? ndl : 0);
        const refl = SPECULAR * (spec[i] / 255) * (0.6 + 0.4 * (nz > 0 ? nz : 0));
        const m = team[i] / 255;
        const c0 = col[o], c1 = col[o + 1], c2 = col[o + 2];
        let l0, l1, l2;
        if (m === 0) { l0 = TO_LINEAR[c0]; l1 = TO_LINEAR[c1]; l2 = TO_LINEAR[c2]; }
        else { // lerp(c, c*T*2, mask) у гамма-просторі, потім лінеаризація
          const g0 = c0 / 255, g1 = c1 / 255, g2 = c2 / 255;
          l0 = Math.pow(g0 + (g0 * t0 * 2 - g0) * m, GAMMA); l1 = Math.pow(g1 + (g1 * t1 * 2 - g1) * m, GAMMA); l2 = Math.pow(g2 + (g2 * t2 * 2 - g2) * m, GAMMA);
        }
        const v0 = l0 * light + refl, v1 = l1 * light + refl, v2 = l2 * light + refl;
        out[o] = TO_GAMMA[v0 >= 1 ? GAMMA_STEPS : (v0 * GAMMA_STEPS + 0.5) | 0];
        out[o + 1] = TO_GAMMA[v1 >= 1 ? GAMMA_STEPS : (v1 * GAMMA_STEPS + 0.5) | 0];
        out[o + 2] = TO_GAMMA[v2 >= 1 ? GAMMA_STEPS : (v2 * GAMMA_STEPS + 0.5) | 0];
        out[o + 3] = 255;
      }
    }
    return out;
  }

  // Прев'ю 2D для полотна. Читає doc.maps як є (rebuild не викликає).
  // 'color' — непрозорий RGB (прозорість дивіться у вигляді 'alpha'); 'game' — формула шейдера RA3.
  function viewRGBA(doc, view, teamRgb) {
    const n = doc.size * doc.size;
    switch (view) {
      case 'color': {
        const col = doc.maps.color, out = new Uint8ClampedArray(n * 4);
        for (let o = 0; o < n * 4; o += 4) { out[o] = col[o]; out[o + 1] = col[o + 1]; out[o + 2] = col[o + 2]; out[o + 3] = 255; }
        return out;
      }
      case 'alpha': {
        const col = doc.maps.color, out = new Uint8ClampedArray(n * 4);
        for (let i = 0, o = 0; i < n; i++, o += 4) { const v = col[o + 3]; out[o] = v; out[o + 1] = v; out[o + 2] = v; out[o + 3] = 255; }
        return out;
      }
      case 'height': {
        const hgt = doc.maps.height, out = new Uint8ClampedArray(n * 4);
        for (let i = 0, o = 0; i < n; i++, o += 4) { const v = hgt[i] * 255; out[o] = v; out[o + 1] = v; out[o + 2] = v; out[o + 3] = 255; }
        return out;
      }
      case 'nrm': return buildNRM(doc);
      case 'spm': return buildSPM(doc);
      case 'game': return viewGame(doc, teamRgb);
      default: throw new Error('Невідомий вигляд: ' + view);
    }
  }

  // ---------- Зміна розміру ----------

  // Ресемплить images і nrmImport (білінійно) та оверлеї (найближчий сусід), maps перевиділяє в базовому стані:
  // після виклику потрібен rebuild. Усе обчислюється наперед, тож помилка не залишає документ напівзміненим.
  function resizeDocument(doc, newSize) {
    checkSize(newSize);
    const old = doc.size;
    if (newSize === old) return;
    const images = {};
    for (const id of Object.keys(doc.images)) images[id] = util.resampleRGBA(doc.images[id], old, old, newSize, newSize);
    const nrmImport = doc.nrmImport ? util.resampleRGBA(doc.nrmImport, old, old, newSize, newSize) : null;
    const src = new Int32Array(newSize);
    for (let x = 0; x < newSize; x++) src[x] = Math.min(old - 1, Math.floor((x + 0.5) * old / newSize));
    const overlays = allocOverlays(newSize);
    for (const t of TARGETS) {
      const from = doc.overlays[t], to = overlays[t], isColor = t === 'color';
      for (let y = 0; y < newSize; y++) {
        const fr = src[y] * old, tr = y * newSize;
        for (let x = 0; x < newSize; x++) {
          const fi = fr + src[x], ti = tr + x;
          to.cov[ti] = from.cov[fi];
          if (isColor) { to.value[ti * 4] = from.value[fi * 4]; to.value[ti * 4 + 1] = from.value[fi * 4 + 1]; to.value[ti * 4 + 2] = from.value[fi * 4 + 2]; }
          else to.value[ti] = from.value[fi];
        }
      }
    }
    doc.size = newSize; doc.images = images; doc.nrmImport = nrmImport; doc.overlays = overlays; doc.maps = allocMaps(newSize);
  }

  // ---------- Імена файлів ----------

  function sanitizeName(s) {
    const clean = (s === null || s === undefined ? '' : String(s)).replace(/[^A-Za-z0-9_]/g, '');
    return clean || 'MySkin';
  }

  // Суфікс _NRM / _SPM без урахування регістру; шлях і розширення відкидаються.
  function classifyTextureName(fileName) {
    let s = fileName === null || fileName === undefined ? '' : String(fileName);
    s = s.slice(Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\')) + 1).replace(/\.[A-Za-z0-9]{1,5}$/, '');
    const m = /^(.*)_(nrm|spm)$/i.exec(s);
    return m ? { base: m[1], role: m[2].toLowerCase() } : { base: s, role: 'diffuse' };
  }

  const api = {
    TARGETS, TEAM_COLORS,
    createDocument, rebuild, applyBrush, cloneOverlay, restoreOverlay, addImageLayer,
    buildDiffuse, buildSPM, buildNRM, viewRGBA, resizeDocument, sanitizeName, classifyTextureName
  };
  RA3.maps = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
