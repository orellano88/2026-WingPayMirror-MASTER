package com.inversioneswing.paymirror

import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var terminalView: TextView
    private lateinit var statusLED: View
    private lateinit var syncLED: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- FONDO STARK GRADIENT ---
        val starkBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())
        )
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = starkBackground
        }

        // --- CABECERA STARK ---
        val header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 250)
            setPadding(20, 20, 20, 20)
        }

        val title = TextView(this).apply {
            text = "STARK OS v40.8"
            textSize = 26f
            setTextColor(0xFF00E5FF.toInt()) // Cyan Neón
            setTypeface(null, Typeface.BOLD)
        }
        
        val subTitle = TextView(this).apply {
            text = "STARK LOGIC REPAIR EDITION"
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subTitle)
        }
        header.addView(titleBox)

        // Indicadores LED
        val ledContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            layoutParams = lp
            gravity = Gravity.CENTER_VERTICAL
        }

        statusLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.RED)
        }
        
        syncLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.GRAY)
        }

        ledContainer.addView(TextView(this).apply { text = "RED"; setTextColor(Color.WHITE); textSize = 10f })
        ledContainer.addView(statusLED)
        ledContainer.addView(TextView(this).apply { text = "SYNC"; setTextColor(Color.WHITE); textSize = 10f })
        ledContainer.addView(syncLED)
        header.addView(ledContainer)

        // --- STARK TERMINAL (LOGS EN VIVO) ---
        val termContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400).apply { setMargins(0, 40, 0, 40) }
            background = getGlassDrawable(0x66000000.toInt())
            setPadding(20, 20, 20, 20)
        }

        terminalView = TextView(this).apply {
            text = "[SISTEMA]: Inicializando protocolos Stark...\n[SISTEMA]: Núcleo listo."
            textSize = 12f
            setTextColor(0xFF00FF41.toInt()) // Verde Matrix
            typeface = Typeface.MONOSPACE
        }
        
        val scroll = ScrollView(this).apply { addView(terminalView) }
        termContainer.addView(scroll)

        // --- BOTONES DE ACCIÓN ---
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            weightSum = 3f
        }

        val btnSettings = createGlassButton("⚙", 1f) { 
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        
        val btnTest = createGlassButton("🧪 TEST", 1f) { 
            starkTotalTest()
        }

        val btnSOS = createGlassButton("🚨 SOS", 1f) { 
            enviarAlertaSOS()
        }

        btnLayout.addView(btnSettings)
        btnLayout.addView(btnTest)
        btnLayout.addView(btnSOS)

        // --- PANEL DE MENSAJES (SIMULADO) ---
        val msgLabel = TextView(this).apply {
            text = "HISTORIAL DE ACTIVIDAD"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 40, 0, 20)
        }

        mainLayout.addView(header)
        mainLayout.addView(termContainer)
        mainLayout.addView(btnLayout)
        mainLayout.addView(msgLabel)
        
        setContentView(mainLayout)

        checkInitialSystems()
        updateUIRunner()
    }

    private fun createGlassButton(txt: String, weight: Float, action: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            layoutParams = LinearLayout.LayoutParams(0, 150, weight).apply { setMargins(10, 10, 10, 10) }
            background = getGlassDrawable(0x33FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { action() }
        }
    }

    private fun getGlassDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 30f
            setStroke(2, 0x66FFFFFF.toInt())
        }
    }

    private fun getCircleDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            terminalView.append("\n[$time] $msg")
            // Scroll al final
            (terminalView.parent as ScrollView).post { (terminalView.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun starkTotalTest() {
        log("INICIANDO_PRUEBA_INTEGRAL_STARK")
        
        // 1. Hablar en el celular (vía Servicio)
        val intent = Intent(this, StarkCaptureService::class.java)
        val mensaje = "Prueba Stark exitosa. Hoy tendrás una buena venta que supera los 10 mil soles. ¡A por ello, jefe!"
        intent.putExtra("TEST_VOICE", mensaje)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            log("AUDIO: ORDEN_ENVIADA_NATIVA")
        } catch (e: Exception) {
            log("ERR_AUDIO: ${e.message}")
        }
        
        // 2. Notificar a la PC (vía ntfy.sh en hilo secundario)
        Thread {
            try {
                val topic = "wingpay_stark_8502345704"
                val url = URL("https://ntfy.sh/$topic")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Title", "TEST STARK v40.8")
                    val json = JSONObject().apply {
                        put("bank", "STARK"); put("name", "VENTA MAESTRA"); put("amt", "10,000.00"); put("stark_log", "TEST_MANUAL_OK")
                    }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    val code = responseCode
                    if (code == 200) {
                        runOnUiThread {
                            syncLED.background = getCircleDrawable(0xFF00E5FF.toInt())
                            log("SYNC_PC: ÉXITO (200 OK)")
                            Handler(Looper.getMainLooper()).postDelayed({ syncLED.background = getCircleDrawable(Color.GRAY) }, 3000)
                        }
                    } else {
                        runOnUiThread { log("ERR_SYNC: CODE_$code") }
                    }
                    disconnect()
                }
            } catch (e: Exception) {
                runOnUiThread { log("ERROR_SYNC: ${e.message}") }
            }
        }.start()
    }

    private fun updateUIRunner() {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                // Actualizar estado del LED de red
                if (isNotificationServiceEnabled()) {
                    statusLED.background = getCircleDrawable(Color.GREEN)
                } else {
                    statusLED.background = getCircleDrawable(Color.RED)
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val cn = ComponentName(this, StarkCaptureService::class.java)
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(cn.flattenToString())
    }

    private fun checkInitialSystems() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("PROTOCOLO OMEGA")
                .setMessage("Señor, JARVIS requiere acceso a las notificaciones.")
                .setPositiveButton("ACTIVAR") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .show()
        }
    }

    private fun enviarAlertaSOS() {
        log("ALERTA: SOS Activado localmente.")
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(1000)
        }
    }
}
