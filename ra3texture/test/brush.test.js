const test = require('node:test'); const assert = require('node:assert');
const brush = require('../js/brush.js');

const near = (a, b, eps, msg) => assert.ok(Math.abs(a - b) <= eps, (msg || '') + ' очікувалось ' + b + ', отримано ' + a);
const sum = (arr) => { let s = 0; for (const v of arr) s += v; return s; };
const at = (dab, x, y) => dab.data[y * dab.w + x];

// ----------------------------------------------------------------- ALPHAS

test('ALPHAS: усі очікувані id, українські підписи, gen у [0,1] і не порожній', () => {
  const ids = brush.ALPHAS.map(a => a.id);
  assert.deepStrictEqual(ids, ['round', 'square', 'grunge', 'splatter', 'scratches', 'chips', 'rivet', 'star', 'hazard', 'camo']);
  for (const a of brush.ALPHAS) {
    assert.ok(/[а-яіїєґ]/i.test(a.label), 'підпис українською: ' + a.id);
    for (const res of [64, 128]) {
      const d = a.gen(res);
      assert.ok(d instanceof Float32Array, a.id);
      assert.strictEqual(d.length, res * res, a.id);
      let mx = 0;
      for (let i = 0; i < d.length; i++) { assert.ok(d[i] >= 0 && d[i] <= 1, a.id + ' поза [0,1]: ' + d[i]); if (d[i] > mx) mx = d[i]; }
      assert.ok(mx > 0.5, a.id + ' має бути непорожнім (max=' + mx + ')');
      assert.ok(sum(d) > 10, a.id + ' має мати помітну площу');
    }
  }
});

test('ALPHAS: gen детермінований', () => {
  for (const a of brush.ALPHAS) assert.deepStrictEqual(a.gen(64), a.gen(64), a.id);
});

test('ALPHAS: характерні форми (зірка симетрична, квадрат суцільний, смуги чергуються)', () => {
  const get = (id) => brush.ALPHAS.find(a => a.id === id);
  const sq = get('square').gen(64);
  assert.ok(sq[32 * 64 + 32] === 1 && sq[2 * 64 + 2] === 0); // квадрат вписаний у коло: кути поза межею
  // усі альфи вміщуються в коло діаметра 1 (інакше за обертання штамп обрізається)
  for (const a of brush.ALPHAS) {
    const d = a.gen(96);
    for (let y = 0; y < 96; y++) for (let x = 0; x < 96; x++) {
      const r = Math.hypot((x + 0.5) / 48 - 1, (y + 0.5) / 48 - 1);
      if (r > 1.02) assert.ok(d[y * 96 + x] < 0.02, a.id + ' виходить за коло: r=' + r.toFixed(2) + ' v=' + d[y * 96 + x]);
    }
  }
  const star = get('star').gen(128);
  // центр заповнений, кути порожні, дзеркальна симетрія відносно вертикалі
  assert.ok(star[64 * 128 + 64] > 0.9); assert.ok(star[2 * 128 + 2] < 0.01);
  for (let y = 0; y < 128; y += 7) for (let x = 0; x < 128; x += 5) near(star[y * 128 + x], star[y * 128 + (127 - x)], 0.06, 'star mirror');
  const hz = get('hazard').gen(128);
  let sw = 0; for (let x = 1; x < 128; x++) if ((hz[64 * 128 + x] > 0.5) !== (hz[64 * 128 + x - 1] > 0.5)) sw++;
  assert.ok(sw >= 6, 'смуги: перемикань ' + sw);
  const rivet = get('rivet').gen(64);
  assert.ok(rivet[32 * 64 + 32] > rivet[32 * 64 + 14], 'заклепка: центр яскравіший за край купола');
});

// ----------------------------------------------------------------- getDab

test('getDab: розміри w = h = ceil(size) + 2 (мін. 1)', () => {
  for (const s of [1, 5.2, 20, 20.01, 33.7, 64]) {
    const d = brush.getDab('square', s, 0, 0.8);
    assert.strictEqual(d.w, Math.ceil(s) + 2, 'w для ' + s);
    assert.strictEqual(d.h, d.w);
    assert.strictEqual(d.data.length, d.w * d.h);
  }
  const tiny = brush.getDab('round', 0.1);
  assert.ok(tiny.w >= 1 && tiny.data.length === tiny.w * tiny.h);
  for (const v of brush.getDab('grunge', 40, 0.7).data) assert.ok(v >= 0 && v <= 1);
});

test('getDab: кеш повертає той самий об\'єкт, різні аргументи — різні', () => {
  brush.clearCache();
  const a = brush.getDab('star', 30, 0.5, 0.8);
  assert.strictEqual(brush.getDab('star', 30, 0.5, 0.8), a);
  assert.notStrictEqual(brush.getDab('star', 31, 0.5, 0.8), a);
  assert.notStrictEqual(brush.getDab('star', 30, 1.5, 0.8), a);
  assert.notStrictEqual(brush.getDab('hazard', 30, 0.5, 0.8), a);
  // hardness впливає лише на круг
  assert.strictEqual(brush.getDab('star', 30, 0.5, 0.1), a);
  const r1 = brush.getDab('round', 30, 0, 0.2), r2 = brush.getDab('round', 30, 0, 0.9);
  assert.notStrictEqual(r1, r2);
  // кут кола не має значення
  assert.strictEqual(brush.getDab('round', 30, 1.3, 0.2), r1);
});

test('getDab: LRU витісняє найстаріші (≤ 64 записів), недавні лишаються', () => {
  brush.clearCache();
  const first = brush.getDab('square', 10, 0, 0.8);
  const keep = brush.getDab('square', 11, 0, 0.8);
  for (let i = 0; i < 70; i++) { brush.getDab('square', 12 + i, 0, 0.8); brush.getDab('square', 11, 0, 0.8); }
  assert.strictEqual(brush.getDab('square', 11, 0, 0.8), keep, 'часто використаний лишається');
  assert.notStrictEqual(brush.getDab('square', 10, 0, 0.8), first, 'найстаріший витіснено');
});

test('getDab: круг hardness 1 — різкий край, hardness 0 — м\'який', () => {
  const size = 40;
  const hard = brush.getDab('round', size, 0, 1), soft = brush.getDab('round', size, 0, 0);
  const c = hard.w / 2;
  const radial = (dab, rr) => at(dab, Math.floor(c + rr), Math.floor(c));
  assert.ok(radial(hard, 17) > 0.99, 'всередині = 1');
  assert.ok(radial(hard, 20.6) < 0.01, 'зовні = 0');
  const mid = (dab) => { let n = 0; for (const v of dab.data) if (v > 0.05 && v < 0.95) n++; return n; };
  assert.ok(mid(hard) < mid(soft) * 0.25, 'перехідних пікселів: hard ' + mid(hard) + ' vs soft ' + mid(soft));
  assert.ok(radial(soft, 10) < 0.7, 'м\'який круг уже на пів-радіусі напівпрозорий');
  assert.ok(at(soft, Math.floor(c), Math.floor(c)) > 0.95, 'центр м\'якого круга повний');
  // центр не залежить від країв; сума площі hard > soft
  assert.ok(sum(hard.data) > sum(soft.data));
  // площа жорсткого круга ≈ π r²
  near(sum(hard.data), Math.PI * 20 * 20, 25, 'площа');
});

test('getDab: обертання квадрата на 45° змінює штамп і не обрізає кути', () => {
  const s0 = brush.getDab('square', 40, 0, 0.8), s45 = brush.getDab('square', 40, Math.PI / 4, 0.8);
  assert.notDeepStrictEqual(s0.data, s45.data);
  const c = s0.w / 2; // центр штампа; сторона квадрата = 0.7·40 = 28 (вписаний у коло діаметра 40)
  const px = (d, dx, dy) => at(d, Math.floor(c + dx), Math.floor(c + dy));
  assert.ok(px(s0, 12.5, 12.5) > 0.99 && px(s45, 12.5, 12.5) < 0.01, 'кут квадрата без обертання vs порожньо в повернутого');
  assert.ok(px(s0, 17.5, 0.5) < 0.01 && px(s45, 17.5, 0.5) > 0.99, 'вістря ромба по осі лише в повернутого');
  // площа зберігається при обертанні (≈ 28²): кути не обрізані краєм штампа
  near(sum(s0.data), 784, 25, 'площа 0°'); near(sum(s45.data), 784, 25, 'площа 45°');
});

test('getDab: обертання на 180° перевертає зірку', () => {
  const a = brush.getDab('star', 48, 0, 0.8), b = brush.getDab('star', 48, Math.PI, 0.8);
  // зірка догори vs зірка догори дриґом: верхній центральний промінь стає нижнім
  const cx = Math.floor(a.w / 2);
  assert.ok(at(a, cx, 12) > 0.5 && at(b, cx, 12) < 0.5);
  assert.ok(at(b, cx, a.h - 13) > 0.5, 'верхній промінь став нижнім');
});

test('getDab: некоректні вхідні дані дають зрозумілу помилку', () => {
  assert.throws(() => brush.getDab('nope', 10), /Невідома альфа/);
  assert.throws(() => brush.getDab('square', 0), /розмір/);
  assert.throws(() => brush.getDab('square', NaN), /розмір/);
  assert.throws(() => brush.getDab({}, 10), /альфа/);
});

// ----------------------------------------------------------------- alphaFromImage

test('alphaFromImage: білий квадрат на чорному → альфа з білим центром', () => {
  const w = 16, h = 16, img = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < w * h; i++) img[i * 4 + 3] = 255;
  for (let y = 4; y < 12; y++) for (let x = 4; x < 12; x++) { const p = (y * w + x) * 4; img[p] = img[p + 1] = img[p + 2] = 255; }
  const a = brush.alphaFromImage(img, w, h);
  assert.ok(/^custom-/.test(a.id)); assert.strictEqual(typeof a.label, 'string');
  assert.strictEqual(a.data.length, a.res * a.res);
  const c = a.res >> 1;
  assert.ok(a.data[c * a.res + c] > 0.99, 'центр білий');
  assert.ok(a.data[2 * a.res + 2] < 0.01, 'кут чорний');
  near(sum(a.data) / a.data.length, 0.25, 0.03, 'частка білого');
  for (const v of a.data) assert.ok(v >= 0 && v <= 1);
  // той самий вміст → той самий id; інший → інший
  assert.strictEqual(brush.alphaFromImage(img, w, h).id, a.id);
  const img2 = img.slice(); img2[(8 * w + 8) * 4] = 0; img2[(8 * w + 8) * 4 + 1] = 0; img2[(8 * w + 8) * 4 + 2] = 0;
  assert.notStrictEqual(brush.alphaFromImage(img2, w, h).id, a.id);
  // придатна для getDab
  const dab = brush.getDab(a, 30, 0.3, 0.8);
  assert.strictEqual(dab.w, 32); assert.ok(sum(dab.data) > 100);
  assert.strictEqual(brush.getDab(a, 30, 0.3, 0.8), dab, 'кеш для власних альф');
});

test('alphaFromImage: враховує альфа-канал, вписує не квадрат по центру', () => {
  const w = 20, h = 10, img = new Uint8ClampedArray(w * h * 4);
  for (let i = 0; i < w * h; i++) { img[i * 4] = img[i * 4 + 1] = img[i * 4 + 2] = 255; img[i * 4 + 3] = 255; }
  // права половина повністю прозора
  for (let y = 0; y < h; y++) for (let x = 10; x < w; x++) img[(y * w + x) * 4 + 3] = 0;
  const a = brush.alphaFromImage(img, w, h), r = a.res;
  assert.ok(a.data[(r >> 1) * r + 20] > 0.99, 'ліва частина непрозора');
  assert.ok(a.data[(r >> 1) * r + (r - 20)] < 0.01, 'права прозора');
  assert.ok(a.data[3 * r + 20] < 0.01, 'поля зверху/знизу порожні (вписано по центру)');
  assert.throws(() => brush.alphaFromImage(new Uint8Array(10), 4, 4), /Розмір буфера/);
});

// ----------------------------------------------------------------- createStroke

const run = (st, pts) => { const out = st.begin(pts[0][0], pts[0][1], 1); for (let i = 1; i < pts.length; i++) out.push(...st.move(pts[i][0], pts[i][1], 1)); out.push(...st.end()); return out; };

test('createStroke: begin видає перший мазок із параметрами за замовчуванням', () => {
  const s = brush.createStroke({ size: 20, flow: 0.6, angle: 0.3 });
  const d = s.begin(10, 20);
  assert.strictEqual(d.length, 1);
  assert.deepStrictEqual(d[0], { x: 10, y: 20, size: 20, angle: 0.3, flow: 0.6 });
});

test('createStroke: 100 px, size 20, spacing 0.25 → ~20 мазків рівномірно', () => {
  const s = brush.createStroke({ size: 20, spacing: 0.25 });
  const first = s.begin(0, 50);
  const dabs = s.move(100, 50);
  assert.strictEqual(first.length, 1);
  assert.ok(Math.abs(dabs.length - 20) <= 1, 'мазків: ' + dabs.length);
  let prev = first[0].x;
  for (const d of dabs) { near(d.x - prev, 5, 1e-6, 'крок'); near(d.y, 50, 1e-9); prev = d.x; }
});

test('createStroke: залишок переноситься між дрібними рухами (така сама кількість)', () => {
  const big = brush.createStroke({ size: 20, spacing: 0.25 }); big.begin(0, 0);
  const nBig = big.move(100, 0).length;
  const small = brush.createStroke({ size: 20, spacing: 0.25 }); small.begin(0, 0);
  let nSmall = 0; const xs = [];
  for (let i = 1; i <= 333; i++) for (const d of small.move(i * 0.3003, 0)) { nSmall++; xs.push(d.x); }
  assert.ok(Math.abs(nSmall - nBig) <= 1, 'малі кроки: ' + nSmall + ', великий: ' + nBig);
  for (let i = 1; i < xs.length; i++) near(xs[i] - xs[i - 1], 5, 1e-6, 'рівномірно');
  // рух по ламаній: відстань між мазками рахується по довжині дуги, а не по хордах
  const poly = brush.createStroke({ size: 20, spacing: 0.5 }); // крок 10
  const out = run(poly, [[0, 0], [30, 0], [30, 30]]);
  assert.strictEqual(out.length, 7); // 0,10,20,30(по x) та 10,20,30 по y → 1 + 6
  near(out[4].x, 30, 1e-6); near(out[4].y, 10, 1e-6);
});

test('createStroke: мінімальний крок 0.5 px', () => {
  const s = brush.createStroke({ size: 2, spacing: 0.01 }); s.begin(0, 0);
  const n = s.move(10, 0).length;
  assert.ok(n <= 21, 'мазків ' + n);
});

test('createStroke: нульовий рух не дає мазків, нескінченні координати ігноруються', () => {
  const s = brush.createStroke({ size: 20 }); s.begin(5, 5);
  assert.deepStrictEqual(s.move(5, 5), []);
  assert.deepStrictEqual(s.move(NaN, 3), []);
  assert.ok(s.move(50, 5).length > 0);
});

test('createStroke: symmetryX додає дзеркальний мазок на docSize − x, кут дзеркалиться', () => {
  const s = brush.createStroke({ size: 20, spacing: 0.25, symmetryX: true, docSize: 512, angle: 0.4 });
  const first = s.begin(100, 50);
  assert.strictEqual(first.length, 2);
  assert.strictEqual(first[1].x, 412); assert.strictEqual(first[1].y, 50);
  near(first[1].angle, Math.PI - 0.4, 1e-12);
  const moved = s.move(150, 50);
  assert.strictEqual(moved.length % 2, 0);
  for (let i = 0; i < moved.length; i += 2) {
    near(moved[i + 1].x, 512 - moved[i].x, 1e-9); near(moved[i + 1].y, moved[i].y, 1e-9);
    assert.strictEqual(moved[i + 1].size, moved[i].size); assert.strictEqual(moved[i + 1].flow, moved[i].flow);
  }
});

test('createStroke: symmetryY та обидві симетрії', () => {
  const y = brush.createStroke({ size: 10, symmetryY: true, docSize: 256, angle: 0.5 }).begin(30, 40);
  assert.strictEqual(y.length, 2); assert.strictEqual(y[1].x, 30); assert.strictEqual(y[1].y, 216); near(y[1].angle, -0.5, 1e-12);
  const xy = brush.createStroke({ size: 10, symmetryX: true, symmetryY: true, docSize: 256, angle: 0.5 }).begin(30, 40);
  assert.strictEqual(xy.length, 4);
  const pts = xy.map(d => d.x + ',' + d.y).sort();
  assert.deepStrictEqual(pts, ['226,216', '226,40', '30,216', '30,40'].sort());
  near(xy[3].angle, Math.PI + 0.5, 1e-12);
  assert.throws(() => brush.createStroke({ symmetryX: true }), /docSize/);
});

test('createStroke: followPath — кут ≈ напрямок руху (+ angle)', () => {
  for (const [x1, y1] of [[100, 100], [-60, 0], [0, -80], [40, -30]]) {
    const s = brush.createStroke({ size: 10, spacing: 0.5, followPath: true });
    s.begin(0, 0);
    const d = s.move(x1, y1);
    assert.ok(d.length > 0);
    for (const m of d) near(m.angle, Math.atan2(y1, x1), 1e-9, 'кут для (' + x1 + ',' + y1 + ')');
  }
  const s = brush.createStroke({ size: 10, spacing: 0.5, followPath: true, angle: 0.25 });
  s.begin(0, 0);
  for (const m of s.move(50, 0)) near(m.angle, 0.25, 1e-9);
  // поворот напрямку посеред штриха
  const t = brush.createStroke({ size: 10, spacing: 0.5, followPath: true }); t.begin(0, 0); t.move(30, 0);
  const turned = t.move(30, 30);
  for (const m of turned) near(m.angle, Math.PI / 2, 1e-9);
});

test('createStroke: детермінований jitter за seed, межі jitter дотримані', () => {
  const opts = { size: 30, spacing: 0.2, sizeJitter: 0.5, angleJitter: 0.5, scatter: 0.3, seed: 42 };
  const pts = [[0, 0], [200, 30], [300, 200]];
  const a = run(brush.createStroke(opts), pts), b = run(brush.createStroke(opts), pts);
  assert.deepStrictEqual(a, b);
  const c = run(brush.createStroke({ ...opts, seed: 43 }), pts);
  assert.notDeepStrictEqual(a, c);
  assert.ok(a.length > 20);
  for (const d of a) {
    assert.ok(d.size <= 30 + 1e-9 && d.size >= 15 - 1e-9, 'size ' + d.size);
    assert.ok(Math.abs(d.angle) <= Math.PI * 0.5 + 1e-9, 'angle ' + d.angle);
  }
  assert.ok(new Set(a.map(d => d.size.toFixed(4))).size > 10, 'розмір справді варіюється');
  // scatter зміщує мазки від осі руху не далі scatter·size
  const sc = run(brush.createStroke({ size: 20, spacing: 0.25, scatter: 0.5, seed: 3 }), [[0, 100], [200, 100]]);
  let off = 0; for (const d of sc) { off = Math.max(off, Math.abs(d.y - 100)); assert.ok(Math.abs(d.y - 100) <= 10 + 1e-9); }
  assert.ok(off > 1, 'scatter працює');
  // без jitter/scatter нічого не змінюється
  for (const d of run(brush.createStroke({ size: 20, seed: 9 }), [[0, 0], [100, 0]])) { assert.strictEqual(d.size, 20); assert.strictEqual(d.angle, 0); assert.strictEqual(d.y, 0); }
});

test('createStroke: pressureSize / pressureFlow', () => {
  const s = brush.createStroke({ size: 40, flow: 0.8, spacing: 0.25, pressureSize: true, pressureFlow: true });
  const first = s.begin(0, 0, 0.5);
  near(first[0].size, 20, 1e-9); near(first[0].flow, 0.4, 1e-9);
  const d = s.move(200, 0, 1);
  assert.ok(d.length > 5);
  for (let i = 1; i < d.length; i++) assert.ok(d[i].size >= d[i - 1].size - 1e-9, 'розмір росте з тиском');
  near(d[d.length - 1].flow, 0.8, 0.01);
  // без прапорців тиск ігнорується
  const plain = brush.createStroke({ size: 40, flow: 0.8 }).begin(0, 0, 0.1);
  assert.strictEqual(plain[0].size, 40); assert.strictEqual(plain[0].flow, 0.8);
});

test('createStroke: move без begin починає штрих, end скидає стан', () => {
  const s = brush.createStroke({ size: 10 });
  assert.strictEqual(s.move(5, 5).length, 1);
  assert.deepStrictEqual(s.end(), []);
  assert.strictEqual(s.move(50, 50).length, 1);
});

// ----------------------------------------------------------------- line

test('line: прямі мазки від початку до кінця з рівним кроком', () => {
  const d = brush.line({ size: 20, spacing: 0.25 }, 10, 10, 110, 10);
  assert.ok(Math.abs(d.length - 21) <= 1, 'мазків ' + d.length);
  near(d[0].x, 10, 1e-9); near(d[d.length - 1].x, 110, 1);
  for (let i = 1; i < d.length; i++) near(d[i].x - d[i - 1].x, 5, 1e-6);
  const diag = brush.line({ size: 10, spacing: 0.5, followPath: true }, 0, 0, 30, 40); // довжина 50, крок 5
  assert.strictEqual(diag.length, 11);
  near(diag[10].x, 30, 1e-6); near(diag[10].y, 40, 1e-6); near(diag[5].angle, Math.atan2(40, 30), 1e-9);
  // з симетрією — по дзеркальному мазку на кожен
  const sym = brush.line({ size: 20, spacing: 0.25, symmetryX: true, docSize: 200 }, 10, 10, 60, 10);
  assert.strictEqual(sym.length % 2, 0);
  assert.strictEqual(brush.line({ size: 10 }, 7, 8, 7, 8).length, 1);
});

// ----------------------------------------------------------------- floodRegion

function twoColour(w, h, fn, a, b) {
  const img = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const c = fn(x, y) ? a : b, p = (y * w + x) * 4;
    img[p] = c[0]; img[p + 1] = c[1]; img[p + 2] = c[2]; img[p + 3] = 255;
  }
  return img;
}

test('floodRegion: двоколірне зображення — заливає рівно одну область', () => {
  const n = 8, RED = [200, 20, 20], BLUE = [20, 20, 200];
  const img = twoColour(n, n, (x) => x < 4, RED, BLUE);
  const m = brush.floodRegion(img, n, 1, 3, 10);
  assert.ok(m instanceof Uint8Array); assert.strictEqual(m.length, n * n);
  let count = 0;
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) { const v = m[y * n + x]; assert.strictEqual(v, x < 4 ? 255 : 0, x + ',' + y); if (v) count++; }
  assert.strictEqual(count, 32);
  const m2 = brush.floodRegion(img, n, 6, 0, 10);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) assert.strictEqual(m2[y * n + x], x >= 4 ? 255 : 0);
});

test('floodRegion: не заливає розрізнені області того самого кольору', () => {
  const n = 9, A = [255, 255, 255], B = [0, 0, 0];
  // дві білі вертикальні смуги x=2 та x=6, розділені чорним; з'єднання через край блокує чорна смуга x=0..1 й x=8 (n=9: 0,1 чорні; 7,8 чорні)
  const img = twoColour(n, n, (x) => x === 2 || x === 5, A, B);
  const m = brush.floodRegion(img, n, 2, 4, 0);
  let cnt = 0; for (let i = 0; i < n * n; i++) if (m[i]) cnt++;
  assert.strictEqual(cnt, n); assert.strictEqual(m[4 * n + 5], 0);
});

test('floodRegion: загортається через край (текстура тайлиться)', () => {
  const n = 10, X = [10, 200, 10], Y = [90, 90, 90];
  // зелена смуга на x=8,9 та x=0,1 (через шов), решта сіра; і те саме по вертикалі: рядки y=9 та y=0
  const img = twoColour(n, n, (x) => x >= 8 || x <= 1, X, Y);
  const m = brush.floodRegion(img, n, 9, 5, 5);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) assert.strictEqual(m[y * n + x], (x >= 8 || x <= 1) ? 255 : 0, x + ',' + y);
  // вертикальний шов
  const imgV = twoColour(n, n, (x, y) => y >= 9 || y <= 0, X, Y);
  const mv = brush.floodRegion(imgV, n, 4, 0, 5);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) assert.strictEqual(mv[y * n + x], (y >= 9 || y <= 0) ? 255 : 0);
  // діагональне сусідство не з'єднує (4-сусідство)
  const imgD = twoColour(4, 4, (x, y) => x === y, X, Y);
  const md = brush.floodRegion(imgD, 4, 0, 0, 5);
  let c = 0; for (const v of md) if (v) c++;
  assert.strictEqual(c, 1, 'діагональ не з\'єднана 4-сусідством');
});

test('floodRegion: толерантність (відстань до кольору зерна, макс. по каналах)', () => {
  const w = 8, h = 1, img = new Uint8ClampedArray(w * 4);
  for (let x = 0; x < w; x++) { img[x * 4] = x * 10; img[x * 4 + 1] = 100; img[x * 4 + 2] = 100; img[x * 4 + 3] = 255; }
  // градієнт 0,10,...,70 по R; зерно x=0
  const cnt = (m) => { let c = 0; for (const v of m) if (v) c++; return c; };
  assert.strictEqual(cnt(brush.floodRegion(img, { w, h }, 0, 0, 0)), 1);
  assert.strictEqual(cnt(brush.floodRegion(img, { w, h }, 0, 0, 25)), 3, 'x=0,1,2');
  assert.strictEqual(cnt(brush.floodRegion(img, { w, h }, 0, 0, 30)), 4, 'межа включно');
  assert.strictEqual(cnt(brush.floodRegion(img, { w, h }, 0, 0, 255)), 8);
  // типове значення 32
  assert.strictEqual(cnt(brush.floodRegion(img, { w, h }, 0, 0)), 4);
  // різниця по зеленому каналу теж враховується
  const img2 = img.slice(); img2[1 * 4 + 1] = 160;
  assert.strictEqual(cnt(brush.floodRegion(img2, { w, h }, 0, 0, 30)), 1);
  // координати зерна за межами загортаються
  assert.strictEqual(brush.floodRegion(img, { w, h }, w, 0, 0)[0], 255);
});

test('floodRegion: softEdge розмиває межу, не чіпає віддалені точки', () => {
  const n = 32, A = [255, 0, 0], B = [0, 0, 255];
  const img = twoColour(n, n, (x) => x >= 8 && x < 24, A, B);
  const hard = brush.floodRegion(img, n, 16, 16, 10), soft = brush.floodRegion(img, n, 16, 16, 10, { softEdge: 4 });
  assert.strictEqual(soft.length, hard.length);
  const row = (m, y) => Array.from(m.slice(y * n, y * n + n));
  const partial = row(soft, 16).filter(v => v > 0 && v < 255).length;
  assert.ok(partial >= 3, 'перехідних пікселів у рядку: ' + partial);
  assert.strictEqual(soft[16 * n + 16], 255); assert.strictEqual(soft[16 * n + 0], 0);
  assert.ok(row(hard, 16).every(v => v === 0 || v === 255));
  // монотонне спадання назовні від межі
  const edge = row(soft, 16).slice(2, 12); for (let i = 1; i < edge.length; i++) assert.ok(edge[i] >= edge[i - 1]);
  // загортання при розмитті: область на шві розмивається симетрично по обидва боки
  const seam = twoColour(n, n, (x) => x >= 28 || x < 4, A, B);
  const sm = brush.floodRegion(seam, n, 0, 5, 10, { softEdge: 4 });
  assert.ok(sm[5 * n + 30] > 0 && sm[5 * n + 2] > 0); assert.strictEqual(sm[5 * n + 16], 0);
});

test('floodRegion: ітеративна на великій текстурі (без рекурсії), O(n)', () => {
  const n = 1024, img = new Uint8ClampedArray(n * n * 4).fill(128);
  const t0 = Date.now();
  const m = brush.floodRegion(img, n, 500, 500, 0);
  assert.strictEqual(m[0], 255); assert.strictEqual(m[n * n - 1], 255);
  let c = 0; for (let i = 0; i < m.length; i++) if (m[i]) c++;
  assert.strictEqual(c, n * n);
  assert.ok(Date.now() - t0 < 3000, 'повільно: ' + (Date.now() - t0) + ' мс');
  // змійка на тайловій текстурі — найгірший випадок для стека
  const snake = twoColour(257, 257, (x, y) => y % 2 === 0 || (y % 4 === 1 && x === 256) || (y % 4 === 3 && x === 0), [1, 1, 1], [200, 200, 200]);
  const ms = brush.floodRegion(snake, 257, 0, 0, 0);
  assert.ok(ms.some(v => v === 255));
  assert.throws(() => brush.floodRegion(new Uint8Array(10), 4, 0, 0), /Розмір буфера/);
});
