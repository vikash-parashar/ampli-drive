package com.vikash.ampli.drive.dashcam

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarNotifier

/**
 * Keeps the dashcam recording while Maps or music is in front: a camera foreground service with a
 * quiet "Dashcam recording" notification. Started only from the visible launcher or the dashcam
 * switch (Android forbids starting a camera service from the background).
 */
class DashcamService : Service() {
    private val app get() = application as DriveApp
    private var recorder: DashcamRecorder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        try {
            ServiceCompat.startForeground(this, ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } catch (_: Exception) {
            running = false
            try {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return
        }
        app.dashcam.setError(null)
        val r = DashcamRecorder(app, app.dashcam)
        recorder = r
        app.dashcam.recorder = r
        r.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        running = false
        app.dashcam.recorder = null
        recorder?.stop()
        recorder = null
        super.onDestroy()
    }

    companion object {
        private const val ID = 5120
        private const val CHANNEL = "dashcam"

        @Volatile
        var running = false
            private set

        fun start(context: Context): Boolean {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
            return try {
                context.startForegroundService(Intent(context, DashcamService::class.java))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DashcamService::class.java))
        }

        private fun notification(context: Context): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.dashcam_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentTitle(context.getString(R.string.dashcam_notification))
                .setContentIntent(CarNotifier.openApp(context))
                .setOngoing(true)
                .build()
        }
    }
}
