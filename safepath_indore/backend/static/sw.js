self.addEventListener('install', (e) => {
  console.log('SafePath Service Worker Installed');
});

self.addEventListener('fetch', (e) => {
  // Simple pass-through for now to satisfy PWA requirements
  e.respondWith(fetch(e.request));
});
