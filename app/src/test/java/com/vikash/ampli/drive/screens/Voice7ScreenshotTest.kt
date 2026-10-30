package com.vikash.ampli.drive.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.VoiceCommandsScreen
import com.vikash.ampli.drive.ui.VoiceOverlay
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import com.vikash.ampli.drive.voice.VoiceAnswer
import com.vikash.ampli.drive.voice.VoiceUiState
import com.vikash.ampli.drive.weather.Sky
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Round 7 "Hey Ampli": the answer card (weather, time, volume) over the home screen and the
 * "What can I say?" list, at the owner's 1280x800 and at 1024x600. Saved to
 * app/build/outputs/roborazzi/voice7-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1280dp-h800dp-land-mdpi")
class Voice7ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    @Composable
    private fun Frame(behind: Boolean = false, content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = false).let {
            if (it.glass != null) it.withoutBlur() else it
        }
        AmpliDriveTheme(theme) {
            Box(Modifier.fillMaxSize().themeBackground(theme)) {
                if (behind) {
                    // A stand-in for the home screen under the overlay's scrim.
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1B2733), Color(0xFF29404F)))))
                    Text("Ampli Car Hub", color = Color(0x99FFFFFF), fontSize = 44.sp, modifier = Modifier.align(Alignment.TopCenter))
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = maxHeight / 100f
                    val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                    CompositionLocalProvider(LocalDims provides dims) { content() }
                }
            }
        }
    }

    private fun overlay(name: String, heard: String, answer: VoiceAnswer) {
        // The mic pulses forever: stop the clock and step it once.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Frame(behind = true) {
                VoiceOverlay(
                    state = VoiceUiState(active = true, phase = VoiceUiState.Phase.Speaking, heard = heard, reply = answer.value, answer = answer),
                    onCancel = {},
                    onChoose = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_500L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun weatherHi() = VoiceAnswer(
        VoiceAnswer.Kind.Weather,
        "यहाँ का मौसम",
        "31°C",
        "हल्के बादल · बारिश की संभावना 10% · अगले कुछ घंटों में बारिश के आसार नहीं हैं।",
        "मौसम डेटा: MET Norway (CC BY 4.0)",
        Sky.PartlyCloudy,
    )

    private fun commands(name: String) {
        compose.setContent { Frame { VoiceCommandsScreen(onBack = {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun weather1280() = overlay("voice7-answer-weather-1280", "मौसम कैसा है", weatherHi())

    @Test
    fun time1280() = overlay(
        "voice7-answer-time-1280",
        "kitne baje hain",
        VoiceAnswer(VoiceAnswer.Kind.Time, "अभी का समय", "5:20 pm", "गुरुवार, 30 अक्तूबर 2026"),
    )

    @Test
    fun volume1280() = overlay(
        "voice7-answer-volume-1280",
        "awaaz badhao",
        VoiceAnswer(VoiceAnswer.Kind.Volume, "म्यूज़िक की आवाज़", "7 / 10", "स्टेप 10 / 15", "आगे बढ़ाने के लिए “और” बोलिए"),
    )

    @Test
    fun commands1280() = commands("voice7-commands-1280")

    @Test
    @Config(qualifiers = "hi-w1024dp-h600dp-land-mdpi")
    fun weather1024() = overlay("voice7-answer-weather-1024", "मौसम कैसा है", weatherHi())

    @Test
    @Config(qualifiers = "en-w1024dp-h600dp-land-mdpi")
    fun offline1024() = overlay(
        "voice7-answer-offline-1024",
        "will it rain",
        VoiceAnswer(VoiceAnswer.Kind.Problem, "Weather here", "—", "No internet, so I can't check the weather right now."),
    )

    @Test
    @Config(qualifiers = "hi-w1024dp-h600dp-land-mdpi")
    fun commands1024() = commands("voice7-commands-1024")

    @Test
    @Config(qualifiers = "en-w1024dp-h600dp-land-mdpi")
    fun commandsEnglish1024() = commands("voice7-commands-en-1024")
}
