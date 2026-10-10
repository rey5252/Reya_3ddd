/* Хвиля — service worker: офлайн-оболонка застосунку + кеш шрифтів.
   Музика користувача тут не кешується: вона живе в IndexedDB і грає через blob: URL. */
const VERSION = 'hvylia-v1';
const RUNTIME = 'hvylia-runtime-v1';
const PRECACHE = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icons/icon.svg',
  './icons/icon-192.png',
  './icons/icon-512.png',
];
const FONT_HOSTS = new Set(['fonts.googleapis.com', 'fonts.gstatic.com']);
const NAV_TIMEOUT = 4000;

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(VERSION);
    // Кешуємо по одному: відсутня іконка не повинна зламати встановлення.
    await Promise.allSettled(PRECACHE.map(async url => {
      const res = await fetch(new Request(url, { cache: 'reload' }));
      if (res.ok) await cache.put(url, res);
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keep = new Set([VERSION, RUNTIME]);
    const keys = await caches.keys();
    // Видаляємо лише власні старі кеші: на спільному домені (напр. GitHub Pages) можуть бути чужі.
    await Promise.all(keys.filter(k => k.startsWith('hvylia-') && !keep.has(k)).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

const cacheable = res => res && (res.ok && res.status !== 206 || res.type === 'opaque');

async function put(cacheName, req, res) {
  if (!cacheable(res)) return;
  try { await (await caches.open(cacheName)).put(req, res); } catch (_) { /* квота або opaque — не критично */ }
}

// Сторінка: спершу мережа (з тайм-аутом), інакше — збережений index.html.
async function networkFirst(event) {
  const req = event.request;
  const fromCache = async () =>
    (await caches.match(req, { ignoreSearch: true })) ||
    (await caches.match('./index.html')) ||
    (await caches.match('./'));
  const network = fetch(req).then(res => {
    if (res.ok && res.type === 'basic') event.waitUntil(put(VERSION, req, res.clone()));
    return res;
  });
  network.catch(() => {}); // помилку обробляємо нижче; тут лише гасимо unhandledrejection
  const timeout = new Promise(resolve => setTimeout(resolve, NAV_TIMEOUT));
  try {
    const first = await Promise.race([network, timeout]);
    if (first) return first;
    return (await fromCache()) || (await network);
  } catch (_) {
    return (await fromCache()) || Response.error();
  }
}

// Ресурси й шрифти: віддаємо з кешу одразу, оновлюємо у фоні.
async function staleWhileRevalidate(event, cacheName) {
  const req = event.request;
  const cached = await caches.match(req);
  const update = fetch(req).then(res => {
    event.waitUntil(put(cacheName, req, res.clone()));
    return res;
  });
  if (cached) {
    event.waitUntil(update.catch(() => {}));
    return cached;
  }
  return update;
}

self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET' || req.headers.has('range')) return;
  if (req.destination === 'audio' || req.destination === 'video') return;
  const url = new URL(req.url);
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return; // blob:, data:, розширення тощо

  if (req.mode === 'navigate') {
    if (url.origin === self.location.origin) event.respondWith(networkFirst(event));
    return;
  }
  if (url.origin === self.location.origin) {
    event.respondWith(staleWhileRevalidate(event, VERSION));
  } else if (FONT_HOSTS.has(url.hostname)) {
    event.respondWith(staleWhileRevalidate(event, RUNTIME));
  }
});
