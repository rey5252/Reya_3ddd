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
const BATCH = 8, SIZE = 224;

// Runs the network on a batch of 224×224 bitmaps; answers [Drawing, Hentai, Neutral, Porn, Sexy] per image.
// Works both inside the worker and on the page (fallback).
function nsfwEngine(wasm) {
  let net;
  const ready = (async () => {
    try { tf.wasm.setWasmPaths(wasm); } catch (e) {}
    for (const be of ['webgl', 'wasm', 'cpu']) {
      try { if (await tf.setBackend(be)) { await tf.ready(); tf.tidy(() => tf.zeros([1, 8, 8, 3]).mean().dataSync()); break; } } catch (e) {}
    }
    net = (await nsfwjs.load()).model;
    return tf.getBackend();
  })();
  const run = async bmps => {
    await ready;
    const out = tf.tidy(() => net.predict(tf.stack(bmps.map(b => tf.browser.fromPixels(b))).toFloat().div(255)));
    const d = await out.data(); out.dispose();
    bmps.forEach(b => b.close && b.close());
    return bmps.map((_, i) => Array.from(d.slice(i * 5, i * 5 + 5)));
  };
  return { ready, run };
}
function nsfwWorkerMain(wasm) {
  const eng = nsfwEngine(wasm);
  self.onmessage = async e => {
    try { self.postMessage({ id: e.data.id, res: await eng.run(e.data.bmps) }); }
    catch (err) { self.postMessage({ id: e.data.id, error: String((err && err.message) || err) }); }
  };
  eng.ready.then(b => self.postMessage({ ready: b }), err => self.postMessage({ fail: String(err) }));
}
const withTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]);

// Preferred: background threads, so scrolling never stutters. On the CPU engine several run in
// parallel (one per spare core); on the GPU one is enough. Workers are built from a blob with every
// script inlined and make no requests of their own.
async function startWorkers() {
  const texts = await Promise.all(MODEL_FILES.map(f => fetch(f).then(r => { if (!r.ok) throw new Error(f); return r.text(); })));
  const wasm = {};
  for (const n of ['tfjs-backend-wasm.wasm', 'tfjs-backend-wasm-simd.wasm']) {
    wasm[n] = URL.createObjectURL(new Blob([await (await fetch('vendor/' + n)).arrayBuffer()], { type: 'application/wasm' }));
  }
  wasm['tfjs-backend-wasm-threaded-simd.wasm'] = wasm['tfjs-backend-wasm-simd.wasm'];
  const src = URL.createObjectURL(new Blob(['self.console.warn = () => {};\n' + texts.join('\n;\n') + `\n;${nsfwEngine};\n(${nsfwWorkerMain})(${JSON.stringify(wasm)});`], { type: 'text/javascript' }));
  const spawn = async () => {
    const w = new Worker(src);
    const backend = await withTimeout(new Promise((res, rej) => {
      w.onmessage = e => (e.data.ready ? res(e.data.ready) : rej(new Error(e.data.fail)));
      w.onerror = e => rej(e);
    }), 60000);
    const waiting = new Map(); let seq = 0;
    w.onmessage = e => { const cb = waiting.get(e.data.id); if (cb) { waiting.delete(e.data.id); cb(e.data); } };
    const run = async bmps => {
      const id = ++seq;
      const r = await withTimeout(new Promise(res => { waiting.set(id, res); w.postMessage({ id, bmps }, bmps); }), 45000);
      if (r.error) throw new Error(r.error);
      return r.res;
    };
    return { backend, run, busy: false, w };
  };
  const first = await spawn();
  ADULT.backend = first.backend;
  const extra = first.backend === 'webgl' ? 0 : Math.max(0, Math.min(3, (navigator.hardwareConcurrency || 2) - 1) - 1);
  const pool = [first, ...(await Promise.all(Array.from({ length: extra }, () => spawn().catch(() => null)))).filter(Boolean)];
  return {
    size: pool.length,
    classify: async bmps => {
      const wk = pool.find(x => !x.busy) || pool[0];
      wk.busy = true;
      try { return await wk.run(bmps); } finally { wk.busy = false; }
    },
  };
}
async function startInPage() {
  for (const s of MODEL_FILES) await loadScript(s);
  const eng = nsfwEngine(new URL('vendor/', location.href).href);
  ADULT.backend = await withTimeout(eng.ready, 60000);
  return { size: 1, classify: bmps => withTimeout(eng.run(bmps), 45000) };
}
async function loadModel() {
  if (ADULT.model) return ADULT.model;
  if (!ADULT.loading) ADULT.loading = startWorkers().catch(() => startInPage());
  ADULT.model = await ADULT.loading;
  return ADULT.model;
}
// Small thumbnail → 224×224 bitmap, decoded off the main thread.
async function prepBitmap(it) {
  const u = it.src ? nativeThumbURL(it.src, it.mime, 256) : turl(it);
  const blob = await (await fetch(u)).blob();
  return createImageBitmap(blob, { resizeWidth: SIZE, resizeHeight: SIZE, resizeQuality: 'medium' });
}
// Next batch, newest photos first (what the library shows first).
async function nextBatch() {
  const its = [];
  for (let k = S.items.length - 1; k >= 0 && its.length < BATCH; k--) {
    const it = S.items[k];
    if (needsCheck(it) && !it._checking) { it._checking = true; its.push(it); }
  }
  const bmps = await Promise.all(its.map(it => prepBitmap(it).catch(() => null)));
  return { its, bmps };
}

function scheduleAdultScan() {
  if (ADULT.running || !S.items.some(needsCheck)) return;
  ADULT.running = true;
  setTimeout(runAdultScan, 600);
}
async function runAdultScan() {
  let found = 0;
  ADULT.done = 0; ADULT.total = S.items.filter(needsCheck).length;
  updateBlurProgress();
  try {
    const pool = await loadModel();
    const handle = async cur => {
      const okIdx = cur.bmps.map((b, i) => (b ? i : -1)).filter(i => i >= 0);
      const res = okIdx.length ? await pool.classify(okIdx.map(i => cur.bmps[i])) : [];
      const score = new Map(okIdx.map((i, k) => [i, res[k]]));
      cur.its.forEach((it, i) => {
        const p = score.get(i);
        it.nsfw = p ? Math.round(Math.max(p[1] + p[3], p[4] * 0.57) * 100) / 100 : -1;
        it.nsfwV = NSFW_V; delete it._checking;
        if (isAdult(it)) found++;
        patchCells(it);
      });
      ADULT.done += cur.its.length;
      updateBlurProgress();
      saveItems(cur.its);
    };
    // keep every checker busy: while one batch is checked, the next ones are already being prepared
    const inflight = new Set();
    for (;;) {
      while (inflight.size < pool.size + 1) {
        const cur = await nextBatch();
        if (!cur.its.length) break;
        const pr = handle(cur).finally(() => inflight.delete(pr));
        inflight.add(pr);
      }
      if (!inflight.size) break;
      await Promise.race(inflight);
    }
  } catch (e) {
    ADULT.failed = true;
    S.items.forEach(i => { delete i._checking; });
    toast('Автоперевірка 18+ не запустилась. Позначайте вручну: утримання фото → «Позначити 18+»', 'info');
  } finally {
    ADULT.running = false;
    updateBlurProgress();
    invalidate();
    if (found && blur18On()) toast(`Знайдено й розмито 18+: ${found}`, 'eyeOff');
    if (!ADULT.failed && S.items.some(needsCheck)) scheduleAdultScan(); // photos added meanwhile
  }
}
