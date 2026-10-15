(function (RA3) { 'use strict';
  const HEADER_SIZE = 18;
  const MAX_DIM = 16384;
  const ERR = 'Непідтримуваний TGA: ';
  const TRUNCATED = ERR + 'обірвані дані';

  function toBytes(input) {
    if (input instanceof ArrayBuffer) return new Uint8Array(input);
    if (ArrayBuffer.isView(input)) return new Uint8Array(input.buffer, input.byteOffset, input.byteLength);
    throw new Error(ERR + 'очікується ArrayBuffer або Uint8Array');
  }

  // Нестиснені пікселі: BGR(A) -> RGBA у порядку файлу.
  function readRaw(bytes, pos, count, bpp, out) {
    if (pos + count * bpp > bytes.length) throw new Error(TRUNCATED);
    let o = 0;
    for (let i = 0; i < count; i++, pos += bpp, o += 4) {
      out[o] = bytes[pos + 2]; out[o + 1] = bytes[pos + 1]; out[o + 2] = bytes[pos];
      out[o + 3] = bpp === 4 ? bytes[pos + 3] : 255;
    }
  }

  // RLE: потік пакетів (біт 7 — повтор, решта + 1 — кількість). Пакети можуть перетинати межі рядків,
  // тому пікселі пишуться суцільним потоком; зайве в останньому пакеті відкидається.
  function readRLE(bytes, pos, count, bpp, out) {
    let n = 0, o = 0;
    while (n < count) {
      if (pos >= bytes.length) throw new Error(TRUNCATED);
      const head = bytes[pos++];
      const len = Math.min((head & 0x7f) + 1, count - n);
      if (head & 0x80) {
        if (pos + bpp > bytes.length) throw new Error(TRUNCATED);
        const r = bytes[pos + 2], g = bytes[pos + 1], b = bytes[pos], a = bpp === 4 ? bytes[pos + 3] : 255;
        pos += bpp;
        for (let i = 0; i < len; i++, o += 4) { out[o] = r; out[o + 1] = g; out[o + 2] = b; out[o + 3] = a; }
      } else {
        if (pos + len * bpp > bytes.length) throw new Error(TRUNCATED);
        for (let i = 0; i < len; i++, pos += bpp, o += 4) {
          out[o] = bytes[pos + 2]; out[o + 1] = bytes[pos + 1]; out[o + 2] = bytes[pos];
          out[o + 3] = bpp === 4 ? bytes[pos + 3] : 255;
        }
      }
      n += len;
    }
  }

  // Приводить порядок файлу до «початок угорі зліва»: біт 5 = 0 — рядки знизу вгору, біт 4 = 1 — стовпці справа наліво.
  function orient(lin, w, h, topDown, rightToLeft) {
    if (topDown && !rightToLeft) return lin;
    const out = new Uint8ClampedArray(lin.length), rowLen = w * 4;
    for (let y = 0; y < h; y++) {
      const src = (topDown ? y : h - 1 - y) * rowLen, dst = y * rowLen;
      if (!rightToLeft) { out.set(lin.subarray(src, src + rowLen), dst); continue; }
      for (let x = 0; x < w; x++) {
        const s = src + (w - 1 - x) * 4, d = dst + x * 4;
        out[d] = lin[s]; out[d + 1] = lin[s + 1]; out[d + 2] = lin[s + 2]; out[d + 3] = lin[s + 3];
      }
    }
    return out;
  }

  function decodeTGA(input) {
    const bytes = toBytes(input);
    if (bytes.length < HEADER_SIZE) throw new Error(TRUNCATED);
    const idLen = bytes[0], cmType = bytes[1], type = bytes[2];
    const cmLen = bytes[5] | (bytes[6] << 8), cmEntryBits = bytes[7];
    const width = bytes[12] | (bytes[13] << 8), height = bytes[14] | (bytes[15] << 8);
    const bpp = bytes[16], desc = bytes[17];

    if (type === 1 || type === 9) throw new Error(ERR + 'зображення з палітрою (тип ' + type + ')');
    if (type !== 2 && type !== 10) throw new Error(ERR + 'тип зображення ' + type + ' (потрібен 2 або 10)');
    if (cmType > 1) throw new Error(ERR + 'невідомий тип палітри ' + cmType);
    if (bpp !== 24 && bpp !== 32) throw new Error(ERR + 'глибина ' + bpp + ' біт (потрібно 24 або 32)');
    if (width < 1 || height < 1 || width > MAX_DIM || height > MAX_DIM) throw new Error(ERR + 'розміри ' + width + '×' + height);

    // Піксельні дані йдуть після ID-поля та (якщо є) палітри, яку для типів 2/10 просто пропускаємо.
    const pos = HEADER_SIZE + idLen + (cmType === 1 ? cmLen * ((cmEntryBits + 7) >> 3) : 0);
    const count = width * height, bytesPerPixel = bpp >> 3;
    const lin = new Uint8ClampedArray(count * 4);
    if (type === 2) readRaw(bytes, pos, count, bytesPerPixel, lin);
    else readRLE(bytes, pos, count, bytesPerPixel, lin);

    return { width, height, data: orient(lin, width, height, !!(desc & 0x20), !!(desc & 0x10)) };
  }

  const api = { decodeTGA };
  RA3.tga = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
