(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');

  // Маски-генератори шарів v2: generate(gen, maps, size) → Float32Array(n) у [0,1], де maps — композиція ПІД шаром.
  // Розміри в пікселях задано для текстури 512² і масштабуються з розміром документа (як у generators.js),
  // шум задано в UV. Усе тайлиться: розмиття й морфологія вибирають пікселі із загортанням, шум — тайловий
  // value-noise з цілими частотами (крім «Градієнта», який за природою не тайлиться).
  const REF = 512;
  const CURV = 0.04;      // різниця висоти з розмитим оточенням, за якої знос/западина повні
  const DEAD = 1e-4;      // мертва зона кривини: похибки сум і мікрошум висоти не дають маски

  // ---------- Каталог ----------

  const range = (key, label, min, max, step, def) => ({ key, label, kind: 'range', min, max, step, default: def });
  const color = (key, label, def) => ({ key, label, kind: 'color', default: def });
  const select = (key, label, options, def) => ({ key, label, kind: 'select', options: options.map(o => ({ value: o[0], label: o[1] })), default: def });

  const catalog = [
    { type: 'edges', label: 'Зношені краї', params: [
      range('width', 'Ширина, px', 1, 16, 0.5, 3),
      range('contrast', 'Контраст', 0, 1, 0.01, 0.6),
      range('breakup', 'Розбивка шумом', 0, 1, 0.01, 0.5),
      range('scale', 'Масштаб шуму', 0.25, 4, 0.05, 1)] },
    { type: 'cavity', label: 'Западини', params: [
      range('width', 'Ширина, px', 1, 16, 0.5, 3),
      range('contrast', 'Контраст', 0, 1, 0.01, 0.4),
      range('spread', 'Розтікання, px', 0, 32, 0.5, 0),
      range('breakup', 'Розбивка шумом', 0, 1, 0.01, 0),
      range('scale', 'Масштаб шуму', 0.25, 4, 0.05, 1)] },
    { type: 'ao', label: 'Затінення', params: [
      range('radius', 'Радіус, px', 2, 64, 1, 16),
      range('strength', 'Сила', 0, 1, 0.01, 0.5)] },
    { type: 'height', label: 'За висотою', params: [
      range('min', 'Від', 0, 1, 0.01, 0.55),
      range('max', 'До', 0, 1, 0.01, 1),
      range('softness', 'М\'якість', 0, 0.5, 0.01, 0.05)] },
    { type: 'noise', label: 'Гранж', params: [
      range('scale', 'Масштаб', 0.25, 4, 0.05, 1),
      range('coverage', 'Покриття', 0, 1, 0.01, 0.5),
      range('contrast', 'Контраст', 0, 1, 0.01, 0.5),
      range('stretch', 'Патьоки (витягнути вниз)', 0, 1, 0.01, 0)] },
    { type: 'gradient', label: 'Градієнт', params: [
      select('direction', 'Звідки', [['bottom', 'Знизу'], ['top', 'Згори'], ['left', 'Ліворуч'], ['right', 'Праворуч']], 'bottom'),
      range('offset', 'Межа (частка від краю)', 0, 1, 0.01, 0.4),
      range('softness', 'М\'якість', 0, 1, 0.01, 0.5)] },
    { type: 'team', label: 'Зона гравця', params: [
      range('grow', 'Розширення, px', 0, 16, 0.5, 0),
      range('blur', 'Розмиття, px', 0, 16, 0.5, 0)] },
    { type: 'color', label: 'За кольором', params: [
      color('color', 'Колір', '#808080'),
      range('tolerance', 'Допуск', 0, 1, 0.01, 0.15),
      range('softness', 'М\'якість', 0, 1, 0.01, 0.05)] }
  ];
  const byType = {};
  for (const c of catalog) byType[c.type] = c;

  function findDef(type) {
    const def = Object.prototype.hasOwnProperty.call(byType, type) ? byType[type] : null;
    if (!def) throw new Error('Невідомий генератор маски: ' + type);
    return def;
  }

  // Параметри, доповнені типовими: числа обмежено діапазоном, невідомі варіанти й кольори замінено на типові.
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
        if (p.options.some(o => o.value === v)) r = v;
      } else if (typeof v === 'boolean') r = v;
      out[p.key] = r;
    }
    return out;
  }

  function defaultGen(type, seed) {
    const def = findDef(type), params = {};
    for (const p of def.params) params[p.key] = p.default;
    return { type, seed: seed === undefined ? Math.floor(Math.random() * 4294967296) : seed, params };
  }

  // ---------- Розмиття й морфологія із загортанням ----------

  // Box-розмиття з дробовим радіусом r: повні відліки в [−⌊r⌋, ⌊r⌋] і два крайні з вагою r − ⌊r⌋, тож повзунок
  // змінює результат плавно. Окремо по рядках (ковзна сума) і по стовпцях (ковзна сума цілих рядків), O(n) за будь-якого r.
  function boxBlur(src, size, r, out, tmp) {
    if (!(r > 0)) { out.set(src); return out; }
    const ri = Math.floor(r), f = r - ri, inv = 1 / (2 * ri + 1 + 2 * f);
    const md = v => ((v % size) + size) % size;
    // Рядок із загорнутими полями: ext[j] = піксель j − ri − 1, тож вікно пікселя x — ext[x+1 .. x+2ri+1],
    // крайні дробові відліки — ext[x] і ext[x+2ri+2]; без перевірок загортання у внутрішньому циклі.
    const span = 2 * ri + 2, ext = new Float32Array(size + span);
    for (let y = 0; y < size; y++) {
      const row = y * size;
      for (let j = 0, k = md(-ri - 1); j < ext.length; j++) { ext[j] = src[row + k]; if (++k === size) k = 0; }
      let s = 0;
      for (let j = 1; j < span; j++) s += ext[j];
      if (f > 0) for (let x = 0; x < size; x++) { const add = ext[x + span]; tmp[row + x] = (s + f * (ext[x] + add)) * inv; s += add - ext[x + 1]; }
      else for (let x = 0; x < size; x++) { tmp[row + x] = s * inv; s += ext[x + span] - ext[x + 1]; }
    }
    const acc = new Float64Array(size);
    for (let d = -ri; d <= ri; d++) { const row = md(d) * size; for (let x = 0; x < size; x++) acc[x] += tmp[row + x]; }
    let add = md(ri + 1), sub = md(-ri), far = md(-ri - 1);
    for (let y = 0; y < size; y++) {
      const dst = y * size, ra = add * size, rs = sub * size, rf = far * size;
      if (f > 0) for (let x = 0; x < size; x++) { out[dst + x] = (acc[x] + f * (tmp[ra + x] + tmp[rf + x])) * inv; acc[x] += tmp[ra + x] - tmp[rs + x]; }
      else for (let x = 0; x < size; x++) { out[dst + x] = acc[x] * inv; acc[x] += tmp[ra + x] - tmp[rs + x]; }
      if (++add === size) add = 0;
      if (++sub === size) sub = 0;
      if (++far === size) far = 0;
    }
    return out;
  }

  // М'яке розмиття: два box-проходи радіусом r/2 — трикутне ядро з півшириною r (заклепки дають круглий, а не
  // квадратний відгук). buf — необов'язкові робочі буфери { a, tmp, out }, щоб не виділяти пам'ять щоразу.
  function smooth(src, size, r, buf) {
    const n = size * size, b = buf || {};
    const a = b.a || new Float32Array(n), tmp = b.tmp || new Float32Array(n), out = b.out || new Float32Array(n);
    boxBlur(src, size, r / 2, a, tmp);
    return boxBlur(a, size, r / 2, out, tmp);
  }

  // Ковзний максимум у вікні [i−hw, i+hw] по колу, O(n) (монотонна черга).
  function slideMax(line, n, hw, out, q, qv) {
    if (2 * hw + 1 >= n) {
      let m = -Infinity;
      for (let i = 0; i < n; i++) if (line[i] > m) m = line[i];
      for (let i = 0; i < n; i++) out[i] = m;
      return;
    }
    let head = 0, tail = 0;
    for (let k = -hw; k < n + hw; k++) {
      const v = line[k < 0 ? k + n : (k >= n ? k - n : k)];
      while (tail > head && qv[tail - 1] <= v) tail--;
      q[tail] = k; qv[tail] = v; tail++;
      if (q[head] < k - 2 * hw) head++;
      if (k >= hw) out[k - hw] = qv[head];
    }
  }

  function dilateRows(a, size, hw) {
    const res = new Float32Array(size), q = new Int32Array(size + 2 * hw + 1), qv = new Float32Array(size + 2 * hw + 1);
    for (let row = 0; row < a.length; row += size) {
      slideMax(a.subarray(row, row + size), size, hw, res, q, qv);
      a.set(res, row);
    }
  }

  // Транспонування блоками 32×32 (прохід по стовпцях стає проходом по рядках, без промахів кешу).
  function transpose(src, dst, size) {
    const B = 32;
    for (let by = 0; by < size; by += B) {
      const ey = Math.min(by + B, size);
      for (let bx = 0; bx < size; bx += B) {
        const ex = Math.min(bx + B, size);
        for (let y = by; y < ey; y++) for (let x = bx, i = y * size + bx; x < ex; x++, i++) dst[x * size + y] = src[i];
      }
    }
  }

  // Розширення (дилатація квадратом (2hw+1)²) на місці: рядки, транспонування, рядки, транспонування назад.
  function dilate(a, size, hw) {
    const t = new Float32Array(a.length);
    dilateRows(a, size, hw);
    transpose(a, t, size);
    dilateRows(t, size, hw);
    transpose(t, a, size);
  }

  // ---------- Шум ----------

  const nextSeed = rng => (rng() * 4294967296) | 0;

  // Хеш вузла решітки (seed, октава, x, y) → [0,1) (як у util.fbm).
  function lattice(seed, o, x, y) {
    let h = (seed | 0) ^ Math.imul(o + 1, 0x632BE5AB);
    h = Math.imul(h ^ x, 0x85EBCA6B); h ^= h >>> 13;
    h = Math.imul(h ^ y, 0xC2B2AE35); h ^= h >>> 16;
    h = Math.imul(h, 0x27D4EB2F); h ^= h >>> 15;
    return (h >>> 0) / 4294967296;
  }

  // Заміна значень рангом: результат рівномірний у [0,1], тож поріг 1−a покриває частку a площі.
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

  // Розтягування res² → size² сплайном Catmull-Rom (окремо по рядках і стовпцях) із загортанням; res — степінь двійки.
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

  // Рангове поле тайлового fbm (value-noise як util.fbm) з окремими цілими частотами по осях (fx > fy — вертикальні
  // патьоки), значення рівномірні в [0,1]. Рахується на сітці res ≤ size (степінь двійки, найдрібніша клітинка ≥ 3 px)
  // і розтягується сплайном: на 1024² і більше в рази швидше, а на вигляд те саме.
  function noiseField(seed, size, fx, fy, octaves) {
    const cells = Math.max(fx, fy) << (octaves - 1);
    let res = 64;
    while (res < cells * 3 && res < size) res *= 2;
    if (res >= size || !util.isPow2(size)) return noiseGrid(seed, size, fx, fy, octaves);
    return upsample(noiseGrid(seed, res, fx, fy, octaves), res, size);
  }

  // fbm на сітці size² (будь-який size): решітка октави o має fx·2^o × fy·2^o вузлів із загортанням; вузли
  // рахуються раз на октаву (якщо їх не більше, ніж пікселів), тож на піксель лише білінійна інтерполяція.
  function noiseGrid(seed, size, fx, fy, octaves) {
    const n = size * size, out = new Float32Array(n);
    const x0 = new Int32Array(size), x1 = new Int32Array(size), tx = new Float32Array(size);
    let amp = 1;
    for (let o = 0; o < octaves; o++) {
      const sx = fx << o, sy = fy << o, grid = sx * sy <= n ? new Float32Array(sx * sy) : null;
      if (grid) for (let j = 0, i = 0; j < sy; j++) for (let k = 0; k < sx; k++, i++) grid[i] = lattice(seed, o, k, j);
      for (let x = 0; x < size; x++) {
        const p = x * sx / size, i = Math.floor(p), t = p - i;
        x0[x] = i % sx; x1[x] = (i + 1) % sx; tx[x] = t * t * (3 - 2 * t);
      }
      for (let y = 0, i = 0; y < size; y++) {
        const p = y * sy / size, iy = Math.floor(p), f = p - iy, ty = f * f * (3 - 2 * f);
        const j0 = iy % sy, j1 = (iy + 1) % sy, r0 = j0 * sx, r1 = j1 * sx;
        if (grid) {
          for (let x = 0; x < size; x++, i++) {
            const u = tx[x], k0 = x0[x], k1 = x1[x], a = grid[r0 + k0], c = grid[r1 + k0];
            const top = a + (grid[r0 + k1] - a) * u, bot = c + (grid[r1 + k1] - c) * u;
            out[i] += amp * (top + (bot - top) * ty);
          }
        } else {
          for (let x = 0; x < size; x++, i++) {
            const u = tx[x], k0 = x0[x], k1 = x1[x], a = lattice(seed, o, k0, j0), c = lattice(seed, o, k0, j1);
            const top = a + (lattice(seed, o, k1, j0) - a) * u, bot = c + (lattice(seed, o, k1, j1) - c) * u;
            out[i] += amp * (top + (bot - top) * ty);
          }
        }
      }
      amp *= 0.5;
    }
    rankNormalize(out);
    return out;
  }

  // ---------- Карти композиції ----------

  function heightOf(maps, n) {
    return maps.height || new Float32Array(n).fill(0.5);
  }

  function checkMaps(maps, size) {
    const n = size * size;
    const bad = (a, len) => a !== undefined && a !== null && a.length !== len;
    if (bad(maps.height, n) || bad(maps.color, n * 4) || bad(maps.team, n) || bad(maps.spec, n)) {
      throw new Error('Карти не відповідають розміру документа ' + size);
    }
  }

  // ---------- Зношені краї й западини ----------

  // Знос/бруд за кривиною. sign = +1: опуклості (піксель вищий за розмите оточення радіуса width — край панелі
  // біля шва, ребро, маківка заклепки; дно западини дає від'ємну різницю, тож туди знос не лізе); sign = −1:
  // западини (шви, щілини, підніжжя заклепок). e = кривина/CURV ∈ [0,1] — частка повного ефекту; розбивка
  // віднімає ранговий шум (рве край на сколи й змінює його ширину); контраст звужує перехід порогу.
  // Без розтікання — один прохід по пікселях; з ним (лише «Западини») — ще розмиття маски.
  function wear(sign, p, seed, maps, size) {
    const n = size * size, h = heightOf(maps, n), k = size / REF, r = Math.max(1, p.width * k);
    const out = smooth(h, size, r), br = p.breakup, kb = 2 * br, ic = 1 / CURV;
    const w = 0.34 * (1 - p.contrast) + 0.01, lo = Math.max(0, 0.35 - w), inv = 1 / (2 * w), f = Math.max(2, Math.round(32 / p.scale));
    const N = br > 0 ? noiseField(nextSeed(util.mulberry32(seed)), size, f, f, 3) : null;
    const spread = p.spread > 0 ? p.spread * k : 0;
    if (spread > 0) {
      // Розтікання: бруд/іржа виходить зі шва на панель. Розмиття лінії шириною w ядром півшириною R має пік ≈ w/R,
      // тож множимо на R/w (шов ≈ 3 px на 512): біля шва ≈ 1, далі лінійний спад до 0 на відстані spread.
      for (let i = 0; i < n; i++) { const e = (sign * (h[i] - out[i]) - DEAD) * ic; out[i] = e > 0 ? (e < 1 ? e : 1) : 0; }
      const sp = smooth(out, size, spread), g = Math.max(2, p.spread / 3);
      for (let i = 0; i < n; i++) { const v = g * sp[i]; if (v > out[i]) out[i] = v < 1 ? v : 1; }
    }
    for (let i = 0; i < n; i++) {
      let e;
      if (spread > 0) e = out[i];
      else { e = (sign * (h[i] - out[i]) - DEAD) * ic; e = e > 0 ? (e < 1 ? e : 1) : 0; }
      if (N) e -= kb * N[i];
      const t = (e - lo) * inv;
      out[i] = t <= 0 ? 0 : (t >= 1 ? 1 : t * t * (3 - 2 * t));
    }
    return out;
  }

  const genEdges = (p, seed, maps, size) => wear(1, p, seed, maps, size);
  const genCavity = (p, seed, maps, size) => wear(-1, p, seed, maps, size);

  // ---------- Затінення ----------

  // Наближений AO: на трьох масштабах (R, R/2, R/4; трикутне ядро півшириною 2·масштаб) рахуємо, наскільки
  // оточення вище за піксель; зважена сума s·gain → 1 − 1/(1 + x + x²/2 + x³/6 + x⁴/24) ≈ 1 − e^(−x):
  // глибокі шви насичуються плавно, а рівна поверхня дає рівно 0. Один прохід по всіх трьох масштабах.
  function genAO(p, seed, maps, size) {
    const n = size * size, h = heightOf(maps, n), R = Math.max(1, p.radius * size / REF), gain = 2 + 28 * p.strength;
    const a = new Float32Array(n), tmp = new Float32Array(n);
    const b1 = smooth(h, size, Math.max(1, 2 * R), { a, tmp });
    const b2 = smooth(h, size, Math.max(1, R), { a, tmp });
    const out = smooth(h, size, Math.max(1, R / 2), { a, tmp });
    for (let i = 0; i < n; i++) {
      const hd = h[i] + DEAD, d1 = b1[i] - hd, d2 = b2[i] - hd, d3 = out[i] - hd;
      const x = gain * (0.5 * (d1 > 0 ? d1 : 0) + 0.3 * (d2 > 0 ? d2 : 0) + 0.2 * (d3 > 0 ? d3 : 0));
      out[i] = x > 0 ? 1 - 1 / (1 + x * (1 + x * (0.5 + x * (1 / 6 + x / 24)))) : 0;
    }
    return out;
  }

  // ---------- За висотою ----------

  function genHeight(p, seed, maps, size) {
    const n = size * size, h = heightOf(maps, n), out = new Float32Array(n);
    const lo = Math.min(p.min, p.max), hi = Math.max(p.min, p.max), s = p.softness;
    for (let i = 0; i < n; i++) {
      const v = h[i];
      const up = lo <= 0 ? 1 : (s > 0 ? util.smoothstep(lo - s, lo + s, v) : (v >= lo ? 1 : 0));
      const down = hi >= 1 ? 1 : (s > 0 ? 1 - util.smoothstep(hi - s, hi + s, v) : (v <= hi ? 1 : 0));
      out[i] = up * down;
    }
    return out;
  }

  // ---------- Гранж ----------

  // Ранговий fbm з порогом: частка площі під маскою ≈ coverage; contrast звужує перехід. stretch робить
  // вертикальні патьоки (більше клітинок по X, ніж по Y).
  function genNoise(p, seed, maps, size) {
    const n = size * size, out = new Float32Array(n), cov = p.coverage;
    if (cov <= 0) return out;
    if (cov >= 1) return out.fill(1);
    const f = Math.max(2, Math.round(5 / p.scale)), fx = Math.max(2, Math.round(f * (1 + 11 * p.stretch)));
    const N = noiseField(seed, size, fx, f, 5);
    const w = 0.5 * (1 - p.contrast) + 0.01, thr = coverageThreshold(cov, w), lo = thr - w, hi = thr + w;
    for (let i = 0; i < n; i++) { const v = N[i]; out[i] = v <= lo ? 0 : (v >= hi ? 1 : util.smoothstep(lo, hi, v)); }
    return out;
  }

  // Поріг thr, за якого середнє smoothstep(thr−w, thr+w, N) для рівномірного N ∈ [0,1] дорівнює cov
  // (біля країв перехід виходить за [0,1], тож просте thr = 1 − cov там дає меншу/більшу частку). Бісекція
  // за аналітичним інтегралом: ∫ smoothstep = 2w·(t³ − t⁴/2).
  function coverageThreshold(cov, w) {
    const G = t => t * t * t - 0.5 * t * t * t * t;
    const share = thr => {
      const lo = thr - w, ta = util.clamp(-lo / (2 * w), 0, 1), tb = util.clamp((1 - lo) / (2 * w), 0, 1);
      return 2 * w * (G(tb) - G(ta)) + Math.max(0, 1 - Math.max(thr + w, 0));
    };
    let a = -w, b = 1 + w;                         // share(a) = 1, share(b) = 0
    for (let k = 0; k < 40; k++) { const m = (a + b) / 2; if (share(m) > cov) a = m; else b = m; }
    return (a + b) / 2;
  }

  // ---------- Градієнт ----------

  // t — відстань від обраного краю (0 біля нього, 1 біля протилежного); маска 1 до межі offset, м'який перехід softness.
  function genGradient(p, seed, maps, size) {
    const n = size * size, out = new Float32Array(n), off = p.offset, s = p.softness;
    const prof = new Float32Array(size);
    for (let k = 0; k < size; k++) {
      const t = (k + 0.5) / size;
      prof[k] = s > 0 ? 1 - util.smoothstep(off - s / 2, off + s / 2, t) : (t < off ? 1 : 0);
    }
    const dir = p.direction;
    for (let y = 0, i = 0; y < size; y++) for (let x = 0; x < size; x++, i++) {
      out[i] = dir === 'top' ? prof[y] : dir === 'bottom' ? prof[size - 1 - y] : dir === 'left' ? prof[x] : prof[size - 1 - x];
    }
    return out;
  }

  // ---------- Зона гравця ----------

  function genTeam(p, seed, maps, size) {
    const n = size * size, k = size / REF;
    let out = new Float32Array(n);
    if (maps.team) for (let i = 0; i < n; i++) out[i] = maps.team[i] / 255;
    const g = Math.round(p.grow * k);
    if (g >= 1) dilate(out, size, g);
    const b = p.blur * k;
    if (b > 0) out = smooth(out, size, b);
    return out;
  }

  // ---------- За кольором ----------

  // Евклідова відстань у RGB, нормована на діагональ куба (0..1): ≤ tolerance → 1, далі спад на ширині softness.
  function genColor(p, seed, maps, size) {
    const n = size * size, out = new Float32Array(n), t = util.hexToRgb(p.color), col = maps.color;
    const tol = p.tolerance, s = p.softness, inv = 1 / (255 * Math.sqrt(3));
    for (let i = 0, o = 0; i < n; i++, o += 4) {
      const r = col ? col[o] : 128, g = col ? col[o + 1] : 128, b = col ? col[o + 2] : 128;
      const dr = r - t[0], dg = g - t[1], db = b - t[2], d = Math.sqrt(dr * dr + dg * dg + db * db) * inv;
      out[i] = s > 0 ? 1 - util.smoothstep(tol, tol + s, d) : (d <= tol ? 1 : 0);
    }
    return out;
  }

  // ---------- Диспетчер ----------

  const GENERATORS = { edges: genEdges, cavity: genCavity, ao: genAO, height: genHeight, noise: genNoise, gradient: genGradient, team: genTeam, color: genColor };

  function generate(gen, maps, size) {
    const def = findDef(gen && gen.type);
    if (!Number.isInteger(size) || size < 1) throw new Error('Некоректний розмір маски: ' + size);
    const m = maps || {};
    checkMaps(m, size);
    const p = resolveParams(def, gen.params), seed = Number.isFinite(gen.seed) ? gen.seed : 0;
    const out = GENERATORS[def.type](p, seed, m, size);
    for (let i = 0; i < out.length; i++) { const v = out[i]; out[i] = v > 0 ? (v < 1 ? v : 1) : 0; } // і NaN → 0
    return out;
  }

  const api = { catalog, defaultGen, generate, resolveParams: (type, params) => resolveParams(findDef(type), params) };
  RA3.maskgen = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
