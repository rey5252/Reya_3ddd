(function (RA3) { 'use strict';

  function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

  function lerp(a, b, t) { return a + (b - a) * t; }

  function smoothstep(e0, e1, x) {
    if (e0 === e1) return x < e0 ? 0 : 1;
    const t = clamp((x - e0) / (e1 - e0), 0, 1);
    return t * t * (3 - 2 * t);
  }

  function isPow2(n) { return Number.isInteger(n) && n > 0 && (n & (n - 1)) === 0; }

  // Детермінований генератор, рівномірний у [0,1).
  function mulberry32(seed) {
    let a = seed | 0;
    return function () {
      a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  // Цілочисельний хеш вузла решітки (seed, октава, x, y) -> [0,1). Без виділення пам'яті.
  function lattice(seed, o, x, y) {
    let h = (seed | 0) ^ Math.imul(o + 1, 0x632BE5AB);
    h = Math.imul(h ^ x, 0x85EBCA6B); h ^= h >>> 13;
    h = Math.imul(h ^ y, 0xC2B2AE35); h ^= h >>> 16;
    h = Math.imul(h, 0x27D4EB2F); h ^= h >>> 15;
    return (h >>> 0) / 4294967296;
  }

  // Тайлова фрактальна value-noise. u,v ∈ [0,1); результат у [0,1].
  // Решітка октави o має розмір freq·2^o і загортається по модулю цього розміру,
  // тому шум безшовно тайлиться по обох осях. Амплітуда кожної наступної октави вдвічі менша.
  function fbm(seed, u, v, freq, octaves) {
    const n = octaves < 1 ? 1 : octaves | 0;
    let sum = 0, norm = 0, amp = 1;
    for (let o = 0; o < n; o++) {
      const size = freq * (1 << o);
      const x = u * size, y = v * size;
      const fx0 = Math.floor(x), fy0 = Math.floor(y);
      let tx = x - fx0, ty = y - fy0;
      const x0 = ((fx0 % size) + size) % size, y0 = ((fy0 % size) + size) % size;
      const x1 = x0 + 1 === size ? 0 : x0 + 1, y1 = y0 + 1 === size ? 0 : y0 + 1;
      tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
      const a = lattice(seed, o, x0, y0), b = lattice(seed, o, x1, y0);
      const c = lattice(seed, o, x0, y1), d = lattice(seed, o, x1, y1);
      const top = a + (b - a) * tx, bot = c + (d - c) * tx;
      sum += (top + (bot - top) * ty) * amp;
      norm += amp;
      amp *= 0.5;
    }
    return sum / norm;
  }

  function hexToRgb(hex) {
    const m = /^#?([0-9a-fA-F]{6})$/.exec(String(hex).trim());
    if (!m) throw new Error('Некоректний колір: ' + hex + ' (очікується #rrggbb)');
    const n = parseInt(m[1], 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  function rgbToHex(rgb) {
    let s = '#';
    for (let i = 0; i < 3; i++) {
      const c = Math.round(clamp(rgb[i], 0, 255));
      s += (c < 16 ? '0' : '') + c.toString(16);
    }
    return s;
  }

  function luminance(r, g, b) { return 0.299 * r + 0.587 * g + 0.114 * b; }

  // Білінійний ресемплінг RGBA (центри пікселів, clamp на краях).
  function resampleRGBA(src, sw, sh, dw, dh) {
    if (src.length !== sw * sh * 4) throw new Error('Розмір буфера не відповідає розмірам зображення ' + sw + '×' + sh);
    const out = new Uint8ClampedArray(dw * dh * 4);
    const xa = new Int32Array(dw), xb = new Int32Array(dw), xw = new Float64Array(dw);
    for (let x = 0; x < dw; x++) {
      const sx = clamp((x + 0.5) * sw / dw - 0.5, 0, sw - 1);
      const x0 = Math.floor(sx);
      xa[x] = x0 * 4; xb[x] = Math.min(x0 + 1, sw - 1) * 4; xw[x] = sx - x0;
    }
    let o = 0;
    for (let y = 0; y < dh; y++) {
      const sy = clamp((y + 0.5) * sh / dh - 0.5, 0, sh - 1);
      const y0 = Math.floor(sy), wy = sy - y0;
      const r0 = y0 * sw * 4, r1 = Math.min(y0 + 1, sh - 1) * sw * 4;
      for (let x = 0; x < dw; x++) {
        const a = xa[x], b = xb[x], wx = xw[x];
        for (let c = 0; c < 4; c++) {
          const top = src[r0 + a + c] + (src[r0 + b + c] - src[r0 + a + c]) * wx;
          const bot = src[r1 + a + c] + (src[r1 + b + c] - src[r1 + a + c]) * wx;
          out[o++] = top + (bot - top) * wy;
        }
      }
    }
    return out;
  }

  const api = { clamp, lerp, smoothstep, isPow2, mulberry32, fbm, hexToRgb, rgbToHex, luminance, resampleRGBA };
  RA3.util = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
