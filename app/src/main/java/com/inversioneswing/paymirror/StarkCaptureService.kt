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

    // --- RECEPTOR NEURAL MEJORADO v44.0 ---
    private val internalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("VOICE_CMD")?.let { 
                Log.d("STARK", "Recibido VOICE_CMD: $it")
                awakeAndSpeak(it) 
            }
            if (intent?.getBooleanExtra("SOS_CMD", false) == true) { 
                Log.d("STARK", "Recibido SOS_CMD")
                enviarSOSaPC() 
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WingPay:WakeLock")
        if (!wakeLock.isHeld) { wakeLock.acquire() }
        
        // Registrar con permisos de alta prioridad
        val filter = IntentFilter("com.inversioneswing.STARK_INTERNAL_CMD")
        registerReceiver(internalReceiver, filter)
        
        // Iniciar motor de voz con prioridad
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
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Forzar audio por canal de ALARMA para bypass de silencio en Huawei
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        
        if (isTtsReady) {
            speak(text)
        } else {
            Log.d("STARK", "TTS No listo, guardando en cola: $text")
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
            val channel = NotificationChannel(CHANNEL_ID, "WING Sentinel OMEGA", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Sincronización Stark Silenciosa"
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Importaciones Wing v44.0")
        .setContentText("Omni-Link Neural Core Activo")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
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

            awakeAndSpeak("Aviso de Pago. $banco. $nombreLimpio te envió $montoRaw soles.")
            networkExecutor.execute {
                sendToMirror(banco, nombreLimpio, montoRaw)
                sendToTelegram("💰 *PAGO RECIBIDO:* $banco\n👤 *CLIENTE:* $nombreLimpio\n💵 *MONTO:* S/ $montoRaw")
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
                setRequestProperty("Priority", "4")
                val json = JSONObject().apply {
                    put("bank", banco); put("name", nombre); put("amt", monto); put("stark_log", "PROCESADO_OK")
                }
                OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                responseCode; disconnect()
            }
        } catch (e: Exception) {}
    }

    private fun enviarSOSaPC() {
        Log.d("STARK", "Enviando SOS FORZADO a PC")
        networkExecutor.execute {
            try {
                val topic = "wingpay_stark_8502345704"
                val url = URL("https://ntfy.sh/$topic")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Title", "ALERTA_SOS_CRITICA")
                    setRequestProperty("Priority", "5") // MÁXIMA PRIORIDAD
                    setRequestProperty("Tags", "rotating_light,skull,fire")
                    val json = JSONObject().apply {
                        put("type", "SOS"); put("stark_log", "SIRENA_FORZADA_REQUERIDA")
                    }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    responseCode; disconnect()
                }
            } catch (e: Exception) {}
        }
    }

    private fun speak(text: String) {
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, "STARK_ID_" + System.currentTimeMillis())
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "PE")
            isTtsReady = true
            Log.d("STARK", "TTS Inicializado con éxito. Procesando cola...")
            synchronized(pendingMessages) {
                val iterator = pendingMessages.iterator()
                while (iterator.hasNext()) {
                    speak(iterator.next())
                    iterator.remove()
                }
            }
        } else {
            Log.e("STARK", "Error inicializando TTS")
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
        serviceJob.cancel()
        if (::tts.isInitialized) { tts.shutdown() }
        super.onDestroy()
    }
}
