const test = require('node:test'); const assert = require('node:assert');
const m = require('../js/maps.js'); const reg = { apply() {} };

// ---- Тести з брифу ----
test('flat height gives 128,128,255', () => { const d = m.createDocument({ size: 8 }); m.rebuild(d, reg);
  assert.deepStrictEqual([...m.buildNRM(d).slice(0, 4)], [128, 128, 255, 255]); });
test('slope direction DirectX vs OpenGL', () => { const d = m.createDocument({ size: 8 });
  const r = { apply(l, maps, s) { for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) maps.height[y * s + x] = y < 4 ? 0.2 : 0.8; } };
  d.layers.push({ id: 'a', type: 't', enabled: true, seed: 1, params: {} }); m.rebuild(d, r);
  const gDx = m.buildNRM(d)[(3 * 8 + 2) * 4 + 1]; d.settings.invertY = true; const gGl = m.buildNRM(d)[(3 * 8 + 2) * 4 + 1];
  assert.ok(gDx < 128 && gGl > 128); });
test('SPM packs R=spec G=0 B=team A=255', () => { const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  d.maps.spec[0] = 200; d.maps.team[0] = 77; assert.deepStrictEqual([...m.buildSPM(d).slice(0, 4)], [200, 0, 77, 255]); });
test('brush overlay full and erase', () => { const d = m.createDocument({ size: 16 });
  m.applyBrush(d, 'team', 8, 8, { radius: 3, hardness: 1, strength: 1, value: 255 }); m.rebuild(d, reg);
  assert.strictEqual(d.maps.team[8 * 16 + 8], 255);
  m.applyBrush(d, 'team', 8, 8, { radius: 3, hardness: 1, strength: 1, erase: true }); m.rebuild(d, reg);
  assert.strictEqual(d.maps.team[8 * 16 + 8], 0); });
test('brush wraps around edges', () => { const d = m.createDocument({ size: 16 });
  m.applyBrush(d, 'spec', 0, 0, { radius: 2, hardness: 1, strength: 1, value: 255 }); m.rebuild(d, reg);
  assert.strictEqual(d.maps.spec[15 * 16 + 15], 255); });
test('addImageLayer resamples non-square image', () => { const d = m.createDocument({ size: 8 });
  const L = m.addImageLayer(d, { width: 3, height: 5, data: new Uint8ClampedArray(60).fill(90) }, { target: 'color' });
  assert.strictEqual(d.images[L.params.imageId].length, 8 * 8 * 4); assert.strictEqual(L.type, 'image'); });
test('sanitizeName', () => { assert.strictEqual(m.sanitizeName('AU My-Tank!'), 'AUMyTank'); assert.strictEqual(m.sanitizeName('Мій'), 'MySkin'); });
test('classifyTextureName is case-insensitive', () => {
  assert.deepStrictEqual(m.classifyTextureName('AUTank_nrm.DDS'), { base: 'AUTank', role: 'nrm' });
  assert.deepStrictEqual(m.classifyTextureName('AUTank_Spm.dds'), { base: 'AUTank', role: 'spm' });
  assert.deepStrictEqual(m.classifyTextureName('AUTank.tga'), { base: 'AUTank', role: 'diffuse' }); });
test('game view applies team color only under mask', () => { const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  d.maps.team[0] = 255; const v = m.viewRGBA(d, 'game', [230, 20, 20]);
  assert.ok(v[0] > v[1] + 40); assert.ok(Math.abs(v[4] - v[5]) < 3); });
test('resizeDocument keeps overlays', () => { const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'team', 4, 4, { radius: 2, hardness: 1, strength: 1, value: 255 }); m.resizeDocument(d, 16); m.rebuild(d, reg);
  assert.strictEqual(d.maps.team.length, 256); assert.strictEqual(d.maps.team[8 * 16 + 8], 255); });

// ---- Допоміжне ----
const IMG = size => ({ width: size, height: size, data: new Uint8ClampedArray(size * size * 4).fill(200) });
// Реєстр, що задає висоту функцією від (x, y).
const heightReg = f => ({ apply(l, maps, s) { for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) maps.height[y * s + x] = f(x, y); } });
const withLayer = (d, r) => { d.layers.push({ id: 'a', type: 't', enabled: true, seed: 1, params: {} }); m.rebuild(d, r); return d; };
const decodeRGB = (buf, i) => [buf[i * 4] / 127.5 - 1, buf[i * 4 + 1] / 127.5 - 1, buf[i * 4 + 2] / 127.5 - 1];

// ---- createDocument ----
test('createDocument defaults: name, size, settings, buffers in base state', () => {
  const d = m.createDocument();
  assert.strictEqual(d.name, 'AUMyTank'); assert.strictEqual(d.size, 512);
  assert.deepStrictEqual(d.settings, { nrmStrength: 2, invertY: false });
  assert.deepStrictEqual(d.layers, []); assert.deepStrictEqual(d.images, {}); assert.strictEqual(d.nrmImport, null);
  const n = 512 * 512;
  assert.strictEqual(d.maps.color.length, n * 4); assert.ok(d.maps.height instanceof Float32Array); assert.strictEqual(d.maps.height.length, n);
  assert.strictEqual(d.maps.spec.length, n); assert.strictEqual(d.maps.team.length, n);
  assert.deepStrictEqual([...d.maps.color.slice(0, 4)], [128, 128, 128, 255]); assert.strictEqual(d.maps.height[0], 0.5);
  assert.deepStrictEqual(Object.keys(d.overlays).sort(), [...m.TARGETS].sort());
  assert.strictEqual(d.overlays.color.value.length, n * 4); assert.strictEqual(d.overlays.color.cov.length, n);
  for (const t of ['alpha', 'height', 'spec', 'team']) {
    assert.strictEqual(d.overlays[t].value.length, n, t); assert.strictEqual(d.overlays[t].cov.length, n, t);
    assert.ok(d.overlays[t].cov.every(v => v === 0), t); } });
test('createDocument uses given name and rejects sizes that are not a power of two', () => {
  assert.strictEqual(m.createDocument({ name: 'X', size: 4 }).name, 'X');
  assert.throws(() => m.createDocument({ size: 500 }), /розмір/i);
  assert.throws(() => m.createDocument({ size: 0 }), /розмір/i); });
test('TARGETS and TEAM_COLORS match the global constraints', () => {
  assert.deepStrictEqual(m.TARGETS, ['color', 'alpha', 'height', 'spec', 'team']);
  assert.deepStrictEqual(m.TEAM_COLORS.map(c => c.id), ['Gold', 'Red', 'Blue', 'Green', 'Orange', 'SkyBlue', 'Purple', 'Pink']);
  assert.deepStrictEqual(m.TEAM_COLORS.map(c => c.rgb), [[240, 215, 25], [230, 20, 20], [50, 75, 200], [20, 105, 60],
    [225, 115, 20], [102, 207, 248], [125, 25, 200], [248, 152, 248]]); });

// ---- rebuild ----
test('rebuild resets maps to the base state before applying layers', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  d.maps.color[0] = 5; d.maps.height[1] = 0.9; d.maps.spec[2] = 77; d.maps.team[3] = 99;
  m.rebuild(d, reg);
  assert.deepStrictEqual([...d.maps.color.slice(0, 4)], [128, 128, 128, 255]); assert.strictEqual(d.maps.height[1], 0.5);
  assert.strictEqual(d.maps.spec[2], 0); assert.strictEqual(d.maps.team[3], 0); });
test('rebuild applies only enabled layers, in order, with (layer, maps, size, images)', () => {
  const d = m.createDocument({ size: 4 }); const calls = [];
  const r = { apply(layer, maps, size, images) { calls.push([layer.id, maps, size, images]); } };
  d.layers.push({ id: 'a', type: 't', name: 'A', enabled: true, seed: 1, params: {} });
  d.layers.push({ id: 'b', type: 't', name: 'B', enabled: false, seed: 1, params: {} });
  d.layers.push({ id: 'c', type: 't', name: 'C', enabled: true, seed: 1, params: {} });
  m.rebuild(d, r);
  assert.deepStrictEqual(calls.map(c => c[0]), ['a', 'c']);
  for (const c of calls) { assert.strictEqual(c[1], d.maps); assert.strictEqual(c[2], 4); assert.strictEqual(c[3], d.images); } });
test('overlay wins over layers where coverage is full and blends where partial', () => {
  const d = m.createDocument({ size: 16 });
  const r = { apply(l, maps) { maps.spec.fill(50); } };
  d.layers.push({ id: 'a', type: 't', enabled: true, seed: 1, params: {} });
  d.overlays.spec.value.fill(250); d.overlays.spec.cov[0] = 255; d.overlays.spec.cov[1] = 128;
  m.rebuild(d, r);
  assert.strictEqual(d.maps.spec[0], 250);
  assert.strictEqual(d.maps.spec[1], Math.round(50 + (250 - 50) * 128 / 255));
  assert.strictEqual(d.maps.spec[2], 50); });
test('colour, alpha and height overlays go to their own maps', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'color', 2, 2, { radius: 1, hardness: 1, strength: 1, value: [10, 20, 30] });
  m.applyBrush(d, 'alpha', 5, 5, { radius: 1, hardness: 1, strength: 1, value: 40 });
  m.applyBrush(d, 'height', 2, 5, { radius: 1, hardness: 1, strength: 1, value: 255 });
  m.rebuild(d, reg);
  assert.deepStrictEqual([...d.maps.color.slice((2 * 8 + 2) * 4, (2 * 8 + 2) * 4 + 4)], [10, 20, 30, 255]);
  assert.deepStrictEqual([...d.maps.color.slice((5 * 8 + 5) * 4, (5 * 8 + 5) * 4 + 4)], [128, 128, 128, 40]);   // alpha пишеться в maps.color[i*4+3]
  assert.ok(Math.abs(d.maps.height[5 * 8 + 2] - 1) < 1e-6);                                                    // 255 -> 1.0
  assert.strictEqual(d.maps.height[0], 0.5); assert.strictEqual(d.maps.spec[2 * 8 + 2], 0); });
test('brush strokes survive a rebuild with changed layers', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'team', 3, 3, { radius: 2, hardness: 1, strength: 1, value: 200 });
  d.layers.push({ id: 'a', type: 't', enabled: true, seed: 1, params: {} });
  m.rebuild(d, { apply(l, maps) { maps.team.fill(10); } });
  assert.strictEqual(d.maps.team[3 * 8 + 3], 200); assert.strictEqual(d.maps.team[0], 10);
  d.layers[0].enabled = false; m.rebuild(d, { apply(l, maps) { maps.team.fill(10); } });
  assert.strictEqual(d.maps.team[3 * 8 + 3], 200); assert.strictEqual(d.maps.team[0], 0); });

// ---- applyBrush ----
test('brush: hardness 1 is a hard disc (d < r), pixel (cx,cy) is at distance 0', () => {
  const d = m.createDocument({ size: 16 });
  m.applyBrush(d, 'spec', 8, 8, { radius: 3, hardness: 1, strength: 1, value: 255 });
  const cov = d.overlays.spec.cov;
  assert.strictEqual(cov[8 * 16 + 8], 255); assert.strictEqual(cov[10 * 16 + 10], 255);   // d = 2.83 < 3
  assert.strictEqual(cov[8 * 16 + 11], 0); assert.strictEqual(cov[11 * 16 + 8], 0);        // d = 3: межа не входить
  assert.strictEqual(cov[10 * 16 + 11], 0);
  assert.strictEqual(cov[8 * 16 + 5], 0); assert.strictEqual(cov[8 * 16 + 6], 255); });    // симетрично відносно cx
test('brush: soft falloff 1 - smoothstep(hardness*r, r, d)', () => {
  const d = m.createDocument({ size: 16 });
  m.applyBrush(d, 'spec', 8, 8, { radius: 4, hardness: 0, strength: 1, value: 200 });
  const cov = d.overlays.spec.cov;
  assert.strictEqual(cov[8 * 16 + 8], 255);
  assert.ok(Math.abs(cov[8 * 16 + 10] - 127.5) <= 1, 'посередині smoothstep = 0.5');            // d = 2
  assert.strictEqual(cov[8 * 16 + 12], 0);                                                      // d = r
  assert.ok(cov[8 * 16 + 9] > cov[8 * 16 + 10] && cov[8 * 16 + 10] > cov[8 * 16 + 11]);
  const half = m.createDocument({ size: 16 });                                                  // hardness 0.5: повне покриття всередині r/2
  m.applyBrush(half, 'spec', 8, 8, { radius: 4, hardness: 0.5, strength: 1, value: 200 });
  assert.strictEqual(half.overlays.spec.cov[8 * 16 + 10], 255); assert.ok(half.overlays.spec.cov[8 * 16 + 11] < 255); });
test('brush: strength accumulates as cov += (255-cov)*strength', () => {
  const d = m.createDocument({ size: 8 }); const o = { radius: 2, hardness: 1, strength: 0.5, value: 255 };
  m.applyBrush(d, 'team', 4, 4, o); assert.ok(Math.abs(d.overlays.team.cov[4 * 8 + 4] - 127.5) <= 1);
  m.applyBrush(d, 'team', 4, 4, o); assert.ok(Math.abs(d.overlays.team.cov[4 * 8 + 4] - 191.25) <= 1.5);
  m.applyBrush(d, 'team', 4, 4, { radius: 2, hardness: 1, strength: 0.5, erase: true });
  assert.ok(Math.abs(d.overlays.team.cov[4 * 8 + 4] - 95.6) <= 1.5); });
test('brush: value on fresh coverage becomes the target, on full coverage is pulled by strength', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'spec', 4, 4, { radius: 2, hardness: 1, strength: 0.25, value: 200 });
  assert.strictEqual(d.overlays.spec.value[4 * 8 + 4], 200);                                    // а не 0.25*200
  m.applyBrush(d, 'team', 4, 4, { radius: 2, hardness: 1, strength: 1, value: 100 });
  m.applyBrush(d, 'team', 4, 4, { radius: 2, hardness: 1, strength: 0.5, value: 200 });
  assert.strictEqual(d.overlays.team.value[4 * 8 + 4], 150);
  assert.strictEqual(d.overlays.team.cov[4 * 8 + 4], 255); });
test('brush: colour target pulls r,g,b together', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'color', 4, 4, { radius: 2, hardness: 1, strength: 1, value: [200, 100, 0] });
  m.applyBrush(d, 'color', 4, 4, { radius: 2, hardness: 1, strength: 0.5, value: [0, 100, 200] });
  const v = d.overlays.color.value, i = (4 * 8 + 4) * 4;
  assert.deepStrictEqual([v[i], v[i + 1], v[i + 2]], [100, 100, 100]); });
test('brush: erase only lowers coverage and keeps value; on empty overlay it changes nothing', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'spec', 4, 4, { radius: 2, hardness: 1, strength: 1, erase: true });
  assert.ok(d.overlays.spec.cov.every(v => v === 0));
  m.applyBrush(d, 'spec', 4, 4, { radius: 2, hardness: 1, strength: 1, value: 200 });
  m.applyBrush(d, 'spec', 4, 4, { radius: 2, hardness: 1, strength: 0.5, erase: true });
  assert.ok(Math.abs(d.overlays.spec.cov[4 * 8 + 4] - 127.5) <= 1); assert.strictEqual(d.overlays.spec.value[4 * 8 + 4], 200); });
test('brush: radius larger than the texture touches every pixel exactly once', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'spec', 3, 3, { radius: 20, hardness: 1, strength: 0.5, value: 255 });
  assert.ok(d.overlays.spec.cov.every(v => Math.abs(v - 127.5) <= 1)); });
test('brush: wraps in both axes and keeps the disc round across the seam', () => {
  const d = m.createDocument({ size: 16 });
  m.applyBrush(d, 'spec', 15, 0, { radius: 2.5, hardness: 1, strength: 1, value: 255 });
  const cov = d.overlays.spec.cov;
  assert.strictEqual(cov[0 * 16 + 1], 255);                 // dx = +2 через шов
  assert.strictEqual(cov[15 * 16 + 15], 255);               // dy = -1 через шов
  assert.strictEqual(cov[15 * 16 + 1], 255);                // dx = 2, dy = -1: d = 2.24 < 2.5
  assert.strictEqual(cov[0 * 16 + 2], 0);                   // dx = 3 > r
  assert.strictEqual(cov[8 * 16 + 8], 0); });
test('brush: bad arguments', () => {
  const d = m.createDocument({ size: 8 });
  assert.throws(() => m.applyBrush(d, 'bogus', 1, 1, { radius: 2, value: 1 }), /ціль/i);
  assert.throws(() => m.applyBrush(d, 'spec', 1, 1, { radius: 2 }), /значення/i);
  assert.throws(() => m.applyBrush(d, 'color', 1, 1, { radius: 2, value: 5 }), /значення/i);
  m.applyBrush(d, 'spec', 1, 1, { radius: 0, value: 255 });                  // нульовий радіус нічого не малює
  assert.ok(d.overlays.spec.cov.every(v => v === 0)); });

// ---- cloneOverlay / restoreOverlay ----
test('cloneOverlay snapshots independently and restoreOverlay undoes a stroke', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'color', 2, 2, { radius: 2, hardness: 1, strength: 1, value: [1, 2, 3] });
  const snap = m.cloneOverlay(d, 'color');
  assert.strictEqual(snap.target, 'color'); assert.strictEqual(snap.cov.length, 64); assert.strictEqual(snap.value.length, 256);
  assert.notStrictEqual(snap.cov, d.overlays.color.cov);
  m.applyBrush(d, 'color', 6, 6, { radius: 2, hardness: 1, strength: 1, value: [9, 9, 9] });
  assert.strictEqual(snap.cov[6 * 8 + 6], 0);                    // знімок не змінився від мазка
  m.restoreOverlay(d, snap);
  assert.deepStrictEqual([...d.overlays.color.cov], [...snap.cov]); assert.deepStrictEqual([...d.overlays.color.value], [...snap.value]);
  assert.strictEqual(d.overlays.color.cov[6 * 8 + 6], 0);
  m.applyBrush(d, 'color', 6, 6, { radius: 2, hardness: 1, strength: 1, value: [9, 9, 9] });
  assert.strictEqual(snap.cov[6 * 8 + 6], 0); });                // restore не прив'язує знімок до живих даних
test('restoreOverlay refuses a snapshot of another size or unknown target', () => {
  const d = m.createDocument({ size: 8 }); const snap = m.cloneOverlay(d, 'spec');
  m.resizeDocument(d, 16);
  assert.throws(() => m.restoreOverlay(d, snap), /розмір/i);
  assert.throws(() => m.cloneOverlay(d, 'bogus'), /ціль/i); });

// ---- addImageLayer ----
test('addImageLayer builds an image layer with unique ids and appends it', () => {
  const d = m.createDocument({ size: 8 });
  const a = m.addImageLayer(d, IMG(8), { target: 'spec', channel: 'g', name: 'Моя' });
  const b = m.addImageLayer(d, IMG(8), {});
  assert.deepStrictEqual(d.layers, [a, b]);
  assert.strictEqual(a.type, 'image'); assert.strictEqual(a.name, 'Моя'); assert.strictEqual(a.enabled, true); assert.strictEqual(typeof a.seed, 'number');
  assert.deepStrictEqual(a.params, { imageId: a.params.imageId, target: 'spec', channel: 'g', opacity: 1 });
  assert.deepStrictEqual(b.params, { imageId: b.params.imageId, target: 'color', channel: 'lum', opacity: 1 });
  assert.notStrictEqual(a.id, b.id); assert.notStrictEqual(a.params.imageId, b.params.imageId);
  assert.ok(typeof b.name === 'string' && b.name.length > 0);
  assert.deepStrictEqual(Object.keys(d.images).sort(), [a.params.imageId, b.params.imageId].sort()); });
test('addImageLayer stores its own copy, even when the size already matches', () => {
  const d = m.createDocument({ size: 8 }); const src = IMG(8);
  const L = m.addImageLayer(d, src, {}); const stored = d.images[L.params.imageId];
  assert.notStrictEqual(stored, src.data); assert.ok(stored instanceof Uint8ClampedArray);
  src.data.fill(1); assert.ok(stored.every(v => v === 200)); });
test('addImageLayer validates target, channel and data length', () => {
  const d = m.createDocument({ size: 8 });
  assert.throws(() => m.addImageLayer(d, IMG(8), { target: 'bogus' }), /ціль/i);
  assert.throws(() => m.addImageLayer(d, IMG(8), { channel: 'x' }), /канал/i);
  assert.throws(() => m.addImageLayer(d, { width: 4, height: 4, data: new Uint8ClampedArray(10) }, {}));
  assert.deepStrictEqual(d.layers, []); assert.deepStrictEqual(d.images, {}); });

// ---- buildDiffuse / buildSPM ----
test('buildDiffuse is an independent copy of maps.color', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg); d.maps.color[3] = 17;
  const out = m.buildDiffuse(d);
  assert.deepStrictEqual([...out], [...d.maps.color]); assert.notStrictEqual(out, d.maps.color);
  out[0] = 0; assert.strictEqual(d.maps.color[0], 128); });
test('buildSPM fills every pixel: R=spec, G=0, B=team, A=255', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  for (let i = 0; i < 16; i++) { d.maps.spec[i] = i * 10; d.maps.team[i] = 255 - i; }
  const s = m.buildSPM(d); assert.strictEqual(s.length, 64);
  for (let i = 0; i < 16; i++) assert.deepStrictEqual([...s.slice(i * 4, i * 4 + 4)], [i * 10, 0, 255 - i, 255]); });

// ---- buildNRM ----
test('NRM: tangent normals are unit length and tile across the seam', () => {
  const d = m.createDocument({ size: 16 });
  withLayer(d, heightReg((x, y) => 0.5 + 0.4 * Math.sin(x * 0.9) * Math.cos(y * 0.7)));
  const n = m.buildNRM(d);
  for (let i = 0; i < 256; i++) { const [x, y, z] = decodeRGB(n, i); assert.ok(Math.abs(Math.hypot(x, y, z) - 1) < 0.02, 'pixel ' + i); assert.ok(z > 0); assert.strictEqual(n[i * 4 + 3], 255); } });
test('NRM: wraps the Sobel kernel (a step at x=4 is also seen at the x=0/7 seam)', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg(x => x < 4 ? 0.2 : 0.8));
  const n = m.buildNRM(d), R = (x, y) => n[(y * 8 + x) * 4];
  assert.ok(R(3, 2) < 128 && R(4, 2) < 128);        // висота росте з x -> n.x < 0
  assert.ok(R(0, 2) > 128 && R(7, 2) > 128);        // через шов висота падає з x -> n.x > 0
  assert.strictEqual(R(1, 2), 128); assert.strictEqual(R(5, 2), 128); });
test('NRM: strength scales the slope; value is normalised Sobel (per-pixel gradient)', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg(x => 0.1 * x));
  const R = () => m.buildNRM(d)[(4 * 8 + 3) * 4];
  // slope 0.1/px, s = 2: n = normalize(-0.2, 0, 1) -> R = (0.5 - 0.5*0.196)*255 = 102.5
  assert.ok(R() === 102 || R() === 103, String(R()));
  d.settings.nrmStrength = 0; assert.strictEqual(R(), 128);
  d.settings.nrmStrength = 10; assert.ok(R() < 102); });
test('NRM: OpenGL flips only the green channel', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg((x, y) => 0.1 * x + 0.05 * y));
  const a = m.buildNRM(d); d.settings.invertY = true; const b = m.buildNRM(d); const i = (3 * 8 + 3) * 4;
  assert.strictEqual(a[i], b[i]); assert.strictEqual(a[i + 2], b[i + 2]);
  assert.ok(Math.abs(a[i + 1] + b[i + 1] - 255) <= 1); assert.notStrictEqual(a[i + 1], 128); });
test('NRM: imported flat map (128,128,255) leaves the result unchanged', () => {
  const d = m.createDocument({ size: 8 });
  withLayer(d, heightReg((x, y) => 0.5 + 0.3 * Math.sin(x * 0.8 + y * 0.5)));
  const plain = m.buildNRM(d);
  d.nrmImport = new Uint8ClampedArray(8 * 8 * 4); for (let i = 0; i < 64; i++) d.nrmImport.set([128, 128, 255, 255], i * 4);
  const mixed = m.buildNRM(d);
  for (let i = 0; i < plain.length; i++) assert.ok(Math.abs(plain[i] - mixed[i]) <= 1, 'byte ' + i);
  const flat = m.createDocument({ size: 8 }); m.rebuild(flat, reg); flat.nrmImport = d.nrmImport;
  assert.deepStrictEqual([...m.buildNRM(flat).slice(0, 4)], [128, 128, 255, 255]); });
test('NRM: import on flat height passes through in either convention (same convention for both operands)', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  d.nrmImport = new Uint8ClampedArray(4 * 4 * 4); for (let i = 0; i < 16; i++) d.nrmImport.set([204, 66, 209, 255], i * 4);   // n = (0.6, -0.48, 0.64)
  for (const inv of [false, true]) { d.settings.invertY = inv; const o = m.buildNRM(d);
    for (let k = 0; k < 3; k++) assert.ok(Math.abs(o[k] - [204, 66, 209][k]) <= 1, 'invertY=' + inv + ' ch ' + k);
    assert.strictEqual(o[3], 255); } });
test('NRM: whiteout adds the xy of the import to the height-derived normal', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg(x => 0.1 * x));
  const plain = m.buildNRM(d)[(4 * 8 + 3) * 4];
  d.nrmImport = new Uint8ClampedArray(8 * 8 * 4); for (let i = 0; i < 64; i++) d.nrmImport.set([204, 128, 209, 255], i * 4);     // n.x = +0.6
  const mixed = m.buildNRM(d); const [x, y, z] = decodeRGB(mixed, 4 * 8 + 3);
  assert.ok(mixed[(4 * 8 + 3) * 4] > plain);                      // додався нахил у бік +x
  assert.ok(Math.abs(Math.hypot(x, y, z) - 1) < 0.02); });
test('NRM: import of a wrong length is an error', () => {
  const d = m.createDocument({ size: 4 }); d.nrmImport = new Uint8ClampedArray(8);
  assert.throws(() => m.buildNRM(d), /NRM/); });

// ---- viewRGBA ----
test('viewRGBA colour, height, alpha views', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  d.maps.color.set([10, 20, 30, 40], 0); d.maps.height[1] = 1; d.maps.height[2] = 0; d.maps.height[3] = 0.25;
  const c = m.viewRGBA(d, 'color'); assert.strictEqual(c.length, 64); assert.deepStrictEqual([...c.slice(0, 4)], [10, 20, 30, 255]);
  const h = m.viewRGBA(d, 'height');
  assert.deepStrictEqual([...h.slice(0, 4)], [128, 128, 128, 255]); assert.deepStrictEqual([...h.slice(4, 8)], [255, 255, 255, 255]);
  assert.deepStrictEqual([...h.slice(8, 12)], [0, 0, 0, 255]); assert.deepStrictEqual([...h.slice(12, 16)], [64, 64, 64, 255]);
  const a = m.viewRGBA(d, 'alpha');
  assert.deepStrictEqual([...a.slice(0, 4)], [40, 40, 40, 255]); assert.deepStrictEqual([...a.slice(4, 8)], [255, 255, 255, 255]); });
test('viewRGBA nrm and spm views are the export buffers', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg(x => 0.1 * x)); d.maps.spec[5] = 99; d.maps.team[5] = 33;
  assert.deepStrictEqual([...m.viewRGBA(d, 'nrm')], [...m.buildNRM(d)]);
  assert.deepStrictEqual([...m.viewRGBA(d, 'spm')], [...m.buildSPM(d)]); });
test('viewRGBA rejects an unknown view', () => {
  const d = m.createDocument({ size: 4 }); assert.throws(() => m.viewRGBA(d, 'bogus'), /вигляд/i); });
test('game view: flat grey follows ambient + lambert, gamma 2.2', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg);
  const L = [-0.5, -0.6, 0.62], ll = Math.hypot(...L);
  const lit = Math.pow(128 / 255, 2.2) * (0.35 + L[2] / ll);
  const want = Math.round(255 * Math.pow(lit, 1 / 2.2));
  const v = m.viewRGBA(d, 'game', [230, 20, 20]);
  for (let k = 0; k < 3; k++) assert.ok(Math.abs(v[k] - want) <= 1, `ch ${k}: ${v[k]} vs ${want}`);
  assert.strictEqual(v[3], 255); });
test('game view: 50% grey under the full mask becomes the player colour (lerp(c, c*T*2, mask))', () => {
  const T = [200, 100, 50];
  const a = m.createDocument({ size: 4 }); m.rebuild(a, reg); a.maps.team[0] = 255;
  const b = m.createDocument({ size: 4 }); m.rebuild(b, reg); b.maps.color.set([T[0], T[1], T[2], 255], 0);
  const va = m.viewRGBA(a, 'game', T), vb = m.viewRGBA(b, 'game', [0, 0, 0]);
  // c = 128/255 дає c*2 = 1.004, тож відтінок збігається з кольором гравця з похибкою в кілька відсотків
  for (let k = 0; k < 3; k++) assert.ok(Math.abs(va[k] - vb[k]) <= 3, `ch ${k}: ${va[k]} vs ${vb[k]}`); });
test('game view: a half mask gives a half-way recolour', () => {
  const T = [200, 100, 50]; const mk = v => { const d = m.createDocument({ size: 4 }); m.rebuild(d, reg); d.maps.team[0] = v; return m.viewRGBA(d, 'game', T); };
  const none = mk(0), half = mk(128), full = mk(255);
  assert.ok(none[0] === none[1] && none[1] === none[2]);
  assert.ok(half[0] > half[1] && half[0] - half[1] < full[0] - full[1] && half[0] - half[1] > 0);
  assert.ok(half[2] < none[2] && half[2] > full[2]); });
test('game view: specular term brightens by spm.r', () => {
  const d = m.createDocument({ size: 4 }); m.rebuild(d, reg); d.maps.spec[1] = 255; d.maps.spec[2] = 128;
  const v = m.viewRGBA(d, 'game', [230, 20, 20]);
  assert.ok(v[4] > v[0] + 20 && v[4] > v[8] && v[8] > v[0]); });
test('game view: sun from top-left, normal from NRM; invertY does not change the picture', () => {
  const d = m.createDocument({ size: 8 });
  withLayer(d, heightReg((x, y) => 0.1 * x + 0.1 * y));            // росте праворуч і вниз: нормаль дивиться вгору-ліворуч (на сонце)
  const toward = m.viewRGBA(d, 'game', [230, 20, 20]);
  const flat = m.createDocument({ size: 8 }); m.rebuild(flat, reg); const f = m.viewRGBA(flat, 'game', [230, 20, 20]);
  const i = (3 * 8 + 3) * 4;
  assert.ok(toward[i] > f[i] + 5);
  const away = m.createDocument({ size: 8 }); withLayer(away, heightReg((x, y) => -0.1 * x - 0.1 * y + 0.9));
  assert.ok(m.viewRGBA(away, 'game', [230, 20, 20])[i] < f[i] - 5);
  d.settings.invertY = true; const gl = m.viewRGBA(d, 'game', [230, 20, 20]);       // лише похибка 8-бітного кодування NRM
  for (let k = 0; k < toward.length; k++) assert.ok(Math.abs(gl[k] - toward[k]) <= 1, 'byte ' + k); });
test('game view: one tilted, glossy, recoloured pixel matches the shader formula from first principles', () => {
  const d = m.createDocument({ size: 8 }); withLayer(d, heightReg((x, y) => 0.3 * x + 0.15 * y));   // крутий схил: n.z ≈ 0.7
  const i = 3 * 8 + 3; d.maps.color.set([120, 70, 40, 255], i * 4); d.maps.spec[i] = 120; d.maps.team[i] = 200;
  const T = [102, 207, 248], got = m.viewRGBA(d, 'game', T);
  const nb = m.buildNRM(d);                                         // нормаль, як її прочитала б гра: tex*2-1, xy *= BumpScale
  let nx = (nb[i * 4] / 255 * 2 - 1) * 1.5, ny = (nb[i * 4 + 1] / 255 * 2 - 1) * 1.5, nz = nb[i * 4 + 2] / 255 * 2 - 1;
  const nl = Math.hypot(nx, ny, nz); nx /= nl; ny /= nl; nz /= nl;
  const Lraw = [-0.5, -0.6, 0.62], ll = Math.hypot(...Lraw);
  const ndl = Math.max(0, (nx * Lraw[0] + ny * Lraw[1] + nz * Lraw[2]) / ll);
  const mask = 200 / 255, refl = 0.8 * (120 / 255) * (0.6 + 0.4 * Math.max(0, nz));
  for (let k = 0; k < 3; k++) {
    const c = [120, 70, 40][k] / 255, rec = c + (c * (T[k] / 255) * 2 - c) * mask;       // lerp(c, c*T*2, spm.b) у гамма-просторі
    const lin = Math.pow(rec, 2.2) * (0.35 + ndl) + refl;
    const want = 255 * Math.pow(Math.min(1, lin), 1 / 2.2);
    assert.ok(Math.abs(got[i * 4 + k] - want) <= 1.01, `ch ${k}: ${got[i * 4 + k]} vs ${want.toFixed(2)}`); } });
test('game view: A < 96 shows a checkerboard, A >= 96 is drawn as the skin', () => {
  const d = m.createDocument({ size: 16 }); m.rebuild(d, reg);
  const A = (x, y, a) => { d.maps.color[(y * 16 + x) * 4 + 3] = a; };
  A(0, 0, 95); A(8, 0, 0); A(1, 0, 96);
  const v = m.viewRGBA(d, 'game', [230, 20, 20]); const px = (x, y) => [...v.slice((y * 16 + x) * 4, (y * 16 + x) * 4 + 4)];
  const skin = px(2, 0);
  assert.deepStrictEqual(px(1, 0), skin);                                              // поріг: A = 96 ще малюється
  for (const p of [px(0, 0), px(8, 0)]) { assert.ok(p[0] === p[1] && p[1] === p[2] && p[3] === 255); assert.notDeepStrictEqual(p, skin); }
  assert.notDeepStrictEqual(px(0, 0), px(8, 0));                                       // сусідні клітинки шахівниці мають різні відтінки
  A(0, 8, 0); assert.deepStrictEqual([...m.viewRGBA(d, 'game', [230, 20, 20]).slice(8 * 16 * 4, 8 * 16 * 4 + 4)], px(8, 0)); });   // (0,8) у діагональній клітинці = (8,0)

// ---- resizeDocument ----
test('resizeDocument resamples images and nrmImport, reallocates maps, updates size', () => {
  const d = m.createDocument({ size: 8 });
  const L = m.addImageLayer(d, IMG(8), {}); d.nrmImport = new Uint8ClampedArray(8 * 8 * 4).fill(128);
  const old = d.maps;
  m.resizeDocument(d, 16);
  assert.strictEqual(d.size, 16); assert.notStrictEqual(d.maps, old);
  assert.strictEqual(d.images[L.params.imageId].length, 16 * 16 * 4); assert.ok(d.images[L.params.imageId].every(v => v === 200));
  assert.strictEqual(d.nrmImport.length, 16 * 16 * 4); assert.ok(d.nrmImport.every(v => v === 128));
  assert.strictEqual(d.maps.color.length, 16 * 16 * 4); assert.strictEqual(d.maps.height.length, 256);
  assert.strictEqual(d.maps.height[0], 0.5); assert.strictEqual(d.maps.color[3], 255);
  for (const t of ['alpha', 'height', 'spec', 'team']) { assert.strictEqual(d.overlays[t].value.length, 256, t); assert.strictEqual(d.overlays[t].cov.length, 256, t); }
  assert.strictEqual(d.overlays.color.value.length, 1024); assert.strictEqual(d.overlays.color.cov.length, 256); });
test('resizeDocument resamples overlays by nearest neighbour, both ways, colour value included', () => {
  const d = m.createDocument({ size: 8 });
  m.applyBrush(d, 'color', 4, 4, { radius: 2, hardness: 1, strength: 1, value: [10, 20, 30] });
  m.resizeDocument(d, 16);
  const i = 8 * 16 + 8; assert.strictEqual(d.overlays.color.cov[i], 255);
  assert.deepStrictEqual([...d.overlays.color.value.slice(i * 4, i * 4 + 3)], [10, 20, 30]);
  assert.strictEqual(d.overlays.color.cov[0], 0);
  m.resizeDocument(d, 8); assert.strictEqual(d.overlays.color.cov[4 * 8 + 4], 255); assert.strictEqual(d.overlays.color.cov.length, 64); });
test('resizeDocument to the same size is a no-op; an invalid size throws and leaves the document intact', () => {
  const d = m.createDocument({ size: 8 }); const maps = d.maps, ov = d.overlays;
  m.resizeDocument(d, 8); assert.strictEqual(d.maps, maps); assert.strictEqual(d.overlays, ov);
  assert.throws(() => m.resizeDocument(d, 100), /розмір/i);
  assert.strictEqual(d.size, 8); assert.strictEqual(d.maps, maps); assert.strictEqual(d.overlays, ov); });

// ---- імена ----
test('sanitizeName keeps letters, digits and underscore only', () => {
  assert.strictEqual(m.sanitizeName('AU_Tank_01'), 'AU_Tank_01'); assert.strictEqual(m.sanitizeName(''), 'MySkin');
  assert.strictEqual(m.sanitizeName('!!!'), 'MySkin'); assert.strictEqual(m.sanitizeName(null), 'MySkin'); assert.strictEqual(m.sanitizeName(undefined), 'MySkin'); });
test('classifyTextureName handles paths, missing extension and non-suffix lookalikes', () => {
  assert.deepStrictEqual(m.classifyTextureName('C:\\skins\\AUTank_NRM.dds'), { base: 'AUTank', role: 'nrm' });
  assert.deepStrictEqual(m.classifyTextureName('a/b/AUTank_SPM.TGA'), { base: 'AUTank', role: 'spm' });
  assert.deepStrictEqual(m.classifyTextureName('AUTank_nrm'), { base: 'AUTank', role: 'nrm' });
  assert.deepStrictEqual(m.classifyTextureName('AUTank'), { base: 'AUTank', role: 'diffuse' });
  assert.deepStrictEqual(m.classifyTextureName('AUTank_nrm_old.dds'), { base: 'AUTank_nrm_old', role: 'diffuse' });
  assert.deepStrictEqual(m.classifyTextureName('Normal.png'), { base: 'Normal', role: 'diffuse' }); });
