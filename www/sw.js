// CepGarson servis çalışanı: uygulama kabuğunu ve kütüphaneleri önbelleğe alır.
const CACHE = 'cepgarson-v1';
const CORE = ['./', 'index.html', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];
const CDN = ['cdn.tailwindcss.com', 'cdnjs.cloudflare.com', 'cdn.jsdelivr.net'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  if (url.origin === self.location.origin) {
    if (req.mode === 'navigate') {
      // Sayfa: önce ağ (güncel sürüm), internet yoksa önbellek
      e.respondWith(
        fetch(req)
          .then((r) => { const copy = r.clone(); caches.open(CACHE).then((c) => c.put('index.html', copy)); return r; })
          .catch(() => caches.match('index.html'))
      );
      return;
    }
    e.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((r) => {
        const copy = r.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); return r;
      }))
    );
    return;
  }

  if (CDN.indexOf(url.hostname) >= 0) {
    // Kütüphaneler: önbellekte varsa onu ver, arkada yenile; internet yoksa önbellekten çalış
    e.respondWith(
      caches.match(req).then((hit) => {
        const net = fetch(req).then((r) => {
          if (r && (r.ok || r.type === 'opaque')) { const copy = r.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
          return r;
        }).catch(() => hit);
        return hit || net;
      })
    );
  }
});
