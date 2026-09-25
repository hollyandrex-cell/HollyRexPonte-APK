package com.hollyandrex.pontevocale

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var db: FirebaseFirestore
    private var risposteListener: ListenerRegistration? = null
    private lateinit var statusText: TextView
    private lateinit var inputDeviceId: EditText
    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // TTS NATIVO OFFLINE - 0 GIGA - FUNZIONA SEMPRE
        tts = TextToSpeech(this, this)

        webView = findViewById(R.id.webView)
        webView.webViewClient = WebViewClient()
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        webView.loadUrl("file:///android_asset/assistente-vocale-v4-finale.html")

        statusText = findViewById(R.id.statusText)
        inputDeviceId = findViewById(R.id.inputDeviceId)
        val btnPermesso = findViewById<Button>(R.id.btnPermesso)
        val btnAvvia = findViewById<Button>(R.id.btnAvvia)
        val btnTest = findViewById<Button>(R.id.btnTest)

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
                Toast.makeText(this, "Inserisci deviceId o 'tutti'", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            prefs.edit().putString("deviceId", deviceId).apply()
            startService(Intent(this, PonteService::class.java))
            avviaListenerRisposte(deviceId)
            statusText.text = "✅ Ponte V5 OFFLINE ATTIVO\nDevice: $deviceId\nTTS Nativo: ${if(ttsReady) "PRONTO" else "carico..."}\n0 giga"
            Toast.makeText(this, "Ponte V5 OFFLINE avviato! 0 giga 💛", Toast.LENGTH_SHORT).show()
            parlaNativo("Ponte avviato. Pronto a leggere WhatsApp. Zero giga.")
        }

        // TEST CHE PARLA DAVVERO OFFLINE - NON USA PIU FIREBASE PER PARLARE
        btnTest.setOnClickListener {
            val testo = "Ciao tesoro, test ponte nostro V5 offline funziona! Zero giga! Dio cane finalmente parla!"
            parlaNativo(testo)
            statusText.text = "✅ TEST NATIVO PARLATO OFFLINE!\nSe hai sentito, il TTS funziona a 0 giga!"

            // prova anche a inviare a Firebase (opzionale)
            val deviceId = inputDeviceId.text.toString().ifEmpty { "tutti" }
            val test = hashMapOf(
                "titolo" to "WhatsApp da Test Ponte V5 OFFLINE",
                "corpo" to testo,
                "mittente" to "Test Holly V5",
                "da" to "Test Holly",
                "tipo" to "whatsapp",
                "personaggio" to "Aura",
                "deviceIdDestinatario" to deviceId,
                "creato" to FieldValue.serverTimestamp(),
                "letto" to false,
                "origine" to "apk-nostra-v5-offline-nativo"
            )
            db.collection("notifiche_reali").add(test)
        }

        if(!savedId.isNullOrEmpty()){
            avviaListenerRisposte(savedId)
            startService(Intent(this, PonteService::class.java))
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.ITALIAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
                statusText.text = "⚠️ Voce italiana mancante, uso inglese. Installa TTS italiano!"
            } else {
                ttsReady = true
                statusText.text = "✅ TTS Nativo PRONTO - 0 giga\nClicca TEST per provare!"
                // parla subito appena pronto
                parlaNativo("Sintesi vocale pronta, tesoro. Zero giga.")
            }
        }
    }

    private fun parlaNativo(testo: String) {
        try {
            tts?.stop()
            // QUEUE_FLUSH per parlare subito
            tts?.speak(testo, TextToSpeech.QUEUE_FLUSH, null, "test_id")
        } catch (e: Exception) {
            Toast.makeText(this, "Errore TTS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

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
                        val deviceDest = data["deviceIdDestinatario"] as? String
                        if(deviceDest != null && deviceDest != deviceIdMio && deviceDest != "tutti") continue
                        PonteService.rispondiUltimoWhatsApp(testoRisposta)
                        db.collection("risposte_pending").document(doc.document.id)
                            .update(mapOf("inviato" to true, "inviatoIl" to FieldValue.serverTimestamp()))
                            .addOnSuccessListener {
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
        tts?.stop()
        tts?.shutdown()
    }

    override fun onBackPressed() {
        if(::webView.isInitialized && webView.canGoBack()){
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
