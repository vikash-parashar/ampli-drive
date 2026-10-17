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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    private fun home() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun homeLeadsToTheCountdownOnlyWhileACrashRuns() {
        assertEquals(Screen.Crash, MainActivity.screenAfterHome(crashRunning = true))
        assertEquals(Screen.Home, MainActivity.screenAfterHome(crashRunning = false))
    }

    @Test
    fun homeDuringCrashCountdownKeepsTheCountdown() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val app = activity.application as DriveApp
        this.app = app

        app.car.triggerCrash()
        // What triggerCrash's own startActivity delivers to the running launcher.
        controller.newIntent(
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_VOICE_ACTION, MainActivity.VOICE_CRASH),
        )
        idle()
        assertEquals(Screen.Crash, activity.currentScreen)

        controller.newIntent(home())
        idle()
        assertEquals(Screen.Crash, activity.currentScreen)
        assertNotNull(app.car.crashAt.value)

        // Cancelled: HOME works again, and the next crash is detected.
        app.car.clearCrash()
        controller.newIntent(home())
        idle()
        assertEquals(Screen.Home, activity.currentScreen)
        app.car.triggerCrash()
        assertNotNull(app.car.crashAt.value)
    }

    @Test
    fun countdownOpensTheSosMessageWithoutTheScreen() {
        val app = org.robolectric.RuntimeEnvironment.getApplication() as DriveApp
        this.app = app

        app.car.triggerCrash()
        assertNotNull(app.car.crashAt.value)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(CarMonitor.CRASH_COUNTDOWN_S + 2L))

        assertNull(app.car.crashAt.value)
        assertNotNull(app.car.crashSos.value)
        val opened = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        assert(opened.any { it.action == Intent.ACTION_SENDTO }) { "SMS composer not opened: ${opened.map { it.action }}" }

        // A later crash starts a fresh countdown.
        app.car.triggerCrash()
        assertNotNull(app.car.crashAt.value)
        assertNull(app.car.crashSos.value)
    }
}
