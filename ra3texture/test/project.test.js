const test = require('node:test'); const assert = require('node:assert');
const zlib = require('node:zlib');
const L = require('../js/layers.js'); const g = require('../js/generators.js'); const p = require('../js/project.js');
const m = require('../js/maps.js');   // лише для побудови файлів версії 1

// ---- Допоміжне ----
const sameBytes = (a, b, msg) => { assert.ok(a instanceof Uint8ClampedArray, msg + ': тип'); assert.strictEqual(a.length, b.length, msg + ': довжина'); assert.ok(Buffer.from(a.buffer, a.byteOffset, a.length).equals(Buffer.from(b.buffer, b.byteOffset, b.length)), msg + ': вміст'); };
const noise = (len, seed) => { const r = require('../js/util.js').mulberry32(seed), a = new Uint8ClampedArray(len); for (let i = 0; i < len; i++) a[i] = r() * 256; return a; };
const b64 = bytes => zlib.deflateSync(Buffer.from(bytes.buffer, bytes.byteOffset, bytes.length)).toString('base64');   // те саме, що CompressionStream('deflate') + base64
const unb64 = s => new Uint8ClampedArray(zlib.inflateSync(Buffer.from(s, 'base64')));
const sameMaps = (a, b, msg) => { for (const k of ['color', 'height', 'spec', 'team']) assert.deepStrictEqual(a[k], b[k], (msg || '') + ' ' + k); };
// М'який круглий відбиток.
const dab = r => { const w = 2 * r + 1, data = new Float32Array(w * w); for (let y = 0; y < w; y++) for (let x = 0; x < w; x++) { const d = Math.hypot(x - r, y - r) / (r + 0.5); data[y * w + x] = d < 1 ? 1 - d * d : 0; } return { w, h: w, data }; };
// Детермінована заглушка maskgen (справжній maskgen.js тут не потрібен).
L.setMaskgen({ generate(gen, maps, size) { const out = new Float32Array(size * size), v = gen.params && gen.params.value !== undefined ? gen.params.value : 1; for (let i = 0; i < out.length; i++) out[i] = (i % size) < size / 2 ? v : maps.spec[i] / 255; return out; } });

// Документ, у якому заповнено все, що зберігається: генератори (зокрема вимкнений і зображення), заливка з маскою
// (малюнок + генератор + інверсія), шар малювання на всіх каналах, маска без малюнку, NRM, змінені налаштування.
function fullDoc(size) {
  const d = L.createDocument({ name: 'AUTestTank', size });
  d.layers.push(L.newLayer('proc', { type: 'base', seed: 11 }));
  const imageId = L.addImage(d, { width: size, height: size, data: noise(size * size * 4, 5) });
  d.layers.push(L.newLayer('proc', { type: 'image', params: { imageId, target: 'spec', channel: 'g' }, name: 'Моє зображення' }));
  d.layers.push(L.newLayer('proc', { type: 'rust', seed: 33, visible: false }));
  const f = L.newLayer('fill', { material: { color: [10, 200, 30], height: 0.7 }, channels: { height: true }, opacity: 0.8 });
  L.addMask(f, size, 'black'); L.applyDab(d, f, 'mask', dab(size / 4), size / 2, size / 2, { flow: 0.8 });
  f.mask.gen = { type: 'edges', seed: 4, params: { value: 0.7 } }; f.mask.invert = true; d.layers.push(f);
  const pl = L.newLayer('paint', { channels: { alpha: true } }); d.layers.push(pl);
  L.applyDab(d, pl, 'paint', dab(size / 4), size / 2, size / 2, { material: { color: [200, 40, 10], alpha: 70, height: 0.95, spec: 180, team: 255 }, flow: 0.8 });
  L.applyDab(d, pl, 'paint', dab(size / 8), 2, 3, { material: { color: [0, 0, 250], alpha: 0, height: 0.1, spec: 20, team: 0 }, channels: { color: true, height: true } });
  const w = L.newLayer('fill', { heightMode: 'replace', material: { spec: 250 }, channels: { color: false }, opacity: 0.5 }); w.mask = { enabled: false, invert: false, paint: null, gen: { type: 'noise', seed: 1, params: {} } }; d.layers.push(w);
  d.nrmImport = noise(size * size * 4, 6);
  d.settings.nrmStrength = 3.5; d.settings.invertY = true;
  return { d, imageId, f, pl };
}
const cleanDoc = size => L.createDocument({ name: 'Clean', size });
const tweak = async (doc, fn) => { const j = JSON.parse(await p.serialize(doc)); fn(j); return JSON.stringify(j); };
const layerByKind = (j, kind) => j.layers.find(l => l.kind === kind);

// Файл версії 1, записаний так, як його писав старий project.js (документ із maps.js: шари-генератори + оверлеї).
function v1File(doc) {
  const enc = b => b64(b);
  const images = {}; for (const id of Object.keys(doc.images)) images[id] = enc(doc.images[id]);
  const overlays = {}; for (const t of m.TARGETS) overlays[t] = { value: enc(doc.overlays[t].value), cov: enc(doc.overlays[t].cov) };
  return JSON.stringify({ format: 'ra3texture', version: 1, name: doc.name, size: doc.size, settings: doc.settings, layers: doc.layers, images, nrmImport: doc.nrmImport ? enc(doc.nrmImport) : null, overlays });
}
function v1Doc(size) {
  const d = m.createDocument({ name: 'AUOldTank', size });
  d.layers.push(g.defaultLayer('base', 11)); d.layers.push(g.defaultLayer('panels', 22));
  const IL = m.addImageLayer(d, { width: size, height: size, data: noise(size * size * 4, 5) }, { target: 'spec', channel: 'g', name: 'Моє зображення' });
  const off = g.defaultLayer('rust', 33); off.enabled = false; d.layers.push(off);
  d.nrmImport = noise(size * size * 4, 6);
  const o = { radius: size / 4, hardness: 0.5, strength: 0.8 };
  m.applyBrush(d, 'color', size / 2, size / 2, { ...o, value: [200, 40, 10] });
  m.applyBrush(d, 'alpha', 2, 3, { ...o, value: 70 }); m.applyBrush(d, 'height', 5, 9, { ...o, value: 250 });
  m.applyBrush(d, 'spec', 12, 4, { ...o, value: 180 }); m.applyBrush(d, 'team', 4, 12, { ...o, value: 255 });
  d.settings.nrmStrength = 3.5; d.settings.invertY = true;
  return { d, imageId: IL.params.imageId };
}

// ---- Версія 2: збереження й відкриття ----
test('v2 roundtrip gives identical maps', async () => {
  const { d } = fullDoc(16);
  const b = await p.deserialize(await p.serialize(d));
  L.composite(d); L.composite(b);
  assert.ok(d.maps.team.some(v => v > 200) && d.maps.height.some(v => v > 0.6) && new Set(d.maps.color).size > 20 && d.maps.color.some((v, i) => i % 4 === 3 && v < 255));
  sameMaps(b.maps, d.maps, 'maps');
  assert.deepStrictEqual(m.buildNRM(b), m.buildNRM(d)); });
test('rejects foreign json', async () => {
  await assert.rejects(p.deserialize('{"format":"x"}'), /Непідтримуваний формат проєкту/); });
test('serialize returns JSON text in the documented v2 format, buffers are base64(deflate(bytes))', async () => {
  const { d, imageId, f, pl } = fullDoc(16);
  const j = JSON.parse(await p.serialize(d));
  assert.strictEqual(j.format, 'ra3texture'); assert.strictEqual(j.version, 2);
  assert.strictEqual(j.name, 'AUTestTank'); assert.strictEqual(j.size, 16);
  assert.deepStrictEqual(j.settings, { nrmStrength: 3.5, invertY: true });
  assert.ok(!('maps' in j) && !('overlays' in j), 'maps не зберігаються');
  assert.deepStrictEqual(Object.keys(j.images), [imageId]); sameBytes(unb64(j.images[imageId]), d.images[imageId], 'image');
  sameBytes(unb64(j.nrmImport), d.nrmImport, 'nrmImport');
  assert.strictEqual(j.layers.length, d.layers.length);
  j.layers.forEach((l, i) => { assert.strictEqual(l.id, d.layers[i].id); assert.strictEqual(l.kind, d.layers[i].kind); });
  const jf = j.layers[3], jp = j.layers[4];
  assert.deepStrictEqual(jf.material, f.material); assert.deepStrictEqual(jf.channels, f.channels); assert.strictEqual(jf.opacity, 0.8);
  assert.deepStrictEqual({ ...jf.mask, paint: null }, { enabled: true, invert: true, paint: null, gen: { type: 'edges', seed: 4, params: { value: 0.7 } } });
  sameBytes(unb64(jf.mask.paint), f.mask.paint, 'mask.paint'); assert.ok(!('paint' in jf), 'заливка без буферів малювання');
  assert.deepStrictEqual(Object.keys(jp.paint).sort(), ['alpha', 'color', 'height', 'spec', 'team']); assert.strictEqual(jp.mask, null);
  for (const c of Object.keys(jp.paint)) { sameBytes(unb64(jp.paint[c].value), pl.paint[c].value, c + '.value'); sameBytes(unb64(jp.paint[c].cov), pl.paint[c].cov, c + '.cov'); }
  assert.deepStrictEqual(j.layers[0].proc, d.layers[0].proc); assert.strictEqual(j.layers[2].visible, false);
  assert.deepStrictEqual(j.layers[5].mask, { enabled: false, invert: false, paint: null, gen: { type: 'noise', seed: 1, params: {} } }); });
test('a clean document: nrmImport null, no images, no layers, roundtrip works and maps are base', async () => {
  const a = cleanDoc(8);
  const s = await p.serialize(a), j = JSON.parse(s);
  assert.strictEqual(j.nrmImport, null); assert.deepStrictEqual(j.images, {}); assert.deepStrictEqual(j.layers, []);
  const b = await p.deserialize(s);
  assert.strictEqual(b.version, 2); assert.strictEqual(b.name, 'Clean'); assert.strictEqual(b.size, 8); assert.strictEqual(b.nrmImport, null); assert.deepStrictEqual(b.images, {});
  assert.deepStrictEqual(b.settings, { nrmStrength: 2, invertY: false }); assert.deepStrictEqual(b.layers, []);
  L.composite(a); sameMaps(b.maps, a.maps); });
test('every field of a filled document survives byte for byte', async () => {
  const { d, imageId } = fullDoc(16);
  const b = await p.deserialize(await p.serialize(d));
  assert.strictEqual(b.name, d.name); assert.strictEqual(b.size, d.size); assert.deepStrictEqual(b.settings, d.settings);
  assert.deepStrictEqual(b.layers, d.layers);                                        // id, параметри, буфери малювання й маски
  assert.ok(b.layers[4].paint.color.value instanceof Uint8ClampedArray && b.layers[3].mask.paint instanceof Uint8ClampedArray);
  assert.deepStrictEqual(Object.keys(b.images), [imageId]); sameBytes(b.images[imageId], d.images[imageId], 'image'); sameBytes(b.nrmImport, d.nrmImport, 'nrmImport');
  assert.deepStrictEqual(Object.keys(b).sort(), Object.keys(d).sort());              // maps створено заново, лишається composite
  assert.strictEqual(b.maps.height.length, 256); assert.strictEqual(b.maps.color[3], 255); });
test('buffers of every length (base64 padding 0, 1, 2) and larger than one base64 chunk survive', async () => {
  for (let seed = 1; seed <= 9; seed++) {
    const a = cleanDoc(4); a.nrmImport = noise(64, seed); const b = await p.deserialize(await p.serialize(a));
    sameBytes(b.nrmImport, a.nrmImport, 'seed ' + seed); }
  const big = cleanDoc(256); big.nrmImport = noise(256 * 256 * 4, 77);                // ~256 КБ майже нестисливих даних
  const s = await p.serialize(big); assert.ok(s.length > 300000);
  sameBytes((await p.deserialize(s)).nrmImport, big.nrmImport, 'big'); });

// ---- Незмінність і копіювання ----
test('serialize does not mutate the document', async () => {
  const { d, pl, f } = fullDoc(16); L.composite(d);
  const snap = { layers: JSON.stringify(d.layers), settings: JSON.stringify(d.settings), name: d.name, size: d.size, keys: Object.keys(d).join(), layerKeys: d.layers.map(l => Object.keys(l).join()),
    nrm: d.nrmImport.slice(), nrmRef: d.nrmImport, mapsColor: d.maps.color.slice(), mapsRef: d.maps, maskRef: f.mask.paint, mask: f.mask.paint.slice(), maskKeys: Object.keys(f.mask).join(),
    paint: Object.keys(pl.paint).map(c => [pl.paint[c].value, pl.paint[c].value.slice(), pl.paint[c].cov, pl.paint[c].cov.slice()]) };
  await p.serialize(d);
  assert.strictEqual(JSON.stringify(d.layers), snap.layers); assert.strictEqual(JSON.stringify(d.settings), snap.settings);
  assert.strictEqual(d.name, snap.name); assert.strictEqual(d.size, snap.size); assert.strictEqual(Object.keys(d).join(), snap.keys);
  assert.deepStrictEqual(d.layers.map(l => Object.keys(l).join()), snap.layerKeys); assert.strictEqual(Object.keys(f.mask).join(), snap.maskKeys);
  assert.strictEqual(d.nrmImport, snap.nrmRef); sameBytes(d.nrmImport, snap.nrm, 'nrmImport');                // і не віддано під «transfer»
  assert.strictEqual(f.mask.paint, snap.maskRef); sameBytes(f.mask.paint, snap.mask, 'mask.paint');
  assert.strictEqual(d.maps, snap.mapsRef); sameBytes(d.maps.color, snap.mapsColor, 'maps.color');
  for (const [v, vc, c, cc] of snap.paint) { sameBytes(v, vc, 'paint.value'); sameBytes(c, cc, 'paint.cov'); } });
test('the file is a snapshot of the document at the moment serialize is called (edits and resize during compression do not leak in)', async () => {
  const { d, imageId, pl, f } = fullDoc(16);
  const want = { name: d.name, size: d.size, settings: JSON.stringify(d.settings), layers: d.layers.map(l => L.cloneLayer(l)), image: d.images[imageId].slice(), nrm: d.nrmImport.slice() };
  const pending = p.serialize(d);                                                      // як збереження: стиснення ще триває…
  L.applyDab(d, pl, 'paint', dab(5), 8, 8, { material: { color: [1, 2, 3], alpha: 4, height: 0.2, spec: 5, team: 6 } });   // …а користувач малює (буфери змінюються на місці),
  L.applyDab(d, f, 'mask', dab(5), 3, 3, { erase: true });
  d.images[imageId].fill(9); d.nrmImport.fill(1);
  d.name = 'Changed'; d.settings.nrmStrength = 7; d.layers[0].proc.params.mutated = true; d.layers[3].opacity = 0.1; d.layers.push(L.newLayer('fill'));
  L.resize(d, 32);                                                                     // …і змінює розмір документа
  const b = await pending.then(s => p.deserialize(s));
  assert.strictEqual(b.name, want.name); assert.strictEqual(b.size, want.size); assert.strictEqual(JSON.stringify(b.settings), want.settings);
  assert.strictEqual(b.layers.length, want.layers.length);
  b.layers.forEach((l, i) => { const w = want.layers[i]; assert.deepStrictEqual({ ...l, id: 0, name: 0 }, { ...w, id: 0, name: 0 }, 'шар ' + i); });
  sameBytes(b.images[imageId], want.image, 'image'); sameBytes(b.nrmImport, want.nrm, 'nrmImport'); });
test('layers and settings are deep copies on save and on load', async () => {
  const { d } = fullDoc(8);
  const s = await p.serialize(d), want = JSON.stringify(d.layers), wantSettings = JSON.stringify(d.settings);
  d.layers[0].proc.params.mutated = true; d.layers[1].name = 'змінено'; d.layers.pop(); d.settings.nrmStrength = 9;     // зміни вихідного документа після збереження
  const a = await p.deserialize(s);
  assert.strictEqual(JSON.stringify(a.layers), want); assert.strictEqual(JSON.stringify(a.settings), wantSettings);
  const b = await p.deserialize(s);
  a.layers[0].proc.params.mutated = 1; a.layers[4].paint.color.cov[0] = 99; a.layers[3].mask.paint[0] = 1; a.layers.pop(); a.settings.invertY = !a.settings.invertY;   // зміни відкритого документа
  assert.strictEqual(JSON.stringify(b.layers), want); assert.strictEqual(JSON.stringify(b.settings), wantSettings);
  assert.notStrictEqual(a.layers, b.layers); assert.notStrictEqual(a.layers[0], b.layers[0]); assert.notStrictEqual(a.layers[0].proc.params, b.layers[0].proc.params);
  assert.notStrictEqual(a.settings, b.settings); assert.notStrictEqual(a.images, b.images); assert.notStrictEqual(a.layers[4].paint.team.cov, b.layers[4].paint.team.cov); });
test('unknown extra keys at the top level are ignored; extra keys of settings, layers, masks and materials survive', async () => {
  const { d } = fullDoc(8);
  const s = await tweak(d, j => { j.future = { a: 1 }; j.overlays = 'v1 залишок'; });
  const b = await p.deserialize(s);
  assert.strictEqual(b.size, 8); assert.ok(!('future' in b) && !('overlays' in b));
  d.settings.previewTeam = 'Red'; d.layers[0].locked = true; d.layers[1].proc.params.nested = { a: [1, 2, { b: null }] }; d.layers[3].mask.note = 'x'; d.layers[3].material.id = 'allied-light';
  const c = await p.deserialize(await p.serialize(d));
  assert.deepStrictEqual(c.settings, d.settings); assert.deepStrictEqual(c.layers, d.layers); });

// ---- Непідтримуваний формат і версія ----
test('wrong version or format value is unsupported', async () => {
  const { d } = fullDoc(8);
  for (const v of [3, 0, '2', '1', null, 2.5]) await assert.rejects(p.deserialize(await tweak(d, j => { j.version = v; })), /Непідтримуваний формат проєкту/, String(v));
  await assert.rejects(p.deserialize(await tweak(d, j => { delete j.version; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.format = 'ra3Texture'; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize('{"format":"ra3texture"}'), /Непідтримуваний формат проєкту/);
  for (const x of ['null', '42', '"ra3texture"', '[]', 'true']) await assert.rejects(p.deserialize(x), /Непідтримуваний формат проєкту/, x); });

// ---- Пошкоджені файли ----
const CORRUPT = /Пошкоджений файл проєкту/;
test('invalid JSON is a corrupt project', async () => {
  await assert.rejects(p.deserialize('{"format":"ra3texture","version":2,'), CORRUPT);
  await assert.rejects(p.deserialize('це не JSON'), CORRUPT);
  await assert.rejects(p.deserialize(''), CORRUPT);
  const s = await p.serialize(cleanDoc(8));
  await assert.rejects(p.deserialize(s.slice(0, s.length >> 1)), CORRUPT);
  for (const x of [undefined, null, 5, {}, Buffer.from(s)]) await assert.rejects(p.deserialize(x), CORRUPT); });
test('truncated base64 is a corrupt project (image, nrmImport, paint, mask)', async () => {
  const { d, imageId } = fullDoc(8);
  const cut = (v, k) => v.slice(0, v.length - k);
  for (const k of [1, 2, 3, 5]) {      // довжина base64 більше не кратна 4
    await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = cut(j.images[imageId], k); })), CORRUPT, 'image -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = cut(j.nrmImport, k); })), CORRUPT, 'nrm -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { const P = layerByKind(j, 'paint').paint; P.spec.cov = cut(P.spec.cov, k); })), CORRUPT, 'cov -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { const M = j.layers[3].mask; M.paint = cut(M.paint, k); })), CORRUPT, 'mask -' + k); }
  // Обрізано рівно по межі групи з 4 символів: base64 коректний, але потік deflate обірваний.
  for (const k of [4, 8, 40]) {
    await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = cut(j.images[imageId], k); })), CORRUPT, 'image deflate -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { const P = layerByKind(j, 'paint').paint; P.color.value = cut(P.color.value, k); })), CORRUPT, 'color.value -' + k); } });
test('garbage in base64 or deflate is a corrupt project', async () => {
  const { d, imageId } = fullDoc(8);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = '!!!!' + j.images[imageId].slice(4); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = j.images[imageId].slice(0, 8) + ' ' + j.images[imageId].slice(9); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = ''; })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = 'AAAA=AAA'; })), CORRUPT);                    // «=» не в кінці
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = 'A==='; })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { layerByKind(j, 'paint').paint.team.value = Buffer.from('це не deflate, а звичайний текст').toString('base64'); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.layers[3].mask.paint = Buffer.alloc(300, 0xAB).toString('base64'); })), CORRUPT);
  const good = Buffer.from(d.nrmImport.buffer, d.nrmImport.byteOffset, d.nrmImport.length);
  const flipped = zlib.deflateSync(good); flipped[flipped.length - 2] ^= 0xFF;                                         // зіпсована контрольна сума
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = flipped.toString('base64'); })), CORRUPT); });
test('a buffer of the wrong length is a corrupt project and never yields a document', async () => {
  const { d, imageId } = fullDoc(8), n = 64;
  const wrong = len => b64(new Uint8ClampedArray(len).fill(7));
  const P = j => layerByKind(j, 'paint').paint;
  const cases = [
    ['image shorter', j => { j.images[imageId] = wrong(n * 4 - 1); }], ['image longer', j => { j.images[imageId] = wrong(n * 4 + 1); }],
    ['image n instead of 4n', j => { j.images[imageId] = wrong(n); }], ['image empty', j => { j.images[imageId] = wrong(0); }],
    ['image 4x too long', j => { j.images[imageId] = wrong(n * 16); }],
    ['nrmImport shorter', j => { j.nrmImport = wrong(n * 4 - 4); }], ['nrmImport longer', j => { j.nrmImport = wrong(n * 4 + 4); }],
    ['color.value n (not 4n)', j => { P(j).color.value = wrong(n); }], ['color.value 4n+1', j => { P(j).color.value = wrong(n * 4 + 1); }],
    ['color.cov 4n', j => { P(j).color.cov = wrong(n * 4); }],
    ['spec.value 4n', j => { P(j).spec.value = wrong(n * 4); }], ['spec.value n-1', j => { P(j).spec.value = wrong(n - 1); }],
    ['team.cov n+1', j => { P(j).team.cov = wrong(n + 1); }], ['alpha.cov empty', j => { P(j).alpha.cov = wrong(0); }],
    ['height.value 16x too long', j => { P(j).height.value = wrong(n * 16); }],
    ['mask paint 4n', j => { j.layers[3].mask.paint = wrong(n * 4); }], ['mask paint n-1', j => { j.layers[3].mask.paint = wrong(n - 1); }],
    ['size does not match the buffers', j => { j.size = 16; }], ['size smaller than the buffers', j => { j.size = 4; }]];
  for (const [name, fn] of cases) await assert.rejects(p.deserialize(await tweak(d, fn)), CORRUPT, name);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = wrong(n * 16); })), /більше/);          // надлишок відсікається одразу
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = wrong(n * 4 - 4); })), /очікується 256/); });
test('a tiny file cannot claim a huge document', async () => {
  const { d } = fullDoc(8);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.size = 1 << 20; })), CORRUPT);
  const c = cleanDoc(8);                                                               // без жодного буфера розмір обмежено окремо
  await assert.rejects(p.deserialize(await tweak(c, j => { j.size = 1 << 16; })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(c, j => { j.size = 8192; })), CORRUPT);
  assert.strictEqual((await p.deserialize(await tweak(c, j => { j.size = 64; }))).size, 64); });
test('bad size is a corrupt project', async () => {
  const { d } = fullDoc(8);
  for (const sz of [500, 0, -8, 3, 8.5, '8', null, 'x', [8], {}]) await assert.rejects(p.deserialize(await tweak(d, j => { j.size = sz; })), /Пошкоджений файл проєкту.*розмір/, String(sz));
  await assert.rejects(p.deserialize(await tweak(d, j => { delete j.size; })), CORRUPT); });
test('missing or malformed structure is a corrupt project', async () => {
  const { d, imageId } = fullDoc(8);
  const F = j => j.layers[3], PL = j => j.layers[4], PR = j => j.layers[0];
  const cases = [
    ['no images', j => { delete j.images; }], ['images array', j => { j.images = []; }], ['images null', j => { j.images = null; }], ['image value number', j => { j.images[imageId] = 5; }], ['image value null', j => { j.images[imageId] = null; }],
    ['no nrmImport', j => { delete j.nrmImport; }], ['nrmImport number', j => { j.nrmImport = 1; }],
    ['no layers', j => { delete j.layers; }], ['layers object', j => { j.layers = {}; }], ['layer null', j => { j.layers[0] = null; }], ['layer string', j => { j.layers[1] = 'base'; }], ['layer array', j => { j.layers[1] = []; }],
    ['no id', j => { delete PR(j).id; }], ['empty id', j => { PR(j).id = ''; }], ['id number', j => { PR(j).id = 5; }], ['duplicate id', j => { j.layers[1].id = j.layers[0].id; }],
    ['name number', j => { PR(j).name = 1; }], ['no kind', j => { delete PR(j).kind; }], ['kind unknown', j => { PR(j).kind = 'image'; }],
    ['visible string', j => { PR(j).visible = 'yes'; }], ['opacity missing', j => { delete F(j).opacity; }], ['opacity > 1', j => { F(j).opacity = 2; }], ['opacity < 0', j => { F(j).opacity = -0.1; }], ['opacity string', j => { F(j).opacity = '1'; }],
    ['channels missing', j => { delete F(j).channels; }], ['channel missing', j => { delete F(j).channels.team; }], ['channel number', j => { F(j).channels.color = 1; }],
    ['heightMode bad', j => { F(j).heightMode = 'mul'; }],
    ['fill without material', j => { delete F(j).material; }], ['material colour of 2', j => { F(j).material.color = [1, 2]; }], ['material colour string', j => { F(j).material.color = '#ffffff'; }],
    ['material spec string', j => { F(j).material.spec = '40'; }], ['material height null', j => { F(j).material.height = null; }],
    ['proc missing', j => { delete PR(j).proc; }], ['proc type unknown', j => { PR(j).proc.type = 'lava'; }], ['proc seed string', j => { PR(j).proc.seed = '1'; }], ['proc params null', j => { PR(j).proc.params = null; }],
    ['paint layer without paint', j => { delete PL(j).paint; }], ['paint is array', j => { PL(j).paint = []; }], ['paint unknown channel', j => { PL(j).paint.gloss = PL(j).paint.spec; }],
    ['paint entry null', j => { PL(j).paint.spec = null; }], ['paint cov missing', j => { delete PL(j).paint.team.cov; }], ['paint value number', j => { PL(j).paint.alpha.value = 3; }],
    ['mask missing', j => { delete PR(j).mask; }], ['mask string', j => { F(j).mask = 'black'; }], ['mask enabled missing', j => { delete F(j).mask.enabled; }], ['mask invert number', j => { F(j).mask.invert = 1; }],
    ['mask paint missing', j => { delete F(j).mask.paint; }], ['mask paint number', j => { F(j).mask.paint = 7; }], ['mask gen string', j => { F(j).mask.gen = 'edges'; }], ['mask gen without type', j => { delete F(j).mask.gen.type; }],
    ['no settings', j => { delete j.settings; }], ['settings null', j => { j.settings = null; }], ['nrmStrength string', j => { j.settings.nrmStrength = '2'; }],
    ['nrmStrength null', j => { j.settings.nrmStrength = null; }], ['invertY number', j => { j.settings.invertY = 1; }],
    ['no name', j => { delete j.name; }], ['name number', j => { j.name = 5; }]];
  for (const [name, fn] of cases) await assert.rejects(p.deserialize(await tweak(d, fn)), CORRUPT, name); });
test('an empty-name project is fine', async () => {
  const a = cleanDoc(4); a.name = ''; assert.strictEqual((await p.deserialize(await p.serialize(a))).name, ''); });

// ---- serialize відмовляється від непридатного документа ----
test('serialize refuses a document that would not open again', async () => {
  const bad = async (fn, re) => { const { d } = fullDoc(8); fn(d); await assert.rejects(p.serialize(d), re); };
  await bad(d => { d.nrmImport = new Uint8ClampedArray(12); }, /NRM/);
  await bad(d => { d.images.x = new Uint8ClampedArray(10); }, /«x»/);
  await bad(d => { d.layers[4].paint.team.cov = new Uint8ClampedArray(3); }, /team/);
  await bad(d => { d.layers[4].paint.color.value = new Uint8ClampedArray(64); }, /color/);
  await bad(d => { d.layers[4].paint.gloss = d.layers[4].paint.spec; }, /gloss/);
  await bad(d => { d.layers[3].mask.paint = new Float32Array(64); }, /маск/);
  await bad(d => { d.layers[0].kind = 'image'; }, /вид шару/);
  await bad(d => { d.layers[0].proc.type = 'lava'; }, /генератор/);
  await bad(d => { d.layers[1].id = d.layers[0].id; }, /id/);
  await bad(d => { d.layers[3].opacity = NaN; }, /непрозорість/);
  await bad(d => { delete d.layers[4].paint; }, /малювання/);
  await bad(d => { d.settings.nrmStrength = '2'; }, /налаштування/);
  await bad(d => { d.size = 12; }, /степінь двійки/);
  for (const re of [/Некоректний документ/]) await bad(d => { d.layers[2] = null; }, re); });

// ---- Версія 1: відкриття з конвертацією ----
test('a v1 file converts: generators → proc layers in order, strokes → one paint layer on top; maps match the v1 renderer', async () => {
  const { d: old, imageId } = v1Doc(16);
  const b = await p.deserialize(v1File(old));
  assert.strictEqual(b.version, 2); assert.strictEqual(b.name, 'AUOldTank'); assert.strictEqual(b.size, 16);
  assert.deepStrictEqual(b.settings, { nrmStrength: 3.5, invertY: true });
  sameBytes(b.images[imageId], old.images[imageId], 'image'); sameBytes(b.nrmImport, old.nrmImport, 'nrmImport');
  assert.strictEqual(b.layers.length, old.layers.length + 1);
  old.layers.forEach((l, i) => {
    const c = b.layers[i];
    assert.strictEqual(c.kind, 'proc'); assert.strictEqual(c.proc.type, l.type); assert.strictEqual(c.proc.seed, l.seed);
    for (const k of Object.keys(l.params)) assert.deepStrictEqual(c.proc.params[k], l.params[k], l.type + '.' + k);
    assert.strictEqual(c.visible, l.enabled); assert.strictEqual(c.name, l.name);
    assert.deepStrictEqual(c.channels, { color: true, alpha: true, height: true, spec: true, team: true }); assert.strictEqual(c.mask, null); assert.strictEqual(c.opacity, 1); });
  const top = b.layers[b.layers.length - 1];
  assert.strictEqual(top.kind, 'paint'); assert.strictEqual(top.name, 'Малювання (з v1)'); assert.strictEqual(top.heightMode, 'replace');
  assert.deepStrictEqual(Object.keys(top.paint).sort(), ['alpha', 'color', 'height', 'spec', 'team']); assert.strictEqual(top.channels.alpha, true);
  for (const t of m.TARGETS) { sameBytes(top.paint[t].value, old.overlays[t].value, t + '.value'); sameBytes(top.paint[t].cov, old.overlays[t].cov, t + '.cov'); }
  assert.strictEqual(new Set(b.layers.map(l => l.id)).size, b.layers.length, 'унікальні id');
  m.rebuild(old, g); L.composite(b);
  assert.ok(old.maps.team.some(v => v > 200) && old.maps.color.some((v, i) => i % 4 === 3 && v < 255));
  sameMaps(b.maps, old.maps, 'v1 → v2');
  // і зберігається вже як v2
  const j = JSON.parse(await p.serialize(b)); assert.strictEqual(j.version, 2); assert.ok(!('overlays' in j));
  const c = await p.deserialize(JSON.stringify(j)); L.composite(c); sameMaps(c.maps, old.maps, 'v1 → v2 → v2'); });
test('a v1 file without strokes gets no paint layer; only covered channels become paint buffers', async () => {
  const a = m.createDocument({ name: 'Old', size: 8 }); a.layers.push(g.defaultLayer('base', 1));
  const b = await p.deserialize(v1File(a));
  assert.deepStrictEqual(b.layers.map(l => l.kind), ['proc']); assert.strictEqual(b.layers[0].proc.seed, 1);
  m.applyBrush(a, 'team', 4, 4, { radius: 2, hardness: 1, strength: 1, value: 255 });
  const c = await p.deserialize(v1File(a));
  assert.deepStrictEqual(c.layers.map(l => l.kind), ['proc', 'paint']); assert.deepStrictEqual(Object.keys(c.layers[1].paint), ['team']); assert.strictEqual(c.layers[1].channels.alpha, false);
  m.rebuild(a, g); L.composite(c); sameMaps(c.maps, a.maps); });
test('a corrupt v1 file is rejected with a corrupt-project error', async () => {
  const { d } = v1Doc(8);
  const t1 = fn => { const j = JSON.parse(v1File(d)); fn(j); return JSON.stringify(j); };
  const cases = [
    ['no overlays', j => { delete j.overlays; }], ['overlay target missing', j => { delete j.overlays.team; }], ['overlay cov number', j => { j.overlays.alpha.cov = 3; }],
    ['overlay wrong length', j => { j.overlays.spec.value = b64(new Uint8ClampedArray(65)); }], ['color overlay n', j => { j.overlays.color.value = b64(new Uint8ClampedArray(64)); }],
    ['unknown layer type', j => { j.layers[0].type = 'lava'; }], ['layer null', j => { j.layers[1] = null; }], ['layers object', j => { j.layers = {}; }],
    ['params string', j => { j.layers[0].params = 'x'; }], ['bad size', j => { j.size = 12; }], ['truncated overlay', j => { j.overlays.team.cov = j.overlays.team.cov.slice(0, -4); }]];
  for (const [name, fn] of cases) await assert.rejects(p.deserialize(t1(fn)), CORRUPT, name); });

// ---- Експорт ----
test('exports serialize, deserialize and the format constants', () => {
  assert.strictEqual(typeof p.serialize, 'function'); assert.strictEqual(typeof p.deserialize, 'function');
  assert.strictEqual(p.FORMAT, 'ra3texture'); assert.strictEqual(p.VERSION, 2); });
