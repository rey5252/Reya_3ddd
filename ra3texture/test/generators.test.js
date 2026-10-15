const test = require('node:test'); const assert = require('node:assert');

// ---- Тести з брифу ----
const g = require('../js/generators.js'); const m = require('../js/maps.js');
const fresh = s => { const d = m.createDocument({ size: s }); m.rebuild(d, { apply() {} }); return d.maps; };
const hash = maps => [...maps.color, ...maps.spec, ...maps.team, ...maps.height.map(v => Math.round(v * 1000))].join(',');
for (const { type } of g.catalog) if (type !== 'image') {
  test(`${type}: same seed same result, different seed differs`, () => {
    const L = g.defaultLayer(type, 5); const a = fresh(64), b = fresh(64), c = fresh(64);
    g.apply(L, a, 64, {}); g.apply(L, b, 64, {}); g.apply({ ...L, seed: 6 }, c, 64, {});
    assert.strictEqual(hash(a), hash(b)); if (type !== 'base' && type !== 'team') assert.notStrictEqual(hash(a), hash(c)); }); }
test('panels lower height and darken seams only', () => { const x = fresh(64); g.apply(g.defaultLayer('panels', 1), x, 64, {});
  const min = Math.min(...x.height); assert.ok(min < 0.5); assert.ok(x.height.some(v => v === 0.5));
  const i = x.height.indexOf(min); assert.ok(x.color[i * 4] < 128); });
test('camo touches only color', () => { const x = fresh(32); g.apply(g.defaultLayer('camo', 1), x, 32, {});
  assert.ok(x.height.every(v => v === 0.5)); assert.ok(x.team.every(v => v === 0)); });
test('rust zeroes spec under rust', () => { const x = fresh(64); x.spec.fill(200);
  g.apply({ ...g.defaultLayer('rust', 3), params: { ...g.defaultLayer('rust').params, amount: 1 } }, x, 64, {});
  assert.ok(x.spec.some(v => v === 0)); });
test('team rect fills mask and neutralizes color', () => { const x = fresh(32);
  const L = g.defaultLayer('team', 1); Object.assign(L.params, { mode: 'rect', x: 0, y: 0, w: 1, h: 1, strength: 1 });
  x.color.fill(200); for (let i = 3; i < x.color.length; i += 4) x.color[i] = 255; g.apply(L, x, 32, {});
  assert.ok(x.team.every(v => v === 255)); assert.strictEqual(x.color[0], Math.round(0.5 * 200 + 64)); });
test('image layer writes chosen channel to target', () => { const x = fresh(4);
  const img = new Uint8ClampedArray(64); for (let i = 0; i < 64; i += 4) img.set([10, 20, 250, 255], i);
  g.apply({ type: 'image', enabled: true, seed: 0, params: { imageId: 'i', target: 'team', channel: 'b', opacity: 1 } }, x, 4, { i: img });
  assert.ok(x.team.every(v => v === 250)); });
test('unknown type throws', () => assert.throws(() => g.apply({ type: 'zzz', params: {} }, fresh(4), 4, {}), /Невідомий шар/));

// ---- Допоміжне ----
const TYPES = ['base', 'camo', 'panels', 'rivets', 'dirt', 'rust', 'scratches', 'team', 'image'];
const PROC = TYPES.filter(t => t !== 'image');
const layer = (type, seed, params) => { const L = g.defaultLayer(type, seed); Object.assign(L.params, params || {}); return L; };
const run = (type, size, seed, params, prep) => { const x = fresh(size); if (prep) prep(x); g.apply(layer(type, seed, params), x, size, {}); return x; };
const rgbAt = (x, i) => [x.color[i * 4], x.color[i * 4 + 1], x.color[i * 4 + 2]];
const minOf = a => a.reduce((p, v) => (v < p ? v : p), Infinity), maxOf = a => a.reduce((p, v) => (v > p ? v : p), -Infinity);
const argmax = a => { let k = 0; for (let i = 1; i < a.length; i++) if (a[i] > a[k]) k = i; return k; };
const mapsEqual = (a, b) => hash(a) === hash(b) && a.height.every((v, i) => v === b.height[i]);
// Середня різниця сусідніх рядів/стовпців: пара через край (size-1 → 0) проти найбільшої внутрішньої пари.
const tileStats = (get, s) => [0, 1].map(axis => {
  const d = new Float64Array(s);
  for (let a = 0; a < s; a++) { const b = (a + 1) % s; let sum = 0;
    for (let t = 0; t < s; t++) sum += Math.abs(axis ? get(t, b) - get(t, a) : get(b, t) - get(a, t));
    d[a] = sum / s; }
  let max = 0; for (let a = 0; a < s - 1; a++) max = Math.max(max, d[a]);
  return { wrap: d[s - 1], max }; });

// ---- Каталог і defaultLayer ----
test('catalog lists every layer type with its Ukrainian label', () => {
  assert.deepStrictEqual(g.catalog.map(c => c.type), TYPES);
  assert.deepStrictEqual(g.catalog.map(c => c.label), ['Основа фракції', 'Камуфляж', 'Панелі', 'Заклепки', 'Бруд/пил', 'Іржа',
    'Подряпини', 'Колір гравця', 'Зображення']); });
test('FACTION_PRESETS match the spec', () => {
  assert.deepStrictEqual(g.FACTION_PRESETS, { allied: ['#9fa9b0', '#4a5560'], soviet: ['#7a2e24', '#4b4a45'], empire: ['#d8d4c8', '#2f3236'] }); });
test('catalog params have the fixed keys and complete UI metadata', () => {
  const required = {
    base: ['preset', 'color1', 'color2', 'noise'], camo: ['pattern', 'color1', 'color2', 'color3', 'scale', 'coverage'],
    panels: ['countX', 'countY', 'jitter', 'seam', 'depth'], rivets: ['spacing', 'size', 'mode'],
    dirt: ['amount', 'gradient', 'scale', 'color'], rust: ['amount', 'size', 'color'], scratches: ['count', 'length', 'width'],
    team: ['mode', 'x', 'y', 'w', 'h', 'angle', 'count', 'strength', 'lumaMin'], image: ['imageId', 'target', 'channel', 'opacity'] };
  for (const c of g.catalog) {
    const keys = c.params.map(p => p.key);
    assert.strictEqual(new Set(keys).size, keys.length, c.type + ': duplicate keys');
    for (const k of required[c.type]) assert.ok(keys.includes(k), c.type + '.' + k);
    for (const p of c.params) {
      const id = c.type + '.' + p.key;
      assert.ok(typeof p.label === 'string' && p.label.length > 0, id);
      assert.ok(['range', 'color', 'select', 'checkbox'].includes(p.kind), id);
      if (p.kind === 'range') {
        assert.ok(p.min < p.max && p.step > 0, id); assert.ok(p.default >= p.min && p.default <= p.max, id);
      } else if (p.kind === 'color') {
        assert.match(p.default, /^#[0-9a-f]{6}$/, id);
      } else if (p.kind === 'select') {
        assert.ok(Array.isArray(p.options), id);
        for (const o of p.options) assert.ok(typeof o.label === 'string' && o.label.length > 0, id);
        if (p.source === undefined) assert.ok(p.options.some(o => o.value === p.default), id);
      } else assert.strictEqual(typeof p.default, 'boolean', id);
    } }
  const opts = (type, key) => g.catalog.find(c => c.type === type).params.find(p => p.key === key).options.map(o => o.value);
  assert.deepStrictEqual(opts('base', 'preset'), ['allied', 'soviet', 'empire', 'custom']);
  assert.deepStrictEqual(opts('camo', 'pattern'), ['blotch', 'stripes', 'digital']);
  assert.deepStrictEqual(opts('rivets', 'mode').slice().sort(), ['grid', 'seams']);
  assert.deepStrictEqual(opts('team', 'mode'), ['stripes', 'rect', 'luma']);
  assert.deepStrictEqual(opts('image', 'target'), m.TARGETS);
  assert.deepStrictEqual(opts('image', 'channel'), ['lum', 'r', 'g', 'b', 'a']);
  assert.strictEqual(g.defaultLayer('rust', 1).params.color, '#7b3f1d');
  assert.strictEqual(g.catalog.find(c => c.type === 'image').params.find(p => p.key === 'imageId').source, 'images'); });
test('defaultLayer builds a layer from catalog defaults', () => {
  for (const c of g.catalog) {
    const L = g.defaultLayer(c.type, 42);
    assert.strictEqual(L.type, c.type); assert.strictEqual(L.name, c.label); assert.strictEqual(L.enabled, true); assert.strictEqual(L.seed, 42);
    assert.ok(typeof L.id === 'string' && L.id.length > 0);
    const want = {}; for (const p of c.params) want[p.key] = p.default;
    assert.deepStrictEqual(L.params, want, c.type); } });
test('defaultLayer ids are unique, params are fresh copies, missing seed is a random 32-bit integer', () => {
  const ids = new Set(); for (let i = 0; i < 200; i++) ids.add(g.defaultLayer('panels', 1).id);
  assert.strictEqual(ids.size, 200);
  const a = g.defaultLayer('panels', 1); a.params.countX = 99; assert.notStrictEqual(g.defaultLayer('panels', 1).params.countX, 99);
  const seeds = new Set();
  for (let i = 0; i < 20; i++) { const s = g.defaultLayer('base').seed; assert.ok(Number.isInteger(s) && s >= 0 && s < 2 ** 32, String(s)); seeds.add(s); }
  assert.ok(seeds.size > 15); });
test('defaultLayer rejects unknown types', () => assert.throws(() => g.defaultLayer('zzz', 1), /Невідомий шар/));
test('apply fills missing params with defaults and clamps out-of-range numbers', () => {
  const a = fresh(64), b = fresh(64), c = fresh(64);
  g.apply({ type: 'panels', seed: 3, params: {} }, a, 64, {}); g.apply(layer('panels', 3), b, 64, {});
  assert.ok(mapsEqual(a, b));
  g.apply({ type: 'panels', seed: 3, params: { countX: 1e9, depth: 'x', jitter: '0.4' } }, c, 64, {});
  const d = fresh(64); g.apply(layer('panels', 3, { countX: 16 }), d, 64, {});
  assert.ok(mapsEqual(c, d)); });

// ---- Загальні властивості процедурних шарів ----
test('procedural layers keep alpha, keep height finite in 0..1, and tile seamlessly', () => {
  const S = 128;
  for (const type of PROC) for (const seed of [11, 12]) {
    const x = run(type, S, seed);
    for (let i = 3; i < x.color.length; i += 4) assert.strictEqual(x.color[i], 255, type + ' alpha');
    assert.ok(x.height.every(v => v >= 0 && v <= 1), type + ' height range');
    const getters = {
      r: (px, py) => x.color[(py * S + px) * 4], g: (px, py) => x.color[(py * S + px) * 4 + 1], b: (px, py) => x.color[(py * S + px) * 4 + 2],
      height: (px, py) => x.height[py * S + px] * 255, spec: (px, py) => x.spec[py * S + px], team: (px, py) => x.team[py * S + px] };
    for (const [name, get] of Object.entries(getters)) for (const [axis, st] of tileStats(get, S).entries()) {
      assert.ok(st.wrap <= st.max * 1.5 + 0.5, `${type} seed ${seed} ${name} axis ${axis}: wrap ${st.wrap.toFixed(2)} vs interior max ${st.max.toFixed(2)}`); }
  } });
test('panel seams continue across the texture edge (largest step at the wrap ≤ largest interior step)', () => {
  const S = 512; // на опорному розмірі дно шва ширше за піксель, тож обрізаний шов дає стрибок, більший за будь-який скіс
  for (const seed of [1, 2, 3]) {
    const h = run('panels', S, seed).height; let wrap = 0, inner = 0;
    for (let a = 0; a < S; a++) for (let b = 0; b < S; b++) {
      const dx = Math.abs(h[a * S + (b + 1) % S] - h[a * S + b]), dy = Math.abs(h[((b + 1) % S) * S + a] - h[b * S + a]);
      if (b === S - 1) wrap = Math.max(wrap, dx, dy); else inner = Math.max(inner, dx, dy); }
    assert.ok(wrap <= inner + 1e-6, `seed ${seed}: wrap ${wrap} vs interior ${inner}`); } });
test('dirt gradient wraps from bottom to top without a seam', () => {
  const S = 256, x = run('dirt', S, 3, { amount: 0.5, gradient: 1, scale: 2, cavity: 0 });
  const st = tileStats((px, py) => x.color[(py * S + px) * 4], S)[1];
  assert.ok(st.wrap <= st.max * 1.5 + 0.5, `wrap ${st.wrap} vs interior ${st.max}`); });
test('a scratch leaving through one edge re-enters through the opposite edge at the same height', () => {
  // Одна подряпина (≤ 25 px) коротша за текстуру (128 px): торкнутися обох протилежних країв у тих самих рядках/стовпцях
  // вона може, лише якщо загортається. Перебираємо seed-и детерміновано й вимагаємо кілька таких перетинів на кожній осі.
  const S = 128, on = 0.15, found = { x: 0, y: 0 };
  const meet = (a, b) => { for (let t = 0; t < S; t++) if (a(t) >= on) for (let d = -2; d <= 2; d++) if (t + d >= 0 && t + d < S && b(t + d) >= on) return true; return false; };
  for (let seed = 1; seed <= 150; seed++) {
    const x = run('scratches', S, seed, { count: 1, length: 60, width: 4 }), F = (px, py) => x.spec[py * S + px] / 140;
    if (meet(py => F(S - 1, py), py => F(0, py))) found.x++;
    if (meet(px => F(px, S - 1), px => F(px, 0))) found.y++; }
  assert.ok(found.x >= 3 && found.y >= 3, JSON.stringify(found)); });
test('rivets straddling the edge are stamped whole (exact shift invariance)', () => {
  // Штучний шов біля краю: ліві заклепки сідають на межу текстури. Той самий шов, зсунутий на S/2, має дати
  // рівно ті самі карти, зсунуті на S/2 (розміщення залежить лише від висоти, купол — від дробової позиції).
  const S = 512;
  for (const axis of [0, 1]) {
    const mk = off => run('rivets', S, 9, { mode: 'seams' }, x => {
      for (let a = 0; a < S; a++) for (let t = 0; t < 3; t++) { const c = (5 + off + t) % S; x.height[axis ? c * S + a : a * S + c] = 0.25; } });
    const A = mk(0), B = mk(S / 2); let diff = 0, edge = 0;
    for (let y = 0; y < S; y++) for (let px = 0; px < S; px++) {
      const i = y * S + px, j = axis ? ((y + S / 2) % S) * S + px : y * S + (px + S / 2) % S;
      if (A.height[i] !== B.height[j] || A.spec[i] !== B.spec[j] || A.color[i * 4] !== B.color[j * 4]) diff++;
      if ((axis ? y : px) === 0 && A.height[i] > 0.5) edge++; }
    assert.ok(edge > 0, 'axis ' + axis + ': rivets reach the edge'); assert.strictEqual(diff, 0, 'axis ' + axis); } });
test('rust streaks run on from the bottom edge onto the top edge', () => {
  // Ефект патьоків = різниця блиску без і з патьоками (той самий seed). Нижче плями він може лише згасати
  // з множником decay ≥ 0.75 на піксель — і на межі рядків S-1 → 0 так само, як усередині.
  const S = 256, mk = streaks => run('rust', S, 4, { amount: 0.4, streaks }, x => { x.spec.fill(200); x.team.fill(255); });
  const off = mk(0), on = mk(1); let wrap = 0, wrapBad = 0, inner = 0, innerBad = 0;
  for (let px = 0; px < S; px++) for (let y = 0; y < S; y++) {
    const a = ((y - 1 + S) % S) * S + px, b = y * S + px;
    if (on.team[a] !== 255 || on.team[b] !== 255 || off.spec[a] < 100 || off.spec[b] < 100) continue; // лише поза іржею
    const ra = (off.spec[a] - on.spec[a]) / off.spec[a], rb = (off.spec[b] - on.spec[b]) / off.spec[b];
    if (ra < 0.1) continue;
    const bad = rb < 0.6 * ra;
    if (y === 0) { wrap++; wrapBad += bad; } else { inner++; innerBad += bad; } }
  assert.ok(wrap >= 10, 'streaks cross the bottom edge: ' + wrap);
  assert.strictEqual(wrapBad, 0, 'breaks at the wrap'); assert.strictEqual(innerBad, 0, 'breaks inside'); });
test('camo patterns: all three tile and only change color', () => {
  for (const pattern of ['blotch', 'stripes', 'digital']) {
    const x = run('camo', 128, 4, { pattern });
    assert.ok(x.height.every(v => v === 0.5) && x.team.every(v => v === 0) && x.spec.every(v => v === 0), pattern);
    const lum = (px, py) => { const i = (py * 128 + px) * 4; return x.color[i] + x.color[i + 1] + x.color[i + 2]; };
    for (const st of tileStats(lum, 128)) assert.ok(st.wrap <= st.max * 1.5 + 0.5, pattern);
    const distinct = new Set(); for (let i = 0; i < 128 * 128; i++) distinct.add(rgbAt(x, i).join());
    assert.ok(distinct.size > 3, pattern + ' has variety'); } });
test('camo coverage 0 changes nothing; coverage controls the painted share', () => {
  assert.ok(mapsEqual(run('camo', 64, 2, { coverage: 0 }), fresh(64)));
  const share = cov => { const x = run('camo', 128, 2, { coverage: cov }); let k = 0;
    for (let i = 0; i < 128 * 128; i++) if (x.color[i * 4] !== 128 || x.color[i * 4 + 1] !== 128 || x.color[i * 4 + 2] !== 128) k++;
    return k / (128 * 128); };
  const lo = share(0.3), hi = share(0.8);
  assert.ok(lo > 0.15 && lo < 0.5, 'coverage 0.3 → ' + lo); assert.ok(hi > 0.65, 'coverage 0.8 → ' + hi); });

// ---- base ----
test('base: noise 0 gives the flat preset colour and gloss 30', () => {
  const x = run('base', 32, 1, { preset: 'soviet', noise: 0 });
  for (let i = 0; i < 32 * 32; i++) assert.deepStrictEqual([...x.color.slice(i * 4, i * 4 + 4)], [0x7a, 0x2e, 0x24, 255]);
  assert.ok(x.spec.every(v => v === 30)); assert.ok(x.height.every(v => v === 0.5)); assert.ok(x.team.every(v => v === 0)); });
test('base: preset overrides colours, custom uses color1', () => {
  const e = run('base', 16, 1, { preset: 'empire', color1: '#000000', noise: 0 });
  assert.deepStrictEqual(rgbAt(e, 5), [0xd8, 0xd4, 0xc8]);
  const c = run('base', 16, 1, { preset: 'custom', color1: '#102030', color2: '#ffffff', noise: 0 });
  assert.deepStrictEqual(rgbAt(c, 5), [0x10, 0x20, 0x30]); });
test('base: noise mixes color1 toward color2', () => {
  const x = run('base', 64, 7, { preset: 'allied', noise: 1 });
  const lo = [0x4a, 0x55, 0x60], hi = [0x9f, 0xa9, 0xb0]; const seen = new Set();
  for (let i = 0; i < 64 * 64; i++) { const c = rgbAt(x, i); seen.add(c.join());
    for (let k = 0; k < 3; k++) assert.ok(c[k] >= lo[k] - 12 && c[k] <= hi[k] + 12, c.join()); }
  assert.ok(seen.size > 20);
  let darker = 0; for (let i = 0; i < 64 * 64; i++) if (x.color[i * 4] < hi[0] - 25) darker++;
  assert.ok(darker > 64 * 64 * 0.05, 'some areas lean to color2'); });

// ---- panels ----
test('panels: seam edges gain gloss, team untouched', () => {
  const x = run('panels', 128, 1); const min = minOf(x.height); const k = argmax(x.spec);
  assert.ok(x.spec[k] > 0); assert.ok(x.height[k] < 0.5 && x.height[k] > min, 'gloss peak sits on the bevel');
  assert.ok(x.team.every(v => v === 0)); });
test('panels: seam width scales with texture size', () => {
  const frac = s => run('panels', s, 9).height.filter(v => v < 0.5).length / (s * s);
  const a = frac(512), b = frac(1024); assert.ok(Math.abs(a / b - 1) < 0.15, a + ' vs ' + b); });

// ---- rivets ----
test('rivets raise height and spec, never lower height', () => {
  const x = run('rivets', 64, 1, { mode: 'grid' });
  assert.ok(maxOf(x.height) > 0.55); assert.ok(minOf(x.height) >= 0.5); assert.ok(maxOf(x.spec) > 0);
  assert.ok(x.team.every(v => v === 0)); });
test('rivets: seams mode follows panel seams, grid mode does not', () => {
  const S = 256, panelsOnly = fresh(S); g.apply(layer('panels', 4), panelsOnly, S, {});
  const groove = new Uint8Array(S * S); panelsOnly.height.forEach((v, i) => { groove[i] = v < 0.45 ? 1 : 0; });
  const nearGroove = (i, R) => { const px = i % S, py = (i / S) | 0;
    for (let dy = -R; dy <= R; dy++) for (let dx = -R; dx <= R; dx++) if (groove[((py + dy + S) % S) * S + (px + dx + S) % S]) return true;
    return false; };
  const raised = mode => { const x = fresh(S); g.apply(layer('panels', 4), x, S, {}); g.apply(layer('rivets', 4, { mode }), x, S, {});
    const out = []; x.height.forEach((v, i) => { if (v > panelsOnly.height[i] + 0.01) out.push(i); }); return out; };
  const seams = raised('seams'), grid = raised('grid');
  assert.ok(seams.length > 50, 'rivets placed: ' + seams.length);
  assert.ok(seams.every(i => nearGroove(i, 8)), 'every seam rivet is next to a seam');
  assert.ok(seams.every(i => !groove[i]), 'no rivet sits inside a seam');
  assert.ok(grid.some(i => !nearGroove(i, 8)), 'grid rivets cover panel interiors'); });
test('rivets: seams mode on a flat surface falls back to a grid', () => {
  const x = run('rivets', 64, 2, { mode: 'seams' }); assert.ok(maxOf(x.height) > 0.55); });
test('rivets: count is size-independent', () => {
  const area = s => run('rivets', s, 3, { mode: 'grid' }).height.filter(v => v > 0.5).length / (s * s);
  const a = area(512), b = area(1024); assert.ok(Math.abs(a / b - 1) < 0.15, a + ' vs ' + b); });

// ---- dirt ----
test('dirt pulls colour toward its colour and lowers spec only', () => {
  const S = 64, before = fresh(S); before.spec.fill(200);
  const x = run('dirt', S, 1, { amount: 1, color: '#403020' }, y => y.spec.fill(200));
  const dist = (mm, i) => Math.abs(mm.color[i * 4] - 0x40) + Math.abs(mm.color[i * 4 + 1] - 0x30) + Math.abs(mm.color[i * 4 + 2] - 0x20);
  let d0 = 0, d1 = 0; for (let i = 0; i < S * S; i++) { d0 += dist(before, i); d1 += dist(x, i); }
  assert.ok(d1 < d0 * 0.6, d1 + ' vs ' + d0);
  assert.ok(x.spec.every(v => v <= 200)); assert.ok(x.spec.some(v => v < 100));
  assert.ok(x.height.every(v => v === 0.5)); assert.ok(x.team.every(v => v === 0)); });
test('dirt amount 0 changes nothing; gradient makes the bottom dirtier', () => {
  assert.ok(mapsEqual(run('dirt', 64, 1, { amount: 0 }), fresh(64)));
  const S = 128, x = run('dirt', S, 5, { amount: 0.5, gradient: 1, color: '#000000' });
  const rowMean = (y0, y1) => { let s = 0; for (let y = y0; y < y1; y++) for (let px = 0; px < S; px++) s += x.color[(y * S + px) * 4]; return s / ((y1 - y0) * S); };
  assert.ok(rowMean(Math.round(S * 0.8), Math.round(S * 0.9)) < rowMean(Math.round(S * 0.1), Math.round(S * 0.2)) - 10); });
test('dirt collects in seams', () => {
  const S = 128, x = fresh(S); g.apply(layer('panels', 2), x, S, {}); const panels = x.color.slice();
  g.apply(layer('dirt', 2, { amount: 0.25, gradient: 0, color: '#000000' }), x, S, {});
  // Частка шляху до чорного бруду: у швах має бути помітно більшою, ніж на рівній панелі.
  let seam = 0, ns = 0, flat = 0, nf = 0;
  for (let i = 0; i < S * S; i++) { const f = 1 - x.color[i * 4] / panels[i * 4];
    if (x.height[i] < 0.4) { seam += f; ns++; } else if (x.height[i] === 0.5) { flat += f; nf++; } }
  assert.ok(ns > 0 && nf > 0); assert.ok(seam / ns > flat / nf + 0.1, (seam / ns) + ' vs ' + (flat / nf)); });

// ---- rust ----
test('rust paints rust colour, roughens height, amount 0 does nothing', () => {
  assert.ok(mapsEqual(run('rust', 64, 1, { amount: 0 }), fresh(64)));
  const x = run('rust', 64, 1, { amount: 0.6 }, y => y.spec.fill(150));
  assert.ok(x.height.some(v => v !== 0.5));
  let rusty = 0; for (let i = 0; i < 64 * 64; i++) if (x.spec[i] === 0) { rusty++; const [r, gg, b] = rgbAt(x, i); assert.ok(r > gg && gg >= b, [r, gg, b].join()); }
  assert.ok(rusty > 64 * 64 * 0.3, 'rusty share ' + rusty); });
test('rust and scratches strip the player colour they cover', () => {
  for (const [type, params] of [['rust', { amount: 0.6 }], ['scratches', { count: 200 }]]) {
    const x = run(type, 64, 1, params, y => y.team.fill(255)); assert.ok(x.team.some(v => v < 128), type); } });

// ---- scratches ----
test('scratches expose bright metal: spec up, height down, colour lighter', () => {
  assert.ok(mapsEqual(run('scratches', 64, 1, { count: 0 }), fresh(64)));
  const x = run('scratches', 512, 1); const k = argmax(x.spec); // 512 — опорний розмір: ширина 1 px
  assert.ok(x.spec[k] > 100); assert.ok(minOf(x.height) < 0.5); assert.ok(maxOf(x.height) <= 0.5);
  assert.ok(x.color[k * 4] > 160, 'metal colour at the brightest scratch'); });

test('scratches never lower gloss, even on a glossy base', () => {
  const S = 128, x = fresh(S); g.apply(layer('base', 1, { gloss: 200 }), x, S, {}); const before = x.spec.slice();
  g.apply(layer('scratches', 1, { count: 200 }), x, S, {});
  assert.ok(x.spec.every((v, i) => v >= before[i])); assert.ok(x.spec.some((v, i) => v > before[i])); });

// ---- team ----
test('team stripes: default paints a band, leaves height and spec', () => {
  const x = run('team', 64, 1);
  assert.ok(x.team.some(v => v === 255)); assert.ok(x.team.some(v => v === 0));
  assert.ok(x.height.every(v => v === 0.5)); assert.ok(x.spec.every(v => v === 0)); });
test('team stripes: count stripes across the region', () => {
  const S = 128, x = run('team', S, 1, { mode: 'stripes', x: 0, y: 0.2, w: 1, h: 0.5, angle: 0, count: 3 });
  for (const px of [0, 50, 127]) { let runs = 0, prev = 0;
    for (let y = 0; y < S; y++) { const on = x.team[y * S + px] > 127 ? 1 : 0; if (on && !prev) runs++; prev = on; }
    assert.strictEqual(runs, 3); }
  assert.ok(x.team.slice(0, Math.floor(0.2 * S) * S).every(v => v === 0)); });
test('team colour neutralization uses luminance; strength scales the mask', () => {
  const x = fresh(8); for (let i = 0; i < 64; i++) x.color.set([200, 100, 50, 255], i * 4);
  g.apply(layer('team', 1, { mode: 'rect', x: 0, y: 0, w: 1, h: 1, strength: 1 }), x, 8, {});
  const v = Math.round(0.5 * (0.299 * 200 + 0.587 * 100 + 0.114 * 50) + 64);
  assert.deepStrictEqual(rgbAt(x, 9), [v, v, v]);
  const y = fresh(8); y.color.fill(200); for (let i = 3; i < 256; i += 4) y.color[i] = 255;
  g.apply(layer('team', 1, { mode: 'rect', x: 0, y: 0, w: 1, h: 1, strength: 0.5 }), y, 8, {});
  assert.ok(Math.abs(y.team[0] - 127.5) <= 0.5); assert.strictEqual(y.color[0], 182); });
test('team rect wraps around the right edge', () => {
  const S = 32, x = run('team', S, 1, { mode: 'rect', x: 0.75, y: 0, w: 0.5, h: 1, strength: 1 });
  for (let px = 0; px < S; px++) assert.strictEqual(x.team[5 * S + px], px < 8 || px >= 24 ? 255 : 0, 'x=' + px); });
test('team luma mode masks bright areas', () => {
  const S = 16, x = fresh(S);
  for (let i = 0; i < S * S; i++) { const v = i < S * S / 2 ? 220 : 40; x.color.set([v, v, v, 255], i * 4); }
  g.apply(layer('team', 1, { mode: 'luma', lumaMin: 0.5, strength: 1 }), x, S, {});
  assert.strictEqual(x.team[0], 255); assert.strictEqual(x.team[S * S - 1], 0); });

// ---- image ----
test('image layer: colour lerp by opacity, alpha, height and luminance channel', () => {
  const S = 4, img = new Uint8ClampedArray(S * S * 4); for (let i = 0; i < S * S; i++) img.set([200, 100, 10, 60], i * 4);
  const lay = (target, channel, opacity) => ({ type: 'image', enabled: true, seed: 0, params: { imageId: 'i', target, channel, opacity } });
  let x = fresh(S); g.apply(lay('color', 'lum', 0.5), x, S, { i: img });
  assert.deepStrictEqual([...x.color.slice(0, 4)], [164, 114, 69, 255]);
  x = fresh(S); g.apply(lay('alpha', 'a', 1), x, S, { i: img }); assert.strictEqual(x.color[3], 60); assert.strictEqual(x.color[0], 128);
  x = fresh(S); g.apply(lay('height', 'r', 1), x, S, { i: img }); assert.ok(Math.abs(x.height[0] - 200 / 255) < 1e-6);
  x = fresh(S); g.apply(lay('spec', 'lum', 1), x, S, { i: img }); assert.strictEqual(x.spec[0], Math.round(0.299 * 200 + 0.587 * 100 + 0.114 * 10));
  x = fresh(S); g.apply(lay('team', 'g', 0.5), x, S, { i: img }); assert.strictEqual(x.team[0], 50); });
test('image layer without its image changes nothing', () => {
  const x = fresh(4); g.apply(g.defaultLayer('image', 0), x, 4, {}); assert.ok(mapsEqual(x, fresh(4))); });
