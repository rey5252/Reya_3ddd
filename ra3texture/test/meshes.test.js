const test = require('node:test'); const assert = require('node:assert');
const meshes = require('../js/meshes.js');

// ---- Допоміжне ----
const MODELS = { cube: () => meshes.cube(), cylinder: () => meshes.cylinder(), sphere: () => meshes.sphere(), tank: () => meshes.tank() };
const v3 = (a, i) => [a[i * 3], a[i * 3 + 1], a[i * 3 + 2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const len = a => Math.sqrt(dot(a, a));
const near = (a, b, eps = 1e-4) => Math.abs(a - b) < eps;

// ---- Тести з брифу (для кожної з 4 моделей) ----
for (const [name, make] of Object.entries(MODELS)) {
  test(`${name}: attribute arrays are consistent`, () => {
    const m = make();
    assert.ok(m.positions instanceof Float32Array && m.normals instanceof Float32Array);
    assert.ok(m.tangents instanceof Float32Array && m.uvs instanceof Float32Array && m.indices instanceof Uint16Array);
    assert.strictEqual(m.positions.length % 3, 0);
    assert.strictEqual(m.positions.length, m.normals.length);
    assert.strictEqual(m.positions.length, m.tangents.length);
    assert.strictEqual(m.uvs.length / 2, m.positions.length / 3);
    for (const a of [m.positions, m.normals, m.tangents, m.uvs]) assert.ok(a.every(Number.isFinite), 'NaN/Infinity у даних меша');
  });

  test(`${name}: indices are valid triangles`, () => {
    const m = make(), n = m.positions.length / 3;
    assert.ok(m.indices.length > 0);
    assert.strictEqual(m.indices.length % 3, 0);
    assert.ok(n < 65536);
    for (const i of m.indices) assert.ok(i < n);
  });

  test(`${name}: normals are unit, tangents are unit and orthogonal to normals`, () => {
    const m = make(), n = m.positions.length / 3;
    for (let i = 0; i < n; i++) {
      const N = v3(m.normals, i), T = v3(m.tangents, i);
      assert.ok(Math.abs(len(N) - 1) < 1e-3, `вершина ${i}: |N| = ${len(N)}`);
      assert.ok(Math.abs(len(T) - 1) < 1e-3, `вершина ${i}: |T| = ${len(T)}`);
      assert.ok(Math.abs(dot(N, T)) < 1e-3, `вершина ${i}: N·T = ${dot(N, T)}`);
    }
  });

  test(`${name}: UVs lie in 0..1`, () => {
    const m = make();
    for (const u of m.uvs) assert.ok(u >= -1e-6 && u <= 1 + 1e-6, 'UV поза 0..1: ' + u);
  });
}

// ---- Конвенція UV / тангентного базису (рішення 2) ----
// Рядок 0 текстури — верх зображення, v росте донизу, T = dP/du, cross(N,T) = dP/dv для кожного трикутника.
// Додатково перевіряємо обхід трикутників проти годинникової стрілки зовні (для відсікання задніх граней).
for (const [name, make] of Object.entries(MODELS)) {
  test(`${name}: tangent frame follows dP/du and dP/dv, winding is outward`, () => {
    const m = make();
    let checked = 0;
    for (let t = 0; t < m.indices.length; t += 3) {
      const ids = [m.indices[t], m.indices[t + 1], m.indices[t + 2]];
      const p = ids.map(i => v3(m.positions, i));
      const uv = ids.map(i => [m.uvs[i * 2], m.uvs[i * 2 + 1]]);
      const e1 = sub(p[1], p[0]), e2 = sub(p[2], p[0]);
      const du1 = uv[1][0] - uv[0][0], dv1 = uv[1][1] - uv[0][1], du2 = uv[2][0] - uv[0][0], dv2 = uv[2][1] - uv[0][1];
      const det = du1 * dv2 - du2 * dv1;
      if (Math.abs(det) < 1e-9 || len(cross(e1, e2)) < 1e-12) continue; // вироджений у UV або в просторі
      const dPdu = [0, 1, 2].map(k => (e1[k] * dv2 - e2[k] * dv1) / det);
      const dPdv = [0, 1, 2].map(k => (e2[k] * du1 - e1[k] * du2) / det);
      const face = cross(e1, e2);
      for (const i of ids) {
        const N = v3(m.normals, i), T = v3(m.tangents, i);
        assert.ok(dot(T, dPdu) > 0, `трикутник ${t / 3}: T·dP/du = ${dot(T, dPdu)}`);
        assert.ok(dot(cross(N, T), dPdv) > 0, `трикутник ${t / 3}: cross(N,T)·dP/dv = ${dot(cross(N, T), dPdv)}`);
        assert.ok(dot(face, N) > 0, `трикутник ${t / 3}: обхід не назовні`);
      }
      checked++;
    }
    assert.strictEqual(checked, m.indices.length / 3, 'усі трикутники мають невироджені UV (без полюсних віял)');
  });
}

// ---- Кожна грань займає UV 0..1 ----
// Грані = компоненти зв'язності за індексами (куб: 6 граней; циліндр: бік і 2 кришки; сфера: одна поверхня).
function patches(m) {
  const n = m.positions.length / 3, parent = Array.from({ length: n }, (_, i) => i);
  const find = i => { while (parent[i] !== i) { parent[i] = parent[parent[i]]; i = parent[i]; } return i; };
  for (let t = 0; t < m.indices.length; t += 3) {
    parent[find(m.indices[t])] = find(m.indices[t + 1]);
    parent[find(m.indices[t + 2])] = find(m.indices[t + 1]);
  }
  const groups = new Map();
  for (let i = 0; i < n; i++) { const r = find(i); if (!groups.has(r)) groups.set(r, []); groups.get(r).push(i); }
  return [...groups.values()];
}

for (const [name, make] of Object.entries(MODELS)) {
  test(`${name}: UVs of every face span 0..1`, () => {
    const m = make();
    for (const g of patches(m)) {
      const us = g.map(i => m.uvs[i * 2]), vs = g.map(i => m.uvs[i * 2 + 1]);
      assert.ok(near(Math.min(...us), 0) && near(Math.max(...us), 1), `u: ${Math.min(...us)}..${Math.max(...us)}`);
      assert.ok(near(Math.min(...vs), 0) && near(Math.max(...vs), 1), `v: ${Math.min(...vs)}..${Math.max(...vs)}`);
    }
  });
}

// ---- Форми ----
test('cube has 6 separate faces with 4 vertices each', () => {
  const m = meshes.cube();
  assert.strictEqual(m.positions.length / 3, 24);
  assert.strictEqual(m.indices.length, 36);
  assert.strictEqual(patches(m).length, 6);
  for (const x of m.positions) assert.ok(near(Math.abs(x), 0.5));
});

test('cylinder and sphere respect the segment count', () => {
  assert.ok(meshes.cylinder(8).positions.length < meshes.cylinder(24).positions.length);
  assert.ok(meshes.sphere(8).positions.length < meshes.sphere(24).positions.length);
  assert.ok(meshes.cylinder(3).indices.length > 0 && meshes.sphere(3).indices.length > 0);
});

test('cylinder has a side and two caps; sphere points lie on the sphere', () => {
  assert.strictEqual(patches(meshes.cylinder()).length, 3);
  const s = meshes.sphere();
  for (let i = 0; i < s.positions.length / 3; i++) assert.ok(near(len(v3(s.positions, i)), 0.5));
});

test('get() returns a mesh by name and rejects unknown names', () => {
  for (const name of meshes.NAMES) assert.ok(meshes.get(name).indices.length > 0);
  assert.deepStrictEqual(meshes.NAMES, ['cube', 'cylinder', 'sphere', 'tank']);
  assert.throws(() => meshes.get('teapot'), /Невідома модель/);
});

// ---- Танк: корпус 1.6x0.5x1.0, башта 0.8x0.35x0.7, ствол довжиною 1.0 вперед (+x) ----
test('tank: hull, turret and barrel dimensions', () => {
  const m = meshes.tank(), n = m.positions.length / 3;
  assert.ok(n < 65536);
  const pts = Array.from({ length: n }, (_, i) => ({ p: v3(m.positions, i), n: v3(m.normals, i) }));
  const has = (x, y, z) => pts.some(q => near(q.p[0], x) && near(q.p[1], y) && near(q.p[2], z));
  // корпус: усі 8 кутів
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) for (const sz of [-1, 1]) assert.ok(has(0.8 * sx, 0.25 * sy, 0.5 * sz), 'кут корпусу');
  // башта: верхня грань на корпусі, розміри 0.8x0.7 на висоті 0.25+0.35
  const top = pts.filter(q => near(q.n[1], 1) && near(q.p[1], 0.6));
  assert.ok(top.length >= 4);
  const ext = k => Math.max(...top.map(q => q.p[k])) - Math.min(...top.map(q => q.p[k]));
  assert.ok(near(ext(0), 0.8) && near(ext(2), 0.7), `верх башти ${ext(0)}x${ext(2)}`);
  assert.ok(pts.some(q => near(q.p[1], 0.25) && near(q.n[1], -1)), 'низ башти лежить на верху корпусу');
  // ствол: кришка на передньому кінці (нормаль +x) далі за корпус, а на відстані 1.0 назад — кришка з нормаллю -x
  const maxX = Math.max(...pts.map(q => q.p[0]));
  assert.ok(maxX > 0.8, 'ствол виступає вперед за корпус');
  const far = pts.filter(q => near(q.n[0], 1) && near(q.p[0], maxX));
  assert.ok(far.length >= 3);
  const c = [0, 1, 2].map(k => far.reduce((s, q) => s + q.p[k], 0) / far.length);
  assert.ok(pts.some(q => near(q.n[0], -1) && near(q.p[0], c[0] - 1.0) && near(q.p[1], c[1]) && near(q.p[2], c[2])), 'довжина ствола 1.0');
});
