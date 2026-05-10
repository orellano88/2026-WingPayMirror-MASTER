package com.inversioneswing.paymirror

import android.animation.ArgbEvaluator
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
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
    private lateinit var centralLogo: ImageView
    private lateinit var mainLayout: LinearLayout
    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var toneGenerator: ToneGenerator? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentClientCode: String = "wingpay_stark_8502345704"

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) { vincularCodigo(result.contents) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("STARK_PREFS", MODE_PRIVATE)
        currentClientCode = prefs.getString("CLIENT_CODE", currentClientCode)!!

        try { toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (e: Exception) {}

        // --- UI NEURAL LIQUID v57.0 ---
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // Fondo Dinámico Animado
        animateNeuralBackground()

        header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 260)
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
                text = "v57.0 NEURAL LIQUID"; textSize = 10f; setTextColor(Color.WHITE); alpha = 0.6f
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

        // STARK TERMINAL GLASS
        val termContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 450).apply { setMargins(0, 30, 0, 30) }
            background = getGlassDrawable(0x77000000.toInt()); setPadding(25, 25, 25, 25)
        }

        terminalView = TextView(this).apply {
            text = "[LIQUID]: Motor Gráfico v57 Online\n[SYNC]: Tópico: $currentClientCode"; textSize = 11f
            setTextColor(0xFF00FF41.toInt()); typeface = Typeface.MONOSPACE
        }
        termContainer.addView(ScrollView(this).apply { addView(terminalView) })

        val visualContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            gravity = Gravity.CENTER
        }

        centralLogo = ImageView(this).apply {
            val lp = FrameLayout.LayoutParams(700, 700).apply { gravity = Gravity.CENTER }
            layoutParams = lp
            setImageResource(R.drawable.stark_logo)
            alpha = 0.4f
        }
        ObjectAnimator.ofFloat(centralLogo, "alpha", 0.2f, 0.6f).apply {
            duration = 2000; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        visualContainer.addView(centralLogo)

        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 4f }
        btnLayout.addView(createGlassButton("⚙", 1f) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        btnLayout.addView(createGlassButton("📷 QR", 1f) { openQRScanner() })
        btnLayout.addView(createGlassButton("🧪 TEST", 1f) { starkTotalTest() })
        btnLayout.addView(createGlassButton("🚨 SOS", 1f) { enviarAlertaSOS() })

        mainLayout.addView(header); mainLayout.addView(termContainer); mainLayout.addView(visualContainer); mainLayout.addView(btnLayout)
        
        val manualBtn = TextView(this).apply {
            text = "VINCULACIÓN MANUAL"; textSize = 10f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20); setOnClickListener { showManualEntryDialog() }
        }
        mainLayout.addView(manualBtn)

        setContentView(mainLayout)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        checkInitialSystems()
        startStatusMonitor()
    }

    // NOVEDAD v57: Fondo Liquido que fluye
    private fun animateNeuralBackground() {
        val color1 = 0xFF0F2027.toInt()
        val color2 = 0xFF2C5364.toInt()
        val color3 = 0xFF203A43.toInt()

        val gradient = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color1, color2, color3))
        mainLayout.background = gradient

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val evaluator = ArgbEvaluator()
                val midColor = evaluator.evaluate(fraction, color2, 0xFF1e3c72.toInt()) as Int
                gradient.colors = intArrayOf(color1, midColor, color3)
            }
        }
        animator.start()
    }

    private fun openQRScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Escanea tu Estación PC")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        barcodeLauncher.launch(options)
    }

    private fun showManualEntryDialog() {
        val input = EditText(this).apply { hint = "wingpay_client_..." }
        AlertDialog.Builder(this).setTitle("VINCULACIÓN MANUAL").setView(input).setPositiveButton("VINCULAR") { _, _ ->
            val code = input.text.toString().trim()
            if (code.isNotEmpty()) vincularCodigo(code)
        }.setNegativeButton("CANCELAR", null).show()
    }

    private fun vincularCodigo(code: String) {
        log("SISTEMA: SINCRONIZANDO $code")
        currentClientCode = code
        getSharedPreferences("STARK_PREFS", MODE_PRIVATE).edit().putString("CLIENT_CODE", code).apply()
        val intent = Intent(this, StarkCaptureService::class.java).apply { putExtra("UPDATE_CODE", code) }
        startService(intent)
        starkTotalTest() 
    }

    private fun createGlassButton(txt: String, weight: Float, action: () -> Unit) = Button(this).apply {
        text = txt; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); textSize = 12f
        val params = LinearLayout.LayoutParams(0, 160, weight)
        params.setMargins(6, 10, 6, 10)
        layoutParams = params
        background = getGlassDrawable(0x22FFFFFF.toInt()); setOnClickListener { 
            try { toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 100) } catch (e: Exception) {}
            action() 
        }
    }

    private fun getGlassDrawable(color: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = 35f; setStroke(3, 0x66FFFFFF.toInt())
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
        log("CMD: TEST_LIQUID_v57")
        StarkCaptureService.sendAudioCommand("Enlace v57 perfeccionado. Hoy recibirás un ingreso superior a los 10 mil soles. ¡Adelante, jefe!")
        
        mainScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://ntfy.sh/$currentClientCode")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.doOutput = true
                val json = JSONObject().apply { 
                    put("sender", "PHONE"); put("bank", "WING"); put("amt", "v57"); put("stark_log", "LIQUID_OK") 
                }
                OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
                if (conn.responseCode == 200) withContext(Dispatchers.Main) {
                    syncLED.background = getCircleDrawable(0xFF00E5FF.toInt())
                    log("SYNC_PC: ÉXITO"); delay(3000); syncLED.background = getCircleDrawable(Color.GRAY)
                }
                conn.disconnect()
            } catch (e: Exception) { withContext(Dispatchers.Main) { log("ERR_SYNC: ${e.message}") } }
        }
    }

    private fun enviarAlertaSOS() {
        log("SOS: LANZANDO ALERTA MAESTRA...")
        StarkCaptureService.sendAudioCommand("¡Protocolo de emergencia activado!")
        StarkCaptureService.triggerRemoteSOS()
    }

    private fun startStatusMonitor() {
        mainScope.launch {
            while (isActive) {
                statusLED.background = if (isNotificationServiceEnabled()) getCircleDrawable(Color.GREEN) else getCircleDrawable(Color.RED)
                statusLED.alpha = 0.4f; delay(150); statusLED.alpha = 1.0f; delay(4850)
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun checkInitialSystems() {
        if (!isNotificationServiceEnabled()) {
            AlertDialog.Builder(this).setTitle("ENLACE NEURAL").setMessage("JARVIS requiere el puente neural.").setPositiveButton("CONECTAR") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.show()
        }
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

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            val x = event.values[0]; val y = event.values[1]
            header.translationX = -x * 2; header.translationY = y * 2
            logoIcon.rotationY = x * 4; logoIcon.rotationX = -y * 4
            centralLogo.translationX = x * 15; centralLogo.translationY = -y * 15
            centralLogo.rotationY = x * 20; centralLogo.rotationX = -y * 20
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onResume() { super.onResume(); sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_UI) }
    override fun onPause() { super.onResume(); sensorManager.unregisterListener(this) }
    override fun onDestroy() { super.onDestroy(); mainScope.cancel() }
}
