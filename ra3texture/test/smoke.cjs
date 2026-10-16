'use strict';
// Smoke-тест сторінки v2 у Chromium (Playwright). Запуск:
//   NODE_PATH=$(npm root -g) node ra3texture/test/smoke.cjs
// Відкриває index.html через file://, чекає демо-скін і перевіряє: мазок на шарі «Малювання» змінює композицію,
// Ctrl+Z повертає її точно; заливка + чорна маска + малювання маски (заливка видна лише там); заливка відром;
// тайлінг; усі вкладки вигляду; розумний матеріал; експорт DDS і PNG; збереження й відкриття проєкту (композиція та
// сама); імпорт набору з гри; відсутність горизонтальної прокрутки на 390 px.
// WebGL не обов'язковий: без нього 3D-прев'ю показує повідомлення, а тест не падає.

const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const INDEX = 'file://' + path.resolve(__dirname, '../index.html');
const NAME = 'AUMyTank';
const DDS_NAMES = [NAME + '.dds', NAME + '_NRM.dds', NAME + '_SPM.dds'];

let browser = null;

async function fail(msg) {
  console.error('SMOKE FAIL: ' + msg);
  if (browser) await browser.close().catch(() => {});
  process.exit(1);
}
function step(msg) { console.log('  ✓ ' + msg); }

// Нестиснений DDS A8R8G8B8 без mip-рівнів, записаний незалежно від dds.js.
function makeDDS(size, pixel) {
  const out = Buffer.alloc(128 + size * size * 4);
  out.write('DDS ', 0, 'latin1');
  out.writeUInt32LE(124, 4);
  out.writeUInt32LE(0x1 | 0x2 | 0x4 | 0x8 | 0x1000, 8);
  out.writeUInt32LE(size, 12);
  out.writeUInt32LE(size, 16);
  out.writeUInt32LE(size * 4, 20);
  out.writeUInt32LE(32, 76);
  out.writeUInt32LE(0x41, 80);
  out.writeUInt32LE(32, 88);
  out.writeUInt32LE(0x00ff0000, 92); out.writeUInt32LE(0x0000ff00, 96);
  out.writeUInt32LE(0x000000ff, 100); out.writeUInt32LE(0xff000000, 104);
  out.writeUInt32LE(0x1000, 108);
  for (let y = 0, o = 128; y < size; y++) {
    for (let x = 0; x < size; x++, o += 4) {
      const [r, g, b, a] = pixel(x, y);
      out[o] = b; out[o + 1] = g; out[o + 2] = r; out[o + 3] = a;
    }
  }
  return out;
}

(async () => {
  browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const errors = [];
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  page.on('pageerror', e => errors.push('pageerror: ' + (e.stack || e.message)));
  const checkErrors = async stage => { if (errors.length) await fail(stage + ':\n  ' + errors.join('\n  ')); };

  const downloads = [];
  page.on('download', d => downloads.push(d));
  async function takeDownloads(n, timeout, what) {
    const t0 = Date.now();
    while (downloads.length < n) {
      if (Date.now() - t0 > timeout) await fail(what + ': за ' + timeout / 1000 + ' с отримано ' + downloads.length + ' з ' + n + ' завантажень');
      await page.waitForTimeout(50);
    }
    return downloads.splice(0, n);
  }
  const statusInfo = () => page.$eval('#status', s => ({ text: s.textContent.trim(), cls: s.className }));
  async function waitFor(pred, timeout, what, arg) {
    try { await page.waitForFunction(pred, arg === undefined ? null : arg, { timeout }); }
    catch (e) {
      const s = await statusInfo().catch(() => ({ text: '?', cls: '?' }));
      await fail(what + ' (статус: «' + s.text + '», клас «' + s.cls + '»)');
    }
  }
  async function expectStatusOk(what) {
    const s = await statusInfo();
    if (/\berror\b/.test(s.cls)) await fail(what + ': помилка в статусі: ' + s.text);
  }

  // Доступ до стану через window.RA3_APP (див. app.js).
  const flush = () => page.evaluate(() => RA3_APP.flush());
  const compHash = () => page.evaluate(() => {
    const m = RA3_APP.doc.maps;
    return [RA3_APP.hash(m.color), RA3_APP.hash(m.height), RA3_APP.hash(m.spec), RA3_APP.hash(m.team)].join(':');
  });
  const px = (x, y) => page.evaluate(([x, y]) => {
    const d = RA3_APP.doc, i = (y * d.size + x) * 4;
    return Array.from(d.maps.color.subarray(i, i + 4));
  }, [x, y]);
  const selected = () => page.evaluate(() => {
    const l = RA3_APP.app.selectedLayer();
    return l ? { id: l.id, kind: l.kind, name: l.name, hasMask: !!l.mask } : null;
  });
  const layerCount = () => page.evaluate(() => RA3_APP.doc.layers.length);
  async function setColor(hex) {
    await page.$eval('#mat-color', (i, v) => { i.value = v; i.dispatchEvent(new Event('input', { bubbles: true })); }, hex);
  }
  async function setRange(sel, v) {
    await page.$eval(sel, (i, v) => { i.value = String(v); i.dispatchEvent(new Event('input', { bubbles: true })); }, v);
  }
  // Мазок мишею по текселях (x0,y)→(x1,y) основної копії текстури.
  async function strokeH(x0, x1, y, steps) {
    const a = await page.evaluate(([x, y]) => RA3_APP.toClient(x, y), [x0, y]);
    const b = await page.evaluate(([x, y]) => RA3_APP.toClient(x, y), [x1, y]);
    await page.mouse.move(a.x, a.y);
    await page.mouse.down();
    const n = steps || 12;
    for (let i = 1; i <= n; i++) await page.mouse.move(a.x + (b.x - a.x) * i / n, a.y + (b.y - a.y) * i / n);
    await page.mouse.up();
    await flush();
  }
  async function clickTexel(x, y) {
    const p = await page.evaluate(([x, y]) => RA3_APP.toClient(x, y), [x, y]);
    await page.mouse.click(p.x, p.y);
    await flush();
  }
  const near = (a, b, tol) => Math.abs(a[0] - b[0]) <= tol && Math.abs(a[1] - b[1]) <= tol && Math.abs(a[2] - b[2]) <= tol;

  // ---------- Завантаження й демо ----------
  await page.goto(INDEX);
  await waitFor(() => { const s = document.querySelector('#status'); return !!s && s.textContent.trim() === 'Готово'; }, 20000, '#status не показав «Готово» за 20 с');
  await checkErrors('після завантаження');
  const webgl = await page.evaluate(() => !document.getElementById('canvas-3d').hidden);
  console.log('3D-прев\'ю: ' + (webgl ? 'WebGL працює' : 'WebGL недоступний (показано повідомлення)'));
  const sel0 = await selected();
  if (!sel0 || sel0.kind !== 'paint') await fail('після запуску вибрано не шар «Малювання»: ' + JSON.stringify(sel0));
  step('демо завантажено, вибрано «' + sel0.name + '»');

  // ---------- Мазок на шарі «Малювання» + Ctrl+Z / Ctrl+Y ----------
  await page.click('#view-tabs button[data-view="color"]');
  await page.click('#tool-brush');
  await setColor('#ff00ff');
  await setRange('#brush-size', 40);
  const h0 = await compHash();
  const p0 = await px(256, 200);
  await strokeH(150, 360, 200);
  const p1 = await px(256, 200);
  if (!near(p1, [255, 0, 255], 3)) await fail('мазок не змінив композицію в (256,200): ' + JSON.stringify(p0) + ' → ' + JSON.stringify(p1));
  if (await compHash() === h0) await fail('мазок не змінив хеш композиції');
  await page.keyboard.press('Control+KeyZ');
  await flush();
  if (await compHash() !== h0) await fail('Ctrl+Z не повернув композицію точно: ' + JSON.stringify(await px(256, 200)) + ' ≠ ' + JSON.stringify(p0));
  await page.keyboard.press('Control+KeyY');
  await flush();
  if (!near(await px(256, 200), [255, 0, 255], 3)) await fail('Ctrl+Y не повернув мазок');
  await page.keyboard.press('Control+KeyZ');
  await flush();
  if (await compHash() !== h0) await fail('повторний Ctrl+Z не повернув композицію');
  await checkErrors('після мазка й Ctrl+Z');
  step('мазок змінює композицію, Ctrl+Z повертає її точно, Ctrl+Y — знову мазок');

  // ---------- Заливка + чорна маска + малювання маски ----------
  await setColor('#00ff00');
  const beforeFill = await compHash();
  const outside = await px(250, 450);
  const n0 = await layerCount();
  await page.click('#btn-add-fill');
  await flush();
  if (await layerCount() !== n0 + 1 || (await selected()).kind !== 'fill') await fail('«+ Заливка» не додала вибраний шар-заливку');
  if (!near(await px(250, 450), [0, 255, 0], 1)) await fail('заливка без маски не покрила текстуру: ' + JSON.stringify(await px(250, 450)));
  await page.click('#tab-layer');
  await page.click('#btn-mask-black');
  await flush();
  if (!(await selected()).hasMask) await fail('«Додати чорну» не додала маску');
  if (await compHash() !== beforeFill) await fail('заливка з чорною маскою змінює композицію');
  const target = await page.evaluate(() => RA3_APP.state.target);
  if (target !== 'mask') await fail('після додавання маски ціль не «Маска»: ' + target);
  await strokeH(120, 400, 300);
  const inMask = await px(250, 300), outMask = await px(250, 450);
  if (!near(inMask, [0, 255, 0], 2)) await fail('заливка не з\'явилась там, де намальовано маску: ' + JSON.stringify(inMask));
  if (JSON.stringify(outMask) !== JSON.stringify(outside)) await fail('заливка видна поза намальованою маскою: ' + JSON.stringify(outMask) + ' ≠ ' + JSON.stringify(outside));
  await checkErrors('після маски');
  step('заливка з чорною маскою видна лише там, де намальовано маску');

  // ---------- Заливка відром на шарі «Малювання» ----------
  await page.click('#layer-list li[data-id="' + sel0.id + '"] .lname');
  if ((await selected()).id !== sel0.id) await fail('клік по рядку не вибрав шар «Малювання»');
  if (await page.evaluate(() => RA3_APP.state.target) !== 'paint') await fail('для шару «Малювання» без маски ціль не стала «Шар»');
  await page.keyboard.press('KeyG');
  if (await page.evaluate(() => RA3_APP.state.tool) !== 'fill') await fail('клавіша G не вибрала заливку');
  await setColor('#0000ff');
  const hBucket = await compHash();
  await clickTexel(40, 470);
  if (!near(await px(40, 470), [0, 0, 255], 2)) await fail('заливка відром не змінила піксель під курсором: ' + JSON.stringify(await px(40, 470)));
  const st = await statusInfo();
  if (!/Залито/.test(st.text)) await fail('статус після заливки: ' + st.text);
  await page.keyboard.press('Control+KeyZ');
  await flush();
  if (await compHash() !== hBucket) await fail('Ctrl+Z не скасував заливку відром');
  await page.keyboard.press('KeyB');
  step('заливка відром (' + st.text + ') і її скасування');

  // ---------- Часткова перебудова = повна ----------
  // Зміни вибраного шару перераховують лише шари від нього вгору (знімок «під шаром»): результат має збігатися
  // з повною композицією layers.composite.
  const consistent = async what => {
    await flush();
    const ok = await page.evaluate(() => {
      const m = RA3_APP.doc.maps, h = () => [m.color, m.height, m.spec, m.team].map(RA3_APP.hash).join(':');
      const before = h();
      RA3.layers.composite(RA3_APP.doc);
      return before === h();
    });
    if (!ok) await fail('після «' + what + '» композиція відрізняється від повної перебудови');
  };
  const idOf = re => page.evaluate(src => { const r = new RegExp(src); const l = RA3_APP.doc.layers.find(x => r.test(x.name)); return l && l.id; }, re.source);
  const teamId = await idOf(/^Колір гравця$/);
  await page.click('#layer-list li[data-id="' + teamId + '"] .lname');
  await page.click('#tab-layer');
  await setRange('#mask-width', 6);
  await consistent('ширина маски «Зношені краї»');
  await setRange('#layer-opacity', 60);
  await consistent('непрозорість шару');
  await page.click('#mask-invert');
  await consistent('інверсія маски');
  await page.click('#mask-invert');
  await page.click('#layer-list li[data-id="' + (await idOf(/^Заклепки$/)) + '"] [data-act="eye"]');
  await consistent('сховати нижній шар');
  await page.click('#btn-layer-down');
  await consistent('шар нижче');
  await page.keyboard.press('Control+KeyZ');
  await consistent('Ctrl+Z порядку');
  const panelsId = await idOf(/^Панелі$/);
  await page.click('#layer-list li[data-id="' + panelsId + '"] .lname');
  await setRange('#gen-countX', 5);
  await consistent('параметр генератора «Панелі»');
  await page.click('#layer-list li[data-id="' + (await idOf(/^Заклепки$/)) + '"] [data-act="eye"]');
  await consistent('показати шар');
  await page.click('#layer-list li[data-id="' + sel0.id + '"] .lname');
  await checkErrors('після змін параметрів');
  step('часткова перебудова (маска, непрозорість, видимість, порядок, генератор) збігається з повною');

  // ---------- Тайлінг, вкладки ----------
  await page.click('#tile-toggle');
  if (!(await page.evaluate(() => RA3_APP.state.tiling)) || await page.$eval('#tile-toggle', b => b.getAttribute('aria-pressed')) !== 'true') await fail('тайлінг не ввімкнувся');
  await strokeH(500, 530, 100, 6);           // мазок через край текстури загортається
  if (!near(await px(10, 100), [0, 0, 255], 3)) await fail('мазок через край не перейшов на протилежний бік: ' + JSON.stringify(await px(10, 100)));
  await page.keyboard.press('KeyT');
  if (await page.evaluate(() => RA3_APP.state.tiling)) await fail('клавіша T не вимкнула тайлінг');
  const views = await page.$$eval('#view-tabs button[data-view]', bs => bs.map(b => b.dataset.view));
  if (views.length !== 6) await fail('очікується 6 вкладок, знайдено ' + views.length);
  for (const v of views) {
    await page.click('#view-tabs button[data-view="' + v + '"]');
    await waitFor(view => document.getElementById('canvas-main').dataset.view === view, 10000, 'вкладка ' + v + ' не намалювалась', v);
  }
  await checkErrors('після тайлінгу й вкладок');
  step('тайлінг 3×3 (мазок загортається через край), ' + views.length + ' вкладок');

  // ---------- Розумний матеріал ----------
  const n1 = await layerCount();
  await page.selectOption('#smart-select', 'soviet-rusty');
  await flush();
  const n2 = await layerCount();
  if (n2 <= n1 + 2) await fail('розумний матеріал додав ' + (n2 - n1) + ' шарів');
  await expectStatusOk('розумний матеріал');
  await checkErrors('після розумного матеріалу');
  step('розумний матеріал «СРСР: червона сталь з іржею» додав ' + (n2 - n1) + ' шарів');

  // ---------- Експорт DDS ----------
  await page.click('#btn-export-dds');
  const dds = await takeDownloads(3, 60000, 'Експорт DDS');
  const ddsNames = dds.map(d => d.suggestedFilename());
  if (JSON.stringify(ddsNames) !== JSON.stringify(DDS_NAMES)) await fail('імена DDS: ' + JSON.stringify(ddsNames) + ', очікується ' + JSON.stringify(DDS_NAMES));
  for (const d of dds) {
    const head = fs.readFileSync(await d.path()).subarray(0, 4).toString('latin1');
    if (head !== 'DDS ') await fail(d.suggestedFilename() + ' починається з ' + JSON.stringify(head));
  }
  await checkErrors('після експорту DDS');
  step('експорт DDS: ' + ddsNames.join(', '));

  await waitFor(() => !document.getElementById('btn-export-png').disabled, 10000, 'кнопка «Експорт PNG» не розблокувалась');
  await page.click('#btn-export-png');
  const png = await takeDownloads(3, 30000, 'Експорт PNG');
  const pngNames = png.map(d => d.suggestedFilename());
  if (JSON.stringify(pngNames) !== JSON.stringify([NAME + '.png', NAME + '_NRM.png', NAME + '_SPM.png'])) await fail('імена PNG: ' + JSON.stringify(pngNames));
  for (const d of png) if (fs.readFileSync(await d.path()).subarray(0, 8).toString('hex') !== '89504e470d0a1a0a') await fail(d.suggestedFilename() + ' не PNG');
  await checkErrors('після експорту PNG');
  step('експорт PNG: ' + pngNames.join(', '));

  // ---------- Збереження й відкриття проєкту ----------
  await waitFor(() => !document.getElementById('btn-save-project').disabled, 10000, 'кнопка «Зберегти» не розблокувалась');
  const hSaved = await compHash();
  const layersSaved = await page.evaluate(() => RA3_APP.doc.layers.map(l => l.kind + ':' + l.name).join('|'));
  await page.click('#btn-save-project');
  const [proj] = await takeDownloads(1, 30000, 'Збереження проєкту');
  if (!/\.ra3tex\.json$/.test(proj.suggestedFilename())) await fail('ім\'я файлу проєкту: ' + proj.suggestedFilename());
  const projBytes = fs.readFileSync(await proj.path());
  let json;
  try { json = JSON.parse(projBytes.toString('utf8')); } catch (e) { await fail('файл проєкту не JSON: ' + e.message); }
  if (json.version !== 2) await fail('версія проєкту ' + json.version);
  await page.setInputFiles('#project-input', { name: proj.suggestedFilename(), mimeType: 'application/json', buffer: projBytes });
  await waitFor(() => { const s = document.getElementById('status'); return !s.classList.contains('busy') && /відкрито|error/.test(s.textContent + ' ' + s.className); }, 20000, 'проєкт не відкрився');
  await expectStatusOk('відкриття проєкту');
  await flush();
  if (await page.evaluate(() => RA3_APP.doc.layers.map(l => l.kind + ':' + l.name).join('|')) !== layersSaved) await fail('шари після відкриття проєкту інші');
  if (await compHash() !== hSaved) await fail('композиція після відкриття проєкту відрізняється');
  await checkErrors('після збереження й відкриття проєкту');
  step('проєкт збережено й відкрито, композиція та сама');

  // ---------- Імпорт набору з гри (X.dds + X_NRM.dds + X_SPM.dds) ----------
  const set = [
    ['SmokeSet.dds', makeDDS(256, (x) => ((x >> 4) & 1 ? [150, 140, 120, 255] : [90, 85, 80, 255]))],
    ['SmokeSet_NRM.dds', makeDDS(256, x => ((x >> 3) & 1 ? [170, 128, 240, 255] : [128, 128, 255, 255]))],
    ['SmokeSet_SPM.dds', makeDDS(256, (x, y) => [80, 0, y < 64 ? 255 : 0, 255])]];
  await page.setInputFiles('#file-input', set.map(([name, buffer]) => ({ name, mimeType: 'application/octet-stream', buffer })));
  await waitFor(() => document.getElementById('doc-name').value === 'SmokeSet' && !document.getElementById('status').classList.contains('busy'), 20000, 'набір з гри не імпортувався');
  await expectStatusOk('імпорт набору');
  const after = await page.evaluate(() => ({
    size: RA3_APP.doc.size,
    images: RA3_APP.doc.layers.filter(l => l.kind === 'proc' && l.proc.type === 'image').length,
    visibleOther: RA3_APP.doc.layers.filter(l => l.visible && !(l.kind === 'proc' && l.proc.type === 'image') && l.kind !== 'paint').length,
    nrm: !!RA3_APP.doc.nrmImport
  }));
  if (after.size !== 256) await fail('розмір документа не став 256 за набором: ' + after.size);
  if (after.images !== 4) await fail('очікується 4 шари-зображення (колір, прозорість, блиск, гравець), є ' + after.images);
  if (after.visibleOther) await fail('після імпорту набору лишились видимі генератори/заливки: ' + after.visibleOther);
  if (!after.nrm || !(await page.$('#layer-list li[data-type="nrm"]'))) await fail('імпортована NRM не з\'явилась');
  await flush();
  const imp = await px(0, 0);
  if (!near(imp, [90, 85, 80], 2)) await fail('колір після імпорту набору: ' + JSON.stringify(imp));
  await checkErrors('після імпорту набору з гри');
  step('імпорт набору з гри: 256×256, 4 шари-зображення, NRM, інші шари вимкнено');

  // ---------- Вузький екран (телефон) ----------
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overflow > 0) {
    const wide = await page.evaluate(() => Array.from(document.querySelectorAll('body *'))
      .filter(e => e.getBoundingClientRect().right > window.innerWidth + 0.5 && e.getBoundingClientRect().width > 0)
      .slice(0, 6).map(e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string' ? '.' + e.className.split(' ').join('.') : '')));
    await fail('на ширині 390 px сторінка прокручується вбік на ' + overflow + ' px: ' + wide.join(', '));
  }
  const stage = await page.locator('#stage').boundingBox();
  if (!stage || stage.width < 300 || stage.height < 300) await fail('полотно на телефоні замале: ' + JSON.stringify(stage));
  await checkErrors('на вузькому екрані');
  step('390 px: без горизонтальної прокрутки, полотно ' + Math.round(stage.width) + '×' + Math.round(stage.height));

  await browser.close();
  console.log('SMOKE OK');
})().catch(e => fail(e && e.stack || String(e)));
