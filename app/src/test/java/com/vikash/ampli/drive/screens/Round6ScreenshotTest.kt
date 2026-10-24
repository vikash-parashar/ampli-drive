package com.vikash.ampli.drive.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.car.ParkedSpot
import com.vikash.ampli.drive.car.Trip
import com.vikash.ampli.drive.nav.SavedKind
import com.vikash.ampli.drive.nav.SavedPlace
import com.vikash.ampli.drive.system.FavouriteContact
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.FavouriteCallsContent
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.SpeedVolumeCardContent
import com.vikash.ampli.drive.ui.StoreContent
import com.vikash.ampli.drive.media.SpeedVolumeLevel
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
 * Previews of the round-6 screens (trips, store, parking QR, saved places, phone card, volume by
 * speed) at 1024x600 in Hindi. Saved to app/build/outputs/roborazzi/round6-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class Round6ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = false).let {
            if (it.glass != null) it.withoutBlur() else it
        }
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

    private fun root(screen: Screen) {
        app.prefs.setNameAsked()
        compose.setContent { DriveRoot(app = app, screen = screen, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
    }

    private fun seedPlaces() {
        app.places.save(SavedPlace(SavedKind.Home, "Ghar", 28.4595, 77.0266))
        app.places.save(SavedPlace(SavedKind.Office, "Cyber City", 28.4950, 77.0890))
        app.places.save(SavedPlace(SavedKind.Favourite, "Mandir", 28.4700, 77.0500))
    }

    @Test
    fun trips() {
        seedPlaces()
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        app.car.trips.add(Trip(now - 30 * hour, now - 29 * hour, 28.4595, 77.0266, 28.4950, 77.0890, 14_200.0, 72f))
        app.car.trips.add(Trip(now - 5 * hour, now - 4 * hour - 1_500_000L, 28.4950, 77.0890, 28.4595, 77.0266, 13_900.0, 64f))
        app.car.trips.add(Trip(now - 2 * hour, now - hour, 28.4595, 77.0266, 28.9931, 77.0151, 62_400.0, 96f))
        root(Screen.Trips)
        shot("round6-trips")
    }

    @Test
    fun store() {
        compose.setContent {
            Frame {
                StoreContent(isPro = false, proPrice = "₹199", launchLine = false, mp3Installed = false, onBack = {}, onPro = {}, onMp3 = {})
            }
        }
        shot("round6-store")
    }

    @Test
    fun parkedQr() {
        app.car.store.setParked(ParkedSpot(28.4595, 77.0266, System.currentTimeMillis() - 1_800_000L, 8f))
        root(Screen.Parked)
        shot("round6-parked-qr")
    }

    @Test
    fun navSearchPlaces() {
        seedPlaces()
        root(Screen.NavSearch)
        shot("round6-navsearch-places")
    }

    @Test
    fun phoneCardAndSpeedVolume() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(16f))) {
                    Column(Modifier.weight(1f)) {
                        FavouriteCallsContent(
                            contacts = listOf(FavouriteContact("Maa", "9810000001"), FavouriteContact("Papa", "9810000002"), FavouriteContact("Ravi", "9810000003")),
                            canAdd = true,
                            onCall = {}, onAdd = {}, onRemove = {}, onOpenPhone = {},
                        )
                    }
                    Column(Modifier.weight(1f)) { SpeedVolumeCardContent(SpeedVolumeLevel.Medium) {} }
                }
            }
        }
        shot("round6-phone-card-speed-volume")
    }
}
