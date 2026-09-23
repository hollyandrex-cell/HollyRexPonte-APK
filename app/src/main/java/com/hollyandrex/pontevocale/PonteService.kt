package com.hollyandrex.pontevocale

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.content.Context

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

    override fun onCreate() {
        super.onCreate()
        contextApp = applicationContext
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Ponte NOSTRO creato - Crew V4")
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
            "origine" to "apk-nostra-ponte-vocale",
            "package" to pkg
        )
        db.collection("notifiche_reali").add(doc)
            .addOnSuccessListener { Log.d(TAG, "WhatsApp inviato a notifiche_reali: $titolo - $testo") }
            .addOnFailureListener { e -> Log.e(TAG, "Errore invio notifiche_reali", e) }

        Log.d(TAG, "Ponte: $titolo -> $testo verso $deviceIdDest")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    }
}
