const test = require('node:test'); const assert = require('node:assert');
const zlib = require('node:zlib');
const m = require('../js/maps.js'); const g = require('../js/generators.js'); const p = require('../js/project.js');

// ---- Допоміжне ----
const sameBytes = (a, b, msg) => { assert.ok(a instanceof Uint8ClampedArray, msg + ': тип'); assert.strictEqual(a.length, b.length, msg + ': довжина'); assert.ok(Buffer.from(a.buffer, a.byteOffset, a.length).equals(Buffer.from(b.buffer, b.byteOffset, b.length)), msg + ': вміст'); };
const noise = (len, seed) => { const r = require('../js/util.js').mulberry32(seed), a = new Uint8ClampedArray(len); for (let i = 0; i < len; i++) a[i] = r() * 256; return a; };
const b64 = bytes => zlib.deflateSync(Buffer.from(bytes.buffer, bytes.byteOffset, bytes.length)).toString('base64');   // те саме, що CompressionStream('deflate') + base64
const unb64 = s => new Uint8ClampedArray(zlib.inflateSync(Buffer.from(s, 'base64')));
const pix = size => new Uint8ClampedArray(size * size * 4);

// Документ, у якому заповнено все, що зберігається: шари, зображення, NRM, мазки на всіх цілях, змінені налаштування.
function fullDoc(size) {
  const d = m.createDocument({ name: 'AUTestTank', size });
  d.layers.push(g.defaultLayer('base', 11)); d.layers.push(g.defaultLayer('panels', 22));
  const L = m.addImageLayer(d, { width: size, height: size, data: noise(size * size * 4, 5) }, { target: 'spec', channel: 'g', name: 'Моє зображення' });
  const off = g.defaultLayer('rust', 33); off.enabled = false; d.layers.push(off);
  d.nrmImport = noise(size * size * 4, 6);
  const o = { radius: size / 4, hardness: 0.5, strength: 0.8 };
  m.applyBrush(d, 'color', size / 2, size / 2, { ...o, value: [200, 40, 10] });
  m.applyBrush(d, 'alpha', 2, 3, { ...o, value: 70 }); m.applyBrush(d, 'height', 5, 9, { ...o, value: 250 });
  m.applyBrush(d, 'spec', 12, 4, { ...o, value: 180 }); m.applyBrush(d, 'team', 4, 12, { ...o, value: 255 });
  d.settings.nrmStrength = 3.5; d.settings.invertY = true;
  return { d, imageId: L.params.imageId };
}
const cleanDoc = size => m.createDocument({ name: 'Clean', size });
const tweak = async (doc, fn) => { const j = JSON.parse(await p.serialize(doc)); fn(j); return JSON.stringify(j); };

// ---- Тести з брифу ----
test('roundtrip', async () => {
  const a = m.createDocument({ size: 16 });
  a.layers.push(g.defaultLayer('base', 1)); a.layers.push(g.defaultLayer('panels', 2));
  m.addImageLayer(a, { width: 16, height: 16, data: noise(16 * 16 * 4, 3) }, { target: 'color', channel: 'lum' });
  m.applyBrush(a, 'team', 8, 8, { radius: 4, hardness: 0.6, strength: 0.9, value: 255 });
  m.applyBrush(a, 'spec', 3, 3, { radius: 3, hardness: 1, strength: 1, value: 190 });
  a.nrmImport = noise(16 * 16 * 4, 4);
  const b = await p.deserialize(await p.serialize(a));
  m.rebuild(a, g); m.rebuild(b, g);
  assert.ok(a.maps.team.some(v => v > 200) && a.maps.height.some(v => v < 0.4) && new Set(a.maps.color).size > 20);                                   // порівнюємо не порожні карти
  for (const k of ['color', 'team', 'spec', 'height']) assert.deepStrictEqual(b.maps[k], a.maps[k], k);
  assert.deepStrictEqual(m.buildNRM(b), m.buildNRM(a)); });
test('rejects foreign json', async () => {
  await assert.rejects(p.deserialize('{"format":"x"}'), /Непідтримуваний формат проєкту/); });

// ---- Формат ----
test('serialize returns JSON text in the documented format, buffers are base64(deflate(bytes))', async () => {
  const { d, imageId } = fullDoc(16);
  const s = await p.serialize(d); assert.strictEqual(typeof s, 'string');
  const j = JSON.parse(s);
  assert.strictEqual(j.format, 'ra3texture'); assert.strictEqual(j.version, 1);
  assert.strictEqual(j.name, 'AUTestTank'); assert.strictEqual(j.size, 16);
  assert.deepStrictEqual(j.settings, { nrmStrength: 3.5, invertY: true }); assert.deepStrictEqual(j.layers, JSON.parse(JSON.stringify(d.layers)));
  assert.ok(!('maps' in j), 'maps не зберігаються');
  assert.deepStrictEqual(Object.keys(j.images), [imageId]); sameBytes(unb64(j.images[imageId]), d.images[imageId], 'image');
  sameBytes(unb64(j.nrmImport), d.nrmImport, 'nrmImport');
  assert.deepStrictEqual(Object.keys(j.overlays).sort(), [...m.TARGETS].sort());
  for (const t of m.TARGETS) { sameBytes(unb64(j.overlays[t].value), d.overlays[t].value, t + '.value'); sameBytes(unb64(j.overlays[t].cov), d.overlays[t].cov, t + '.cov'); } });
test('a document with nothing imported and nothing painted: nrmImport is null, overlays are empty, roundtrip works', async () => {
  const a = cleanDoc(8); a.layers.push(g.defaultLayer('base', 1));
  const s = await p.serialize(a), j = JSON.parse(s);
  assert.strictEqual(j.nrmImport, null); assert.deepStrictEqual(j.images, {});
  const b = await p.deserialize(s);
  assert.strictEqual(b.name, 'Clean'); assert.strictEqual(b.size, 8); assert.strictEqual(b.nrmImport, null); assert.deepStrictEqual(b.images, {});
  assert.deepStrictEqual(b.settings, { nrmStrength: 2, invertY: false }); assert.deepStrictEqual(b.layers, a.layers);
  for (const t of m.TARGETS) { sameBytes(b.overlays[t].value, a.overlays[t].value, t + '.value'); sameBytes(b.overlays[t].cov, a.overlays[t].cov, t + '.cov'); }
  assert.ok(b.overlays.team.cov.every(v => v === 0)); assert.strictEqual(b.overlays.color.value[3], 255);
  m.rebuild(a, g); m.rebuild(b, g); assert.deepStrictEqual(b.maps.color, a.maps.color); });
test('every field of a filled document survives byte for byte', async () => {
  const { d, imageId } = fullDoc(16);
  const b = await p.deserialize(await p.serialize(d));
  assert.strictEqual(b.name, d.name); assert.strictEqual(b.size, d.size); assert.deepStrictEqual(b.settings, d.settings); assert.deepStrictEqual(b.layers, d.layers);
  assert.deepStrictEqual(Object.keys(b.images), [imageId]); sameBytes(b.images[imageId], d.images[imageId], 'image'); sameBytes(b.nrmImport, d.nrmImport, 'nrmImport');
  for (const t of m.TARGETS) { sameBytes(b.overlays[t].value, d.overlays[t].value, t + '.value'); sameBytes(b.overlays[t].cov, d.overlays[t].cov, t + '.cov'); }
  assert.ok(d.overlays.team.cov.some(v => v > 0));                                    // мазки справді були
  assert.deepStrictEqual(Object.keys(b).sort(), Object.keys(d).sort());              // maps створено заново, лишається rebuild
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
  const { d } = fullDoc(16); m.rebuild(d, g);
  const snap = { layers: JSON.stringify(d.layers), settings: JSON.stringify(d.settings), name: d.name, size: d.size, keys: Object.keys(d).join(), imageKeys: Object.keys(d.images).join(),
    nrm: d.nrmImport.slice(), nrmRef: d.nrmImport, imgs: Object.values(d.images).map(x => [x, x.slice()]), mapsColor: d.maps.color.slice(), mapsRef: d.maps,
    ov: m.TARGETS.map(t => [d.overlays[t], d.overlays[t].value.slice(), d.overlays[t].cov.slice()]) };
  await p.serialize(d);
  assert.strictEqual(JSON.stringify(d.layers), snap.layers); assert.strictEqual(JSON.stringify(d.settings), snap.settings);
  assert.strictEqual(d.name, snap.name); assert.strictEqual(d.size, snap.size); assert.strictEqual(Object.keys(d).join(), snap.keys); assert.strictEqual(Object.keys(d.images).join(), snap.imageKeys);
  assert.strictEqual(d.nrmImport, snap.nrmRef); sameBytes(d.nrmImport, snap.nrm, 'nrmImport');                // і не віддано під «transfer»
  for (const [ref, copy] of snap.imgs) { assert.strictEqual(ref.length, 16 * 16 * 4); sameBytes(ref, copy, 'image'); }
  assert.strictEqual(d.maps, snap.mapsRef); sameBytes(d.maps.color, snap.mapsColor, 'maps.color');
  for (const [ov, v, c] of snap.ov) { sameBytes(ov.value, v, 'ov.value'); sameBytes(ov.cov, c, 'ov.cov'); } });
test('the file is a snapshot of the document at the moment serialize is called (edits and resize during compression do not leak in)', async () => {
  const { d, imageId } = fullDoc(16);
  const want = { name: d.name, size: d.size, settings: JSON.stringify(d.settings), layers: JSON.stringify(d.layers), image: d.images[imageId].slice(), nrm: d.nrmImport.slice(),
    ov: m.TARGETS.map(t => [t, d.overlays[t].value.slice(), d.overlays[t].cov.slice()]) };
  const pending = p.serialize(d);                                                      // як saveProject: стиснення ще триває…
  m.applyBrush(d, 'team', 8, 8, { radius: 6, hardness: 1, strength: 1, value: 3 });    // …а користувач малює (буфери змінюються на місці),
  m.applyBrush(d, 'color', 3, 3, { radius: 6, hardness: 1, strength: 1, value: [1, 2, 3] });
  d.images[imageId].fill(9); d.nrmImport.fill(1);
  d.name = 'Changed'; d.settings.nrmStrength = 7; d.layers[0].params.mutated = true; d.layers.push(g.defaultLayer('dirt', 4));
  m.resizeDocument(d, 32);                                                              // …і змінює розмір документа
  const b = await p.deserialize(await pending);
  assert.strictEqual(b.name, want.name); assert.strictEqual(b.size, want.size);
  assert.strictEqual(JSON.stringify(b.settings), want.settings); assert.strictEqual(JSON.stringify(b.layers), want.layers);
  sameBytes(b.images[imageId], want.image, 'image'); sameBytes(b.nrmImport, want.nrm, 'nrmImport');
  for (const [t, v, c] of want.ov) { sameBytes(b.overlays[t].value, v, t + '.value'); sameBytes(b.overlays[t].cov, c, t + '.cov'); } });
test('layers and settings are deep copies on save and on load', async () => {
  const { d } = fullDoc(8);
  const s = await p.serialize(d), want = JSON.stringify(d.layers), wantSettings = JSON.stringify(d.settings);
  d.layers[0].params.mutated = true; d.layers[1].name = 'змінено'; d.layers.pop(); d.settings.nrmStrength = 9;        // зміни вихідного документа після збереження
  const a = await p.deserialize(s);
  assert.strictEqual(JSON.stringify(a.layers), want); assert.strictEqual(JSON.stringify(a.settings), wantSettings);
  const b = await p.deserialize(s);
  a.layers[0].params.mutated = 1; a.layers.pop(); a.settings.invertY = !a.settings.invertY;                           // зміни відкритого документа
  assert.strictEqual(JSON.stringify(b.layers), want); assert.strictEqual(JSON.stringify(b.settings), wantSettings);
  assert.notStrictEqual(a.layers, b.layers); assert.notStrictEqual(a.layers[0], b.layers[0]); assert.notStrictEqual(a.layers[0].params, b.layers[0].params);
  assert.notStrictEqual(a.settings, b.settings); assert.notStrictEqual(a.images, b.images); assert.notStrictEqual(a.overlays.team.cov, b.overlays.team.cov); });
test('unknown extra keys in the JSON are ignored', async () => {
  const { d } = fullDoc(8);
  const s = await tweak(d, j => { j.future = { a: 1 }; j.overlays.team.extra = 'x'; j.overlays.extraTarget = {}; });
  const b = await p.deserialize(s);
  assert.strictEqual(b.size, 8); assert.ok(!('future' in b)); assert.ok(!('extraTarget' in b.overlays)); assert.deepStrictEqual(Object.keys(b.overlays).sort(), [...m.TARGETS].sort());
  sameBytes(b.overlays.team.cov, d.overlays.team.cov, 'team.cov'); });
test('settings and layers are copied whole, so keys added by later features survive a save', async () => {
  const { d } = fullDoc(8); d.settings.previewTeam = 'Red'; d.layers[0].locked = true; d.layers[1].params.nested = { a: [1, 2, { b: null }] };
  const b = await p.deserialize(await p.serialize(d));
  assert.deepStrictEqual(b.settings, d.settings); assert.deepStrictEqual(b.layers, d.layers); });

// ---- Непідтримуваний формат і версія ----
test('wrong version or format value is unsupported', async () => {
  const { d } = fullDoc(8);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.version = 2; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.version = 0; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.version = '1'; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize(await tweak(d, j => { delete j.version; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.format = 'ra3Texture'; })), /Непідтримуваний формат проєкту/);
  await assert.rejects(p.deserialize('{"format":"ra3texture"}'), /Непідтримуваний формат проєкту/);
  for (const x of ['null', '42', '"ra3texture"', '[]', 'true']) await assert.rejects(p.deserialize(x), /Непідтримуваний формат проєкту/, x); });

// ---- Пошкоджені файли ----
const CORRUPT = /Пошкоджений файл проєкту/;
test('invalid JSON is a corrupt project', async () => {
  await assert.rejects(p.deserialize('{"format":"ra3texture","version":1,'), CORRUPT);
  await assert.rejects(p.deserialize('це не JSON'), CORRUPT);
  await assert.rejects(p.deserialize(''), CORRUPT);
  const s = await p.serialize(cleanDoc(8));
  await assert.rejects(p.deserialize(s.slice(0, s.length >> 1)), CORRUPT);
  for (const x of [undefined, null, 5, {}, Buffer.from(s)]) await assert.rejects(p.deserialize(x), CORRUPT); });
test('truncated base64 is a corrupt project (image, nrmImport, overlay)', async () => {
  const { d, imageId } = fullDoc(8);
  const cut = (v, k) => v.slice(0, v.length - k);
  for (const k of [1, 2, 3, 5]) {      // довжина base64 більше не кратна 4
    await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = cut(j.images[imageId], k); })), CORRUPT, 'image -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = cut(j.nrmImport, k); })), CORRUPT, 'nrm -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { j.overlays.spec.cov = cut(j.overlays.spec.cov, k); })), CORRUPT, 'cov -' + k); }
  // Обрізано рівно по межі групи з 4 символів: base64 коректний, але потік deflate обірваний.
  for (const k of [4, 8, 40]) {
    await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = cut(j.images[imageId], k); })), CORRUPT, 'image deflate -' + k);
    await assert.rejects(p.deserialize(await tweak(d, j => { j.overlays.color.value = cut(j.overlays.color.value, k); })), CORRUPT, 'color.value -' + k); } });
test('garbage in base64 or deflate is a corrupt project', async () => {
  const { d, imageId } = fullDoc(8);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = '!!!!' + j.images[imageId].slice(4); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = j.images[imageId].slice(0, 8) + ' ' + j.images[imageId].slice(9); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = ''; })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = 'AAAA=AAA'; })), CORRUPT);                    // «=» не в кінці
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = 'A==='; })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.overlays.team.value = Buffer.from('це не deflate, а звичайний текст').toString('base64'); })), CORRUPT);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.overlays.team.value = Buffer.alloc(300, 0xAB).toString('base64'); })), CORRUPT);
  const good = Buffer.from(d.nrmImport.buffer, d.nrmImport.byteOffset, d.nrmImport.length);
  const flipped = zlib.deflateSync(good); flipped[flipped.length - 2] ^= 0xFF;                                         // зіпсована контрольна сума
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = flipped.toString('base64'); })), CORRUPT); });
test('a buffer of the wrong length is a corrupt project and never yields a document', async () => {
  const { d, imageId } = fullDoc(8), n = 64;
  const wrong = len => b64(new Uint8ClampedArray(len).fill(7));
  const cases = [
    ['image shorter', j => { j.images[imageId] = wrong(n * 4 - 1); }], ['image longer', j => { j.images[imageId] = wrong(n * 4 + 1); }],
    ['image n instead of 4n', j => { j.images[imageId] = wrong(n); }], ['image empty', j => { j.images[imageId] = wrong(0); }],
    ['image 4x too long', j => { j.images[imageId] = wrong(n * 16); }],
    ['nrmImport shorter', j => { j.nrmImport = wrong(n * 4 - 4); }], ['nrmImport longer', j => { j.nrmImport = wrong(n * 4 + 4); }],
    ['color.value n (not 4n)', j => { j.overlays.color.value = wrong(n); }], ['color.value 4n+1', j => { j.overlays.color.value = wrong(n * 4 + 1); }],
    ['color.cov 4n', j => { j.overlays.color.cov = wrong(n * 4); }],
    ['spec.value 4n', j => { j.overlays.spec.value = wrong(n * 4); }], ['spec.value n-1', j => { j.overlays.spec.value = wrong(n - 1); }],
    ['team.cov n+1', j => { j.overlays.team.cov = wrong(n + 1); }], ['alpha.cov empty', j => { j.overlays.alpha.cov = wrong(0); }],
    ['height.value 16x too long', j => { j.overlays.height.value = wrong(n * 16); }],
    ['size does not match the buffers', j => { j.size = 16; }], ['size smaller than the buffers', j => { j.size = 4; }]];
  for (const [name, fn] of cases) await assert.rejects(p.deserialize(await tweak(d, fn)), CORRUPT, name);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.images[imageId] = wrong(n * 16); })), /більше/);          // надлишок відсікається одразу
  await assert.rejects(p.deserialize(await tweak(d, j => { j.nrmImport = wrong(n * 4 - 4); })), /очікується 256/); });
test('a tiny file cannot claim a huge document: lengths are checked against what is inflated', async () => {
  const { d } = fullDoc(8);
  await assert.rejects(p.deserialize(await tweak(d, j => { j.size = 1 << 20; })), CORRUPT); });
test('bad size is a corrupt project', async () => {
  const { d } = fullDoc(8);
  for (const sz of [500, 0, -8, 3, 8.5, '8', null, 'x', [8], {}]) await assert.rejects(p.deserialize(await tweak(d, j => { j.size = sz; })), /Пошкоджений файл проєкту.*розмір/, String(sz));
  await assert.rejects(p.deserialize(await tweak(d, j => { delete j.size; })), CORRUPT); });
test('missing or malformed structure is a corrupt project', async () => {
  const { d, imageId } = fullDoc(8);
  const cases = [
    ['no images', j => { delete j.images; }], ['images array', j => { j.images = []; }], ['images null', j => { j.images = null; }], ['image value number', j => { j.images[imageId] = 5; }], ['image value null', j => { j.images[imageId] = null; }],
    ['no nrmImport', j => { delete j.nrmImport; }], ['nrmImport number', j => { j.nrmImport = 1; }],
    ['no overlays', j => { delete j.overlays; }], ['overlays string', j => { j.overlays = 'x'; }], ['overlay target missing', j => { delete j.overlays.team; }],
    ['overlay entry null', j => { j.overlays.spec = null; }], ['overlay value missing', j => { delete j.overlays.alpha.value; }], ['overlay cov number', j => { j.overlays.alpha.cov = 3; }],
    ['no layers', j => { delete j.layers; }], ['layers object', j => { j.layers = {}; }], ['layer null', j => { j.layers[0] = null; }], ['layer string', j => { j.layers[1] = 'base'; }], ['layer array', j => { j.layers[1] = []; }],
    ['no settings', j => { delete j.settings; }], ['settings null', j => { j.settings = null; }], ['nrmStrength string', j => { j.settings.nrmStrength = '2'; }],
    ['nrmStrength null', j => { j.settings.nrmStrength = null; }], ['invertY number', j => { j.settings.invertY = 1; }],
    ['no name', j => { delete j.name; }], ['name number', j => { j.name = 5; }]];
  for (const [name, fn] of cases) await assert.rejects(p.deserialize(await tweak(d, fn)), CORRUPT, name); });
test('an empty-name project is fine', async () => {
  const a = cleanDoc(4); a.name = ''; assert.strictEqual((await p.deserialize(await p.serialize(a))).name, ''); });

// ---- serialize відмовляється від непридатного документа ----
test('serialize refuses a document whose buffers do not match its size (it would not open again)', async () => {
  const a = cleanDoc(8); a.nrmImport = new Uint8ClampedArray(12);
  await assert.rejects(p.serialize(a), /NRM/);
  const b = cleanDoc(8); b.images.x = new Uint8ClampedArray(10);
  await assert.rejects(p.serialize(b), /«x»/);
  const c = cleanDoc(8); c.overlays.team.cov = new Uint8ClampedArray(3);
  await assert.rejects(p.serialize(c), /team/);
  const d = cleanDoc(8); d.overlays.color.value = new Uint8ClampedArray(64);
  await assert.rejects(p.serialize(d), /color/); });

// ---- Експорт ----
test('exports serialize, deserialize and the format constants', () => {
  assert.strictEqual(typeof p.serialize, 'function'); assert.strictEqual(typeof p.deserialize, 'function');
  assert.strictEqual(p.FORMAT, 'ra3texture'); assert.strictEqual(p.VERSION, 1); });
