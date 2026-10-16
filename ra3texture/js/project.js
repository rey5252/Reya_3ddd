(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');
  const layers = RA3.layers || require('./layers.js');
  const generators = RA3.generators || require('./generators.js');

  // Файл проєкту — JSON: усе, крім піксельних буферів, як є; буфери — base64(deflate(байти)).
  // Пишеться завжди версія 2 (стек шарів); версія 1 (шари-генератори + оверлеї пензля) відкривається з конвертацією.
  const FORMAT = 'ra3texture';
  const VERSION = 2;
  const MAX_SIZE = 4096;                              // більший документ із крихітного файлу не створюється
  const CHANNELS = layers.CHANNELS;
  const V1_TARGETS = ['color', 'alpha', 'height', 'spec', 'team'];
  const V1_PAINT_NAME = 'Малювання (з v1)';

  // ---------- Помилки ----------

  function corrupt(detail) { return new Error('Пошкоджений файл проєкту: ' + detail); }
  function badDoc(detail) { return new Error('Некоректний документ: ' + detail); }
  // Потоки стиснення в Node кладуть подробиці в cause, а message лишають порожнім.
  function reason(e) { return (e && (e.message || (e.cause && e.cause.message) || e.name)) || String(e); }
  function isObject(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

  // ---------- Base64 ----------

  // Шматки кратні 3 байтам (при кодуванні) і 4 символам (при розкодуванні): проміжні шматки не мають «=»,
  // а рядок складається з масиву шматків, тож без квадратичної конкатенації й без переповнення стека в apply.
  const ENC_STEP = 3 * 8192, DEC_STEP = 4 * 8192;

  function toBase64(bytes) {
    const parts = [];
    for (let i = 0; i < bytes.length; i += ENC_STEP) {
      parts.push(btoa(String.fromCharCode.apply(null, bytes.subarray(i, Math.min(i + ENC_STEP, bytes.length)))));
    }
    return parts.join('');
  }

  function fromBase64(str) {
    if (typeof str !== 'string') throw new Error('очікується рядок base64');
    const len = str.length;
    if (len % 4 !== 0) throw new Error('довжина base64 (' + len + ') не кратна 4');
    const pad = str.endsWith('==') ? 2 : (str.endsWith('=') ? 1 : 0);
    const out = new Uint8Array(len / 4 * 3 - pad);
    let o = 0;
    for (let i = 0; i < len; i += DEC_STEP) {
      const bin = atob(str.slice(i, i + DEC_STEP)); // викидає виняток на сторонньому символі
      if (o + bin.length > out.length) throw new Error('base64 містить зайві символи');
      for (let k = 0; k < bin.length; k++) out[o + k] = bin.charCodeAt(k);
      o += bin.length;
    }
    if (o !== out.length) throw new Error('base64 містить зайві символи');
    return out;
  }

  // ---------- Стиснення ----------

  function needStreams() {
    if (typeof CompressionStream === 'undefined' || typeof DecompressionStream === 'undefined' || typeof ReadableStream === 'undefined') {
      throw new Error('Цей браузер не підтримує CompressionStream, тож проєкт не можна зберегти чи відкрити');
    }
  }

  function source(bytes) { return new ReadableStream({ start(c) { c.enqueue(bytes); c.close(); } }); }

  function join(chunks, total) {
    const out = new Uint8Array(total);
    let o = 0;
    for (const c of chunks) { out.set(c, o); o += c.length; }
    return out;
  }

  async function deflate(bytes) {
    const reader = source(bytes).pipeThrough(new CompressionStream('deflate')).getReader();
    const chunks = [];
    let total = 0;
    for (;;) {
      const r = await reader.read();
      if (r.done) break;
      chunks.push(r.value); total += r.value.length;
    }
    return join(chunks, total);
  }

  // Розпаковує рівно `expected` байтів. Зайві дані відсікаються одразу, не накопичуючись у пам'яті.
  async function inflate(bytes, expected) {
    const reader = source(bytes).pipeThrough(new DecompressionStream('deflate')).getReader();
    const chunks = [];
    let total = 0;
    try {
      for (;;) {
        const r = await reader.read();
        if (r.done) break;
        total += r.value.length;
        if (total > expected) throw new Error('розпаковано більше, ніж очікується (' + expected + ' байт)');
        chunks.push(r.value);
      }
    } catch (e) {
      reader.cancel().catch(() => {});
      throw e;
    }
    if (total !== expected) throw new Error('розпаковано ' + total + ' байт, очікується ' + expected);
    return join(chunks, total);
  }

  // Завдання йдуть одночасно (потоки стиснення працюють поза основним потоком), результати — у порядку завдань.
  // При збої видається перша помилка за порядком, а не за часом завершення, тож повідомлення детерміноване.
  async function all(tasks) {
    const res = await Promise.allSettled(tasks);
    for (const r of res) if (r.status === 'rejected') throw r.reason;
    return res.map(r => r.value);
  }

  async function encodeBuffer(bytes) { return toBase64(await deflate(bytes)); }

  async function decodeBuffer(str, expected, what) {
    try {
      const bytes = await inflate(fromBase64(str), expected);
      return new Uint8ClampedArray(bytes.buffer);
    } catch (e) {
      throw corrupt(what + ': ' + reason(e));
    }
  }

  // ---------- Перевірка шарів ----------

  const procTypes = new Set(generators.catalog.map(c => c.type));
  const isNum = v => typeof v === 'number' && Number.isFinite(v);
  const isBool = v => typeof v === 'boolean';
  const chLen = (c, n) => c === 'color' ? n * 4 : n;

  // Перевіряє все, крім піксельних буферів (paint і mask.paint): ті самі правила при збереженні (fail = badDoc)
  // і при відкритті (fail = corrupt), тож збережений файл завжди відкривається.
  function checkLayerMeta(L, what, fail) {
    if (!isObject(L)) throw fail(what + ' не є об\'єктом');
    if (typeof L.id !== 'string' || L.id === '') throw fail(what + ': немає id');
    if (typeof L.name !== 'string') throw fail(what + ': немає назви');
    if (layers.KINDS.indexOf(L.kind) < 0) throw fail(what + ': невідомий вид шару ' + JSON.stringify(L.kind));
    if (!isBool(L.visible)) throw fail(what + ': некоректна видимість');
    if (!isNum(L.opacity) || L.opacity < 0 || L.opacity > 1) throw fail(what + ': некоректна непрозорість');
    if (!isObject(L.channels) || !CHANNELS.every(c => isBool(L.channels[c]))) throw fail(what + ': некоректні канали');
    if (L.heightMode !== 'add' && L.heightMode !== 'replace') throw fail(what + ': некоректний режим висоти');
    if (L.kind === 'fill') {
      const m = L.material;
      if (!isObject(m) || !Array.isArray(m.color) || m.color.length !== 3 || !m.color.every(isNum) ||
          !isNum(m.alpha) || !isNum(m.height) || !isNum(m.spec) || !isNum(m.team)) throw fail(what + ': некоректний матеріал');
    }
    if (L.kind === 'proc') {
      const p = L.proc;
      if (!isObject(p) || !procTypes.has(p.type)) throw fail(what + ': невідомий генератор ' + JSON.stringify(p && p.type));
      if (!isNum(p.seed) || !isObject(p.params)) throw fail(what + ': некоректні параметри генератора');
    }
    const k = L.mask;
    if (k !== null) {
      if (!isObject(k) || !isBool(k.enabled) || !isBool(k.invert)) throw fail(what + ': некоректна маска');
      if (k.gen !== null && !(isObject(k.gen) && typeof k.gen.type === 'string' && k.gen.type !== '')) throw fail(what + ': некоректний генератор маски');
    }
  }

  // ---------- Збереження ----------

  function checkBuffer(arr, len, what) {
    if (!(ArrayBuffer.isView(arr) && arr.BYTES_PER_ELEMENT === 1 && arr.length === len)) {
      throw badDoc(what + ' має містити ' + len + ' байт');
    }
  }

  // Неглибока копія шару, де paint і mask.paint замінено на задані значення (на тих самих місцях серед ключів).
  function withBuffers(layer, paint, maskPaint) {
    const out = {};
    for (const k of Object.keys(layer)) {
      if (k === 'paint') out.paint = paint;
      else if (k === 'mask' && isObject(layer.mask)) out.mask = Object.assign({}, layer.mask, { paint: maskPaint });
      else out[k] = layer[k];
    }
    return out;
  }

  // Повертає JSON-текст проєкту. Документ не змінюється. Файл — знімок документа на момент виклику: усе, що
  // потрапить у файл (поля й копії буферів), береться синхронно до першого await, тож мазки, зміна розміру чи
  // параметрів під час стиснення не роблять файл суперечливим.
  async function serialize(doc) {
    if (!doc || !util.isPow2(doc.size)) throw badDoc('розмір ' + (doc && doc.size) + ' (очікується степінь двійки)');
    const n = doc.size * doc.size;

    // Усе перевіряється до стиснення: файл, який потім не відкрити, не записується.
    if (typeof doc.name !== 'string') throw badDoc('немає назви');
    const s = doc.settings;
    if (!isObject(s) || !isNum(s.nrmStrength) || !isBool(s.invertY)) throw badDoc('некоректні налаштування');
    if (!isObject(doc.images)) throw badDoc('немає зображень');
    const ids = Object.keys(doc.images);
    for (const id of ids) checkBuffer(doc.images[id], n * 4, 'зображення «' + id + '»');
    if (doc.nrmImport) checkBuffer(doc.nrmImport, n * 4, 'імпортована NRM');
    if (!Array.isArray(doc.layers)) throw badDoc('немає списку шарів');
    const seen = new Set();
    const metas = doc.layers.map((layer, i) => {
      const what = 'шар ' + (i + 1);
      if (!isObject(layer)) throw badDoc(what + ' не є об\'єктом');
      // Буфери замінюються заповнювачами на тих самих місцях, тож порядок ключів у файлі такий самий, як у шарі.
      const meta = JSON.parse(JSON.stringify(withBuffers(layer, null, null)));
      if (meta.mask === undefined) meta.mask = null;
      if (isObject(meta.mask) && meta.mask.gen === undefined) meta.mask.gen = null;
      checkLayerMeta(meta, what, badDoc);
      if (seen.has(meta.id)) throw badDoc(what + ': повторюваний id ' + meta.id);
      seen.add(meta.id);
      const label = what + ' «' + meta.name + '»';
      if (layer.paint !== undefined) {
        if (!isObject(layer.paint)) throw badDoc(label + ': некоректні буфери малювання');
        for (const c of Object.keys(layer.paint)) {
          if (CHANNELS.indexOf(c) < 0) throw badDoc(label + ': невідомий канал малювання «' + c + '»');
          const P = layer.paint[c];
          if (!isObject(P)) throw badDoc(label + ': немає буферів каналу «' + c + '»');
          checkBuffer(P.value, chLen(c, n), label + ', канал «' + c + '» (value)');
          checkBuffer(P.cov, n, label + ', канал «' + c + '» (cov)');
        }
      } else if (meta.kind === 'paint') throw badDoc(label + ': немає буферів малювання');
      if (meta.mask && layer.mask.paint !== null && layer.mask.paint !== undefined) checkBuffer(layer.mask.paint, n, label + ', малюнок маски');
      return meta;
    });

    needStreams();
    const meta = JSON.parse(JSON.stringify({ name: doc.name, size: doc.size, settings: doc.settings }));
    const hasNrm = !!doc.nrmImport;
    const bufs = ids.map(id => new Uint8Array(doc.images[id]));                 // копії, а не вигляди на живі буфери
    if (hasNrm) bufs.push(new Uint8Array(doc.nrmImport));
    const plan = doc.layers.map(layer => {
      const chans = layer.paint ? Object.keys(layer.paint) : null;               // усі ключі вже перевірено
      if (chans) for (const c of chans) bufs.push(new Uint8Array(layer.paint[c].value), new Uint8Array(layer.paint[c].cov));
      const mp = !!(layer.mask && layer.mask.paint);
      if (mp) bufs.push(new Uint8Array(layer.mask.paint));
      return { chans, mp };
    });
    const enc = await all(bufs.map(encodeBuffer));
    let k = 0;   // розкладання в тому ж порядку, у якому збирався bufs
    const images = {};
    for (const id of ids) images[id] = enc[k++];
    const nrmImport = hasNrm ? enc[k++] : null;
    const fileLayers = metas.map((m, i) => {
      const { chans, mp } = plan[i];
      if (chans) {
        m.paint = {};
        for (const c of chans) { m.paint[c] = { value: enc[k], cov: enc[k + 1] }; k += 2; }
      }
      if (m.mask) m.mask.paint = mp ? enc[k++] : null;
      return m;
    });
    return JSON.stringify({ format: FORMAT, version: VERSION, name: meta.name, size: meta.size, settings: meta.settings, nrmImport, images, layers: fileLayers });
  }

  // ---------- Відкриття ----------

  // Спершу читається й перевіряється все, і лише потім збирається документ: напівготового результату не буває.
  // JSON.parse щоразу дає свіжі об'єкти, тож шари й налаштування документа ні з чим не спільні.
  // maps документа в базовому стані: після відкриття потрібен layers.composite.
  async function deserialize(json) {
    if (typeof json !== 'string') throw corrupt('очікується текст JSON');
    let data;
    try { data = JSON.parse(json); } catch (e) { throw corrupt('некоректний JSON (' + reason(e) + ')'); }
    if (!isObject(data) || data.format !== FORMAT || (data.version !== 1 && data.version !== 2)) throw new Error('Непідтримуваний формат проєкту');

    const size = data.size;
    if (!util.isPow2(size) || size > MAX_SIZE) throw corrupt('Некоректний розмір документа: ' + JSON.stringify(size) + ' (очікується степінь двійки до ' + MAX_SIZE + ')');
    if (typeof data.name !== 'string') throw corrupt('немає назви документа');
    const s = data.settings;
    if (!isObject(s) || !isNum(s.nrmStrength) || !isBool(s.invertY)) throw corrupt('некоректні налаштування');
    if (!isObject(data.images)) throw corrupt('немає зображень');
    if (data.nrmImport !== null && typeof data.nrmImport !== 'string') throw corrupt('некоректна імпортована NRM');
    if (!Array.isArray(data.layers) || !data.layers.every(isObject)) throw corrupt('некоректний список шарів');

    const n = size * size, imageIds = Object.keys(data.images), jobs = [];
    for (const id of imageIds) jobs.push([data.images[id], n * 4, 'зображення «' + id + '»']);
    if (data.nrmImport !== null) jobs.push([data.nrmImport, n * 4, 'імпортована NRM']);
    const build = data.version === 1 ? planV1(data, n, jobs) : planV2(data, n, jobs);

    needStreams();
    const dec = await all(jobs.map(j => decodeBuffer(j[0], j[1], j[2])));
    let k = 0;   // розкладання в тому ж порядку, у якому збирався jobs
    const images = {};
    for (const id of imageIds) images[id] = dec[k++];
    const nrmImport = data.nrmImport === null ? null : dec[k++];
    const docLayers = build(dec, k);

    let doc;
    try { doc = layers.createDocument({ name: data.name, size }); } catch (e) { throw corrupt(reason(e)); }
    doc.settings = s; doc.images = images; doc.nrmImport = nrmImport; doc.layers = docLayers;
    return doc;
  }

  // Версія 2: шари як є, буфери paint[канал] і mask.paint розкодовуються.
  function planV2(data, n, jobs) {
    const seen = new Set(), plan = [];
    data.layers.forEach((L, i) => {
      const what = 'шар ' + (i + 1);
      checkLayerMeta(L, what, corrupt);
      if (seen.has(L.id)) throw corrupt(what + ': повторюваний id ' + L.id);
      seen.add(L.id);
      let chans = null;
      if (L.paint !== undefined) {
        if (!isObject(L.paint)) throw corrupt(what + ': некоректні буфери малювання');
        chans = Object.keys(L.paint);
        for (const c of chans) {
          if (CHANNELS.indexOf(c) < 0) throw corrupt(what + ': невідомий канал малювання «' + c + '»');
          const P = L.paint[c];
          if (!isObject(P) || typeof P.value !== 'string' || typeof P.cov !== 'string') throw corrupt(what + ': немає буферів каналу «' + c + '»');
          jobs.push([P.value, chLen(c, n), what + ', канал «' + c + '» (value)'], [P.cov, n, what + ', канал «' + c + '» (cov)']);
        }
      } else if (L.kind === 'paint') throw corrupt(what + ': немає буферів малювання');
      const mp = L.mask ? L.mask.paint : null;
      if (L.mask && mp !== null && typeof mp !== 'string') throw corrupt(what + ': некоректний малюнок маски');
      if (mp !== null) jobs.push([mp, n, what + ', малюнок маски']);
      plan.push({ chans, mp: mp !== null });
    });
    return (dec, k) => data.layers.map((L, i) => {
      const { chans, mp } = plan[i];
      let paint;
      if (chans) {
        paint = {};
        for (const c of chans) { paint[c] = { value: dec[k], cov: dec[k + 1] }; k += 2; }
      }
      return withBuffers(L, paint, mp ? dec[k++] : null);
    });
  }

  // Версія 1: кожен шар-генератор -> шар 'proc' (enabled -> visible); оверлеї пензля з покриттям -> один шар
  // «Малювання (з v1)» нагорі. Висота оверлеїв у v1 замінювала карту, тож шар має heightMode 'replace'.
  function planV1(data, n, jobs) {
    data.layers.forEach((l, i) => {
      if (!procTypes.has(l.type)) throw corrupt('шар ' + (i + 1) + ': невідомий тип шару ' + JSON.stringify(l.type));
      if (l.params !== undefined && !isObject(l.params)) throw corrupt('шар ' + (i + 1) + ': некоректні параметри');
    });
    if (!isObject(data.overlays)) throw corrupt('немає оверлеїв пензля');
    for (const t of V1_TARGETS) {
      const ov = data.overlays[t];
      if (!isObject(ov) || typeof ov.value !== 'string' || typeof ov.cov !== 'string') throw corrupt('немає оверлею «' + t + '»');
      jobs.push([ov.value, t === 'color' ? n * 4 : n, 'оверлей «' + t + '» (value)'], [ov.cov, n, 'оверлей «' + t + '» (cov)']);
    }
    return (dec, k) => {
      const out = data.layers.map(l => layers.newLayer('proc', {
        proc: { type: l.type, seed: isNum(l.seed) ? l.seed : 0, params: l.params || {} },
        name: typeof l.name === 'string' ? l.name : undefined,
        visible: !!l.enabled
      }));
      const ov = {};
      for (const t of V1_TARGETS) { ov[t] = { value: dec[k], cov: dec[k + 1] }; k += 2; }
      const covered = V1_TARGETS.filter(t => ov[t].cov.some(v => v > 0));
      if (covered.length) {
        const p = layers.newLayer('paint', { name: V1_PAINT_NAME, heightMode: 'replace', channels: { alpha: covered.indexOf('alpha') >= 0 } });
        for (const t of covered) p.paint[t] = ov[t];
        out.push(p);
      }
      return out;
    };
  }

  const api = { FORMAT, VERSION, serialize, deserialize };
  RA3.project = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
