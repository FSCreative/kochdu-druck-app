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
        // Druckbreite in Punkten: 58mm = 384, 80mm = 576. V-Serie meist 58mm.
        const val PRINT_WIDTH = 576
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
        val renderer = WebView(this)
        renderer.settings.javaScriptEnabled = false
        renderer.setInitialScale(100)
        renderer.layout(0, 0, PRINT_WIDTH, 4000)
        renderer.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                // kurz warten bis Layout/Schriften fertig sind, dann Bitmap erzeugen
                ui.postDelayed({
                    try {
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(PRINT_WIDTH, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        val h = view.measuredHeight.coerceAtLeast(1)
                        view.layout(0, 0, PRINT_WIDTH, h)
                        val bmp = Bitmap.createBitmap(PRINT_WIDTH, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        view.draw(canvas)

                        svc.printBitmap(bmp, null)
                        svc.lineWrap(3, null)
                        try { svc.cutPaper(null) } catch (_: Exception) { /* Handheld hat keinen Cutter */ }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Druckfehler: " + e.message, Toast.LENGTH_LONG).show()
                    }
                }, 350)
            }
        }
        renderer.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
