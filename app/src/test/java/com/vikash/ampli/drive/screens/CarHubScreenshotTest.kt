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
import com.vikash.ampli.drive.car.PaperKind
import com.vikash.ampli.drive.fastag.FastagParser
import com.vikash.ampli.drive.ui.AnnualPassCard
import com.vikash.ampli.drive.ui.Dims
import com.vikash.ampli.drive.ui.Disclosure
import com.vikash.ampli.drive.ui.DisclosureDialog
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.FastagSettingsCard
import com.vikash.ampli.drive.ui.LocalDims
import com.vikash.ampli.drive.ui.PapersReminderCard
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.ui.VoiceSettingsCard
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
import java.time.LocalDate
import java.time.ZoneId

/**
 * Previews of the Car Hub hardening screens (FASTag Annual Pass, SMS disclosure, paper reminders,
 * challan, offline Hindi model) at 1024x600 in Hindi, like [ScreenshotTest]. Saved to
 * app/build/outputs/roborazzi/carhub-*.png; nothing is compared.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], qualifiers = "hi-w1024dp-h600dp-land-mdpi")
class CarHubScreenshotTest {

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

    /** A pass bought 40 days ago, a few tolls, and the bank's "152 trips left". */
    private fun seedFastag() {
        val store = app.fastag
        val today = LocalDate.now()
        store.setPassStart(today.minusDays(40))
        fun at(daysAgo: Long, hour: Int) = today.minusDays(daysAgo).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sms = listOf(
            at(30, 9) to "Rs 145 debited from your FASTag for HR26AB1234 at Kherki Daula toll plaza. Avl Bal Rs 1,240.50",
            at(3, 18) to "Dear Customer, Annual Pass trip availed at Panipat Toll Plaza for HR26AB1234. Remaining trips: 153. -IHMCL",
            at(0, 8) to "Dear Customer, Annual Pass trip availed at Ghaggar Toll Plaza for HR26AB1234. Remaining trips: 152. -IHMCL",
        )
        for ((t, text) in sms) FastagParser.parse(text, "VM-NHAIFT")?.let { store.add(it, t, announce = false) }
        store.adjustPassTrips(+1, at(0, 9))
    }

    @Test
    fun fastagAnnualPass() {
        app.prefs.setNameAsked()
        seedFastag()
        compose.setContent { DriveRoot(app = app, screen = Screen.Fastag, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        shot("carhub-fastag-annual-pass")
    }

    @Test
    fun annualPassStates() {
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(16f))) {
                    Column(Modifier.weight(1f)) { AnnualPassCard(app) }
                    Box(Modifier.weight(1f))
                }
            }
        }
        shot("carhub-annual-pass-empty")
    }

    @Test
    fun settingsCards() {
        app.prefs.setNameAsked()
        app.car.store.setPaper(PaperKind.Insurance, LocalDate.now().plusDays(5))
        app.car.store.setPaper(PaperKind.Puc, LocalDate.now().minusDays(2))
        compose.setContent {
            Frame {
                Row(Modifier.fillMaxSize().padding(Dp(16f)), horizontalArrangement = Arrangement.spacedBy(Dp(16f))) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dp(12f))) {
                        FastagSettingsCard(app = app, onOpenFastag = {})
                        PapersReminderCard(app = app)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        VoiceSettingsCard(app = app, resumeTick = 0)
                    }
                }
            }
        }
        shot("carhub-settings-fastag-papers-voice")
    }

    @Test
    fun papersWithChallan() {
        app.prefs.setNameAsked()
        app.car.store.setPaper(PaperKind.Insurance, LocalDate.now().plusDays(5))
        compose.setContent { DriveRoot(app = app, screen = Screen.Papers, resumeTick = 0, onNavigate = {}, onRequestPermissions = {}) }
        shot("carhub-papers-challan")
    }

    @Test
    fun fastagSmsDisclosure() {
        compose.setContent {
            Frame {
                DisclosureDialog(kind = Disclosure.FastagSms, onAgree = {}, onDecline = {})
            }
        }
        compose.waitForIdle()
        @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/carhub-fastag-sms-disclosure.png")
    }
}
