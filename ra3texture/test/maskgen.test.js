const test = require('node:test'); const assert = require('node:assert');
const mg = require('../js/maskgen.js');
const util = require('../js/util.js');

// ---- Допоміжне ----
const TYPES = ['edges', 'cavity', 'ao', 'height', 'noise', 'gradient', 'team', 'color'];

function flatMaps(s) {
  const n = s * s, color = new Uint8ClampedArray(n * 4).fill(128);
  for (let i = 3; i < n * 4; i += 4) color[i] = 255;
  return { color, height: new Float32Array(n).fill(0.5), spec: new Uint8ClampedArray(n), team: new Uint8ClampedArray(n) };
}

// Карта з усім потроху: шви, ребро, заклепки, шум висоти; два кольори; смуга кольору гравця.
function busyMaps(s, seed) {
  const m = flatMaps(s), rnd = util.mulberry32(seed || 7), h = m.height;
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
    const i = y * s + x;
    let v = 0.5 + 0.02 * (rnd() - 0.5);
    if (x % 16 === 3 || y % 21 === 5) v = 0.28;
    if (x % 23 === 11) v = 0.72;
    h[i] = v;
    const o = i * 4;
    if (x < s / 2) m.color.set([200, 40, 40], o); else m.color.set([40, 40, 200], o);
    m.team[i] = y >= 10 && y < 20 ? 255 : (y === 20 ? 128 : 0);
    m.spec[i] = (x * 3) & 255;
  }
  for (let k = 0; k < 12; k++) {
    const cx = rnd() * s, cy = rnd() * s;
    for (let dy = -3; dy <= 3; dy++) for (let dx = -3; dx <= 3; dx++) {
      if (dx * dx + dy * dy > 6) continue;
      const x = ((Math.round(cx) + dx) % s + s) % s, y = ((Math.round(cy) + dy) % s + s) % s;
      h[y * s + x] = 0.66;
    }
  }
  return m;
}

// Кругове зсунення всіх карт на (dx, dy).
function shiftMaps(m, s, dx, dy) {
  const out = flatMaps(s);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
    const i = y * s + x, j = ((y + dy) % s) * s + ((x + dx) % s);
    out.height[j] = m.height[i]; out.spec[j] = m.spec[i]; out.team[j] = m.team[i];
    for (let c = 0; c < 4; c++) out.color[j * 4 + c] = m.color[i * 4 + c];
  }
  return out;
}
function shiftArr(a, s, dx, dy) {
  const out = new Float32Array(a.length);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) out[((y + dy) % s) * s + ((x + dx) % s)] = a[y * s + x];
  return out;
}
const gen = (type, seed, params) => ({ type, seed, params: params || {} });
const maxDiff = (a, b) => { let d = 0; for (let i = 0; i < a.length; i++) d = Math.max(d, Math.abs(a[i] - b[i])); return d; };
const mean = a => a.reduce((p, v) => p + v, 0) / a.length;
// Середня різниця сусідніх стовпців (axis 0) чи рядків (axis 1): пара через край проти найбільшої внутрішньої.
function tileStats(a, s, axis) {
  const at = (p, q) => axis === 0 ? a[q * s + p] : a[p * s + q];
  const pair = (p0, p1) => { let d = 0; for (let q = 0; q < s; q++) d += Math.abs(at(p0, q) - at(p1, q)); return d / s; };
  let inner = 0;
  for (let p = 0; p < s - 1; p++) inner = Math.max(inner, pair(p, p + 1));
  return { wrap: pair(s - 1, 0), inner };
}

// ---- Каталог ----

test('catalog lists all mask generator types with valid param metadata', () => {
  assert.deepStrictEqual(mg.catalog.map(c => c.type).sort(), [...TYPES].sort());
  for (const c of mg.catalog) {
    assert.ok(typeof c.label === 'string' && c.label.length > 0, c.type);
    assert.ok(Array.isArray(c.params));
    for (const p of c.params) {
      assert.ok(typeof p.key === 'string' && typeof p.label === 'string', c.type + '.' + p.key);
      assert.ok(['range', 'color', 'select', 'checkbox'].includes(p.kind), c.type + '.' + p.key);
      if (p.kind === 'range') assert.ok(p.min <= p.default && p.default <= p.max && p.step > 0, c.type + '.' + p.key);
      if (p.kind === 'color') assert.match(p.default, /^#[0-9a-f]{6}$/i);
      if (p.kind === 'select') assert.ok(p.options.some(o => o.value === p.default), c.type + '.' + p.key);
    }
  }
});

test('defaultGen returns catalog defaults', () => {
  const g = mg.defaultGen('edges', 9);
  assert.strictEqual(g.type, 'edges'); assert.strictEqual(g.seed, 9);
  for (const p of mg.catalog.find(c => c.type === 'edges').params) assert.strictEqual(g.params[p.key], p.default);
});

// ---- Загальні властивості ----

for (const type of TYPES) {
  test(`${type}: Float32Array(n) in [0,1], deterministic per seed`, () => {
    const s = 64, maps = busyMaps(s, 3);
    const params = type === 'cavity' ? { breakup: 0.5 } : {};
    const a = mg.generate(gen(type, 11, params), maps, s), b = mg.generate(gen(type, 11, params), maps, s);
    assert.ok(a instanceof Float32Array); assert.strictEqual(a.length, s * s);
    for (const v of a) assert.ok(v >= 0 && v <= 1 && Number.isFinite(v), 'value ' + v);
    assert.deepStrictEqual(Array.from(a), Array.from(b));
    if (type === 'edges' || type === 'noise' || type === 'cavity') {
      const c = mg.generate(gen(type, 12, params), maps, s);
      assert.ok(maxDiff(a, c) > 0.01, 'seed must change the noise');
    }
  });
}

test('generate does not modify the input maps', () => {
  const s = 32, maps = busyMaps(s, 4), copy = busyMaps(s, 4);
  for (const type of TYPES) mg.generate(gen(type, 1, { breakup: 0.5, grow: 8, blur: 8 }), maps, s);
  assert.deepStrictEqual(Array.from(maps.height), Array.from(copy.height));
  assert.deepStrictEqual(Array.from(maps.color), Array.from(copy.color));
  assert.deepStrictEqual(Array.from(maps.team), Array.from(copy.team));
});

test('unknown type throws a Ukrainian error', () => {
  assert.throws(() => mg.generate(gen('zzz', 1), flatMaps(4), 4), /Невідомий генератор маски: zzz/);
  assert.throws(() => mg.generate(null, flatMaps(4), 4), /Невідомий генератор маски/);
});

test('maps of the wrong size are rejected', () => {
  assert.throws(() => mg.generate(gen('ao', 1), flatMaps(8), 16), /розмір/i);
});

test('params are clamped to the catalog range', () => {
  const s = 32, maps = flatMaps(s);
  const all = mg.generate(gen('noise', 1, { coverage: 5 }), maps, s);
  assert.ok(all.every(v => v === 1));
  const none = mg.generate(gen('noise', 1, { coverage: -3 }), maps, s);
  assert.ok(none.every(v => v === 0));
  const huge = mg.generate(gen('edges', 1, { width: 1e9, breakup: 'x' }), busyMaps(s), s);
  assert.ok(huge.every(v => v >= 0 && v <= 1));
});

// ---- Тайлінг ----

// Без шуму маска зсунутої карти — це зсунута маска: усі вибірки загортаються через край.
for (const [type, params] of [['edges', { breakup: 0 }], ['cavity', { breakup: 0 }], ['cavity', { breakup: 0, spread: 24 }], ['ao', { radius: 40 }], ['height', {}],
  ['team', { grow: 16, blur: 12 }], ['color', { color: '#c82828', tolerance: 0.2, softness: 0.1 }]]) {
  test(`${type} ${JSON.stringify(params)}: shifting the maps shifts the mask (wrap-around sampling)`, () => {
    const s = 64, maps = busyMaps(s, 5);
    const a = mg.generate(gen(type, 1, params), maps, s);
    for (const [dx, dy] of [[1, 0], [0, 1], [37, 50]]) {
      const b = mg.generate(gen(type, 1, params), shiftMaps(maps, s, dx, dy), s);
      assert.ok(maxDiff(shiftArr(a, s, dx, dy), b) < 1e-4, `${type} shift ${dx},${dy}: ${maxDiff(shiftArr(a, s, dx, dy), b)}`);
    }
  });
}

for (const [type, params] of [['noise', {}], ['noise', { stretch: 1, scale: 0.5 }], ['edges', { breakup: 1 }], ['cavity', { breakup: 1 }]]) {
  test(`${type} ${JSON.stringify(params)}: noise tiles across both edges`, () => {
    const s = 128, maps = type === 'noise' ? flatMaps(s) : busyMaps(s, 6);
    const a = mg.generate(gen(type, 21, params), maps, s);
    for (const axis of [0, 1]) {
      const st = tileStats(a, s, axis);
      assert.ok(st.wrap <= st.inner * 1.25 + 1e-6, `${type} axis ${axis}: wrap ${st.wrap} vs inner ${st.inner}`);
    }
  });
}

// ---- Окремі генератори ----

test('edges: a ridge across the texture edge gives a symmetric, interior-equal response', () => {
  const s = 64, P = { width: 24, breakup: 0, contrast: 0.6 }; // ширина 24 px на 512 → 3 px на 64
  const wrapM = flatMaps(s), intM = flatMaps(s);
  for (let y = 0; y < s; y++) {
    wrapM.height[y * s + s - 1] = 0.75; wrapM.height[y * s] = 0.75;         // ребро на стику n−1 | 0
    intM.height[y * s + 31] = 0.75; intM.height[y * s + 32] = 0.75;         // те саме ребро всередині
  }
  const a = mg.generate(gen('edges', 1, P), wrapM, s), b = mg.generate(gen('edges', 1, P), intM, s);
  for (let y = 0; y < s; y += 7) {
    for (let j = 0; j < 12; j++) {
      assert.ok(Math.abs(a[y * s + j] - a[y * s + s - 1 - j]) < 1e-5, `mirror ${j}`);
      assert.ok(Math.abs(a[y * s + j] - b[y * s + 32 + j]) < 1e-5, `interior ${j}`);
    }
    assert.ok(a[y * s] > 0.8 && a[y * s + s - 1] > 0.8, 'high on the ridge: ' + a[y * s]);
    assert.strictEqual(a[y * s + 20], 0, 'zero on flat');
  }
});

test('edges: high on ridges and rivets, zero on flat and in grooves', () => {
  const s = 128, m = flatMaps(s), P = { width: 12, breakup: 0 };
  for (let y = 0; y < s; y++) { m.height[y * s + 40] = 0.75; m.height[y * s + 90] = 0.25; m.height[y * s + 91] = 0.25; }
  const e = mg.generate(gen('edges', 3, P), m, s);
  const row = 50 * s;
  assert.ok(e[row + 40] > 0.8, 'ridge ' + e[row + 40]);
  assert.strictEqual(e[row + 10], 0);
  assert.strictEqual(e[row + 90], 0, 'groove floor is not an edge');
  assert.ok(e[row + 89] > 0.3 || e[row + 88] > 0.3, 'lip next to the groove wears');
  assert.ok(mg.generate(gen('edges', 3, {}), flatMaps(s), s).every(v => v === 0), 'flat map → no edges');
});

test('edges: breakup removes part of the edge, more breakup removes more', () => {
  const s = 128, m = busyMaps(s, 2);
  const sum = b => mg.generate(gen('edges', 5, { breakup: b }), m, s).reduce((p, v) => p + v, 0);
  const s0 = sum(0), s5 = sum(0.5), s1 = sum(1);
  assert.ok(s0 > s5 && s5 > s1, `${s0} > ${s5} > ${s1}`);
  assert.ok(s1 > 0);
});

test('cavity: high in a groove, zero on flat and on ridge tops, symmetric across the wrap', () => {
  const s = 64, m = flatMaps(s), P = { width: 24, breakup: 0 };
  for (let x = 0; x < s; x++) { m.height[(s - 1) * s + x] = 0.25; m.height[x] = 0.25; m.height[30 * s + x] = 0.8; }
  const c = mg.generate(gen('cavity', 1, P), m, s);
  for (let x = 0; x < s; x += 5) {
    assert.ok(c[x] > 0.8 && c[(s - 1) * s + x] > 0.8, 'groove ' + c[x]);
    for (let j = 0; j < 10; j++) assert.ok(Math.abs(c[j * s + x] - c[(s - 1 - j) * s + x]) < 1e-5, 'mirror ' + j);
    assert.strictEqual(c[15 * s + x], 0, 'flat');
    assert.strictEqual(c[30 * s + x], 0, 'ridge top');
  }
  assert.ok(c[28 * s] > 0, 'foot of the ridge collects some dirt');
});

test('cavity: spread carries the mask out of the groove onto the panel, fading with distance', () => {
  const s = 128, m = flatMaps(s);
  for (let x = 0; x < s; x++) { m.height[60 * s + x] = 0.25; m.height[61 * s + x] = 0.25; }
  const P = { width: 12, breakup: 0, contrast: 0 };
  const a = mg.generate(gen('cavity', 1, P), m, s), b = mg.generate(gen('cavity', 1, { ...P, spread: 32 }), m, s);
  const col = (arr, y) => arr[y * s + 7];
  assert.strictEqual(col(a, 56), 0, 'no spread: panel stays clean');
  assert.ok(col(b, 60) >= col(a, 60) && col(b, 60) > 0.9, 'groove stays full');
  assert.ok(col(b, 58) > 0.9, 'full right beside the groove: ' + col(b, 58));
  for (let y = 59; y > 45; y--) assert.ok(col(b, y - 1) <= col(b, y) + 1e-6, 'fades monotonically, row ' + y);
  assert.ok([50, 51, 52, 53, 54].some(y => col(b, y) > 0.05 && col(b, y) < 0.95), 'soft fade-out');
  assert.strictEqual(col(b, 50), 0, 'stops at the spread distance (8 px)');
  assert.strictEqual(col(b, 40), 0, 'far panel clean');
  assert.ok(Math.abs(col(b, 57) - col(b, 64)) < 1e-5, 'symmetric about the groove');
});

test('ao: darker in a pit than on the plateau, zero on a flat map', () => {
  const s = 128, m = flatMaps(s);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) {
    const dx = x - 64, dy = y - 64;
    if (dx * dx + dy * dy < 100) m.height[y * s + x] = 0.3;
  }
  const P = { radius: 48 };  // 48 px на 512 → 12 px на 128: стінки ямки радіусом 10 px затіняють її центр
  const a = mg.generate(gen('ao', 1, P), m, s);
  assert.ok(a[64 * s + 64] > 0.5, 'pit centre ' + a[64 * s + 64]);
  assert.ok(a[64 * s + 72] >= a[64 * s + 64], 'near the wall at least as dark as the centre');
  assert.strictEqual(a[64 * s + 80], 0, 'rim of the pit is not occluded');
  assert.strictEqual(a[10 * s + 10], 0, 'far plateau');
  assert.ok(mg.generate(gen('ao', 1, {}), flatMaps(s), s).every(v => v === 0));
  const small = mg.generate(gen('ao', 1, { radius: 4 }), m, s);
  assert.ok(small[64 * s + 64] < a[64 * s + 64], 'a small radius sees no walls from the pit centre');
  const weak = mg.generate(gen('ao', 1, { ...P, strength: 0.1 }), m, s), strong = mg.generate(gen('ao', 1, { ...P, strength: 1 }), m, s);
  assert.ok(strong[64 * s + 64] > weak[64 * s + 64]);
});

test('height: selects the range min..max with soft or hard edges', () => {
  const s = 16, m = flatMaps(s);
  for (let i = 0; i < s * s; i++) m.height[i] = i / (s * s - 1);
  const hard = mg.generate(gen('height', 1, { min: 0.3, max: 0.6, softness: 0 }), m, s);
  for (let i = 0; i < s * s; i++) assert.strictEqual(hard[i], m.height[i] >= 0.3 && m.height[i] <= 0.6 ? 1 : 0, 'i ' + i);
  const soft = mg.generate(gen('height', 1, { min: 0.3, max: 0.6, softness: 0.1 }), m, s);
  assert.strictEqual(soft[0], 0); assert.strictEqual(soft[s * s - 1], 0);
  const mid = Math.round(0.45 * (s * s - 1)); assert.strictEqual(soft[mid], 1);
  assert.ok(soft.some(v => v > 0 && v < 1));
  const full = mg.generate(gen('height', 1, { min: 0, max: 1, softness: 0.2 }), m, s);
  assert.ok(full.every(v => v === 1), 'min 0 / max 1 include the ends');
  const swapped = mg.generate(gen('height', 1, { min: 0.6, max: 0.3, softness: 0 }), m, s);
  assert.deepStrictEqual(Array.from(swapped), Array.from(hard));
});

test('noise: coverage controls the masked share; contrast hardens it', () => {
  const s = 128, m = flatMaps(s);
  for (const cov of [0.2, 0.5, 0.8]) {
    const a = mg.generate(gen('noise', 4, { coverage: cov }), m, s);
    assert.ok(Math.abs(mean(a) - cov) < 0.08, `coverage ${cov}: mean ${mean(a)}`);
  }
  const hard = mg.generate(gen('noise', 4, { coverage: 0.5, contrast: 1 }), m, s);
  const soft = mg.generate(gen('noise', 4, { coverage: 0.5, contrast: 0 }), m, s);
  const mids = a => a.filter(v => v > 0.05 && v < 0.95).length;
  assert.ok(mids(hard) < mids(soft) / 4, `${mids(hard)} vs ${mids(soft)}`);
});

test('noise: stretch makes vertical streaks (smoother along Y than along X)', () => {
  const s = 128, a = mg.generate(gen('noise', 8, { stretch: 1, contrast: 0 }), flatMaps(s), s);
  let dx = 0, dy = 0;
  for (let y = 0; y < s - 1; y++) for (let x = 0; x < s - 1; x++) {
    dx += Math.abs(a[y * s + x + 1] - a[y * s + x]); dy += Math.abs(a[(y + 1) * s + x] - a[y * s + x]);
  }
  assert.ok(dx > 2 * dy, `dx ${dx} dy ${dy}`);
});

test('gradient: each direction is 1 at its side and falls off', () => {
  const s = 64, m = flatMaps(s), P = { offset: 0.5, softness: 0.4 };
  const at = (a, x, y) => a[y * s + x];
  const bottom = mg.generate(gen('gradient', 1, { ...P, direction: 'bottom' }), m, s);
  const top = mg.generate(gen('gradient', 1, { ...P, direction: 'top' }), m, s);
  const left = mg.generate(gen('gradient', 1, { ...P, direction: 'left' }), m, s);
  const right = mg.generate(gen('gradient', 1, { ...P, direction: 'right' }), m, s);
  assert.strictEqual(at(bottom, 5, s - 1), 1); assert.strictEqual(at(bottom, 5, 0), 0);
  assert.strictEqual(at(top, 5, 0), 1); assert.strictEqual(at(top, 5, s - 1), 0);
  assert.strictEqual(at(left, 0, 9), 1); assert.strictEqual(at(left, s - 1, 9), 0);
  assert.strictEqual(at(right, s - 1, 9), 1); assert.strictEqual(at(right, 0, 9), 0);
  for (let y = 1; y < s; y++) assert.ok(at(bottom, 3, y) >= at(bottom, 3, y - 1), 'monotonic');
  for (let x = 0; x < s; x++) assert.strictEqual(at(bottom, x, 40), at(bottom, 0, 40), 'constant along a row');
  const hard = mg.generate(gen('gradient', 1, { direction: 'bottom', offset: 0.25, softness: 0 }), m, s);
  for (let y = 0; y < s; y++) assert.strictEqual(at(hard, 0, y), y >= s * 0.75 ? 1 : 0, 'row ' + y);
});

test('team: mirrors the team map; grow widens it, blur softens it', () => {
  const s = 64, m = flatMaps(s), rnd = util.mulberry32(3);
  for (let i = 0; i < s * s; i++) m.team[i] = Math.floor(rnd() * 256);
  const a = mg.generate(gen('team', 1, {}), m, s);
  for (let i = 0; i < s * s; i++) assert.ok(Math.abs(a[i] - m.team[i] / 255) < 1e-6);
  const d = flatMaps(s); d.team[10 * s + 10] = 255;
  const g = mg.generate(gen('team', 1, { grow: 16 }), d, s); // 16 px на 512 → 2 px на 64
  assert.strictEqual(g[12 * s + 12], 1); assert.strictEqual(g[10 * s + 8], 1); assert.strictEqual(g[13 * s + 10], 0);
  const b = mg.generate(gen('team', 1, { blur: 16 }), d, s);
  assert.ok(b[10 * s + 10] < 1 && b[10 * s + 10] > 0 && b[10 * s + 11] > 0);
  const e = flatMaps(s); e.team[0] = 255;  // у кутку: розширення загортається на протилежні краї
  const w = mg.generate(gen('team', 1, { grow: 16 }), e, s);
  assert.strictEqual(w[(s - 1) * s + s - 1], 1); assert.strictEqual(w[(s - 2) * s + 1], 1);
});

test('color: selects the region near the picked colour', () => {
  const s = 32, m = busyMaps(s, 1); // ліва половина 200,40,40, права 40,40,200
  const a = mg.generate(gen('color', 1, { color: '#c82828', tolerance: 0.1, softness: 0 }), m, s);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) assert.strictEqual(a[y * s + x], x < s / 2 ? 1 : 0);
  const near = mg.generate(gen('color', 1, { color: '#be3232', tolerance: 0.02, softness: 0.1 }), m, s);
  assert.ok(near[0] > 0 && near[0] < 1, 'soft falloff just outside the tolerance: ' + near[0]);
  assert.strictEqual(near[s - 1], 0);
});

test('missing maps fall back to the composite base (flat, grey, no team)', () => {
  const s = 16;
  for (const type of TYPES) {
    const a = mg.generate(gen(type, 1, {}), {}, s);
    assert.strictEqual(a.length, s * s);
    assert.ok(a.every(v => v >= 0 && v <= 1));
  }
  assert.ok(mg.generate(gen('edges', 1, {}), {}, s).every(v => v === 0));
  assert.ok(mg.generate(gen('team', 1, {}), {}, s).every(v => v === 0));
});
