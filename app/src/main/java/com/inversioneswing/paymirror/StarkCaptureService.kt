package com.inversioneswing.paymirror

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern

/* --- PROTOCOLO STARK v63.5: MASTER CROSS-ALARM ---
   ESTADO: SELECTIVE AUDIO (ALERTA CRUZADA)
   - SOS LOCAL: Silencio total en celular, alerta a PC.
   - SOS REMOTO (PC): El celular dispara sirena y voz.
   - PAGOS: El celular anuncia para aviso del cajero.
*/

class StarkCaptureService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val CHANNEL_ID = "WING_CROSS_CHANNEL"
    private var pcListenerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private val pendingMessages = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var wakeLock: PowerManager.WakeLock
    private var toneGenerator: ToneGenerator? = null
    
    private var currentTopic: String = "wingpay_client_A2ZQV4"

    companion object {
        private var activeInstance: StarkCaptureService? = null
        fun sendAudioCommand(text: String) { activeInstance?.awakeAndSpeak(text) }
        fun triggerRemoteSOS() { activeInstance?.enviarSOSaPC() }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WingPay:CrossLock")
        if (!wakeLock.isHeld) { wakeLock.acquire() }
        
        try { toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (e: Exception) {}
        tts = TextToSpeech(this, this)
        reloadTopic()
    }

    private fun reloadTopic() {
        val prefs = getSharedPreferences("STARK_PREFS", MODE_PRIVATE)
        currentTopic = prefs.getString("CLIENT_CODE", "wingpay_client_A2ZQV4")!!
        startPCListener()
    }

    private fun startPCListener() {
        pcListenerJob?.cancel()
        pcListenerJob = serviceScope.launch(Dispatchers.IO) {
            val url = URL("https://ntfy.sh/$currentTopic/json")
            while (isActive) {
                try {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.readTimeout = 0 
                    conn.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val json = JSONObject(line)
                            if (json.has("message")) {
                                val msgRaw = json.getString("message")
                                try {
                                    val data = JSONObject(msgRaw)
                                    // REGLA: REACCIONAR SOLO SI VIENE DE LA PC
                                    if (data.optString("sender") == "PC") {
                                        val type = data.optString("type", "")
                                        if (type == "SOS") {
                                            dispararAlarmaLocal("¡ALERTA CRÍTICA! SEÑAL DE PÁNICO RECIBIDA DESDE EL MANDO CENTRAL.")
                                        } else {
                                            awakeAndSpeak(data.optString("message", ""))
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) { delay(10000) }
            }
        }
    }

    private fun dispararAlarmaLocal(text: String) {
        // ACTIVAR SONIDO SOLO EN RESPUESTA A LA PC
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 500), -1))
        
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val r = RingtoneManager.getRingtone(applicationContext, notification)
            r.play()
            Handler(Looper.getMainLooper()).postDelayed({ r.stop() }, 5000)
        } catch (e: Exception) {}
        
        awakeAndSpeak(text)
    }

    fun awakeAndSpeak(text: String) {
        if (!wakeLock.isHeld) { wakeLock.acquire(15 * 1000L) }
        try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 250) } catch (e: Exception) {}
        if (isTtsReady) {
            val params = Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "STARK_" + System.currentTimeMillis())
        } else {
            pendingMessages.add(text)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("UPDATE_CODE")?.let { reloadTopic() }
        
        if (intent != null) {
            if (intent.getBooleanExtra("CMD_SOS", false)) {
                // SOS LOCAL -> SILENCIO EN CELULAR, ENVÍO A PC
                enviarSOSaPC()
            } else if (intent.getBooleanExtra("CMD_PAYMENT", false)) {
                val b = intent.getStringExtra("BANK") ?: "STARK"
                val n = intent.getStringExtra("NAME") ?: "Cliente"
                val a = intent.getStringExtra("AMT") ?: "0.00"
                
                // ANUNCIO DE PAGO EN CELULAR (OPCIONAL SEGÚN Wilson)
                awakeAndSpeak("Pago de $n por $a soles recibido en $b.")
                
                serviceScope.launch(Dispatchers.IO) { sendToMirror(b, n, a) }
            }
        }

        createNotificationChannel()
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WingPay Master Sync", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Importaciones Wing 2026")
        .setContentText("Neural Cross-Alarm v63.5 Active")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true).build()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullContent = "$title | $text | $bigText".trim()

        if (pkg.contains("yape") || pkg.contains("bcp") || pkg.contains("plin") || pkg.contains("interbank")) {
            processSmartContent(fullContent, pkg)
        }
    }

    private fun processSmartContent(content: String, pkg: String): Boolean {
        val regex = Pattern.compile("(?i)(S\\\\s*/?\\\\s*\\\\.?)\\\\s*([\\\\d,]+\\\\.\\\\d{2}|[\\\\d,]+)")
        val matcher = regex.matcher(content)
        if (matcher.find()) {
            val montoRaw = matcher.group(2)?.replace(",", "") ?: "0.00"
            val montoFull = matcher.group(0)!!
            var nombreRaw = content.replace(montoFull, "", true).replace(Regex("[^a-zA-Z\\\\sñÑáéíóúÁÉÍÓÚ]"), "").trim()
            val nombreLimpio = if (nombreRaw.isEmpty()) "un cliente" else nombreRaw.lowercase().capitalize()
            val banco = when {
                pkg.contains("yape") -> "YAPE"
                pkg.contains("plin") -> "PLIN"
                pkg.contains("interbank") -> "INTERBANK"
                pkg.contains("bcp") -> "BCP"
                else -> "BANCO"
            }
            
            // HABLAR PAGO EN CELULAR
            awakeAndSpeak("¡Aviso de Pago! $banco. $nombreLimpio te envió $montoRaw soles.")
            serviceScope.launch(Dispatchers.IO) { sendToMirror(banco, nombreLimpio, montoRaw) }
            return true
        }
        return false
    }

    private fun sendToMirror(banco: String, nombre: String, monto: String) {
        try {
            val url = URL("https://ntfy.sh/$currentTopic")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                val json = JSONObject().apply { 
                    put("sender", "PHONE")
                    put("bank", banco)
                    put("name", nombre)
                    put("amt", monto)
                }
                OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                responseCode; disconnect()
            }
        } catch (e: Exception) {}
    }

    fun enviarSOSaPC() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/$currentTopic")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    val json = JSONObject().apply { 
                        put("sender", "PHONE")
                        put("type", "SOS")
                        put("message", "ALERTA_SOS_CRITICA") 
                    }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    responseCode; disconnect()
                }
            } catch (e: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "PE")
            isTtsReady = true
            synchronized(pendingMessages) {
                val iterator = pendingMessages.iterator()
                while (iterator.hasNext()) { awakeAndSpeak(iterator.next()); iterator.remove() }
            }
        }
    }

    override fun onDestroy() {
        activeInstance = null
        serviceScope.cancel()
        if (::tts.isInitialized) { tts.shutdown() }
        super.onDestroy()
    }
}
