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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.tripcost.TripCostMath
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.TripCostCard
import com.vikash.ampli.drive.ui.WhatsAppDisclosureDialog
import com.vikash.ampli.drive.ui.WhatsAppReadCardContent
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import com.vikash.ampli.drive.ui.themeBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /**
     * Left: a unit with no speech engine (toggle off and disabled, with the reason); right: the
     * car is moving, so switching on waits for a stop.
     */
    @Test
    fun whatsAppSettingsTtsMissing() {
        compose.setContent { WhatsAppTtsAndMovingCards() }
        shot("growth-whatsapp-tts-missing")
    }

    /** The same card in German: a non-Hindi locale with the new strings. */
    @Test
    @Config(qualifiers = "de-w1024dp-h600dp-land-mdpi")
    fun whatsAppSettingsGerman() {
        compose.setContent { WhatsAppTtsAndMovingCards() }
        shot("growth-whatsapp-settings-de")
    }

    /** P4-6: no speech engine, so a tap on the card cannot switch it on. */
    @Test
    fun whatsAppToggleStaysOffWithoutTts() = assertToggleLocked(ttsMissing = true, moving = false)

    /** P4-9: switching on waits for a parked car ("Park to switch on"). */
    @Test
    fun whatsAppToggleStaysOffWhileMoving() = assertToggleLocked(ttsMissing = false, moving = true)

    /** Switching off still works while moving. */
    @Test
    fun whatsAppToggleTurnsOffWhileMoving() {
        val calls = mutableListOf<Boolean>()
        compose.setContent {
            Frame {
                WhatsAppReadCardContent(
                    isPro = true, enabled = true, allowGroups = false, hasAccess = true,
                    onToggle = { calls += it }, onGroups = {}, onAllowAccess = {}, moving = true,
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.wa_title)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(false), calls)
    }

    /** The notification-access button waits for a parked car too. */
    @Test
    fun whatsAppAccessButtonWaitsForParkedCar() {
        var opened = 0
        compose.setContent {
            Frame {
                WhatsAppReadCardContent(
                    isPro = true, enabled = true, allowGroups = false, hasAccess = false,
                    onToggle = {}, onGroups = {}, onAllowAccess = { opened++ }, moving = true,
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.park_to_set)).performClick()
        compose.waitForIdle()
        assertEquals(0, opened)
    }

    private fun assertToggleLocked(ttsMissing: Boolean, moving: Boolean) {
        val calls = mutableListOf<Boolean>()
        compose.setContent {
            Frame {
                WhatsAppReadCardContent(
                    isPro = true, enabled = false, allowGroups = false, hasAccess = true,
                    onToggle = { calls += it }, onGroups = {}, onAllowAccess = {},
                    ttsMissing = ttsMissing, moving = moving,
                )
            }
        }
        compose.onNodeWithText(app.getString(R.string.wa_title)).performClick()
        compose.waitForIdle()
        assertTrue("toggle switched on (ttsMissing=$ttsMissing, moving=$moving): $calls", calls.isEmpty())
        val hint = if (ttsMissing) R.string.wa_tts_missing else R.string.wa_park_to_turn_on
        compose.onNodeWithText(app.getString(hint)).assertExists()
    }

    @Composable
    private fun WhatsAppTtsAndMovingCards() {
        Frame {
            Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(12f))) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    WhatsAppReadCardContent(isPro = true, enabled = false, allowGroups = false, hasAccess = true, onToggle = {}, onGroups = {}, onAllowAccess = {}, ttsMissing = true)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    WhatsAppReadCardContent(isPro = true, enabled = false, allowGroups = false, hasAccess = true, onToggle = {}, onGroups = {}, onAllowAccess = {}, moving = true)
                }
            }
        }
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
