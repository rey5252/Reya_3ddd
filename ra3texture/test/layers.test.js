const test = require('node:test'); const assert = require('node:assert');
const L = require('../js/layers.js'); const g = require('../js/generators.js');

// ---- Допоміжне ----
const MAT = { color: [200, 40, 10], alpha: 30, height: 0.9, spec: 180, team: 255 };
const px = (d, x, y) => { const i = y * d.size + x, o = i * 4; return { c: [...d.maps.color.slice(o, o + 4)], h: d.maps.height[i], s: d.maps.spec[i], t: d.maps.team[i] }; };
const ones = (w, h) => ({ w, h, data: new Float32Array(w * h).fill(1) });
const isBase = (d, msg) => {
  const m = d.maps, n = d.size * d.size;
  for (let i = 0; i < n; i++) {
    const o = i * 4;
    if (m.color[o] !== 128 || m.color[o + 1] !== 128 || m.color[o + 2] !== 128 || m.color[o + 3] !== 255 || m.height[i] !== 0.5 || m.spec[i] !== 0 || m.team[i] !== 0) {
      assert.fail((msg || 'база') + ': піксель ' + i + ' не базовий');
    }
  }
};
const sameMaps = (a, b, msg) => { for (const k of ['color', 'height', 'spec', 'team']) assert.deepStrictEqual(a[k], b[k], (msg || '') + ' ' + k); };
// Заглушка maskgen: ліва половина 1, права 0; запам'ятовує, які карти бачила.
function stubMaskgen() {
  const calls = [];
  return { calls, generate(gen, maps, size) {
    calls.push({ gen, color0: maps.color[0], height0: maps.height[0] });
    const out = new Float32Array(size * size);
    for (let y = 0; y < size; y++) for (let x = 0; x < size / 2; x++) out[y * size + x] = gen.params && gen.params.value !== undefined ? gen.params.value : 1;
    return out;
  } };
}
test.afterEach(() => L.setMaskgen(null));

// ---- Документ ----
test('createDocument: v2 shape, base maps, pow2 size', () => {
  const d = L.createDocument();
  assert.strictEqual(d.version, 2); assert.strictEqual(d.name, 'AUMyTank'); assert.strictEqual(d.size, 512);
  assert.deepStrictEqual(d.settings, { nrmStrength: 2, invertY: false }); assert.strictEqual(d.nrmImport, null);
  assert.deepStrictEqual(d.images, {}); assert.deepStrictEqual(d.layers, []);
  const s = L.createDocument({ name: 'X', size: 8 });
  assert.strictEqual(s.maps.color.length, 256); assert.strictEqual(s.maps.height.length, 64); assert.ok(s.maps.height instanceof Float32Array);
  isBase(s);
  assert.throws(() => L.createDocument({ size: 100 }), /степінь двійки/);
  assert.deepStrictEqual(L.CHANNELS, ['color', 'alpha', 'height', 'spec', 'team']); });
test('empty doc composite = base (also after maps were dirtied)', () => {
  const d = L.createDocument({ size: 8 }); d.maps.color.fill(3); d.maps.height.fill(0.1); d.maps.team.fill(9);
  L.composite(d); isBase(d); });

// ---- Шари ----
test('newLayer defaults per kind', () => {
  const f = L.newLayer('fill'), p = L.newLayer('paint'), q = L.newLayer('proc', { type: 'rust', seed: 7 });
  for (const l of [f, p, q]) { assert.strictEqual(l.visible, true); assert.strictEqual(l.opacity, 1); assert.strictEqual(l.heightMode, 'add'); assert.strictEqual(l.mask, null); assert.strictEqual(typeof l.id, 'string'); }
  assert.notStrictEqual(f.id, p.id); assert.notStrictEqual(p.id, q.id);
  assert.strictEqual(f.kind, 'fill'); assert.strictEqual(f.name, 'Заливка');
  assert.deepStrictEqual(f.channels, { color: true, alpha: false, height: false, spec: true, team: false });
  assert.deepStrictEqual(f.material, { color: [128, 128, 128], alpha: 255, height: 0.5, spec: 40, team: 0 });
  assert.strictEqual(p.name, 'Малювання'); assert.deepStrictEqual(p.channels, { color: true, alpha: false, height: true, spec: true, team: true }); assert.deepStrictEqual(p.paint, {});
  assert.strictEqual(q.name, 'Іржа'); assert.deepStrictEqual(q.channels, { color: true, alpha: true, height: true, spec: true, team: true });
  assert.deepStrictEqual(q.proc, { type: 'rust', seed: 7, params: g.defaultLayer('rust', 7).params });
  assert.throws(() => L.newLayer('weird'), /Невідомий вид шару/); });
test('newLayer: opts override fields; channels and material merge; proc from opts.proc', () => {
  const f = L.newLayer('fill', { name: 'Фарба', opacity: 0.3, channels: { height: true }, material: { color: [1, 2, 3] }, heightMode: 'replace' });
  assert.strictEqual(f.name, 'Фарба'); assert.strictEqual(f.opacity, 0.3); assert.strictEqual(f.heightMode, 'replace');
  assert.deepStrictEqual(f.channels, { color: true, alpha: false, height: true, spec: true, team: false });
  assert.deepStrictEqual(f.material, { color: [1, 2, 3], alpha: 255, height: 0.5, spec: 40, team: 0 });
  const mat = { color: [5, 6, 7], alpha: 1, height: 0.1, spec: 2, team: 3 }; const f2 = L.newLayer('fill', { material: mat });
  mat.color[0] = 99; assert.strictEqual(f2.material.color[0], 5, 'матеріал скопійовано');
  const q = L.newLayer('proc', { proc: { type: 'dirt', seed: 3, params: { amount: 0.9 } } });
  assert.strictEqual(q.proc.type, 'dirt'); assert.strictEqual(q.proc.seed, 3); assert.strictEqual(q.proc.params.amount, 0.9);
  assert.strictEqual(q.proc.params.scale, g.defaultLayer('dirt', 1).params.scale, 'відсутні параметри — типові');
  assert.throws(() => L.newLayer('proc', { type: 'nope' }), /Невідомий/);
  assert.throws(() => L.newLayer('proc'), /тип/); });
test('addMask / removeMask', () => {
  const l = L.newLayer('fill');
  L.addMask(l, 4); assert.deepStrictEqual(l.mask.enabled, true); assert.strictEqual(l.mask.invert, false); assert.strictEqual(l.mask.gen, null);
  assert.strictEqual(l.mask.paint.length, 16); assert.ok(l.mask.paint.every(v => v === 255));
  L.addMask(l, 4, 'black'); assert.ok(l.mask.paint.every(v => v === 0));
  assert.throws(() => L.addMask(l, 4, 'grey'), /маск/);
  L.removeMask(l); assert.strictEqual(l.mask, null); });
test('layerFromSpec: full layer, mask fill black allocated for the doc size, gen copied', () => {
  const gen = { type: 'edges', seed: 5, params: { width: 0.3 } };
  const spec = { kind: 'fill', name: 'Сталь', material: { color: [90, 90, 95], spec: 200 }, channels: { height: true }, heightMode: 'replace', opacity: 0.8, mask: { invert: true, fill: 'black', gen } };
  const l = L.layerFromSpec(spec, 8);
  assert.strictEqual(l.kind, 'fill'); assert.strictEqual(l.name, 'Сталь'); assert.strictEqual(l.opacity, 0.8); assert.strictEqual(l.heightMode, 'replace');
  assert.deepStrictEqual(l.material, { color: [90, 90, 95], alpha: 255, height: 0.5, spec: 200, team: 0 });
  assert.strictEqual(l.channels.height, true); assert.strictEqual(l.channels.color, true);
  assert.strictEqual(l.mask.enabled, true); assert.strictEqual(l.mask.invert, true);
  assert.strictEqual(l.mask.paint.length, 64); assert.ok(l.mask.paint.every(v => v === 0));
  assert.deepStrictEqual(l.mask.gen, gen); assert.notStrictEqual(l.mask.gen, gen); assert.notStrictEqual(l.mask.gen.params, gen.params);
  const w = L.layerFromSpec({ kind: 'fill', mask: { gen } }, 8); assert.strictEqual(w.mask.paint, null); assert.strictEqual(w.mask.invert, false);
  assert.strictEqual(L.layerFromSpec({ kind: 'paint' }, 8).mask, null);
  const q = L.layerFromSpec({ kind: 'proc', proc: { type: 'scratches', seed: 2, params: {} } }, 8); assert.strictEqual(q.proc.type, 'scratches'); assert.strictEqual(q.proc.seed, 2); });
test('cloneLayer: deep copy with new id and name suffix', () => {
  const d = L.createDocument({ size: 8 }); const p = L.newLayer('paint'); L.addMask(p, 8); p.mask.gen = { type: 'noise', seed: 1, params: { a: [1] } };
  L.applyDab(d, p, 'paint', ones(2, 2), 4, 4, { material: MAT });
  const c = L.cloneLayer(p);
  assert.notStrictEqual(c.id, p.id); assert.strictEqual(c.name, 'Малювання (копія)');
  assert.deepStrictEqual(c.paint, p.paint); assert.notStrictEqual(c.paint.color.value, p.paint.color.value);
  assert.deepStrictEqual(c.mask.paint, p.mask.paint); assert.notStrictEqual(c.mask.paint, p.mask.paint);
  assert.notStrictEqual(c.mask.gen.params.a, p.mask.gen.params.a); assert.notStrictEqual(c.channels, p.channels);
  const f = L.newLayer('fill'), cf = L.cloneLayer(f); assert.notStrictEqual(cf.material.color, f.material.color); assert.deepStrictEqual(cf.material, f.material); });

// ---- Композиція: заливка ----
test('fill colour at opacity 0.5 is halfway; opacity 1 is the material', () => {
  const d = L.createDocument({ size: 4 });
  d.layers.push(L.newLayer('fill', { material: MAT, opacity: 0.5 })); L.composite(d);
  assert.deepStrictEqual(px(d, 1, 2), { c: [164, 84, 69, 255], h: 0.5, s: 90, t: 0 });
  d.layers[0].opacity = 1; L.composite(d);
  assert.deepStrictEqual(px(d, 3, 3), { c: [200, 40, 10, 255], h: 0.5, s: 180, t: 0 }); });
test('channel toggles are respected (alpha writes colour A)', () => {
  const d = L.createDocument({ size: 4 });
  d.layers.push(L.newLayer('fill', { material: MAT, channels: { color: false, spec: false, team: true, alpha: true } })); L.composite(d);
  assert.deepStrictEqual(px(d, 0, 0), { c: [128, 128, 128, 30], h: 0.5, s: 0, t: 255 });
  d.layers[0].channels = { color: false, alpha: false, height: true, spec: false, team: false }; L.composite(d);
  const p0 = px(d, 0, 0); assert.deepStrictEqual(p0.c, [128, 128, 128, 255]); assert.ok(Math.abs(p0.h - 0.9) < 1e-6); });
test('height add vs replace', () => {
  const d = L.createDocument({ size: 4 }); const ch = { color: false, spec: false, height: true };
  d.layers.push(L.newLayer('fill', { channels: ch, material: { height: 0.8 } })); L.composite(d);
  assert.ok(Math.abs(px(d, 0, 0).h - 0.8) < 1e-6, 'add: 0.5 + 0.3');
  d.layers.push(L.newLayer('fill', { channels: ch, material: { height: 0.6 }, opacity: 0.5 })); L.composite(d);
  assert.ok(Math.abs(px(d, 0, 0).h - 0.85) < 1e-6, 'add: 0.8 + 0.1·0.5');
  d.layers.push(L.newLayer('fill', { channels: ch, material: { height: 1 } })); L.composite(d);
  assert.strictEqual(px(d, 0, 0).h, 1, 'add обмежується 0..1');
  d.layers.push(L.newLayer('fill', { channels: ch, material: { height: 0.2 }, heightMode: 'replace', opacity: 0.5 })); L.composite(d);
  assert.ok(Math.abs(px(d, 0, 0).h - 0.6) < 1e-6, 'replace: lerp(1, 0.2, 0.5)'); });
test('invisible and zero-opacity layers are skipped; layers apply bottom to top', () => {
  const d = L.createDocument({ size: 4 });
  d.layers.push(L.newLayer('fill', { material: { color: [10, 20, 30] } }), L.newLayer('fill', { material: { color: [50, 60, 70] }, visible: false }), L.newLayer('fill', { material: { color: [1, 1, 1] }, opacity: 0 }));
  L.composite(d); assert.deepStrictEqual(px(d, 2, 2).c, [10, 20, 30, 255]);
  d.layers[1].visible = true; L.composite(d); assert.deepStrictEqual(px(d, 2, 2).c, [50, 60, 70, 255]); });

// ---- Пензель ----
test('paint dab: fresh pixel takes the material exactly; only layer∩brush channels are created', () => {
  const d = L.createDocument({ size: 16 }); const p = L.newLayer('paint'); d.layers.push(p);
  L.applyDab(d, p, 'paint', ones(3, 3), 8, 8, { material: MAT });                    // top-left = round(6.5) = 7 → 7..9
  assert.deepStrictEqual(Object.keys(p.paint).sort(), ['color', 'height', 'spec', 'team'], 'alpha вимкнено в шарі');
  assert.strictEqual(p.paint.color.value.length, 16 * 16 * 4); assert.strictEqual(p.paint.spec.cov.length, 256);
  L.composite(d);
  const c = px(d, 8, 8); assert.deepStrictEqual(c.c, [200, 40, 10, 255]); assert.strictEqual(c.s, 180); assert.strictEqual(c.t, 255);
  assert.ok(Math.abs(c.h - 0.9) < 0.003, 'висота зберігається як 0..255');
  assert.deepStrictEqual(px(d, 7, 7).c, [200, 40, 10, 255]); assert.deepStrictEqual(px(d, 9, 9).c, [200, 40, 10, 255]);
  assert.deepStrictEqual(px(d, 6, 6), { c: [128, 128, 128, 255], h: 0.5, s: 0, t: 0 }); assert.deepStrictEqual(px(d, 10, 8).c, [128, 128, 128, 255]);
  const q = L.newLayer('paint'); L.applyDab(d, q, 'paint', ones(3, 3), 8, 8, { material: MAT, channels: { color: true, alpha: true } });
  assert.deepStrictEqual(Object.keys(q.paint), ['color']);
  assert.throws(() => L.applyDab(d, q, 'paint', ones(3, 3), 8, 8, {}), /матеріал/);
  assert.throws(() => L.applyDab(d, L.newLayer('fill'), 'paint', ones(3, 3), 8, 8, { material: MAT }), /Малювання/); });
test('paint dab: flow, "over" accumulation, erase', () => {
  const d = L.createDocument({ size: 8 }); const p = L.newLayer('paint', { channels: { color: false, height: false, team: false } }); d.layers.push(p);
  L.applyDab(d, p, 'paint', ones(1, 1), 3, 3, { material: MAT, flow: 0.5 });       // 1×1: top-left = round(2.5) = 3
  const i = 3 * 8 + 3;
  assert.strictEqual(p.paint.spec.cov[i], 128); assert.strictEqual(p.paint.spec.value[i], 180, 'свіжий піксель бере значення точно');
  L.applyDab(d, p, 'paint', ones(1, 1), 3, 3, { material: { ...MAT, spec: 0 }, flow: 0.5 });
  assert.strictEqual(p.paint.spec.cov[i], 192); assert.ok(Math.abs(p.paint.spec.value[i] - 180 * (1 - 0.5 / (192 / 255))) <= 1);
  L.composite(d); assert.ok(Math.abs(d.maps.spec[i] - p.paint.spec.value[i] * 192 / 255) <= 1);
  L.applyDab(d, p, 'paint', ones(1, 1), 3, 3, { erase: true, flow: 0.5 }); assert.strictEqual(p.paint.spec.cov[i], 96);
  L.applyDab(d, p, 'paint', ones(1, 1), 3, 3, { erase: true }); assert.strictEqual(p.paint.spec.cov[i], 0);
  L.composite(d); isBase(d, 'після гумки'); });
test('dab wraps across edges and a dab bigger than the texture hits each texel once', () => {
  const d = L.createDocument({ size: 16 }); const p = L.newLayer('paint'); d.layers.push(p);
  L.applyDab(d, p, 'paint', ones(4, 4), 0, 0, { material: MAT, channels: { team: true } });   // top-left -2 → 14,15,0,1
  const cov = p.paint.team.cov, at = (x, y) => cov[y * 16 + x];
  for (const [x, y] of [[14, 14], [15, 15], [0, 0], [1, 1], [15, 0], [0, 15], [14, 1]]) assert.strictEqual(at(x, y), 255, x + ',' + y);
  for (const [x, y] of [[2, 2], [13, 13], [2, 0], [0, 13]]) assert.strictEqual(at(x, y), 0, x + ',' + y);
  const q = L.newLayer('paint');
  L.applyDab(d, q, 'paint', ones(40, 37), 5, 5, { material: MAT, channels: { spec: true }, flow: 0.5 });
  assert.ok(q.paint.spec.cov.every(v => v === 128), 'кожен тексель рівно один раз'); });
test('fillRegion paints by region weights; whole-layer fill', () => {
  const d = L.createDocument({ size: 4 }); const p = L.newLayer('paint'); d.layers.push(p);
  const region = new Uint8ClampedArray(16); region.fill(255, 0, 8); region[8] = 51;
  L.fillRegion(d, p, 'paint', region, { material: MAT, channels: { spec: true } });
  assert.strictEqual(p.paint.spec.cov[0], 255); assert.strictEqual(p.paint.spec.cov[8], 51); assert.strictEqual(p.paint.spec.cov[9], 0);
  assert.strictEqual(p.paint.spec.value[8], 180);
  L.fillRegion(d, p, 'paint', new Uint8ClampedArray(16).fill(255), { erase: true, flow: 1 }); assert.ok(p.paint.spec.cov.every(v => v === 0));
  assert.throws(() => L.fillRegion(d, p, 'paint', new Uint8ClampedArray(3), { material: MAT }), /област/); });

// ---- Маски ----
test('black mask paint hides a fill; painting the mask reveals; erase hides again', () => {
  const d = L.createDocument({ size: 16 }); const f = L.newLayer('fill', { material: MAT }); d.layers.push(f);
  assert.throws(() => L.applyDab(d, f, 'mask', ones(2, 2), 4, 4, {}), /маск/);
  L.addMask(f, 16, 'black'); L.composite(d); isBase(d, 'чорна маска');
  L.applyDab(d, f, 'mask', ones(4, 4), 4, 4, {});                                    // 2..5
  L.composite(d);
  assert.deepStrictEqual(px(d, 3, 3).c, [200, 40, 10, 255]); assert.deepStrictEqual(px(d, 6, 6).c, [128, 128, 128, 255]);
  L.applyDab(d, f, 'mask', ones(4, 4), 4, 4, { erase: true, flow: 0.5 }); assert.strictEqual(f.mask.paint[3 * 16 + 3], 128);
  L.composite(d); assert.deepStrictEqual(px(d, 3, 3).c, [164, 84, 69, 255]);
  f.mask.enabled = false; L.composite(d); assert.deepStrictEqual(px(d, 6, 6).c, [200, 40, 10, 255], 'вимкнена маска = 1');
  const w = L.newLayer('fill'); w.mask = { enabled: true, invert: false, paint: null, gen: null };
  L.applyDab(d, w, 'mask', ones(1, 1), 0, 0, { erase: true }); assert.strictEqual(w.mask.paint.length, 256); assert.strictEqual(w.mask.paint[0], 0); assert.strictEqual(w.mask.paint[1], 255); });
test('mask invert', () => {
  const d = L.createDocument({ size: 4 }); const f = L.newLayer('fill', { material: MAT }); d.layers.push(f);
  L.addMask(f, 4, 'black'); f.mask.invert = true; L.composite(d); assert.deepStrictEqual(px(d, 1, 1).c, [200, 40, 10, 255]);
  f.mask.paint[5] = 255; L.composite(d); assert.deepStrictEqual(px(d, 1, 1).c, [128, 128, 128, 255]); assert.deepStrictEqual(px(d, 2, 1).c, [200, 40, 10, 255]); });
test('mask gen: generated from the composite below, multiplied by mask paint, inverted', () => {
  const mg = stubMaskgen(); L.setMaskgen(mg);
  const d = L.createDocument({ size: 8 });
  d.layers.push(L.newLayer('fill', { material: { color: [10, 10, 10], spec: 0 } }));
  const f = L.newLayer('fill', { material: MAT }); f.mask = { enabled: true, invert: false, paint: null, gen: { type: 'edges', seed: 1, params: {} } }; d.layers.push(f);
  L.composite(d);
  assert.strictEqual(mg.calls.length, 1); assert.strictEqual(mg.calls[0].color0, 10, 'maskgen бачить результат нижніх шарів'); assert.strictEqual(mg.calls[0].gen, f.mask.gen);
  assert.deepStrictEqual(px(d, 1, 1).c, [200, 40, 10, 255]); assert.deepStrictEqual(px(d, 6, 1).c, [10, 10, 10, 255]);
  L.addMask(f, 8, 'white'); f.mask.gen = { type: 'edges', seed: 1, params: { value: 0.5 } }; f.mask.paint[1 * 8 + 2] = 0;
  L.composite(d);
  assert.deepStrictEqual(px(d, 1, 1).c, [105, 25, 10, 255], 'gen 0.5'); assert.deepStrictEqual(px(d, 2, 1).c, [10, 10, 10, 255], 'paint 0 × gen');
  f.mask.invert = true; L.composite(d);
  assert.deepStrictEqual(px(d, 1, 1).c, [105, 25, 10, 255]); assert.deepStrictEqual(px(d, 2, 1).c, [200, 40, 10, 255]); assert.deepStrictEqual(px(d, 6, 1).c, [200, 40, 10, 255]); });
test('mask gen without maskgen or with a wrong-size result is an error', () => {
  const d = L.createDocument({ size: 4 }); const f = L.newLayer('fill'); f.mask = { enabled: true, invert: false, paint: null, gen: { type: 'edges', seed: 1, params: {} } }; d.layers.push(f);
  L.setMaskgen({}); assert.throws(() => L.composite(d), /maskgen/);
  L.setMaskgen({ generate: () => new Float32Array(3) }); assert.throws(() => L.composite(d), /маск/);
  f.mask.enabled = false; L.composite(d); });

// ---- Генератор ----
test('proc layer: all channels = generator; channels limited to colour leave height untouched; opacity lerps', () => {
  const size = 32, ref = L.createDocument({ size }); ref.layers.push(L.newLayer('proc', { type: 'rust', seed: 4, params: { amount: 0.7 } })); L.composite(ref);
  const direct = L.createDocument({ size }).maps; g.apply({ type: 'rust', seed: 4, params: { amount: 0.7 } }, direct, size, {});
  sameMaps(ref.maps, direct, 'proc = generators.apply');
  assert.ok(direct.height.some(v => v !== 0.5), 'іржа змінює висоту');
  const d = L.createDocument({ size }); d.layers.push(L.newLayer('proc', { type: 'rust', seed: 4, params: { amount: 0.7 }, channels: { alpha: false, height: false, spec: false, team: false } })); L.composite(d);
  assert.deepStrictEqual(d.maps.color, direct.color); assert.ok(d.maps.height.every(v => v === 0.5)); assert.ok(d.maps.spec.every(v => v === 0));
  d.layers[0].channels.height = true; d.layers[0].opacity = 0.5; L.composite(d);
  for (let i = 0; i < size * size; i++) assert.ok(Math.abs(d.maps.height[i] - (0.5 + (direct.height[i] - 0.5) * 0.5)) < 1e-6);
  d.layers[0].mask = { enabled: true, invert: false, paint: new Uint8ClampedArray(size * size), gen: null }; L.composite(d); isBase(d, 'маска 0'); });
test('proc image layer uses doc.images (addImage resamples to the doc size)', () => {
  const d = L.createDocument({ size: 8 });
  const id = L.addImage(d, { width: 2, height: 3, data: new Uint8ClampedArray(24).fill(60) });
  assert.strictEqual(d.images[id].length, 256); assert.strictEqual(d.images[id][0], 60);
  assert.throws(() => L.addImage(d, { width: 2, height: 2, data: new Uint8ClampedArray(3) }), /Розмір буфера/);
  d.layers.push(L.newLayer('proc', { type: 'image', params: { imageId: id, target: 'color' } })); L.composite(d);
  assert.deepStrictEqual(px(d, 4, 4).c, [60, 60, 60, 255]); });

// ---- Швидке перемальовування, undo, розмір ----
function richDoc(size) {
  L.setMaskgen(stubMaskgen());
  const d = L.createDocument({ size });
  d.layers.push(L.newLayer('proc', { type: 'base', seed: 1 }));
  const f = L.newLayer('fill', { material: MAT, opacity: 0.7 }); f.mask = { enabled: true, invert: true, paint: null, gen: { type: 'edges', seed: 2, params: { value: 0.6 } } }; d.layers.push(f);
  const p = L.newLayer('paint'); d.layers.push(p);
  L.applyDab(d, p, 'paint', ones(5, 5), 3, 3, { material: MAT, flow: 0.7 });
  const h = L.newLayer('fill', { channels: { color: false, spec: false, height: true }, material: { height: 0.2 } }); L.addMask(h, size, 'black'); d.layers.push(h);
  L.applyDab(d, h, 'mask', ones(6, 6), size / 2, size / 2, { flow: 0.8 });
  d.layers.push(L.newLayer('proc', { type: 'scratches', seed: 3, channels: { team: false } }));
  return d;
}
test('snapshotBelow + compositeFrom equals a full composite; snapshotBelow does not touch doc.maps', () => {
  const d = richDoc(32); L.composite(d); const full = L.cloneMaps(d.maps);
  assert.notStrictEqual(full.color, d.maps.color); sameMaps(full, d.maps);
  for (let k = 0; k <= d.layers.length; k++) {
    d.maps.color.fill(1);
    const below = L.snapshotBelow(d, k);
    assert.ok(d.maps.color.every(v => v === 1), 'doc.maps не змінено');
    assert.notStrictEqual(below.color, d.maps.color);
    const keep = L.cloneMaps(below);
    L.compositeFrom(d, k, below); sameMaps(d.maps, full, 'k=' + k);
    sameMaps(below, keep, 'below не змінено'); }
  const below0 = L.snapshotBelow(d, 0); isBase({ size: 32, maps: below0 }); });
test('snapshot / restore for paint and mask', () => {
  const d = L.createDocument({ size: 8 }); const p = L.newLayer('paint');
  L.applyDab(d, p, 'paint', ones(2, 2), 2, 2, { material: MAT, channels: { color: true } });
  const s1 = L.snapshot(p, 'paint'); const before = p.paint.color.value.slice();
  L.applyDab(d, p, 'paint', ones(3, 3), 2, 2, { material: { ...MAT, color: [0, 0, 0] } });
  assert.deepStrictEqual(Object.keys(p.paint).sort(), ['color', 'height', 'spec', 'team']);
  L.restore(p, s1); assert.deepStrictEqual(Object.keys(p.paint), ['color']); assert.deepStrictEqual(p.paint.color.value, before);
  p.paint.color.value[0] = 7; L.restore(p, s1); assert.strictEqual(p.paint.color.value[0], before[0], 'знімок не спільний із шаром');
  L.addMask(p, 8, 'black'); const s2 = L.snapshot(p, 'mask');
  L.applyDab(d, p, 'mask', ones(2, 2), 4, 4, {}); assert.ok(p.mask.paint.some(v => v > 0));
  L.restore(p, s2); assert.ok(p.mask.paint.every(v => v === 0));
  const q = L.newLayer('fill'); const s3 = L.snapshot(q, 'mask'); L.addMask(q, 8); L.restore(q, s3); assert.strictEqual(q.mask, null);
  assert.throws(() => L.snapshot(q, 'other'), /ціль/); });
test('resize: paint and mask nearest-neighbour, images/nrmImport resampled, maps reallocated', () => {
  const d = L.createDocument({ size: 16 }); const p = L.newLayer('paint'); L.addMask(p, 16, 'white'); d.layers.push(p);
  L.applyDab(d, p, 'paint', ones(2, 2), 5, 5, { material: MAT, channels: { team: true } });   // 4..5
  p.mask.paint[0] = 0;
  d.nrmImport = new Uint8ClampedArray(16 * 16 * 4).fill(200); const id = L.addImage(d, { width: 16, height: 16, data: new Uint8ClampedArray(1024).fill(9) });
  L.resize(d, 32); L.composite(d);
  assert.strictEqual(d.size, 32); assert.strictEqual(d.maps.team.length, 1024); assert.strictEqual(d.nrmImport.length, 4096); assert.strictEqual(d.images[id].length, 4096);
  assert.strictEqual(p.paint.team.cov.length, 1024); assert.strictEqual(p.mask.paint.length, 1024);
  for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
    const inDab = x >= 8 && x <= 11 && y >= 8 && y <= 11;
    assert.strictEqual(d.maps.team[y * 32 + x], inDab ? 255 : 0, x + ',' + y); }
  assert.deepStrictEqual([...p.mask.paint.slice(0, 3)], [0, 0, 255]); assert.strictEqual(p.mask.paint[32], 0);
  assert.throws(() => L.resize(d, 20), /степінь двійки/); assert.strictEqual(d.size, 32); });

// ---- Швидкодія ----
test('performance: 1024² composite with 8 layers stays well under 0.5 s', () => {
  const size = 1024, n = size * size;
  const cached = new Float32Array(n); for (let i = 0; i < n; i++) cached[i] = (i % 1024) / 1024;
  L.setMaskgen({ generate: () => cached.slice() });                           // реальний maskgen має власну вартість
  const d = L.createDocument({ size });
  for (let k = 0; k < 5; k++) {
    const f = L.newLayer('fill', { material: { color: [k * 40, 100, 50], height: 0.6 }, channels: { height: true, team: k === 2 }, opacity: 0.9 });
    f.mask = { enabled: true, invert: k % 2 === 1, paint: k === 0 ? new Uint8ClampedArray(n).fill(200) : null, gen: { type: 'edges', seed: k, params: {} } };
    d.layers.push(f);
  }
  const p = L.newLayer('paint'); L.fillRegion(d, p, 'paint', new Uint8ClampedArray(n).fill(180), { material: MAT }); d.layers.push(p);
  d.layers.push(L.newLayer('fill', { material: MAT, opacity: 0.3 }));
  d.layers.push(L.newLayer('proc', { type: 'panels', seed: 9 }));
  L.composite(d);                                                            // прогрів
  const t0 = performance.now(); L.composite(d); const ms = performance.now() - t0;
  console.log('composite 1024² × 8 layers: ' + ms.toFixed(1) + ' ms');
  assert.ok(ms < 500, ms + ' ms'); });
