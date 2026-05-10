package com.inversioneswing.paymirror

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var terminalView: TextView
    private lateinit var statusLED: View
    private lateinit var syncLED: View
    private lateinit var header: RelativeLayout
    private lateinit var logoIcon: ImageView
    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val starkBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())
        )
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = starkBackground
        }

        // --- CABECERA PARALLAX v44.0 ---
        header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 250)
        }

        logoIcon = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(160, 160).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT); addRule(RelativeLayout.CENTER_VERTICAL)
            }
            try {
                val original = BitmapFactory.decodeResource(resources, R.drawable.stark_logo)
                setImageBitmap(makeTransparent(original))
            } catch (e: Exception) { setImageResource(R.drawable.stark_logo) }
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.RIGHT_OF, logoIcon.id); leftMargin = 30; addRule(RelativeLayout.CENTER_VERTICAL)
            }
            addView(TextView(this@MainActivity).apply {
                text = "IMPORTACIONES WING"; textSize = 22f; setTextColor(0xFF00E5FF.toInt()); setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v44.0 OMNI-LINK EDITION"; textSize = 10f; setTextColor(Color.GRAY)
            })
        }
        
        header.addView(logoIcon); header.addView(titleContainer)

        val ledContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT); addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }

        statusLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.RED)
        }
        
        syncLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.GRAY)
        }

        ledContainer.addView(statusLED); ledContainer.addView(syncLED)
        header.addView(ledContainer)

        // --- TERMINAL NEURAL ---
        val termContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 550).apply { setMargins(0, 30, 0, 30) }
            background = getGlassDrawable(0x66000000.toInt()); setPadding(25, 25, 25, 25)
        }

        terminalView = TextView(this).apply {
            text = "[OMNI-LINK]: Protocolos Nivel 5 Activos\n[OMNI-LINK]: Cola de voz inicializada."; textSize = 11f
            setTextColor(0xFF00FF41.toInt()); typeface = Typeface.MONOSPACE
        }
        termContainer.addView(ScrollView(this).apply { addView(terminalView) })

        // --- BOTONES ---
        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 3f }
        btnLayout.addView(createGlassButton("⚙", 1f) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        btnLayout.addView(createGlassButton("🧪 TEST", 1f) { starkTotalTest() })
        btnLayout.addView(createGlassButton("🚨 SOS", 1f) { enviarAlertaSOS() })

        mainLayout.addView(header); mainLayout.addView(termContainer); mainLayout.addView(btnLayout)
        setContentView(mainLayout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        
        checkInitialSystems()
        startStatusMonitor()
    }

    private fun makeTransparent(bit: Bitmap): Bitmap {
        val myBitmap = Bitmap.createBitmap(bit.width, bit.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bit.width * bit.height)
        bit.getPixels(pixels, 0, bit.width, 0, 0, bit.width, bit.height)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
            if (r > 215 && g > 215 && b > 215) pixels[i] = Color.TRANSPARENT
        }
        myBitmap.setPixels(pixels, 0, bit.width, 0, 0, bit.width, bit.height)
        return myBitmap
    }

    private fun createGlassButton(txt: String, weight: Float, action: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, 160, weight).apply { setMargins(10, 10, 10, 10) }
        background = getGlassDrawable(0x22FFFFFF.toInt()); setOnClickListener { action() }
    }

    private fun getGlassDrawable(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 30f; setStroke(2, 0x44FFFFFF.toInt())
    }

    private fun getCircleDrawable(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            terminalView.append("\n[$time] $msg")
            (terminalView.parent as ScrollView).post { (terminalView.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun starkTotalTest() {
        log("INICIANDO_OMNI_TEST_v44")
        // Enviar vía BROADCAST NEURAL v44
        sendBroadcast(Intent("com.inversioneswing.STARK_INTERNAL_CMD").apply {
            putExtra("VOICE_CMD", "Omni Link exitoso. Hoy tendrás una gran venta superior a los 10 mil soles. ¡A por ello!")
        })
        
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/wingpay_stark_8502345704")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    setRequestProperty("Title", "TEST OMNI-LINK v44")
                    setRequestProperty("Priority", "4")
                    val json = JSONObject().apply { put("bank", "WING"); put("amt", "10k"); put("stark_log", "v44_OMNI_OK") }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    if (responseCode == 200) withContext(Dispatchers.Main) {
                        syncLED.background = getCircleDrawable(0xFF00E5FF.toInt())
                        log("OMNI_SYNC_PC: ÉXITO"); delay(3000); syncLED.background = getCircleDrawable(Color.GRAY)
                    }
                    disconnect()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { log("ERR_SYNC: ${e.message}") } }
        }
    }

    private fun enviarAlertaSOS() {
        log("SOS: Activando Sirena Prioridad 5 en PC...")
        sendBroadcast(Intent("com.inversioneswing.STARK_INTERNAL_CMD").apply { putExtra("SOS_CMD", true) })
    }

    private fun startStatusMonitor() {
        mainScope.launch {
            while (isActive) {
                statusLED.background = if (isNotificationServiceEnabled()) getCircleDrawable(Color.GREEN) else getCircleDrawable(Color.RED)
                // Efecto latido
                statusLED.alpha = 0.5f; delay(200); statusLED.alpha = 1.0f; delay(4800)
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun checkInitialSystems() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this).setTitle("OMNI-LINK").setMessage("Señor, JARVIS requiere el puente neural.").setPositiveButton("CONECTAR") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            val x = event.values[0]; val y = event.values[1]
            header.translationX = -x * 3; header.translationY = y * 3
            logoIcon.rotationY = x * 5; logoIcon.rotationX = -y * 5
            
            // NOVEDAD v44: El color del logo reacciona sutilmente al movimiento
            val colorFilter = LightingColorFilter(0xFFFFFF, (Math.abs(x) * 20).toInt() shl 16)
            logoIcon.colorFilter = colorFilter
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onResume() { super.onResume(); sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI) }
    override fun onPause() { super.onResume(); sensorManager.unregisterListener(this) }
    override fun onDestroy() { super.onDestroy(); mainScope.cancel() }
}
