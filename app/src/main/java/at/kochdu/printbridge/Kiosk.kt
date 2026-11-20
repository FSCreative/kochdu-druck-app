package at.kochdu.printbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings

// Kiosk-Modus (pro Gerät freischalten): kochdu geht nach dem Neustart und nach dem Entsperren / Bildschirm-An
// automatisch auf. Man kann die App trotzdem schließen und z. B. in die Einstellungen wechseln – sie kommt erst
// beim nächsten Entsperren wieder nach vorne.
// Aktiv nur auf freigeschalteten Geräten: die Druck-App ist dort der Startbildschirm (Home). Nur so darf Android sie
// aus dem Hintergrund nach vorne holen (Sunmi setzt "Über anderen Apps anzeigen" bei jedem Neustart zurück).
// Freischalten per ADB (die App schaltet ihren Home-Eintrag selbst um – die Shell darf das auf Sunmi nicht):
//   adb shell am start -n at.kochdu.printbridge/.MainActivity --es kiosk on
//   adb shell cmd package set-home-activity at.kochdu.printbridge/.KioskHome
// Ausschalten:  adb shell am start -n at.kochdu.printbridge/.MainActivity --es kiosk off
object Kiosk {
    // Extra "kiosk" = on/off aus dem Start-Intent übernehmen
    fun applyIntent(ctx: Context, intent: Intent?) {
        val v = intent?.getStringExtra("kiosk") ?: return
        val on = v == "on"
        ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, "at.kochdu.printbridge.KioskHome"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
        if (on) start(ctx) else ctx.stopService(Intent(ctx, KioskService::class.java))
    }

    fun enabled(ctx: Context): Boolean {
        val homeAlias = ComponentName(ctx, "at.kochdu.printbridge.KioskHome")
        val aliasOn = ctx.packageManager.getComponentEnabledSetting(homeAlias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        return aliasOn || (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(ctx))
    }

    fun start(ctx: Context) {
        if (!enabled(ctx)) return
        val i = Intent(ctx, KioskService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        } catch (_: Exception) {}
    }

    private var lastLaunch = 0L
    fun bringToFront(ctx: Context) {
        if (!enabled(ctx)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 3000) return
        lastLaunch = now
        try {
            ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (_: Exception) {}
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!Kiosk.enabled(ctx)) return
        Kiosk.start(ctx)
        Kiosk.bringToFront(ctx)
    }
}

class KioskService : Service() {
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) { Kiosk.bringToFront(ctx) }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm?.createNotificationChannel(NotificationChannel("kiosk", "Kiosk-Modus", NotificationManager.IMPORTANCE_MIN))
        val n: Notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "kiosk") else Notification.Builder(this))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("kochdu Kiosk-Modus aktiv")
            .setContentText("kochdu öffnet sich nach Neustart und Entsperren automatisch")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        val f = IntentFilter().apply { addAction(Intent.ACTION_USER_PRESENT); addAction(Intent.ACTION_SCREEN_ON) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, f, Context.RECEIVER_EXPORTED) else registerReceiver(screen, f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Kiosk.enabled(this)) { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    override fun onDestroy() { try { unregisterReceiver(screen) } catch (_: Exception) {}; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
