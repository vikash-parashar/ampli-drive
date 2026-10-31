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
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.bluetooth.BtDevice
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.OVERLAY_CONTROLS
import com.vikash.ampli.drive.ui.QuickControlsPanel
import com.vikash.ampli.drive.ui.QuickPanelExtras
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.VoiceOverlay
import com.vikash.ampli.drive.ui.quickTiles
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
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
 * The Ampli Soft Day / Night (neumorphic) look on the main screens, at the SC7731E unit's
 * 1280x800 and at 1024x600. Saved to app/build/outputs/roborazzi/neo-<screen>-<size>-<day|night>.png;
 * nothing is compared.
 */
internal class NeoShots(private val compose: ComposeContentTestRule, private val size: String) {

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun useSoft(night: Boolean) {
        app.prefs.setNameAsked()
        app.shell.homeCardDismissed = true
        val id = if (night) ThemeDefaults.SOFT_NIGHT else ThemeDefaults.SOFT_DAY
        app.prefs.setTheme(ThemeChoice(auto = false, dayId = ThemeDefaults.SOFT_DAY, nightId = ThemeDefaults.SOFT_NIGHT, fixedId = id))
    }

    private fun shot(screen: String, night: Boolean) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/neo-$screen-$size-${if (night) "night" else "day"}.png")
    }

    /** The active theme, backdrop optional, and layout units, as DriveRoot sets them up. */
    @Composable
    private fun Frame(night: Boolean, content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = night, isPro = false)
        AmpliDriveTheme(theme) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = maxHeight / 100f
                val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                CompositionLocalProvider(LocalDims provides dims) { content() }
            }
        }
    }

    fun root(screen: Screen, name: String, night: Boolean) {
        useSoft(night)
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = screen, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        compose.mainClock.advanceTimeBy(2_000L)
        shot(name, night)
    }

    /** The swipe-down quick panel over a stand-in for Google Maps (its own overlay window). */
    fun quickPanel(night: Boolean) {
        useSoft(night)
        compose.mainClock.autoAdvance = false
        val phone = BtDevice("AA:01", "Vikash ka Redmi", connected = true, phone = true, music = true, calls = true, battery = 72)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE8EEF2), Color(0xFFB9D7B0), Color(0xFFDCE6EA))))) {
                Text("Google Maps", color = Color(0xFF5F6368), fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                Frame(night) {
                    QuickControlsPanel(
                        visible = true,
                        tiles = quickTiles(
                            Shell.Status(wifiOn = true, wifiConnected = true, ssid = "Vikash Hotspot", brightnessPct = 70, volumePct = 45, eqApp = null),
                            btSupported = true,
                            btOn = true,
                            btDevice = phone.name,
                        ).filter { it.control in OVERLAY_CONTROLS },
                        onTile = {},
                        onClose = {},
                        perRow = OVERLAY_CONTROLS.size,
                        header = {
                            QuickPanelExtras(
                                phone = phone, playing = true, onPlayPhone = {}, onBtSettings = {}, onKey = {}, onAmpliMusic = {}, onHome = {},
                            )
                        },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000L)
        shot("quickpanel", night)
    }

    /** "Hey Ampli, what's the time?": the voice overlay with its answer card, over Home. */
    fun voiceAnswer(night: Boolean) {
        useSoft(night)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
                Frame(night) {
                    val answer = VoiceAnswer(VoiceAnswer.Kind.Weather, "Weather here", "27°C", "Sunny · 10% chance of rain", "", Sky.Clear)
                    VoiceOverlay(
                        state = VoiceUiState(active = true, phase = VoiceUiState.Phase.Speaking, heard = "Hey Ampli, mausam kaisa hai?", reply = answer.value, answer = answer),
                        onCancel = {},
                        onChoose = {},
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500L)
        shot("voice-answer", night)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "en-w1280dp-h800dp-land-mdpi")
class NeoScreenshot1280Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = NeoShots(compose, "1280x800")

    @Test fun homeDay() = shots.root(Screen.Home, "home", night = false)
    @Test fun homeNight() = shots.root(Screen.Home, "home", night = true)
    @Test fun settingsDay() = shots.root(Screen.Settings, "settings", night = false)
    @Test fun settingsNight() = shots.root(Screen.Settings, "settings", night = true)
    @Test fun appsDay() = shots.root(Screen.Apps, "apps", night = false)
    @Test fun appsNight() = shots.root(Screen.Apps, "apps", night = true)
    @Test fun quickPanelDay() = shots.quickPanel(night = false)
    @Test fun quickPanelNight() = shots.quickPanel(night = true)
    @Test fun voiceDay() = shots.voiceAnswer(night = false)
    @Test fun voiceNight() = shots.voiceAnswer(night = true)
    @Test fun sosDay() = shots.root(Screen.Help, "sos", night = false)
    @Test fun sosNight() = shots.root(Screen.Help, "sos", night = true)
    @Test fun sosCountdownDay() = shots.root(Screen.Sos, "sos-countdown", night = false)
    @Test fun sosCountdownNight() = shots.root(Screen.Sos, "sos-countdown", night = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class NeoScreenshot1024Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = NeoShots(compose, "1024x600")

    @Test fun homeDay() = shots.root(Screen.Home, "home", night = false)
    @Test fun homeNight() = shots.root(Screen.Home, "home", night = true)
    @Test fun settingsDay() = shots.root(Screen.Settings, "settings", night = false)
    @Test fun settingsNight() = shots.root(Screen.Settings, "settings", night = true)
    @Test fun appsDay() = shots.root(Screen.Apps, "apps", night = false)
    @Test fun appsNight() = shots.root(Screen.Apps, "apps", night = true)
    @Test fun quickPanelDay() = shots.quickPanel(night = false)
    @Test fun quickPanelNight() = shots.quickPanel(night = true)
    @Test fun voiceDay() = shots.voiceAnswer(night = false)
    @Test fun voiceNight() = shots.voiceAnswer(night = true)
    @Test fun sosDay() = shots.root(Screen.Help, "sos", night = false)
    @Test fun sosNight() = shots.root(Screen.Help, "sos", night = true)
    @Test fun sosCountdownDay() = shots.root(Screen.Sos, "sos-countdown", night = false)
    @Test fun sosCountdownNight() = shots.root(Screen.Sos, "sos-countdown", night = true)
}
