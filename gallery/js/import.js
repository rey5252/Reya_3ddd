'use strict';
// ---------- reading files ----------
const MEDIA_EXT = { jpg: 'image', jpeg: 'image', png: 'image', gif: 'image', webp: 'image', heic: 'image', heif: 'image', avif: 'image', bmp: 'image', mp4: 'video', mov: 'video', m4v: 'video', webm: 'video', '3gp': 'video', mkv: 'video' };
function mediaKind(f) {
  const t = f.type || '';
  if (t.startsWith('image/') && t !== 'image/svg+xml') return 'image';
  if (t.startsWith('video/')) return 'video';
  return MEDIA_EXT[ext(f.name).toLowerCase()] || null;
}
const fkey = f => `${f.name}|${f.size}|${f.lastModified}`;

function scaleToBlob(src, w, hh, short = 400) {
  if (!w || !hh) return Promise.resolve(null);
  const s = Math.min(1, short / Math.min(w, hh));
  const c = document.createElement('canvas');
  c.width = Math.max(1, Math.round(w * s)); c.height = Math.max(1, Math.round(hh * s));
  const g = c.getContext('2d');
  g.fillStyle = '#ffffff'; g.fillRect(0, 0, c.width, c.height);
  g.imageSmoothingQuality = 'high';
  g.drawImage(src, 0, 0, c.width, c.height);
  return new Promise(r => c.toBlob(b => r(b), 'image/jpeg', .82));
}
async function imageInfo(blob) {
  const url = URL.createObjectURL(blob);
  try {
    const img = new Image(); img.src = url; await img.decode();
    const w = img.naturalWidth, hh = img.naturalHeight;
    return { w, h: hh, thumb: await scaleToBlob(img, w, hh) };
  } finally { URL.revokeObjectURL(url); }
}
function videoInfo(blob) {
  return new Promise(res => {
    const url = URL.createObjectURL(blob), v = document.createElement('video');
    v.muted = true; v.playsInline = true; v.preload = 'auto';
    let done = false;
    const fin = r => { if (done) return; done = true; clearTimeout(to); v.removeAttribute('src'); v.load(); URL.revokeObjectURL(url); res(r); };
    const basic = () => ({ w: v.videoWidth || 0, h: v.videoHeight || 0, duration: isFinite(v.duration) ? v.duration : 0, thumb: null });
    const to = setTimeout(() => fin(basic()), 7000);
    v.onloadedmetadata = () => { try { v.currentTime = Math.min(1, (isFinite(v.duration) ? v.duration : 2) * .1); } catch (e) { fin(basic()); } };
    v.onseeked = async () => { try { fin({ ...basic(), thumb: await scaleToBlob(v, v.videoWidth, v.videoHeight) }); } catch (e) { fin(basic()); } };
    v.onerror = () => fin(basic());
    v.src = url;
  });
}
// Reads DateTimeOriginal from a JPEG's EXIF block so photos sort by when they were taken.
async function exifDate(file) {
  try {
    if (!/\.jpe?g$/i.test(file.name) && file.type !== 'image/jpeg') return null;
    const v = new DataView(await file.slice(0, 131072).arrayBuffer());
    if (v.byteLength < 4 || v.getUint16(0) !== 0xFFD8) return null;
    let o = 2;
    while (o + 4 < v.byteLength) {
      const m = v.getUint16(o);
      if (m === 0xFFE1 && v.getUint32(o + 4) === 0x45786966) return parseTiff(v, o + 10);
      if ((m & 0xFF00) !== 0xFF00) break;
      o += 2 + v.getUint16(o + 2);
    }
  } catch (e) {}
  return null;
}
function parseTiff(v, t) {
  const le = v.getUint16(t) === 0x4949, u16 = p => v.getUint16(p, le), u32 = p => v.getUint32(p, le);
  const tags = ifd => { const n = u16(ifd), out = {}; for (let i = 0; i < n; i++) { const e = ifd + 2 + i * 12; if (e + 12 > v.byteLength) break; out[u16(e)] = { count: u32(e + 4), off: e + 8 }; } return out; };
  const str = tag => { const p = tag.count > 4 ? t + u32(tag.off) : tag.off; let s = ''; for (let i = 0; i < tag.count - 1 && p + i < v.byteLength; i++) s += String.fromCharCode(v.getUint8(p + i)); return s; };
  const t0 = tags(t + u32(t + 4));
  let dt = null;
  if (t0[0x8769]) { const ex = tags(t + u32(t0[0x8769].off)); if (ex[0x9003]) dt = str(ex[0x9003]); }
  if (!dt && t0[0x0132]) dt = str(t0[0x0132]);
  const m = dt && dt.match(/(\d{4}):(\d{2}):(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
  if (!m) return null;
  const d = new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5], +m[6]);
  return isNaN(d) ? null : d.getTime();
}
async function makeItem(file, path) {
  const kind = mediaKind(file);
  const it = { id: uid(), name: file.name, kind, mime: file.type || '', size: file.size, w: 0, h: 0, duration: 0, date: file.lastModified || Date.now(), added: Date.now(), path: path || '', fav: false, deleted: null, sample: false, fkey: fkey(file), thumb: null, _blob: file };
  try {
    if (kind === 'image') { Object.assign(it, await imageInfo(file)); const d = await exifDate(file); if (d) it.date = d; }
    else Object.assign(it, await videoInfo(file));
  } catch (e) {}
  return it;
}

// ---------- import sheet ----------
const IMP = { queue: [], mode: null, albumName: '', dedupe: true, target: {}, sheet: null, running: false, urls: [] };
const impMode = () => IMP.target.album ? 'album' : (IMP.mode || (IMP.queue.some(s => s.kind === 'folder') ? 'structure' : 'library'));
const defaultAlbumName = () => `Імпорт ${fmtDate(Date.now())}`;
const DIR_OK = !!NATIVE || 'webkitdirectory' in document.createElement('input');

function openImport(target = {}) {
  if (IMP.sheet) return;
  Object.assign(IMP, { queue: [], mode: null, albumName: '', target: target || {}, running: false });
  const sh = IMP.sheet = openSheet({ title: 'Додати медіа', cls: 'tall' });
  sh.setLeft('Скасувати', () => sh.close());
  sh.onClose = () => { IMP.sheet = null; IMP.urls.forEach(u => URL.revokeObjectURL(u)); IMP.urls = []; };
  sh.body.addEventListener('click', onImpClick);
  sh.body.addEventListener('change', e => { if (e.target.id === 'imp-dedupe') IMP.dedupe = e.target.checked; });
  sh.body.addEventListener('input', e => { if (e.target.id === 'imp-album-name') IMP.albumName = e.target.value; });
  renderImport();
}
const ensureImportOpen = () => { if (!IMP.sheet) openImport(); };
function pickInput(id) {
  if (id === '#in-dir' && NATIVE) { NATIVE.pickFolder(); return; }
  const i = $(id); i.value = ''; i.click();
}

// Android app: the system folder picker returns a file list; the bytes are fetched
// from the app's own /saf endpoint and turned into File objects with folder paths.
window.__onNativeFolderStart = () => toast('Шукаю фото й відео…', 'folder');
window.__onNativeFolder = async data => {
  if (!data) return;
  if (data.error) { toast(data.error === 'unsupported' ? 'На цьому телефоні не можна вибрати папку' : 'Не вдалося прочитати папку', 'info'); return; }
  if (!data.files || !data.files.length) { toast(`У папці «${data.root}» немає фото чи відео`, 'info'); return; }
  ensureImportOpen();
  const list = data.files, out = new Array(list.length);
  let next = 0, done = 0;
  const t = h(`<div class="toast" role="status">${I.folder}<span></span></div>`), span = $('span', t);
  document.body.append(t);
  const tick = () => { span.textContent = `Читаю «${data.root}»: ${done} з ${list.length}`; };
  tick();
  const worker = async () => {
    while (next < list.length) {
      const k = next++, f = list[k];
      try {
        const b = await (await fetch('/saf?u=' + encodeURIComponent(f.u))).blob();
        const file = new File([b], f.name, { type: f.mime || b.type, lastModified: f.mod || Date.now() });
        Object.defineProperty(file, 'webkitRelativePath', { value: f.rel });
        out[k] = file;
      } catch (e) {}
      done++; if (done % 5 === 0 || done === list.length) tick();
    }
  };
  await Promise.all([worker(), worker(), worker(), worker()]);
  t.remove();
  addDirSource(out.filter(Boolean));
};

function qRow(s) {
  const imgs = s.files.filter(f => mediaKind(f.file) === 'image');
  const thumbs = imgs.slice(0, 3).map(f => { if (!f.url) { f.url = URL.createObjectURL(f.file); IMP.urls.push(f.url); } return `<img src="${f.url}" alt="">`; }).join('');
  const v = s.files.length - imgs.length, dirs = s.kind === 'folder' ? new Set(s.files.map(f => f.path)).size : 0;
  const detail = [imgs.length ? `${imgs.length} фото` : '', v ? `${v} відео` : '', dirs > 1 ? `${dirs} ${plural(dirs, 'папка', 'папки', 'папок')} з медіа` : ''].filter(Boolean).join(' · ');
  return `<div class="row q-row">${s.kind === 'folder' ? I.folder : I.photo}<span class="r-label"><b>${esc(s.name)}</b><small>${detail}</small></span><span class="q-thumbs">${thumbs}</span><button class="q-x" data-rm="${s.id}" aria-label="Прибрати ${esc(s.name)}">${I.x}</button></div>`;
}
const opt = (val, title, sub, mode, disabled) => `<button class="row opt${mode === val ? ' on' : ''}" data-mode="${val}" ${disabled ? 'disabled' : ''} role="radio" aria-checked="${mode === val}"><span class="radio"></span><span class="r-label"><b>${title}</b>${sub ? `<small>${sub}</small>` : ''}</span></button>`;

function renderImport() {
  const sh = IMP.sheet; if (!sh || IMP.running) return;
  const total = IMP.queue.reduce((n, s) => n + s.files.length, 0), mode = impMode();
  const hasFolder = IMP.queue.some(s => s.kind === 'folder');
  const tAlbum = IMP.target.album && albumById(IMP.target.album), tFolder = IMP.target.folder && folderById(IMP.target.folder);
  sh.body.innerHTML = `
    <div class="imp-pick">
      <button class="imp-big" data-imp="files">${I.photo}<b>Фото й відео</b><small>Окремі файли — скільки завгодно за раз</small></button>
      <button class="imp-big" data-imp="dir" ${DIR_OK ? '' : 'disabled'}>${I.folder}<b>Папку</b><small>${DIR_OK ? 'Разом з підпапками. Додавайте кілька по черзі' : 'Цей браузер не дає вибрати папку'}</small></button>
    </div>
    ${NATIVE && NATIVE.pickDownloads ? `<button class="imp-big wide" data-imp="downloads">${I.download}<span><b>Папка Download</b><small>Усі фото й відео із «Завантажень», разом з підпапками (Telegram, браузер тощо)</small></span></button>` : ''}
    <div class="imp-drop">Або перетягніть сюди файли й папки — одразу кілька</div>
    <div class="group-h">Вибрано${total ? ` · ${nItems(total)}` : ''}</div>
    ${IMP.queue.length ? `<div class="rows q-list">${IMP.queue.map(qRow).join('')}</div>` : '<div class="q-empty">Ще нічого не вибрано. Додавайте папки й файли по черзі — усе збереться в цьому списку, а потім імпортується разом.</div>'}
    ${tAlbum ? `<div class="group-h">Куди</div><div class="rows"><div class="row">${I.albums}<span class="r-label">В альбом «${esc(tAlbum.name)}»</span></div></div>` : `
    <div class="group-h">Як упорядкувати${tFolder ? ` · у папці «${esc(tFolder.name)}»` : ''}</div>
    <div class="rows opts" role="radiogroup">
      ${opt('structure', 'Зберегти структуру папок', 'Папка стане папкою, а кожна підпапка з фото — альбомом', mode, !hasFolder)}
      ${opt('album', 'Усе в один новий альбом', '', mode)}
      ${mode === 'album' ? `<div class="row opt-input"><input id="imp-album-name" type="text" maxlength="80" placeholder="${esc(defaultAlbumName())}" value="${esc(IMP.albumName)}" aria-label="Назва альбому"></div>` : ''}
      ${opt('library', 'Лише в Медіатеку', 'Без альбомів', mode)}
    </div>`}
    <div class="group-h"></div>
    <div class="rows"><label class="row"><span class="r-label">Пропускати дублікати</span><span class="switch"><input type="checkbox" id="imp-dedupe" ${IMP.dedupe ? 'checked' : ''}><i></i></span></label></div>
    <button class="btn-primary" data-imp="go" ${total ? '' : 'disabled'}>${total ? `Імпортувати ${nItems(total)}` : 'Імпортувати'}</button>
    <p class="imp-note">Файли зберігаються лише на цьому пристрої, у пам’яті програми.</p>`;
  $$('.q-thumbs img', sh.body).forEach(i => { i.onerror = () => i.remove(); });
}
function bounceLast() {
  const r = IMP.sheet && $$('.q-row', IMP.sheet.body).pop();
  if (r && !RM) r.animate([{ transform: 'scale(.9)', opacity: 0 }, { transform: 'none', opacity: 1 }], { duration: 420, easing: SPRING });
}
function onImpClick(e) {
  const b = e.target.closest('button'); if (!b || b.disabled) return;
  if (b.dataset.imp === 'files') pickInput('#in-files');
  else if (b.dataset.imp === 'dir') pickInput('#in-dir');
  else if (b.dataset.imp === 'downloads') NATIVE.pickDownloads();
  else if (b.dataset.imp === 'go') runImport();
  else if (b.dataset.imp === 'done') IMP.sheet && IMP.sheet.close();
  else if (b.dataset.rm) { IMP.queue = IMP.queue.filter(s => s.id !== b.dataset.rm); renderImport(); }
  else if (b.dataset.mode) { IMP.mode = b.dataset.mode; renderImport(); if (IMP.mode === 'album') $('#imp-album-name')?.focus(); }
}

function addFilesSource(files) {
  const ok = files.filter(f => mediaKind(f) && !f.name.startsWith('.'));
  if (!ok.length) { toast('Серед вибраного немає фото чи відео', 'info'); return; }
  IMP.queue.push({ id: uid(), kind: 'files', name: ok.length === 1 ? ok[0].name : `${ok.length} ${plural(ok.length, 'файл', 'файли', 'файлів')}`, files: ok.map(file => ({ file, path: '' })) });
  if (ok.length < files.length) toast(`Пропущено файлів не з фото: ${files.length - ok.length}`, 'info');
  renderImport(); bounceLast();
}
function addDirSource(files) {
  const groups = new Map(), loose = [];
  for (const f of files) {
    const parts = (f.webkitRelativePath || '').split('/');
    if (parts.length < 2) { loose.push(f); continue; }
    if (parts.some(p => p.startsWith('.')) || !mediaKind(f)) continue;
    if (!groups.has(parts[0])) groups.set(parts[0], []);
    groups.get(parts[0]).push({ file: f, path: parts.slice(0, -1).join('/') });
  }
  let added = 0;
  for (const [root, list] of groups) {
    if (IMP.queue.some(s => s.kind === 'folder' && s.name === root && s.files.length === list.length)) { toast(`Папку «${root}» уже вибрано`, 'info'); continue; }
    IMP.queue.push({ id: uid(), kind: 'folder', name: root, files: list }); added++;
  }
  if (loose.length) { addFilesSource(loose); return; }
  if (!added && !groups.size) toast('У цій папці немає фото чи відео', 'info');
  renderImport(); bounceLast();
}
async function walkEntry(dir, path, out) {
  const reader = dir.createReader();
  for (;;) {
    const batch = await new Promise(res => reader.readEntries(res, () => res([])));
    if (!batch.length) break;
    for (const e of batch) {
      if (e.name.startsWith('.')) continue;
      if (e.isFile) { try { out.push({ file: await new Promise((res, rej) => e.file(res, rej)), path }); } catch (err) {} }
      else if (e.isDirectory) await walkEntry(e, path + '/' + e.name, out);
    }
  }
}
async function readDrop(dt) {
  const entries = [...(dt.items || [])].filter(i => i.kind === 'file').map(i => (i.webkitGetAsEntry ? i.webkitGetAsEntry() : null));
  if (!entries.length || entries.some(x => !x)) { addFilesSource([...dt.files]); return; }
  const loose = [];
  for (const en of entries) {
    if (en.isDirectory) {
      const list = []; await walkEntry(en, en.name, list);
      const media = list.filter(x => mediaKind(x.file));
      if (media.length) IMP.queue.push({ id: uid(), kind: 'folder', name: en.name, files: media });
      else toast(`У папці «${en.name}» немає фото чи відео`, 'info');
    } else { try { loose.push(await new Promise((res, rej) => en.file(res, rej))); } catch (e) {} }
  }
  if (loose.length) addFilesSource(loose); else { renderImport(); bounceLast(); }
}
function initDrop() {
  const ov = $('#drop'); let depth = 0;
  const hasFiles = e => e.dataTransfer && [...(e.dataTransfer.types || [])].includes('Files');
  addEventListener('dragenter', e => { if (!hasFiles(e)) return; e.preventDefault(); depth++; ov.hidden = false; });
  addEventListener('dragover', e => { if (!hasFiles(e)) return; e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; });
  addEventListener('dragleave', e => { if (!hasFiles(e)) return; depth = Math.max(0, depth - 1); if (!depth) ov.hidden = true; });
  addEventListener('drop', e => { if (!hasFiles(e)) return; e.preventDefault(); depth = 0; ov.hidden = true; ensureImportOpen(); readDrop(e.dataTransfer); });
}

// Folder "Trip/Day 1/*.jpg" → folder "Trip" with album "Day 1". A folder that has photos
// of its own *and* subfolders gets an album with its own name inside it.
function ensureFolder(name, parent) { return S.folders.find(f => f.name === name && f.parent === parent && !f.sample) || createFolder(name, parent); }
function ensureAlbum(name, parent) { return S.albums.find(a => a.name === name && a.parent === parent && !a.sample) || createAlbum(name, parent); }
function buildStructure(queue, parent) {
  const map = new Map();
  for (const s of queue) {
    if (s.kind !== 'folder') continue;
    const dirs = [...new Set(s.files.map(f => f.path))];
    const hasKids = d => dirs.some(x => x !== d && x.startsWith(d + '/'));
    for (const d of dirs) {
      const segs = d.split('/'), folderSegs = hasKids(d) ? segs : segs.slice(0, -1);
      let p = parent;
      for (const seg of folderSegs) p = ensureFolder(seg, p).id;
      map.set(d, ensureAlbum(segs[segs.length - 1], p));
    }
  }
  return map;
}

async function runImport() {
  if (IMP.running || !IMP.queue.length) return;
  IMP.running = true;
  const sh = IMP.sheet, mode = impMode();
  const jobs = []; for (const s of IMP.queue) for (const f of s.files) jobs.push({ ...f, src: s });
  const total = jobs.length;
  let single = null;
  if (IMP.target.album) single = albumById(IMP.target.album);
  else if (mode === 'album') single = createAlbum(IMP.albumName.trim() || defaultAlbumName(), IMP.target.folder || null);
  const dirAlbum = mode === 'structure' ? buildStructure(IMP.queue, IMP.target.folder || null) : new Map();
  const touched = new Set(), sets = new Map();
  const setOf = a => { if (!sets.has(a)) sets.set(a, new Set(a.items)); return sets.get(a); };
  const known = new Map(S.items.map(i => [i.fkey, i]));
  sh.setLeft('');
  sh.setTitle('Імпорт');
  sh.body.innerHTML = `<div class="imp-prog" aria-live="polite"><div class="ring-wrap"><svg class="ring" viewBox="0 0 120 120"><circle class="ring-bg" cx="60" cy="60" r="52"/><circle class="ring-fg" cx="60" cy="60" r="52" pathLength="100"/><path class="ring-ok" d="M40 61l14 14 27-30" pathLength="100"/></svg><div class="ring-txt">0%</div></div><b class="prog-title">Імпортуємо…</b><div class="prog-sub">0 з ${total}</div><div class="prog-thumbs"></div></div>`;
  const fg = $('.ring-fg', sh.body), txt = $('.ring-txt', sh.body), sub = $('.prog-sub', sh.body), pile = $('.prog-thumbs', sh.body);
  let done = 0, added = 0, dup = 0, failed = 0;
  const revived = [], q = jobs.slice();
  const worker = async () => {
    while (q.length) {
      const job = q.shift();
      let it = IMP.dedupe ? known.get(fkey(job.file)) : null;
      if (it) { dup++; if (it.deleted) { it.deleted = null; revived.push(it); } }
      else {
        try { it = await makeItem(job.file, job.path); } catch (e) { it = null; }
        if (it) {
          S.items.push(it); S.byId.set(it.id, it); known.set(it.fkey, it); added++;
          await Promise.all([DB.put('blobs', { id: it.id, blob: job.file }), DB.put('items', rec(it))]);
          const u = turl(it);
          if (u) {
            const img = h(`<img src="${u}" alt="">`); pile.append(img);
            if (!RM) img.animate([{ opacity: 0, transform: `translateY(30px) scale(.6) rotate(${(Math.random() - .5) * 30}deg)` }, { opacity: 1, transform: `rotate(${(Math.random() - .5) * 16}deg)` }], { duration: 420, easing: SOFT_SPRING, fill: 'forwards' });
            while (pile.childElementCount > 5) pile.firstElementChild.remove();
          }
        } else failed++;
      }
      if (it) {
        const a = single || (job.src.kind === 'folder' ? dirAlbum.get(job.path) : null);
        if (a) { const st = setOf(a); if (!st.has(it.id)) { st.add(it.id); a.items.push(it.id); touched.add(a); } }
      }
      done++;
      const pct = Math.round(done / total * 100);
      fg.style.strokeDashoffset = 100 - pct; txt.textContent = pct + '%'; sub.textContent = `${done} з ${total}`;
    }
  };
  await Promise.all([worker(), worker(), worker()]);
  await Promise.all([saveItems(revived), saveAlbums([...touched])]);
  indexItems();
  IMP.running = false; IMP.queue = [];
  invalidate({ flip: true });
  if (!sh.closed) {
    const ring = $('.ring', sh.body); ring.classList.add('done'); txt.textContent = '';
    $('.prog-title', sh.body).textContent = added ? 'Готово' : dup ? 'Усе це вже є в галереї' : 'Не вдалося імпортувати';
    sub.textContent = [`Додано ${nItems(added)}`, dup ? `дублікатів: ${dup}` : '', failed ? `не вдалося: ${failed}` : ''].filter(Boolean).join(' · ');
    const rr = ring.getBoundingClientRect(); FX.sparkle(rr.left + rr.width / 2, rr.top + rr.height / 2, 64);
    haptic(20);
    sh.body.insertAdjacentHTML('beforeend', '<button class="btn-primary" data-imp="done">Готово</button>');
    sh.setRight('Готово', () => sh.close(), true);
  } else toast(`Імпортовано: ${nItems(added)}`, 'check');
}

// ---------- sample photos, drawn on canvas ----------
function rng(a) { return () => { a |= 0; a = a + 0x6D2B79F5 | 0; let t = Math.imul(a ^ a >>> 15, 1 | a); t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t; return ((t ^ t >>> 14) >>> 0) / 4294967296; }; }
function hexRgb(hx) { const n = parseInt(hx.slice(1), 16); return [n >> 16 & 255, n >> 8 & 255, n & 255]; }
function mix(a, b, t) { const x = hexRgb(a), y = hexRgb(b); return `rgb(${x.map((v, i) => Math.round(v + (y[i] - v) * t)).join(',')})`; }
function scene(kind, seed, w, hh) {
  const c = document.createElement('canvas'); c.width = w; c.height = hh;
  const g = c.getContext('2d'), r = rng(seed), pick = a => a[Math.floor(r() * a.length)];
  const lin = (y0, y1, stops) => { const gr = g.createLinearGradient(0, y0, 0, y1); stops.forEach((s, i) => gr.addColorStop(i / (stops.length - 1), s)); return gr; };
  const glow = (x, y, rad, col) => { const gr = g.createRadialGradient(x, y, 0, x, y, rad); gr.addColorStop(0, col); gr.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = gr; g.fillRect(0, 0, w, hh); };
  const disc = (x, y, rad, col) => { g.fillStyle = col; g.beginPath(); g.arc(x, y, rad, 0, 6.2832); g.fill(); };
  const ridge = (baseY, amp, color) => {
    let pts = [baseY + (r() - .5) * amp, baseY + (r() - .5) * amp], disp = amp;
    while (pts.length < 80) { const nx = []; for (let i = 0; i < pts.length - 1; i++) nx.push(pts[i], (pts[i] + pts[i + 1]) / 2 + (r() - .5) * disp); nx.push(pts[pts.length - 1]); pts = nx; disp *= .56; }
    g.fillStyle = color; g.beginPath(); g.moveTo(0, hh);
    pts.forEach((y, i) => g.lineTo(i / (pts.length - 1) * w, y));
    g.lineTo(w, hh); g.closePath(); g.fill();
  };
  if (kind === 'mountains') {
    const p = pick([['#ffe1b3', '#ffb38a', '#e8879c', '#8d6a9f'], ['#d6ecff', '#a9d1f5', '#f3d9c4', '#f0b59a'], ['#fde2c0', '#f7b267', '#f4845f', '#7a4c7e']]);
    g.fillStyle = lin(0, hh * .8, p); g.fillRect(0, 0, w, hh);
    const sx = w * (.2 + r() * .6), sy = hh * (.24 + r() * .14);
    glow(sx, sy, w * .45, 'rgba(255,244,220,.75)'); disc(sx, sy, w * .055, '#fff7e6');
    for (let i = 0; i < 5; i++) {
      ridge(hh * (.42 + i * .1), hh * (.26 - i * .035), mix(p[3], '#1d1830', .15 + i * .2));
      g.fillStyle = 'rgba(255,255,255,.07)'; g.fillRect(0, 0, w, hh);
    }
  } else if (kind === 'forest') {
    g.fillStyle = lin(0, hh, ['#e8efe9', '#c3d6c9', '#94b2a1']); g.fillRect(0, 0, w, hh);
    glow(w * (.3 + r() * .4), hh * .2, w * .5, 'rgba(255,250,235,.6)');
    for (let i = 0; i < 5; i++) {
      const col = mix('#a3bcac', '#16302a', i / 4), base = hh * (.48 + i * .11), sc = 1 + i * .45;
      g.fillStyle = col;
      for (let x = -30; x < w + 30; x += (16 + r() * 22) * sc) {
        const th = hh * (.1 + r() * .09) * sc, tw = th * .42;
        for (let k = 0; k < 3; k++) { const ty = base - th + k * th * .28; g.beginPath(); g.moveTo(x, ty); g.lineTo(x - tw * (.55 + k * .22), ty + th * .5); g.lineTo(x + tw * (.55 + k * .22), ty + th * .5); g.closePath(); g.fill(); }
      }
      g.fillRect(0, base + hh * .02, w, hh);
      g.fillStyle = lin(base - hh * .1, base + hh * .05, ['rgba(255,255,255,0)', 'rgba(255,255,255,.22)']); g.fillRect(0, base - hh * .1, w, hh * .15);
    }
  } else if (kind === 'sea' || kind === 'dunes') {
    const p = pick([['#8ec5ff', '#ffd9b5', '#ffb08a'], ['#ffcf8a', '#ff8a5c', '#d65a6f'], ['#b8e1ff', '#e8f4ff', '#ffe7c7']]);
    const hz = hh * (.5 + r() * .12);
    g.fillStyle = lin(0, hz, p); g.fillRect(0, 0, w, hz);
    const sx = w * (.3 + r() * .4), sy = hz - hh * (.04 + r() * .12);
    glow(sx, sy, w * .5, 'rgba(255,236,200,.7)'); disc(sx, sy, w * .06, '#fff3dc');
    if (kind === 'sea') {
      g.fillStyle = lin(hz, hh, ['#5f8fb8', '#2a4e78', '#142c4a']); g.fillRect(0, hz, w, hh - hz);
      for (let k = 0; k < 60; k++) { const t = k / 60, y = hz + t * t * (hh - hz), ww = w * (.03 + t * .18) * (.5 + r()); g.fillStyle = `rgba(255,226,180,${.55 * (1 - t)})`; g.fillRect(sx - ww / 2 + (r() - .5) * w * .05, y, ww, 1 + t * 4); }
      for (let k = 0; k < 90; k++) { const t = r(), y = hz + t * t * (hh - hz); g.fillStyle = `rgba(255,255,255,${.12 * (1 - t) + .03})`; g.fillRect(r() * w, y, w * (.02 + t * .08), 1 + t * 2); }
      g.fillStyle = '#e9d3ad'; g.beginPath(); g.moveTo(0, hh); g.lineTo(0, hh * .9); g.bezierCurveTo(w * .3, hh * .86, w * .6, hh * .95, w, hh * .88); g.lineTo(w, hh); g.fill();
    } else {
      g.fillStyle = '#f2c38b'; g.fillRect(0, hz, w, hh);
      for (let i = 0; i < 4; i++) {
        const y0 = hz + (hh - hz) * (i * .24), col1 = mix('#f6c98f', '#b5622f', i / 3), col2 = mix('#e8a565', '#7d3a1b', i / 3);
        const gr = g.createLinearGradient(0, 0, w, 0); gr.addColorStop(0, col1); gr.addColorStop(.55 + r() * .2, col1); gr.addColorStop(1, col2);
        g.fillStyle = gr; g.beginPath(); g.moveTo(0, hh); g.lineTo(0, y0 + hh * .05);
        g.bezierCurveTo(w * (.2 + r() * .2), y0 - hh * .08, w * (.5 + r() * .2), y0 + hh * .1, w, y0 - hh * .02); g.lineTo(w, hh); g.fill();
      }
    }
  } else if (kind === 'city') {
    g.fillStyle = lin(0, hh, ['#0b1030', '#1f2a64', '#45478f']); g.fillRect(0, 0, w, hh);
    for (let k = 0; k < 160; k++) disc(r() * w, r() * hh * .55, r() * 1.4 + .3, `rgba(255,255,255,${.3 + r() * .6})`);
    const mx = w * (.15 + r() * .7), my = hh * (.12 + r() * .12);
    glow(mx, my, w * .25, 'rgba(200,215,255,.35)'); disc(mx, my, w * .045, '#f4f1e6');
    for (let layer = 0; layer < 3; layer++) {
      const col = ['#2b2f63', '#1a1d40', '#0c0e22'][layer];
      let x = -10;
      while (x < w) {
        const bw = w * (.05 + r() * .09), bh = hh * (.18 + r() * .3) * (1 - layer * .18), top = hh - bh - layer * hh * .02;
        g.fillStyle = col; g.fillRect(x, top, bw, hh - top);
        if (layer) for (let wy = top + 8; wy < hh - 6; wy += 11) for (let wx = x + 5; wx < x + bw - 6; wx += 9) if (r() < .32) { g.fillStyle = pick(['#ffd27a', '#ffe9b0', '#9fd3ff', '#ffb36b']); g.fillRect(wx, wy, 4, 6); }
        x += bw + 2;
      }
    }
  } else if (kind === 'bokeh') {
    g.fillStyle = lin(0, hh, ['#120d24', '#2b1240', '#0d1b33']); g.fillRect(0, 0, w, hh);
    g.globalCompositeOperation = 'lighter';
    const hues = pick([[30, 45, 340], [190, 210, 280], [20, 300, 50]]);
    for (let k = 0; k < 70; k++) {
      const x = r() * w, y = r() * hh, rad = w * (.02 + r() * .11), hue = hues[k % 3] + (r() - .5) * 20;
      const gr = g.createRadialGradient(x, y, 0, x, y, rad);
      gr.addColorStop(0, `hsla(${hue},90%,65%,${.25 + r() * .3})`); gr.addColorStop(.85, `hsla(${hue},90%,60%,${.12 + r() * .15})`); gr.addColorStop(1, 'hsla(0,0%,0%,0)');
      g.fillStyle = gr; g.beginPath(); g.arc(x, y, rad, 0, 6.2832); g.fill();
    }
    g.globalCompositeOperation = 'source-over';
  } else if (kind === 'flower') {
    g.fillStyle = lin(0, hh, ['#2f5d3a', '#6f9b52', '#c8d98a']); g.fillRect(0, 0, w, hh);
    for (let k = 0; k < 30; k++) { const x = r() * w, y = r() * hh, rad = w * (.03 + r() * .08); const gr = g.createRadialGradient(x, y, 0, x, y, rad); gr.addColorStop(0, 'rgba(255,255,220,.25)'); gr.addColorStop(1, 'rgba(255,255,220,0)'); g.fillStyle = gr; g.fillRect(x - rad, y - rad, rad * 2, rad * 2); }
    const [c1, c2] = pick([['#ffd1e3', '#ff5f9e'], ['#fff2a8', '#ffa630'], ['#ffffff', '#f3b8d8'], ['#e5d2ff', '#8a5cf5']]);
    const cx = w * (.4 + r() * .2), cy = hh * (.4 + r() * .2), R = Math.min(w, hh) * (.3 + r() * .08);
    for (let layer = 0; layer < 2; layer++) {
      const n = 9 + Math.floor(r() * 6), off = r() * Math.PI, len = R * (layer ? .7 : 1);
      for (let k = 0; k < n; k++) {
        const a = off + k / n * Math.PI * 2;
        g.save(); g.translate(cx, cy); g.rotate(a);
        const gr = g.createLinearGradient(0, 0, len, 0); gr.addColorStop(0, c2); gr.addColorStop(1, c1);
        g.fillStyle = gr; g.beginPath(); g.ellipse(len * .5, 0, len * .5, len * .17, 0, 0, 6.2832); g.fill();
        g.restore();
      }
    }
    const gr = g.createRadialGradient(cx, cy, 0, cx, cy, R * .2); gr.addColorStop(0, '#ffe066'); gr.addColorStop(1, '#c76f00');
    disc(cx, cy, R * .2, gr);
    for (let k = 0; k < 40; k++) { const a = r() * 6.28, d = r() * R * .17; disc(cx + Math.cos(a) * d, cy + Math.sin(a) * d, 2, 'rgba(120,60,0,.6)'); }
  }
  const v = g.createRadialGradient(w / 2, hh / 2, Math.min(w, hh) * .35, w / 2, hh / 2, Math.max(w, hh) * .75);
  v.addColorStop(0, 'rgba(0,0,0,0)'); v.addColorStop(1, 'rgba(0,0,0,.28)');
  g.fillStyle = v; g.fillRect(0, 0, w, hh);
  return c;
}
async function makeSamples(progress) {
  const plan = [
    { folder: 'Подорожі', album: 'Карпати', start: [2025, 7, 9], kinds: ['mountains', 'forest', 'mountains', 'mountains', 'forest', 'mountains', 'forest', 'mountains'], fav: [2, 5] },
    { folder: 'Подорожі', album: 'Одеса', start: [2025, 6, 14], kinds: ['sea', 'sea', 'dunes', 'sea', 'sea', 'bokeh', 'sea'], fav: [0] },
    { album: 'Нічне місто', start: [2024, 11, 20], kinds: ['city', 'bokeh', 'city', 'city', 'bokeh', 'city'], fav: [3] },
    { album: 'Квіти', start: [2026, 4, 2], kinds: ['flower', 'flower', 'flower', 'flower', 'flower'], fav: [1] },
    { start: [2024, 3, 6], spread: true, kinds: ['dunes', 'mountains', 'sea', 'forest', 'flower', 'city', 'bokeh', 'mountains', 'sea'] },
  ];
  const now = Date.now(), items = [], albums = [], folders = [];
  const total = plan.reduce((n, p) => n + p.kinds.length, 0);
  let n = 4012, seed = 7, made = 0;
  for (const p of plan) {
    let parent = null;
    if (p.folder) {
      let f = folders.find(x => x.name === p.folder);
      if (!f) { f = { id: uid(), name: p.folder, parent: null, created: now - 5000, sample: true }; folders.push(f); }
      parent = f.id;
    }
    const album = p.album ? { id: uid(), name: p.album, parent, items: [], created: now - 4000 + albums.length, sample: true } : null;
    if (album) albums.push(album);
    const r = rng(seed * 31);
    let t = new Date(p.start[0], p.start[1], p.start[2], 9 + Math.floor(r() * 6), Math.floor(r() * 60)).getTime();
    for (let i = 0; i < p.kinds.length; i++) {
      const portrait = r() < .45, w = portrait ? 900 : 1200, hh = portrait ? 1200 : 900;
      const cv = scene(p.kinds[i], seed++, w, hh);
      const blob = await new Promise(res => cv.toBlob(res, 'image/jpeg', .86));
      const thumb = await scaleToBlob(cv, w, hh);
      const name = `IMG_${n++}.JPG`;
      const it = { id: uid(), name, kind: 'image', mime: 'image/jpeg', size: blob ? blob.size : 0, w, h: hh, duration: 0, date: t, added: now - (total - made) * 1000, path: [p.folder, p.album].filter(Boolean).join('/'), fav: (p.fav || []).includes(i), deleted: null, sample: true, fkey: 'sample|' + name, thumb, _blob: blob };
      items.push(it); if (album) album.items.push(it.id);
      t += p.spread ? (40 + r() * 60) * 864e5 : (20 + r() * 900) * 60000 * (r() < .3 ? 24 : 1);
      made++; progress && progress(made, total);
      if (made % 4 === 0) await sleep(0);
    }
  }
  return { items, albums, folders };
}
