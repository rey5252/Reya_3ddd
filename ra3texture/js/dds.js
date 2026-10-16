(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');
  const clamp = util.clamp;

  const DDS_MAGIC = 0x20534444;      // 'DDS '
  const HEADER_SIZE = 128;           // magic + DDS_HEADER (124)
  const FOURCC = { DXT1: 0x31545844, DXT3: 0x33545844, DXT5: 0x35545844 };
  const MAX_DIM = 16384;

  // ---------------------------------------------------------------- mip chain

  function hasAlpha(rgba) {
    for (let i = 3; i < rgba.length; i += 4) if (rgba[i] < 255) return true;
    return false;
  }

  // Box 2×2 (з притисненням координат на краю для непарних розмірів).
  function downsample(src, w, h, normalMap) {
    const nw = Math.max(1, w >> 1), nh = Math.max(1, h >> 1);
    const dst = new Uint8ClampedArray(nw * nh * 4);
    let o = 0;
    for (let y = 0; y < nh; y++) {
      const y0 = Math.min(y * 2, h - 1) * w, y1 = Math.min(y * 2 + 1, h - 1) * w;
      for (let x = 0; x < nw; x++) {
        const x0 = Math.min(x * 2, w - 1), x1 = Math.min(x * 2 + 1, w - 1);
        const a = (y0 + x0) * 4, b = (y0 + x1) * 4, c = (y1 + x0) * 4, d = (y1 + x1) * 4;
        if (normalMap) {
          let vx = (src[a] + src[b] + src[c] + src[d]) / 1020 * 2 - 1;
          let vy = (src[a + 1] + src[b + 1] + src[c + 1] + src[d + 1]) / 1020 * 2 - 1;
          let vz = (src[a + 2] + src[b + 2] + src[c + 2] + src[d + 2]) / 1020 * 2 - 1;
          const len = Math.sqrt(vx * vx + vy * vy + vz * vz);
          if (len < 1e-6) { vx = 0; vy = 0; vz = 1; }
          else { vx /= len; vy /= len; vz /= len; }
          dst[o] = Math.round((vx * 0.5 + 0.5) * 255); dst[o + 1] = Math.round((vy * 0.5 + 0.5) * 255); dst[o + 2] = Math.round((vz * 0.5 + 0.5) * 255);
        } else {
          dst[o] = (src[a] + src[b] + src[c] + src[d] + 2) >> 2;
          dst[o + 1] = (src[a + 1] + src[b + 1] + src[c + 1] + src[d + 1] + 2) >> 2;
          dst[o + 2] = (src[a + 2] + src[b + 2] + src[c + 2] + src[d + 2] + 2) >> 2;
        }
        dst[o + 3] = (src[a + 3] + src[b + 3] + src[c + 3] + src[d + 3] + 2) >> 2;
        o += 4;
      }
    }
    return dst;
  }

  // Рівень 0 — це переданий масив (без копії); решта рівнів нові.
  function buildMips(rgba, size, opts) {
    const normalMap = !!(opts && opts.normalMap);
    if (!(size >= 1) || rgba.length !== size * size * 4) throw new Error('Некоректні розміри текстури для mip-рівнів');
    const levels = [{ width: size, height: size, data: rgba }];
    let w = size, h = size, data = rgba;
    while (w > 1 || h > 1) {
      data = downsample(data, w, h, normalMap);
      w = Math.max(1, w >> 1); h = Math.max(1, h >> 1);
      levels.push({ width: w, height: h, data });
    }
    return levels;
  }

  // ------------------------------------------------------------ block encoding

  // Спільні буфери блоку: безпечні, бо блок кодується синхронно від початку до кінця.
  const bpx = new Int32Array(48);    // 16 пікселів × RGB
  const bal = new Int32Array(16);    // альфа 16 пікселів
  const apal = new Int32Array(8);    // палітра альфи

  function fetchBlock(data, w, h, bx, by) {
    const x0 = bx * 4, y0 = by * 4;
    let k = 0, a = 0;
    for (let j = 0; j < 4; j++) {
      const yy = y0 + j < h ? y0 + j : h - 1;
      const row = yy * w;
      for (let i = 0; i < 4; i++) {
        const xx = x0 + i < w ? x0 + i : w - 1;
        const s = (row + xx) * 4;
        bpx[k++] = data[s]; bpx[k++] = data[s + 1]; bpx[k++] = data[s + 2];
        bal[a++] = data[s + 3];
      }
    }
  }

  function q5(v) { return clamp(Math.round(v * 31 / 255), 0, 31); }
  function q6(v) { return clamp(Math.round(v * 63 / 255), 0, 63); }

  // «Оптимальний один колір» (як у stb_dxt): для кожного 8-бітного v — пара кінців [a, b] (5 чи 6 біт),
  // у якої колір палітри (2·a + b) / 3, як його відновлює декодер, найближчий до v. Штраф 3% розкиду кінців
  // (допуск інтерполяції в DX10) і менший розкид при рівності: тоді й декодер з округленням дає ±1.
  // Так однотонний блок, колір якого лежить між рівнями 565 (рівна нормаль 128,128,255), не зсувається.
  function singleColorTable(bits, expand) {
    const n = 1 << bits, t = new Uint8Array(512);
    for (let v = 0; v < 256; v++) {
      let best = Infinity, bestSpread = Infinity;
      for (let a = 0; a < n; a++) {
        for (let b = 0; b < n; b++) {
          const ea = expand(a), eb = expand(b), spread = Math.abs(ea - eb);
          const err = Math.abs((((2 * ea + eb) / 3) | 0) - v) + ((spread * 3 / 100) | 0);
          if (err < best || (err === best && spread < bestSpread)) { best = err; bestSpread = spread; t[v * 2] = a; t[v * 2 + 1] = b; }
        }
      }
    }
    return t;
  }
  const SINGLE5 = singleColorTable(5, expand5), SINGLE6 = singleColorTable(6, expand6);
  const SINGLE_RANGE = 24;   // розмах блока вздовж головної осі, до якого пробуємо один колір

  let single0 = 0, single1 = 0, singleBits = 0;   // кінці й індекси, знайдені останнім singleEndpoints()

  // Кінці для кольору (r, g, b) з таблиць, упорядковані до c0 > c1. Колір дає індекс 2 (2/3·c0 + 1/3·c1),
  // а якщо кінці довелося переставити — індекс 3 (той самий колір). c0 === c1 — колір точний, індекс 0.
  function singleEndpoints(r, g, b) {
    let s0 = (SINGLE5[r * 2] << 11) | (SINGLE6[g * 2] << 5) | SINGLE5[b * 2];
    let s1 = (SINGLE5[r * 2 + 1] << 11) | (SINGLE6[g * 2 + 1] << 5) | SINGLE5[b * 2 + 1];
    singleBits = 0xAAAAAAAA;
    if (s0 < s1) { const t = s0; s0 = s1; s1 = t; singleBits = 0xFFFFFFFF; }
    else if (s0 === s1) singleBits = 0;
    single0 = s0; single1 = s1;
  }

  let scoreBits = 0;   // індекси, знайдені останнім scoreEndpoints()

  function pack565(mr, mg, mb, ax, ay, az, t) {
    return (q5(mr + ax * t) << 11) | (q6(mg + ay * t) << 5) | q5(mb + az * t);
  }

  // Призначає кожному пікселю з bpx найближчий колір палітри {c0, c1, 2/3·c0+1/3·c1, 1/3·c0+2/3·c1}
  // (палітра така, як її відновить декодер; c0 >= c1 у 565). Повертає сумарну квадратичну похибку,
  // а 32 біти індексів кладе в scoreBits. При c0 === c1 усі індекси 0.
  function scoreEndpoints(c0, c1) {
    const r0 = expand5(c0 >> 11), g0 = expand6((c0 >> 5) & 63), b0 = expand5(c0 & 31);
    let err = 0, bits = 0;
    if (c0 === c1) {
      for (let p = 0; p < 48; p += 3) {
        const dr = bpx[p] - r0, dg = bpx[p + 1] - g0, db = bpx[p + 2] - b0;
        err += dr * dr + dg * dg + db * db;
      }
      scoreBits = 0;
      return err;
    }
    const r1 = expand5(c1 >> 11), g1 = expand6((c1 >> 5) & 63), b1 = expand5(c1 & 31);
    const r2 = ((2 * r0 + r1) / 3) | 0, g2 = ((2 * g0 + g1) / 3) | 0, b2 = ((2 * b0 + b1) / 3) | 0;
    const r3 = ((r0 + 2 * r1) / 3) | 0, g3 = ((g0 + 2 * g1) / 3) | 0, b3 = ((b0 + 2 * b1) / 3) | 0;
    for (let p = 15; p >= 0; p--) {
      const r = bpx[p * 3], g = bpx[p * 3 + 1], b = bpx[p * 3 + 2];
      let dr = r - r0, dg = g - g0, db = b - b0;
      let best = dr * dr + dg * dg + db * db, idx = 0;
      dr = r - r1; dg = g - g1; db = b - b1;
      let d = dr * dr + dg * dg + db * db;
      if (d < best) { best = d; idx = 1; }
      dr = r - r2; dg = g - g2; db = b - b2;
      d = dr * dr + dg * dg + db * db;
      if (d < best) { best = d; idx = 2; }
      dr = r - r3; dg = g - g3; db = b - b3;
      d = dr * dr + dg * dg + db * db;
      if (d < best) { best = d; idx = 3; }
      err += best;
      bits = ((bits << 2) | idx) >>> 0;
    }
    scoreBits = bits;
    return err;
  }

  // Кодує колірний 4×4 блок із bpx у out[o..o+7].
  function encodeColorBlock(out, o) {
    // Однотонний блок: одразу один колір. За таблицями він у кожному каналі не гірший за найближчий колір 565
    // (пара a = b теж є серед варіантів), тож порівнювати з іншими кандидатами не треба.
    let uniform = true;
    for (let i = 3; i < 48; i += 3) if (bpx[i] !== bpx[0] || bpx[i + 1] !== bpx[1] || bpx[i + 2] !== bpx[2]) { uniform = false; break; }
    if (uniform) { singleEndpoints(bpx[0], bpx[1], bpx[2]); writeColorBlock(out, o, single0, single1, singleBits); return; }

    let mr = 0, mg = 0, mb = 0;
    for (let i = 0; i < 48; i += 3) { mr += bpx[i]; mg += bpx[i + 1]; mb += bpx[i + 2]; }
    mr /= 16; mg /= 16; mb /= 16;

    // ковариація 3×3
    let xx = 0, xy = 0, xz = 0, yy = 0, yz = 0, zz = 0;
    for (let i = 0; i < 48; i += 3) {
      const r = bpx[i] - mr, g = bpx[i + 1] - mg, b = bpx[i + 2] - mb;
      xx += r * r; xy += r * g; xz += r * b; yy += g * g; yz += g * b; zz += b * b;
    }

    // головна вісь: степеневий метод, 8 ітерацій зі стартом (1,1,1).
    // Якщо відхилення блока ортогональні до (1,1,1), то C·v = 0 (напр. червоний/зелений):
    // беремо стовпець ковариації з найбільшою нормою, а якщо й він ~0 — діагональ bbox (max−min).
    const eps = 1e-6 * (xx + yy + zz);
    let ax = 1, ay = 1, az = 1;
    for (let it = 0; it < 8; it++) {
      let nx = xx * ax + xy * ay + xz * az;
      let ny = xy * ax + yy * ay + yz * az;
      let nz = xz * ax + yz * ay + zz * az;
      let len = Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (len <= eps) {
        const n0 = xx * xx + xy * xy + xz * xz, n1 = xy * xy + yy * yy + yz * yz, n2 = xz * xz + yz * yz + zz * zz;
        if (n0 >= n1 && n0 >= n2) { nx = xx; ny = xy; nz = xz; }
        else if (n1 >= n2) { nx = xy; ny = yy; nz = yz; }
        else { nx = xz; ny = yz; nz = zz; }
        len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len <= eps) {
          let rmin = 255, rmax = 0, gmin = 255, gmax = 0, bmin = 255, bmax = 0;
          for (let i = 0; i < 48; i += 3) {
            const r = bpx[i], g = bpx[i + 1], b = bpx[i + 2];
            if (r < rmin) rmin = r; if (r > rmax) rmax = r;
            if (g < gmin) gmin = g; if (g > gmax) gmax = g;
            if (b < bmin) bmin = b; if (b > bmax) bmax = b;
          }
          nx = rmax - rmin; ny = gmax - gmin; nz = bmax - bmin;
          len = Math.sqrt(nx * nx + ny * ny + nz * nz);
          if (len <= 0) break;   // однотонний блок: вісь неважлива
        }
      }
      ax = nx / len; ay = ny / len; az = nz / len;
    }

    // проєкції на вісь -> min/max
    let tmin = Infinity, tmax = -Infinity;
    for (let i = 0; i < 48; i += 3) {
      const t = (bpx[i] - mr) * ax + (bpx[i + 1] - mg) * ay + (bpx[i + 2] - mb) * az;
      if (t < tmin) tmin = t;
      if (t > tmax) tmax = t;
    }

    // Кандидат A: інсет (max−min)/16 з обох боків (план). Кандидат B: без інсету, точні min/max,
    // щоб жорсткі дво-тональні блоки (чорне/біле, маска гравця B=255) не втрачали крайні значення.
    // Лишаємо той, у кого менша сумарна квадратична похибка після призначення індексів.
    const inset = (tmax - tmin) / 16;
    let c0 = pack565(mr, mg, mb, ax, ay, az, tmax - inset), c1 = pack565(mr, mg, mb, ax, ay, az, tmin + inset);
    if (c0 < c1) { const t = c0; c0 = c1; c1 = t; }
    let bestErr = scoreEndpoints(c0, c1), bestBits = scoreBits;
    if (inset > 0 && bestErr > 0) {
      let e0 = pack565(mr, mg, mb, ax, ay, az, tmax), e1 = pack565(mr, mg, mb, ax, ay, az, tmin);
      if (e0 < e1) { const t = e0; e0 = e1; e1 = t; }
      if (e0 !== c0 || e1 !== c1) {
        const err = scoreEndpoints(e0, e1);
        if (err < bestErr) { bestErr = err; bestBits = scoreBits; c0 = e0; c1 = e1; }
      }
    }
    // Кандидат C для майже однотонного блока: середній колір через таблиці одного кольору.
    if (bestErr > 0 && tmax - tmin <= SINGLE_RANGE) {
      singleEndpoints(Math.round(mr), Math.round(mg), Math.round(mb));
      if (single0 !== c0 || single1 !== c1) {
        const err = scoreEndpoints(single0, single1);
        if (err < bestErr) { bestErr = err; bestBits = scoreBits; c0 = single0; c1 = single1; }
      }
    }

    writeColorBlock(out, o, c0, c1, bestBits);
  }

  function writeColorBlock(out, o, c0, c1, bits) {
    out[o] = c0 & 255; out[o + 1] = c0 >> 8; out[o + 2] = c1 & 255; out[o + 3] = c1 >> 8;
    out[o + 4] = bits & 255; out[o + 5] = (bits >>> 8) & 255; out[o + 6] = (bits >>> 16) & 255; out[o + 7] = bits >>> 24;
  }

  // Кодує альфу 4×4 блока з bal у out[o..o+7] (DXT5, 8-рівневий режим a0 > a1).
  function encodeAlphaBlock(out, o) {
    let amax = 0, amin = 255;
    for (let i = 0; i < 16; i++) { const a = bal[i]; if (a > amax) amax = a; if (a < amin) amin = a; }
    out[o] = amax; out[o + 1] = amin;
    if (amax === amin) { for (let i = 2; i < 8; i++) out[o + i] = 0; return; }
    fillAlphaPalette(apal, amax, amin);
    // 48 біт, little-endian: молодша та старша 24-бітні половини
    let lo = 0, hi = 0;
    for (let p = 7; p >= 0; p--) lo = (lo << 3) | nearestAlpha(bal[p]);
    for (let p = 15; p >= 8; p--) hi = (hi << 3) | nearestAlpha(bal[p]);
    out[o + 2] = lo & 255; out[o + 3] = (lo >> 8) & 255; out[o + 4] = (lo >> 16) & 255;
    out[o + 5] = hi & 255; out[o + 6] = (hi >> 8) & 255; out[o + 7] = (hi >> 16) & 255;
  }

  function nearestAlpha(a) {
    let best = 1 << 30, idx = 0;
    for (let k = 0; k < 8; k++) {
      const d = a - apal[k];
      const dd = d * d;
      if (dd < best) { best = dd; idx = k; }
    }
    return idx;
  }

  // Палітра альфи DXT5 так, як її відновлює декодер.
  function fillAlphaPalette(pal, a0, a1) {
    pal[0] = a0; pal[1] = a1;
    if (a0 > a1) {
      for (let k = 1; k <= 6; k++) pal[k + 1] = ((7 - k) * a0 + k * a1) / 7 | 0;
    } else {
      for (let k = 1; k <= 4; k++) pal[k + 1] = ((5 - k) * a0 + k * a1) / 5 | 0;
      pal[6] = 0; pal[7] = 255;
    }
  }

  function expand5(v) { return (v << 3) | (v >> 2); }
  function expand6(v) { return (v << 2) | (v >> 4); }

  // ------------------------------------------------------------------- header

  function levelSize(w, h, kind) {
    if (kind === 'A8R8G8B8') return w * h * 4;
    return Math.max(1, Math.ceil(w / 4)) * Math.max(1, Math.ceil(h / 4)) * (kind === 'DXT1' ? 8 : 16);
  }

  function writeHeader(out, levels, format) {
    const dv = new DataView(out.buffer, out.byteOffset, out.byteLength);
    const dxt = format !== 'A8R8G8B8';
    const w = levels[0].width, h = levels[0].height;
    dv.setUint32(0, DDS_MAGIC, true);
    dv.setUint32(4, 124, true);
    dv.setUint32(8, 0x1 | 0x2 | 0x4 | 0x1000 | 0x20000 | (dxt ? 0x80000 : 0x8), true);
    dv.setUint32(12, h, true);
    dv.setUint32(16, w, true);
    dv.setUint32(20, dxt ? levelSize(w, h, format) : w * 4, true);
    dv.setUint32(28, levels.length, true);
    dv.setUint32(76, 32, true);
    if (dxt) {
      dv.setUint32(80, 0x4, true);
      dv.setUint32(84, FOURCC[format], true);
    } else {
      dv.setUint32(80, 0x41, true);
      dv.setUint32(88, 32, true);
      dv.setUint32(92, 0x00ff0000, true); dv.setUint32(96, 0x0000ff00, true);
      dv.setUint32(100, 0x000000ff, true); dv.setUint32(104, 0xff000000, true);
    }
    dv.setUint32(108, levels.length > 1 ? (0x1000 | 0x400000 | 0x8) : 0x1000, true);
  }

  // ----------------------------------------------------------- level encoding

  // Відновлюваний кодувальник: кодує до maxRows «рядків блоків» (по 4 рядки пікселів)
  // і запам'ятовує позицію. Синхронний та асинхронний шляхи користуються тим самим step().
  function createEncoder(levels, format) {
    if (format !== 'DXT1' && format !== 'DXT5' && format !== 'A8R8G8B8') {
      throw new Error('Непідтримуваний формат кодування: ' + format);
    }
    if (!levels || !levels.length) throw new Error('Немає рівнів для кодування DDS');
    let total = HEADER_SIZE, totalRows = 0;
    for (const lv of levels) {
      if (!lv || !lv.data || !(lv.width >= 1) || !(lv.height >= 1) || lv.data.length !== lv.width * lv.height * 4) {
        throw new Error('Некоректний рівень текстури для кодування DDS');
      }
      total += levelSize(lv.width, lv.height, format);
      totalRows += Math.ceil(lv.height / 4);
    }
    const out = new Uint8Array(total);
    writeHeader(out, levels, format);
    const st = { out, totalRows, rowsDone: 0, done: false, li: 0, by: 0, off: HEADER_SIZE };

    st.step = function (maxRows) {
      let n = 0;
      while (n < maxRows && st.li < levels.length) {
        const lv = levels[st.li], w = lv.width, h = lv.height, by = st.by;
        if (format === 'A8R8G8B8') st.off = copyBgraRows(out, st.off, lv.data, w, h, by);
        else st.off = encodeBlockRow(out, st.off, lv.data, w, h, by, format === 'DXT5');
        n++; st.rowsDone++;
        if (++st.by >= Math.ceil(h / 4)) { st.by = 0; st.li++; }
      }
      st.done = st.li >= levels.length;
      return n;
    };
    return st;
  }

  function encodeBlockRow(out, off, data, w, h, by, withAlpha) {
    const bw = Math.max(1, Math.ceil(w / 4));
    for (let bx = 0; bx < bw; bx++) {
      fetchBlock(data, w, h, bx, by);
      if (withAlpha) { encodeAlphaBlock(out, off); off += 8; }
      encodeColorBlock(out, off); off += 8;
    }
    return off;
  }

  function copyBgraRows(out, off, data, w, h, by) {
    const y1 = Math.min(by * 4 + 4, h);
    let s = by * 4 * w * 4;
    for (let y = by * 4; y < y1; y++) {
      for (let x = 0; x < w; x++) {
        out[off] = data[s + 2]; out[off + 1] = data[s + 1]; out[off + 2] = data[s]; out[off + 3] = data[s + 3];
        off += 4; s += 4;
      }
    }
    return off;
  }

  function encodeDDS(levels, format) {
    const enc = createEncoder(levels, format);
    enc.step(Infinity);
    return enc.out;
  }

  function encodeDDSAsync(levels, format, onProgress) {
    return new Promise(function (resolve, reject) {
      let enc;
      try { enc = createEncoder(levels, format); } catch (e) { reject(e); return; }
      const report = typeof onProgress === 'function' ? onProgress : null;
      function tick() {
        try {
          enc.step(64);
          if (report) report(enc.rowsDone / enc.totalRows);
          if (enc.done) resolve(enc.out); else setTimeout(tick, 0);
        } catch (e) { reject(e); }
      }
      setTimeout(tick, 0);
    });
  }

  // ----------------------------------------------------------------- decoding

  const FMT_ERR = 'Непідтримуваний формат DDS: ';

  function shiftOf(mask) { let s = 0; while (s < 32 && !((mask >>> s) & 1)) s++; return s; }
  function bitsOf(mask) { const s = shiftOf(mask); let n = 0; while (s + n < 32 && ((mask >>> (s + n)) & 1)) n++; return n; }

  // Канал за довільною маскою -> 0..255
  function channelReader(mask) {
    if (!mask) return null;
    const shift = shiftOf(mask), bits = bitsOf(mask), max = Math.pow(2, bits) - 1;
    return { mask, shift, max, direct: bits === 8 };
  }

  function decodeDDS(input) {
    let bytes;
    if (input instanceof ArrayBuffer) bytes = new Uint8Array(input);
    else if (ArrayBuffer.isView(input)) bytes = new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
    else throw new Error('Це не DDS-файл');
    if (bytes.length < HEADER_SIZE) throw new Error('Це не DDS-файл');
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    if (dv.getUint32(0, true) !== DDS_MAGIC || dv.getUint32(4, true) !== 124) throw new Error('Це не DDS-файл');

    const flags = dv.getUint32(8, true);
    const height = dv.getUint32(12, true), width = dv.getUint32(16, true);
    if (width < 1 || height < 1 || width > MAX_DIM || height > MAX_DIM) throw new Error('Некоректні розміри DDS: ' + width + '×' + height);
    const mipCount = (flags & 0x20000) ? Math.max(1, dv.getUint32(28, true)) : 1;
    const pfFlags = dv.getUint32(80, true);
    const cc = String.fromCharCode(bytes[84], bytes[85], bytes[86], bytes[87]);
    const bitCount = dv.getUint32(88, true);
    const data = new Uint8ClampedArray(width * height * 4);

    if (pfFlags & 0x4) {                                     // DDPF_FOURCC
      if (cc !== 'DXT1' && cc !== 'DXT3' && cc !== 'DXT5') throw new Error(FMT_ERR + cc.replace(/[^\x20-\x7e]/g, '?'));
      if (bytes.length < HEADER_SIZE + levelSize(width, height, cc === 'DXT1' ? 'DXT1' : 'DXT5')) {
        throw new Error('DDS-файл обірваний: даних менше, ніж потрібно');
      }
      decodeDXT(bytes, HEADER_SIZE, width, height, cc, data);
      return { width, height, data, format: cc, mipCount };
    }

    if ((pfFlags & 0x40) && (bitCount === 32 || bitCount === 24)) {       // DDPF_RGB
      const rm = dv.getUint32(92, true), gm = dv.getUint32(96, true), bm = dv.getUint32(100, true);
      const am = (pfFlags & 0x1) ? dv.getUint32(104, true) : 0;
      if (!rm || !gm || !bm) throw new Error(FMT_ERR + 'RGB ' + bitCount + '-біт без масок каналів');
      const bpp = bitCount >> 3;
      if (bytes.length < HEADER_SIZE + width * height * bpp) throw new Error('DDS-файл обірваний: даних менше, ніж потрібно');
      decodeRGB(bytes, HEADER_SIZE, width, height, bpp, rm, gm, bm, am, data);
      const canon = rm === 0xff0000 && gm === 0xff00 && bm === 0xff && (am === 0 || am === 0xff000000);
      const format = !canon ? 'RGB' + bitCount : (bitCount === 24 ? 'R8G8B8' : (am ? 'A8R8G8B8' : 'X8R8G8B8'));
      return { width, height, data, format, mipCount };
    }

    throw new Error(FMT_ERR + (pfFlags & 0x40 ? 'RGB ' + bitCount + '-біт' : 'прапорці 0x' + pfFlags.toString(16)));
  }

  function decodeRGB(bytes, base, w, h, bpp, rm, gm, bm, am, out) {
    const R = channelReader(rm), G = channelReader(gm), B = channelReader(bm), A = channelReader(am);
    const get = (px, c) => {
      const v = (px & c.mask) >>> c.shift;
      return c.direct ? v : Math.round(v * 255 / c.max);
    };
    let s = base, o = 0;
    for (let i = 0; i < w * h; i++) {
      let px = bytes[s] | (bytes[s + 1] << 8) | (bytes[s + 2] << 16);
      if (bpp === 4) px |= bytes[s + 3] << 24;
      px >>>= 0;
      s += bpp;
      out[o] = get(px, R); out[o + 1] = get(px, G); out[o + 2] = get(px, B);
      out[o + 3] = A ? get(px, A) : 255;
      o += 4;
    }
  }

  function decodeDXT(bytes, base, w, h, fmt, out) {
    const bw = Math.max(1, Math.ceil(w / 4)), bh = Math.max(1, Math.ceil(h / 4));
    const blockBytes = fmt === 'DXT1' ? 8 : 16;
    const col = new Int32Array(16);        // палітра: 4 × RGBA
    const ap = new Int32Array(8);
    let p = base;
    for (let by = 0; by < bh; by++) {
      for (let bx = 0; bx < bw; bx++, p += blockBytes) {
        const c = fmt === 'DXT1' ? p : p + 8;
        const c0 = bytes[c] | (bytes[c + 1] << 8), c1 = bytes[c + 2] | (bytes[c + 3] << 8);
        const r0 = expand5(c0 >> 11), g0 = expand6((c0 >> 5) & 63), b0 = expand5(c0 & 31);
        const r1 = expand5(c1 >> 11), g1 = expand6((c1 >> 5) & 63), b1 = expand5(c1 & 31);
        col[0] = r0; col[1] = g0; col[2] = b0; col[3] = 255;
        col[4] = r1; col[5] = g1; col[6] = b1; col[7] = 255;
        if (c0 > c1 || fmt !== 'DXT1') {
          col[8] = ((2 * r0 + r1) / 3) | 0; col[9] = ((2 * g0 + g1) / 3) | 0; col[10] = ((2 * b0 + b1) / 3) | 0; col[11] = 255;
          col[12] = ((r0 + 2 * r1) / 3) | 0; col[13] = ((g0 + 2 * g1) / 3) | 0; col[14] = ((b0 + 2 * b1) / 3) | 0; col[15] = 255;
        } else {                           // 3 кольори + прозорий чорний
          col[8] = (r0 + r1) >> 1; col[9] = (g0 + g1) >> 1; col[10] = (b0 + b1) >> 1; col[11] = 255;
          col[12] = 0; col[13] = 0; col[14] = 0; col[15] = 0;
        }
        const bits = (bytes[c + 4] | (bytes[c + 5] << 8) | (bytes[c + 6] << 16) | (bytes[c + 7] << 24)) >>> 0;

        let a0 = 0, a1 = 0, alo = 0, ahi = 0;
        if (fmt === 'DXT5') {
          a0 = bytes[p]; a1 = bytes[p + 1];
          fillAlphaPalette(ap, a0, a1);
          alo = bytes[p + 2] | (bytes[p + 3] << 8) | (bytes[p + 4] << 16);
          ahi = bytes[p + 5] | (bytes[p + 6] << 8) | (bytes[p + 7] << 16);
        }

        for (let j = 0; j < 4; j++) {
          const y = by * 4 + j; if (y >= h) break;
          for (let i = 0; i < 4; i++) {
            const x = bx * 4 + i; if (x >= w) break;
            const k = j * 4 + i, ci = ((bits >>> (k * 2)) & 3) * 4, o = (y * w + x) * 4;
            out[o] = col[ci]; out[o + 1] = col[ci + 1]; out[o + 2] = col[ci + 2];
            if (fmt === 'DXT1') out[o + 3] = col[ci + 3];
            else if (fmt === 'DXT3') {
              const nib = (bytes[p + (k >> 1)] >> ((k & 1) * 4)) & 15;
              out[o + 3] = nib * 17;
            } else {
              const idx = k < 8 ? (alo >> (k * 3)) & 7 : (ahi >> ((k - 8) * 3)) & 7;
              out[o + 3] = ap[idx];
            }
          }
        }
      }
    }
  }

  const api = { buildMips, hasAlpha, encodeDDS, encodeDDSAsync, decodeDDS };
  RA3.dds = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
