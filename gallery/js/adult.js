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
// Re-draw one photo's cells in place (cheaper than re-rendering the whole grid).
function patchCells(it) {
  $$(`.cell[data-id="${it.id}"]`).forEach(c => {
    if (c.classList.contains('adult') === blurred(it)) return;
    const n = h(cellHTML(it));
    ['sel', 'hole', 'in'].forEach(k => c.classList.contains(k) && n.classList.add(k));
    c.replaceWith(n);
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
const MODEL_FILES = ['vendor/tf.min.js', 'vendor/tf-backend-wasm.min.js', 'vendor/nsfw/model.min.js', 'vendor/nsfw/group1-shard1of1.min.js', 'vendor/nsfwjs.min.js'];
async function pickBackend() {
  for (const be of ['webgl', 'wasm', 'cpu']) {
    try { if (await tf.setBackend(be)) { await tf.ready(); tf.tidy(() => tf.zeros([1, 8, 8, 3]).mean().dataSync()); return be; } } catch (e) {}
  }
}

// Preferred: the model runs in a background thread, so scrolling and animations never stutter.
// The worker is built from a blob with every script inlined; it makes no network requests itself.
function nsfwWorkerMain(wasm) {
  let model;
  const ready = (async () => {
    try { tf.wasm.setWasmPaths(wasm); } catch (e) {}
    for (const be of ['webgl', 'wasm', 'cpu']) {
      try { if (await tf.setBackend(be)) { await tf.ready(); tf.tidy(() => tf.zeros([1, 8, 8, 3]).mean().dataSync()); break; } } catch (e) {}
    }
    model = await nsfwjs.load();
    return tf.getBackend();
  })();
  self.onmessage = async e => {
    const { id, bmp } = e.data;
    try {
      await ready;
      const t = tf.browser.fromPixels(bmp); if (bmp.close) bmp.close();
      const p = await model.classify(t, 5); t.dispose();
      self.postMessage({ id, p });
    } catch (err) { self.postMessage({ id, error: String((err && err.message) || err) }); }
  };
  ready.then(b => self.postMessage({ ready: b }), err => self.postMessage({ fail: String(err) }));
}
async function startWorker() {
  const texts = await Promise.all(MODEL_FILES.map(f => fetch(f).then(r => { if (!r.ok) throw new Error(f); return r.text(); })));
  const wasm = {};
  for (const n of ['tfjs-backend-wasm.wasm', 'tfjs-backend-wasm-simd.wasm']) {
    wasm[n] = URL.createObjectURL(new Blob([await (await fetch('vendor/' + n)).arrayBuffer()], { type: 'application/wasm' }));
  }
  wasm['tfjs-backend-wasm-threaded-simd.wasm'] = wasm['tfjs-backend-wasm-simd.wasm'];
  const w = new Worker(URL.createObjectURL(new Blob([texts.join('\n;\n') + `\n;(${nsfwWorkerMain})(${JSON.stringify(wasm)});`], { type: 'text/javascript' })));
  await new Promise((res, rej) => {
    const to = setTimeout(() => rej(new Error('timeout')), 60000);
    w.onmessage = e => { clearTimeout(to); e.data.ready ? res() : rej(new Error(e.data.fail)); };
    w.onerror = e => { clearTimeout(to); rej(e); };
  });
  const waiting = new Map(); let seq = 0;
  w.onmessage = e => { const cb = waiting.get(e.data.id); if (cb) { waiting.delete(e.data.id); cb(e.data); } };
  return async img => {
    const bmp = await createImageBitmap(img, { resizeWidth: 224, resizeHeight: 224, resizeQuality: 'medium' });
    const id = ++seq;
    const r = await new Promise(res => { waiting.set(id, res); w.postMessage({ id, bmp }, [bmp]); });
    if (r.error) throw new Error(r.error);
    return r.p;
  };
}
// Fallback: same model on the main thread.
async function startInPage() {
  for (const s of MODEL_FILES) await loadScript(s);
  try { tf.wasm.setWasmPaths(new URL('vendor/', location.href).href); } catch (e) {}
  await pickBackend();
  const model = await nsfwjs.load();
  return async blob => {
    const img = new Image(); const u = URL.createObjectURL(blob); img.src = u;
    try { await img.decode(); return await model.classify(img, 5); } finally { URL.revokeObjectURL(u); }
  };
}
async function loadModel() {
  if (ADULT.model) return ADULT.model;
  if (!ADULT.loading) ADULT.loading = startWorker().catch(() => startInPage());
  ADULT.model = await ADULT.loading;
  return ADULT.model;
}
const idle = () => new Promise(r => setTimeout(r, 15));

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
        const blob = await (await fetch(turl(it))).blob();
        const p = {};
        (await model(blob)).forEach(x => { p[x.className] = x.probability; });
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
