# Holly & Rex - Ponte Vocale NOSTRO - APK - Crew V4

APK NOSTRA, senza pubblicità, gratis per sempre - filosofia Holly & Rex.

## Cosa fa
- Legge notifiche WhatsApp vere con NotificationListenerService
- Scrive in Firebase `notifiche_reali` per doppio ponte (FCM + Firestore live) - idea tesoro per quando FCM dorme
- Ascolta `risposte_pending` per rispondere davvero su WhatsApp con RemoteInput
- Cancella dopo inviato per non intasare Firebase - idea tua tesoro

## Come compilare (1 click)
1. Apri Android Studio
2. File > Open > seleziona cartella HollyRexPonte
3. Metti file google-services.json scaricato da Firebase Console > holly-rex-spesa > Impostazioni progetto > Le tue app > google-services.json
4. Build > Build APK(s) -> app-debug.apk in app/build/outputs/apk/debug/
5. Installa su cellulare

## Come usare
1. Installa APK
2. Apri V4 finale: assistente-vocale-v4-finale.html > Attiva Aura Live > copia device_xxx
3. Apri APK > incolla device_xxx in Device ID (o lascia 'tutti')
4. 1️⃣ Dai permesso Notifiche > attiva Holly & Rex Ponte
5. 2️⃣ Avvia Ponte NOSTRO
6. Test con 3️⃣ - la V4 dovrebbe parlare

## Firebase
- notifiche_reali: { titolo, corpo, mittente, tipo=whatsapp, deviceIdDestinatario, creato, letto }
- risposte_pending: { risposta, destinatario, deviceIdDestinatario, inviato=false -> true poi delete }
- fcm_tokens: già usato da V4

Pulizia automatica: V4 cancella >7gg + inviato=true. Consigliato attivare TTL Policy in Firebase Console su notifiche_reali e risposte_pending campo 'creato' = 7 giorni.

NOSTRO 💛💛💛
