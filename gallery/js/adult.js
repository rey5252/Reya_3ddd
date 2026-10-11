'use strict';
// ---------- 18+ blur ----------
// Every photo is checked on the device by a small image model (NSFWJS, loaded only when needed);
// nothing leaves the phone. it.nsfw holds the score (−1 = could not check) for model version
// it.nsfwV; it.adult is a manual "yes / no" that always wins. While the blur is on, photos that
// have not been checked yet stay blurred too, so nothing explicit shows before the check.
const NSFW_V = 2;
const ADULT = { model: null, loading: null, running: false, failed: false, done: 0, total: 0 };
const blur18On = () => LS.get('blur18', true);
const needsCheck = i => !!i && hasThumb(i) && i.adult == null && i.nsfwV !== NSFW_V;
const pending18 = i => !ADULT.failed && needsCheck(i);
const isAdult = it => !!it && (it.adult === true || (it.adult !== false && it.nsfwV === NSFW_V && it.nsfw >= 0.5));
const blurred = it => blur18On() && (isAdult(it) || pending18(it));
const b18 = it => (blurred(it) ? ' class="b18"' : '');
const a18Label = it => (isAdult(it) ? `${I.eyeOff}18+` : `${I.eyeOff}<small>перевірка…</small>`);

function toggleBlur18() {
  const on = !blur18On();
  LS.set('blur18', on);
  invalidate();
  for (const t in Tabs.stacks) Tabs.stacks[t].forEach(p => !p.selecting && p.renderNav());
  toast(on ? '18+ тепер розмито' : 'Увага: 18+ тепер видно', on ? 'eyeOff' : 'eye');
}
function blurButton() {
  const on = blur18On();
  const pct = ADULT.running && ADULT.total ? ` ${Math.floor(ADULT.done / ADULT.total * 100)}%` : '';
  return navBtn(`${on ? I.eyeOff : I.eye}<span>18+</span><small class="pct">${pct}</small>`, toggleBlur18, `pill b18btn${on ? ' on' : ' off'}`, on ? '18+ розмито. Натисніть, щоб показати' : '18+ видно. Натисніть, щоб розмити');
}
function updateBlurProgress() {
  const t = ADULT.running && ADULT.total ? ` ${Math.floor(ADULT.done / ADULT.total * 100)}%` : '';
  $$('.b18btn .pct').forEach(e => { e.textContent = t; });
}
// Re-style one photo's cells in place (cheaper than re-rendering the whole grid).
function patchCells(it) {
  $$(`.cell[data-id="${it.id}"]`).forEach(c => {
    const b = blurred(it);
    c.classList.toggle('adult', b);
    let lab = $('.a18', c);
    if (b) { if (!lab) { lab = h('<span class="a18"></span>'); c.insertBefore(lab, $('.check', c)); } lab.innerHTML = a18Label(it); }
    else if (lab) lab.remove();
  });
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
    for (const s of ['vendor/tf.min.js', 'vendor/tf-backend-wasm.min.js', 'vendor/nsfw/model.min.js', 'vendor/nsfw/group1-shard1of1.min.js', 'vendor/nsfwjs.min.js']) await loadScript(s);
    try { tf.wasm.setWasmPaths(new URL('vendor/', location.href).href); } catch (e) {}
    // fastest engine that works on this phone: graphics chip, then WebAssembly, then plain JS
    let ok = false;
    for (const be of ['webgl', 'wasm', 'cpu']) {
      try { ok = await tf.setBackend(be); if (ok) { await tf.ready(); tf.tidy(() => tf.zeros([1, 8, 8, 3]).mean().dataSync()); break; } } catch (e) { ok = false; }
    }
    return nsfwjs.load();
  })();
  ADULT.model = await ADULT.loading;
  return ADULT.model;
}
const idle = () => new Promise(r => (window.requestIdleCallback ? requestIdleCallback(() => r(), { timeout: 120 }) : setTimeout(r, 30)));

function scheduleAdultScan() {
  if (ADULT.running || !S.items.some(needsCheck)) return;
  ADULT.running = true;
  setTimeout(runAdultScan, 600);
}
// Newest photos first: those are what the library shows first.
async function runAdultScan() {
  let found = 0, pending = [];
  ADULT.done = 0; ADULT.total = S.items.filter(needsCheck).length;
  updateBlurProgress();
  try {
    const model = await loadModel();
    for (;;) {
      let it = null;
      for (let k = S.items.length - 1; k >= 0; k--) if (needsCheck(S.items[k])) { it = S.items[k]; break; }
      if (!it) break;
      try {
        const img = new Image(); img.src = turl(it); await img.decode();
        const p = {};
        (await model.classify(img, 5)).forEach(x => { p[x.className] = x.probability; });
        it.nsfw = Math.round(Math.max((p.Porn || 0) + (p.Hentai || 0), (p.Sexy || 0) * 0.57) * 100) / 100;
      } catch (e) { it.nsfw = -1; }
      it.nsfwV = NSFW_V;
      if (isAdult(it)) found++;
      patchCells(it);
      pending.push(it); ADULT.done++;
      if (ADULT.done % 5 === 0) updateBlurProgress();
      if (pending.length >= 25) { await saveItems(pending); pending = []; }
      await idle();
    }
  } catch (e) {
    ADULT.failed = true;
    toast('Автоперевірка 18+ не запустилась. Позначайте вручну: утримання фото → «Позначити 18+»', 'info');
  } finally {
    await saveItems(pending);
    ADULT.running = false;
    updateBlurProgress();
    invalidate();
    if (found && blur18On()) toast(`Знайдено й розмито 18+: ${found}`, 'eyeOff');
  }
}
