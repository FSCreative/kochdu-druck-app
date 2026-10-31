package at.kochdu.printbridge

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Selbst-Update: prüft das GitHub-Release "latest" (version.json) und installiert eine neuere APK.
 * Android verlangt beim Installieren immer eine Bestätigung am Gerät ("Aktualisieren" tippen).
 */
object Updater {
    private const val BASE = "https://github.com/FSCreative/kochdu-druck-app/releases/download/latest/"
    private const val VERSION_URL = BASE + "version.json"
    private const val APK_URL = BASE + "app-debug.apk"
    private const val TAG = "KochduUpdater"

    private const val INTERVAL_MS = 6 * 60 * 60 * 1000L

    @Volatile private var running = false
    @Volatile private var lastCheck = 0L
    @Volatile private var waitingForPermission = false

    /** Höchstens alle 6 Stunden – außer die App wartet gerade auf die Installations-Erlaubnis. */
    fun check(activity: Activity) {
        val now = System.currentTimeMillis()
        if (running || (!waitingForPermission && now - lastCheck < INTERVAL_MS)) return
        running = true
        lastCheck = now
        val ctx = activity.applicationContext
        Thread {
            try {
                val remote = JSONObject(get(VERSION_URL).bufferedReader().use { it.readText() }).getLong("versionCode")
                val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                val local = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                if (remote <= local) return@Thread
                // Einmalig nötig: "Unbekannte Apps installieren" für Kochdu Druck erlauben
                waitingForPermission = Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()
                if (waitingForPermission) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "Update verfügbar: bitte „Aus dieser Quelle zulassen“ aktivieren und zurück zur App", Toast.LENGTH_LONG).show()
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)))
                    }
                    return@Thread
                }
                install(ctx)
            } catch (e: Exception) {
                Log.w(TAG, "Update-Prüfung fehlgeschlagen", e)
            } finally {
                running = false
            }
        }.start()
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 15000
        readTimeout = 60000
        useCaches = false
        instanceFollowRedirects = true
        inputStream
    }

    private fun install(ctx: Context) {
        val installer = ctx.packageManager.packageInstaller
        val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
        installer.openSession(sessionId).use { session ->
            get(APK_URL).use { input ->
                session.openWrite("kochdu-druck.apk", 0, -1).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, sessionId, Intent(ctx, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}

/** Zeigt den Android-Bestätigungsdialog an, sobald die APK bereitliegt. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Log.w("KochduUpdater", "Installation fehlgeschlagen: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
