package com.vikash.ampli.drive.emergency

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R

/**
 * Android 10+ blocks activity starts from the background, so the crash SOS composer cannot
 * open while another app (Maps, mirroring) is in front. This posts a high-priority alert with
 * a full-screen intent instead; it opens the SOS screen in [MainActivity], which then opens
 * the SMS composer from the foreground. Nothing is ever sent automatically.
 */
object SosNotifier {
    const val CHANNEL_SOS = "crash_sos"
    const val ID_CRASH_SOS = 5201

    /**
     * The alert and its "open the SMS composer" action are only good for this long after the
     * crash: later, the location in the message is stale, so the alert times out and a late tap
     * shows the Help screen instead.
     */
    const val ALERT_TTL_MS = 15 * 60_000L

    /** True while a crash detected at [crashElapsedMs] (elapsedRealtime) may still open the composer. */
    fun isFresh(crashElapsedMs: Long?, nowElapsedMs: Long): Boolean =
        crashElapsedMs != null && nowElapsedMs >= crashElapsedMs && nowElapsedMs - crashElapsedMs < ALERT_TTL_MS

    /** Extra on the SOS intent: open the SMS composer once the activity is in front. */
    const val EXTRA_OPEN_SMS = "com.vikash.ampli.drive.SOS_OPEN_SMS"

    private const val TAG = "SosNotifier"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, context.getString(R.string.sos_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.sos_channel_description)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    /** The intent that brings Car Hub forward on the crash SOS screen (existing crash routing). */
    fun sosIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_VOICE_ACTION, MainActivity.VOICE_CRASH)
        .putExtra(EXTRA_OPEN_SMS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Posts the crash SOS alert. Returns false when notifications are off or blocked. */
    fun postCrashSos(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Crash SOS alert not posted: notification permission missing")
            return false
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, ID_CRASH_SOS, sosIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.sos_notif_text)
        @Suppress("DEPRECATION")
        val builder = Notification.Builder(context, CHANNEL_SOS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.sos_notif_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(ALERT_TTL_MS)
            .addAction(
                Notification.Action.Builder(null as Icon?, context.getString(R.string.sos_notif_action), open).build(),
            )
        // Android 14+: full-screen intents need the user's (or Play's) permission; without it
        // the alert stays a heads-up notification.
        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) nm.canUseFullScreenIntent() else true
        if (fullScreen) builder.setFullScreenIntent(open, true)
        return try {
            nm.notify(ID_CRASH_SOS, builder.build())
            Log.i(TAG, "Crash SOS alert posted (fullScreen=$fullScreen)")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Crash SOS alert blocked", e)
            false
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_CRASH_SOS)
    }
}
