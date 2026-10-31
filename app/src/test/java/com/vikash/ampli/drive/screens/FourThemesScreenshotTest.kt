package com.vikash.ampli.drive.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import android.Manifest
import android.location.Location
import android.location.LocationManager
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.location.LocationSource
import com.vikash.ampli.drive.media.MediaUiState
import com.vikash.ampli.drive.media.NowPlaying
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.nav.SavedPlace
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.Shadows.shadowOf
import com.vikash.ampli.drive.nav.Maneuver
import com.vikash.ampli.drive.nav.NavDestination
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteProgress
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.nav.route.TimedSpan
import com.vikash.ampli.drive.prefs.ThemeChoice
import com.vikash.ampli.drive.prefs.ThemeDefaults
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.LocalMapPreview
import com.vikash.ampli.drive.ui.MoreSheet
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.ThemePicker
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The four home themes at the head unit sizes: Home (parked and navigating), the More sheet and
 * Settings › Home screen theme. Saved to app/build/outputs/roborazzi/fourthemes-*.png; nothing is
 * compared. Pro is unlocked with the debug test switch so the Pro themes can be shown.
 */
internal class FourThemeShots(private val compose: ComposeContentTestRule, private val size: String) {

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun setUp(themeId: String, pro: Boolean = true) {
        app.prefs.setOwnerName("Vikash")
        app.prefs.setNameAsked()
        app.shell.homeCardDismissed = true
        app.prefs.setProTestUnlock(pro)
        app.prefs.setTheme(ThemeChoice(themeId))
        app.voicePlayer.showCaption("Bhai, aage camera sai, 80 te upar mat ja.")
        assertEquals(themeId, DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = app.prefs.pro.value.isPro).id)
    }

    private fun shot(name: String, before: () -> Unit = {}) {
        before()
        compose.mainClock.advanceTimeBy(1_500L)
        before()
        compose.mainClock.advanceTimeBy(100L)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/fourthemes-$name-$size.png")
    }

    private fun short(id: String) = when (id) {
        ThemeDefaults.MAP_FIRST -> "mapfirst"
        ThemeDefaults.BIG_GLANCE -> "bigglance"
        ThemeDefaults.BENTO -> "bento"
        else -> "roadsign"
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = app.prefs.pro.value.isPro)
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

    fun home(themeId: String, navigating: Boolean) {
        setUp(themeId)
        // Parked: Home is saved, so Map First's card offers "Go home".
        if (!navigating) app.places.save(SavedPlace(SavedKind.Home, "Home", 28.6517, 77.0510))
        if (navigating) app.navigator.showPreview(previewTrip())
        compose.mainClock.autoAdvance = false
        compose.setContent { DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        shot(short(themeId) + if (navigating) "-home-nav" else "-home")
    }

    /**
     * The real driving look: location on at 62 km/h under an 80 limit, a camera 400 m ahead, the
     * trip to Rohtak and a song playing. All fake, set only here. The live map cannot draw off a
     * device, so the map pane draws the drawn map with the route ([LocalMapPreview]).
     */
    fun driving(themeId: String) {
        setUp(themeId)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        app.navigator.showPreview(previewTrip())
        val fake = {
            val here = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 28.6517
                longitude = 77.0510
                speed = 17.2f
                bearing = 270f
                time = System.currentTimeMillis()
            }
            flow<GpsState>(app.speedTracker, "_state").value = GpsState(
                hasPermission = true,
                providerEnabled = true,
                location = here,
                speedKmh = 62f,
                satellitesUsed = 9,
                satellitesInView = 14,
                source = LocationSource.Gps,
            )
            flow<Int?>(app.cameraAlerts, "_limit").value = 80
            flow<CameraAlert?>(app.cameraAlerts, "_current").value =
                CameraAlert("preview", CameraAlert.Kind.FixedCamera, 400, 80, "NH 9")
            flow<MediaUiState>(app.mediaHub, "_state").value = MediaUiState(
                accessGranted = true,
                nowPlaying = NowPlaying(
                    packageName = "com.example.music",
                    title = "Tu Cheez Lajawab",
                    artist = "Sapna Choudhary",
                    album = null,
                    art = null,
                    durationMs = 214_000L,
                    positionMs = 61_000L,
                    positionUpdatedAt = 0L,
                    playbackSpeed = 1f,
                    isPlaying = true,
                ),
            )
        }
        fake()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalMapPreview provides true) {
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
            }
        }
        compose.mainClock.advanceTimeBy(300L)
        shot(short(themeId) + "-home-drive", before = fake)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> flow(owner: Any, field: String): MutableStateFlow<T> =
        owner.javaClass.getDeclaredField(field).apply { isAccessible = true }.get(owner) as MutableStateFlow<T>

    fun more(themeId: String) {
        setUp(themeId)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                DriveRoot(app = app, screen = Screen.Home, resumeTick = 0, onNavigate = {}, onRequestPermissions = {})
                Frame(content = { MoreSheet(app = app, onDismiss = {}, onTile = {}) })
            }
        }
        shot(short(themeId) + "-more")
    }

    /** Settings › Home screen theme as a free driver sees it: three locked Pro themes. */
    fun picker(themeId: String) {
        setUp(themeId, pro = themeId != ThemeDefaults.MAP_FIRST)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Frame {
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                    ThemePicker(choice = app.prefs.theme.value, isPro = app.prefs.pro.value.isPro, onChange = {}, onLocked = {})
                }
            }
        }
        shot(short(themeId) + "-settings-theme")
    }

    /** Delhi to Rohtak, 300 m before a left turn onto NH 9. */
    private fun previewTrip(): NavSession {
        val a = LatLon(28.6517, 77.0510)
        val b = LatLon(28.6600, 76.9800)
        val c = LatLon(28.8955, 76.6066)
        val steps = listOf(
            RouteStep(Maneuver.Left, null, b, "Rohtak Road", "NH 9", 6_500.0, "turn", "left"),
            RouteStep(Maneuver.Arrive, null, c, "Rohtak", null, 70_000.0, "arrive", null),
        )
        val route = Route(listOf(a, b, c), steps, listOf(TimedSpan(0.0, 70_000.0, 4_200.0)), 70_000.0, 4_200.0, null)
        val progress = RouteProgress(a, 0.0, 6_100.0, 0, 300.0, 63_900.0, 3_800.0, 270.0, 60, false)
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
class FourThemesScreenshot1280Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = FourThemeShots(compose, "1280x800")

    @Test fun mapFirstHome() = shots.home(ThemeDefaults.MAP_FIRST, navigating = false)
    @Test fun mapFirstHomeNavigating() = shots.home(ThemeDefaults.MAP_FIRST, navigating = true)
    @Test fun bigGlanceHome() = shots.home(ThemeDefaults.BIG_GLANCE, navigating = true)
    @Test fun bentoHome() = shots.home(ThemeDefaults.BENTO, navigating = true)
    @Test fun roadSignHome() = shots.home(ThemeDefaults.ROAD_SIGN, navigating = true)
    @Test fun mapFirstDriving() = shots.driving(ThemeDefaults.MAP_FIRST)
    @Test fun bigGlanceDriving() = shots.driving(ThemeDefaults.BIG_GLANCE)
    @Test fun bentoDriving() = shots.driving(ThemeDefaults.BENTO)
    @Test fun roadSignDriving() = shots.driving(ThemeDefaults.ROAD_SIGN)
    @Test fun mapFirstMore() = shots.more(ThemeDefaults.MAP_FIRST)
    @Test fun bigGlanceMore() = shots.more(ThemeDefaults.BIG_GLANCE)
    @Test fun bentoMore() = shots.more(ThemeDefaults.BENTO)
    @Test fun roadSignMore() = shots.more(ThemeDefaults.ROAD_SIGN)
    @Test fun pickerFree() = shots.picker(ThemeDefaults.MAP_FIRST)
    @Test fun pickerPro() = shots.picker(ThemeDefaults.BENTO)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class FourThemesScreenshot1024Test {
    @get:Rule
    val compose = createComposeRule()
    private val shots get() = FourThemeShots(compose, "1024x600")

    @Test fun mapFirstHome() = shots.home(ThemeDefaults.MAP_FIRST, navigating = false)
    @Test fun mapFirstHomeNavigating() = shots.home(ThemeDefaults.MAP_FIRST, navigating = true)
    @Test fun bigGlanceHome() = shots.home(ThemeDefaults.BIG_GLANCE, navigating = true)
    @Test fun bentoHome() = shots.home(ThemeDefaults.BENTO, navigating = true)
    @Test fun roadSignHome() = shots.home(ThemeDefaults.ROAD_SIGN, navigating = true)
    @Test fun mapFirstDriving() = shots.driving(ThemeDefaults.MAP_FIRST)
    @Test fun bigGlanceDriving() = shots.driving(ThemeDefaults.BIG_GLANCE)
    @Test fun bentoDriving() = shots.driving(ThemeDefaults.BENTO)
    @Test fun roadSignDriving() = shots.driving(ThemeDefaults.ROAD_SIGN)
    @Test fun mapFirstMore() = shots.more(ThemeDefaults.MAP_FIRST)
    @Test fun roadSignMore() = shots.more(ThemeDefaults.ROAD_SIGN)
    @Test fun pickerFree() = shots.picker(ThemeDefaults.MAP_FIRST)
}
