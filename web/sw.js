// Keeps the page working offline: the app's own files are cached; the weather and the maps are never cached here
// (the page keeps its last forecast itself).
const CACHE = 'sunw-web-v1';
const FILES = [
  './', 'index.html', 'css/style.css', 'manifest.webmanifest', 'icon.svg',
  'js/app.js', 'js/astro/moon.js', 'js/astro/sun.js', 'js/backup.js', 'js/charts.js', 'js/color.js', 'js/i18n.js',
  'js/store.js', 'js/sunmoon.js', 'js/themes.js', 'js/tz.js', 'js/weather.js',
];

self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // Network first (a new version arrives at once), the cache when offline.
  e.respondWith(fetch(e.request).then((res) => {
    const copy = res.clone();
    caches.open(CACHE).then((c) => c.put(e.request, copy));
    return res;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match('index.html'))));
});
