# RA3 Texture Studio: план реалізації

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Браузерна програма без залежностей, що створює скіни юнітів RA3 (`Назва.dds`, `Назва_NRM.dds`, `Назва_SPM.dds`) із процедурних шарів, власних зображень або текстур гри.

**Architecture:** Чистий JS у звичайних `<script>` (працює з `file://`). Ядро (`util`, `dds`, `tga`, `maps`, `generators`, `meshes`, `project`) не торкається DOM і тестується через `node --test`. `preview3d.js` (WebGL) та `app.js` (UI) перевіряються smoke-тестом у Chromium.

**Tech Stack:** HTML/CSS/JS (ES2020), Canvas 2D, WebGL 1, Node 20+ (`node:test`), Playwright (глобально встановлений у контейнері) для smoke-тесту.

**Spec:** `docs/superpowers/specs/2026-10-15-ra3-texture-studio-design.md`

## Global Constraints

- Жодних зовнішніх бібліотек і CDN, сторінка має працювати з `file://` без інтернету.
- Жодних ES-модулів і Web Worker у браузерному коді.
- Кожен файл у `ra3texture/js/` має одну обгортку:
  ```js
  (function (RA3) { 'use strict';
    const util = RA3.util || require('./util.js');   // залежності так само
    const api = { /* … */ };
    RA3.<name> = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
  })(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
  ```
- Тексти інтерфейсу й повідомлення помилок українською, ідентифікатори коду англійською.
- Розмір документа: `256 | 512 | 1024 | 2048`, за замовчуванням `512`. Назва за замовчуванням `AUMyTank`.
- Суфікси файлів: `_NRM`, `_SPM` (великими літерами під час експорту, без урахування регістру під час імпорту).
- SPM: `R = блиск`, `G = 0`, `B = колір гравця`, `A = 255`.
- Шейдер RA3: `lerp(c, c*T*2, spm.b)` у гамма-просторі, гамма `2.2`, `BumpScale = 1.5`, `SpecularColor = 0.8`, alpha-test `96/255`.
- Кольори гравців (8 шт., RGB): Gold 240,215,25 · Red 230,20,20 · Blue 50,75,200 · Green 20,105,60 · Orange 225,115,20 · SkyBlue 102,207,248 · Purple 125,25,200 · Pink 248,152,248.
- NRM за замовчуванням у конвенції DirectX: `G = 0.5 + 0.5·n.y`, де y вниз по зображенню. Інверсія Y дає OpenGL.
- Команда тестів ядра: `node --test ra3texture/test/*.test.js`.

## Review Focus

1. **Неквадратний або не-pow2 імпорт** (PNG 1000×700) масштабується до розміру документа без падіння. Тест додано в Task 4 (`addImageLayer` з 3×5).
2. **Малі mip-рівні 2×2 і 1×1** у DXT, а також DDS з гри з шириною < 4. Блок доповнюється, кодування й декодування не падають. Тест у Task 2.
3. **Великий документ 2048²:** експорт не блокує сторінку, а асинхронний кодер дає ті самі байти, що й синхронний, і викликає прогрес. Тест у Task 2.
4. **Регістр суфіксів у текстурах гри** (`TDirt_nrm.tga`, `X_Spm.DDS`) розпізнається. Тест у Task 4 (`classifyTextureName`).
5. **Кирилиця, пробіли, символи в назві скіна:** імена файлів безпечні для SDK (`[A-Za-z0-9_]`). Тест у Task 4 (`sanitizeName`).

---

### Task 1: Каркас і `util.js`

**Files:**
- Create: `ra3texture/js/util.js`
- Test: `ra3texture/test/util.test.js`

**Interfaces:**
- Produces (`RA3.util`):
  - `clamp(v, lo, hi)`, `lerp(a, b, t)`, `smoothstep(e0, e1, x)`;
  - `isPow2(n) -> boolean`;
  - `mulberry32(seed:number) -> () => number` (рівномірне `[0,1)`);
  - `fbm(seed:number, u:number, v:number, freq:int, octaves:int) -> number` (`[0,1]`, тайлиться по `u,v ∈ [0,1)`, бо частоти цілі й решітка загортається по модулю `freq·2^octave`);
  - `hexToRgb('#rrggbb') -> [r,g,b]`, `rgbToHex([r,g,b]) -> '#rrggbb'`;
  - `luminance(r,g,b) -> number` (`0.299r + 0.587g + 0.114b`);
  - `resampleRGBA(src:Uint8ClampedArray, sw, sh, dw, dh) -> Uint8ClampedArray` (білінійний, з clamp на краях).

- [ ] **Step 1: Написати тести**

```js
const test = require('node:test'); const assert = require('node:assert');
const u = require('../js/util.js');
test('mulberry32 deterministic', () => { const a = u.mulberry32(42), b = u.mulberry32(42);
  for (let i = 0; i < 5; i++) assert.strictEqual(a(), b()); });
test('fbm in range and tileable', () => {
  for (let i = 0; i < 50; i++) { const x = i / 50; const v = u.fbm(7, x, 0.3, 4, 4); assert.ok(v >= 0 && v <= 1); }
  assert.ok(Math.abs(u.fbm(7, 0, 0.3, 4, 4) - u.fbm(7, 1 - 1e-9, 0.3, 4, 4)) < 1e-3); });
test('hex roundtrip', () => { assert.deepStrictEqual(u.hexToRgb('#9fa9b0'), [159, 169, 176]);
  assert.strictEqual(u.rgbToHex([159, 169, 176]), '#9fa9b0'); });
test('isPow2', () => { assert.ok(u.isPow2(512)); assert.ok(!u.isPow2(500)); });
test('resampleRGBA solid stays solid', () => {
  const src = new Uint8ClampedArray(3 * 5 * 4).fill(200);
  const out = u.resampleRGBA(src, 3, 5, 8, 8);
  assert.strictEqual(out.length, 256); assert.ok(out.every(v => v === 200)); });
```

- [ ] **Step 2:** `node --test ra3texture/test/util.test.js` має впасти з `Cannot find module '../js/util.js'`.
- [ ] **Step 3:** Реалізувати `util.js` в обгортці з Global Constraints.
- [ ] **Step 4:** Той самий запуск має пройти (PASS).
- [ ] **Step 5:** `git add ra3texture && git commit -m "RA3 Texture Studio: util module"`.

---

### Task 2: `dds.js`, кодек DDS і mip-рівні

**Files:**
- Create: `ra3texture/js/dds.js`
- Test: `ra3texture/test/dds.test.js`

**Interfaces:**
- Consumes: `RA3.util.clamp`.
- Produces (`RA3.dds`):
  - `buildMips(rgba:Uint8ClampedArray, size:int, opts={normalMap:false}) -> Level[]`, де `Level = {width, height, data:Uint8ClampedArray RGBA}`. Box 2×2 до 1×1. При `normalMap` кожен піксель перенормалізується: `(rgb/255*2-1)` → normalize → назад.
  - `hasAlpha(rgba) -> boolean` (будь-яке A < 255).
  - `encodeDDS(levels:Level[], format:'DXT1'|'DXT5'|'A8R8G8B8') -> Uint8Array`.
  - `encodeDDSAsync(levels, format, onProgress:(0..1)=>void) -> Promise<Uint8Array>` дає ті самі байти, обробляючи порціями по ~64 рядки блоків через `setTimeout(0)`.
  - `decodeDDS(bytes:Uint8Array|ArrayBuffer) -> {width, height, data, format:string, mipCount:int}`, тільки верхній рівень. Підтримує `DXT1` (включно з 3-кольоровим режимом і прозорим індексом 3), `DXT3`, `DXT5`, RGB 32-біт з довільними масками (з альфою й без), RGB 24-біт. Інакше `throw new Error('Непідтримуваний формат DDS: …')`. Якщо magic ≠ `DDS `, то `throw new Error('Це не DDS-файл')`.
- Заголовок: magic `0x20534444`; `dwSize=124`; flags `0x1|0x2|0x4|0x1000|0x20000` плюс `0x80000` (LINEARSIZE) для DXT або `0x8` (PITCH) для A8R8G8B8; `pitchOrLinearSize` = розмір верхнього рівня (DXT) або `width*4`; `mipMapCount = levels.length`; pixel format `dwSize=32`. Для DXT: `DDPF_FOURCC=0x4` і fourCC. Для A8R8G8B8: `0x41`, 32 біти, маски `R 0x00ff0000, G 0x0000ff00, B 0x000000ff, A 0xff000000`. Caps `0x1000|0x400000|0x8` (якщо рівнів > 1, інакше `0x1000`). Розмір DXT-рівня: `max(1,ceil(w/4))*max(1,ceil(h/4))*(8|16)`.
- Кодування блоку (алгоритм, якого не задають тести):
  ```
  колір: середнє μ, коваріація 3×3, головна вісь степеневим методом (8 ітерацій, старт (1,1,1));
         проєкції → min/max; інсет на (max−min)/16; квантування в 565;
         якщо c0 < c1, поміняти місцями (і індекси); якщо c0 == c1, усі індекси 0;
         палітра 4 кольори (2/3·c0+1/3·c1, 1/3·c0+2/3·c1); індекс = найближчий за RGB.
  DXT5 альфа: a0=max, a1=min; якщо рівні, індекси 0; інакше 8-рівневий режим, 3-бітні індекси (48 біт little-endian).
  блоки на краю рівня (< 4 пікселів) доповнюються повтором останнього рядка/стовпця.
  ```

- [ ] **Step 1: Написати тести**

```js
const test = require('node:test'); const assert = require('node:assert');
const dds = require('../js/dds.js');
const solid = (s, rgba) => { const d = new Uint8ClampedArray(s * s * 4); for (let i = 0; i < d.length; i += 4) d.set(rgba, i); return d; };
const u32 = (b, o) => new DataView(b.buffer, b.byteOffset).getUint32(o, true);
test('header fields DXT1 with mips', () => {
  const lv = dds.buildMips(solid(64, [255, 0, 0, 255]), 64); assert.strictEqual(lv.length, 7);
  const b = dds.encodeDDS(lv, 'DXT1');
  assert.strictEqual(String.fromCharCode(...b.slice(0, 4)), 'DDS ');
  assert.strictEqual(u32(b, 4), 124); assert.strictEqual(u32(b, 12), 64); assert.strictEqual(u32(b, 16), 64);
  assert.strictEqual(u32(b, 28), 7); assert.strictEqual(String.fromCharCode(...b.slice(84, 88)), 'DXT1');
  assert.strictEqual(b.length, 128 + 512 + 128 + 32 + 8 + 8 + 8 + 8); });
test('solid color roundtrip exact in 565', () => {
  for (const f of ['DXT1', 'DXT5', 'A8R8G8B8']) {
    const d = dds.decodeDDS(dds.encodeDDS(dds.buildMips(solid(16, [255, 0, 0, 255]), 16), f));
    assert.deepStrictEqual([...d.data.slice(0, 4)], [255, 0, 0, 255], f); assert.strictEqual(d.format, f); } });
test('gradient DXT1 mean error <= 8', () => {
  const s = 64, d = new Uint8ClampedArray(s * s * 4);
  for (let y = 0; y < s; y++) for (let x = 0; x < s; x++) d.set([x * 4, y * 4, 128, 255], (y * s + x) * 4);
  const out = dds.decodeDDS(dds.encodeDDS([{ width: s, height: s, data: d }], 'DXT1')).data;
  let e = 0; for (let i = 0; i < d.length; i++) if (i % 4 !== 3) e += Math.abs(d[i] - out[i]);
  assert.ok(e / (s * s * 3) <= 8); });
test('DXT5 keeps alpha levels', () => {
  const d = solid(8, [10, 20, 30, 255]); for (let i = 3; i < 64; i += 4) d[i] = 0;
  const out = dds.decodeDDS(dds.encodeDDS([{ width: 8, height: 8, data: d }], 'DXT5')).data;
  assert.strictEqual(out[3], 0); assert.strictEqual(out[64 * 4 - 1], 255); assert.ok(dds.hasAlpha(d)); });
test('tiny mips 2x2 and 1x1 encode and decode', () => {
  const lv = dds.buildMips(solid(4, [0, 255, 0, 255]), 4); assert.deepStrictEqual(lv.map(l => l.width), [4, 2, 1]);
  const b = dds.encodeDDS(lv, 'DXT5'); assert.strictEqual(b.length, 128 + 16 * 3);
  const one = dds.decodeDDS(dds.encodeDDS([lv[2]], 'DXT1')); assert.strictEqual(one.width, 1); });
test('normal map mips are renormalized', () => {
  const d = solid(4, [255, 128, 128, 255]); for (let i = 0; i < 32; i += 4) d.set([0, 128, 128, 255], i);
  const lv = dds.buildMips(d, 4, { normalMap: true }); const p = lv[2].data;
  const v = [0, 1, 2].map(k => p[k] / 255 * 2 - 1); assert.ok(Math.abs(Math.hypot(...v) - 1) < 0.05); });
test('async encode equals sync and reports progress', async () => {
  const lv = dds.buildMips(solid(256, [1, 2, 3, 255]), 256); let calls = 0;
  const a = await dds.encodeDDSAsync(lv, 'DXT1', () => calls++);
  assert.deepStrictEqual(a, dds.encodeDDS(lv, 'DXT1')); assert.ok(calls > 0); });
test('rejects non-DDS and unsupported', () => {
  assert.throws(() => dds.decodeDDS(new Uint8Array(128)), /не DDS/);
  const b = dds.encodeDDS([{ width: 4, height: 4, data: solid(4, [0, 0, 0, 255]) }], 'DXT1');
  b.set([65, 84, 73, 50], 84); assert.throws(() => dds.decodeDDS(b), /Непідтримуваний/); });
test('decodes DXT3 and 32-bit BGRX', () => {
  // DXT3 4×4: alpha bytes 0x88 ×8, color [0x00,0xF8,0x00,0xF8,0,0,0,0] → піксель 0 = [255,0,0,136]
  // X8R8G8B8 2×2: pf flags 0x40, bitcount 32, masks R 0xff0000 G 0xff00 B 0xff A 0,
  //   пікселі BGRX [30,20,10,0] → піксель 0 = [10,20,30,255]
});
```

Заголовок для останнього тесту збирається допоміжною функцією `makeHeader({w, h, fourCC|masks, pfFlags})` прямо в тестовому файлі.

- [ ] **Step 2:** `node --test ra3texture/test/dds.test.js` має впасти з `Cannot find module`.
- [ ] **Step 3:** Реалізувати `dds.js` за Interfaces.
- [ ] **Step 4:** Той самий запуск має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: DDS DXT1/DXT5/A8R8G8B8 codec with mipmaps"`.

---

### Task 3: `tga.js`, читання TGA

**Files:**
- Create: `ra3texture/js/tga.js`
- Test: `ra3texture/test/tga.test.js`

**Interfaces:**
- Produces: `RA3.tga.decodeTGA(bytes) -> {width, height, data:RGBA}`. Типи зображення 2 (нестиснений) і 10 (RLE), 24/32 біти, порядок BGR(A). Якщо біт 5 дескриптора = 0, рядки йдуть знизу вгору й перевертаються. Інакше `throw new Error('Непідтримуваний TGA: …')`.

- [ ] **Step 1: Написати тести.** Допоміжна функція `makeTGA(type, bpp, topLeft, w, h, pixelBytes)`. Тести:
  - `uncompressed 32-bit top-left`: 2×1, перший піксель BGRA `[0,0,255,128]` → `[255,0,0,128]`;
  - `bottom-left origin flips rows`: 1×2, перший записаний рядок має опинитися знизу;
  - `RLE packet repeats`: тип 10, 24 біти, пакет `0x83` + BGR `[0,255,0]` → 4 зелені пікселі з A = 255;
  - `rejects colormapped`: тип 1 → `throws /Непідтримуваний TGA/`.
- [ ] **Step 2:** `node --test ra3texture/test/tga.test.js` має впасти (FAIL).
- [ ] **Step 3:** Реалізувати `tga.js`.
- [ ] **Step 4:** Має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: TGA reader"`.

---

### Task 4: `maps.js`, документ, оверлеї, NRM/SPM, вигляд у грі

**Files:**
- Create: `ra3texture/js/maps.js`
- Test: `ra3texture/test/maps.test.js`

**Interfaces:**
- Consumes: `RA3.util.{resampleRGBA, luminance, clamp, lerp}`. Реєстр генераторів передається параметром, щоб не було циклу.
- Produces (`RA3.maps`):
  - `TARGETS = ['color','alpha','height','spec','team']`;
  - `TEAM_COLORS = [{id:'Gold', rgb:[240,215,25]}, …]` (8 штук із Global Constraints);
  - `createDocument({name='AUMyTank', size=512}={}) -> Doc`:
    ```
    Doc = { name, size, layers: Layer[], images: {[id]: Uint8ClampedArray RGBA size×size},
            nrmImport: Uint8ClampedArray RGBA | null,
            settings: { nrmStrength: 2, invertY: false },
            overlays: { color:{value:RGBA(4n), cov:U8(n)}, alpha|height|spec|team:{value:U8(n), cov:U8(n)} },
            maps: { color:RGBA(4n), height:Float32Array(n), spec:U8(n), team:U8(n) } }
    Layer = { id:string, type:string, name:string, enabled:boolean, seed:number, params:object }
    ```
  - `rebuild(doc, registry)`: база (колір 128,128,128,255; висота 0.5; блиск 0; колір гравця 0) → `registry.apply(layer, doc.maps, doc.size, doc.images)` для ввімкнених шарів → оверлеї `lerp(map, value, cov/255)` (для висоти `value/255`; для `alpha` пишеться в `maps.color[i*4+3]`);
  - `applyBrush(doc, target, cx, cy, {radius, hardness, strength, value, erase})`: м'який круг `falloff = 1 − smoothstep(hardness·r, r, d)`, координати загортаються по модулю `size`. Без `erase`: `cov += (255−cov)·falloff·strength`, а `value` тягнеться до заданого (для `color` це `[r,g,b]`, для інших число 0..255). З `erase`: `cov −= cov·falloff·strength`;
  - `cloneOverlay(doc, target) -> {target, value, cov}` і `restoreOverlay(doc, snap)` для undo;
  - `addImageLayer(doc, {width, height, data}, {target='color', channel='lum', name}) -> Layer`: ресемпл до `size`, збереження в `doc.images[id]`, додавання шару `type:'image'` з `params {imageId, target, channel, opacity:1}`;
  - `buildDiffuse(doc) -> RGBA` (копія `maps.color`), `buildSPM(doc) -> RGBA`, `buildNRM(doc) -> RGBA`;
  - `viewRGBA(doc, view:'color'|'height'|'nrm'|'spm'|'alpha'|'game', teamRgb:[r,g,b]) -> RGBA`. Для `game`: recolor у гамма-просторі, лінеаризація, ламберт від `L = normalize(-0.5,-0.6,0.62)` (y вниз), ambient 0.35, відбиття `0.8·spm.r·(0.6 + 0.4·max(0, n.z))`, назад у гамму. Прозорі пікселі (A < 96) показуються шаховим фоном;
  - `resizeDocument(doc, newSize)`: ресемпл `images`, `nrmImport`, оверлеїв (найближчий сусід), перевиділення `maps`;
  - `sanitizeName(s) -> string`: лише `[A-Za-z0-9_]`, решта видаляється; якщо результат порожній, то `'MySkin'`;
  - `classifyTextureName(fileName) -> {base, role:'diffuse'|'nrm'|'spm'}`: суфікс без урахування регістру, розширення відкидається.
- NRM: Собель із загортанням, `s = settings.nrmStrength`, `n = normalize(−dx·s, −dy·s, 1)`, `G = 0.5 ± 0.5·n.y` (`+` для DirectX, `−` при `invertY`). Якщо є `nrmImport`, whiteout-змішування `normalize(a.xy + b.xy, a.z·b.z)`, де обидва вектори декодовані в одній конвенції.

- [ ] **Step 1: Написати тести.** Використати мінімальний реєстр `{apply(){}}`.

```js
const m = require('../js/maps.js'); const reg = { apply() {} };
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
```

Зображення-шар застосовує `registry`, тому в цьому тесті перевіряється лише ресемпл і структура шару. Саме застосування тестується в Task 5.

- [ ] **Step 2:** `node --test ra3texture/test/maps.test.js` має впасти (FAIL).
- [ ] **Step 3:** Реалізувати `maps.js`.
- [ ] **Step 4:** Має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: document model, brush overlays, NRM/SPM, game view"`.

---

### Task 5: `generators.js`, процедурні шари

**Files:**
- Create: `ra3texture/js/generators.js`
- Test: `ra3texture/test/generators.test.js`

**Interfaces:**
- Consumes: `RA3.util.{mulberry32, fbm, hexToRgb, luminance, clamp, lerp, smoothstep}`.
- Produces (`RA3.generators`):
  - `FACTION_PRESETS = { allied:['#9fa9b0','#4a5560'], soviet:['#7a2e24','#4b4a45'], empire:['#d8d4c8','#2f3236'] }`;
  - `catalog: Array<{type, label, params: ParamDef[]}>`, де `ParamDef = {key, label, kind:'range'|'color'|'select'|'checkbox', min?, max?, step?, options?:[{value,label}], default}`;
  - `defaultLayer(type, seed=random) -> Layer` (з `catalog` defaults, `name = label`, `enabled = true`, `id` унікальний);
  - `apply(layer, maps, size, images)` диспатчить за `layer.type`. Невідомий тип: `throw new Error('Невідомий шар: …')`.
- Типи та ключові параметри (ключі фіксовані, діапазони на розсуд виконавця):

| type | label | params | ефект (тестується) |
|---|---|---|---|
| `base` | Основа фракції | `preset` (allied/soviet/empire/custom), `color1`, `color2`, `noise` | колір = суміш color1/color2 за fbm, блиск 30 |
| `camo` | Камуфляж | `pattern` (blotch/stripes/digital), `color1..3`, `scale`, `coverage` | лише колір |
| `panels` | Панелі | `countX`, `countY`, `jitter`, `seam`, `depth` | висота ↓ на швах, колір темніший на швах, блиск ↑ на краю шва |
| `rivets` | Заклепки | `spacing`, `size`, `mode` (grid/seams) | висота ↑ (купол), блиск ↑ |
| `dirt` | Бруд/пил | `amount`, `gradient`, `scale`, `color` | колір → color, блиск ↓ |
| `rust` | Іржа | `amount`, `size`, `color` (`#7b3f1d`) | колір, висота (шум), блиск = 0 під іржею |
| `scratches` | Подряпини | `count`, `length`, `width` | колір → світлий метал `#c8c8c0`, блиск ↑, висота ↓ |
| `team` | Колір гравця | `mode` (stripes/rect/luma), `x`,`y`,`w`,`h` (0..1), `angle`, `count`, `strength`, `lumaMin` | `team` ↑. Колір під маскою m: `c = lerp(c, 0.5·lum + 64, m)` |
| `image` | Зображення | `imageId`, `target`, `channel` (lum/r/g/b/a), `opacity` | обрана карта = lerp(карта, джерело, opacity). Для `height` джерело/255 |

- [ ] **Step 1: Написати тести**

```js
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
```

- [ ] **Step 2:** `node --test ra3texture/test/generators.test.js` має впасти (FAIL).
- [ ] **Step 3:** Реалізувати `generators.js`. Усі шари мають тайлитися: шум через `util.fbm`, геометрія (шви, смуги, подряпини) з координатами по модулю `size`.
- [ ] **Step 4:** Має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: procedural layers"`.

---

### Task 6: `meshes.js` і `preview3d.js`

**Files:**
- Create: `ra3texture/js/meshes.js`, `ra3texture/js/preview3d.js`
- Test: `ra3texture/test/meshes.test.js`

**Interfaces:**
- Produces:
  - `RA3.meshes.{cube(), cylinder(seg=24), sphere(seg=24), tank()}` повертає `{positions, normals, tangents, uvs: Float32Array, indices: Uint16Array}`. Танк складається з корпусу `1.6×0.5×1.0`, башти `0.8×0.35×0.7` і ствола-циліндра довжиною 1.0, злитих в один меш. UV кожної грані займає 0..1;
  - `RA3.Preview3D`: клас `new Preview3D(canvas)`. Властивість `ok:boolean` дорівнює false без WebGL. Методи:
    - `setTextures(diffuse, nrm, spm, size)` (RGBA Uint8ClampedArray, mip через `generateMipmap`, REPEAT);
    - `setTeamColor([r,g,b])`, `setModel('cube'|'cylinder'|'sphere'|'tank')`;
    - `start()` / `stop()`.
    Обертання мишею (drag) та автообертання, якщо миша не рухалась 3 с.
- Фрагментний шейдер: формула зі спеку, розділ «3D-прев'ю». TBN з атрибутів normal і tangent, бітангенс = `cross(N,T)`. Знак Y нормалі з текстури: DirectX (`n.y` інвертується відносно OpenGL-бітангенса). `discard`, якщо `a < 96/255`.

- [ ] **Step 1: Написати тести.** Для кожної з 4 моделей перевірити:
  - `positions.length === normals.length === tangents.length`;
  - `uvs.length / 2 === positions.length / 3`;
  - усі `indices < positions.length/3`, а `indices.length % 3 === 0`;
  - нормалі одиничні (|len−1| < 1e-3), тангенси ортогональні нормалям (|dot| < 1e-3).
- [ ] **Step 2:** `node --test ra3texture/test/meshes.test.js` має впасти (FAIL).
- [ ] **Step 3:** Реалізувати `meshes.js`, потім `preview3d.js`. Останній не тестується в Node, перевіряється smoke-тестом у Task 8.
- [ ] **Step 4:** Має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: preview meshes and WebGL preview"`.

---

### Task 7: `project.js`, збереження й відкриття проєкту

**Files:**
- Create: `ra3texture/js/project.js`
- Test: `ra3texture/test/project.test.js`

**Interfaces:**
- Consumes: `RA3.maps.createDocument`.
- Produces: `RA3.project.serialize(doc) -> Promise<string>` і `RA3.project.deserialize(json) -> Promise<Doc>`. Формат `{format:'ra3texture', version:1, name, size, settings, layers, images:{id:b64}, nrmImport:b64|null, overlays:{target:{value:b64, cov:b64}}}`. b64 = base64(deflate(bytes)) через `CompressionStream('deflate')`. `maps` не зберігаються, їх відтворює `rebuild`. Чужий формат чи версія: `throw new Error('Непідтримуваний формат проєкту')`.

- [ ] **Step 1: Написати тести.**
  - `roundtrip`: документ 16², шари `base` + `panels` + шар зображення, мазок пензля, `nrmImport`. Після `rebuild` обох документів (`RA3.generators`) мають збігатися `maps.color`, `maps.team`, `maps.spec`, `maps.height`, а також `buildNRM`;
  - `rejects foreign json`: `deserialize('{"format":"x"}')` → `rejects /Непідтримуваний формат проєкту/`.
- [ ] **Step 2:** `node --test ra3texture/test/project.test.js` має впасти (FAIL).
- [ ] **Step 3:** Реалізувати `project.js`.
- [ ] **Step 4:** Має пройти (PASS).
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: project save/load"`.

---

### Task 8: Інтерфейс: `index.html`, `style.css`, `app.js` + smoke-тест

**Files:**
- Create: `ra3texture/index.html`, `ra3texture/style.css`, `ra3texture/js/app.js`, `ra3texture/test/smoke.cjs`

**Interfaces:**
- Consumes: усе з Tasks 1–7.
- DOM-ідентифікатори, на які спирається smoke-тест:
  - статус і експорт: `#status` (після першої перебудови текст `Готово`), `#btn-export-dds`, `#btn-export-png`;
  - імпорт і проєкт: `#btn-import` + прихований `#file-input` (multiple), `#btn-save-project`, `#btn-open-project` + `#project-input`;
  - документ: `#doc-name`, `#doc-size`, `#nrm-strength`, `#nrm-invert-y`, `#uncompressed`;
  - шари: `#layer-list`, `#layer-params`, `#add-layer-type`, `#btn-add-layer`;
  - полотно й прев'ю: `#view-tabs button[data-view]`, `#canvas-main`, `#canvas-3d`, `#team-colors`, `#model-select`;
  - пензель: `#brush-target`, `#brush-size`, `#brush-hardness`, `#brush-strength`, `#brush-color`, `#brush-value`, `#brush-erase`.
- Поведінка `app.js`:
  - Демо-документ `AUMyTank` 512² із шарами base (allied), panels, rivets, dirt, team (stripes).
  - Перебудова з debounce 60 мс після зміни параметра.
  - Мазок пензля: `cloneOverlay` перед мазком у стек undo (до 30). Ctrl+Z / Ctrl+Y.
  - Імпорт: перетягування на сторінку або `#file-input`. `.dds` → `decodeDDS`, `.tga` → `decodeTGA`, інше → `createImageBitmap`. Роль файлу визначає `classifyTextureName`, і за нею: `nrm` → `nrmImport`; `spm` → два image-шари (r→spec, b→team); `diffuse` → image-шар color + image-шар alpha (channel a). Назва документа береться з `base`. Помилка йде в `#status` з класом `error`.
  - Експорт DDS: імена `sanitizeName(name)` + `.dds` / `_NRM.dds` / `_SPM.dds`. Формати: color у `DXT5`, якщо `hasAlpha`, інакше `DXT1`; NRM і SPM у `DXT1`; якщо `#uncompressed`, то все `A8R8G8B8`. Кодування через `encodeDDSAsync` з відсотком у `#status`. Файли завантажуються послідовно з інтервалом 250 мс.
  - Експорт PNG: три PNG з тими ж іменами.
  - Порожні стани й мобільна ширина: колонки складаються вертикально при ширині < 900 px.

- [ ] **Step 1: Написати smoke-тест `test/smoke.cjs`.** Playwright: `chromium.launch()`, `acceptDownloads: true`, `page.goto('file://' + path.resolve(__dirname, '../index.html'))`. Збирати `console` типу `error` та `pageerror`. Чекати `#status` з текстом `Готово` (таймаут 20 с). Клікнути `#btn-export-dds`, зібрати 3 події `download`. Перевірити:
  - `suggestedFilename()` дорівнює `['AUMyTank.dds','AUMyTank_NRM.dds','AUMyTank_SPM.dds']`;
  - перші 4 байти кожного файлу `DDS `;
  - масив помилок порожній.
  Потім клікнути кожну вкладку `#view-tabs button` і знову переконатися, що помилок немає. Вихід `process.exit(1)` з повідомленням, якщо щось не так, і `console.log('SMOKE OK')` наприкінці.
- [ ] **Step 2:** `NODE_PATH=$(npm root -g) node ra3texture/test/smoke.cjs` має впасти (сторінки ще немає).
- [ ] **Step 3:** Написати `index.html` (скрипти в порядку util, dds, tga, maps, generators, meshes, preview3d, project, app), `style.css` і `app.js`.
- [ ] **Step 4:** Той самий запуск має вивести `SMOKE OK`. Додатково зробити скриншот `page.screenshot` у scratchpad і переглянути його очима.
- [ ] **Step 5:** `git commit -m "RA3 Texture Studio: UI, import/export, smoke test"`.

---

### Task 9: README, CI, фінальна перевірка

**Files:**
- Create: `ra3texture/README.md`
- Modify: `.github/workflows/build.yml` (новий job `ra3texture` поруч із `build`), `README.md` у корені (розділ-посилання на `ra3texture/`)

- [ ] **Step 1:** `ra3texture/README.md` українською:
  - як відкрити (подвійний клік на `index.html`);
  - що означають канали (таблиця зі спеку);
  - робочий процес: шари → пензель → експорт;
  - куди класти файли в Mod SDK: папка `Art` мода, підпапка за першими двома літерами назви, наприклад `Art/AU/AUMyTank.dds`; в W3X моделі ці текстури прописуються в параметрах шейдера `DiffuseTexture`, `NormalMap`, `SpecMap`;
  - порада щодо інверсії Y.
- [ ] **Step 2:** Додати до `build.yml` job:
  ```yaml
  ra3texture:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - name: Test RA3 Texture Studio
        run: node --test ra3texture/test/*.test.js
  ```
- [ ] **Step 3:** Прогнати `node --test ra3texture/test/*.test.js`: усі PASS. Прогнати `NODE_PATH=$(npm root -g) node ra3texture/test/smoke.cjs`: `SMOKE OK`. Перевірити YAML: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build.yml'))"`.
- [ ] **Step 4:** `git commit -m "RA3 Texture Studio: README and CI tests"` і `git push -u origin ccr-54f33c0f-zacquf`.
