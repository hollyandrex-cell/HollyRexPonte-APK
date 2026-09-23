// firebase-messaging-sw.js V4 FINALE - Holly & Rex - Fix 401 + Spesa Live + WhatsApp + Ponte
// DEVE stare nella stessa cartella di assistente-vocale.html

const CACHE_NAME = 'assistente-vocale-v4-finale-fix-401-spesa';

const ASSETS = [
  './',
  './assistente-vocale.html',
  './assistente-vocale-v4-finale.html',
  './manifest.json',
  './bau-192x192.png',
  './bau-32.png'
];

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ASSETS).catch(()=>{}))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k))
    )).then(() => self.clients.claim())
  );
});

// FIX CRITICO 401: NON intercettare fcmregistrations e googleapis
self.addEventListener('fetch', (e) => {
  const url = e.request.url;
  if (e.request.method !== 'GET') return;
  if (url.includes('fcmregistrations') || url.includes('fcm.googleapis.com') || url.includes('googleapis.com/identity') || url.includes('www.googleapis.com') || (url.includes('fcm') && url.includes('googleapis'))) {
    return; // lascia passare diretta a Google, evita 401
  }
  if (url.includes('/api/')) return;
  e.respondWith(
    caches.match(e.request).then((cached) => cached || fetch(e.request).then((res) => {
      if(!res || res.status !== 200 || res.type !== 'basic') return res;
      const clone = res.clone();
      caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
      return res;
    }).catch(()=>caches.match(e.request)))
  );
});

try {
  importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js');
  importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-messaging-compat.js');
  
  const firebaseConfig = {
    apiKey: "AIzaSyAR8NVxtUu5QhlDWd0DXjp1lWV6vLdRM1c",
    authDomain: "holly-rex-spesa.firebaseapp.com",
    projectId: "holly-rex-spesa",
    storageBucket: "holly-rex-spesa.firebasestorage.app",
    messagingSenderId: "450072023944",
    appId: "1:450072023944:web:888af5555a5b50c599203d"
  };
  firebase.initializeApp(firebaseConfig);
  const messaging = firebase.messaging();

  messaging.onBackgroundMessage((payload) => {
    console.log('[SW V4 FINALE] Background:', payload);
    const tipo = payload.data?.tipo || payload.data?.type || "";
    const title = payload.notification?.title || payload.data?.title || 'Holly & Rex - Aura';
    const body = payload.notification?.body || payload.data?.body || payload.data?.message || 'Nuovo messaggio crew';
    const personaggio = payload.data?.personaggio || 'Aura';

    // SPESA LIVE IN CASSA - avviso speciale che resta
    if(tipo === 'spesa_nuova'){
      const prodotto = payload.data?.prodotto || body;
      const autore = payload.data?.autore || payload.data?.da || 'Holly';
      return self.registration.showNotification("🛒 Nuova voce in lista spesa!", {
        body: `Nuova voce inserita: ${prodotto} da ${autore} - Sei ancora in cassa?`,
        icon: './bau-192x192.png',
        badge: './bau-32.png',
        vibrate: [200, 100, 200, 100, 400, 100, 400],
        data: { personaggio: 'Aura', tipo: 'spesa_nuova', prodotto, autore, ...payload.data },
        tag: 'spesa-live-' + Date.now(),
        requireInteraction: true,
        actions: [{action: 'visto', title: 'Visto, lo prendo!'}]
      });
    }

    // WHATSAPP DAL PONTE - notifica normale
    if(tipo === 'whatsapp' || title.toLowerCase().includes('whatsapp')){
      return self.registration.showNotification(title, {
        body: body,
        icon: './bau-192x192.png',
        badge: './bau-32.png',
        vibrate: [200, 100, 200],
        data: { personaggio, tipo: 'whatsapp', ...payload.data },
        tag: 'whatsapp-' + Date.now(),
        actions: [{action: 'rispondi', title: '🎤 Rispondi a voce'}]
      });
    }

    // GENERICO
    return self.registration.showNotification(title, {
      body: body,
      icon: './bau-192x192.png',
      badge: './bau-32.png',
      vibrate: [200, 100, 200],
      data: { personaggio, body, title, ...payload.data },
      tag: 'aura-live-' + Date.now()
    });
  });
} catch(err){
  console.log('Firebase compat non caricato in SW', err);
}

self.addEventListener('push', (event) => {
  // fallback per push non FCM (se usi web push diretto)
  if (event.data && event.data.json) {
    try {
      const dati = event.data.json();
      if(dati.notification || dati.data) {
        if(self.firebase && self.firebase.messaging) return;
      }
      const titolo = dati.notification?.title || 'Holly & Rex - Assistente';
      const messaggio = dati.notification?.body || 'Nuovo messaggio in arrivo';
      event.waitUntil(
        self.registration.showNotification(titolo, {
          body: messaggio,
          icon: './bau-192x192.png',
          badge: './bau-32.png',
          vibrate: [200, 100, 200],
          data: dati.data || {}
        })
      );
    } catch(e){ console.log('Push non JSON', e); }
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  const action = event.action;
  
  event.waitUntil(
    clients.matchAll({type:'window'}).then(clientList => {
      // Se clicchi su notifica spesa o whatsapp, apri assistente vocale V4
      for(const client of clientList){
        if(client.url.includes('assistente-vocale') && 'focus' in client){
          client.postMessage({type:'AURA_READ', data, action});
          return client.focus();
        }
      }
      return clients.openWindow('./assistente-vocale-v4-finale.html');
    })
  );
});

self.addEventListener('message', (event)=>{
  if(event.data && event.data.type === 'SKIP_WAITING'){
    self.skipWaiting();
  }
});
