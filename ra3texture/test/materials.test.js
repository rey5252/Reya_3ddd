const test = require('node:test'); const assert = require('node:assert');
const mat = require('../js/materials.js');
const mg = require('../js/maskgen.js');

const CHANNELS = ['color', 'alpha', 'height', 'spec', 'team'];
const byId = id => mat.list.find(m => m.id === id);

function checkMaterial(m, where) {
  assert.ok(Array.isArray(m.color) && m.color.length === 3, where + ': color');
  for (const c of m.color) assert.ok(Number.isInteger(c) && c >= 0 && c <= 255, where + ': color ' + c);
  assert.ok(Number.isInteger(m.alpha) && m.alpha >= 0 && m.alpha <= 255, where + ': alpha');
  assert.ok(typeof m.height === 'number' && m.height >= 0 && m.height <= 1, where + ': height');
  assert.ok(Number.isInteger(m.spec) && m.spec >= 0 && m.spec <= 255, where + ': spec');
  assert.ok(Number.isInteger(m.team) && m.team >= 0 && m.team <= 255, where + ': team');
}
function checkChannels(ch, where) {
  assert.deepStrictEqual(Object.keys(ch).sort(), [...CHANNELS].sort(), where + ': channel keys');
  for (const k of CHANNELS) assert.strictEqual(typeof ch[k], 'boolean', where + ': ' + k);
  assert.ok(CHANNELS.some(k => ch[k]), where + ': at least one channel');
}

// ---- Прості матеріали ----

test('every simple material has 5 channel values in range and at least one channel enabled', () => {
  assert.ok(mat.list.length >= 18);
  const ids = new Set();
  for (const m of mat.list) {
    assert.ok(typeof m.id === 'string' && /^[a-z0-9-]+$/.test(m.id), 'id ' + m.id);
    assert.ok(!ids.has(m.id), 'duplicate id ' + m.id); ids.add(m.id);
    assert.ok(typeof m.label === 'string' && /[а-яіїєґ]/i.test(m.label), 'Ukrainian label for ' + m.id);
    checkMaterial(m.material, m.id);
    checkChannels(m.channels, m.id);
  }
});

test('the required RA3 materials are present', () => {
  for (const id of ['allied-light', 'allied-dark', 'soviet-red', 'soviet-steel', 'empire-white', 'empire-graphite', 'bare-steel',
    'rust', 'dirt', 'rubber', 'glass', 'concrete', 'team-paint', 'seam', 'bump', 'gloss-up', 'matte', 'cutout']) {
    assert.ok(byId(id), 'missing ' + id);
  }
});

test('player-colour paint is 50% grey with team 255 and colour+spec+team channels', () => {
  const t = byId('team-paint');
  assert.deepStrictEqual(t.material.color, [128, 128, 128]);
  assert.strictEqual(t.material.team, 255);
  assert.ok(t.material.spec > 30 && t.material.spec < 100);
  assert.deepStrictEqual(t.channels, { color: true, alpha: false, height: false, spec: true, team: true });
});

test('single-channel helpers touch only their channel', () => {
  const only = (id, ch) => { const m = byId(id); for (const k of CHANNELS) assert.strictEqual(m.channels[k], k === ch, id + '.' + k); return m.material; };
  assert.ok(only('seam', 'height').height < 0.4);
  assert.ok(only('bump', 'height').height > 0.6);
  assert.ok(only('gloss-up', 'spec').spec >= 180);
  assert.strictEqual(only('matte', 'spec').spec, 0);
  assert.strictEqual(only('cutout', 'alpha').alpha, 0);
});

test('materials read right in RA3: steel and glass glossy, rust/dirt/rubber dull, rust kills gloss', () => {
  assert.ok(byId('bare-steel').material.spec >= 180);
  assert.ok(byId('glass').material.spec >= 180);
  assert.strictEqual(byId('rust').material.spec, 0);
  assert.ok(byId('dirt').material.spec <= 20 && byId('rubber').material.spec <= 30);
  assert.ok(byId('concrete').material.spec <= 20);
  const lum = c => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
  assert.ok(lum(byId('rubber').material.color) < 50);
  assert.ok(lum(byId('allied-light').material.color) > lum(byId('allied-dark').material.color));
  assert.ok(lum(byId('empire-white').material.color) > 180);
  const red = byId('soviet-red').material.color; assert.ok(red[0] > red[1] * 1.8 && red[0] > red[2] * 1.8);
  const glass = byId('glass').material.color; assert.ok(glass[2] > glass[0]);
  // Фарби, що перекривають маску гравця, мають команду 0 (а не лишають її як є)
  for (const id of ['bare-steel', 'rust', 'dirt']) { assert.strictEqual(byId(id).material.team, 0); assert.ok(byId(id).channels.team, id); }
});

test('get returns a deep copy', () => {
  const a = mat.get('allied-light'); a.material.color[0] = 1; a.channels.color = false;
  assert.notStrictEqual(byId('allied-light').material.color[0], 1);
  assert.strictEqual(byId('allied-light').channels.color, true);
  assert.throws(() => mat.get('nope'), /Невідомий матеріал/);
});

// ---- Розумні матеріали ----

const SMART = ['allied-worn', 'soviet-rusty', 'empire-clean', 'bare-metal', 'concrete-building', 'team-stripes-worn'];

test('smart list has the required ids with Ukrainian labels and descriptions', () => {
  for (const id of SMART) assert.ok(mat.smart.some(s => s.id === id), 'missing ' + id);
  for (const s of mat.smart) {
    assert.ok(/[а-яіїєґ]/i.test(s.label) && typeof s.description === 'string' && s.description.length > 10, s.id);
  }
});

for (const { id } of mat.smart) {
  test(`smart ${id}: ≥2 valid plain fill specs, mask gens exist in maskgen.catalog`, () => {
    const specs = mat.smartLayers(id, 1234);
    assert.ok(Array.isArray(specs) && specs.length >= 2);
    let masked = 0;
    for (const s of specs) {
      const where = id + '/' + s.name;
      assert.strictEqual(s.kind, 'fill', where);
      assert.ok(typeof s.name === 'string' && s.name.length > 0, where);
      assert.ok(s.heightMode === 'add' || s.heightMode === 'replace', where);
      assert.ok(typeof s.opacity === 'number' && s.opacity > 0 && s.opacity <= 1, where);
      checkMaterial(s.material, where);
      checkChannels(s.channels, where);
      for (const v of Object.values(s)) assert.ok(!ArrayBuffer.isView(v), where + ': plain spec, no buffers');
      if (s.mask !== undefined) {
        assert.ok(s.mask && typeof s.mask === 'object', where);
        if (s.mask.invert !== undefined) assert.strictEqual(typeof s.mask.invert, 'boolean', where);
        if (s.mask.gen) {
          masked++;
          const def = mg.catalog.find(c => c.type === s.mask.gen.type);
          assert.ok(def, where + ': unknown mask type ' + s.mask.gen.type);
          assert.ok(Number.isInteger(s.mask.gen.seed), where + ': seed');
          for (const k of Object.keys(s.mask.gen.params)) assert.ok(def.params.some(p => p.key === k), where + ': unknown param ' + k);
          const resolved = mg.resolveParams(s.mask.gen.type, s.mask.gen.params);
          for (const [k, v] of Object.entries(s.mask.gen.params)) assert.strictEqual(resolved[k], v, where + ': param ' + k + ' out of range');
        }
      }
    }
    assert.ok(masked >= 1, 'a smart material masks at least one layer');
    assert.ok(!specs[0].mask || !specs[0].mask.gen || id === 'team-stripes-worn', 'base layer covers everything');
  });

  test(`smart ${id}: deterministic per seed, seed changes mask seeds, fresh objects each call`, () => {
    const a = mat.smartLayers(id, 77), b = mat.smartLayers(id, 77), c = mat.smartLayers(id, 78);
    assert.deepStrictEqual(a, b);
    const seeds = x => x.filter(s => s.mask && s.mask.gen).map(s => s.mask.gen.seed);
    assert.notDeepStrictEqual(seeds(a), seeds(c));
    a[0].material.color[0] = 3; a[0].channels.color = !a[0].channels.color;
    assert.deepStrictEqual(mat.smartLayers(id, 77), b);
  });
}

test('smart materials use the intended masks', () => {
  const types = id => mat.smartLayers(id, 1).map(s => s.mask && s.mask.gen ? s.mask.gen.type : null);
  assert.ok(types('allied-worn').includes('edges') && types('allied-worn').includes('cavity') && types('allied-worn').includes('noise'));
  assert.ok(types('soviet-rusty').includes('edges') && types('soviet-rusty').includes('gradient'));
  assert.ok(types('empire-clean').includes('cavity'));
  assert.ok(types('concrete-building').includes('gradient') && types('concrete-building').includes('cavity'));
  const team = mat.smartLayers('team-stripes-worn', 1);
  const paint = team.find(s => s.mask && s.mask.gen && s.mask.gen.type === 'team');
  assert.ok(paint && paint.material.team === 255 && paint.channels.team);
  const wear = team.find(s => s.mask && s.mask.gen && s.mask.gen.type === 'edges');
  assert.ok(wear && wear.channels.team && wear.material.team === 0, 'edges remove the player colour');
  assert.ok(team.indexOf(wear) > team.indexOf(paint), 'wear above the paint');
  // Основна фарба розумного матеріалу не стирає наявну маску гравця
  for (const id of ['allied-worn', 'soviet-rusty', 'empire-clean', 'bare-metal', 'concrete-building']) {
    assert.strictEqual(mat.smartLayers(id, 1)[0].channels.team, false, id);
  }
});

test('unknown smart id throws a Ukrainian error', () => {
  assert.throws(() => mat.smartLayers('zzz', 1), /Невідомий розумний матеріал: zzz/);
});

test('smart mask specs actually generate on a real map', () => {
  const s = 32, n = s * s, maps = { color: new Uint8ClampedArray(n * 4).fill(128), height: new Float32Array(n).fill(0.5), spec: new Uint8ClampedArray(n), team: new Uint8ClampedArray(n) };
  for (let i = 0; i < n; i += 7) maps.height[i] = 0.3;
  for (const { id } of mat.smart) for (const l of mat.smartLayers(id, 5)) {
    if (l.mask && l.mask.gen) {
      const m = mg.generate(l.mask.gen, maps, s);
      assert.strictEqual(m.length, n);
    }
  }
});
