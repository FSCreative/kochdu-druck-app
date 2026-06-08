package at.kochdu.printbridge

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
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
        // Alt: HTML wird in der App gerendert (Fallback fuer aeltere Seiten).
        @JavascriptInterface
        fun printHtml(html: String) {
            ui.post { renderAndPrint(html) }
        }

        // Neu (duenne Huelle): Die Webseite liefert ein fertiges PNG (Base64),
        // die App druckt es nur noch. So koennen alle Bon-Aenderungen web-seitig
        // gemacht werden, ohne die App neu zu installieren.
        @JavascriptInterface
        fun printBase64(dataUrlOrB64: String) {
            ui.post { printBase64Image(dataUrlOrB64) }
        }
    }

    private fun printBase64Image(input: String) {
        val svc = printer
        if (svc == null) {
            Toast.makeText(this, "Drucker noch nicht verbunden", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val b64 = if (input.contains(",")) input.substringAfter(",") else input
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp == null) {
                Toast.makeText(this, "Druckbild ungueltig", Toast.LENGTH_SHORT).show()
                return
            }
            svc.printBitmap(bmp, null)
            svc.lineWrap(2, null)
            try { svc.cutPaper(null) } catch (_: Exception) { /* Handheld hat keinen Cutter */ }
        } catch (e: Exception) {
            Toast.makeText(this, "Druckfehler: " + e.message, Toast.LENGTH_LONG).show()
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
                        // Grosszuegig hoch rendern; ueberschuessiger Weissraum wird danach
                        // automatisch weggeschnitten -> Bon ist nur so lang wie der Inhalt.
                        val h = ((view.contentHeight * dens).toInt() + (200 * dens).toInt()).coerceIn(1, 30000)
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(renderW, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
                        )
                        view.layout(0, 0, renderW, h)
                        val base = Bitmap.createBitmap(renderW, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(base)
                        canvas.drawColor(Color.WHITE)
                        view.draw(canvas)
                        // Leeren Rand oben/unten wegschneiden
                        val trimmed = trimVertical(base)
                        // exakt auf Druckerbreite skalieren
                        val outH = (trimmed.height.toLong() * PRINT_WIDTH / trimmed.width).toInt().coerceAtLeast(1)
                        val scaled = if (trimmed.width == PRINT_WIDTH) trimmed
                                     else Bitmap.createScaledBitmap(trimmed, PRINT_WIDTH, outH, true)
                        // reines Schwarz/Weiss fuer scharfen Thermodruck
                        val mono = toMonochrome(scaled)

                        svc.printBitmap(mono, null)
                        svc.lineWrap(2, null)
                        try { svc.cutPaper(null) } catch (_: Exception) { /* Handheld hat keinen Cutter */ }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Druckfehler: " + e.message, Toast.LENGTH_LONG).show()
                    }
                }, 450)
            }
        }
        renderer.loadDataWithBaseURL(null, cleanedHtml, "text/html", "UTF-8", null)
    }

    /** Schneidet leere (weisse) Zeilen oben und unten weg, damit der Bon nicht zu lang ist. */
    private fun trimVertical(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val row = IntArray(w)
        fun hasInk(y: Int): Boolean {
            src.getPixels(row, 0, w, 0, y, w, 1)
            for (p in row) {
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r < 220 || g < 220 || b < 220) return true
            }
            return false
        }
        var top = 0
        while (top < h && !hasInk(top)) top++
        var bottom = h - 1
        while (bottom > top && !hasInk(bottom)) bottom--
        if (top >= bottom) return src
        val pad = (10 * resources.displayMetrics.density).toInt()
        val y0 = (top - pad).coerceAtLeast(0)
        val y1 = (bottom + pad).coerceAtMost(h - 1)
        return Bitmap.createBitmap(src, 0, y0, w, y1 - y0 + 1)
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
