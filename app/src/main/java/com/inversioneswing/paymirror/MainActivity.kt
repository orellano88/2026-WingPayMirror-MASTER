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
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
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
    private var toneGenerator: ToneGenerator? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentClientCode: String = "wingpay_stark_8502345704"

    // --- ESCÁNER OPTIMIZADO v49.0 ---
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            vincularCodigo(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("STARK_PREFS", MODE_PRIVATE)
        currentClientCode = prefs.getString("CLIENT_CODE", currentClientCode)!!

        try { toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (e: Exception) {}

        val starkBackground = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF0F2027.toInt(), 0xFF203A43.toInt(), 0xFF2C5364.toInt())
        )
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            background = starkBackground
        }

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
                text = "IMPORTACIONES WING"; textSize = 20f; setTextColor(0xFF00E5FF.toInt()); setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "v49.0 MULTI-MODE LINK"; textSize = 10f; setTextColor(Color.GRAY)
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
            layoutParams = LinearLayout.LayoutParams(35, 35).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.RED)
        }
        syncLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(35, 35).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.GRAY)
        }

        ledContainer.addView(statusLED); ledContainer.addView(syncLED)
        header.addView(ledContainer)

        val termContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 550).apply { setMargins(0, 30, 0, 30) }
            background = getGlassDrawable(0x66000000.toInt()); setPadding(25, 25, 25, 25)
        }

        terminalView = TextView(this).apply {
            text = "[VÍNCULO]: Canal: $currentClientCode\n[SISTEMA]: Sensores e IA listos."; textSize = 11f
            setTextColor(0xFF00FF41.toInt()); typeface = Typeface.MONOSPACE
        }
        termContainer.addView(ScrollView(this).apply { addView(terminalView) })

        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        btnLayout.addView(createGlassButton("⚙", 1f) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        btnLayout.addView(createGlassButton("📷 QR", 1f) { openQRScanner() })
        btnLayout.addView(createGlassButton("🧪 TEST", 1f) { starkTotalTest() })
        btnLayout.addView(createGlassButton("🚨 SOS", 1f) { enviarAlertaSOS() })

        mainLayout.addView(header); mainLayout.addView(termContainer); mainLayout.addView(btnLayout)
        
        // --- BOTÓN MANUAL (NOVEDAD v49) ---
        val manualBtn = TextView(this).apply {
            text = "VINCULACIÓN MANUAL"; textSize = 10f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 40, 0, 0)
            setOnClickListener { showManualEntryDialog() }
        }
        mainLayout.addView(manualBtn)

        setContentView(mainLayout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        checkInitialSystems()
        startStatusMonitor()
    }

    private fun openQRScanner() {
        log("ESCÁNER: INICIANDO MOTOR ULTRA-FAST...")
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Apunta al QR (Soporta Vertical/Horizontal)")
        options.setCameraId(0)
        options.setBeepEnabled(true)
        options.setOrientationLocked(false) // PERMITE AMBAS ORIENTACIONES
        options.setTimeout(30000)
        barcodeLauncher.launch(options)
    }

    private fun showManualEntryDialog() {
        val input = EditText(this)
        input.hint = "wingpay_client_..."
        AlertDialog.Builder(this)
            .setTitle("VINCULACIÓN MANUAL")
            .setMessage("Ingrese el código de su PC:")
            .setView(input)
            .setPositiveButton("VINCULAR") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isNotEmpty()) vincularCodigo(code)
            }
            .setNegativeButton("CANCELAR", null)
            .show()
    }

    private fun vincularCodigo(code: String) {
        log("VÍNCULO: VINCULANDO A $code")
        currentClientCode = code
        getSharedPreferences("STARK_PREFS", MODE_PRIVATE).edit().putString("CLIENT_CODE", code).apply()
        
        val intent = Intent(this, StarkCaptureService::class.java)
        intent.putExtra("UPDATE_CODE", code)
        startService(intent)
        
        log("SISTEMA: REINICIADO EN CANAL DINÁMICO")
        starkTotalTest() 
    }

    private fun makeTransparent(bit: Bitmap): Bitmap {
        val myBitmap = Bitmap.createBitmap(bit.width, bit.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(bit.width * bit.height)
        bit.getPixels(pixels, 0, bit.width, 0, 0, bit.width, bit.height)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
            if (r > 210 && g > 210 && b > 210) pixels[i] = Color.TRANSPARENT
        }
        myBitmap.setPixels(pixels, 0, bit.width, 0, 0, bit.width, bit.height)
        return myBitmap
    }

    private fun createGlassButton(txt: String, weight: Float, action: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); textSize = 12f
        layoutParams = LinearLayout.LayoutParams(0, 150, weight).apply { setMargins(5, 10, 5, 10) }
        background = getGlassDrawable(0x22FFFFFF.toInt()); setOnClickListener { 
            try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100) } catch (e: Exception) {}
            action() 
        }
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
        log("CMD: TEST_NEURAL_v49")
        sendBroadcast(Intent("com.inversioneswing.STARK_INTERNAL_CMD").apply {
            setPackage(packageName)
            putExtra("VOICE_CMD", "Vínculo exitoso. Canal dinámico v49 operativo.")
        })
        
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/$currentClientCode")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    val json = JSONObject().apply { put("bank", "WING"); put("amt", "v49"); put("stark_log", "SCAN_SUCCESS") }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    if (responseCode == 200) withContext(Dispatchers.Main) {
                        syncLED.background = getCircleDrawable(0xFF00E5FF.toInt())
                        log("SYNC: PC_CONFIRMADA"); delay(3000); syncLED.background = getCircleDrawable(Color.GRAY)
                    }
                    disconnect()
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { log("ERR_SYNC: ${e.message}") } }
        }
    }

    private fun enviarAlertaSOS() {
        log("SOS: ALERTA EN CANAL $currentClientCode")
        sendBroadcast(Intent("com.inversioneswing.STARK_INTERNAL_CMD").apply { setPackage(packageName); putExtra("SOS_CMD", true) })
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/$currentClientCode")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    val json = JSONObject().apply { put("type", "SOS"); put("stark_log", "SIRENA_5S") }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    responseCode; disconnect()
                }
            } catch (e: Exception) {}
        }
    }

    private fun startStatusMonitor() {
        mainScope.launch {
            while (isActive) {
                statusLED.background = if (isNotificationServiceEnabled()) getCircleDrawable(Color.GREEN) else getCircleDrawable(Color.RED)
                delay(5000)
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun checkInitialSystems() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this).setTitle("OMNI-LINK").setMessage("JARVIS requiere el puente neural.").setPositiveButton("CONECTAR") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.show()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            val x = event.values[0]; val y = event.values[1]
            header.translationX = -x * 3; header.translationY = y * 3
            logoIcon.rotationY = x * 5; logoIcon.rotationX = -y * 5
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onResume() { super.onResume(); sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI) }
    override fun onPause() { super.onResume(); sensorManager.unregisterListener(this) }
    override fun onDestroy() { super.onDestroy(); mainScope.cancel() }
}
