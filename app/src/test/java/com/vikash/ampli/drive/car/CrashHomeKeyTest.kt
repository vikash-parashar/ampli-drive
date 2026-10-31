package com.vikash.ampli.drive.car

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.emergency.SosNotifier
import com.vikash.ampli.drive.ui.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import java.time.Duration

/**
 * Pressing HOME (or a vendor ignition event sending a HOME intent) during a crash countdown
 * must not hide the countdown, and the SOS message must still open when it runs out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w1024dp-h600dp-land-mdpi")
class CrashHomeKeyTest {

    private var app: DriveApp? = null

    @After
    fun tearDown() {
        app?.car?.clearCrash()
        MainActivity.inFront = false
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test
    fun homeLeadsToTheCountdownOnlyWhileACrashRuns() {
        assertEquals(Screen.Crash, MainActivity.screenAfterHome(crashRunning = true))
        assertEquals(Screen.Home, MainActivity.screenAfterHome(crashRunning = false))
    }

    @Test
    fun countdownOpensTheSosMessageWhileCarHubIsInFront() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as DriveApp
        this.app = app
        MainActivity.inFront = true

        app.car.triggerCrash()
        assertNotNull(app.car.crashAt.value)
        // HOME arriving now (key, vendor ignition event) keeps the countdown.
        assertEquals(Screen.Crash, MainActivity.screenAfterHome(app.car.crashAt.value != null))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(CarMonitor.CRASH_COUNTDOWN_S + 2L))

        assertNull(app.car.crashAt.value)
        assertNotNull(app.car.crashSos.value)
        val opened = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        assertTrue("SMS composer not opened: ${opened.map { it.action }}", opened.any { it.action == Intent.ACTION_SENDTO })

        // A later crash starts a fresh countdown.
        app.car.triggerCrash()
        assertNotNull(app.car.crashAt.value)
        assertNull(app.car.crashSos.value)
    }

    /** Android 10+ blocks the composer from the background: a full-screen alert opens the SOS screen. */
    @Test
    fun countdownInTheBackgroundPostsTheSosAlert() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as DriveApp
        this.app = app
        MainActivity.inFront = false

        app.car.triggerCrash()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(CarMonitor.CRASH_COUNTDOWN_S + 2L))

        assertNotNull(app.car.crashSos.value)
        val opened = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        assertTrue("Composer started from the background: $opened", opened.none { it.action == Intent.ACTION_SENDTO })
        val nm = app.getSystemService(NotificationManager::class.java)
        val alert = shadowOf(nm).getNotification(SosNotifier.ID_CRASH_SOS)
        assertNotNull("No crash SOS alert", alert)
        assertEquals(SosNotifier.CHANNEL_SOS, alert.channelId)
        assertNotNull("No full-screen intent", alert.fullScreenIntent)
        val target = shadowOf(alert.fullScreenIntent).savedIntent
        assertEquals(MainActivity.VOICE_CRASH, target.getStringExtra(MainActivity.EXTRA_VOICE_ACTION))
        assertTrue(target.getBooleanExtra(SosNotifier.EXTRA_OPEN_SMS, false))
        // A stale alert must not linger: it times out with the composer window.
        assertEquals(SosNotifier.ALERT_TTL_MS, alert.timeoutAfter)
        assertTrue(app.car.crashSosFresh())
        val crashAt = app.car.lastCrashElapsed!!
        assertTrue(app.car.crashSosFresh(now = crashAt + SosNotifier.ALERT_TTL_MS - 1))
        assertFalse("Composer may not open 15 min after the crash", app.car.crashSosFresh(now = crashAt + SosNotifier.ALERT_TTL_MS))
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            nm.getNotificationChannel(SosNotifier.CHANNEL_SOS).importance,
        )

        // A new crash clears the old alert.
        app.car.triggerCrash()
        assertNull(shadowOf(nm).getNotification(SosNotifier.ID_CRASH_SOS))
    }

    /**
     * Android 15+: "Display over other apps" no longer lets a hidden app start an activity unless
     * its overlay is showing, and the blocked start fails silently. With Car Hub not in front the
     * alert must be posted even though the permission is granted.
     */
    @Test
    fun notInFrontWithOverlayPermissionStillPostsTheSosAlert() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as DriveApp
        this.app = app
        MainActivity.inFront = false
        ShadowSettings.setCanDrawOverlays(true)

        app.car.triggerCrash()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(CarMonitor.CRASH_COUNTDOWN_S + 2L))

        assertNotNull(app.car.crashSos.value)
        val nm = app.getSystemService(NotificationManager::class.java)
        val alert = shadowOf(nm).getNotification(SosNotifier.ID_CRASH_SOS)
        assertNotNull("No crash SOS alert while Car Hub is not in front", alert)
        assertNotNull("No full-screen intent", alert.fullScreenIntent)
    }

    @Test
    fun sosAlertIsFreshForFifteenMinutesOnly() {
        assertFalse(SosNotifier.isFresh(null, 1_000L))
        assertTrue(SosNotifier.isFresh(1_000L, 1_000L))
        assertTrue(SosNotifier.isFresh(1_000L, 1_000L + 14 * 60_000L))
        assertFalse(SosNotifier.isFresh(1_000L, 1_000L + 15 * 60_000L))
        assertFalse(SosNotifier.isFresh(1_000L, 1_000L + 3 * 60 * 60_000L))
        // A clock that went backwards (reboot) is never fresh.
        assertFalse(SosNotifier.isFresh(10_000L, 5_000L))
    }
}
