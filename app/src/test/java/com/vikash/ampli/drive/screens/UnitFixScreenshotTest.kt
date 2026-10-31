package com.vikash.ampli.drive.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.bluetooth.BtDevice
import com.vikash.ampli.drive.system.FavouriteContact
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.GetAmpliContent
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.Mp3Download
import com.vikash.ampli.drive.ui.Pane
import com.vikash.ampli.drive.ui.PhoneLinkContent
import com.vikash.ampli.drive.ui.PhoneMusic
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.ui.themeBackground
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Previews of the unit fixes: "Get Ampli MP3 Player", the Phone Link sheet and the "Phone music"
 * card, at 1024x600 and 1280x800 in Hindi. Saved to app/build/outputs/roborazzi/unitfix-*.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class UnitFixScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: DriveApp get() = RuntimeEnvironment.getApplication() as DriveApp

    private fun shot(name: String) {
        compose.waitForIdle()
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

    /** A dialog-like panel (the real sheets are AlertDialogs, which a root capture can't see). */
    @Composable
    private fun Sheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        val theme = LocalDriveTheme.current
        val bg = if (theme.surface.alpha < 1f) theme.background else theme.surface
        Box(modifier.background(bg, RoundedCornerShape(24.dp)).padding(20.dp)) { content() }
    }

    private val devices = listOf(
        BtDevice("AA:01", "Vikash ka Redmi", connected = true, phone = true, music = true, calls = true, battery = 72),
        BtDevice("AA:02", "Papa ka Vivo", connected = false, phone = true),
        BtDevice("AA:03", "OBDII", connected = false, phone = false),
    )
    private val contacts = listOf(FavouriteContact("Maa", "9810000001"), FavouriteContact("Papa", "9810000002"))

    private fun sheets() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Sheet(Modifier.weight(1f)) {
                        PhoneLinkContent(
                            phoneName = devices[0].name, connected = devices[0], devices = devices, btSupported = true,
                            hasPermission = true, switchingName = null, contacts = contacts, mirrorLabel = null,
                            onPlay = {}, onCall = {}, onAddFavourite = {}, onSwitch = {}, onBtSettings = {}, onAllow = {}, onMirror = {},
                        )
                    }
                    Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Sheet {
                            GetAmpliContent(showDownload = true, download = Mp3Download.Running(42), onPlay = {}, onDownload = {}, onOther = {})
                        }
                    }
                }
            }
        }
    }

    @Test
    fun sheets1024() {
        sheets()
        shot("unitfix-sheets-1024x600")
    }

    @Test
    @Config(qualifiers = "hi-w1280dp-h800dp-land-mdpi")
    fun sheets1280() {
        sheets()
        shot("unitfix-sheets-1280x800")
    }

    @Test
    fun phoneMusicCard() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Pane(Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.padding(16.dp)) { PhoneMusic("Vikash ka Redmi") {} }
                    }
                    Sheet(Modifier.weight(1f)) {
                        PhoneLinkContent(
                            phoneName = null, connected = null, devices = emptyList(), btSupported = true,
                            hasPermission = true, switchingName = "Papa ka Vivo", contacts = emptyList(), mirrorLabel = "phonelink",
                            onPlay = {}, onCall = {}, onAddFavourite = {}, onSwitch = {}, onBtSettings = {}, onAllow = {}, onMirror = {},
                        )
                    }
                    Sheet(Modifier.weight(1f)) {
                        GetAmpliContent(showDownload = false, download = Mp3Download.Idle, onPlay = {}, onDownload = {}, onOther = {})
                    }
                }
            }
        }
        shot("unitfix-phone-music-1024x600")
    }
}
