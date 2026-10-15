(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');

  // Розміри в пікселях (шов, заклепка, подряпина) задано для текстури 512² і масштабуються з розміром документа,
  // а шум задано в UV, тож на 256 і на 2048 скін виглядає однаково. Усе тайлиться (гра семплить з Wrap):
  // шум через util.fbm з цілими частотами, геометрія з координатами по модулю size (size — степінь двійки,
  // тож загортання робиться маскою `& (size - 1)`).
  const REF = 512;
  const FACTION_PRESETS = { allied: ['#9fa9b0', '#4a5560'], soviet: ['#7a2e24', '#4b4a45'], empire: ['#d8d4c8', '#2f3236'] };
  const METAL = [0xc8, 0xc8, 0xc0]; // світлий метал під подряпинами

  // ---------- Каталог ----------

  const range = (key, label, min, max, step, def) => ({ key, label, kind: 'range', min, max, step, default: def });
  const color = (key, label, def) => ({ key, label, kind: 'color', default: def });
  const select = (key, label, options, def) => ({ key, label, kind: 'select', options: options.map(o => ({ value: o[0], label: o[1] })), default: def });

  const catalog = [
    { type: 'base', label: 'Основа фракції', params: [
      select('preset', 'Пресет', [['allied', 'Альянс'], ['soviet', 'СРСР'], ['empire', 'Імперія'], ['custom', 'Свої кольори']], 'allied'),
      color('color1', 'Основний колір', '#9fa9b0'),
      color('color2', 'Другий колір', '#4a5560'),
      range('noise', 'Шум відтінку', 0, 1, 0.01, 0.35),
      range('gloss', 'Блиск', 0, 255, 1, 30)] },
    { type: 'camo', label: 'Камуфляж', params: [
      select('pattern', 'Візерунок', [['blotch', 'Плями'], ['stripes', 'Смуги'], ['digital', 'Цифровий']], 'blotch'),
      color('color1', 'Колір 1', '#5d6448'),
      color('color2', 'Колір 2', '#33372c'),
      color('color3', 'Колір 3', '#8b815f'),
      range('scale', 'Масштаб', 0.25, 2, 0.05, 1),
      range('coverage', 'Покриття', 0, 1, 0.01, 0.6)] },
    { type: 'panels', label: 'Панелі', params: [
      range('countX', 'Панелей по горизонталі', 1, 16, 1, 3),
      range('countY', 'Панелей по вертикалі', 1, 16, 1, 3),
      range('jitter', 'Розкид', 0, 1, 0.01, 0.4),
      range('seam', 'Ширина шва, px', 1, 8, 0.5, 2.5),
      range('depth', 'Глибина шва', 0, 0.5, 0.01, 0.25)] },
    { type: 'rivets', label: 'Заклепки', params: [
      // «Уздовж швів» шукає шви в карті висоти (шар «Панелі» чи зображення); якщо швів немає, ставить сітку.
      select('mode', 'Розміщення', [['seams', 'Уздовж швів'], ['grid', 'Сіткою']], 'seams'),
      range('spacing', 'Крок, px', 6, 64, 1, 16),
      range('size', 'Розмір, px', 1, 6, 0.25, 2.5)] },
    { type: 'dirt', label: 'Бруд/пил', params: [
      range('amount', 'Сила', 0, 1, 0.01, 0.35),
      range('gradient', 'Градієнт знизу', 0, 1, 0.01, 0.5),
      range('scale', 'Масштаб плям', 0.25, 2, 0.05, 1),
      range('cavity', 'Бруд у швах', 0, 1, 0.01, 0.6),
      color('color', 'Колір', '#5b4c3a')] },
    { type: 'rust', label: 'Іржа', params: [
      range('amount', 'Кількість', 0, 1, 0.01, 0.15),
      range('size', 'Розмір плям', 0.1, 1, 0.01, 0.3),
      range('streaks', 'Патьоки', 0, 1, 0.01, 0.5),
      color('color', 'Колір', '#7b3f1d')] },
    { type: 'scratches', label: 'Подряпини', params: [
      range('count', 'Кількість', 0, 400, 1, 50),
      range('length', 'Довжина, px', 4, 200, 1, 24),
      range('width', 'Ширина, px', 0.5, 4, 0.25, 1)] },
    { type: 'team', label: 'Колір гравця', params: [
      select('mode', 'Режим', [['stripes', 'Смуги'], ['rect', 'Прямокутник'], ['luma', 'За яскравістю']], 'stripes'),
      range('x', 'Ліворуч (X)', 0, 1, 0.01, 0),
      range('y', 'Згори (Y)', 0, 1, 0.01, 0.4),
      range('w', 'Ширина', 0, 1, 0.01, 1),
      range('h', 'Висота', 0, 1, 0.01, 0.2),
      range('angle', 'Кут смуг, °', -90, 90, 1, 0),
      range('count', 'Кількість смуг', 1, 20, 1, 2),
      range('strength', 'Сила', 0, 1, 0.01, 1),
      range('lumaMin', 'Поріг яскравості', 0, 1, 0.01, 0.55)] },
    { type: 'image', label: 'Зображення', params: [
      // Варіанти для imageId застосунок бере з doc.images (source: 'images').
      Object.assign(select('imageId', 'Файл', [], ''), { source: 'images' }),
      select('target', 'Куди', [['color', 'Колір'], ['alpha', 'Прозорість'], ['height', 'Висота'], ['spec', 'Блиск'], ['team', 'Колір гравця']], 'color'),
      select('channel', 'Канал', [['lum', 'Яскравість'], ['r', 'Червоний (R)'], ['g', 'Зелений (G)'], ['b', 'Синій (B)'], ['a', 'Альфа (A)']], 'lum'),
      range('opacity', 'Непрозорість', 0, 1, 0.01, 1)] }
  ];
  const byType = {};
  for (const c of catalog) byType[c.type] = c;

  function findDef(type) {
    const def = Object.prototype.hasOwnProperty.call(byType, type) ? byType[type] : null;
    if (!def) throw new Error('Невідомий шар: ' + type);
    return def;
  }

  let idCounter = 0;
  function freshId() {
    return 'lay' + Date.now().toString(36) + (idCounter++).toString(36) + Math.floor(Math.random() * 1679616).toString(36);
  }

  function defaultLayer(type, seed) {
    const def = findDef(type), params = {};
    for (const p of def.params) params[p.key] = p.default;
    return {
      id: freshId(), type, name: def.label, enabled: true,
      seed: seed === undefined ? Math.floor(Math.random() * 4294967296) : seed,
      params
    };
  }

  // Параметри шару, доповнені типовими: числа обмежено діапазоном, невідомі варіанти й кольори замінено на типові.
  // Генератори читають лише ці ключі, тож каталог описує все, що впливає на результат.
  function resolveParams(def, given) {
    const src = given || {}, out = {};
    for (const p of def.params) {
      const v = src[p.key];
      let r = p.default;
      if (p.kind === 'range') {
        const x = typeof v === 'number' ? v : (typeof v === 'string' && v.trim() !== '' ? Number(v) : NaN);
        if (Number.isFinite(x)) r = util.clamp(x, p.min, p.max);
      } else if (p.kind === 'color') {
        if (typeof v === 'string' && /^#[0-9a-fA-F]{6}$/.test(v.trim())) r = v.trim();
      } else if (p.kind === 'select') {
        if (p.source !== undefined) { if (typeof v === 'string') r = v; } else if (p.options.some(o => o.value === v)) r = v;
      } else if (typeof v === 'boolean') r = v;
      out[p.key] = r;
    }
    return out;
  }

  // ---------- Шум і допоміжне ----------

  const nextSeed = rng => (rng() * 4294967296) | 0;
  const mod = (a, n) => ((a % n) + n) % n;

  function mix3(col, o, r, g, b, t) {
    col[o] = col[o] + (r - col[o]) * t; col[o + 1] = col[o + 1] + (g - col[o + 1]) * t; col[o + 2] = col[o + 2] + (b - col[o + 2]) * t;
  }

  function scale3(col, o, f) { col[o] *= f; col[o + 1] *= f; col[o + 2] *= f; }

  // Цілочисельний хеш (seed, a, b) -> [0,1).
  function hash3(seed, a, b) {
    let h = (seed | 0) ^ Math.imul(a | 0, 0x27D4EB2F) ^ Math.imul((b | 0) + 0x165667B1, 0x9E3779B1);
    h = Math.imul(h ^ (h >>> 15), 0x85EBCA6B); h = Math.imul(h ^ (h >>> 13), 0xC2B2AE35); h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }

  // Поле fn(u, v) розміром size². Рахується на сітці res ≤ size (не більше 1024), де найдрібніша клітинка шуму
  // (cells на текстуру) займає щонайменше 3 px, і розтягується сплайном із загортанням: на 2048² це в десятки
  // разів швидше, а на вигляд те саме. rank = true: значення замінено рангом (рівномірно в [0,1]).
  function sampleField(size, cells, fn, rank) {
    let res = 64;
    while (res < cells * 3 && res < 1024) res *= 2;
    if (res > size) res = size;
    const lo = new Float32Array(res * res);
    for (let y = 0, i = 0; y < res; y++) { const v = y / res; for (let x = 0; x < res; x++, i++) lo[i] = fn(x / res, v); }
    if (rank) rankNormalize(lo);
    return res === size ? lo : upsample(lo, res, size);
  }

  // Тайловий fbm; частота округлюється до цілого ≥ 2 (інакше шум не тайлиться).
  function noiseField(seed, size, freq, octaves, rank) {
    const f = Math.max(2, Math.round(freq));
    return sampleField(size, f << (octaves - 1), (u, v) => util.fbm(seed, u, v, f, octaves), rank);
  }

  // Анізотропний value-noise (cx клітинок по X, cy по Y, обидва цілі) — вертикальні патьоки, що тайляться.
  function streakNoise(seed, u, v, cx, cy) {
    const x = u * cx, y = v * cy, x0 = Math.floor(x), y0 = Math.floor(y);
    let tx = x - x0, ty = y - y0;
    tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
    const xa = mod(x0, cx), xb = mod(x0 + 1, cx), ya = mod(y0, cy), yb = mod(y0 + 1, cy);
    const a = hash3(seed, xa, ya), b = hash3(seed, xb, ya), c = hash3(seed, xa, yb), d = hash3(seed, xb, yb);
    const top = a + (b - a) * tx, bot = c + (d - c) * tx;
    return top + (bot - top) * ty;
  }

  // Заміна значень на їхній ранг: результат рівномірно розподілений у [0,1], тож поріг 1−a покриває частку a площі.
  function rankNormalize(a) {
    const n = a.length, BINS = 4096;
    let lo = Infinity, hi = -Infinity;
    for (let i = 0; i < n; i++) { const v = a[i]; if (v < lo) lo = v; if (v > hi) hi = v; }
    if (!(hi > lo)) { a.fill(0.5); return; }
    const sc = BINS / (hi - lo), cdf = new Float64Array(BINS + 1);
    for (let i = 0; i < n; i++) { const b = Math.min(BINS - 1, ((a[i] - lo) * sc) | 0); cdf[b + 1]++; }
    for (let b = 0; b < BINS; b++) cdf[b + 1] = cdf[b] + cdf[b + 1] / n;
    for (let i = 0; i < n; i++) {
      const t = (a[i] - lo) * sc, b = Math.min(BINS - 1, t | 0);
      a[i] = cdf[b] + (cdf[b + 1] - cdf[b]) * (t - b);
    }
  }

  // Розтягування res² → size² сплайном Catmull-Rom (окремо по рядках і по стовпцях) із загортанням. Білінійне тут
  // не годиться: після порогу (плями, іржа) на великих текстурах проступає сітка низької роздільності.
  function upsample(lo, res, size) {
    const k = res / size, W = res - 1, idx = new Int32Array(size * 4), wt = new Float32Array(size * 4);
    for (let x = 0; x < size; x++) {
      const t = x * k, i = Math.floor(t), f = t - i, f2 = f * f, f3 = f2 * f, q = x * 4;
      idx[q] = (i - 1) & W; idx[q + 1] = i & W; idx[q + 2] = (i + 1) & W; idx[q + 3] = (i + 2) & W;
      wt[q] = 0.5 * (-f3 + 2 * f2 - f); wt[q + 1] = 0.5 * (3 * f3 - 5 * f2 + 2); wt[q + 2] = 0.5 * (-3 * f3 + 4 * f2 + f); wt[q + 3] = 0.5 * (f3 - f2);
    }
    const tmp = new Float32Array(res * size), out = new Float32Array(size * size);
    for (let r = 0; r < res; r++) {
      const src = r * res, dst = r * size;
      for (let x = 0, q = 0; x < size; x++, q += 4) {
        tmp[dst + x] = wt[q] * lo[src + idx[q]] + wt[q + 1] * lo[src + idx[q + 1]] + wt[q + 2] * lo[src + idx[q + 2]] + wt[q + 3] * lo[src + idx[q + 3]];
      }
    }
    for (let y = 0, q = 0; y < size; y++, q += 4) {
      const a = idx[q] * size, b = idx[q + 1] * size, c = idx[q + 2] * size, d = idx[q + 3] * size, dst = y * size;
      const wa = wt[q], wb = wt[q + 1], wc = wt[q + 2], wd = wt[q + 3];
      for (let x = 0; x < size; x++) out[dst + x] = wa * tmp[a + x] + wb * tmp[b + x] + wc * tmp[c + x] + wd * tmp[d + x];
    }
    return out;
  }

  // Box-розмиття з загортанням: горизонтальний прохід ковзною сумою по рядку, вертикальний — ковзною сумою цілих рядків.
  function blurWrap(src, size, r) {
    const n = size * size, W = size - 1, tmp = new Float32Array(n), out = new Float32Array(n), inv = 1 / (2 * r + 1);
    for (let y = 0; y < size; y++) {
      const row = y * size; let s = 0;
      for (let d = -r; d <= r; d++) s += src[row + (d & W)];
      for (let x = 0; x < size; x++) { tmp[row + x] = s * inv; s += src[row + ((x + r + 1) & W)] - src[row + ((x - r) & W)]; }
    }
    const acc = new Float64Array(size);
    for (let d = -r; d <= r; d++) { const row = (d & W) * size; for (let x = 0; x < size; x++) acc[x] += tmp[row + x]; }
    for (let y = 0; y < size; y++) {
      const dst = y * size, add = ((y + r + 1) & W) * size, sub = ((y - r) & W) * size;
      for (let x = 0; x < size; x++) { out[dst + x] = acc[x] * inv; acc[x] += tmp[add + x] - tmp[sub + x]; }
    }
    return out;
  }

  // Западини (шви, підніжжя заклепок): наскільки піксель нижчий за своє розмите оточення, 0..1.
  function cavityMap(height, size) {
    const r = Math.max(1, Math.round(3 * size / REF)), b = blurWrap(height, size, r);
    for (let i = 0; i < b.length; i++) { const c = (b[i] - height[i]) * 8; b[i] = c > 0 ? (c < 1 ? c : 1) : 0; }
    return b;
  }

  // Періодичний профіль «бруд знизу»: 0 угорі, 1 біля низу; у нижніх 6% плавно вертається до значення верхнього
  // краю, тож при загортанні шва немає.
  function bottomProfile(v) {
    const up = util.smoothstep(0.15, 0.94, v);
    return v <= 0.94 ? up : up * (1 - util.smoothstep(0.94, 1, v));
  }

  // ---------- Основа фракції ----------

  function applyBase(p, seed, maps, size) {
    const pair = p.preset !== 'custom' && FACTION_PRESETS[p.preset] ? FACTION_PRESETS[p.preset] : [p.color1, p.color2];
    const c1 = util.hexToRgb(pair[0]), c2 = util.hexToRgb(pair[1]);
    const n = size * size, col = maps.color, amt = p.noise;
    maps.spec.fill(Math.round(p.gloss));
    if (!(amt > 0)) {
      for (let o = 0; o < n * 4; o += 4) { col[o] = c1[0]; col[o + 1] = c1[1]; col[o + 2] = c1[2]; }
      return;
    }
    const rng = util.mulberry32(seed), sBig = nextSeed(rng), sMid = nextSeed(rng), sGrain = nextSeed(rng);
    // Частка другого кольору: великі плями відтінку + дрібніші, обидва поля в одному проході.
    const mixT = sampleField(size, 48, (u, v) => 0.75 * util.smoothstep(0.42, 0.68, util.fbm(sBig, u, v, 3, 5)) +
      0.25 * util.smoothstep(0.5, 0.7, util.fbm(sMid, u, v, 10, 3)), false);
    const grain = noiseField(sGrain, size, 96, 2, false); // зерно фарби
    const d0 = c2[0] - c1[0], d1 = c2[1] - c1[1], d2 = c2[2] - c1[2];
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const t = amt * mixT[i], sh = 1 + amt * 0.08 * (grain[i] - 0.5);
      col[o] = (c1[0] + d0 * t) * sh; col[o + 1] = (c1[1] + d1 * t) * sh; col[o + 2] = (c1[2] + d2 * t) * sh;
    }
  }

  // ---------- Камуфляж ----------

  function applyCamo(p, seed, maps, size) {
    const cov = p.coverage;
    if (!(cov > 0)) return;
    const cols = [util.hexToRgb(p.color1), util.hexToRgb(p.color2), util.hexToRgb(p.color3)];
    const rng = util.mulberry32(seed), n = size * size, col = maps.color;
    const freq = Math.max(2, Math.round(4 / p.scale));
    const seeds = [nextSeed(rng), nextSeed(rng), nextSeed(rng)], sDetail = nextSeed(rng), sCell = nextSeed(rng);
    const detail = noiseField(sDetail, size, freq * 8, 2, false); // рвані краї й варіація фарби
    // Кожен колір має своє незалежне поле з часткою share, разом вони покривають cov площі.
    const share = 1 - Math.pow(1 - cov, 1 / 3), thr = 1 - share;
    const masks = [new Float32Array(n), new Float32Array(n), new Float32Array(n)];

    if (p.pattern === 'stripes') {
      // «Тигрові» смуги: хвиляста фаза a·u + b·v (цілі a, b, тож тайлиться), у кожному періоді по смузі кожного
      // кольору; товщина кожної смуги змінюється вздовж неї аж до розривів. Середня частка всіх смуг ≈ cov.
      const warp = noiseField(sCell, size, Math.max(2, Math.round(freq * 0.75)), 3, false);
      const thick = seeds.map(s => noiseField(s, size, freq * 2, 3, true));
      const a = Math.round(freq / 4), b = freq, fw = 1.5 * (a + b + 4) / size, d0 = cov / 3 * 2;
      for (let y = 0, i = 0; y < size; y++) for (let x = 0; x < size; x++, i++) {
        const ph = (a * x + b * y) / size + 1.6 * (warp[i] - 0.5) + 0.05 * (detail[i] - 0.5);
        for (let k = 0; k < 3; k++) {
          const d = d0 * util.smoothstep(0.15, 0.85, thick[k][i]);
          let f = ph - k / 3 - d / 2; f -= Math.floor(f);                  // від центру смуги k, у [0, 1)
          const inside = d / 2 - (f < 0.5 ? f : 1 - f);                    // > 0 усередині смуги
          // AA краю; смуга, тонша за піксель, лише частково фарбує піксель (без «волосин» там, де вона зникає)
          masks[k][i] = util.clamp(Math.min(inside / fw + 0.5, d / fw), 0, 1);
        }
      }
    } else {
      const fields = seeds.map(s => noiseField(s, size, freq, 4, true));
      if (p.pattern === 'digital') {
        // Квадратні «пікселі»: N клітинок на текстуру (степінь двійки, тож клітинки рівні), значення з центру клітинки.
        let N = 1;
        while (N * 2 <= REF / (8 * p.scale) * 1.42) N *= 2;
        N = Math.min(N, size);
        const cell = size / N;
        for (let y = 0, i = 0; y < size; y++) {
          const cy = Math.floor(y / cell), sy = Math.floor((cy + 0.5) * cell);
          for (let x = 0; x < size; x++, i++) {
            const cx = Math.floor(x / cell), si = sy * size + Math.floor((cx + 0.5) * cell);
            for (let k = 0; k < 3; k++) masks[k][i] = fields[k][si] + 0.2 * (hash3(sCell + k, cx, cy) - 0.5) >= thr ? 1 : 0;
          }
        }
      } else {
        const e = 1.5 * freq / size;
        for (let i = 0; i < n; i++) {
          const dv = 0.12 * (detail[i] - 0.5);
          for (let k = 0; k < 3; k++) masks[k][i] = util.smoothstep(thr - e, thr + e, fields[k][i] + dv);
        }
      }
    }
    // Колір 3 знизу, колір 1 зверху; легка варіація фарби.
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const sh = 1 + 0.06 * (detail[i] - 0.5);
      for (let k = 2; k >= 0; k--) {
        const m = masks[k][i];
        if (m > 0) { const c = cols[k]; mix3(col, o, c[0] * sh, c[1] * sh, c[2] * sh, m); }
      }
    }
  }

  // ---------- Панелі ----------

  // Розкладка: вертикальні шви на всю висоту, у кожній колонці свої горизонтальні шви (зі зсувом, як цегла),
  // іноді панель ділиться ще одним вертикальним швом. Відрізки в пікселях, без загортання (воно при растеризації).
  function panelLayout(p, rng, size) {
    const nx = Math.round(p.countX), ny = Math.round(p.countY), jit = p.jitter, segs = [];
    const offX = rng(), offY = rng(), xs = [];
    for (let i = 0; i < nx; i++) xs.push((i + offX + jit * 0.6 * (rng() - 0.5)) * size / nx);
    xs.push(xs[0] + size);
    for (let j = 0; j < nx; j++) {
      segs.push({ vertical: true, c: xs[j], a0: 0, a1: size, full: true });
      const stagger = offY + jit * rng(), ys = [];
      for (let r = 0; r < ny; r++) ys.push((r + stagger + jit * 0.5 * (rng() - 0.5)) * size / ny);
      ys.push(ys[0] + size);
      for (let r = 0; r < ny; r++) {
        segs.push({ vertical: false, c: ys[r], a0: xs[j], a1: xs[j + 1], full: nx === 1 });
        if (rng() < jit * 0.35) {
          const sx = xs[j] + (xs[j + 1] - xs[j]) * (0.3 + 0.4 * rng());
          segs.push({ vertical: true, c: sx, a0: ys[r], a1: ys[r + 1], full: false });
        }
      }
    }
    return segs;
  }

  // Мінімальна відстань від пікселя (x, y) до відрізків (лише в смузі R довкола них), із загортанням.
  function seamDistance(segs, size, R) {
    const dist = new Float32Array(size * size).fill(Infinity), W = size - 1;
    for (const s of segs) {
      const c0 = Math.ceil(s.c - R), c1 = Math.floor(s.c + R);
      const a0 = s.full ? 0 : Math.ceil(s.a0 - R), a1 = s.full ? size - 1 : Math.floor(s.a1 + R);
      for (let a = a0; a <= a1; a++) {
        const da = s.full ? 0 : (a < s.a0 ? s.a0 - a : (a > s.a1 ? a - s.a1 : 0));
        if (da >= R) continue;
        const am = a & W;
        for (let c = c0; c <= c1; c++) {
          const dc = c - s.c, d = Math.sqrt(dc * dc + da * da);
          const i = s.vertical ? am * size + (c & W) : (c & W) * size + am;
          if (d < dist[i]) dist[i] = d;
        }
      }
    }
    return dist;
  }

  function applyPanels(p, seed, maps, size) {
    const rng = util.mulberry32(seed), k = size / REF;
    const w = Math.max(1, p.seam * k), half = w / 2, bevel = Math.max(1, w * 0.6);
    const g0 = half - 0.5, reach = half + bevel;          // щілина до g0, скіс до reach, далі панель не змінюється
    const aoR = reach + Math.max(1.5, 4 * k);             // затінення фарби біля шва
    const mid = (g0 + reach) / 2, bw = (reach - g0) / 2;  // середина скосу: тут блищить край
    const dist = seamDistance(panelLayout(p, rng, size), size, aoR);
    const n = size * size, col = maps.color, hgt = maps.height, spec = maps.spec, depth = p.depth;
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const d = dist[i];
      if (!(d < aoR)) continue;
      const gr = 1 - util.smoothstep(g0, reach, d);
      if (gr > 0) hgt[i] = util.clamp(hgt[i] - depth * gr, 0, 1);
      const edge = Math.max(0, 1 - Math.abs(d - mid) / bw);
      if (edge > 0) spec[i] = spec[i] + 70 * edge;
      const ao = 1 - util.smoothstep(reach, aoR, d);
      scale3(col, o, (1 - 0.6 * gr) * (1 - 0.14 * ao) * (1 + 0.1 * edge));
    }
  }

  // ---------- Заклепки ----------

  const GROOVE_MIN = 0.025; // мінімальна глибина, з якої западина вважається швом

  // Ковзний максимум (sign = 1) чи мінімум (sign = −1) у вікні [i−hw, i+hw] по колу (n — степінь двійки), O(n).
  function slide(src, n, hw, sign, out, q) {
    let head = 0, tail = 0;
    const W = n - 1;
    for (let k = -hw; k < n + hw; k++) {
      const v = sign * src[k & W];
      while (tail > head && sign * src[q[tail - 1] & W] <= v) tail--;
      q[tail++] = k;
      if (q[head] < k - 2 * hw) head++;
      if (k >= hw) out[k - hw] = src[q[head] & W];
    }
  }

  // Шви на одному рядку/стовпці висоти: морфологічне закриття (max, потім min у вікні) заповнює вузькі западини,
  // різниця з висотою — їхня глибина. Повертає [перший, останній піксель, найбільша глибина]
  // (індекси без загортання, можуть виходити за size).
  function findGrooves(line, size, hw, buf) {
    const out = [];
    if (hw < 1) return out;
    slide(line, size, hw, 1, buf.dil, buf.q);
    slide(buf.dil, size, hw, -1, buf.clo, buf.q);
    const clo = buf.clo, W = size - 1;
    let start = -1;
    for (let i = 0; i < size; i++) if (clo[i] - line[i] <= GROOVE_MIN) { start = i; break; }
    if (start < 0) return out;
    let runStart = -1, deepest = 0;
    for (let t = 1; t <= size; t++) {
      const i = start + t, depth = clo[i & W] - line[i & W];
      if (depth > GROOVE_MIN && t < size) {
        if (runStart < 0) { runStart = i; deepest = 0; }
        if (depth > deepest) deepest = depth;
      } else if (runStart >= 0) {
        if (i - runStart <= 2 * hw && deepest > 2 * GROOVE_MIN) out.push([runStart, i - 1, deepest]);
        runStart = -1;
      }
    }
    return out;
  }

  // Центри заклепок обабіч знайдених швів: сканування рядків (вертикальні шви) і стовпців (горизонтальні) з кроком pitch.
  function seamRivets(hgt, size, pitch, r) {
    const k = size / REF, hw = Math.min(Math.ceil(13 * k), size >> 2), gap = r + Math.max(1, 0.9 * r), W = size - 1;
    const lines = Math.max(1, Math.round(size / pitch)), minD2 = (0.7 * pitch) * (0.7 * pitch), pts = [];
    const line = new Float32Array(size);
    const buf = { dil: new Float32Array(size), clo: new Float32Array(size), q: new Int32Array(size + 2 * hw + 1) };
    // Заклепка має стояти на рівній панелі: увесь її диск (r+1) не нижчий за найвищу точку довкола (r+3) мінус допуск,
    // інакше вона лізе на скіс чи в сусідній шов.
    const R1 = r + 1, R2 = r + 3, span = Math.ceil(R2);
    const flat = (cx, cy, tol) => {
      const x0 = Math.round(cx), y0 = Math.round(cy);
      let mx = -Infinity, mn = Infinity;
      for (let dy = -span; dy <= span; dy++) {
        const row = ((y0 + dy) & W) * size;
        for (let dx = -span; dx <= span; dx++) {
          const ex = x0 + dx - cx, ey = y0 + dy - cy, d2 = ex * ex + ey * ey;
          if (d2 > R2 * R2) continue;
          const v = hgt[row + ((x0 + dx) & W)];
          if (v > mx) mx = v;
          if (d2 <= R1 * R1 && v < mn) mn = v;
        }
      }
      return mn >= mx - tol;
    };
    const free = (cx, cy) => {
      for (const q of pts) {
        let dx = Math.abs(q[0] - cx), dy = Math.abs(q[1] - cy);
        if (dx > size / 2) dx = size - dx;
        if (dy > size / 2) dy = size - dy;
        if (dx * dx + dy * dy < minD2) return false;
      }
      return true;
    };
    for (let axis = 0; axis < 2; axis++) {
      for (let t = 0; t < lines; t++) {
        const fixed = Math.floor((t + 0.5) * size / lines);
        for (let a = 0; a < size; a++) line[a] = axis === 0 ? hgt[fixed * size + a] : hgt[a * size + fixed];
        for (const [s, e, deepest] of findGrooves(line, size, hw, buf)) {
          const tol = Math.max(GROOVE_MIN, 0.3 * deepest);
          for (const c of [s - 0.5 - gap, e + 0.5 + gap]) {
            const cx = mod(axis === 0 ? c : fixed, size), cy = mod(axis === 0 ? fixed : c, size);
            if (flat(cx, cy, tol) && free(cx, cy)) pts.push([cx, cy]);
          }
        }
      }
    }
    return pts;
  }

  function gridRivets(size, spacing) {
    const cnt = Math.max(1, Math.round(REF / spacing)), step = size / cnt, pts = [];
    for (let j = 0; j < cnt; j++) for (let i = 0; i < cnt; i++) pts.push([(i + 0.5) * step, (j + 0.5) * step]);
    return pts;
  }

  // Купол заклепки: висота й блиск угору, фарба трохи світліша на маківці й темніша в облямівці.
  function stampRivet(maps, size, cx, cy, r, amp) {
    const ring = r + Math.max(1, 0.6 * r), col = maps.color, hgt = maps.height, spec = maps.spec, W = size - 1;
    const x0 = Math.ceil(cx - ring), x1 = Math.floor(cx + ring), y0 = Math.ceil(cy - ring), y1 = Math.floor(cy + ring);
    for (let py = y0; py <= y1; py++) {
      const dy = py - cy, row = (py & W) * size;
      for (let px = x0; px <= x1; px++) {
        const dx = px - cx, d = Math.sqrt(dx * dx + dy * dy);
        if (d >= ring) continue;
        const i = row + (px & W);
        if (d < r) {
          const t = d / r, dome = Math.sqrt(1 - t * t);
          hgt[i] = Math.min(1, hgt[i] + amp * dome);
          spec[i] = spec[i] + 60 * dome;
          scale3(col, i * 4, 0.86 + 0.2 * dome);
        } else {
          scale3(col, i * 4, 1 - 0.18 * (1 - util.smoothstep(r, ring, d)));
        }
      }
    }
  }

  function applyRivets(p, seed, maps, size) {
    const rng = util.mulberry32(seed), k = size / REF, r = Math.max(1.2, p.size * k);
    let pts = p.mode === 'seams' ? seamRivets(maps.height, size, Math.max(2, p.spacing * k), r) : [];
    if (pts.length === 0) pts = gridRivets(size, p.spacing); // без швів «уздовж швів» стає сіткою
    for (const [cx, cy] of pts) {
      const missing = rng() < 0.08, rr = r * (0.9 + 0.2 * rng()), amp = 0.15 * (0.8 + 0.4 * rng());
      if (!missing) stampRivet(maps, size, cx, cy, rr, amp);
    }
  }

  // ---------- Бруд/пил ----------

  function applyDirt(p, seed, maps, size) {
    const amt = p.amount;
    if (!(amt > 0)) return;
    const dc = util.hexToRgb(p.color), rng = util.mulberry32(seed), n = size * size, col = maps.color, spec = maps.spec;
    const freq = Math.max(2, Math.round(5 / p.scale));
    const big = noiseField(nextSeed(rng), size, freq, 5, true);
    const detail = noiseField(nextSeed(rng), size, freq * 8, 2, false);
    const sA = nextSeed(rng), sB = nextSeed(rng);
    // Патьоки: вузькі вертикальні смуги двох ширин; у 4-му степені лишаються рідкими.
    const streak = sampleField(size, 128, (u, v) => {
      const s = 0.6 * streakNoise(sA, u, v, 128, 5) + 0.4 * streakNoise(sB, u, v, 40, 3);
      return s * s * s * s;
    }, false);
    const cav = p.cavity > 0 ? cavityMap(maps.height, size) : null;
    const grad = p.gradient, cavK = p.cavity * 0.6, lo = 1 - amt * 1.25, hi = lo + 0.45;
    for (let y = 0, i = 0; y < size; y++) {
      const gp = grad * 0.5 * bottomProfile((y + 0.5) / size);
      for (let x = 0; x < size; x++, i++) {
        const b = big[i];
        let P = b * (1 - 0.5 * grad) + gp + 0.15 * (detail[i] - 0.5) + 0.18 * streak[i] * (0.4 + b);
        if (cav) P += cavK * cav[i];
        const m = util.smoothstep(lo, hi, P);
        if (m <= 0) continue;
        const sh = 0.8 + 0.4 * detail[i];
        mix3(col, i * 4, dc[0] * sh, dc[1] * sh, dc[2] * sh, 0.8 * m);
        spec[i] = spec[i] * (1 - 0.85 * m);
      }
    }
  }

  // ---------- Іржа ----------

  function applyRust(p, seed, maps, size) {
    const amt = p.amount;
    if (!(amt > 0)) return;
    const rc = util.hexToRgb(p.color), rng = util.mulberry32(seed), k = size / REF, n = size * size;
    const col = maps.color, hgt = maps.height, spec = maps.spec, team = maps.team;
    const freq = Math.max(2, Math.round(3 + (1 - p.size) * 13));
    const big = noiseField(nextSeed(rng), size, freq, 4, true);
    const detail = noiseField(nextSeed(rng), size, freq * 4, 2, false);
    const speckle = noiseField(nextSeed(rng), size, 128, 1, false);
    const sRough = nextSeed(rng), sCol = nextSeed(rng);
    const cav = cavityMap(hgt, size);
    // Частка площі під іржею росте повільніше за повзунок: 0.15 → ~8%, 1 → уся поверхня.
    const thr = 1 - Math.pow(amt, 1.3), mask = new Float32Array(n);
    const dark = [rc[0] * 0.5, rc[1] * 0.5, rc[2] * 0.5], dl = [rc[0] * 0.62, rc[1] * 0.62, rc[2] * 0.62];
    for (let y = 0, i = 0; y < size; y++) for (let x = 0; x < size; x++, i++) {
      const P = big[i] + 0.2 * (detail[i] - 0.5) + 0.45 * cav[i];
      const core = util.smoothstep(thr + 0.02, thr + 0.1, P);
      const speck = util.smoothstep(thr - 0.07, thr + 0.02, P) * util.smoothstep(0.56, 0.66, speckle[i]); // крапки довкола плям
      const m = core > speck ? core : speck;
      const halo = util.smoothstep(thr - 0.12, thr, P) * (1 - m); // фарба довкола плями буріє й тьмяніє
      const o = i * 4;
      if (halo > 0) { mix3(col, o, dark[0], dark[1], dark[2], 0.3 * halo); spec[i] = spec[i] * (1 - 0.5 * halo); }
      if (m <= 0) continue;
      mask[i] = m;
      const t = util.smoothstep(0.35, 0.75, detail[i]), gr = 0.9 + 0.2 * hash3(sRough, x, y); // зерно в 1 тексель
      mix3(col, o, (dark[0] + dl[0] * t) * gr, (dark[1] + dl[1] * t) * gr, (dark[2] + dl[2] * t) * gr, m);
      const rough = util.fbm(sRough, x / size, y / size, 128, 2);
      hgt[i] = util.clamp(hgt[i] + m * (0.015 + 0.1 * (rough - 0.5)), 0, 1);
      spec[i] = spec[i] * (1 - m);
      team[i] = team[i] * (1 - m);
    }
    // Патьоки: іржа стікає донизу; довжина й сила залежать від стовпця. Ідемо рядками згори вниз з «запасом» для
    // кожного стовпця; два проходи по висоті загортають низ на верх.
    const sAmt = p.streaks;
    if (!(sAmt > 0)) return;
    const sr = rc[0] * 0.75, sg = rc[1] * 0.75, sb = rc[2] * 0.75;
    const decay = new Float32Array(size), str = new Float32Array(size), carry = new Float32Array(size);
    for (let x = 0; x < size; x++) {
      const u = x / size, len = sAmt * 70 * k * (0.2 + 1.6 * streakNoise(sCol, u, 0, 96, 1));
      decay[x] = Math.exp(-1 / Math.max(1, len));
      str[x] = sAmt * util.smoothstep(0.25, 0.8, streakNoise(sCol + 1, u, 0, 64, 1));
    }
    for (let pass = 0; pass < 2; pass++) for (let y = 0; y < size; y++) {
      const row = y * size;
      for (let x = 0; x < size; x++) {
        const i = row + x, m = mask[i], c = Math.max(m * 0.8, carry[x] * decay[x]);
        carry[x] = c;
        if (pass === 0) continue;
        const sv = c * (1 - m) * str[x];
        if (sv > 0.004) { mix3(col, i * 4, sr, sg, sb, 0.5 * sv); spec[i] = spec[i] * (1 - 0.6 * sv); }
      }
    }
  }

  // ---------- Подряпини ----------

  // Гладкий 1D value-noise (аргумент — довжина вздовж подряпини), для розривів.
  function noise1D(seed, x) {
    const i = Math.floor(x), f = x - i, t = f * f * (3 - 2 * f), a = hash3(seed, i, 7), b = hash3(seed, i + 1, 7);
    return a + (b - a) * t;
  }

  // Подряпина — трохи зігнута лінія зі звуженими кінцями й розривами; маска max-ом, AA за відстанню до відрізка.
  function drawScratch(mask, size, x0, y0, ang, len, wid, bend, inten, seed) {
    const dx = Math.cos(ang), dy = Math.sin(ang), nx = -dy, ny = dx, W = size - 1;
    const steps = Math.max(2, Math.ceil(len / 4));
    const ptx = t => x0 + dx * len * t + nx * bend * 4 * t * (1 - t), pty = t => y0 + dy * len * t + ny * bend * 4 * t * (1 - t);
    let ax = ptx(0), ay = pty(0);
    for (let s = 0; s < steps; s++) {
      const t1 = (s + 1) / steps, tm = (s + 0.5) / steps, bx = ptx(t1), by = pty(t1);
      const hw = 0.5 * wid * Math.sqrt(Math.sin(Math.PI * tm)), v = inten * util.smoothstep(0.2, 0.45, noise1D(seed, tm * len / 10));
      if (v > 0 && hw > 0.05) {
        const ex = bx - ax, ey = by - ay, l2 = ex * ex + ey * ey || 1, R = hw + 1;
        const xa = Math.floor(Math.min(ax, bx) - R), xb = Math.ceil(Math.max(ax, bx) + R);
        const ya = Math.floor(Math.min(ay, by) - R), yb = Math.ceil(Math.max(ay, by) + R);
        for (let py = ya; py <= yb; py++) {
          const row = (py & W) * size;
          for (let px = xa; px <= xb; px++) {
            const qx = px - ax, qy = py - ay, t = util.clamp((qx * ex + qy * ey) / l2, 0, 1);
            const ux = qx - ex * t, uy = qy - ey * t, c = util.clamp(hw + 0.5 - Math.sqrt(ux * ux + uy * uy), 0, 1) * v;
            if (c > 0) { const i = row + (px & W); if (c > mask[i]) mask[i] = c; }
          }
        }
      }
      ax = bx; ay = by;
    }
  }

  function applyScratches(p, seed, maps, size) {
    const count = Math.round(p.count);
    if (count <= 0) return;
    const rng = util.mulberry32(seed), k = size / REF, n = size * size;
    const len = Math.max(2, p.length * k), wid = Math.max(0.5, p.width * k), mask = new Float32Array(n);
    // Подряпини групами з близьким напрямком (так лишають сліди уламки), чверть поодинці.
    const nC = Math.max(1, Math.round(count / 7)), clusters = [];
    for (let c = 0; c < nC; c++) clusters.push([rng() * size, rng() * size, rng() * Math.PI]);
    for (let s = 0; s < count; s++) {
      const lone = rng() < 0.25, cl = clusters[s % nC], spread = len * 1.2;
      const x0 = lone ? rng() * size : cl[0] + (rng() - 0.5) * 2 * spread;
      const y0 = lone ? rng() * size : cl[1] + (rng() - 0.5) * 2 * spread;
      const ang = lone ? rng() * Math.PI * 2 : cl[2] + (rng() - 0.5) * 0.5 + (rng() < 0.5 ? Math.PI : 0);
      const L = len * (0.35 + 1.3 * rng()), Wd = wid * (0.6 + 0.8 * rng()), bend = (rng() - 0.5) * 0.08 * L;
      drawScratch(mask, size, x0, y0, ang, L, Wd, bend, 0.35 + 0.45 * rng(), nextSeed(rng));
    }
    const col = maps.color, hgt = maps.height, spec = maps.spec, team = maps.team;
    for (let i = 0; i < n; i++) {
      const m = mask[i];
      if (m <= 0) continue;
      mix3(col, i * 4, METAL[0], METAL[1], METAL[2], m);
      spec[i] = spec[i] + (140 - spec[i]) * m;
      hgt[i] = util.clamp(hgt[i] - 0.06 * m, 0, 1);
      team[i] = team[i] * (1 - m);
    }
  }

  // ---------- Колір гравця ----------

  // Частка пікселя [x, x+1)/size, що потрапляє у відрізок [start, start+len) із загортанням (точний box-фільтр).
  function coverage1D(start, len, size) {
    const out = new Float32Array(size);
    if (len >= 1) return out.fill(1);
    if (!(len > 0)) return out;
    const r0 = start * size, r1 = (start + len) * size;
    for (let x = 0; x < size; x++) {
      let c = 0;
      for (let s = -size; s <= size; s += size) c += Math.max(0, Math.min(x + 1, r1 + s) - Math.max(x, r0 + s));
      out[x] = Math.min(1, c);
    }
    return out;
  }

  // Маска m: team += (255 − team)·m, колір під маскою → нейтральний сірий тієї ж яскравості: c = lerp(c, 0.5·lum + 64, m).
  function applyTeam(p, maps, size) {
    const str = p.strength;
    if (!(str > 0)) return;
    const n = size * size, col = maps.color, team = maps.team, m = new Float32Array(n);
    if (p.mode === 'luma') {
      for (let i = 0, o = 0; i < n; i++, o += 4) {
        m[i] = str * util.smoothstep(p.lumaMin - 0.06, p.lumaMin + 0.06, util.luminance(col[o], col[o + 1], col[o + 2]) / 255);
      }
    } else {
      const cu = coverage1D(p.x, p.w, size), cv = coverage1D(p.y, p.h, size), stripes = p.mode === 'stripes';
      let A = 0, B = 0, fw = 1;
      const duty = 0.6;
      if (stripes) {
        // count смуг уздовж нормалі до них у межах прямокутника; якщо прямокутник перетинає край текстури,
        // частоту по цій осі округлено до цілої, щоб смуги тайлилися.
        const th = p.angle * Math.PI / 180, sn = Math.sin(th), cs = Math.cos(th), cnt = Math.round(p.count);
        const ext = Math.abs(p.w * sn) + Math.abs(p.h * cs);
        if (!(ext > 0)) return;
        A = -sn * cnt / ext; B = cs * cnt / ext;
        if (p.w >= 1 || p.x + p.w > 1) A = Math.round(A);
        if (p.h >= 1 || p.y + p.h > 1) B = Math.round(B);
        fw = Math.sqrt(A * A + B * B) / size || 1;
      }
      for (let y = 0, i = 0; y < size; y++) {
        const vy = cv[y];
        for (let x = 0; x < size; x++, i++) {
          let v = vy * cu[x];
          if (v <= 0) continue;
          if (stripes) {
            let f = A * ((x + 0.5) / size - p.x) + B * ((y + 0.5) / size - p.y);
            f -= Math.floor(f);
            v *= util.clamp((duty / 2 - Math.abs(f - 0.5)) / fw + 0.5, 0, 1);
          }
          m[i] = str * v;
        }
      }
    }
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const t = m[i];
      if (t <= 0) continue;
      team[i] = team[i] + (255 - team[i]) * t;
      const g = 0.5 * util.luminance(col[o], col[o + 1], col[o + 2]) + 64;
      mix3(col, o, g, g, g, t);
    }
  }

  // ---------- Зображення ----------

  const CHANNEL_INDEX = { r: 0, g: 1, b: 2, a: 3 };

  // Обрана карта = lerp(карта, джерело, opacity). Колір бере RGB зображення; одноканальні цілі — обраний канал
  // (для висоти джерело/255). Шар без файлу нічого не змінює.
  function applyImage(p, maps, size, images) {
    const img = images && Object.prototype.hasOwnProperty.call(images, p.imageId) ? images[p.imageId] : null;
    if (!img) return;
    const n = size * size;
    if (img.length !== n * 4) throw new Error('Зображення ' + p.imageId + ' має ' + img.length + ' байт, очікується ' + n * 4);
    const op = p.opacity, col = maps.color;
    if (!(op > 0)) return;
    if (p.target === 'color') {
      for (let o = 0; o < n * 4; o += 4) mix3(col, o, img[o], img[o + 1], img[o + 2], op);
      return;
    }
    const ci = p.channel === 'lum' ? -1 : CHANNEL_INDEX[p.channel];
    const src = o => ci < 0 ? util.luminance(img[o], img[o + 1], img[o + 2]) : img[o + ci];
    if (p.target === 'alpha') {
      for (let o = 0; o < n * 4; o += 4) col[o + 3] = col[o + 3] + (src(o) - col[o + 3]) * op;
    } else if (p.target === 'height') {
      const h = maps.height;
      for (let i = 0; i < n; i++) h[i] = h[i] + (src(i * 4) / 255 - h[i]) * op;
    } else {
      const m = p.target === 'spec' ? maps.spec : maps.team;
      for (let i = 0; i < n; i++) m[i] = m[i] + (src(i * 4) - m[i]) * op;
    }
  }

  // ---------- Диспетчер ----------

  function apply(layer, maps, size, images) {
    const def = findDef(layer && layer.type);
    if (!util.isPow2(size) || !maps || !maps.height || maps.height.length !== size * size) {
      throw new Error('Карти не відповідають розміру документа ' + size);
    }
    const p = resolveParams(def, layer.params), seed = Number.isFinite(layer.seed) ? layer.seed : 0;
    switch (def.type) {
      case 'base': return applyBase(p, seed, maps, size);
      case 'camo': return applyCamo(p, seed, maps, size);
      case 'panels': return applyPanels(p, seed, maps, size);
      case 'rivets': return applyRivets(p, seed, maps, size);
      case 'dirt': return applyDirt(p, seed, maps, size);
      case 'rust': return applyRust(p, seed, maps, size);
      case 'scratches': return applyScratches(p, seed, maps, size);
      case 'team': return applyTeam(p, maps, size);
      case 'image': return applyImage(p, maps, size, images);
    }
  }

  const api = { FACTION_PRESETS, catalog, defaultLayer, apply };
  RA3.generators = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
