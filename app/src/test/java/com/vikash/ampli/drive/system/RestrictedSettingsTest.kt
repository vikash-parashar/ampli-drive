package com.vikash.ampli.drive.system

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.RestrictedSettingsGuideHost
import com.vikash.ampli.drive.ui.restrictedSteps
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Android 13+ restricted settings on a sideloaded APK: the guide must show before the switch and
 * again on return, even when the phone (OnePlus, OxygenOS) killed the app while the driver was in
 * Settings. Android 8.1 head units and Play installs keep going straight to the switch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en")
class RestrictedSettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application get() = RuntimeEnvironment.getApplication()

    private fun savedArmed(): Boolean =
        app.getSharedPreferences("restricted_settings", Context.MODE_PRIVATE).getBoolean("armed", false)

    @Before
    fun reset() {
        RestrictedSettings.disarm(app)
    }

    @After
    fun cleanUp() {
        RestrictedSettings.disarm(app)
    }

    @Test
    fun sideloadedOnAndroid13MayBlock() {
        assertTrue(RestrictedSettings.mayBlock(app))
    }

    @Test
    fun playInstallIsNotBlocked() {
        shadowOf(app.packageManager).setInstallSourceInfo(app.packageName, "com.android.vending", "com.android.vending")
        assertFalse(RestrictedSettings.mayBlock(app))
        RestrictedSettings.arm(app)
        assertFalse(RestrictedSettings.armed.value)
    }

    @Test
    fun otherInstallerIsBlocked() {
        shadowOf(app.packageManager).setInstallSourceInfo(app.packageName, "com.oplus.filemanager", "com.android.packageinstaller")
        assertTrue(RestrictedSettings.mayBlock(app))
    }

    @Test
    fun armIsSavedForWhenTheAppIsRestarted() {
        RestrictedSettings.arm(app)
        assertTrue(RestrictedSettings.armed.value)
        assertTrue(savedArmed())
        RestrictedSettings.disarm(app)
        assertFalse(savedArmed())
    }

    @Test
    fun restoreReadsTheSavedFlag() {
        // A new process starts with the in-memory flag off; the saved one brings the guide back.
        app.getSharedPreferences("restricted_settings", Context.MODE_PRIVATE).edit().putBoolean("armed", true).commit()
        RestrictedSettings.restore(app)
        assertTrue(RestrictedSettings.armed.value)
    }

    @Test
    fun allowShowsTheStepsFirst() {
        Launchers.allowNotificationAccess(app)
        assertTrue(RestrictedSettings.before.value)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun openingTheSwitchArmsAndHidesTheStepsBefore() {
        RestrictedSettings.showBefore()
        Launchers.openNotificationAccess(app)
        assertFalse(RestrictedSettings.before.value)
        assertTrue(RestrictedSettings.armed.value)
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    @Config(sdk = [27])
    fun headUnitGoesStraightToTheSwitch() {
        assertFalse(RestrictedSettings.applies)
        Launchers.allowNotificationAccess(app)
        assertFalse(RestrictedSettings.before.value)
        assertFalse(RestrictedSettings.armed.value)
        assertFalse(savedArmed())
        assertTrue(shadowOf(app).nextStartedActivity.action!!.startsWith("android.settings.NOTIFICATION_LISTENER"))
    }

    @Test
    fun stepsStartWithTheRefusedTry() {
        // Android offers ⋮ › Allow restricted settings only after one refused try.
        assertEquals(
            listOf(R.string.restricted_step_try, R.string.restricted_step_1, R.string.restricted_step_2, R.string.restricted_step_3),
            restrictedSteps(),
        )
    }

    @Test
    fun guideShowsAfterTheAppWasRestartedInSettings() {
        app.getSharedPreferences("restricted_settings", Context.MODE_PRIVATE).edit().putBoolean("armed", true).commit()
        compose.setContent { RestrictedSettingsGuideHost(resumeTick = 0) }
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.restricted_title)).assertExists()
    }

    @Test
    fun guideShowsBeforeTheSwitch() {
        compose.setContent { RestrictedSettingsGuideHost(resumeTick = 0) }
        RestrictedSettings.showBefore()
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.restricted_before_title)).assertExists()
        compose.onNodeWithText(app.getString(R.string.restricted_step_try)).assertExists()
    }

    @Test
    fun nothingShowsWhenNotArmed() {
        compose.setContent { RestrictedSettingsGuideHost(resumeTick = 0) }
        compose.waitForIdle()
        compose.onNodeWithText(app.getString(R.string.restricted_title)).assertDoesNotExist()
    }
}
