(function (RA3) { 'use strict';
  const util = RA3.util || require('./util.js');
  const maps = RA3.maps || require('./maps.js');

  const FORMAT = 'ra3texture';
  const VERSION = 1;

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

  async function encodeBuffer(arr) {
    return toBase64(await deflate(new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength)));
  }

  async function decodeBuffer(str, expected, what) {
    try {
      const bytes = await inflate(fromBase64(str), expected);
      return new Uint8ClampedArray(bytes.buffer);
    } catch (e) {
      throw corrupt(what + ': ' + reason(e));
    }
  }

  // ---------- Збереження ----------

  function checkBuffer(arr, len, what) {
    if (!(ArrayBuffer.isView(arr) && arr.BYTES_PER_ELEMENT === 1 && arr.length === len)) {
      throw badDoc(what + ' має містити ' + len + ' байт');
    }
  }

  // Повертає JSON-текст проєкту. Документ не змінюється; шари й налаштування потрапляють у текст, тож копіюються.
  async function serialize(doc) {
    if (!doc || !util.isPow2(doc.size)) throw badDoc('розмір ' + (doc && doc.size) + ' (очікується степінь двійки)');
    const n = doc.size * doc.size;

    // Усе перевіряється до стиснення: файл, який потім не відкрити, не записується.
    const ids = Object.keys(doc.images);
    for (const id of ids) checkBuffer(doc.images[id], n * 4, 'зображення «' + id + '»');
    if (doc.nrmImport) checkBuffer(doc.nrmImport, n * 4, 'імпортована NRM');
    for (const t of maps.TARGETS) {
      const ov = doc.overlays && doc.overlays[t];
      checkBuffer(ov && ov.value, t === 'color' ? n * 4 : n, 'оверлей «' + t + '» (value)');
      checkBuffer(ov.cov, n, 'оверлей «' + t + '» (cov)');
    }

    needStreams();
    const bufs = ids.map(id => doc.images[id]);
    if (doc.nrmImport) bufs.push(doc.nrmImport);
    for (const t of maps.TARGETS) bufs.push(doc.overlays[t].value, doc.overlays[t].cov);
    const enc = await all(bufs.map(encodeBuffer));
    let k = 0;   // розкладання в тому ж порядку, у якому збирався bufs
    const images = {};
    for (const id of ids) images[id] = enc[k++];
    const nrmImport = doc.nrmImport ? enc[k++] : null;
    const overlays = {};
    for (const t of maps.TARGETS) { overlays[t] = { value: enc[k], cov: enc[k + 1] }; k += 2; }
    return JSON.stringify({ format: FORMAT, version: VERSION, name: doc.name, size: doc.size, settings: doc.settings, layers: doc.layers, images, nrmImport, overlays });
  }

  // ---------- Відкриття ----------

  // Спершу читається й перевіряється все, і лише потім збирається документ: напівготового результату не буває.
  // JSON.parse щоразу дає свіжі об'єкти, тож шари й налаштування документа ні з чим не спільні.
  // maps створює createDocument у базовому стані: після відкриття потрібен maps.rebuild.
  async function deserialize(json) {
    if (typeof json !== 'string') throw corrupt('очікується текст JSON');
    let data;
    try { data = JSON.parse(json); } catch (e) { throw corrupt('некоректний JSON (' + reason(e) + ')'); }
    if (!isObject(data) || data.format !== FORMAT || data.version !== VERSION) throw new Error('Непідтримуваний формат проєкту');

    const size = data.size;
    if (!util.isPow2(size)) throw corrupt('Некоректний розмір документа: ' + JSON.stringify(size) + ' (очікується степінь двійки)');
    if (typeof data.name !== 'string') throw corrupt('немає назви документа');
    const s = data.settings;
    if (!isObject(s) || !Number.isFinite(s.nrmStrength) || typeof s.invertY !== 'boolean') throw corrupt('некоректні налаштування');
    if (!Array.isArray(data.layers) || !data.layers.every(isObject)) throw corrupt('некоректний список шарів');
    if (!isObject(data.images)) throw corrupt('немає зображень');
    if (data.nrmImport !== null && typeof data.nrmImport !== 'string') throw corrupt('некоректна імпортована NRM');
    if (!isObject(data.overlays)) throw corrupt('немає оверлеїв пензля');
    for (const t of maps.TARGETS) {
      const ov = data.overlays[t];
      if (!isObject(ov) || typeof ov.value !== 'string' || typeof ov.cov !== 'string') throw corrupt('немає оверлею «' + t + '»');
    }

    needStreams();
    const n = size * size, imageIds = Object.keys(data.images), jobs = [];
    for (const id of imageIds) jobs.push([data.images[id], n * 4, 'зображення «' + id + '»']);
    if (data.nrmImport !== null) jobs.push([data.nrmImport, n * 4, 'імпортована NRM']);
    for (const t of maps.TARGETS) {
      jobs.push([data.overlays[t].value, t === 'color' ? n * 4 : n, 'оверлей «' + t + '» (value)']);
      jobs.push([data.overlays[t].cov, n, 'оверлей «' + t + '» (cov)']);
    }
    const dec = await all(jobs.map(j => decodeBuffer(j[0], j[1], j[2])));
    let k = 0;   // розкладання в тому ж порядку, у якому збирався jobs
    const images = {};
    for (const id of imageIds) images[id] = dec[k++];
    const nrmImport = data.nrmImport === null ? null : dec[k++];
    const overlays = {};
    for (const t of maps.TARGETS) { overlays[t] = { value: dec[k], cov: dec[k + 1] }; k += 2; }

    let doc;
    try { doc = maps.createDocument({ name: data.name, size }); } catch (e) { throw corrupt(reason(e)); }
    doc.settings = s; doc.layers = data.layers;
    doc.images = images; doc.nrmImport = nrmImport; doc.overlays = overlays;
    return doc;
  }

  const api = { FORMAT, VERSION, serialize, deserialize };
  RA3.project = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
