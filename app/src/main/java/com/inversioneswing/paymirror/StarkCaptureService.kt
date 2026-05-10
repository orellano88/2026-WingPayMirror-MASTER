package com.inversioneswing.paymirror

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import java.util.concurrent.Executors

class StarkCaptureService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val CHANNEL_ID = "WING_OMEGA_CHANNEL"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val networkExecutor = Executors.newSingleThreadExecutor()
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private val pendingMessages = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var wakeLock: PowerManager.WakeLock
    private var toneGenerator: ToneGenerator? = null

    // --- RECEPTOR NEURAL v45.0 (EXPLICIT ONLY) ---
    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val voiceMsg = intent?.getStringExtra("VOICE_CMD")
            if (voiceMsg != null) {
                Log.d("STARK", "Recibido VOICE_CMD Explicito: $voiceMsg")
                awakeAndSpeak(voiceMsg)
            }
            if (intent?.getBooleanExtra("SOS_CMD", false) == true) {
                enviarSOSaPC()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WingPay:WakeLock")
        if (!wakeLock.isHeld) { wakeLock.acquire() }
        
        // Inicializar Generador de Tonos (Buzzer Hardware)
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) { Log.e("STARK", "Error ToneGen") }

        // Registrar Receptor Neural con Exported flag para Android 14+
        val filter = IntentFilter("com.inversioneswing.STARK_INTERNAL_CMD")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(internalReceiver, filter)
        }
        
        tts = TextToSpeech(this, this)
        startPCListener()
    }

    private fun startPCListener() {
        serviceScope.launch(Dispatchers.IO) {
            val topic = "wingpay_stark_8502345704"
            val url = URL("https://ntfy.sh/$topic/json")
            while (isActive) {
                try {
                    val conn = url.openConnection() as HttpURLConnection
                    conn.readTimeout = 0 
                    conn.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val json = JSONObject(line)
                            if (json.has("message")) {
                                val msg = json.getString("message")
                                if (msg.contains("PC_CMD:")) {
                                    awakeAndSpeak(msg.replace("PC_CMD:", "").trim())
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    delay(15000)
                }
            }
        }
    }

    private fun awakeAndSpeak(text: String) {
        if (!wakeLock.isHeld) { wakeLock.acquire(15 * 1000L) }
        
        // 1. DISPARAR BEEP DE HARDWARE (Imposible de bloquear)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {}

        // 2. HABLAR
        if (isTtsReady) {
            speak(text)
        } else {
            pendingMessages.add(text)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(101, notification)
        }
        
        intent?.getStringExtra("TEST_VOICE")?.let { awakeAndSpeak(it) }
        intent?.getBooleanExtra("TRIGGER_SOS", false)?.let { if(it) enviarSOSaPC() }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WingPay Core", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notificaciones de Pago Stark"
                enableLights(true)
                lightColor = Color.CYAN
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Importaciones Wing v45.0")
        .setContentText("Cerebro Neural en Linea")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true)
        .build()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName.lowercase()
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullContent = "$title | $text".trim()

        if (pkg.contains("yape") || pkg.contains("bcp") || pkg.contains("plin") || pkg.contains("interbank")) {
            processSmartContent(fullContent, pkg)
        }
    }

    private fun processSmartContent(content: String, pkg: String): Boolean {
        val regex = Pattern.compile("(?i)(S\\\\s*/?\\\\s*\\\\.?)\\\\s*([\\\\d,]+\\\\.\\\\d{2}|[\\\\d,]+)")
        val matcher = regex.matcher(content)
        
        if (matcher.find()) {
            val montoRaw = matcher.group(2)?.replace(",", "") ?: "0.00"
            val nombreRaw = content.replace(matcher.group(0)!!, "", true).replace(Regex("[^a-zA-Z\\\\s]"), "").trim()
            val nombreLimpio = if (nombreRaw.isEmpty()) "un cliente" else nombreRaw.lowercase().capitalize()
            val banco = if (pkg.contains("yape")) "YAPE" else "BCP"

            awakeAndSpeak("Atención. Pago de $nombreLimpio por $montoRaw soles en $banco.")
            networkExecutor.execute {
                sendToMirror(banco, nombreLimpio, montoRaw)
                sendToTelegram("💰 *PAGO:* $banco | $nombreLimpio | S/ $montoRaw")
            }
            return true
        }
        return false
    }

    private fun sendToMirror(banco: String, nombre: String, monto: String) {
        try {
            val topic = "wingpay_stark_8502345704"
            val url = URL("https://ntfy.sh/$topic")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Title", "PAGO $banco")
                setRequestProperty("Priority", "5")
                val json = JSONObject().apply {
                    put("bank", banco); put("name", nombre); put("amt", monto); put("stark_log", "SYNC_OK")
                }
                OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                responseCode; disconnect()
            }
        } catch (e: Exception) {}
    }

    private fun enviarSOSaPC() {
        networkExecutor.execute {
            try {
                val topic = "wingpay_stark_8502345704"
                val url = URL("https://ntfy.sh/$topic")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Title", "ALERTA_SOS")
                    setRequestProperty("Priority", "5")
                    setRequestProperty("Tags", "rotating_light,skull")
                    val json = JSONObject().apply { put("type", "SOS"); put("stark_log", "SIRENA_5S") }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    responseCode; disconnect()
                }
            } catch (e: Exception) {}
        }
    }

    private fun speak(text: String) {
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "STARK_" + System.currentTimeMillis())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "ES")
            isTtsReady = true
            synchronized(pendingMessages) {
                val iterator = pendingMessages.iterator()
                while (iterator.hasNext()) {
                    speak(iterator.next())
                    iterator.remove()
                }
            }
        }
    }

    private fun sendToTelegram(message: String) {
        val token = "8629465941:AAH-5rwmNDTP_91UKZIRrJO_oZ24p1IcIQE"
        val chatId = "8502345704"
        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json")
                OutputStreamWriter(outputStream).use { it.write(JSONObject().apply { put("chat_id", chatId); put("text", message); put("parse_mode", "Markdown") }.toString()) }
                responseCode; disconnect()
            }
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        try { unregisterReceiver(internalReceiver) } catch (e: Exception) {}
        toneGenerator?.release()
        if (::tts.isInitialized) { tts.shutdown() }
        super.onDestroy()
    }
}
