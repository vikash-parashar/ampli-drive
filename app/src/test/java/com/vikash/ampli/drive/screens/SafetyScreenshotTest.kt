package com.vikash.ampli.drive.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.emergency.EmergencyContact
import com.vikash.ampli.drive.emergency.EmergencyProfile
import com.vikash.ampli.drive.emergency.Sos
import com.vikash.ampli.drive.ui.CarSettingsCard
import com.vikash.ampli.drive.ui.CrashOverlayCard
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.SosReady
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Previews of the safety screens at 1024x600 in Hindi: the crash countdown, the SOS countdown,
 * the SOS-ready screen, and the "Display over other apps" card for crash detection.
 * Saved to app/build/outputs/roborazzi/safety-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class SafetyScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private val profile = EmergencyProfile(
        ownerName = "Vikash",
        contacts = listOf(EmergencyContact("Papa", "+91 98765 43210"), EmergencyContact("Bhai", "+91 91234 56789")),
        bloodGroup = "B+",
        vehicleNumber = "HR26AB1234",
        vehicleModel = "Maruti Swift",
    )

    private fun shot(name: String) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = false)
        AmpliDriveTheme(theme) {
            Box(Modifier.fillMaxSize().themeBackground(theme)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = maxHeight / 100f
                    val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                    CompositionLocalProvider(LocalDims provides dims) { content() }
                }
            }
        }
    }

    /** The 30 s crash countdown, a second in. The clock is held so it never runs out. */
    @Test
    fun crashCountdown() {
        app.prefs.setNameAsked()
        app.emergency.save(profile)
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = Screen.Crash, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        compose.mainClock.advanceTimeBy(1_500L)
        shot("safety-crash-countdown")
    }

    /** The SOS countdown (Help button held), a second in. */
    @Test
    fun sosCountdown() {
        app.prefs.setNameAsked()
        app.emergency.save(profile)
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = Screen.Sos, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        compose.mainClock.advanceTimeBy(1_500L)
        shot("safety-sos-countdown")
    }

    /** After the countdown: the message is open in the SMS app; this screen is for responders. */
    @Test
    fun sosReady() {
        val message = Sos.buildMessage(profile, null, now = 1_760_000_000_000L)
        compose.setContent {
            Frame {
                SosReady(profile = profile, message = message, onSmsAgain = {}, onShare = {}, onDial = {}, onDone = {})
            }
        }
        compose.waitForIdle()
        shot("safety-sos-ready")
    }

    /**
     * Left: Settings › Car with crash detection on and "Display over other apps" missing (status
     * row). Right: the card shown right after crash detection is switched on.
     */
    @Test
    fun crashOverlayCard() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(16f))) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        CarSettingsCard(app = app, onOpenCar = {})
                    }
                    Column(Modifier.weight(1f)) {
                        CrashOverlayCard(onAllow = {}, onLater = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("safety-crash-overlay")
    }
}
