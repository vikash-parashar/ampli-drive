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
import com.vikash.ampli.drive.nav.Maneuver
import com.vikash.ampli.drive.nav.NavDestination
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.nav.SavedPlace
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteProgress
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.nav.route.TimedSpan
import com.vikash.ampli.drive.prefs.ThemeDefaults
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.CarHeroImage
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Crimson Drive (the default) on the main screens: 1280x800 in English and 1024x600 in Hindi.
 * Saved to app/build/outputs/roborazzi/crimson-<screen>-<size>.png; nothing is compared. No theme
 * is set: a fresh install must come up in Crimson Drive by itself.
 */
internal class CrimsonShots(private val compose: ComposeContentTestRule, private val size: String) {

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun setUp(homeSaved: Boolean = true) {
        app.prefs.setOwnerName("Vikash")
        app.shell.homeCardDismissed = true
        if (homeSaved) app.places.save(SavedPlace(SavedKind.Home, "Home", 28.8955, 76.6066))
        // The default theme, untouched.
        assertEquals(ThemeDefaults.CRIMSON_DEFAULT, app.prefs.theme.value)
    }

    private fun shot(screen: String) {
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/crimson-$screen-$size.png")
    }

    /** The car is decoded off the main thread: give it a moment, then let Home pick it up. */
    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000L)
        var waited = 0
        while (!CarHeroImage.loaded && waited < 5_000) {
            Thread.sleep(50)
            waited += 50
        }
        compose.mainClock.advanceTimeBy(1_000L)
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = true, isPro = false)
        AmpliDriveTheme(theme) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val unit = maxHeight / 100f
                val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                CompositionLocalProvider(LocalDims provides dims) { content() }
            }
        }
    }

    fun root(screen: Screen, name: String, homeSaved: Boolean = true) {
        setUp(homeSaved)
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = screen, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        settle()
        shot(name)
    }

    /** Home while a route is running: the live map (route in crimson) replaces the car. */
    fun navigating() {
        setUp()
        app.navigator.showPreview(previewTrip())
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        settle()
        shot("home-navigating")
    }

    /** The swipe-down quick panel over a stand-in for Google Maps (its own overlay window). */
    fun quickPanel() {
        setUp()
        compose.mainClock.autoAdvance = false
        val phone = BtDevice("AA:01", "Vikash ka Redmi", connected = true, phone = true, music = true, calls = true, battery = 72)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE8EEF2), Color(0xFFB9D7B0), Color(0xFFDCE6EA))))) {
                Text("Google Maps", color = Color(0xFF5F6368), fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                Frame {
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
        shot("quickpanel")
    }

    /** "Hey Ampli, mausam kaisa hai?": the voice overlay with its answer card, over Home. */
    fun voiceAnswer() {
        setUp()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
                Frame {
                    val answer = VoiceAnswer(VoiceAnswer.Kind.Weather, "Weather here", "27°C", "Sunny · 10% chance of rain", "", Sky.Clear)
                    VoiceOverlay(
                        state = VoiceUiState(active = true, phase = VoiceUiState.Phase.Speaking, heard = "Hey Ampli, mausam kaisa hai?", reply = answer.value, answer = answer),
                        onCancel = {},
                        onChoose = {},
                    )
                }
            }
        }
        settle()
        shot("voice-answer")
    }

    /** Delhi to Rohtak, 400 m before a left turn onto NH 9. */
    private fun previewTrip(): NavSession {
        val a = LatLon(28.6517, 77.0510)
        val b = LatLon(28.6600, 76.9800)
        val c = LatLon(28.8955, 76.6066)
        val steps = listOf(
            RouteStep(Maneuver.Left, null, b, "Rohtak Road", "NH 9", 6_500.0, "turn", "left"),
            RouteStep(Maneuver.Arrive, null, c, "Rohtak", null, 70_000.0, "arrive", null),
        )
        val route = Route(listOf(a, b, c), steps, listOf(TimedSpan(0.0, 70_000.0, 4_200.0)), 70_000.0, 4_200.0, null)
        val progress = RouteProgress(a, 0.0, 6_100.0, 0, 400.0, 63_900.0, 3_800.0, 270.0, 60, false)
        return NavSession(
            phase = NavSession.Phase.Active,
            destination = NavDestination("Rohtak", "Haryana", c.lat, c.lon),
            route = route,
            routeVersion = 1,
            progress = progress,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "en-w1280dp-h800dp-land-mdpi")
class CrimsonScreenshot1280Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = CrimsonShots(compose, "1280x800")

    @Test fun homeParked() = shots.root(Screen.Home, "home-parked")
    @Test fun homeNavigating() = shots.navigating()
    @Test fun settings() = shots.root(Screen.Settings, "settings")
    @Test fun quickPanel() = shots.quickPanel()
    @Test fun voice() = shots.voiceAnswer()
    @Test fun sos() = shots.root(Screen.Help, "sos")
    @Test fun sosCountdown() = shots.root(Screen.Sos, "sos-countdown")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class CrimsonScreenshot1024Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = CrimsonShots(compose, "1024x600")

    // No Home saved here, so the car shows "Where to?" instead of "Go home".
    @Test fun homeParked() = shots.root(Screen.Home, "home-parked", homeSaved = false)
    @Test fun homeNavigating() = shots.navigating()
    @Test fun settings() = shots.root(Screen.Settings, "settings")
    @Test fun quickPanel() = shots.quickPanel()
    @Test fun voice() = shots.voiceAnswer()
    @Test fun sos() = shots.root(Screen.Help, "sos")
    @Test fun sosCountdown() = shots.root(Screen.Sos, "sos-countdown")
}
