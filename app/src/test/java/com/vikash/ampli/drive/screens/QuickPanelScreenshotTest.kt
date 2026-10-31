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
import com.vikash.ampli.drive.bluetooth.BtDevice
import com.vikash.ampli.drive.system.Shell
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.OVERLAY_CONTROLS
import com.vikash.ampli.drive.ui.QuickControlsPanel
import com.vikash.ampli.drive.ui.QuickPanelExtras
import com.vikash.ampli.drive.ui.quickTiles
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The quick panel that a swipe down from the top opens over other apps, drawn over a stand-in
 * for Google Maps. Saved to app/build/outputs/roborazzi/quickpanel-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1280dp-h800dp-land-mdpi")
class QuickPanelScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun OverMaps(content: @Composable () -> Unit) {
        val theme = DriveThemes.resolve(app.prefs.theme.value, night = false, isPro = false).let {
            if (it.glass != null) it.withoutBlur() else it
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFFE8EEF2), Color(0xFFB9D7B0), Color(0xFFDCE6EA)))),
        ) {
            Text("Google Maps", color = Color(0xFF5F6368), fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
            AmpliDriveTheme(theme) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val unit = maxHeight / 100f
                    val dims = Dims(u = unit, uSp = with(LocalDensity.current) { unit.toSp().value }, compact = maxWidth / maxHeight < 1.72f)
                    CompositionLocalProvider(LocalDims provides dims) { content() }
                }
            }
        }
    }

    private fun panel(phone: BtDevice?, playing: Boolean) {
        compose.setContent {
            OverMaps {
                QuickControlsPanel(
                    visible = true,
                    tiles = quickTiles(
                        Shell.Status(wifiOn = true, wifiConnected = true, ssid = "Vikash Hotspot", brightnessPct = 70, volumePct = 45, eqApp = null),
                        btSupported = true,
                        btOn = true,
                        btDevice = phone?.name,
                    ).filter { it.control in OVERLAY_CONTROLS },
                    onTile = {},
                    onClose = {},
                    perRow = OVERLAY_CONTROLS.size,
                    header = {
                        QuickPanelExtras(
                            phone = phone,
                            playing = playing,
                            onPlayPhone = {},
                            onBtSettings = {},
                            onKey = {},
                            onAmpliMusic = {},
                            onHome = {},
                        )
                    },
                )
            }
        }
    }

    private val redmi = BtDevice("AA:01", "Vikash ka Redmi", connected = true, phone = true, music = true, calls = true, battery = 72)

    @Test
    fun panel1280() {
        panel(redmi, playing = true)
        shot("quickpanel-1280x800")
    }

    @Test
    @Config(qualifiers = "hi-w1024dp-h600dp-land-mdpi")
    fun panel1024() {
        panel(redmi, playing = false)
        shot("quickpanel-1024x600")
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-land-mdpi")
    fun panelNoPhoneEnglish() {
        panel(null, playing = false)
        shot("quickpanel-1280x800-en-nophone")
    }
}
