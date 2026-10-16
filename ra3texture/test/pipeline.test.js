const test = require('node:test'); const assert = require('node:assert');
const dds = require('../js/dds.js'); const maps = require('../js/maps.js'); const g = require('../js/generators.js');

// Повний шлях експорту, як у «Експорт DDS»: демо-документ (ті самі шари, що в app.js) → карти → dds.encodeSet
// (вибір формату, mip-рівні, кодування) → читання назад dds.decodeDDS кожного рівня.
const S = 256, MIPS = 9;                                   // 256, 128, …, 1
function demo() { const d = maps.createDocument({ name: 'AUMyTank', size: S }); d.layers = g.demoLayers(); maps.rebuild(d, g); return d; }
const sources = d => ({ diffuse: maps.buildDiffuse(d), nrm: maps.buildNRM(d), spm: maps.buildSPM(d) });
const levelSize = (w, h, f) => (f === 'A8R8G8B8' ? w * h * 4 : Math.ceil(w / 4) * Math.ceil(h / 4) * (f === 'DXT1' ? 8 : 16));

// Окремий DDS на один рівень: заголовок файлу з розмірами рівня і mipCount = 1, дані — зріз рівня.
function splitLevels(bytes, format) {
  const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength), out = [];
  let w = dv.getUint32(16, true), h = dv.getUint32(12, true), off = 128;
  for (let k = 0; k < dv.getUint32(28, true); k++) {
    const n = levelSize(w, h, format), one = new Uint8Array(128 + n);
    one.set(bytes.subarray(0, 128)); one.set(bytes.subarray(off, off + n), 128);
    const odv = new DataView(one.buffer); odv.setUint32(12, h, true); odv.setUint32(16, w, true); odv.setUint32(28, 1, true);
    out.push({ w, h, bytes: one, data: bytes.subarray(off, off + n) });
    off += n; w = Math.max(1, w >> 1); h = Math.max(1, h >> 1); }
  assert.strictEqual(off, bytes.length, 'після останнього рівня даних не лишається');
  return out; }
// DXT1-блоки у 3-кольоровому режимі (c0 <= c1) з індексом 3 — це прозорий чорний посеред непрозорої текстури.
function transparentDxt1Blocks(data) {
  let bad = 0;
  for (let p = 0; p < data.length; p += 8) {
    const c0 = data[p] | (data[p + 1] << 8), c1 = data[p + 2] | (data[p + 3] << 8);
    const bits = (data[p + 4] | (data[p + 5] << 8) | (data[p + 6] << 16) | (data[p + 7] << 24)) >>> 0;
    if (c0 <= c1) for (let i = 0; i < 16; i++) if (((bits >>> (2 * i)) & 3) === 3) { bad++; break; } }
  return bad; }

async function checkSet(src, opts, want) {
  const progress = [];
  const files = await dds.encodeSet(src, S, Object.assign({ onProgress: (p, f) => progress.push([p, f.suffix]) }, opts));
  assert.deepStrictEqual(files.map(f => f.suffix), ['', '_NRM', '_SPM']);
  assert.deepStrictEqual(files.map(f => f.format), want);
  for (let i = 1; i < progress.length; i++) assert.ok(progress[i][0] >= progress[i - 1][0], 'прогрес не спадає');
  assert.strictEqual(progress[0][0], 0); assert.strictEqual(progress[progress.length - 1][0], 1);
  const keys = ['diffuse', 'nrm', 'spm'];
  files.forEach((f, k) => {
    const head = dds.decodeDDS(f.bytes);
    assert.strictEqual(head.width, S); assert.strictEqual(head.height, S); assert.strictEqual(head.mipCount, MIPS); assert.strictEqual(head.format, f.format);
    const ref = dds.buildMips(src[keys[k]], S, { normalMap: keys[k] === 'nrm' });
    splitLevels(f.bytes, f.format).forEach((lv, li) => {
      const label = f.suffix + ' ' + lv.w + '×' + lv.h, dec = dds.decodeDDS(lv.bytes), r = ref[li].data;
      assert.strictEqual(dec.width, ref[li].width, label);
      if (f.format === 'DXT1') assert.strictEqual(transparentDxt1Blocks(lv.data), 0, label + ': 3-кольорові блоки з прозорим індексом');
      let e = 0, emax = 0;
      for (let i = 0; i < r.length; i++) { const d = Math.abs(dec.data[i] - r[i]); e += d; if (d > emax) emax = d; }
      const mean = e / r.length;
      if (f.format === 'A8R8G8B8') assert.strictEqual(emax, 0, label + ': без стиснення без втрат');
      else assert.ok(mean <= 6, label + ': середня похибка ' + mean.toFixed(2)); }); });
  return files; }

test('export pipeline: demo set → DXT1 diffuse, NRM, SPM with full mips, decoded back within DXT tolerance', async () => {
  const d = demo(), src = sources(d);
  assert.ok(!dds.hasAlpha(src.diffuse));
  const files = await checkSet(src, {}, ['DXT1', 'DXT1', 'DXT1']);
  // Рівна ділянка NRM (увесь блок 4×4 = 128,128,255) після стиснення лишається рівною (у грі не нахиляється).
  const out = dds.decodeDDS(files[1].bytes).data, nrm = src.nrm, isFlat = i => nrm[i] === 128 && nrm[i + 1] === 128 && nrm[i + 2] === 255;
  let flatBlocks = 0;
  for (let by = 0; by < S; by += 4) for (let bx = 0; bx < S; bx += 4) {
    const px = []; for (let y = by; y < by + 4; y++) for (let x = bx; x < bx + 4; x++) px.push((y * S + x) * 4);
    if (!px.every(isFlat)) continue;
    flatBlocks++;
    for (const i of px) for (let k = 0; k < 3; k++) assert.ok(Math.abs(out[i + k] - nrm[i + k]) <= 1, 'рівний блок ' + bx + ',' + by + ' канал ' + k + ': ' + out[i + k]); }
  assert.ok(flatBlocks > 100, 'у демо є рівні блоки: ' + flatBlocks); });
test('export pipeline: transparency switches the diffuse to DXT5; NRM and SPM stay DXT1', async () => {
  const d = demo();
  for (let k = 0; k < 6; k++) maps.applyBrush(d, 'alpha', 30 + 40 * k, 60 + 25 * k, { radius: 12, hardness: 0.5, strength: 1, value: 0 });
  maps.rebuild(d, g);
  const src = sources(d); assert.ok(dds.hasAlpha(src.diffuse));
  await checkSet(src, {}, ['DXT5', 'DXT1', 'DXT1']); });
test('export pipeline: uncompressed writes A8R8G8B8 for all three, lossless on every level', async () => {
  await checkSet(sources(demo()), { uncompressed: true }, ['A8R8G8B8', 'A8R8G8B8', 'A8R8G8B8']); });
test('demoLayers: the demo stack (Allied base, panels, rivets, team stripes, then dirt), fresh objects each call', () => {
  const a = g.demoLayers(), b = g.demoLayers();
  assert.deepStrictEqual(a.map(l => l.type), ['base', 'panels', 'rivets', 'team', 'dirt']);
  assert.strictEqual(a[0].params.preset, 'allied'); assert.strictEqual(a[3].params.mode, 'stripes');
  assert.deepStrictEqual(a.map(l => l.seed), [1, 2, 3, 4, 5]); assert.ok(a.every(l => l.enabled));
  assert.notStrictEqual(a[0], b[0]); assert.notStrictEqual(a[0].params, b[0].params); assert.notStrictEqual(a[0].id, b[0].id); });
