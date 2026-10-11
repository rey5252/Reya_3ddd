'use strict';
// ---------- boot ----------
const splashText = t => { const s = $('#splash small'); if (s) s.textContent = t; };

function buildUI() {
  Tabs.views = { library: $('.tabview[data-tab="library"]'), albums: $('.tabview[data-tab="albums"]'), search: $('.tabview[data-tab="search"]') };
  const roots = { library: new LibraryPage(), albums: new AlbumsPage(null), search: new SearchPage() };
  for (const t of ['albums', 'search', 'library']) { Tabs.cur = t; push(roots[t], { animate: false }); }
  $('#tabbar').addEventListener('click', e => { const b = e.target.closest('.tab'); if (b) switchTab(b.dataset.tab); });
  $('#selbar').addEventListener('click', onSelbar);
  $('#in-files').addEventListener('change', e => { const f = [...e.target.files]; if (f.length) { ensureImportOpen(); addFilesSource(f); } });
  $('#in-dir').addEventListener('change', e => { const f = [...e.target.files]; if (f.length) { ensureImportOpen(); addDirSource(f); } });
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const p = topPage();
    if (b.dataset.act === 'import') openImport({ folder: p && p.folderId || null });
    else if (b.dataset.act === 'album-add' && p && p.album) pickFromLibrary(p.album);
  });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') handleBack(); });
  document.addEventListener('gesturestart', e => e.preventDefault());
  addEventListener('resize', () => { measureSafe(); const p = topPage('library'); p && p.moveKnob(); });
  initEdgeSwipe(); initDrop(); V.init(); Back.init();
  document.body.classList.toggle('blur18', blur18On());
  scheduleAdultScan();
}

async function boot() {
  measureSafe();
  const ok = await DB.open();
  S.persist = ok;
  let items = [], albums = [], folders = [];
  if (ok) [items, albums, folders] = await Promise.all([DB.all('items'), DB.all('albums'), DB.all('folders')]);
  const seeded = ok ? await DB.meta('seeded') : false;
  if (!items.length && !albums.length && !seeded) {
    splashText('Готуємо приклади…');
    const r = await makeSamples((n, t) => splashText(`Готуємо приклади… ${n} з ${t}`));
    ({ items, albums, folders } = r);
    if (ok) {
      await DB.put('items', items.map(rec));
      await DB.put('blobs', items.map(i => ({ id: i.id, blob: i._blob })));
      await DB.put('albums', albums); await DB.put('folders', folders);
      await DB.setMeta('seeded', 1);
    }
  }
  S.items = items; S.albums = albums; S.folders = folders;
  indexItems();
  const expired = S.items.filter(i => i.deleted && i.deleted < Date.now() - 30 * 864e5).map(i => i.id);
  if (expired.length) await purgeItems(expired);
  buildUI();
  const sp = $('#splash');
  await anim(sp, [{ opacity: '1', transform: 'scale(1)' }, { opacity: '0', transform: 'scale(1.08)' }], { duration: 380 });
  sp.remove();
  if (!RM) $('.tabview[data-tab="library"]').animate([{ opacity: 0, transform: 'scale(.97)' }, { opacity: 1, transform: 'none' }], { duration: 420, easing: EASE });
  if (!ok) toast('Пам’ять браузера недоступна — фото не збережуться після закриття', 'info');
  else if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
  window.__nativeToast = msg => toast(msg, 'check');
  if (!IN_FRAME && !NATIVE && 'serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost')) navigator.serviceWorker.register('sw.js').catch(() => {});
}
boot().catch(err => { console.error(err); splashText('Не вдалося запустити галерею. Оновіть сторінку.'); });
