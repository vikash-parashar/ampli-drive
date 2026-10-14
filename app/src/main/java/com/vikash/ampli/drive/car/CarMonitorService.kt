package com.vikash.ampli.drive.car

import android.Manifest
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

/**
 * Keeps GPS, the break timer, the odometer, crash detection and the parking spot working while
 * Google Maps or another app is in front. A location foreground service with an ongoing
 * notification; the driver can switch it off in Settings › Car.
 *
 * It must be started while the launcher is visible (Android forbids starting a location
 * service from the background), which MainActivity does in onStart.
 */
class CarMonitorService : Service() {
    private val app get() = application as DriveApp

    /** Head units usually turn the screen off (or shut down) when the ignition goes off. */
    private val ignition = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            app.car.onIgnitionOff()
        }
    }
    private var registered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try {
            startForeground(CarNotifier.ID_MONITOR, CarNotifier.monitorNotification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (_: Exception) {
            // Permission revoked or a background start refused: just don't monitor.
            running = false
            stopSelf()
            return
        }
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SHUTDOWN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(ignition, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(ignition, filter)
            }
            registered = true
        } catch (_: Exception) {
        }
        app.speedTracker.start()
        app.car.startSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        if (registered) {
            try {
                unregisterReceiver(ignition)
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
