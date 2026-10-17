package com.vikash.ampli.drive.car

import android.content.Intent
import android.os.Looper
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.ui.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
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
    }

    @Test
    fun homeLeadsToTheCountdownOnlyWhileACrashRuns() {
        assertEquals(Screen.Crash, MainActivity.screenAfterHome(crashRunning = true))
        assertEquals(Screen.Home, MainActivity.screenAfterHome(crashRunning = false))
    }

    @Test
    fun countdownOpensTheSosMessageWithoutTheScreen() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as DriveApp
        this.app = app

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
}
