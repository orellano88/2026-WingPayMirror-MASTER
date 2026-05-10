package com.inversioneswing.paymirror

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
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

    private val CHANNEL_ID = "WING_CORE_CHANNEL"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private val pendingMessages = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var wakeLock: PowerManager.WakeLock
    private var toneGenerator: ToneGenerator? = null

    // --- RECEPTOR NEURAL v46.0 (RECUPERACIÓN TOTAL) ---
    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("VOICE_CMD")?.let { awakeAndSpeak(it) }
            if (intent?.getBooleanExtra("SOS_CMD", false) == true) { 
                awakeAndSpeak("Activando protocolo de pánico.")
                enviarSOSaPC() 
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WingPay:WakeLock")
        if (!wakeLock.isHeld) { wakeLock.acquire() }
        
        try { toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (e: Exception) {}

        registerReceiver(internalReceiver, IntentFilter("com.inversioneswing.STARK_INTERNAL_CMD"))
        tts = TextToSpeech(this, this)
        startPCListener()
    }

    private fun startPCListener() {
        serviceScope.launch(Dispatchers.IO) {
            val url = URL("https://ntfy.sh/wingpay_stark_8502345704/json")
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
                } catch (e: Exception) { delay(15000) }
            }
        }
    }

    private fun awakeAndSpeak(text: String) {
        if (!wakeLock.isHeld) { wakeLock.acquire(15 * 1000L) }
        try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 250) } catch (e: Exception) {}
        if (isTtsReady) speak(text) else pendingMessages.add(text)
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
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WingPay Core", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Importaciones Wing v46.0")
        .setContentText("Protección de Pagos Activa")
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
            val banco = if (pkg.contains("yape")) "YAPE" else "BCP"
            awakeAndSpeak("Pago de $montoRaw soles en $banco.")
            serviceScope.launch(Dispatchers.IO) {
                sendToMirror(banco, "Cliente", montoRaw)
            }
            return true
        }
        return false
    }

    private fun sendToMirror(banco: String, nombre: String, monto: String) {
        try {
            val url = URL("https://ntfy.sh/wingpay_stark_8502345704")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Title", "PAGO $banco")
                val json = JSONObject().apply {
                    put("bank", banco); put("name", nombre); put("amt", monto); put("stark_log", "SYNC_OK")
                }
                OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                responseCode; disconnect()
            }
        } catch (e: Exception) {}
    }

    private fun enviarSOSaPC() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/wingpay_stark_8502345704")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Title", "ALERTA_SOS")
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
            tts.language = Locale("es", "PE")
            isTtsReady = true
            synchronized(pendingMessages) {
                val iterator = pendingMessages.iterator()
                while (iterator.hasNext()) { speak(iterator.next()); iterator.remove() }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(internalReceiver) } catch (e: Exception) {}
        serviceJob.cancel()
        super.onDestroy()
    }
}
