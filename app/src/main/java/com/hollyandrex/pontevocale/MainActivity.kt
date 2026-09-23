package com.hollyandrex.pontevocale

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private var risposteListener: ListenerRegistration? = null
    private lateinit var statusText: TextView
    private lateinit var inputDeviceId: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        statusText = findViewById(R.id.statusText)
        inputDeviceId = findViewById(R.id.inputDeviceId)
        val btnPermesso = findViewById<Button>(R.id.btnPermesso)
        val btnAvvia = findViewById<Button>(R.id.btnAvvia)
        val btnTest = findViewById<Button>(R.id.btnTest)

        // carica deviceId salvato da V4 finale (crew_device_id)
        val prefs = getSharedPreferences("ponte", MODE_PRIVATE)
        val savedId = prefs.getString("deviceId", "")
        inputDeviceId.setText(savedId)

        btnPermesso.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Attiva 'Holly & Rex Ponte' nei permessi", Toast.LENGTH_LONG).show()
        }

        btnAvvia.setOnClickListener {
            val deviceId = inputDeviceId.text.toString().trim()
            if(deviceId.isEmpty()){
                Toast.makeText(this, "Inserisci deviceId della V4 (es: device_abc123) o 'tutti'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().putString("deviceId", deviceId).apply()
            startService(Intent(this, PonteService::class.java))
            avviaListenerRisposte(deviceId)
            statusText.text = "✅ Ponte NOSTRO attivo\nDevice: $deviceId\nLeggo WhatsApp veri e mando a notifiche_reali + fcm_tokens"
            Toast.makeText(this, "Ponte avviato! Ora è NOSTRO 💛", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val deviceId = inputDeviceId.text.toString().ifEmpty { "tutti" }
            val test = hashMapOf(
                "titolo" to "WhatsApp da Test Ponte",
                "corpo" to "Ciao tesoro, test ponte NOSTRO funziona! 💛",
                "mittente" to "Test Holly",
                "da" to "Test Holly",
                "tipo" to "whatsapp",
                "personaggio" to "Aura",
                "deviceIdDestinatario" to deviceId,
                "creato" to FieldValue.serverTimestamp(),
                "letto" to false,
                "origine" to "apk-nostra"
            )
            db.collection("notifiche_reali").add(test)
                .addOnSuccessListener { statusText.text = "✅ Test inviato a notifiche_reali\nLa V4 dovrebbe parlare!" }
                .addOnFailureListener { e -> statusText.text = "❌ Errore test: ${e.message}" }
        }

        // avvia automatico se già configurato
        if(!savedId.isNullOrEmpty()){
            avviaListenerRisposte(savedId)
        }
    }

    // Ascolta risposte_pending per rispondere davvero su WhatsApp con RemoteInput
    private fun avviaListenerRisposte(deviceIdMio: String){
        risposteListener?.remove()
        risposteListener = db.collection("risposte_pending")
            .whereEqualTo("inviato", false)
            .addSnapshotListener { snap, e ->
                if(e != null || snap == null) return@addSnapshotListener
                for(doc in snap.documentChanges){
                    if(doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED){
                        val data = doc.document.data
                        val testoRisposta = data["risposta"] as? String ?: continue
                        val destinatario = data["destinatario"] as? String ?: data["mittente"] as? String ?: ""
                        val deviceDest = data["deviceIdDestinatario"] as? String
                        // se è per questo telefono o per tutti
                        if(deviceDest != null && deviceDest != deviceIdMio && deviceDest != "tutti") continue
                        // Prova a rispondere tramite PonteService
                        PonteService.rispondiUltimoWhatsApp(testoRisposta)
                        // Segna come inviato e poi cancella per non intasare Firebase - idea tua tesoro
                        db.collection("risposte_pending").document(doc.document.id)
                            .update(mapOf("inviato" to true, "inviatoIl" to FieldValue.serverTimestamp()))
                            .addOnSuccessListener {
                                // cancella dopo 3 secondi per risparmiare spazio - come dicevi tu
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    db.collection("risposte_pending").document(doc.document.id).delete()
                                }, 3000)
                            }
                        statusText.text = "📤 Risposta inviata su WhatsApp: $testoRisposta"
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        risposteListener?.remove()
    }
}
