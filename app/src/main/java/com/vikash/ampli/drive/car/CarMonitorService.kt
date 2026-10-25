package com.vikash.ampli.drive.car

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.overlay.BubbleLogic
import com.vikash.ampli.drive.overlay.QuickStrip
import com.vikash.ampli.drive.overlay.QuickStripGate
import com.vikash.ampli.drive.overlay.QuickStripLogic
import com.vikash.ampli.drive.overlay.SpeedBubble
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.core.app.ServiceCompat

/**
 * Background alerts: keeps GPS, speed-camera alerts, the break timer, the odometer, crash
 * detection and the parking spot working while Google Maps or another app is in front. A
 * location foreground service with an ongoing notification; the driver can switch it off in
 * Settings › Car. It also owns the optional floating speed bubble.
 *
 * It must be started while the launcher is visible (Android forbids starting a location
 * service from the background), which MainActivity does in onStart, and only after the driver
 * has agreed to the background-location disclosure.
 */
class CarMonitorService : Service() {
    private val app get() = application as DriveApp
    private val scope = MainScope()
    private var bubble: SpeedBubble? = null
    private var quick: QuickStrip? = null

    /** Head units usually turn the screen off (or shut down) when the ignition goes off. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Back after a long screen-off or a deep sleep: the car was started again (greeting).
                Intent.ACTION_SCREEN_ON -> CarStart.onScreenOn(app)
                Intent.ACTION_SCREEN_OFF -> {
                    CarStart.onScreenOff()
                    app.car.onIgnitionOff()
                }
                else -> app.car.onIgnitionOff()
            }
        }
    }

    /** The phone's Bluetooth link, while the launcher is hidden too (auto-play, Phone Link). */
    private val btLink = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = com.vikash.ampli.drive.bluetooth.PhoneBluetooth.deviceFrom(intent)
            app.phoneBt.onAcl(device, intent.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
        }
    }

    /** Vendor ACC broadcasts (FYT, Microntek, MediaTek quick boot…), see IgnitionActions. */
    private val vendorIgnition = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = IgnitionActions.classify(intent.action) { key -> intent.getStringExtra(key) } ?: return
            app.car.onIgnition(event)
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try {
            ServiceCompat.startForeground(
                this, CarNotifier.ID_MONITOR, CarNotifier.monitorNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (_: Exception) {
            // Permission revoked or a background start refused: just don't monitor.
            // (startForeground is always the first call, so there is no not-started-in-time crash.)
            running = false
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return
        }
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SHUTDOWN)
            }
            val acl = IntentFilter().apply {
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val vendor = IntentFilter().apply { IgnitionActions.actions.forEach { addAction(it) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
                // Sent by the head unit's own system apps, so it must be exported.
                registerReceiver(vendorIgnition, vendor, RECEIVER_EXPORTED)
                registerReceiver(btLink, acl, RECEIVER_EXPORTED)
            } else {
                registerReceiver(screenOff, filter)
                registerReceiver(vendorIgnition, vendor)
                registerReceiver(btLink, acl)
            }
            registered = true
        } catch (_: Exception) {
        }
        app.speedTracker.start()
        app.car.startSensors()
        app.navPrompter.start()
        app.desi.start()

        bubble = SpeedBubble(app)
        // The bubble shows only while another app is in front.
        scope.launch {
            combine(app.prefs.bubble, app.car.foregroundState) { on, fg -> on && !fg }
                .distinctUntilChanged()
                .collect { show -> if (show) bubble?.show() else bubble?.hide() }
        }
        // "Swipe down from the top" over other apps: the thin strip, hidden on Car Hub's Home
        // (it has its own pull-down), during a crash countdown / SOS and while the panel is open.
        val strip = QuickStrip(app).also { quick = it }
        scope.launch {
            combine(
                app.prefs.quickStrip,
                app.car.foregroundState,
                app.car.crashAt,
                strip.panelOpen,
                QuickStripGate.hubScreenBlocks,
            ) { on, fg, crash, panel, blocks ->
                QuickStripLogic.visible(on, strip.canShow(), panel, crash != null, fg, blocks)
            }
                .distinctUntilChanged()
                .collect { show -> if (show) strip.showStrip() else strip.hideStrip() }
        }
        // A crash closes an open panel so the countdown is seen.
        scope.launch {
            app.car.crashAt.collect { if (it != null) strip.closePanel() }
        }
        // Free drivers have no spoken camera alerts: the notification names the next camera instead.
        scope.launch {
            combine(app.cameraAlerts.current, app.car.foregroundState) { cam, fg ->
                if (fg || cam == null) null else (cam.limitKmh to (cam.distanceMeters / 100) * 100)
            }.distinctUntilChanged().collect { cam ->
                val nm = getSystemService(NotificationManager::class.java) ?: return@collect
                val text = cam?.let { (limit, m) ->
                    if (limit != null) {
                        getString(com.vikash.ampli.drive.R.string.monitor_camera_limit, BubbleLogic.distanceText(m), limit)
                    } else {
                        getString(com.vikash.ampli.drive.R.string.monitor_camera, BubbleLogic.distanceText(m))
                    }
                }
                try {
                    nm.notify(CarNotifier.ID_MONITOR, CarNotifier.monitorNotification(this@CarMonitorService, text))
                } catch (_: SecurityException) {
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        scope.cancel()
        bubble?.hide()
        bubble = null
        quick?.release()
        quick = null
        if (registered) {
            try {
                unregisterReceiver(screenOff)
                unregisterReceiver(vendorIgnition)
                unregisterReceiver(btLink)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        var running = false
            private set

        fun start(context: Context): Boolean {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false
            val app = context.applicationContext as DriveApp
            // Play's prominent disclosure comes first (DisclosureDialog).
            if (!app.prefs.bgDisclosureAccepted.value) return false
            return try {
                context.startForegroundService(Intent(context, CarMonitorService::class.java))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CarMonitorService::class.java))
        }
    }
}
