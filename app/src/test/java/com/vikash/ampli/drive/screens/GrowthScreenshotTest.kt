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
import com.vikash.ampli.drive.tripcost.TripCostMath
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.TripCostCard
import com.vikash.ampli.drive.ui.WhatsAppDisclosureDialog
import com.vikash.ampli.drive.ui.WhatsAppReadCardContent
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
 * Previews of the growth features at 1024x600 in Hindi, like [CarHubScreenshotTest]: the WhatsApp
 * read-aloud disclosure and Settings card, and the (flag-off) trip cost card with sample numbers.
 * Saved to app/build/outputs/roborazzi/growth-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class GrowthScreenshotTest {

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

    @Test
    fun whatsAppDisclosure() {
        compose.setContent {
            Frame { WhatsAppDisclosureDialog(onAgree = {}, onDecline = {}) }
        }
        compose.waitForIdle()
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/growth-whatsapp-disclosure.png")
    }

    /** Left: off (Pro); middle: locked (no Pro); right: on, groups off, notification access missing. */
    @Test
    fun whatsAppSettings() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(12f))) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dp(12f))) {
                        WhatsAppReadCardContent(isPro = true, enabled = false, allowGroups = false, hasAccess = true, onToggle = {}, onGroups = {}, onAllowAccess = {})
                        WhatsAppReadCardContent(isPro = false, enabled = false, allowGroups = false, hasAccess = true, onToggle = {}, onGroups = {}, onAllowAccess = {})
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        WhatsAppReadCardContent(isPro = true, enabled = true, allowGroups = false, hasAccess = false, onToggle = {}, onGroups = {}, onAllowAccess = {})
                    }
                }
            }
        }
        shot("growth-whatsapp-settings")
    }

    @Test
    fun tripCostExample() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(16f))) {
                    TripCostCard(cost = TripCostMath.example(), moving = false, modifier = Modifier.weight(1.3f))
                    Box(Modifier.weight(1f))
                }
            }
        }
        shot("growth-trip-cost-example")
    }
}
