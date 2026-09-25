package com.hollyandrex.pontevocale

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class PonteService : NotificationListenerService() {

    companion object {
        var ultimaNotificaWhatsApp: StatusBarNotification? = null
        var ultimoBundleAzioni: Notification.Action? = null
        var contextApp: Context? = null

        fun rispondiUltimoWhatsApp(testo: String){
            try{
                val sbn = ultimaNotificaWhatsApp ?: return
                val notification = sbn.notification
                val azioni = notification.actions ?: return
                val ctx = contextApp ?: return
                for(azione in azioni){
                    if(azione.remoteInputs != null && azione.remoteInputs.isNotEmpty()){
                        val remoteInputs = azione.remoteInputs
                        val intent = Intent()
                        val bundle = Bundle()
                        for(ri in remoteInputs){
                            bundle.putCharSequence(ri.resultKey, testo)
                        }
                        RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                        try{
                            azione.actionIntent.send(ctx, 0, intent)
                            Log.d("PonteNOSTRO", "Risposta inviata: $testo")
                        }catch(e: Exception){
                            Log.e("PonteNOSTRO", "Errore invio risposta", e)
                        }
                        break
                    }
                }
            }catch(e: Exception){
                Log.e("PonteNOSTRO", "rispondiUltimoWhatsApp errore", e)
            }
        }
    }

    private lateinit var db: FirebaseFirestore
    private val TAG = "PonteNOSTRO"
    private val CHANNEL_ID = "ponte_channel_v5"
    private val NOTIF_ID = 101

    override fun onCreate() {
        super.onCreate()
        contextApp = applicationContext
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Ponte NOSTRO creato - Crew V4 V5 OFFLINE")

        // Crea canale notifica fissa
        creaCanale()
        avviaForeground()
    }

    // === TESORO - QUESTA è L'ISTRUZIONE CHE MANCAVA! ===
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand V5 OFFLINE - START_STICKY")
        creaCanale()
        avviaForeground()
        // Se Android lo killa, lo riavvia da solo!
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Ponte V5 OFFLINE Connesso - in ascolto WhatsApp 💛")
        avviaForeground()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Ponte disconnesso, chiedo rebind...")
        // Chiede ad Android di ri-collegare il listener - trucco per restare vivo
        requestRebind(android.content.ComponentName(this, PonteService::class.java))
    }

    private fun creaCanale() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Holly & Rex Ponte V5 OFFLINE",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ponte vocale NOSTRO sempre attivo - 0 giga"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun avviaForeground() {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Holly & Rex Ponte V5 OFFLINE 💛")
                .setContentText("In ascolto WhatsApp veri - App chiudibile!")
                .setSmallIcon(android.R.drawable.sym_def_app_icon) // usa icona di sistema, poi metteremo la vostra
                .setOngoing(true) // non si può scorrere via!
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            startForeground(NOTIF_ID, notification)
            Log.d(TAG, "Foreground avviato - Ora puoi chiudere l'app!")
        } catch (e: Exception) {
            Log.e(TAG, "Errore foreground", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if(sbn == null) return
        val pkg = sbn.packageName ?: return
        if(!pkg.contains("whatsapp") && !pkg.contains("com.whatsapp")) return

        val extras = sbn.notification.extras
        val titolo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "WhatsApp"
        val testo = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        if(testo.isBlank()) return
        if(titolo.contains("WhatsApp") && testo.length < 2) return

        ultimaNotificaWhatsApp = sbn
        for(act in sbn.notification.actions ?: emptyArray()){
            if(act.remoteInputs != null && act.remoteInputs.isNotEmpty()){
                ultimoBundleAzioni = act
                break
            }
        }

        val prefs = getSharedPreferences("ponte", MODE_PRIVATE)
        val deviceIdDest = prefs.getString("deviceId", "tutti") ?: "tutti"

        val doc = hashMapOf(
            "titolo" to titolo,
            "corpo" to testo,
            "mittente" to titolo.replace("WhatsApp", "").trim(),
            "da" to titolo,
            "message" to testo,
            "body" to testo,
            "tipo" to "whatsapp",
            "type" to "whatsapp",
            "personaggio" to "Aura",
            "deviceIdDestinatario" to deviceIdDest,
            "creato" to FieldValue.serverTimestamp(),
            "letto" to false,
            "origine" to "apk-nostra-ponte-vocale-v5-offline",
            "package" to pkg
        )
        db.collection("notifiche_reali").add(doc)
            .addOnSuccessListener { Log.d(TAG, "WhatsApp inviato a notifiche_reali: $titolo - $testo") }
            .addOnFailureListener { e -> Log.e(TAG, "Errore invio notifiche_reali", e) }

        Log.d(TAG, "Ponte V5: $titolo -> $testo verso $deviceIdDest")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}