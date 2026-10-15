const test = require('node:test'); const assert = require('node:assert');
const dds = require('../js/dds.js');
const solid = (s, rgba) => { const d = new Uint8ClampedArray(s * s * 4); for (let i = 0; i < d.length; i += 4) d.set(rgba, i); return d; };
const u32 = (b, o) => new DataView(b.buffer, b.byteOffset).getUint32(o, true);

// Збирає 128-байтовий DDS-заголовок для тестів декодера (поля, яких немає в опціях, лишаються нулями).
function makeHeader({ w, h, fourCC, masks, pfFlags, bitCount = 0, mipCount = 1 }) {
  const b = new Uint8Array(128); const dv = new DataView(b.buffer);
  dv.setUint32(0, 0x20534444, true); dv.setUint32(4, 124, true);
  dv.setUint32(8, 0x1 | 0x2 | 0x4 | 0x1000, true);
  dv.setUint32(12, h, true); dv.setUint32(16, w, true); dv.setUint32(28, mipCount, true);
  dv.setUint32(76, 32, true); dv.setUint32(80, pfFlags, true);
  if (fourCC) for (let i = 0; i < 4; i++) b[84 + i] = fourCC.charCodeAt(i);
  dv.setUint32(88, bitCount, true);
  if (masks) { dv.setUint32(92, masks.r, true); dv.setUint32(96, masks.g, true); dv.setUint32(100, masks.b, true); dv.setUint32(104, masks.a, true); }
  dv.setUint32(108, 0x1000, true);
  return b;
}
const concat = (...parts) => { const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0)); let o = 0; for (const p of parts) { out.set(p, o); o += p.length; } return out; };

test('header fields DXT1 with mips', () => {
  const lv = dds.buildMips(solid(64, [255, 0, 0, 255]), 64); assert.strictEqual(lv.length, 7);
  const b = dds.encodeDDS(lv, 'DXT1');
  assert.strictEqual(String.fromCharCode(...b.slice(0, 4)), 'DDS ');
  assert.strictEqual(u32(b, 4), 124); assert.strictEqual(u32(b, 12), 64); assert.strictEqual(u32(b, 16), 64);
  assert.strictEqual(u32(b, 28), 7); assert.strictEqual(String.fromCharCode(...b.slice(84, 88)), 'DXT1');
  // рівні 64,32,16,8,4,2,1 -> блоків 256,64,16,4,1,1,1 по 8 байт
  assert.strictEqual(b.length, 128 + 2048 + 512 + 128 + 32 + 8 + 8 + 8); });
test('solid color roundtrip exact in 565', () => {
  for (const f of ['DXT1', 'DXT5', 'A8R8G8B8']) {
    const d = dds.decodeDDS(dds.encodeDDS(dds.buildMips(solid(16, [255, 0, 0, 255]), 16), f));
    assert.deepStrictEqual([...d.data.slice(0, 4)], [255, 0, 0, 255], f); assert.strictEqual(d.format, f); } });
test('gradient DXT1 mean error <= 8', () => {
  const s = 64, d = new Uint8ClampedArray(s * s * 4);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) d.set([x * 4, y * 4, 128, 255], (y * s + x) * 4);
  const out = dds.decodeDDS(dds.encodeDDS([{ width: s, height: s, data: d }], 'DXT1')).data;
  let e = 0; for (let i = 0; i < d.length; i++) if (i % 4 !== 3) e += Math.abs(d[i] - out[i]);
  assert.ok(e / (s * s * 3) <= 8); });
test('DXT5 keeps alpha levels', () => {
  const d = solid(8, [10, 20, 30, 255]); for (let i = 3; i < 64; i += 4) d[i] = 0;
  const out = dds.decodeDDS(dds.encodeDDS([{ width: 8, height: 8, data: d }], 'DXT5')).data;
  assert.strictEqual(out[3], 0); assert.strictEqual(out[64 * 4 - 1], 255); assert.ok(dds.hasAlpha(d)); });
test('tiny mips 2x2 and 1x1 encode and decode', () => {
  const lv = dds.buildMips(solid(4, [0, 255, 0, 255]), 4); assert.deepStrictEqual(lv.map(l => l.width), [4, 2, 1]);
  const b = dds.encodeDDS(lv, 'DXT5'); assert.strictEqual(b.length, 128 + 16 * 3);
  const one = dds.decodeDDS(dds.encodeDDS([lv[2]], 'DXT1')); assert.strictEqual(one.width, 1); });
test('normal map mips are renormalized', () => {
  const d = solid(4, [255, 128, 128, 255]); for (let i = 0; i < 32; i += 4) d.set([0, 128, 128, 255], i);
  const lv = dds.buildMips(d, 4, { normalMap: true }); const p = lv[2].data;
  const v = [0, 1, 2].map(k => p[k] / 255 * 2 - 1); assert.ok(Math.abs(Math.hypot(...v) - 1) < 0.05); });
test('async encode equals sync and reports progress', async () => {
  const lv = dds.buildMips(solid(256, [1, 2, 3, 255]), 256); let calls = 0;
  const a = await dds.encodeDDSAsync(lv, 'DXT1', () => calls++);
  assert.deepStrictEqual(a, dds.encodeDDS(lv, 'DXT1')); assert.ok(calls > 0); });
test('rejects non-DDS and unsupported', () => {
  assert.throws(() => dds.decodeDDS(new Uint8Array(128)), /не DDS/);
  const b = dds.encodeDDS([{ width: 4, height: 4, data: solid(4, [0, 0, 0, 255]) }], 'DXT1');
  b.set([65, 84, 73, 50], 84); assert.throws(() => dds.decodeDDS(b), /Непідтримуваний/); });
test('decodes DXT3 and 32-bit BGRX', () => {
  // DXT3 4×4: alpha bytes 0x88 ×8, color [0x00,0xF8,0x00,0xF8,0,0,0,0] -> піксель 0 = [255,0,0,136]
  const dxt3 = concat(makeHeader({ w: 4, h: 4, fourCC: 'DXT3', pfFlags: 0x4 }),
    new Uint8Array([0x88, 0x88, 0x88, 0x88, 0x88, 0x88, 0x88, 0x88, 0x00, 0xF8, 0x00, 0xF8, 0, 0, 0, 0]));
  const a = dds.decodeDDS(dxt3);
  assert.strictEqual(a.format, 'DXT3'); assert.strictEqual(a.width, 4); assert.strictEqual(a.height, 4);
  assert.deepStrictEqual([...a.data.slice(0, 4)], [255, 0, 0, 136]);
  // X8R8G8B8 2×2: pf flags 0x40, bitcount 32, masks R 0xff0000 G 0xff00 B 0xff A 0,
  //   пікселі BGRX [30,20,10,0] -> піксель 0 = [10,20,30,255]
  const bgrx = concat(makeHeader({ w: 2, h: 2, pfFlags: 0x40, bitCount: 32, masks: { r: 0xff0000, g: 0xff00, b: 0xff, a: 0 } }),
    new Uint8Array([30, 20, 10, 0, 30, 20, 10, 0, 30, 20, 10, 0, 30, 20, 10, 0]));
  const b = dds.decodeDDS(bgrx);
  assert.strictEqual(b.width, 2); assert.strictEqual(b.height, 2); assert.strictEqual(b.mipCount, 1);
  assert.deepStrictEqual([...b.data.slice(0, 4)], [10, 20, 30, 255]); });

// ---- Додаткові перевірки гілок декодера й кодера, яких не покривають тести з брифу ----

test('A8R8G8B8 header and BGRA byte order', () => {
  const d = new Uint8ClampedArray([10, 20, 30, 40, 50, 60, 70, 80]);
  const b = dds.encodeDDS([{ width: 2, height: 1, data: d }], 'A8R8G8B8');
  assert.strictEqual(b.length, 128 + 8);
  assert.strictEqual(u32(b, 8), 0x1 | 0x2 | 0x4 | 0x1000 | 0x20000 | 0x8);
  assert.strictEqual(u32(b, 20), 2 * 4); assert.strictEqual(u32(b, 76), 32);
  assert.strictEqual(u32(b, 80), 0x41); assert.strictEqual(u32(b, 88), 32);
  assert.strictEqual(u32(b, 92), 0x00ff0000); assert.strictEqual(u32(b, 96), 0x0000ff00);
  assert.strictEqual(u32(b, 100), 0x000000ff); assert.strictEqual(u32(b, 104), 0xff000000);
  assert.strictEqual(u32(b, 108), 0x1000);
  assert.deepStrictEqual([...b.slice(128)], [30, 20, 10, 40, 70, 60, 50, 80]); });
test('DXT header flags, linear size and caps', () => {
  const lv = dds.buildMips(solid(16, [0, 0, 0, 255]), 16);
  const b = dds.encodeDDS(lv, 'DXT5');
  assert.strictEqual(u32(b, 8), 0x1 | 0x2 | 0x4 | 0x1000 | 0x20000 | 0x80000);
  assert.strictEqual(u32(b, 20), 16 * 16); assert.strictEqual(u32(b, 80), 0x4);
  assert.strictEqual(String.fromCharCode(...b.slice(84, 88)), 'DXT5');
  assert.strictEqual(u32(b, 108), 0x1000 | 0x400000 | 0x8);
  const single = dds.encodeDDS([lv[0]], 'DXT1');
  assert.strictEqual(u32(single, 108), 0x1000); assert.strictEqual(u32(single, 28), 1); });
test('mip chain is a 2x2 box average', () => {
  const d = new Uint8ClampedArray([0, 0, 0, 255, 100, 0, 0, 255, 0, 100, 0, 255, 0, 0, 100, 0]);
  const lv = dds.buildMips(d, 2);
  assert.deepStrictEqual(lv.map(l => [l.width, l.height]), [[2, 2], [1, 1]]);
  assert.deepStrictEqual([...lv[1].data], [25, 25, 25, 191]); });
test('normal map mips: zero average vector falls back to (0,0,1), no NaN', () => {
  // (1,1,1) і (-1,-1,-1) (у байтах 255 та 0) в сумі дають рівно нульовий вектор
  const w = new Uint8ClampedArray(2 * 2 * 4);
  w.set([255, 255, 255, 255], 0); w.set([0, 0, 0, 255], 4); w.set([255, 255, 255, 255], 8); w.set([0, 0, 0, 255], 12);
  const lv = dds.buildMips(w, 2, { normalMap: true });
  assert.deepStrictEqual([...lv[1].data], [128, 128, 255, 255]); }); // (0,0,1) -> 127.5 округлюється до 128
test('edge blocks (size not multiple of 4) roundtrip', () => {
  for (const f of ['DXT1', 'DXT5']) {
    const d = dds.decodeDDS(dds.encodeDDS([{ width: 6, height: 5, data: new Uint8ClampedArray(6 * 5 * 4).fill(0).map((_, i) => i % 4 === 3 ? 255 : (i % 4 === 0 ? 255 : 0)) }], f));
    assert.strictEqual(d.width, 6); assert.strictEqual(d.height, 5); assert.strictEqual(d.data.length, 6 * 5 * 4);
    for (let i = 0; i < d.data.length; i += 4) assert.deepStrictEqual([...d.data.slice(i, i + 4)], [255, 0, 0, 255], f + ' px ' + i / 4); } });
test('decodeDDS reports mip count and accepts ArrayBuffer', () => {
  const b = dds.encodeDDS(dds.buildMips(solid(8, [0, 0, 255, 255]), 8), 'DXT1');
  const d = dds.decodeDDS(b.buffer.slice(b.byteOffset, b.byteOffset + b.length));
  assert.strictEqual(d.mipCount, 4); assert.strictEqual(d.width, 8); assert.strictEqual(d.format, 'DXT1');
  assert.deepStrictEqual([...d.data.slice(0, 4)], [0, 0, 255, 255]); });
test('DXT1 decodes 3-color mode with transparent index 3', () => {
  // c0=0x0000 (чорний) <= c1=0xF800 (червоний): режим 3 кольори; індекси: піксель0=0, 1=1, 2=2 (середина), 3=3 (прозорий)
  const body = new Uint8Array([0x00, 0x00, 0x00, 0xF8, 0b11100100, 0, 0, 0]);
  const d = dds.decodeDDS(concat(makeHeader({ w: 4, h: 4, fourCC: 'DXT1', pfFlags: 0x4 }), body)).data;
  assert.deepStrictEqual([...d.slice(0, 4)], [0, 0, 0, 255]);
  assert.deepStrictEqual([...d.slice(4, 8)], [255, 0, 0, 255]);
  assert.deepStrictEqual([...d.slice(8, 12)], [127, 0, 0, 255]);
  assert.deepStrictEqual([...d.slice(12, 16)], [0, 0, 0, 0]); });
test('DXT5 decodes 6-level alpha mode (a0 <= a1) with explicit 0 and 255', () => {
  // a0=0, a1=255; індекси першого ряду: 0,1,6,7 -> 0, 255, 0 (індекс 6 = 0), 255 (індекс 7)
  // 3-бітні індекси LE: пікселі 0..3 = 0,1,6,7 -> біти 000 001 110 111 -> (6<<6)|(7<<9)|(1<<3) = 0xF88
  const alpha = new Uint8Array([0, 255, 0x88, 0x0F, 0, 0, 0, 0]);
  const color = new Uint8Array([0x00, 0xF8, 0x00, 0xF8, 0, 0, 0, 0]);
  const d = dds.decodeDDS(concat(makeHeader({ w: 4, h: 4, fourCC: 'DXT5', pfFlags: 0x4 }), alpha, color)).data;
  assert.deepStrictEqual([d[3], d[7], d[11], d[15]], [0, 255, 0, 255]); });
test('decodes 24-bit RGB and 32-bit ARGB', () => {
  const rgb24 = concat(makeHeader({ w: 2, h: 1, pfFlags: 0x40, bitCount: 24, masks: { r: 0xff0000, g: 0xff00, b: 0xff, a: 0 } }),
    new Uint8Array([3, 2, 1, 6, 5, 4]));
  assert.deepStrictEqual([...dds.decodeDDS(rgb24).data], [1, 2, 3, 255, 4, 5, 6, 255]);
  const argb = concat(makeHeader({ w: 1, h: 1, pfFlags: 0x41, bitCount: 32, masks: { r: 0xff0000, g: 0xff00, b: 0xff, a: 0xff000000 } }),
    new Uint8Array([30, 20, 10, 99]));
  const r = dds.decodeDDS(argb); assert.strictEqual(r.format, 'A8R8G8B8'); assert.deepStrictEqual([...r.data], [10, 20, 30, 99]);
  // довільні маски: RGBA-порядок
  const rgba = concat(makeHeader({ w: 1, h: 1, pfFlags: 0x41, bitCount: 32, masks: { r: 0xff, g: 0xff00, b: 0xff0000, a: 0xff000000 } }),
    new Uint8Array([10, 20, 30, 99]));
  assert.deepStrictEqual([...dds.decodeDDS(rgba).data], [10, 20, 30, 99]); });
test('rejects truncated data and bad header', () => {
  const b = dds.encodeDDS([{ width: 8, height: 8, data: solid(8, [1, 2, 3, 255]) }], 'DXT5');
  assert.throws(() => dds.decodeDDS(b.slice(0, b.length - 1)), /обірван/);
  assert.throws(() => dds.decodeDDS(new Uint8Array(10)), /не DDS/);
  assert.throws(() => dds.decodeDDS(concat(makeHeader({ w: 4, h: 4, pfFlags: 0x20, bitCount: 8 }), new Uint8Array(16))), /Непідтримуваний формат DDS/); });
test('async encode equals sync for DXT5 and A8R8G8B8 (multi-chunk)', async () => {
  const s = 512, d = new Uint8ClampedArray(s * s * 4);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) d.set([x & 255, y & 255, (x * y) & 255, 255 - (x & 255)], (y * s + x) * 4);
  const lv = dds.buildMips(d, s);
  for (const f of ['DXT5', 'A8R8G8B8']) {
    const fr = []; const a = await dds.encodeDDSAsync(lv, f, p => fr.push(p));
    assert.deepStrictEqual(a, dds.encodeDDS(lv, f), f); assert.ok(fr.length > 1, f);
    assert.strictEqual(fr[fr.length - 1], 1); for (let i = 1; i < fr.length; i++) assert.ok(fr[i] >= fr[i - 1] && fr[i] <= 1); } });

// ---- Fix round 1: регресії кодувальника блоків ----

// Кодує один блок 4×4 (функція кольору (x,y) -> [r,g,b,a]) і повертає розкодовані RGBA.
const roundtripBlock = (fn, fmt) => {
  const d = new Uint8ClampedArray(64);
  for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) d.set(fn(x, y), (y * 4 + x) * 4);
  return { src: d, out: dds.decodeDDS(dds.encodeDDS([{ width: 4, height: 4, data: d }], fmt)).data };
};
const assertBlockClose = (src, out, tol, msg) => {
  for (let i = 0; i < 64; i++) if (i % 4 !== 3) assert.ok(Math.abs(src[i] - out[i]) <= tol,
    `${msg}: px ${i >> 2} ch ${i & 3}: src ${src[i]} out ${out[i]}`);
};

test('hue-only block: red/green checker keeps both colours (deviation orthogonal to grey axis)', () => {
  const { src, out } = roundtripBlock((x, y) => ((x + y) & 1 ? [0, 255, 0, 255] : [255, 0, 0, 255]), 'DXT1');
  assertBlockClose(src, out, 8, 'red/green checker'); });
test('hue-only block: SPM-style red/blue edge keeps both colours', () => {
  const { src, out } = roundtripBlock(x => (x < 2 ? [255, 0, 0, 255] : [0, 0, 255, 255]), 'DXT1');
  assertBlockClose(src, out, 8, 'red/blue edge'); });
test('hue-only block with non-axis-aligned chroma direction (constant R+G+B)', () => {
  const a = [200, 100, 50, 255], b = [150, 150, 50, 255]; // R+G+B == 350 у обох
  const { src, out } = roundtripBlock((x, y) => ((x + y) & 1 ? a : b), 'DXT1');
  assertBlockClose(src, out, 8, 'chroma-only pair'); });

test('DXT5 alpha ramp: index bit packing is correct for all 16 pixels', () => {
  const { out } = roundtripBlock((x, y) => [40, 80, 120, (y * 4 + x) * 17], 'DXT5');
  let prev = -1;
  for (let i = 0; i < 16; i++) {
    const a = out[i * 4 + 3];
    assert.ok(Math.abs(a - i * 17) <= 18, `px ${i}: alpha ${a} vs ${i * 17}`);
    assert.ok(a >= prev, `px ${i}: alpha ${a} < previous ${prev}`); prev = a; } });

test('hard two-tone blocks decode to exact extremes (no inset loss)', () => {
  const bw = roundtripBlock(x => (x < 2 ? [0, 0, 0, 255] : [255, 255, 255, 255]), 'DXT1');
  assertBlockClose(bw.src, bw.out, 0, 'black/white');
  const rb = roundtripBlock((x, y) => (y < 2 ? [255, 0, 0, 255] : [0, 0, 255, 255]), 'DXT1');
  assertBlockClose(rb.src, rb.out, 0, 'red/blue halves');
  const mask = roundtripBlock(x => (x < 3 ? [200, 0, 255, 255] : [200, 0, 0, 255]), 'DXT1'); // SPM: B=255 край маски
  for (let y = 0; y < 4; y++) assert.strictEqual(mask.out[(y * 4) * 4 + 2], 255, 'mask B stays 255'); });
test('DXT5 two-tone block is exact in color too', () => {
  const { src, out } = roundtripBlock(x => (x < 2 ? [0, 0, 0, 255] : [255, 255, 255, 255]), 'DXT5');
  assertBlockClose(src, out, 0, 'DXT5 black/white'); });
