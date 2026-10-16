(function (RA3) { 'use strict';
  // RA3 Texture Studio v2 — файли (імпорт DDS/TGA/PNG/JPG, експорт DDS/PNG, проєкт), документ, 3D-прев'ю,
  // клавіатура, перетягування файлів і запуск. Спільні стан і функції — у RA3.App (app-core.js).
  const util = RA3.util, dds = RA3.dds, tga = RA3.tga, maps = RA3.maps, layers = RA3.layers, project = RA3.project;
  const A = RA3.App, S = A.S, C = A.C, $ = A.$, el = A.el;

  const DOWNLOAD_GAP = 250;                    // мс між завантаженнями
  const ROLE_LABEL = { diffuse: 'колір + прозорість', spm: 'блиск + колір гравця', nrm: 'нормалі' };
  const ROLE_ORDER = { diffuse: 0, spm: 1, nrm: 2 };

  // ---------- Документ ----------

  function updateDocInfo() {
    const d = S.doc, raw = $('doc-name').value, base = maps.sanitizeName(raw), files = $('doc-files');
    files.textContent = '';
    files.append('Файли: ', el('span', { class: 'mono', text: base + '.dds, ' + base + '_NRM.dds, ' + base + '_SPM.dds' }));
    const bad = raw !== base;
    files.classList.toggle('warn', bad);
    if (bad) files.append(el('br'), 'Лише латинські літери, цифри та «_» — інші символи буде відкинуто.');
    const chip = $('doc-chip');
    chip.textContent = '';
    chip.append(el('b', { text: maps.sanitizeName(d.name) }), ' · ' + d.size + '×' + d.size + (S.dirty ? ' · не збережено' : ''));
  }
  A.updateDocInfo = updateDocInfo;

  function commitName() {
    const input = $('doc-name'), name = maps.sanitizeName(input.value);
    if (name !== S.doc.name) A.markDirty();
    S.doc.name = name;
    input.value = name;
    updateDocInfo();
  }

  A.syncDocControls = function () {
    const d = S.doc;
    $('doc-name').value = d.name;
    $('doc-size').value = String(d.size);
    $('nrm-strength').value = d.settings.nrmStrength;
    $('nrm-strength-out').textContent = (+d.settings.nrmStrength).toFixed(1);
    $('nrm-invert-y').checked = !!d.settings.invertY;
    updateDocInfo();
  };

  // Новий розмір: буфери ресемплюються (layers.resize), історія скидається (знімки старого розміру не підходять).
  function resizeDoc(n) {
    layers.resize(S.doc, n);
    S.below = null;
    S.lastPoint = null;
    A.clearHistory();
    A.markDirty();
    $('doc-size').value = String(n);
    A.resetView();
    updateDocInfo();
  }

  function changeSize() {
    const sel = $('doc-size'), n = +sel.value, d = S.doc, old = d.size;
    if (n === old || C.SIZES.indexOf(n) < 0) return;
    A.flushRebuild();
    A.setStatus('Зміна розміру на ' + n + '×' + n + '…', 'busy');
    sel.disabled = true;
    setTimeout(() => {
      sel.disabled = false;
      if (S.doc !== d) return;                 // за цей час відкрили інший документ
      try { resizeDoc(n); }
      catch (e) { sel.value = String(old); A.setStatus('Не вдалося змінити розмір: ' + A.errText(e), 'error'); return; }
      if (A.rebuildNow()) {
        const shrunk = n < old;
        A.setStatus('Розмір документа: ' + n + '×' + n + '. Історію скасування очищено' + (shrunk ? '; дрібні деталі мазків і зображень при збільшенні назад не повернуться' : '') + '.', 'ok');
      }
    }, 30);
  }

  function bindDocument() {
    const name = $('doc-name');
    name.addEventListener('input', updateDocInfo);
    name.addEventListener('change', commitName);
    $('doc-size').addEventListener('change', changeSize);
    const nrm = $('nrm-strength');
    nrm.addEventListener('input', () => {
      S.doc.settings.nrmStrength = +nrm.value;
      $('nrm-strength-out').textContent = (+nrm.value).toFixed(1);
      A.markDirty();
      A.mapsChanged(true);
      A.schedulePreview(150);
    });
    $('nrm-invert-y').addEventListener('change', e => {
      S.doc.settings.invertY = e.target.checked;
      A.markDirty();
      A.mapsChanged(true);
      A.schedulePreview(0);
    });
  }

  A.dropNrmImport = function () {
    S.doc.nrmImport = null;
    S.nrmImportName = '';
    A.markDirty();
    A.renderLayers();
    A.mapsChanged(true);
    A.schedulePreview(0);
    A.setStatus('Імпортовану NRM прибрано', 'ok');
  };

  function newDocument() {
    let d;
    try { d = A.blankDocument(S.doc.name, S.doc.size); } catch (e) { A.setStatus('Не вдалося створити документ: ' + A.errText(e), 'error'); return; }
    A.setDocument(d);
    A.setStatus('Новий документ: заливка-основа й шар «Малювання». Додавайте шари, розумні матеріали або імпортуйте текстуру.', 'ok');
  }

  // ---------- 3D-прев'ю ----------

  function initPreview() {
    const canvas = $('canvas-3d');
    let p;
    try { p = new RA3.Preview3D(canvas); } catch (e) { p = { ok: false, error: A.errText(e) }; }
    A.preview = p;
    if (!p.ok) {
      canvas.hidden = true;
      $('model-select').disabled = true;
      const box = $('preview-error');
      box.append(el('b', { text: p.error || 'WebGL недоступний' }),
        el('span', { text: '3D-прев\'ю вимкнено, решта програми працює. Як скін виглядає в грі, показує вкладка «У грі».' }));
      box.hidden = false;
      return;
    }
    p.setModel($('model-select').value);
    p.setTeamColor(maps.TEAM_COLORS[S.team].rgb);
    $('model-select').addEventListener('change', e => { p.setModel(e.target.value); p.render(); });
    // Прев'ю поза екраном чи згорнуте не малюється.
    let visible = true;
    const sync = () => { if (visible && $('preview-panel').open) p.start(); else p.stop(); };
    $('preview-panel').addEventListener('toggle', () => { sync(); if ($('preview-panel').open) A.schedulePreview(0); });
    if (typeof IntersectionObserver === 'function') {
      new IntersectionObserver(entries => { visible = entries[entries.length - 1].isIntersecting; sync(); }).observe(canvas);
    } else sync();
  }

  A.schedulePreview = function (delay) {
    const p = A.preview;
    if (!p || !p.ok) return;
    clearTimeout(S.timers.preview);
    S.timers.preview = setTimeout(updatePreview, delay === undefined ? 100 : delay);
  };

  function updatePreview() {
    S.timers.preview = 0;
    const p = A.preview;
    if (!p || !p.ok) return;
    if (S.stroke) { A.schedulePreview(150); return; }     // під час мазка — після нього
    const d = S.doc;
    try {
      p.setTextures(maps.buildDiffuse(d), maps.buildNRM(d), maps.buildSPM(d), d.size, { invertY: d.settings.invertY });
      p.render();
    } catch (e) { A.setStatus('3D-прев\'ю: ' + A.errText(e), 'error'); }
  }

  function buildTeamSwatches() {
    const box = $('team-colors');
    maps.TEAM_COLORS.forEach((t, i) => {
      const name = C.TEAM_NAMES[t.id] || t.id;
      const b = el('button', { type: 'button', class: 'swatch', role: 'radio', title: 'Колір гравця: ' + name, 'aria-label': name, dataset: { team: i } });
      b.style.background = 'rgb(' + t.rgb.join(',') + ')';
      box.append(b);
    });
    box.addEventListener('click', e => { const b = e.target.closest('[data-team]'); if (b) A.setTeam(+b.dataset.team); });
  }

  // ---------- Імпорт ----------

  function extOf(name) { const m = /\.([^.\\/]+)$/.exec(name); return m ? m[1].toLowerCase() : ''; }

  async function decodeImage(file) {
    const ext = extOf(file.name);
    if (ext === 'dds') return dds.decodeDDS(await file.arrayBuffer());
    if (ext === 'tga') return tga.decodeTGA(await file.arrayBuffer());
    let bmp;
    try { bmp = await createImageBitmap(file, { premultiplyAlpha: 'none', colorSpaceConversion: 'none' }); }
    catch (e) { throw new Error('формат не підтримується (потрібен DDS, TGA, PNG, JPG або WebP)'); }
    try {
      const c = el('canvas');
      c.width = bmp.width; c.height = bmp.height;
      const ctx = c.getContext('2d', { willReadFrequently: true });
      ctx.drawImage(bmp, 0, 0);
      return { width: bmp.width, height: bmp.height, data: ctx.getImageData(0, 0, bmp.width, bmp.height).data };
    } finally { bmp.close(); }
  }

  function imageLayer(imageId, name, target, channel) {
    return layers.newLayer('proc', { proc: { type: 'image', seed: 0, params: { imageId, target, channel: channel || 'lum', opacity: 1 } }, name });
  }

  // Наклейка: одне зображення кольором; прозорість картинки стає маскою шару.
  function decalLayer(img, fileName) {
    const d = S.doc, id = layers.addImage(d, img), data = d.images[id], n = d.size * d.size;
    const l = imageLayer(id, 'Зображення · ' + fileName, 'color');
    let transparent = false;
    for (let i = 3; i < data.length; i += 4) if (data[i] < 250) { transparent = true; break; }
    if (transparent) {
      const paint = new Uint8ClampedArray(n);
      for (let i = 0; i < n; i++) paint[i] = data[i * 4 + 3];
      l.mask = { enabled: true, invert: false, paint, gen: null };
    }
    return l;
  }

  // Набір з гри (є _NRM чи _SPM): документ бере розмір кольорової текстури (якщо підтримується), видимі шари,
  // крім зображень і порожніх «Малювань», вимикаються (не видаляються), шари набору стають у самий низ стеку.
  // Окреме зображення — наклейка над вибраним шаром. Спершу розкодовується все, потім змінюється документ.
  async function importFiles(files, asDecal) {
    const items = files.map(file => ({ file, info: maps.classifyTextureName(file.name) }))
      .sort((a, b) => ROLE_ORDER[a.info.role] - ROLE_ORDER[b.info.role]);
    const decoded = [], errors = [], notes = [];
    for (let i = 0; i < items.length; i++) {
      const { file, info } = items[i];
      A.setStatus('Імпорт ' + (i + 1) + ' з ' + items.length + ': «' + file.name + '»…', 'busy');
      try { decoded.push({ file, info, img: await decodeImage(file) }); }
      catch (e) { errors.push('«' + file.name + '»: ' + A.errText(e)); }
    }
    A.flushRebuild();
    const d = S.doc;
    const gameSet = !asDecal && decoded.length > 0 && items.some(x => x.info.role !== 'diffuse');
    let scaled = 0;
    if (decoded.length) {
      const entry = A.stackEntry('імпорт');
      if (gameSet) {
        const diffuse = decoded.find(x => x.info.role === 'diffuse'), w = diffuse ? diffuse.img.width : 0;
        if (diffuse && w === diffuse.img.height && w !== d.size && C.SIZES.indexOf(w) >= 0) {
          resizeDoc(w);
          notes.push('Розмір документа: ' + w + '×' + w + ', як у текстури гри (історію скасування очищено).');
        }
        const hidden = d.layers.filter(l => l.visible && !(l.kind === 'proc' && l.proc.type === 'image') && !(l.kind === 'paint' && isEmptyPaint(l)));
        for (const l of hidden) l.visible = false;
        if (hidden.length) notes.push('Інші шари вимкнено, щоб не змішувати з текстурою гри — увімкніть потрібні вручну.');
      }
      const added = [];
      let base = null;
      for (const { file, info, img } of decoded) {
        if (img.width !== d.size || img.height !== d.size) scaled++;
        if (!gameSet) { added.push(decalLayer(img, file.name)); continue; }
        if (base === null) base = info.base;
        if (info.role === 'nrm') {
          d.nrmImport = new Uint8ClampedArray(img.width === d.size && img.height === d.size ? img.data : util.resampleRGBA(img.data, img.width, img.height, d.size, d.size));
          S.nrmImportName = file.name;
          continue;
        }
        const id = layers.addImage(d, img);
        if (info.role === 'spm') added.push(imageLayer(id, 'Блиск (R) · ' + file.name, 'spec', 'r'), imageLayer(id, 'Колір гравця (B) · ' + file.name, 'team', 'b'));
        else added.push(imageLayer(id, 'Колір · ' + file.name, 'color'), imageLayer(id, 'Прозорість (A) · ' + file.name, 'alpha', 'a'));
      }
      if (gameSet) {
        // Шари набору — знизу, у порядку «колір, блиск»; шари користувача лишаються над ними.
        // Undo прибирає шари набору (видимість інших шарів і імпортована NRM лишаються як є).
        d.layers.splice(0, 0, ...added);
        A.pushUndo(entry);
        d.name = maps.sanitizeName(base);
        $('doc-name').value = d.name;
        A.renderLayers();
        A.renderProps();
        A.markDirty();
        S.below = null;
        A.rebuildNow();
      } else if (added.length) {
        A.insertLayers(added, 'імпорт');
        A.flushRebuild();
      }
      updateDocInfo();
    }
    let msg = decoded.length ? 'Імпортовано: ' + decoded.map(x => x.file.name + ' (' + (gameSet ? ROLE_LABEL[x.info.role] : 'зображення') + ')').join(', ') + '.' : '';
    if (notes.length) msg += ' ' + notes.join(' ');
    if (scaled) msg += ' Масштабовано до ' + S.doc.size + '×' + S.doc.size + '.';
    if (errors.length) msg = (msg ? msg + ' ' : '') + 'Не вдалося імпортувати ' + errors.join('; ');
    A.setStatus(msg.trim() || 'Нічого не імпортовано', errors.length ? 'error' : 'ok');
  }

  function isEmptyPaint(l) {
    for (const c of Object.keys(l.paint || {})) { const cov = l.paint[c].cov; for (let i = 0; i < cov.length; i++) if (cov[i]) return false; }
    return true;
  }

  async function importAlpha(file) {
    A.setStatus('Альфа з «' + file.name + '»…', 'busy');
    const img = await decodeImage(file);
    A.addCustomAlpha(img, file.name);
  }

  // ---------- Експорт ----------

  function download(blob, name) {
    const url = URL.createObjectURL(blob), a = el('a', { href: url, download: name });
    a.style.display = 'none';
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  async function downloadAll(files) {
    for (let i = 0; i < files.length; i++) {
      if (i) await A.sleep(DOWNLOAD_GAP);
      download(files[i].blob, files[i].name);
    }
  }

  // Кнопки групи вимкнені, поки триває робота; помилка йде в рядок стану.
  async function runJob(ids, failText, fn) {
    const btns = ids.map($);
    if (btns.some(b => b.disabled)) return;
    btns.forEach(b => { b.disabled = true; });
    try { await fn(); }
    catch (e) { A.setStatus(failText + ': ' + A.errText(e), 'error'); }
    finally { btns.forEach(b => { b.disabled = false; }); }
  }
  const EXPORT_BUTTONS = ['btn-export-dds', 'btn-export-png'];

  // Знімок трьох карт на момент натискання: подальші зміни документа на файли не впливають.
  function exportSources() {
    commitName();
    A.flushRebuild();
    const d = S.doc;
    return { base: maps.sanitizeName(d.name), size: d.size, diffuse: maps.buildDiffuse(d), nrm: maps.buildNRM(d), spm: maps.buildSPM(d) };
  }

  async function exportDDS() {
    const src = exportSources(), name = f => src.base + f.suffix + '.dds';
    const files = await dds.encodeSet(src, src.size, {
      uncompressed: $('uncompressed').checked,
      onProgress: (p, f) => A.setStatus('Кодування DDS… ' + Math.floor(p * 100) + '% — ' + name(f) + ' (' + f.format + ')', 'busy')
    });
    A.setStatus('Кодування DDS… 100%', 'busy');
    await downloadAll(files.map(f => ({ name: name(f), blob: new Blob([f.bytes], { type: 'application/octet-stream' }) })));
    A.setStatus('Експортовано: ' + files.map(f => name(f) + ' (' + f.format + ')').join(', '), 'ok');
  }

  async function exportPNG() {
    const src = exportSources();
    const items = [[src.base + '.png', src.diffuse], [src.base + '_NRM.png', src.nrm], [src.base + '_SPM.png', src.spm]];
    const c = el('canvas');
    c.width = src.size; c.height = src.size;
    const ctx = c.getContext('2d');
    const files = [];
    for (const [name, data] of items) {
      A.setStatus('Підготовка PNG: ' + name + '…', 'busy');
      ctx.putImageData(new ImageData(data, src.size, src.size), 0, 0);
      const blob = await new Promise((resolve, reject) => c.toBlob(b => (b ? resolve(b) : reject(new Error('браузер не зміг створити PNG'))), 'image/png'));
      files.push({ name, blob });
    }
    await downloadAll(files);
    A.setStatus('Експортовано PNG: ' + items.map(x => x[0]).join(', '), 'ok');
  }

  // ---------- Проєкт ----------

  // Лише зображення, на які посилаються шари (видалені шари могли лишити непотрібні).
  function usedImages(d) {
    const used = {};
    for (const l of d.layers) {
      const id = l.kind === 'proc' && l.proc && l.proc.type === 'image' ? l.proc.params.imageId : null;
      if (id && d.images[id]) used[id] = d.images[id];
    }
    return used;
  }

  // serialize знімає документ синхронно, тож файл відповідає моменту натискання; зміни під час стиснення
  // лишаються незбереженими.
  async function saveProject() {
    commitName();
    A.setStatus('Збереження проєкту…', 'busy');
    const version = S.version, d = S.doc, name = maps.sanitizeName(d.name) + '.ra3tex.json';
    const json = await project.serialize(Object.assign({}, d, { images: usedImages(d) }));
    download(new Blob([json], { type: 'application/json' }), name);
    const later = S.version !== version;
    if (!later) S.dirty = false;
    updateDocInfo();
    A.setStatus('Проєкт збережено: ' + name + (later ? '. Зміни, зроблені під час збереження, у файл не ввійшли.' : ''), 'ok');
  }

  // Новий документ готується повністю (розбір + композиція) і лише тоді замінює поточний.
  async function openProject(file) {
    A.setStatus('Відкриття проєкту «' + file.name + '»…', 'busy');
    let doc;
    try {
      doc = await project.deserialize(await file.text());
      if (C.SIZES.indexOf(doc.size) < 0) throw new Error('розмір ' + doc.size + '×' + doc.size + ' не підтримується (потрібно 256, 512, 1024 або 2048)');
      layers.composite(doc);
    } catch (e) {
      A.setStatus(A.sentence('Не вдалося відкрити проєкт «' + file.name + '»: ' + A.errText(e)) + ' Поточна робота не змінилася.', 'error');
      return;
    }
    A.setDocument(doc, { nrmName: doc.nrmImport ? 'Імпортована NRM' : '' });
    updateDocInfo();
    A.setStatus('Проєкт відкрито: ' + file.name, 'ok');
  }

  const LOAD_BUTTONS = ['btn-import', 'btn-open-project', 'btn-new'];
  const SAVE_BUTTONS = ['btn-save-project', 'doc-size'].concat(LOAD_BUTTONS);
  function saveJob() { runJob(SAVE_BUTTONS, 'Не вдалося зберегти проєкт', saveProject); }
  function openProjectJob(file) { runJob(LOAD_BUTTONS, 'Не вдалося відкрити проєкт', () => openProject(file)); }

  // Відкриття замінює документ: з незбереженими змінами «Відкрити» просить другий клік. Перетягнутий .json
  // чекає в pendingProject, і клік по підсвіченій кнопці відкриває саме його.
  let pendingProject = null;
  function onOpenProjectClick(e) {
    const btn = e.currentTarget;
    if (!A.isArmed(btn)) pendingProject = null;
    const go = () => { const f = pendingProject; pendingProject = null; if (f) openProjectJob(f); else $('project-input').click(); };
    if (!S.dirty) { A.disarm(btn); go(); return; }
    A.confirmThen(btn, 'Натисніть «Відкрити» ще раз: незбережені зміни поточного документа зникнуть', go);
  }

  function handleFiles(files) {
    if (!files.length || LOAD_BUTTONS.some(id => $(id).disabled)) return;
    const proj = files.find(f => extOf(f.name) === 'json');
    if (!proj) { runJob(LOAD_BUTTONS, 'Не вдалося імпортувати', () => importFiles(files, false)); return; }
    if (!S.dirty) { openProjectJob(proj); return; }
    pendingProject = proj;
    A.armConfirm($('btn-open-project'), 'Є незбережені зміни. Щоб замінити їх проєктом «' + proj.name + '», натисніть «Відкрити»', C.DROP_CONFIRM_MS);
  }

  function bindFiles() {
    const pick = (id, fn) => {
      const input = $(id);
      input.addEventListener('change', () => { const files = Array.from(input.files); input.value = ''; if (files.length) fn(files); });
    };
    $('btn-import').addEventListener('click', () => $('file-input').click());
    pick('file-input', handleFiles);
    pick('image-input', files => runJob(LOAD_BUTTONS, 'Не вдалося додати зображення', () => importFiles(files, true)));
    pick('alpha-input', files => runJob(['btn-add-alpha'], 'Не вдалося створити альфу', () => importAlpha(files[0])));
    pick('project-input', files => openProjectJob(files[0]));
    $('btn-open-project').addEventListener('click', onOpenProjectClick);
    $('btn-save-project').addEventListener('click', saveJob);
    $('btn-export-dds').addEventListener('click', () => runJob(EXPORT_BUTTONS, 'Не вдалося експортувати DDS', exportDDS));
    $('btn-export-png').addEventListener('click', () => runJob(EXPORT_BUTTONS, 'Не вдалося експортувати PNG', exportPNG));
    $('btn-new').addEventListener('click', e => A.confirmThen(e.currentTarget, 'Натисніть «Новий» ще раз: поточні шари й мазки зникнуть', newDocument));
  }

  // ---------- Клавіатура й перетягування ----------

  function onKeyDown(e) {
    const t = e.target, typing = t && t.matches && t.matches('input[type="text"], input[type="number"], textarea, select');
    if ((e.ctrlKey || e.metaKey) && !e.altKey) {
      // e.code, а не e.key: на українській розкладці Z дає «я».
      if (e.code === 'KeyS') { e.preventDefault(); saveJob(); return; }
      if (typing) return;
      if (e.code === 'KeyZ') { e.preventDefault(); if (e.shiftKey) A.redo(); else A.undo(); }
      else if (e.code === 'KeyY') { e.preventDefault(); A.redo(); }
      return;
    }
    if (typing || e.altKey || e.ctrlKey || e.metaKey) return;
    switch (e.code) {
      case 'Space': e.preventDefault(); if (!e.repeat) A.setSpace(true); break;
      case 'KeyB': A.setTool('brush'); break;
      case 'KeyE': A.setTool('eraser'); break;
      case 'KeyG': A.setTool('fill'); break;
      case 'KeyI': A.setTool('picker'); break;
      case 'KeyM': A.setTarget(S.target === 'mask' ? 'paint' : 'mask'); break;
      case 'BracketLeft': A.nudgeBrushSize(-1); break;
      case 'BracketRight': A.nudgeBrushSize(1); break;
      case 'KeyT': A.setTiling(!S.tiling); break;
      case 'KeyF': A.fit(); break;
      case 'Digit0': case 'Numpad0': A.zoomTo(1); break;
      default: return;
    }
  }

  function bindDragDrop() {
    const overlay = $('drop-overlay');
    let depth = 0;
    const hasFiles = e => !!e.dataTransfer && Array.prototype.indexOf.call(e.dataTransfer.types, 'Files') >= 0;
    window.addEventListener('dragenter', e => { if (!hasFiles(e)) return; e.preventDefault(); depth++; overlay.hidden = false; });
    window.addEventListener('dragover', e => { if (!hasFiles(e)) return; e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; });
    window.addEventListener('dragleave', e => { if (!hasFiles(e)) return; depth = Math.max(0, depth - 1); if (!depth) overlay.hidden = true; });
    window.addEventListener('drop', e => {
      if (!hasFiles(e)) return;
      e.preventDefault();
      depth = 0;
      overlay.hidden = true;
      handleFiles(Array.from(e.dataTransfer.files));
    });
  }

  // ---------- Запуск ----------

  function fnv(arr) {
    const b = new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength);
    let h = 2166136261;
    for (let i = 0; i < b.length; i++) h = Math.imul(h ^ b[i], 16777619);
    return (h >>> 0).toString(16);
  }

  function init() {
    A.initCanvas();
    A.initPanels();
    bindDocument();
    bindFiles();
    bindDragDrop();
    buildTeamSwatches();
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('keyup', e => { if (e.code === 'Space') A.setSpace(false); });
    window.addEventListener('blur', () => A.setSpace(false));
    window.addEventListener('beforeunload', e => { if (S.dirty) { e.preventDefault(); e.returnValue = ''; } });
    initPreview();
    A.setTool('brush');
    A.setTeam(C.DEFAULT_TEAM);
    A.setView(S.view);
    A.setTiling(false);
    A.updateUndoButtons();
    let doc = null, error = null;
    try { doc = A.createDemo(); } catch (e) { error = e; doc = A.blankDocument('AUMyTank', 512); }
    A.setDocument(doc);
    A.renderNow();
    if (error) A.setStatus('Не вдалося побудувати демо-скін: ' + A.errText(error), 'error');
    else if (A.statusKind() !== 'error') A.setStatus('Готово', 'ok');
    // Незбережений стан у назві документа (шапка) оновлюється періодично — дешевше, ніж після кожної дії.
    let shown = null;
    setInterval(() => { if (shown !== S.dirty) { shown = S.dirty; updateDocInfo(); } }, 1000);
  }

  // Налагодження й тести (smoke.cjs): документ, стан, координати полотна, хеш буфера, час кадрів.
  window.RA3_APP = {
    app: A,
    get doc() { return S.doc; },
    get state() { return S; },
    get view() { return A.V; },
    flush() { A.flushRebuild(); A.renderNow(); },
    toClient: (x, y) => A.toClient(x, y),
    hash: fnv,
    perf: () => ({ rebuild: S.perf.rebuild, frames: S.perf.frames.slice() })
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})(typeof window !== 'undefined' ? (window.RA3 = window.RA3 || {}) : {});
