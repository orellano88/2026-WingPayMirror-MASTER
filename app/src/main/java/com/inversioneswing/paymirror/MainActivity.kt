package com.inversioneswing.paymirror

import android.content.*
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        // --- CABECERA IMPORTACIONES WING ---
        val header = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 250)
        }

        val logoIcon = ImageView(this).apply {
            id = View.generateViewId()
            layoutParams = RelativeLayout.LayoutParams(150, 150).apply {
                addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            // --- PROCESAMIENTO DE LOGO TRANSPARENTE EN TIEMPO REAL ---
            try {
                val original = BitmapFactory.decodeResource(resources, R.drawable.stark_logo)
                val processed = makeTransparent(original, Color.WHITE)
                setImageBitmap(processed)
            } catch (e: Exception) {
                setImageResource(R.drawable.stark_logo)
            }
        }

        val titleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.RIGHT_OF, logoIcon.id)
                leftMargin = 30
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            val title = TextView(this@MainActivity).apply {
                text = "IMPORTACIONES WING"
                textSize = 24f
                setTextColor(0xFF00E5FF.toInt()) 
                setTypeface(null, Typeface.BOLD)
            }
            val subTitle = TextView(this@MainActivity).apply {
                text = "v42.0 PERFECCIÓN TOTAL"
                textSize = 10f
                setTextColor(Color.GRAY)
            }
            addView(title)
            addView(subTitle)
        }
        
        header.addView(logoIcon)
        header.addView(titleContainer)

        // Indicadores LED
        val ledContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            lp.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = lp
        }

        statusLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.RED)
        }
        
        syncLED = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(40, 40).apply { setMargins(10, 0, 10, 0) }
            background = getCircleDrawable(Color.GRAY)
        }

        ledContainer.addView(statusLED)
        ledContainer.addView(syncLED)
        header.addView(ledContainer)

        // --- STARK TERMINAL (PANEL DE VIDRIO) ---
        val termContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 600).apply { setMargins(0, 40, 0, 40) }
            background = getGlassDrawable(0x66000000.toInt())
            setPadding(30, 30, 30, 30)
        }

        terminalView = TextView(this).apply {
            text = "[SISTEMA]: Importaciones Wing v42.0 Online\n[IA]: JARVIS listo para la acción."
            textSize = 12f
            setTextColor(0xFF00FF41.toInt()) 
            typeface = Typeface.MONOSPACE
        }
        
        val scroll = ScrollView(this).apply { addView(terminalView) }
        termContainer.addView(scroll)

        // --- BOTONES GLASS ---
        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            weightSum = 3f
        }

        btnLayout.addView(createGlassButton("⚙", 1f) { 
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        
        btnLayout.addView(createGlassButton("🧪 TEST", 1f) { 
            starkTotalTest()
        })

        btnLayout.addView(createGlassButton("🚨 SOS", 1f) { 
            enviarAlertaSOS()
        })

        mainLayout.addView(header)
        mainLayout.addView(termContainer)
        mainLayout.addView(btnLayout)
        
        setContentView(mainLayout)
        checkInitialSystems()
        updateUIRunner()
    }

    // --- FUNCIÓN PARA QUITAR FONDO BLANCO AL LOGO ---
    private fun makeTransparent(bit: Bitmap, transparentColor: Int): Bitmap {
        val width = bit.width
        val height = bit.height
        val myBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val allpixels = IntArray(width * height)
        bit.getPixels(allpixels, 0, width, 0, 0, width, height)
        for (i in 0 until width * height) {
            val r = Color.red(allpixels[i])
            val g = Color.green(allpixels[i])
            val b = Color.blue(allpixels[i])
            // Si el pixel es casi blanco, lo hacemos transparente
            if (r > 220 && g > 220 && b > 220) {
                allpixels[i] = Color.TRANSPARENT
            }
        }
        myBitmap.setPixels(allpixels, 0, width, 0, 0, width, height)
        return myBitmap
    }

    private fun createGlassButton(txt: String, weight: Float, action: () -> Unit): Button {
        return Button(this).apply {
            text = txt
            layoutParams = LinearLayout.LayoutParams(0, 160, weight).apply { setMargins(10, 10, 10, 10) }
            background = getGlassDrawable(0x22FFFFFF.toInt())
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setOnClickListener { action() }
        }
    }

    private fun getGlassDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 35f
            setStroke(2, 0x55FFFFFF.toInt())
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
            (terminalView.parent as ScrollView).post { (terminalView.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun starkTotalTest() {
        log("INICIANDO_TEST_V42")
        // Enviar Intent de voz
        val intent = Intent(this, StarkCaptureService::class.java).apply {
            putExtra("TEST_VOICE", "Prueba exitosa. Hoy tendrás una buena venta que supera los 10 mil soles. ¡A por ello, jefe!")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        
        // Enviar Sincronización a PC
        Thread {
            try {
                val topic = "wingpay_stark_8502345704"
                val url = URL("https://ntfy.sh/$topic")
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true
                    val json = JSONObject().apply {
                        put("bank", "WING"); put("name", "VENTA_RECORD"); put("amt", "10,000.00"); put("stark_log", "TEST_V42_OK")
                    }
                    OutputStreamWriter(outputStream).use { it.write(json.toString()) }
                    if (responseCode == 200) {
                        runOnUiThread {
                            syncLED.background = getCircleDrawable(0xFF00E5FF.toInt())
                            log("SYNC_PC: ÉXITO")
                            Handler(Looper.getMainLooper()).postDelayed({ syncLED.background = getCircleDrawable(Color.GRAY) }, 3000)
                        }
                    }
                    disconnect()
                }
            } catch (e: Exception) { runOnUiThread { log("ERR_SYNC: ${e.message}") } }
        }.start()
    }

    private fun enviarAlertaSOS() {
        log("SOS: Activando sirena en PC...")
        val intent = Intent(this, StarkCaptureService::class.java).apply { putExtra("TRIGGER_SOS", true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(2000, 255)) else vibrator.vibrate(2000)
    }

    private fun updateUIRunner() {
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                statusLED.background = if (isNotificationServiceEnabled()) getCircleDrawable(Color.GREEN) else getCircleDrawable(Color.RED)
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
            AlertDialog.Builder(this).setTitle("IMPORTACIONES WING").setMessage("Señor, JARVIS requiere acceso a las notificaciones.").setPositiveButton("ACTIVAR") { _, _ -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.show()
        }
    }
}
