'use strict';
// ---------- 18+ blur ----------
// Every photo is checked on the device by a small image model (NSFWJS, loaded only when needed);
// nothing leaves the phone. it.nsfw holds the score (−1 = could not check), it.adult a manual
// "yes / no" that always wins. With the 18+ button on, adult photos are blurred everywhere.
const ADULT = { model: null, loading: null, running: false };
const blur18On = () => LS.get('blur18', true);
const isAdult = it => !!it && (it.adult === true || (it.adult !== false && (it.nsfw || 0) >= 0.55));
const blurred = it => blur18On() && isAdult(it);
const b18 = it => (blurred(it) ? ' class="b18"' : '');

function toggleBlur18() {
  const on = !blur18On();
  LS.set('blur18', on);
  document.body.classList.toggle('blur18', on);
  invalidate();
  for (const t in Tabs.stacks) Tabs.stacks[t].forEach(p => !p.selecting && p.renderNav());
  toast(on ? 'Вміст 18+ розмито' : 'Вміст 18+ показано', on ? 'eyeOff' : 'eye');
}
function blurButton() {
  const on = blur18On();
  return navBtn(`${on ? I.eyeOff : I.eye}<span>18+</span>`, toggleBlur18, `pill b18btn${on ? '' : ' off'}`, on ? 'Показати вміст 18+' : 'Розмивати вміст 18+');
}

async function markAdult(ids, val) {
  const its = itemsOf(ids);
  its.forEach(i => { i.adult = val; });
  await saveItems(its);
  invalidate();
  toast(val ? (blur18On() ? 'Позначено 18+ і розмито' : 'Позначено 18+') : 'Позначку 18+ знято', val ? 'eyeOff' : 'eye');
}

function loadScript(src) {
  return new Promise((res, rej) => { const s = document.createElement('script'); s.src = src; s.onload = res; s.onerror = rej; document.head.append(s); });
}
async function loadModel() {
  if (ADULT.model) return ADULT.model;
  if (!ADULT.loading) ADULT.loading = (async () => {
    for (const s of ['vendor/tf.min.js', 'vendor/nsfw/model.min.js', 'vendor/nsfw/group1-shard1of1.min.js', 'vendor/nsfwjs.min.js']) await loadScript(s);
    try { await tf.setBackend('webgl'); } catch (e) {}
    await tf.ready();
    return nsfwjs.load();
  })();
  ADULT.model = await ADULT.loading;
  return ADULT.model;
}
const idle = () => new Promise(r => (window.requestIdleCallback ? requestIdleCallback(() => r(), { timeout: 400 }) : setTimeout(r, 60)));
const needsCheck = i => i.nsfw == null && hasThumb(i);

// Checks unchecked photos in the background, a few at a time when the phone is idle.
function scheduleAdultScan() {
  if (ADULT.running || !S.items.some(needsCheck)) return;
  ADULT.running = true;
  setTimeout(runAdultScan, 2000);
}
async function runAdultScan() {
  let found = 0, pending = [], shown = 0;
  try {
    const model = await loadModel();
    for (;;) {
      const it = S.items.find(needsCheck);
      if (!it) break;
      try {
        const img = new Image(); img.src = turl(it); await img.decode();
        const p = {};
        (await model.classify(img, 5)).forEach(x => { p[x.className] = x.probability; });
        it.nsfw = Math.round(((p.Porn || 0) + (p.Hentai || 0) + 0.5 * (p.Sexy || 0)) * 100) / 100;
        if (isAdult(it)) found++;
      } catch (e) { it.nsfw = -1; }
      pending.push(it);
      if (pending.length >= 25) {
        await saveItems(pending); pending = [];
        if (found > shown && !V.on && !OV.length) { shown = found; invalidate(); }
      }
      await idle();
    }
  } catch (e) {
    // the model could not load: manual marking still works
  } finally {
    await saveItems(pending);
    ADULT.running = false;
    if (found) { invalidate(); if (blur18On()) toast(`Знайдено й розмито 18+: ${found}`, 'eyeOff'); }
  }
}
