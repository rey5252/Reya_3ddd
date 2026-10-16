'use strict';
// Smoke-тест сторінки в Chromium (Playwright). Запуск:
//   NODE_PATH=$(npm root -g) node ra3texture/test/smoke.cjs
// Відкриває index.html через file://, чекає демо-скін, перевіряє експорт DDS/PNG, вкладки, імпорт DDS,
// мазок пензля з Ctrl+Z, збереження й відкриття проєкту та відсутність горизонтальної прокрутки на телефоні.
// WebGL не обов'язковий: без нього 3D-прев'ю показує повідомлення, а тест не падає.

const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');

const INDEX = 'file://' + path.resolve(__dirname, '../index.html');
const NAME = 'AUMyTank';
const DDS_NAMES = [NAME + '.dds', NAME + '_NRM.dds', NAME + '_SPM.dds'];
const PNG_NAMES = [NAME + '.png', NAME + '_NRM.png', NAME + '_SPM.png'];
const PNG_MAGIC = '89504e470d0a1a0a';

let browser = null;

async function fail(msg) {
  console.error('SMOKE FAIL: ' + msg);
  if (browser) await browser.close().catch(() => {});
  process.exit(1);
}

// Нестиснений DDS A8R8G8B8 без mip-рівнів, записаний незалежно від dds.js.
function makeDDS(size, pixel) {
  const out = Buffer.alloc(128 + size * size * 4);
  out.write('DDS ', 0, 'latin1');
  out.writeUInt32LE(124, 4);
  out.writeUInt32LE(0x1 | 0x2 | 0x4 | 0x8 | 0x1000, 8);   // CAPS | HEIGHT | WIDTH | PITCH | PIXELFORMAT
  out.writeUInt32LE(size, 12);
  out.writeUInt32LE(size, 16);
  out.writeUInt32LE(size * 4, 20);
  out.writeUInt32LE(32, 76);                               // DDS_PIXELFORMAT.dwSize
  out.writeUInt32LE(0x41, 80);                             // DDPF_RGB | DDPF_ALPHAPIXELS
  out.writeUInt32LE(32, 88);
  out.writeUInt32LE(0x00ff0000, 92); out.writeUInt32LE(0x0000ff00, 96);
  out.writeUInt32LE(0x000000ff, 100); out.writeUInt32LE(0xff000000, 104);
  out.writeUInt32LE(0x1000, 108);                          // DDSCAPS_TEXTURE
  for (let y = 0, o = 128; y < size; y++) {
    for (let x = 0; x < size; x++, o += 4) {
      const [r, g, b, a] = pixel(x, y);
      out[o] = b; out[o + 1] = g; out[o + 2] = r; out[o + 3] = a;
    }
  }
  return out;
}

(async () => {
  browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
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
  async function headHex(d, n) {
    const p = await d.path();
    return fs.readFileSync(p).subarray(0, n).toString('hex');
  }
  const statusInfo = () => page.$eval('#status', s => ({ text: s.textContent.trim(), cls: s.className }));
  async function waitStatus(pred, timeout, what, arg) {
    try {
      await page.waitForFunction(pred, arg === undefined ? null : arg, { timeout });
    } catch (e) {
      const s = await statusInfo().catch(() => ({ text: '?', cls: '?' }));
      await fail(what + ' (статус: «' + s.text + '», клас «' + s.cls + '»)');
    }
  }
  const sampleCenter = () => page.evaluate(() => {
    const c = document.getElementById('canvas-main');
    return Array.from(c.getContext('2d').getImageData(c.width >> 1, c.height >> 1, 1, 1).data);
  });

  // ---------- Завантаження й демо-скін ----------
  await page.goto(INDEX);
  await waitStatus(() => { const s = document.querySelector('#status'); return !!s && s.textContent.trim() === 'Готово'; },
    20000, '#status не показав «Готово» за 20 с');
  await checkErrors('після завантаження');
  const webgl = await page.evaluate(() => !document.getElementById('canvas-3d').hidden);
  console.log('3D-прев\'ю: ' + (webgl ? 'WebGL працює' : 'WebGL недоступний (показано повідомлення)'));

  // ---------- Експорт DDS ----------
  await page.click('#btn-export-dds');
  const dds = await takeDownloads(3, 60000, 'Експорт DDS');
  const ddsNames = dds.map(d => d.suggestedFilename());
  if (JSON.stringify(ddsNames) !== JSON.stringify(DDS_NAMES)) await fail('імена DDS: ' + JSON.stringify(ddsNames) + ', очікується ' + JSON.stringify(DDS_NAMES));
  for (const d of dds) {
    const head = Buffer.from(await headHex(d, 4), 'hex').toString('latin1');
    if (head !== 'DDS ') await fail(d.suggestedFilename() + ' починається з ' + JSON.stringify(head) + ', а не "DDS "');
  }
  await checkErrors('після експорту DDS');

  // ---------- Експорт PNG ----------
  await waitStatus(() => !document.getElementById('btn-export-png').disabled, 10000, 'кнопка «Експорт PNG» не розблокувалася');
  await page.click('#btn-export-png');
  const png = await takeDownloads(3, 30000, 'Експорт PNG');
  const pngNames = png.map(d => d.suggestedFilename());
  if (JSON.stringify(pngNames) !== JSON.stringify(PNG_NAMES)) await fail('імена PNG: ' + JSON.stringify(pngNames));
  for (const d of png) if (await headHex(d, 8) !== PNG_MAGIC) await fail(d.suggestedFilename() + ' не PNG');
  await checkErrors('після експорту PNG');

  // ---------- Вкладки полотна ----------
  const views = await page.$$eval('#view-tabs button[data-view]', bs => bs.map(b => b.dataset.view));
  if (views.length !== 6) await fail('очікується 6 вкладок, знайдено ' + views.length);
  for (const v of views) {
    await page.click('#view-tabs button[data-view="' + v + '"]');
    await waitStatus(view => document.getElementById('canvas-main').dataset.view === view, 10000, 'вкладка ' + v + ' не намалювалась', v);
  }
  await checkErrors('після перемикання вкладок');

  // ---------- Імпорт DDS через #file-input ----------
  const imagesBefore = await page.$$eval('#layer-list li[data-type="image"]', els => els.length);
  const ddsFile = makeDDS(64, (x, y) => (((x >> 3) ^ (y >> 3)) & 1 ? [200, 40, 30, 255] : [30, 60, 200, 255]));
  await page.setInputFiles('#file-input', { name: 'SmokeSkin.dds', mimeType: 'application/octet-stream', buffer: ddsFile });
  await waitStatus(n => document.querySelectorAll('#layer-list li[data-type="image"]').length >= n + 2 &&
    !document.getElementById('status').classList.contains('busy'), 15000, 'після імпорту DDS не з\'явилися шари «Зображення»', imagesBefore);
  let st = await statusInfo();
  if (/\berror\b/.test(st.cls)) await fail('імпорт DDS завершився помилкою: ' + st.text);
  const nameAfterImport = await page.$eval('#doc-name', i => i.value);
  if (nameAfterImport !== 'SmokeSkin') await fail('назва документа після імпорту: ' + nameAfterImport);
  await checkErrors('після імпорту DDS');

  // ---------- Мазок пензля й Ctrl+Z ----------
  await page.click('#view-tabs button[data-view="color"]');
  await page.selectOption('#brush-target', 'color');
  await page.$eval('#brush-color', i => { i.value = '#ff00ff'; i.dispatchEvent(new Event('input', { bubbles: true })); i.dispatchEvent(new Event('change', { bubbles: true })); });
  await page.$eval('#brush-erase', i => { if (i.checked) i.click(); });
  await page.waitForFunction(() => document.getElementById('canvas-main').dataset.view === 'color');
  const before = await sampleCenter();
  const box = await page.locator('#canvas-main').boundingBox();
  const cy = box.y + box.height / 2;
  await page.mouse.move(box.x + box.width * 0.3, cy);
  await page.mouse.down();
  for (let i = 1; i <= 8; i++) await page.mouse.move(box.x + box.width * (0.3 + 0.05 * i), cy);
  await page.mouse.up();
  try {
    await page.waitForFunction(() => {
      const c = document.getElementById('canvas-main'), d = c.getContext('2d').getImageData(c.width >> 1, c.height >> 1, 1, 1).data;
      return d[0] > 180 && d[1] < 90 && d[2] > 180;
    }, null, { timeout: 10000 });
  } catch (e) { await fail('мазок пензля не змінив центр полотна: ' + JSON.stringify(await sampleCenter())); }
  await page.keyboard.press('Control+z');
  try {
    await page.waitForFunction(px => {
      const c = document.getElementById('canvas-main'), d = c.getContext('2d').getImageData(c.width >> 1, c.height >> 1, 1, 1).data;
      return d[0] === px[0] && d[1] === px[1] && d[2] === px[2];
    }, before, { timeout: 10000 });
  } catch (e) { await fail('Ctrl+Z не повернув полотно: ' + JSON.stringify(await sampleCenter()) + ' ≠ ' + JSON.stringify(before)); }
  await checkErrors('після мазка й Ctrl+Z');

  // ---------- Зберегти й відкрити проєкт ----------
  const layersBefore = await page.$$eval('#layer-list li[data-id]', els => els.map(e => e.dataset.type).join(','));
  await page.click('#btn-save-project');
  const [proj] = await takeDownloads(1, 30000, 'Збереження проєкту');
  const projBytes = fs.readFileSync(await proj.path());
  try { JSON.parse(projBytes.toString('utf8')); } catch (e) { await fail('файл проєкту не JSON: ' + e.message); }
  await page.setInputFiles('#project-input', { name: proj.suggestedFilename(), mimeType: 'application/json', buffer: projBytes });
  await waitStatus(() => { const s = document.getElementById('status'); return !s.classList.contains('busy') && /відкрито|error/.test(s.textContent + ' ' + s.className); },
    20000, 'проєкт не відкрився');
  st = await statusInfo();
  if (/\berror\b/.test(st.cls)) await fail('відкриття проєкту: ' + st.text);
  const layersAfter = await page.$$eval('#layer-list li[data-id]', els => els.map(e => e.dataset.type).join(','));
  if (layersAfter !== layersBefore) await fail('шари після відкриття проєкту: ' + layersAfter + ', очікується ' + layersBefore);
  if (await page.$eval('#doc-name', i => i.value) !== 'SmokeSkin') await fail('назва документа після відкриття проєкту змінилася');
  await checkErrors('після збереження й відкриття проєкту');

  // ---------- Імпорт набору з гри (X.dds + X_NRM.dds + X_SPM.dds) ----------
  // Документ бере розмір набору (256), процедурні шари вимикаються, щоб не змішуватися з текстурою гри.
  const procOn = await page.$$eval('#layer-list li[data-id]:not([data-type="image"]) input[data-act="toggle"]', els => els.filter(e => e.checked).length);
  if (!procOn) await fail('перед імпортом набору немає ввімкнених процедурних шарів');
  const set = [
    ['SmokeSet.dds', makeDDS(256, (x, y) => ((x >> 4) & 1 ? [150, 140, 120, 255] : [90, 85, 80, 255]))],
    ['SmokeSet_NRM.dds', makeDDS(256, x => ((x >> 3) & 1 ? [170, 128, 240, 255] : [128, 128, 255, 255]))],
    ['SmokeSet_SPM.dds', makeDDS(256, (x, y) => [80, 0, y < 64 ? 255 : 0, 255])]];
  await page.setInputFiles('#file-input', set.map(([name, buffer]) => ({ name, mimeType: 'application/octet-stream', buffer })));
  await waitStatus(() => document.getElementById('doc-name').value === 'SmokeSet' && !document.getElementById('status').classList.contains('busy'),
    20000, 'набір з гри не імпортувався');
  st = await statusInfo();
  if (/\berror\b/.test(st.cls)) await fail('імпорт набору завершився помилкою: ' + st.text);
  const procAfter = await page.$$eval('#layer-list li[data-id]:not([data-type="image"]) input[data-act="toggle"]', els => els.map(e => e.checked));
  if (!procAfter.length || procAfter.some(c => c)) await fail('після імпорту набору процедурні шари не вимкнено: ' + JSON.stringify(procAfter));
  if (!/Процедурні шари вимкнено/.test(st.text)) await fail('статус не каже, що процедурні шари вимкнено: ' + st.text);
  if (await page.$eval('#doc-size', s => s.value) !== '256') await fail('розмір документа не став 256 за набором');
  if (!(await page.$('#layer-list li[data-type="nrm"]'))) await fail('імпортована NRM не з\'явилась у списку шарів');
  await checkErrors('після імпорту набору з гри');

  // ---------- Вузький екран (телефон) ----------
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(400);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  if (overflow > 0) await fail('на ширині 390 px сторінка прокручується вбік на ' + overflow + ' px');
  await checkErrors('на вузькому екрані');

  await browser.close();
  console.log('SMOKE OK');
})().catch(e => fail(e && e.stack || String(e)));
