package com.vikash.ampli.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.media.AudioManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vikash.ampli.drive.emergency.Sos
import com.vikash.ampli.drive.emergency.SosNotifier
import com.vikash.ampli.drive.car.CarMonitorService
import com.vikash.ampli.drive.car.CarStart
import com.vikash.ampli.drive.car.StartTrigger
import com.vikash.ampli.drive.voice.WakeModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.voice.VoiceKeys
import com.vikash.ampli.drive.voice.VoiceService
import com.vikash.ampli.drive.system.BtPermissions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app: DriveApp get() = application as DriveApp

    /** Current screen. The activity is never recreated on rotation/resize (configChanges). */
    private val screen = mutableStateOf(Screen.Home)

    /** Bumped on every return to the foreground so status texts re-read permissions. */
    private val resumeTick = mutableIntStateOf(0)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startSensors()
            resumeTick.intValue++
        }

    /** Android 12 has no per-app language API: apply the saved language here. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguages.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Re-checks Play for the Pro purchase (refunds, purchases on another device).
        app.billing.start()
        // Hooks the car features (break timer, odometer, parking, crash) to the GPS fixes.
        app.car
        // Speaks in-app navigation turns, and Google Maps' when Settings › Haryanvi navigation prompts is on.
        app.navPrompter.start()
        app.desi.start()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.prefs.keepScreenOn.collect { on ->
                    if (on) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        // Settings › Full screen: hide the status and navigation bars (a swipe brings them back).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.shell.fullScreen.collect { applyFullScreen(it) }
            }
        }

        // Car-start actions (greeting, auto start). They run in lifecycleScope, not tied to
        // RESUMED, because opening the map or the mirroring app pauses this activity mid-way.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                CarStart.events.collect { trigger -> lifecycleScope.launch { CarStart.run(this@MainActivity, app, trigger) } }
            }
        }
        // The wake word starts as soon as its model has downloaded, wherever the driver is.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.wakeModel.state.collect { if (it == WakeModel.State.Ready) VoiceService.start(this@MainActivity) }
            }
        }
        // "When the car starts" can also run when the chosen phone connects over Bluetooth.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.bluetooth.state.map { it.connectedName }.distinctUntilChanged().collect { name ->
                    val wanted = app.prefs.autoStart.value.btDeviceName
                    if (name != null && wanted != null && name == wanted) CarStart.signal(app, StartTrigger.PhoneConnected)
                }
            }
        }

        setContent {
            DriveRoot(
                app = app,
                screen = screen.value,
                resumeTick = resumeTick.intValue,
                onNavigate = { screen.value = it },
                onRequestPermissions = ::askPermissions,
            )
        }

        handleVoiceIntent(intent)

        if (!app.prefs.askedPermissions) {
            app.prefs.askedPermissions = true
            askPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        inFront = true
        startSensors()
        app.car.onForeground()
        // Keeps the drive monitored while Google Maps or another app is in front.
        if (app.car.store.settings.value.backgroundMonitor) CarMonitorService.start(this)
        // Help & feedback reports waiting from an offline moment go out once online.
        app.feedback.watchNetwork()
    }

    override fun onResume() {
        super.onResume()
        // Notification access may have just been granted in Settings.
        app.mediaHub.start()
        resumeTick.intValue++
        // "Hey Ampli" wake word. A microphone service may only start while we are in front,
        // which as the HOME app includes right after boot (see VoiceService). A missing model
        // starts downloading here too, so a failed download is retried on every return home.
        VoiceService.start(this)
        // Boot (or a start noticed while hidden): greeting and auto start, from the foreground.
        CarStart.launcherVisible = true
        CarStart.takePending(this, app)?.let { trigger -> lifecycleScope.launch { CarStart.run(this@MainActivity, app, trigger) } }
    }

    override fun onPause() {
        CarStart.launcherVisible = false
        super.onPause()
    }

    override fun onStop() {
        // Only GPS and the motion sensor keep running while the launcher is hidden, and only
        // when the drive monitor service is on: keeps 2 GB units responsive.
        if (!CarMonitorService.running) app.speedTracker.stop()
        app.car.onBackground()
        app.mediaHub.stop()
        app.bluetooth.stop()
        inFront = false
        super.onStop()
    }

    /**
     * Pressing Home while the launcher is open returns to the main screen, except during a crash
     * countdown: a brushed Home key or a vendor ignition event must not hide the SOS countdown.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen.value = screenAfterHome(app.car.crashAt.value != null)
        handleVoiceIntent(intent)
    }

    /** The screen in front, for tests. */
    internal val currentScreen: Screen get() = screen.value

    /** The assistant brings the launcher forward for the place list or the Help panel. */
    private fun handleVoiceIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_VOICE_ACTION)) {
            VOICE_OPEN_HELP -> screen.value = Screen.Help
            VOICE_CRASH -> {
                screen.value = Screen.Crash
                // From the crash SOS alert: the countdown ended while another app was in front.
                // Now Car Hub is in front, so the composer may open (the driver still taps Send).
                if (intent?.getBooleanExtra(SosNotifier.EXTRA_OPEN_SMS, false) == true) {
                    intent.removeExtra(SosNotifier.EXTRA_OPEN_SMS)
                    SosNotifier.cancel(this)
                    app.car.crashSos.value?.let { message ->
                        Sos.openSmsComposer(this, app.emergency.profile.value.contacts.map { it.phone }, message)
                    }
                }
            }
            // The form itself waits until the car is stopped.
            VOICE_FEEDBACK -> screen.value = Screen.Feedback
            // "Hey Ampli, <place> le chal" with the Ampli map: Car Hub's own navigation.
            VOICE_NAVIGATE -> screen.value = Screen.Navigation
            null -> Unit
            else -> screen.value = Screen.Home
        }
        intent?.removeExtra(EXTRA_VOICE_ACTION)
    }

    /**
     * Steering-wheel voice key or a search key, when the head unit passes it to the home app.
     * A long press of the headset-hook or play/pause key also starts listening; a short press
     * is handed on to the music player as usual. (With another app in front, VoiceKeyActivity
     * answers the assist intents those keys turn into.)
     */
    private var voiceLongPressed = false

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (VoiceKeys.isVoiceKey(keyCode)) {
            if (event.repeatCount == 0) app.voice.start()
            return true
        }
        if (VoiceKeys.isLongPressVoiceKey(keyCode)) {
            if (event.repeatCount == 0) {
                voiceLongPressed = false
                event.startTracking()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (VoiceKeys.isLongPressVoiceKey(keyCode)) {
            voiceLongPressed = true
            app.voice.start()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (VoiceKeys.isLongPressVoiceKey(keyCode)) {
            if (event.isTracking && !event.isCanceled && !voiceLongPressed) {
                // Short press: play/pause the music as if we had never looked at it.
                val am = getSystemService(AudioManager::class.java)
                am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and other apps can bring the bars back; hide them again on return.
        if (hasFocus) applyFullScreen(app.shell.fullScreen.value)
    }

    private fun applyFullScreen(on: Boolean) {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (on) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (_: Exception) {
            // Some head-unit ROMs have no navigation bar to hide.
        }
    }

    private fun startSensors() {
        app.speedTracker.start()
        app.bluetooth.start()
    }

    private fun askPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                BtPermissions.connect,
            ),
        )
    }

    companion object {
        /** Where the HOME key leads: the main screen, or the crash countdown while one runs. */
        fun screenAfterHome(crashRunning: Boolean): Screen = if (crashRunning) Screen.Crash else Screen.Home

        /** True while this activity is started (visible), so activity starts are allowed. */
        @Volatile
        var inFront: Boolean = false
            private set

        const val EXTRA_VOICE_ACTION = "com.vikash.ampli.drive.VOICE_ACTION"
        const val VOICE_OPEN_HELP = "help"
        const val VOICE_CRASH = "crash"
        const val VOICE_FEEDBACK = "feedback"
        const val VOICE_NAVIGATE = "navigate"
    }
}
