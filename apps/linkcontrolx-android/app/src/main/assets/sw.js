/* LinkControl X — service worker (offline app shell).
   Bump CACHE when you change files so phones pick up the new version. */
const CACHE = "linkcontrol-x-v1";
const ASSETS = [
  "./",
  "./index.html",
  "./style.css",
  "./script.js",
  "./manifest.json",
  "./vendor/qrcode-generator.js",
  "./vendor/jsQR.js",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/maskable-512.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  // Google Fonts: network-first, cache fallback. App assets: cache-first.
  e.respondWith(
    caches.match(req).then((cached) => cached ||
      fetch(req).then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => { try { c.put(req, copy); } catch (err) {} });
        return res;
      }).catch(() => cached)
    )
  );
});
