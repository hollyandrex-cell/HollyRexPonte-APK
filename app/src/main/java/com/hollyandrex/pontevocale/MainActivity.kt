package com.hollyandrex.pontevocale

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
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

    // Bridge per far parlare la WebView con TTS NATIVO
    inner class AndroidBridge {
        @JavascriptInterface
        fun parlaNativo(testo: String) {
            runOnUiThread { parla(testo) }
        }
        @JavascriptInterface
        fun stop() {
            runOnUiThread { tts?.stop() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        db = FirebaseFirestore.getInstance()
        tts = TextToSpeech(this, this)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowFileAccessFromFileURLs = true
        webView.settings.allowUniversalAccessFromFileURLs = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // COLLEGA WEBVIEW AL NATIVO
        webView.addJavascriptInterface(AndroidBridge(), "AndroidTTS")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // SOSTITUISCE la funzione parla() della pagina HTML con quella nativa
                view?.evaluateJavascript("""
                (function(){
                  // Se esiste parla(), la sostituiamo
                  window.parlaOriginale = window.parla;
                  window.parla = function(testo){
                    try{ AndroidTTS.parlaNativo(testo); }catch(e){
                      if(window.parlaOriginale) window.parlaOriginale(testo);
                    }
                  };
                  window.speak = window.parla;
                  window.riproduci = function(){
                    var txt = document.querySelector('textarea')?.value || document.querySelector('input[type=text]')?.value || 'Ciao tesoro test ponte nostro V5 offline funziona zero giga';
                    try{ AndroidTTS.parlaNativo(txt); }catch(e){}
                  };
                  console.log('Bridge AndroidTTS installato');
                })();
                """.trimIndent(), null)
            }
        }
        webView.loadUrl("file:///android_asset/assistente-vocale-v5-finale.html")

        statusText = findViewById(R.id.statusText)
        inputDeviceId = findViewById(R.id.inputDeviceId)
        val btnPermesso = findViewById<Button>(R.id.btnPermesso)
        val btnAvvia = findViewById<Button>(R.id.btnAvvia)
        val btnTest = findViewById<Button>(R.id.btnTest)

        val prefs = getSharedPreferences("ponte", MODE_PRIVATE)
        inputDeviceId.setText(prefs.getString("deviceId", ""))

        btnPermesso.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(this, "Attiva 'Holly & Rex Ponte'", Toast.LENGTH_LONG).show()
        }
        btnAvvia.setOnClickListener {
            val deviceId = inputDeviceId.text.toString().trim()
            if(deviceId.isEmpty()){ Toast.makeText(this, "Inserisci deviceId o tutti", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            prefs.edit().putString("deviceId", deviceId).apply()
            startService(Intent(this, PonteService::class.java))
            avviaListenerRisposte(deviceId)
            statusText.text = "✅ Ponte ATTIVO V5 NATIVO\nDevice: $deviceId\nTTS: ${if(ttsReady) "PRONTO" else "carico..."}"
            parla("Ponte avviato. Pronto.")
        }
        btnTest.setOnClickListener {
            val testo = "Ciao tesoro, test ponte nostro V5 offline funziona! Zero giga! Dio cane finalmente parla!"
            parla(testo)
            statusText.text = "✅ TEST NATIVO PARLATO!"
            val deviceId = inputDeviceId.text.toString().ifEmpty { "tutti" }
            val map = hashMapOf("titolo" to "Test V5 NATIVO","corpo" to testo,"mittente" to "Test","da" to "Test","tipo" to "whatsapp","deviceIdDestinatario" to deviceId,"creato" to FieldValue.serverTimestamp(),"letto" to false,"origine" to "apk-v5-nativo-bridge")
            db.collection("notifiche_reali").add(map)
        }

        val saved = prefs.getString("deviceId", "")
        if(!saved.isNullOrEmpty()){ avviaListenerRisposte(saved); startService(Intent(this, PonteService::class.java)) }
    }

    override fun onInit(status: Int) {
        if(status==TextToSpeech.SUCCESS){
            val r = tts?.setLanguage(Locale.ITALIAN)
            ttsReady = true
            statusText.text = "✅ TTS NATIVO PRONTO - 0 giga\nClicca TEST o Riproduci viola!"
            parla("Sintesi vocale pronta tesoro. Zero giga.")
        }
    }
    private fun parla(testo: String){
        try{ tts?.stop(); tts?.speak(testo, TextToSpeech.QUEUE_FLUSH, null, "id") }
        catch(e:Exception){ Toast.makeText(this,"TTS err ${e.message}",Toast.LENGTH_SHORT).show() }
    }

    private fun avviaListenerRisposte(deviceIdMio: String){
        risposteListener?.remove()
        risposteListener = db.collection("risposte_pending").whereEqualTo("inviato", false)
            .addSnapshotListener{ snap,e-> if(e!=null||snap==null) return@addSnapshotListener
                for(doc in snap.documentChanges){ if(doc.type==com.google.firebase.firestore.DocumentChange.Type.ADDED){
                    val data=doc.document.data; val txt=data["risposta"] as? String ?: continue
                    val dest=data["deviceIdDestinatario"] as? String
                    if(dest!=null && dest!=deviceIdMio && dest!="tutti") continue
                    PonteService.rispondiUltimoWhatsApp(txt)
                    db.collection("risposte_pending").document(doc.document.id).update(mapOf("inviato" to true))
                }}
            }
    }
    override fun onDestroy(){ super.onDestroy(); risposteListener?.remove(); tts?.stop(); tts?.shutdown() }
    override fun onBackPressed(){ if(webView.canGoBack()) webView.goBack() else super.onBackPressed() }
}
