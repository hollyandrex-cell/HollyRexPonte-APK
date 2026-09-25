package com.hollyandrex.pontevocale

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private var risposteListener: ListenerRegistration? = null
    private lateinit var statusText: TextView
    private lateinit var inputDeviceId: EditText
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()

        // === WEBVIEW OFFLINE V5 - FIX VOCI SBLOCCATE - TESORO NOSTRO - 0 GIGA ===
        webView = findViewById(R.id.webView)
        webView.webChromeClient = android.webkit.WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript("""
                    (function() {
                        function sbloccaVoci() {
                            try {
                                var v = window.speechSynthesis.getVoices();
                                if (v.length > 0) {
                                    if (typeof caricaVoci === 'function') caricaVoci();
                                    if (typeof populateVoiceList === 'function') populateVoiceList();
                                    if (typeof loadVoices === 'function') loadVoices();
                                }
                            } catch(e) {}
                        }
                        sbloccaVoci();
                        window.speechSynthesis.onvoiceschanged = sbloccaVoci;
                        setTimeout(sbloccaVoci, 500);
                        setTimeout(sbloccaVoci, 1500);
                    })();
                """.trimIndent(), null)
            }
        }
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        // Carica la pagina DENTRO l'APK, non da GitHub!
        webView.loadUrl("file:///android_asset/assistente-vocale-v4-finale.html")

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
            statusText.text = "✅ Ponte NOSTRO attivo V5 OFFLINE\nDevice: $deviceId\nWebView: file:///android_asset/\nLeggo WhatsApp veri e mando a notifiche_reali + fcm_tokens"
            Toast.makeText(this, "Ponte V5 OFFLINE avviato! 0 giga 💛", Toast.LENGTH_SHORT).show()
        }

        btnTest.setOnClickListener {
            val deviceId = inputDeviceId.text.toString().ifEmpty { "tutti" }
            val test = hashMapOf(
                "titolo" to "WhatsApp da Test Ponte V5 OFFLINE",
                "corpo" to "Ciao tesoro, test ponte NOSTRO V5 OFFLINE funziona! 💛 0 giga!",
                "mittente" to "Test Holly V5",
                "da" to "Test Holly",
                "tipo" to "whatsapp",
                "personaggio" to "Aura",
                "deviceIdDestinatario" to deviceId,
                "creato" to FieldValue.serverTimestamp(),
                "letto" to false,
                "origine" to "apk-nostra-v5-offline"
            )
            db.collection("notifiche_reali").add(test)
                .addOnSuccessListener { statusText.text = "✅ Test V5 OFFLINE inviato a notifiche_reali\nLa WebView dovrebbe parlare!" }
                .addOnFailureListener { e -> statusText.text = "❌ Errore test: ${e.message}" }
        }

        // avvia automatico se già configurato
        if(!savedId.isNullOrEmpty()){
            avviaListenerRisposte(savedId)
            // avvia anche il ponte automaticamente
            startService(Intent(this, PonteService::class.java))
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

    // Tasto indietro: se WebView può tornare indietro, torna indietro, altrimenti chiudi
    override fun onBackPressed() {
        if(::webView.isInitialized && webView.canGoBack()){
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
