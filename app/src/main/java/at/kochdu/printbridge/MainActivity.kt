package at.kochdu.printbridge

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService

class MainActivity : AppCompatActivity() {

    companion object {
        // Welche Seite geladen wird:
        const val START_URL = "https://www.kochdu.at/kitchen"
        // Druckbreite in Punkten: 58mm = 384, 80mm = 576.
        const val PRINT_WIDTH = 576
        // Natuerliche Breite des kochdu-Bons in CSS-Pixeln (~76mm @ 96dpi).
        // Der Bon ist in mm ausgelegt; wir skalieren ihn auf PRINT_WIDTH hoch,
        // damit er die volle Papierbreite fuellt (sonst nur halbe Seite).
        const val DESIGN_WIDTH = 287
    }

    private lateinit var web: WebView
    private var printer: SunmiPrinterService? = null
    private val ui = Handler(Looper.getMainLooper())

    private val printerCallback = object : InnerPrinterCallback() {
        override fun onConnected(service: SunmiPrinterService) {
            printer = service
            try { service.printerInit(null) } catch (_: Exception) {}
        }
        override fun onDisconnected() { printer = null }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verbindung zum eingebauten Sunmi-Drucker aufbauen
        try {
            InnerPrinterManager.getInstance().bindService(applicationContext, printerCallback)
        } catch (e: Exception) {
            Toast.makeText(this, "Drucker-Dienst nicht gefunden: " + e.message, Toast.LENGTH_LONG).show()
        }

        web = WebView(this)
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        // Diese Bruecke ruft kochdu auf: window.SunmiPrinter.printHtml(html)
        web.addJavascriptInterface(Bridge(), "SunmiPrinter")
        setContentView(web)
        web.loadUrl(START_URL)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    inner class Bridge {
        @JavascriptInterface
        fun printHtml(html: String) {
            ui.post { renderAndPrint(html) }
        }
    }

    /** Rendert den HTML-Bon in fester Breite, macht ein Bitmap daraus und druckt es ueber das Sunmi-SDK. */
    private fun renderAndPrint(html: String) {
        val svc = printer
        if (svc == null) {
            Toast.makeText(this, "Drucker noch nicht verbunden", Toast.LENGTH_SHORT).show()
            return
        }
        // Abstandhalter/Leerraum am Ende des Bons entfernen
        val cleanedHtml = html.replace("height:25mm", "height:0")
        // Der Bon ist in mm ausgelegt (~76mm = DESIGN_WIDTH CSS-px). Wir rendern ihn
        // in seiner natuerlichen Breite und skalieren das Bitmap exakt auf die
        // Druckerbreite. So fuellt er die Breite, ohne dass die Hoehe aufgeblaeht
        // wird (kein langer Leerlauf), und bleibt scharf.
        val dens = resources.displayMetrics.density.coerceAtLeast(1f)
        val renderW = (DESIGN_WIDTH * dens).toInt().coerceAtLeast(1)
        val renderer = WebView(this)
        renderer.settings.javaScriptEnabled = false
        renderer.settings.useWideViewPort = false
        renderer.settings.loadWithOverviewMode = false
        renderer.layout(0, 0, renderW, 4000)
        renderer.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // kurz warten bis Layout/Schriften fertig sind, dann Bitmap erzeugen
                ui.postDelayed({
                    try {
                        // Tatsaechliche Inhaltshoehe (nicht die View-Hoehe) verwenden -> tighter Bon
                        val h = (view.contentHeight * dens).toInt().coerceIn(1, 20000)
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(renderW, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                        )
                        view.layout(0, 0, renderW, h)
                        val base = Bitmap.createBitmap(renderW, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(base)
                        canvas.drawColor(Color.WHITE)
                        view.draw(canvas)
                        // exakt auf Druckerbreite skalieren
                        val outH = (h.toLong() * PRINT_WIDTH / renderW).toInt().coerceAtLeast(1)
                        val scaled = if (renderW == PRINT_WIDTH) base
                                     else Bitmap.createScaledBitmap(base, PRINT_WIDTH, outH, true)
                        // reines Schwarz/Weiss fuer scharfen Thermodruck
                        val mono = toMonochrome(scaled)

                        svc.printBitmap(mono, null)
                        svc.lineWrap(3, null)
                        try { svc.cutPaper(null) } catch (_: Exception) { /* Handheld hat keinen Cutter */ }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Druckfehler: " + e.message, Toast.LENGTH_LONG).show()
                    }
                }, 400)
            }
        }
        renderer.loadDataWithBaseURL(null, cleanedHtml, "text/html", "UTF-8", null)
    }

    /** Wandelt ein Bild in reines Schwarz/Weiss (Schwellwert) fuer scharfen Thermodruck. */
    private fun toMonochrome(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 30 + g * 59 + b * 11) / 100
            px[i] = if (lum < 170) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
