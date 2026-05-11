package com.inversioneswing.paymirror

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.regex.Pattern

/* --- PROTOCOLO STARK v61.0: STEALTH MASTER ---
   ESTADO: 100% SILENCIOSO (MODO SIGILO)
   EL CELULAR NO EMITE SONIDOS NI VOZ. TODA LA ALERTA VIAJA A LA PC.
*/

class StarkCaptureService : NotificationListenerService() {

    private val CHANNEL_ID = "WING_STEALTH_CHANNEL"
    private var pcListenerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wakeLock: PowerManager.WakeLock
    
    private var currentTopic: String = "wingpay_client_A2ZQV4"

    companion object {
        private var activeInstance: StarkCaptureService? = null
        fun sendAudioCommand(text: String) { /* STEALTH: MUDO */ }
        fun triggerRemoteSOS() { activeInstance?.enviarSOSaPC() }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WingPay:StealthLock")
        if (!wakeLock.isHeld) { wakeLock.acquire() }
        
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
                                    if (data.optString("sender") == "PC") {
                                        // STEALTH: Solo log interno, el celular no habla
                                        Log.d("STARK_STEALTH", "Data from PC: $msgRaw")
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) { delay(10000) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("UPDATE_CODE")?.let { reloadTopic() }
        
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
            // IMPORTANCE_LOW: Para que no haga ruido ni vibre el sistema
            val channel = NotificationChannel(CHANNEL_ID, "WingPay Stealth Mode", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Importaciones Wing Stealth")
        .setContentText("Vigilancia Maestra 2026 - Modo Silencioso")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_LOW)
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
            
            // STEALTH: Solo enviamos a la PC, cero ruidos en celular
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
                    setRequestProperty("Title", "ALERTA_SOS")
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

    override fun onDestroy() {
        activeInstance = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
