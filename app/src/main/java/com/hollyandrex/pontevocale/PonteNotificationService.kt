package com.hollyandrex.pontevocale

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.firebase.firestore.FirebaseFirestore

class PonteNotificationService : NotificationListenerService() {

    private val db = FirebaseFirestore.getInstance()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName != "com.whatsapp") return

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: "WhatsApp"
        val text = extras.getCharSequence("android.text")?.toString() ?: return
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
        if (text.isBlank()) return

        val prefs = getSharedPreferences("hollyrex_ponte", MODE_PRIVATE)
        val deviceId = prefs.getString("device_id", "tutti") ?: "tutti"

        val data = hashMapOf(
            "deviceId" to deviceId,
            "mittente" to title,
            "messaggio" to bigText,
            "pacchetto" to sbn.packageName,
            "timestamp" to System.currentTimeMillis(),
            "letto" to false
        )

        db.collection("notifiche_reali").add(data)
    }
}
