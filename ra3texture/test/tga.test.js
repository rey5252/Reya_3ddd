const test = require('node:test'); const assert = require('node:assert');
const tga = require('../js/tga.js');

// Збирає TGA: 18-байтовий заголовок + (опційно) ID-поле + піксельні байти.
// type: 2 (нестиснений) / 10 (RLE); bpp: 24 / 32; topLeft: біт 5 дескриптора (початок угорі).
// extra: { idLen: байти ID-поля, rightToLeft: біт 4 дескриптора, colorMap: { entries, entrySize } }.
function makeTGA(type, bpp, topLeft, w, h, pixelBytes, extra = {}) {
  const idLen = extra.idLen || 0;
  const cm = extra.colorMap || null;
  const cmBytes = cm ? cm.entries * (cm.entrySize >> 3) : 0;
  const b = new Uint8Array(18 + idLen + cmBytes + pixelBytes.length); const dv = new DataView(b.buffer);
  b[0] = idLen; b[1] = cm ? 1 : 0; b[2] = type;
  if (cm) { dv.setUint16(5, cm.entries, true); b[7] = cm.entrySize; }
  dv.setUint16(12, w, true); dv.setUint16(14, h, true); b[16] = bpp;
  b[17] = (bpp === 32 ? 8 : 0) | (topLeft ? 0x20 : 0) | (extra.rightToLeft ? 0x10 : 0);
  for (let i = 0; i < idLen; i++) b[18 + i] = 0xee;                 // сміття в ID-полі
  for (let i = 0; i < cmBytes; i++) b[18 + idLen + i] = 0xdd;       // сміття в палітрі
  b.set(pixelBytes, 18 + idLen + cmBytes);
  return b;
}

test('uncompressed 32-bit top-left', () => {
  const t = tga.decodeTGA(makeTGA(2, 32, true, 2, 1, [0, 0, 255, 128, 30, 20, 10, 255]));
  assert.strictEqual(t.width, 2); assert.strictEqual(t.height, 1);
  assert.ok(t.data instanceof Uint8ClampedArray); assert.strictEqual(t.data.length, 8);
  assert.deepStrictEqual([...t.data], [255, 0, 0, 128, 10, 20, 30, 255]); });

test('uncompressed 24-bit gets alpha 255', () => {
  const t = tga.decodeTGA(makeTGA(2, 24, true, 2, 1, [1, 2, 3, 4, 5, 6]));
  assert.deepStrictEqual([...t.data], [3, 2, 1, 255, 6, 5, 4, 255]); });

test('bottom-left origin flips rows', () => {
  // перший записаний рядок (червоний) — нижній у зображенні
  const t = tga.decodeTGA(makeTGA(2, 32, false, 1, 2, [0, 0, 255, 255, 255, 0, 0, 255]));
  assert.deepStrictEqual([...t.data], [0, 0, 255, 255, 255, 0, 0, 255]);   // верх: синій, низ: червоний
  const f = tga.decodeTGA(makeTGA(2, 32, true, 1, 2, [0, 0, 255, 255, 255, 0, 0, 255]));
  assert.deepStrictEqual([...f.data], [255, 0, 0, 255, 0, 0, 255, 255]); });   // top-left: порядок збережено

test('right-to-left origin flips columns', () => {
  const t = tga.decodeTGA(makeTGA(2, 24, true, 3, 1, [1, 0, 0, 2, 0, 0, 3, 0, 0], { rightToLeft: true }));
  assert.deepStrictEqual([...t.data], [0, 0, 3, 255, 0, 0, 2, 255, 0, 0, 1, 255]);
  // разом із bottom-left: 2×2, записано (0,0)=1 (1,0)=2 / (0,1)=3 (1,1)=4 у синьому каналі
  const b = tga.decodeTGA(makeTGA(2, 24, false, 2, 2, [1, 0, 0, 2, 0, 0, 3, 0, 0, 4, 0, 0], { rightToLeft: true }));
  assert.deepStrictEqual([...b.data].filter((_, i) => i % 4 === 2), [4, 3, 2, 1]); });

test('RLE packet repeats', () => {
  const t = tga.decodeTGA(makeTGA(10, 24, true, 4, 1, [0x83, 0, 255, 0]));
  assert.strictEqual(t.width, 4);
  assert.deepStrictEqual([...t.data], [0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255]); });

test('RLE mixes raw and run packets, runs may span rows', () => {
  // 2×2 top-left, 32-bit: raw(2 px) + run(2 px)
  const t = tga.decodeTGA(makeTGA(10, 32, true, 2, 2, [0x01, 0, 0, 255, 255, 0, 255, 0, 255, 0x81, 255, 0, 0, 128]));
  assert.deepStrictEqual([...t.data], [255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 128, 0, 0, 255, 128]);
  // 1×2 bottom-left: один пакет-повтор на 2 пікселі, обидва рядки однакові
  const s = tga.decodeTGA(makeTGA(10, 24, false, 1, 2, [0x81, 9, 8, 7]));
  assert.deepStrictEqual([...s.data], [7, 8, 9, 255, 7, 8, 9, 255]);
  // bottom-left: raw-пакет із двох різних пікселів
  const f = tga.decodeTGA(makeTGA(10, 24, false, 1, 2, [0x01, 1, 1, 1, 2, 2, 2]));
  assert.deepStrictEqual([...f.data], [2, 2, 2, 255, 1, 1, 1, 255]); });

test('skips the ID field and an embedded colour map', () => {
  const px = [0, 0, 255, 128];
  assert.deepStrictEqual([...tga.decodeTGA(makeTGA(2, 32, true, 1, 1, px, { idLen: 11 })).data], [255, 0, 0, 128]);
  assert.deepStrictEqual([...tga.decodeTGA(makeTGA(2, 32, true, 1, 1, px, { idLen: 5, colorMap: { entries: 4, entrySize: 24 } })).data], [255, 0, 0, 128]);
  assert.deepStrictEqual([...tga.decodeTGA(makeTGA(10, 24, true, 2, 1, [0x81, 0, 255, 0], { idLen: 3 })).data], [0, 255, 0, 255, 0, 255, 0, 255]); });

test('accepts ArrayBuffer and typed-array views', () => {
  const b = makeTGA(2, 32, true, 1, 1, [0, 0, 255, 128]);
  assert.deepStrictEqual([...tga.decodeTGA(b.buffer.slice(0)).data], [255, 0, 0, 128]);
  const padded = new Uint8Array(b.length + 7); padded.set(b, 5);
  assert.deepStrictEqual([...tga.decodeTGA(padded.subarray(5, 5 + b.length)).data], [255, 0, 0, 128]); });

test('rejects colormapped', () => {
  assert.throws(() => tga.decodeTGA(makeTGA(1, 24, true, 1, 1, [0])), /Непідтримуваний TGA/); });

test('rejects unsupported image type, bit depth and sizes', () => {
  assert.throws(() => tga.decodeTGA(makeTGA(3, 24, true, 1, 1, [0, 0, 0])), /Непідтримуваний TGA/);   // grayscale
  assert.throws(() => tga.decodeTGA(makeTGA(2, 16, true, 1, 1, [0, 0])), /Непідтримуваний TGA/);
  assert.throws(() => tga.decodeTGA(makeTGA(2, 24, true, 0, 1, [])), /Непідтримуваний TGA/);
  assert.throws(() => tga.decodeTGA(makeTGA(2, 24, true, 1, 0, [])), /Непідтримуваний TGA/); });

test('truncated data throws', () => {
  assert.throws(() => tga.decodeTGA(makeTGA(2, 32, true, 2, 2, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])), /Непідтримуваний TGA: обірвані дані/);
  assert.throws(() => tga.decodeTGA(makeTGA(10, 24, true, 4, 1, [0x83, 0, 255])), /Непідтримуваний TGA: обірвані дані/);
  assert.throws(() => tga.decodeTGA(makeTGA(10, 24, true, 4, 1, [0x01, 1, 2, 3])), /Непідтримуваний TGA: обірвані дані/);   // пакет обіцяє 2 пікселі
  assert.throws(() => tga.decodeTGA(makeTGA(10, 24, true, 4, 1, [0x81, 0, 255, 0])), /Непідтримуваний TGA: обірвані дані/);   // пікселів менше, ніж w*h
  assert.throws(() => tga.decodeTGA(new Uint8Array(10)), /Непідтримуваний TGA: обірвані дані/);
  assert.throws(() => tga.decodeTGA(makeTGA(2, 24, true, 1, 1, [0, 0, 0], { idLen: 40 }).subarray(0, 30)), /Непідтримуваний TGA: обірвані дані/); });

test('rejects non-binary input', () => {
  assert.throws(() => tga.decodeTGA('TRUEVISION'), /Непідтримуваний TGA/);
  assert.throws(() => tga.decodeTGA(null), /Непідтримуваний TGA/); });
