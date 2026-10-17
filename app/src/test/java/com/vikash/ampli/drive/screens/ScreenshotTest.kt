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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.billing.ProBilling
import com.vikash.ampli.drive.nav.Maneuver
import com.vikash.ampli.drive.nav.NavDestination
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteProgress
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.nav.route.TimedSpan
import com.vikash.ampli.drive.ui.DesiSamplesCard
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.FirstRunNameHost
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.MoreFromAmpliCard
import com.vikash.ampli.drive.ui.SupportCardContent
import com.vikash.ampli.drive.ui.RestrictedSettingsGuide
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.VoiceStyleCard
import com.vikash.ampli.drive.ui.WelcomeCard
import com.vikash.ampli.drive.ui.WelcomeSettingsCard
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import com.vikash.ampli.drive.voice.WelcomeGreeter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screen previews at the head unit's 1024x600 (landscape, mdpi), in Hindi like a fresh install.
 * No emulator: Robolectric's native graphics draws the real Compose UI and Roborazzi saves PNGs to
 * app/build/outputs/roborazzi/ (CI uploads them as an artifact and attaches them to the pr-test
 * release). They are previews, not pixel-diff tests: nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** The app's theme, backdrop and 1024x600 proportions, as DriveRoot sets them up. */
    @Composable
    private fun Frame(backdrop: Boolean = true, content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = false).let {
            if (it.glass != null) it.withoutBlur() else it
        }
        AmpliDriveTheme(theme) {
            Box(Modifier.fillMaxSize().then(if (backdrop) Modifier.themeBackground(theme) else Modifier)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = maxHeight / 100f
                    val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                    CompositionLocalProvider(LocalDims provides dims) { content() }
                }
            }
        }
    }

    @Test
    fun settingsVoiceWithHearSamples() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(16.dpCompat()), horizontalArrangement = Arrangement.spacedBy(16.dpCompat())) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dpCompat())) {
                        VoiceStyleCard(app = app)
                        WelcomeSettingsCard(app = app)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        DesiSamplesCard(app = app, initiallyOpen = true)
                    }
                }
            }
        }
        shot("settings-voice-hear-samples")
    }

    @Test
    fun nameCard() {
        compose.setContent { Frame { FirstRunNameHost(app = app) } }
        shot("name-card")
    }

    @Test
    fun welcomeCard() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Frame {
                Box(Modifier.fillMaxSize().padding(top = 36.dpCompat()), contentAlignment = Alignment.TopCenter) {
                    WelcomeCard(app, WelcomeGreeter.Card("Ram Ram Vikash Ji 🙏", "राम राम Vikash जी, आज कित का प्लान सै? सवेरे सवेरे कित चाल पड़े ताऊ?", 1L))
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/welcome-card.png")
    }

    @Test
    fun settingsMoreFromAmpli() {
        compose.setContent {
            Frame {
                Column(Modifier.fillMaxSize().padding(16.dpCompat()).verticalScroll(rememberScrollState())) {
                    MoreFromAmpliCard()
                }
            }
        }
        shot("settings-more-from-ampli")
    }

    /** Left: tips on sale (Play build). Right: just after a tip, and a sideloaded build. */
    @Test
    fun settingsSupport() {
        val prices = mapOf(ProBilling.TIP_49 to "₹49.00", ProBilling.TIP_99 to "₹99.00", ProBilling.TIP_199 to "₹199.00")
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(16.dpCompat()), horizontalArrangement = Arrangement.spacedBy(16.dpCompat())) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dpCompat())) {
                        SupportCardContent(prices = prices, thanked = false, onTip = {})
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dpCompat())) {
                        SupportCardContent(prices = prices, thanked = true, onTip = {})
                        SupportCardContent(prices = emptyMap(), thanked = false, onTip = {})
                    }
                }
            }
        }
        shot("settings-support")
    }

    /** Android 13+ guide shown when notification access is still off (sideloaded APK). */
    @Test
    fun restrictedSettingsGuide() {
        app.prefs.setNameAsked()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
                Frame(backdrop = false) {
                    RestrictedSettingsGuide(visible = true, onOpenAppInfo = {}, onOpenAccess = {}, onClose = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/restricted-settings-guide.png")
    }

    @Test
    fun homeScreen() {
        app.prefs.setNameAsked()
        compose.setContent { DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        shot("home")
    }

    @Test
    fun navScreen() {
        app.prefs.setNameAsked()
        app.navigator.showPreview(previewTrip())
        compose.setContent { DriveRoot(app = app, screen = Screen.Navigation, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        shot("navigation")
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

private fun Int.dpCompat() = androidx.compose.ui.unit.Dp(this.toFloat())
