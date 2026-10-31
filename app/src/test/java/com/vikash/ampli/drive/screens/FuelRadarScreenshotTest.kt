package com.vikash.ampli.drive.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.cameras.AheadItem
import com.vikash.ampli.drive.cameras.AheadKind
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.cameras.ReportKind
import com.vikash.ampli.drive.fuel.FuelFlags
import com.vikash.ampli.drive.fuel.FuelFilter
import com.vikash.ampli.drive.fuel.FuelHit
import com.vikash.ampli.drive.fuel.FuelStation
import com.vikash.ampli.drive.fuel.FuelStats
import com.vikash.ampli.drive.ui.AheadMode
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.FuelContent
import com.vikash.ampli.drive.ui.FuelStatus
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.RadarContent
import com.vikash.ampli.drive.ui.ReportSheet
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Previews of the fuel finder, the radar screen and the "What's ahead?" report sheet, in Crimson
 * Drive and in the Soft (neumorphic) look, at 1280x800 and 1024x600. Built from the stateless
 * contents with sample data, so no GPS or snapshot is needed. Saved to
 * app/build/outputs/roborazzi/fuelradar-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "en-w1280dp-h800dp-land-mdpi")
class FuelRadarScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/fuelradar-$name.png")
    }

    @Composable
    private fun Frame(theme: DriveTheme, content: @Composable () -> Unit) {
        val t = if (theme.glass != null) theme.withoutBlur() else theme
        AmpliDriveTheme(t) {
            Box(Modifier.fillMaxSize().themeBackground(t)) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = maxHeight / 100f
                    val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                    CompositionLocalProvider(LocalDims provides dims) { content() }
                }
            }
        }
    }

    private val car = 28.8955 to 76.6066

    private val hits = listOf(
        FuelHit(FuelStation(1, 28.9005, 76.6110, FuelFlags.CNG, "IGL CNG Station Sector 7"), 700.0),
        FuelHit(FuelStation(2, 28.8890, 76.6150, FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.CNG or FuelFlags.OPEN_24H, "Indian Oil, Delhi Road"), 1_100.0),
        FuelHit(FuelStation(3, 28.9080, 76.5950, FuelFlags.EV, "Tata Power EZ Charge"), 1_800.0),
        FuelHit(FuelStation(4, 28.8800, 76.5900, FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.ASSUMED, "Bharat Petroleum"), 2_400.0),
        FuelHit(FuelStation(5, 28.9150, 76.6300, FuelFlags.PETROL or FuelFlags.DIESEL, ""), 3_600.0),
        FuelHit(FuelStation(6, 28.8700, 76.6400, FuelFlags.CNG, "Haryana City Gas"), 4_900.0),
    )

    private val stats = FuelStats(ready = true, count = 41_250, dataDate = "2026-10-31")

    private fun fuel(theme: DriveTheme, filter: FuelFilter = FuelFilter.All) {
        val shown = hits.filter { filter.matches(it.station.flags) }
        compose.setContent {
            Frame(theme) {
                FuelContent(
                    filter = filter, onFilter = {}, status = FuelStatus.Ready, hits = shown, car = car,
                    selected = 0, onSelect = {}, stats = stats, onGo = {}, onBack = {},
                )
            }
        }
    }

    private val now = 1_790_000_000_000L

    private val ahead = listOf(
        AheadItem("n1", AheadKind.FixedCamera, 380.0, 60),
        AheadItem("rep_1", AheadKind.Police, 2_100.0, null, reportedAt = now - 12 * 60_000L),
        AheadItem("r9", AheadKind.AverageZone, 5_400.0, 80, zoneLengthM = 6_200.0, name = "Rohtak bypass"),
        AheadItem("n2", AheadKind.RedLight, 8_900.0, null),
        AheadItem("rep_2", AheadKind.Jam, 12_300.0, null, reportedAt = now - 40 * 60_000L),
    )

    private fun radar(theme: DriveTheme, over: Boolean, pro: Boolean) {
        val alert = CameraAlert(
            id = "n1", kind = CameraAlert.Kind.FixedCamera, distanceMeters = 380, limitKmh = 60,
            description = "", overLimit = over,
        )
        compose.setContent {
            Frame(theme) {
                RadarContent(
                    alert = alert, speedKmh = if (over) 74 else 52, limitKmh = 60, alertDistanceM = 500,
                    ahead = if (pro) ahead else null, aheadMode = AheadMode.Route,
                    aheadLocked = !pro, reportLocked = !pro, now = now,
                    onReport = {}, onUnlockPro = {}, onBack = {},
                )
            }
        }
    }

    @Test
    fun fuelCrimson() {
        fuel(DriveThemes.CrimsonDrive)
        shot("fuel-crimson-1280x800")
    }

    @Test
    fun fuelCngSoft() {
        fuel(DriveThemes.Neumorphic, FuelFilter.Cng)
        shot("fuel-cng-soft-1280x800")
    }

    @Test
    @Config(qualifiers = "hi-w1024dp-h600dp-land-mdpi")
    fun fuelHindiSmall() {
        fuel(DriveThemes.CrimsonDrive)
        shot("fuel-crimson-hi-1024x600")
    }

    @Test
    fun radarOverLimitPro() {
        radar(DriveThemes.CrimsonDrive, over = true, pro = true)
        shot("radar-over-crimson-1280x800")
    }

    @Test
    fun radarFree() {
        radar(DriveThemes.CrimsonDrive, over = false, pro = false)
        shot("radar-free-crimson-1280x800")
    }

    @Test
    fun radarSoft() {
        radar(DriveThemes.Neumorphic, over = false, pro = true)
        shot("radar-soft-1280x800")
    }

    @Test
    @Config(qualifiers = "hi-w1024dp-h600dp-land-mdpi")
    fun radarHindiSmall() {
        radar(DriveThemes.CrimsonDrive, over = true, pro = true)
        shot("radar-over-crimson-hi-1024x600")
    }

    @Test
    fun reportSheet() {
        compose.setContent {
            Frame(DriveThemes.CrimsonDrive) {
                RadarContent(
                    alert = null, speedKmh = 48, limitKmh = null, alertDistanceM = 500,
                    ahead = ahead, aheadMode = AheadMode.Heading, aheadLocked = false, reportLocked = false, now = now,
                    onReport = {}, onUnlockPro = {}, onBack = {},
                )
                ReportSheet(reported = ReportKind.Police, onReport = {}, onDismiss = {})
            }
        }
        shot("report-crimson-1280x800")
    }
}
