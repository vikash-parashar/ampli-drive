package com.vikash.ampli.drive.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** What the power layer over Car Hub shows. */
sealed interface PowerUi {
    data object None : PowerUi

    /** Screen off, Restart, Power off (from ⏻ in the top strip or Settings). */
    data object Sheet : PowerUi

    /** First Restart / Power off without root or helper; [rooted]: the one-time root note instead. */
    data class Setup(val action: PowerAction, val rooted: Boolean) : PowerUi

    /** "Restart the screen?" asked by voice, with Yes / No keys too. */
    data class Confirm(val action: PowerAction) : PowerUi

    /** [left] of [total] seconds before [action]; [voice]: started by voice (Screen off says "radd karo"). */
    data class Countdown(val action: PowerAction, val total: Int, val left: Int, val voice: Boolean, val rooted: Boolean) : PowerUi

    /** Rooted unit: su is restarting or powering off. */
    data class Working(val action: PowerAction) : PowerUi

    /** Android's power menu is open: "Tap Restart here". */
    data class Pointer(val action: PowerAction) : PowerUi

    /** Nothing could do it on this unit. */
    data class Failed(val action: PowerAction) : PowerUi

    /** Black cover, backlight at its lowest; a touch wakes. */
    data object ScreenOff : PowerUi

    /** The clock screensaver; a touch returns. */
    data object Screensaver : PowerUi
}

/**
 * Screen off, Restart and Power off for the Power sheet, the quick panel, Settings and voice.
 * Restart and Power off always wait [PowerPlan.COUNTDOWN_S] seconds with a big Cancel, then try
 * root, then the "Ampli power helper" (Android's power menu). Main thread.
 */
object PowerControl {
    private val scope = MainScope()
    private var job: Job? = null

    private val _state = MutableStateFlow<PowerUi>(PowerUi.None)
    val state: StateFlow<PowerUi> = _state.asStateFlow()

    /** Setup card's "Turn on": come back to this action once the helper is on. */
    private var pendingAction: PowerAction? = null

    private val rootAvailable: Boolean by lazy {
        try {
            PowerPlan.hasSu { File(it).exists() }
        } catch (_: Exception) {
            false
        }
    }

    fun rooted(): Boolean = rootAvailable

    fun openSheet() {
        cancelJob()
        _state.value = PowerUi.Sheet
    }

    fun close() {
        cancelJob()
        pendingAction = null
        _state.value = PowerUi.None
    }

    val busy: Boolean get() = _state.value != PowerUi.None

    /** Voice: "restart karo" shows Yes / No first. */
    fun confirm(action: PowerAction) {
        cancelJob()
        _state.value = PowerUi.Confirm(action)
    }

    /**
     * A tap on Screen off, Restart or Power off (or "haan" after a voice question). Screen off acts
     * at once ([voiceSeconds]: a short countdown first); the others show the setup card or count down.
     */
    fun request(context: Context, action: PowerAction, voiceSeconds: Int? = null) {
        cancelJob()
        val app = context.applicationContext
        if (action == PowerAction.ScreenOff) {
            if (voiceSeconds != null && voiceSeconds > 0) countdown(app, action, voiceSeconds, voice = true) else screenOff(app)
            return
        }
        val shell = (app as? DriveApp)?.shell
        when (PowerPlan.firstStep(action, rootAvailable, PowerHelperService.enabled(app), shell?.rootNoticeSeen ?: true)) {
            PowerStep.Now -> screenOff(app)
            PowerStep.RootNotice -> _state.value = PowerUi.Setup(action, rooted = true)
            PowerStep.SetupHelper -> _state.value = PowerUi.Setup(action, rooted = false)
            PowerStep.Countdown -> countdown(app, action, PowerPlan.COUNTDOWN_S, voice = false)
        }
    }

    /** Setup card: "OK, continue" (rooted) or "Turn on" (opens Accessibility settings). */
    fun setupContinue(context: Context) {
        val s = _state.value as? PowerUi.Setup ?: return
        val app = context.applicationContext
        if (s.rooted) {
            (app as? DriveApp)?.shell?.rootNoticeSeen = true
            countdown(app, s.action, PowerPlan.COUNTDOWN_S, voice = false)
            return
        }
        pendingAction = s.action
        PowerHelperService.openSettings(context)
    }

    /** Back in Car Hub after the Accessibility settings: carry on when the helper is now on. */
    fun onResume(context: Context) {
        val action = pendingAction ?: return
        val s = _state.value
        if (s !is PowerUi.Setup || s.rooted) {
            pendingAction = null
            return
        }
        if (PowerHelperService.enabled(context)) {
            pendingAction = null
            countdown(context.applicationContext, action, PowerPlan.COUNTDOWN_S, voice = false)
        }
    }

    /** True while the setup card waits for the helper (the restricted-settings guide may help). */
    val waitingForHelper: Boolean get() = pendingAction != null && (_state.value as? PowerUi.Setup)?.rooted == false

    /** "Do it now" on the countdown. */
    fun now(context: Context) {
        val c = _state.value as? PowerUi.Countdown ?: return
        cancelJob()
        run(context.applicationContext, c.action)
    }

    fun screensaver() {
        cancelJob()
        _state.value = PowerUi.Screensaver
    }

    /** A touch on the black cover or the screensaver. */
    fun wake() {
        if (_state.value == PowerUi.ScreenOff || _state.value == PowerUi.Screensaver) _state.value = PowerUi.None
    }

    private fun countdown(app: Context, action: PowerAction, total: Int, voice: Boolean) {
        cancelJob()
        val rooted = rootAvailable && action != PowerAction.ScreenOff
        _state.value = PowerUi.Countdown(action, total, total, voice, rooted)
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
            run(app, action)
        }
    }

    private fun run(app: Context, action: PowerAction) {
        if (action == PowerAction.ScreenOff) {
            screenOff(app)
            return
        }
        job = scope.launch {
            for (route in PowerPlan.routes(rootAvailable, PowerHelperService.enabled(app))) {
                when (route) {
                    PowerRoute.Root -> {
                        _state.value = PowerUi.Working(action)
                        if (withContext(Dispatchers.IO) { runRoot(action) }) return@launch
                    }
                    PowerRoute.Helper -> {
                        if (PowerHelperService.powerDialog()) {
                            _state.value = PowerUi.Pointer(action)
                            delay(PowerPlan.POINTER_HINT_MS)
                            if (_state.value is PowerUi.Pointer) _state.value = PowerUi.None
                            return@launch
                        }
                    }
                }
            }
            _state.value = PowerUi.Failed(action)
        }
    }

    /** `su -c reboot` (or `reboot -p`). True when su accepted it (the unit is going down). */
    private fun runRoot(action: PowerAction): Boolean {
        val cmd = PowerPlan.rootCommand(action) ?: return false
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            if (!p.waitFor(PowerPlan.ROOT_WAIT_S, TimeUnit.SECONDS)) {
                p.destroy()
                false
            } else {
                p.exitValue() == 0
            }
        } catch (_: Exception) {
            false
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
