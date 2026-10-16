(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');
  const { clamp, smoothstep, mulberry32, fbm, luminance } = util;

  const BASE_RES = 128;          // базова роздільність вбудованих альф
  const CUSTOM_RES = 256;        // базова роздільність імпортованих альф
  const MAX_DAB = 4096;          // верхня межа діаметра штампа (захист пам'яті)
  const MIN_DAB = 0.5;           // мінімальний діаметр мазка в потоці
  const MIN_STEP = 0.5;          // мінімальний крок між мазками, px
  const CACHE_MAX_ENTRIES = 64;
  const CACHE_MAX_FLOATS = 8 * 1024 * 1024; // ~32 МБ на всі закешовані штампи
  const EPS = 1e-7;
  const DEG = Math.PI / 180;

  // ---------------------------------------------------------------- Допоміжне

  function fillAlpha(res, fn) {
    const out = new Float32Array(res * res);
    let i = 0;
    for (let y = 0; y < res; y++) {
      const v = (y + 0.5) / res;
      for (let x = 0; x < res; x++) out[i++] = clamp(fn((x + 0.5) / res, v, x, y), 0, 1);
    }
    return out;
  }

  // Профіль круга: r — відстань від центру в частках радіуса, pxPerR — пікселів на одиницю r.
  // hardness 1 — різкий край (антиаліасинг ~1 px), 0 — м'яке спадання від самого центру.
  function discValue(r, hardness, pxPerR) {
    const px = 1 / Math.max(pxPerR, 1);               // розмір пікселя в одиницях r
    const soft = Math.max(1 - clamp(hardness, 0, 1), px);
    const t = clamp((r - (1 - soft) - px / 2) / soft, 0, 1); // 50% різкого краю — рівно на r = 1
    return 1 - t * t * (3 - 2 * t);
  }

  function stampDisc(out, res, cx, cy, rad) {
    const x0 = Math.max(0, Math.floor(cx - rad - 2)), x1 = Math.min(res - 1, Math.ceil(cx + rad + 2));
    const y0 = Math.max(0, Math.floor(cy - rad - 2)), y1 = Math.min(res - 1, Math.ceil(cy + rad + 2));
    for (let y = y0; y <= y1; y++) {
      for (let x = x0; x <= x1; x++) {
        const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        const a = clamp(rad - d + 0.5, 0, 1);
        const i = y * res + x;
        if (a > out[i]) out[i] = a;
      }
    }
  }

  // Знакова відстань до простого багатокутника (від'ємна всередині). pts — [[x,y],...].
  function sdPolygon(pts, px, py) {
    const n = pts.length;
    let d = (px - pts[0][0]) * (px - pts[0][0]) + (py - pts[0][1]) * (py - pts[0][1]);
    let s = 1;
    for (let i = 0, j = n - 1; i < n; j = i, i++) {
      const ex = pts[j][0] - pts[i][0], ey = pts[j][1] - pts[i][1];
      const wx = px - pts[i][0], wy = py - pts[i][1];
      const t = clamp((wx * ex + wy * ey) / (ex * ex + ey * ey), 0, 1);
      const bx = wx - ex * t, by = wy - ey * t;
      d = Math.min(d, bx * bx + by * by);
      const c0 = py >= pts[i][1], c1 = py < pts[j][1], c2 = ex * wy > ey * wx;
      if ((c0 && c1 && c2) || (!c0 && !c1 && !c2)) s = -s;
    }
    return s * Math.sqrt(d);
  }

  // Радіальне згасання до країв квадрата (u,v ∈ [0,1)): 1 у центрі, 0 на краю.
  function edgeFade(u, v, from) {
    const r = Math.hypot(2 * u - 1, 2 * v - 1);
    return 1 - smoothstep(from, 1, r);
  }

  // Квадрат, вписаний у коло діаметра 1 (сторона 0.7 < 1/√2): за обертання кути не виходять за межі штампа.
  function insetRect(u, v, res) {
    const bx = Math.min(u, 1 - u, v, 1 - v) * res;
    return clamp(bx - 0.15 * res + 0.5, 0, 1);
  }

  // Гарантує, що штамп не виходить за коло діаметра 1 (вписане в квадрат альфи).
  function clipToCircle(data, res) {
    for (let y = 0, i = 0; y < res; y++) {
      for (let x = 0; x < res; x++, i++) {
        const r = Math.hypot(2 * (x + 0.5) / res - 1, 2 * (y + 0.5) / res - 1);
        if (r > 0.92) data[i] *= 1 - smoothstep(0.92, 1, r);
      }
    }
    return data;
  }

  // ---------------------------------------------------------------- Вбудовані альфи
  // Угода: sizePx штампа — діаметр кола, що вміщує весь штамп за будь-якого кута (курсор пензля = це коло).

  function genRound(res) {
    return fillAlpha(res, (u, v) => discValue(Math.hypot(2 * u - 1, 2 * v - 1), 0.8, res / 2));
  }

  function genSquare(res) {
    return fillAlpha(res, (u, v) => insetRect(u, v, res));
  }

  function genGrunge(res) {
    return fillAlpha(res, (u, v) => {
      const n = fbm(11, u, v, 4, 5);
      const detail = fbm(12, u, v, 12, 3);
      return smoothstep(0.38, 0.6, n) * (0.55 + 0.45 * detail) * edgeFade(u, v, 0.7);
    });
  }

  function genSplatter(res) {
    const out = new Float32Array(res * res);
    const rng = mulberry32(1337);
    const cx = res / 2, cy = res / 2;
    // центральна пляма — кілька накладених кіл
    for (let i = 0; i < 7; i++) {
      const a = rng() * Math.PI * 2, d = rng() * 0.06 * res;
      stampDisc(out, res, cx + Math.cos(a) * d, cy + Math.sin(a) * d, (0.07 + rng() * 0.05) * res);
    }
    // краплі: чим далі від центру, тим дрібніші
    for (let i = 0; i < 70; i++) {
      const a = rng() * Math.PI * 2;
      const dist = (0.1 + 0.36 * Math.pow(rng(), 0.8)) * res;
      const fall = 1 - dist / (0.5 * res);
      const rad = Math.max(0.6, (0.008 + 0.06 * Math.pow(rng(), 3) * (0.3 + fall)) * res);
      stampDisc(out, res, cx + Math.cos(a) * dist, cy + Math.sin(a) * dist, rad);
    }
    return clipToCircle(out, res);
  }

  // Притискає точку (в частках альфи) всередину кола радіуса 0.45 навколо центру.
  function inDisc(x, y) {
    const dx = x - 0.5, dy = y - 0.5, d = Math.hypot(dx, dy);
    return d > 0.45 ? [0.5 + dx * 0.45 / d, 0.5 + dy * 0.45 / d] : [x, y];
  }

  function genScratches(res) {
    const out = new Float32Array(res * res);
    const rng = mulberry32(2024);
    const main = rng() * Math.PI;
    const k = res / BASE_RES;
    for (let s = 0; s < 8; s++) {
      const ang = main + (rng() - 0.5) * 0.7;
      const len = 0.35 + 0.5 * rng();
      const cx = 0.15 + 0.7 * rng(), cy = 0.15 + 0.7 * rng();
      const dx = Math.cos(ang) * len * 0.5, dy = Math.sin(ang) * len * 0.5;
      const p0 = inDisc(cx - dx, cy - dy), p1 = inDisc(cx + dx, cy + dy);
      const x0 = p0[0] * res, y0 = p0[1] * res, x1 = p1[0] * res, y1 = p1[1] * res;
      const half = (0.35 + 0.5 * rng()) * k;
      const inten = 0.55 + 0.45 * rng();
      const ex = x1 - x0, ey = y1 - y0, e2 = ex * ex + ey * ey || 1;
      const minX = Math.max(0, Math.floor(Math.min(x0, x1) - half - 2)), maxX = Math.min(res - 1, Math.ceil(Math.max(x0, x1) + half + 2));
      const minY = Math.max(0, Math.floor(Math.min(y0, y1) - half - 2)), maxY = Math.min(res - 1, Math.ceil(Math.max(y0, y1) + half + 2));
      for (let y = minY; y <= maxY; y++) {
        for (let x = minX; x <= maxX; x++) {
          const px = x + 0.5 - x0, py = y + 0.5 - y0;
          const t = clamp((px * ex + py * ey) / e2, 0, 1);
          const d = Math.hypot(px - ex * t, py - ey * t);
          const a = clamp(half + 0.5 - d, 0, 1) * clamp(Math.min(t, 1 - t) * 6, 0, 1) * inten;
          const i = y * res + x;
          if (a > out[i]) out[i] = a;
        }
      }
    }
    return clipToCircle(out, res);
  }

  function genChips(res) {
    const rng = mulberry32(77);
    const chips = [];
    for (let tries = 0; chips.length < 6 && tries < 200; tries++) {
      const R = 0.05 + 0.08 * rng();
      const cx = 0.5 + (rng() - 0.5) * 0.9, cy = 0.5 + (rng() - 0.5) * 0.9;
      let ok = Math.hypot(cx - 0.5, cy - 0.5) + R * 1.5 <= 0.46; // сколи цілком усередині кола штампа
      for (const c of chips) if (Math.hypot(c.cx - cx, c.cy - cy) < (c.R + R) * 1.15) { ok = false; break; }
      if (!ok) continue;
      const harm = [];
      for (let k = 2; k <= 8; k++) harm.push({ k, a: (0.3 / k) * (0.4 + rng()), ph: rng() * Math.PI * 2 });
      chips.push({ cx, cy, R, harm, jag: 0.06 + 0.08 * rng(), jph: rng() * Math.PI * 2 });
    }
    return clipToCircle(fillAlpha(res, (u, v) => {
      let best = 0;
      for (const c of chips) {
        const dx = u - c.cx, dy = v - c.cy;
        const d = Math.hypot(dx, dy);
        if (d > c.R * 1.7) continue;
        const th = Math.atan2(dy, dx);
        let f = 1;
        for (const h of c.harm) f += h.a * Math.cos(h.k * th + h.ph);
        f += c.jag * (Math.abs(Math.sin(5 * th + c.jph)) - 0.5);
        const edge = c.R * f;
        const a = clamp((edge - d) * res + 0.5, 0, 1);
        if (a > best) best = a;
      }
      return best > 0 ? best * (0.8 + 0.2 * fbm(5, u, v, 6, 2)) : 0;
    }), res);
  }

  function genRivet(res) {
    return fillAlpha(res, (u, v) => {
      const r = Math.hypot(2 * u - 1, 2 * v - 1);
      if (r < 0.72) { const q = r / 0.72; return 0.62 + 0.38 * Math.sqrt(1 - q * q); }
      return 0.62 * (1 - smoothstep(0.72, 0.96, r));
    });
  }

  function genStar(res) {
    const R = 0.46, r = R * 0.4;
    const cy = 0.5 + 0.0955 * R; // оптичний центр зірки (нижні промені коротші за верхній)
    const pts = [];
    for (let i = 0; i < 10; i++) {
      const a = -Math.PI / 2 + i * Math.PI / 5, rad = i % 2 === 0 ? R : r;
      pts.push([0.5 + Math.cos(a) * rad, cy + Math.sin(a) * rad]);
    }
    return fillAlpha(res, (u, v) => 0.5 - sdPolygon(pts, u, v) * res);
  }

  function genHazard(res) {
    const N = 4;
    return fillAlpha(res, (u, v) => {
      const f = ((u + v) * N) % 1;
      const stripe = clamp((0.25 - Math.abs(f - 0.5)) / (N / res) + 0.5, 0, 1);
      return stripe * insetRect(u, v, res);
    });
  }

  function genCamo(res) {
    return fillAlpha(res, (u, v) => {
      const r = Math.hypot(2 * u - 1, 2 * v - 1);
      const n = fbm(31, u, v, 3, 4);
      const shape = n - 0.5 + (0.6 - r) * 1.3;
      return smoothstep(-0.04, 0.16, shape) * edgeFade(u, v, 0.8);
    });
  }

  const ALPHAS = [
    { id: 'round', label: 'Круг', gen: genRound },
    { id: 'square', label: 'Квадрат', gen: genSquare },
    { id: 'grunge', label: 'Гранж', gen: genGrunge },
    { id: 'splatter', label: 'Бризки', gen: genSplatter },
    { id: 'scratches', label: 'Подряпини', gen: genScratches },
    { id: 'chips', label: 'Сколи', gen: genChips },
    { id: 'rivet', label: 'Заклепка', gen: genRivet },
    { id: 'star', label: 'Зірка', gen: genStar },
    { id: 'hazard', label: 'Смуги', gen: genHazard },
    { id: 'camo', label: 'Плями', gen: genCamo }
  ];
  const ALPHA_BY_ID = {};
  for (const a of ALPHAS) ALPHA_BY_ID[a.id] = a;

  const baseCache = new Map(); // id -> Float32Array(BASE_RES²)
  function baseData(def) {
    let d = baseCache.get(def.id);
    if (!d) { d = def.gen(BASE_RES); baseCache.set(def.id, d); }
    return d;
  }

  // ---------------------------------------------------------------- Власні альфи

  // Яскравість (× альфа-канал) зображення, вписана по центру в квадрат CUSTOM_RES×CUSTOM_RES.
  function alphaFromImage(rgba, w, h) {
    if (!Number.isInteger(w) || !Number.isInteger(h) || w < 1 || h < 1) throw new Error('Некоректні розміри зображення: ' + w + '×' + h);
    if (!rgba || rgba.length !== w * h * 4) throw new Error('Розмір буфера не відповідає розмірам зображення ' + w + '×' + h);
    const lum = new Float32Array(w * h);
    for (let i = 0, j = 0; i < lum.length; i++, j += 4) {
      lum[i] = clamp(luminance(rgba[j], rgba[j + 1], rgba[j + 2]) / 255 * (rgba[j + 3] / 255), 0, 1);
    }
    const res = CUSTOM_RES;
    // Угода як у вбудованих альф: sizePx — діаметр кола, що вміщує штамп. Якщо вміст виходить за вписане коло
    // зображення (наприклад, суцільний квадрат), його зменшують, щоб кути не обрізались за обертання.
    let rmax = 0;
    for (let y = 0, i = 0; y < h; y++) {
      for (let x = 0; x < w; x++, i++) {
        if (lum[i] <= 0.01) continue;
        const d = Math.hypot(Math.abs(x + 0.5 - w / 2) + 0.5, Math.abs(y + 0.5 - h / 2) + 0.5);
        if (d > rmax) rmax = d;
      }
    }
    const rNorm = rmax / Math.max(w, h);
    const fit = rNorm > 0.5 ? 0.5 / rNorm : 1;
    const scale = res / Math.max(w, h) * fit;
    const ox = (res - w * scale) / 2, oy = (res - h * scale) / 2;
    const taps = clamp(Math.ceil(1 / scale), 1, 8);
    const data = new Float32Array(res * res);
    for (let y = 0; y < res; y++) {
      for (let x = 0; x < res; x++) {
        let sum = 0;
        for (let j = 0; j < taps; j++) {
          const sy = (y + (j + 0.5) / taps - oy) / scale;
          if (sy < 0 || sy >= h) continue;
          for (let i = 0; i < taps; i++) {
            const sx = (x + (i + 0.5) / taps - ox) / scale;
            if (sx < 0 || sx >= w) continue;
            sum += bilinear(lum, w, h, sx - 0.5, sy - 0.5);
          }
        }
        data[y * res + x] = sum / (taps * taps);
      }
    }
    // id за вмістом: однакові картинки дають однакові ключі кешу
    let hash = 2166136261;
    for (let i = 0; i < data.length; i++) hash = Math.imul(hash ^ Math.round(data[i] * 255), 16777619);
    return { id: 'custom-' + (hash >>> 0).toString(16), label: 'Власна альфа', data, res };
  }

  // Білінійна вибірка за координатами текселів (з притисканням до країв).
  function bilinear(data, w, h, fx, fy) {
    let x0 = Math.floor(fx), y0 = Math.floor(fy);
    const tx = fx - x0, ty = fy - y0;
    let x1 = x0 + 1, y1 = y0 + 1;
    x0 = x0 < 0 ? 0 : (x0 > w - 1 ? w - 1 : x0); x1 = x1 < 0 ? 0 : (x1 > w - 1 ? w - 1 : x1);
    y0 = y0 < 0 ? 0 : (y0 > h - 1 ? h - 1 : y0); y1 = y1 < 0 ? 0 : (y1 > h - 1 ? h - 1 : y1);
    const top = data[y0 * w + x0] + (data[y0 * w + x1] - data[y0 * w + x0]) * tx;
    const bot = data[y1 * w + x0] + (data[y1 * w + x1] - data[y1 * w + x0]) * tx;
    return top + (bot - top) * ty;
  }

  // ---------------------------------------------------------------- Штампи (getDab) + кеш

  const dabCache = new Map(); // ключ -> { w, h, data } (Map зберігає порядок вставки = LRU)
  let cacheFloats = 0;

  function cacheGet(key) {
    const d = dabCache.get(key);
    if (d) { dabCache.delete(key); dabCache.set(key, d); }
    return d;
  }

  function cachePut(key, dab) {
    dabCache.set(key, dab);
    cacheFloats += dab.data.length;
    // найстаріші витісняються за кількістю та за сумарним обсягом (свіжий штамп лишається завжди)
    for (const [k, v] of dabCache) {
      if (dabCache.size <= CACHE_MAX_ENTRIES && cacheFloats <= CACHE_MAX_FLOATS) break;
      if (v === dab) break;
      dabCache.delete(k);
      cacheFloats -= v.data.length;
    }
  }

  function clearCache() { dabCache.clear(); cacheFloats = 0; }

  function resolveAlpha(alpha) {
    if (typeof alpha === 'string') {
      const def = ALPHA_BY_ID[alpha];
      if (!def) throw new Error('Невідома альфа: ' + alpha);
      return { id: def.id, data: def.id === 'round' ? null : baseData(def), res: BASE_RES };
    }
    if (alpha && alpha.data && alpha.res > 0 && alpha.data.length === alpha.res * alpha.res) {
      if (alpha.id === 'round' && ALPHA_BY_ID.round) return { id: 'round', data: null, res: BASE_RES };
      return { id: alpha.id || null, data: alpha.data, res: alpha.res };
    }
    throw new Error('Некоректна альфа: очікується id зі списку ALPHAS або результат alphaFromImage');
  }

  // Растеризує штамп діаметра size px, повернутий на angle (за годинниковою в координатах зображення).
  // Не змінюйте повернутий об'єкт — він розділяється кешем.
  function getDab(alpha, sizePx, angleRad, hardness) {
    const a = resolveAlpha(alpha);
    if (!(sizePx > 0) || !isFinite(sizePx)) throw new Error('Некоректний розмір штампа: ' + sizePx);
    const isRound = a.id === 'round';
    const hard = clamp(hardness === undefined ? 0.8 : hardness, 0, 1);
    // квантування: розмір округлюється вгору до 0.25 px (ceil не змінюється), кут — до 1°, м'якість — до 0.02
    const qs = Math.ceil(Math.min(sizePx, MAX_DAB) * 4);
    const qa = isRound ? 0 : ((Math.round((angleRad || 0) / DEG) % 360) + 360) % 360;
    const qh = isRound ? Math.round(hard * 50) : 0;
    const key = a.id ? a.id + '|' + qs + '|' + qa + '|' + qh : null;
    if (key) { const hit = cacheGet(key); if (hit) return hit; }

    const size = qs / 4, ang = qa * DEG;
    const w = Math.max(1, Math.ceil(size) + 2), h = w;
    const data = new Float32Array(w * h);
    const cx = w / 2, cy = h / 2;
    if (isRound) {
      const hq = qh / 50, pxPerR = size / 2;
      let i = 0;
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) data[i++] = discValue(Math.hypot(x + 0.5 - cx, y + 0.5 - cy) / pxPerR, hq, pxPerR);
      }
    } else {
      const res = a.res, src = a.data;
      const cos = Math.cos(ang), sin = Math.sin(ang);
      const n = clamp(Math.ceil(res / size), 2, 8); // суперсемплінг для дрібних штампів
      const inv = 1 / size, norm = 1 / (n * n);
      let i = 0;
      for (let y = 0; y < h; y++) {
        for (let x = 0; x < w; x++) {
          let sum = 0;
          for (let j = 0; j < n; j++) {
            const Y = y + (j + 0.5) / n - cy;
            for (let k = 0; k < n; k++) {
              const X = x + (k + 0.5) / n - cx;
              const u = (X * cos + Y * sin) * inv + 0.5, v = (-X * sin + Y * cos) * inv + 0.5;
              if (u < 0 || u >= 1 || v < 0 || v >= 1) continue; // поза штампом — прозоро
              sum += bilinear(src, res, res, u * res - 0.5, v * res - 0.5);
            }
          }
          data[i++] = clamp(sum * norm, 0, 1);
        }
      }
    }
    const dab = { w, h, data };
    if (key) cachePut(key, dab);
    return dab;
  }

  // ---------------------------------------------------------------- Потік мазків

  // Мазок: { x, y, size, angle, flow }. Відстань між мазками рівномірна за довжиною дуги,
  // залишок між викликами move переноситься, тож густота не залежить від швидкості руху.
  function createStroke(opts) {
    const o = opts || {};
    const num = (v, d) => (typeof v === 'number' && isFinite(v) ? v : d);
    const size = Math.max(MIN_DAB, num(o.size, 20));
    const spacing = num(o.spacing, 0.15) > 0 ? num(o.spacing, 0.15) : 0.15;
    const baseFlow = clamp(num(o.flow, 1), 0, 1);
    const sizeJitter = clamp(num(o.sizeJitter, 0), 0, 1);
    const baseAngle = num(o.angle, 0);
    const angleJitter = clamp(num(o.angleJitter, 0), 0, 1);
    const followPath = !!o.followPath;
    const scatter = clamp(num(o.scatter, 0), 0, 1);
    const symX = !!o.symmetryX, symY = !!o.symmetryY;
    const pressureSize = !!o.pressureSize, pressureFlow = !!o.pressureFlow;
    const rng = mulberry32(o.seed === undefined ? 1 : o.seed);
    let docW = 0, docH = 0;
    if (symX || symY) {
      const ds = o.docSize;
      if (typeof ds === 'number' && ds > 0) { docW = docH = ds; }
      else if (ds && ds.w > 0 && ds.h > 0) { docW = ds.w; docH = ds.h; }
      else throw new Error('Для симетрії потрібен docSize (розмір текстури в пікселях)');
    }

    let started = false, lx = 0, ly = 0, lp = 1, toNext = 0;

    const stepFor = (p) => Math.max(MIN_STEP, spacing * size * (pressureSize ? p : 1));

    function emit(x, y, p, dir, out) {
      // завжди 4 випадкові числа на мазок — послідовність не залежить від увімкнених опцій
      const r0 = rng(), r1 = rng(), r2 = rng(), r3 = rng();
      const pr = clamp(p, 0, 1);
      const sz = Math.max(MIN_DAB, size * (pressureSize ? pr : 1) * (1 - sizeJitter * r0));
      const ang = baseAngle + (followPath ? dir : 0) + angleJitter * Math.PI * (2 * r1 - 1);
      if (scatter > 0) {
        const sa = r2 * Math.PI * 2, sr = scatter * sz * Math.sqrt(r3);
        x += Math.cos(sa) * sr; y += Math.sin(sa) * sr;
      }
      const flow = clamp(baseFlow * (pressureFlow ? pr : 1), 0, 1);
      out.push({ x, y, size: sz, angle: ang, flow });
      if (symX) out.push({ x: docW - x, y, size: sz, angle: Math.PI - ang, flow });
      if (symY) out.push({ x, y: docH - y, size: sz, angle: -ang, flow });
      if (symX && symY) out.push({ x: docW - x, y: docH - y, size: sz, angle: Math.PI + ang, flow });
    }

    function begin(x, y, p) {
      const out = [];
      if (!isFinite(x) || !isFinite(y)) return out;
      const pr = p === undefined ? 1 : p;
      started = true; lx = x; ly = y; lp = pr;
      emit(x, y, pr, 0, out); // напрямку ще немає — followPath починається з наступного мазка
      toNext = stepFor(clamp(pr, 0, 1));
      return out;
    }

    function move(x, y, p) {
      if (!started) return begin(x, y, p);
      const out = [];
      if (!isFinite(x) || !isFinite(y)) return out;
      const pr = p === undefined ? 1 : p;
      const dx = x - lx, dy = y - ly, len = Math.hypot(dx, dy);
      if (len < 1e-9) { lp = pr; return out; }
      const dir = Math.atan2(dy, dx);
      let at = toNext;
      while (at <= len + EPS) {
        const t = Math.min(at / len, 1);
        const pp = lp + (pr - lp) * t;
        emit(lx + dx * t, ly + dy * t, pp, dir, out);
        at += stepFor(clamp(pp, 0, 1));
      }
      toNext = at - len;
      lx = x; ly = y; lp = pr;
      return out;
    }

    function end() {
      started = false;
      return [];
    }

    return { begin, move, end };
  }

  // Пряма лінія (Shift+клік): мазки від (x0,y0) до (x1,y1) з тими самими опціями.
  function line(opts, x0, y0, x1, y1) {
    const s = createStroke(opts);
    const out = s.begin(x0, y0, 1);
    const rest = s.move(x1, y1, 1);
    for (let i = 0; i < rest.length; i++) out.push(rest[i]);
    const tail = s.end();
    for (let i = 0; i < tail.length; i++) out.push(tail[i]);
    return out;
  }

  // ---------------------------------------------------------------- Заливка області

  function boxBlurAxis(src, dst, count, len, stride, lineStride, r) {
    const inv = 1 / (2 * r + 1);
    const wrap = (i) => ((i % len) + len) % len;
    for (let l = 0; l < count; l++) {
      const base = l * lineStride;
      let sum = 0;
      for (let k = -r; k <= r; k++) sum += src[base + wrap(k) * stride];
      for (let i = 0; i < len; i++) {
        dst[base + i * stride] = sum * inv;
        sum += src[base + wrap(i + r + 1) * stride] - src[base + wrap(i - r) * stride];
      }
    }
  }

  // Розмиття маски з загортанням країв (2 проходи box-фільтра по кожній осі).
  function featherMask(mask, w, h, softEdge) {
    const r = Math.max(1, Math.round(softEdge / 2));
    let a = new Float32Array(mask), b = new Float32Array(mask.length);
    for (let pass = 0; pass < 2; pass++) {
      boxBlurAxis(a, b, h, w, 1, w, r);
      boxBlurAxis(b, a, w, h, w, 1, r);
    }
    const out = new Uint8Array(mask.length);
    for (let i = 0; i < out.length; i++) out[i] = Math.round(clamp(a[i], 0, 255));
    return out;
  }

  // Заливка області схожого кольору (як «чарівна паличка» / відро): 4-сусідство, краї загортаються.
  // size — сторона квадратної текстури (або { w, h }). Повертає Uint8Array: 255 в області, 0 поза нею.
  function floodRegion(rgba, size, x, y, tolerance, opts) {
    const w = typeof size === 'number' ? size : size && size.w;
    const h = typeof size === 'number' ? size : size && size.h;
    if (!Number.isInteger(w) || !Number.isInteger(h) || w < 1 || h < 1) throw new Error('Некоректний розмір текстури для заливки');
    const n = w * h;
    if (!rgba || rgba.length !== n * 4) throw new Error('Розмір буфера не відповідає розмірам текстури ' + w + '×' + h);
    const tol = tolerance === undefined ? 32 : Math.max(0, tolerance);
    const sx = ((Math.floor(x) % w) + w) % w, sy = ((Math.floor(y) % h) + h) % h;
    const si = sy * w + sx;
    const r0 = rgba[si * 4], g0 = rgba[si * 4 + 1], b0 = rgba[si * 4 + 2];
    const mask = new Uint8Array(n);
    const stack = new Int32Array(n); // піксель потрапляє в стек не більше одного разу
    let sp = 0;
    mask[si] = 255; stack[sp++] = si;
    const test = (j) => {
      if (mask[j]) return;
      const p = j * 4;
      if (Math.abs(rgba[p] - r0) <= tol && Math.abs(rgba[p + 1] - g0) <= tol && Math.abs(rgba[p + 2] - b0) <= tol) {
        mask[j] = 255; stack[sp++] = j;
      }
    };
    while (sp > 0) {
      const i = stack[--sp];
      const px = i % w, py = (i - px) / w;
      test(px > 0 ? i - 1 : i + w - 1);
      test(px + 1 < w ? i + 1 : i - px);
      test(py > 0 ? i - w : i + (h - 1) * w);
      test(py + 1 < h ? i + w : px);
    }
    const soft = opts && opts.softEdge;
    return soft > 0 ? featherMask(mask, w, h, soft) : mask;
  }

  const api = { ALPHAS, BASE_RES, alphaFromImage, getDab, clearCache, createStroke, line, floodRegion };
  RA3.brush = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
