package com.vikash.ampli.drive.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** What the power layer over Car Hub shows. */
sealed interface PowerUi {
    data object None : PowerUi

    /** Screen off and the screensaver (from the top strip's key or Settings). */
    data object Sheet : PowerUi

    /** [left] of [total] seconds before Screen off, started by voice ("radd karo" stops it). */
    data class Countdown(val action: PowerAction, val total: Int, val left: Int) : PowerUi

    /** Black cover; a touch wakes. */
    data object ScreenOff : PowerUi

    /** The clock screensaver; a touch returns. */
    data object Screensaver : PowerUi
}

/**
 * Screen off and the screensaver for the Power sheet, the quick panel, Settings and voice. Car Hub
 * never restarts or powers off the unit. Main thread.
 */
object PowerControl {
    private val scope = MainScope()
    private var job: Job? = null

    private val _state = MutableStateFlow<PowerUi>(PowerUi.None)
    val state: StateFlow<PowerUi> = _state.asStateFlow()

    private val rootAvailable: Boolean by lazy {
        try {
            PowerPlan.hasSu { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    /** su exists on this unit (the volume keys fall back to `input keyevent` with it). */
    fun rooted(): Boolean = rootAvailable

    fun openSheet() {
        cancelJob()
        _state.value = PowerUi.Sheet
    }

    fun close() {
        cancelJob()
        _state.value = PowerUi.None
    }

    val busy: Boolean get() = _state.value != PowerUi.None

    /** A tap on Screen off acts at once; by voice ([voiceSeconds]) a short countdown comes first. */
    fun request(context: Context, action: PowerAction, voiceSeconds: Int? = null) {
        cancelJob()
        val app = context.applicationContext
        when (action) {
            PowerAction.ScreenOff ->
                if (voiceSeconds != null && voiceSeconds > 0) countdown(app, action, voiceSeconds) else screenOff(app)
        }
    }

    /** "Do it now" on the countdown. */
    fun now(context: Context) {
        if (_state.value !is PowerUi.Countdown) return
        cancelJob()
        screenOff(context.applicationContext)
    }

    fun screensaver() {
        cancelJob()
        _state.value = PowerUi.Screensaver
    }

    /** A touch on the black cover or the screensaver. */
    fun wake() {
        if (_state.value == PowerUi.ScreenOff || _state.value == PowerUi.Screensaver) _state.value = PowerUi.None
    }

    private fun countdown(app: Context, action: PowerAction, total: Int) {
        cancelJob()
        _state.value = PowerUi.Countdown(action, total, total)
        job = scope.launch {
            val start = android.os.SystemClock.elapsedRealtime()
            while (true) {
                val left = PowerPlan.secondsLeft(total, android.os.SystemClock.elapsedRealtime() - start)
                val now = _state.value as? PowerUi.Countdown ?: return@launch
                if (now.left != left) _state.value = now.copy(left = left)
                if (left <= 0) break
                // Wake on the next whole second only: one redraw per second.
                delay(1_000L - (android.os.SystemClock.elapsedRealtime() - start) % 1_000L)
            }
            job = null
            screenOff(app)
        }
    }

    // ---------------------------------------------------------------- Screen off

    fun adminComponent(context: Context) = ComponentName(context, AmpliDeviceAdmin::class.java)

    fun adminActive(context: Context): Boolean = try {
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(adminComponent(context)) == true
    } catch (_: Exception) {
        false
    }

    /** Android's own "Activate device admin app?" screen, with Ampli's reason. */
    fun adminRequestIntent(context: Context, reason: String): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, reason)

    /** Settings switch off: give the grant back to Android. */
    fun removeAdmin(context: Context) {
        try {
            context.getSystemService(DevicePolicyManager::class.java)?.removeActiveAdmin(adminComponent(context))
        } catch (_: Exception) {
        }
    }

    private fun screenOff(app: Context) {
        cancelJob()
        val wanted = (app as? DriveApp)?.shell?.lockScreenOff?.value == true
        if (PowerPlan.screenOffRoute(adminActive(app), wanted) == ScreenOffRoute.DeviceAdmin) {
            val locked = try {
                app.getSystemService(DevicePolicyManager::class.java)?.lockNow()
                true
            } catch (_: Exception) {
                false
            }
            if (locked) {
                _state.value = PowerUi.None
                return
            }
        }
        _state.value = PowerUi.ScreenOff
    }

    private fun cancelJob() {
        job?.cancel()
        job = null
    }
}
