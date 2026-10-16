(function (RA3) { 'use strict';

  // Процедурні меші для 3D-прев'ю. Чисті функції, без WebGL.
  //
  // Конвенція UV і тангентного базису (одна для всіх мешів, перевіряється тестом):
  //   * рядок 0 текстури — верх зображення і вантажиться без UNPACK_FLIP_Y, тож v росте ДОНИЗУ;
  //   * зображення на кожній грані НЕ дзеркальне, якщо дивитися ззовні: u росте вправо, v росте донизу;
  //   * тангент T = dP/du (вісь +x зображення);
  //   * бітангент cross(T, N) = dP/dv (вісь «вниз по зображенню»), для кожного трикутника
  //     (для зовнішньої нормалі трійка T, dP/dv, N ліва, бо зображення дивиться на глядача);
  //   * трикутники обходяться проти годинникової стрілки, якщо дивитися ззовні (відсікання задніх граней).
  // Звідси: tbn-нормаль = T·nx + cross(T,N)·ny + N·nz, де ny — у конвенції «y вниз по зображенню».
  // Орієнтація «вгору» на гранях: бокові грані й бік циліндра — світове +y; верх і низ коробки — +x (вперед у танка);
  // кришки циліндра — -x (після повороту ствола танка це +y, тож дульний зріз стоїть «прямо»); сфера — +y.

  const TWO_PI = Math.PI * 2;
  const MAX_VERTICES = 65536; // Uint16-індекси

  const NAMES = ['cube', 'cylinder', 'sphere', 'tank'];

  // ---------- Геометрія як прості масиви (до остаточного перетворення в типізовані) ----------

  function geom() { return { pos: [], nrm: [], tan: [], uv: [], idx: [] }; }

  function vertex(g, p, n, t, u, v) {
    g.pos.push(p[0], p[1], p[2]); g.nrm.push(n[0], n[1], n[2]); g.tan.push(t[0], t[1], t[2]); g.uv.push(u, v);
    return g.pos.length / 3 - 1;
  }

  function tri(g, a, b, c) { g.idx.push(a, b, c); }

  function cross(a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; }

  // Плоска грань: центр c, зовнішня нормаль n, напрям u (одиничний тангент t) завширшки w,
  // напрям v = cross(t, n) завдовжки h. Кути (u,v) = (0,0) вгорі ліворуч, (1,0), (1,1), (0,1) внизу ліворуч,
  // якщо дивитися ззовні. Обхід проти годинникової стрілки: верх-ліво, низ-ліво, низ-право, верх-право.
  function addQuad(g, c, n, t, w, h) {
    const b = cross(t, n);
    const corner = (su, sv) => [
      c[0] + t[0] * w * (su - 0.5) + b[0] * h * (sv - 0.5),
      c[1] + t[1] * w * (su - 0.5) + b[1] * h * (sv - 0.5),
      c[2] + t[2] * w * (su - 0.5) + b[2] * h * (sv - 0.5)
    ];
    const i0 = vertex(g, corner(0, 0), n, t, 0, 0), i1 = vertex(g, corner(1, 0), n, t, 1, 0);
    const i2 = vertex(g, corner(1, 1), n, t, 1, 1), i3 = vertex(g, corner(0, 1), n, t, 0, 1);
    tri(g, i0, i3, i2); tri(g, i0, i2, i1);
  }

  // Паралелепіпед sx × sy × sz з центром у `at`: 6 окремих граней по 4 вершини, UV кожної грані 0..1.
  function box(sx, sy, sz, at) {
    const g = geom(), hx = sx / 2, hy = sy / 2, hz = sz / 2;
    // Бокові грані: «вгору» = +y, праворуч для глядача ззовні = cross(y, n). Верх і низ: «вгору» = +x.
    addQuad(g, [at[0], at[1], at[2] + hz], [0, 0, 1], [1, 0, 0], sx, sy);
    addQuad(g, [at[0], at[1], at[2] - hz], [0, 0, -1], [-1, 0, 0], sx, sy);
    addQuad(g, [at[0] + hx, at[1], at[2]], [1, 0, 0], [0, 0, -1], sz, sy);
    addQuad(g, [at[0] - hx, at[1], at[2]], [-1, 0, 0], [0, 0, 1], sz, sy);
    addQuad(g, [at[0], at[1] + hy, at[2]], [0, 1, 0], [0, 0, 1], sz, sx);
    addQuad(g, [at[0], at[1] - hy, at[2]], [0, -1, 0], [0, 0, -1], sz, sx);
    return g;
  }

  // Циліндр з віссю y: радіус r, висота h (від -h/2 до h/2), seg сегментів.
  // Бік: u іде по колу (φ = 2π·u, у бік -z від +x, тобто вправо для глядача ззовні), v донизу від верху (y = +h/2).
  // Кришки: плоска проєкція кола на квадрат UV 0..1, «вгору» = -x; тангент (0,0,-sign), v донизу = +x.
  function cylinderGeom(seg, r, h) {
    seg = Math.max(3, seg | 0);
    const g = geom(), hh = h / 2;
    const top = [], bot = [];
    for (let i = 0; i <= seg; i++) {
      const phi = TWO_PI * i / seg, c = Math.cos(phi), s = Math.sin(phi), n = [c, 0, -s], t = [-s, 0, -c];
      top.push(vertex(g, [r * c, hh, -r * s], n, t, i / seg, 0));
      bot.push(vertex(g, [r * c, -hh, -r * s], n, t, i / seg, 1));
    }
    for (let i = 0; i < seg; i++) {
      tri(g, top[i], bot[i], bot[i + 1]); tri(g, top[i], bot[i + 1], top[i + 1]);
    }
    for (const sign of [1, -1]) { // +1: верхня кришка (N = +y), -1: нижня (N = -y)
      const n = [0, sign, 0], t = [0, 0, -sign], y = sign * hh;
      const centre = vertex(g, [0, y, 0], n, t, 0.5, 0.5), ring = [];
      for (let i = 0; i < seg; i++) {
        const phi = TWO_PI * i / seg, c = Math.cos(phi), s = Math.sin(phi);
        ring.push(vertex(g, [r * c, y, r * s], n, t, 0.5 - sign * 0.5 * s, 0.5 + 0.5 * c));
      }
      for (let i = 0; i < seg; i++) {
        const a = ring[i], b = ring[(i + 1) % seg];
        if (sign > 0) tri(g, centre, b, a); else tri(g, centre, a, b);
      }
    }
    return g;
  }

  // Сфера радіуса r: u — довгота (φ = 2π·u, від +x у бік -z, тобто вправо для глядача ззовні),
  // v — широта донизу від північного полюса.
  // Кожен трикутник біля полюса має власну вершину полюса з u посередині сегмента, тож UV не вироджені,
  // а нормаль (0,±1,0) і тангент (-sinφ, 0, -cosφ) визначені аналітично.
  function sphereGeom(seg, r) {
    seg = Math.max(3, seg | 0);
    const rings = Math.max(2, Math.round(seg / 2)), g = geom();
    const rows = [];
    for (let j = 0; j <= rings; j++) {
      const theta = Math.PI * j / rings, st = Math.sin(theta), ct = Math.cos(theta), row = [];
      const pole = j === 0 || j === rings;
      for (let i = 0; i < (pole ? seg : seg + 1); i++) {
        const u = (i + (pole ? 0.5 : 0)) / seg, phi = TWO_PI * u, c = Math.cos(phi), s = Math.sin(phi);
        const n = pole ? [0, ct, 0] : [c * st, ct, -s * st];
        row.push(vertex(g, [r * n[0], r * n[1], r * n[2]], n, [-s, 0, -c], u, j / rings));
      }
      rows.push(row);
    }
    for (let i = 0; i < seg; i++) {
      tri(g, rows[0][i], rows[1][i], rows[1][i + 1]);
      tri(g, rows[rings][i], rows[rings - 1][i + 1], rows[rings - 1][i]);
    }
    for (let j = 1; j < rings - 1; j++) {
      for (let i = 0; i < seg; i++) {
        const a = rows[j][i], b = rows[j][i + 1], c = rows[j + 1][i + 1], d = rows[j + 1][i];
        tri(g, a, d, c); tri(g, a, c, b);
      }
    }
    return g;
  }

  // Поворот на -90° навколо z: (x,y,z) -> (y,-x,z). Вісь y переходить у +x; це власний поворот (det = +1),
  // тож інваріанти T = dP/du, cross(T,N) = dP/dv, відсутність дзеркальності й обхід трикутників зберігаються.
  function axisYToX(g, at) {
    const out = geom();
    for (let i = 0; i < g.pos.length; i += 3) {
      out.pos.push(g.pos[i + 1] + at[0], -g.pos[i] + at[1], g.pos[i + 2] + at[2]);
      out.nrm.push(g.nrm[i + 1], -g.nrm[i], g.nrm[i + 2]);
      out.tan.push(g.tan[i + 1], -g.tan[i], g.tan[i + 2]);
    }
    out.uv = g.uv.slice(); out.idx = g.idx.slice();
    return out;
  }

  function merge(list) {
    const out = geom();
    for (const g of list) {
      const base = out.pos.length / 3;
      for (const k of ['pos', 'nrm', 'tan', 'uv']) for (const x of g[k]) out[k].push(x);
      for (const i of g.idx) out.idx.push(i + base);
    }
    return out;
  }

  function finish(g) {
    const n = g.pos.length / 3;
    if (n >= MAX_VERTICES) throw new Error('Забагато вершин для 16-бітних індексів: ' + n);
    return {
      positions: new Float32Array(g.pos), normals: new Float32Array(g.nrm), tangents: new Float32Array(g.tan),
      uvs: new Float32Array(g.uv), indices: new Uint16Array(g.idx)
    };
  }

  // ---------- Публічні меші ----------

  function cube() { return finish(box(1, 1, 1, [0, 0, 0])); }

  function cylinder(seg) { return finish(cylinderGeom(seg === undefined ? 24 : seg, 0.5, 1)); }

  function sphere(seg) { return finish(sphereGeom(seg === undefined ? 24 : seg, 0.5)); }

  // Танк (вперед = +x): корпус 1.6×0.5×1.0 з центром у початку координат; башта 0.8×0.35×0.7 зверху,
  // трохи позаду центру; ствол — циліндр завдовжки 1.0 від передньої грані башти вперед.
  function tank() {
    const hull = box(1.6, 0.5, 1.0, [0, 0, 0]);
    const turretX = -0.1, turretY = 0.25 + 0.35 / 2;
    const turret = box(0.8, 0.35, 0.7, [turretX, turretY, 0]);
    const barrelStart = turretX + 0.8 / 2, barrelLen = 1.0;
    const barrel = axisYToX(cylinderGeom(16, 0.05, barrelLen), [barrelStart + barrelLen / 2, turretY, 0]);
    return finish(merge([hull, turret, barrel]));
  }

  function get(name) {
    switch (name) {
      case 'cube': return cube();
      case 'cylinder': return cylinder();
      case 'sphere': return sphere();
      case 'tank': return tank();
      default: throw new Error('Невідома модель: ' + name);
    }
  }

  const api = { NAMES, cube, cylinder, sphere, tank, get };
  RA3.meshes = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
