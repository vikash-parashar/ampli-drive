package com.vikash.ampli.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vikash.ampli.drive.car.CarMonitorService
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.Screen
import com.vikash.ampli.drive.voice.VoiceService
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
        startSensors()
        app.car.onForeground()
        // Keeps the drive monitored while Google Maps or another app is in front.
        if (app.car.store.settings.value.backgroundMonitor) CarMonitorService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // Notification access may have just been granted in Settings.
        app.mediaHub.start()
        resumeTick.intValue++
        // "Hey Ampli" wake word. A microphone service may only start while we are in front,
        // which as the HOME app includes right after boot (see VoiceService).
        VoiceService.start(this)
    }

    override fun onStop() {
        // Only GPS and the motion sensor keep running while the launcher is hidden, and only
        // when the drive monitor service is on: keeps 2 GB units responsive.
        if (!CarMonitorService.running) app.speedTracker.stop()
        app.car.onBackground()
        app.mediaHub.stop()
        app.bluetooth.stop()
        super.onStop()
    }

    /** Pressing Home while the launcher is open returns to the main screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen.value = Screen.Home
        handleVoiceIntent(intent)
    }

    /** The assistant brings the launcher forward for the place list or the Help panel. */
    private fun handleVoiceIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_VOICE_ACTION)) {
            VOICE_OPEN_HELP -> screen.value = Screen.Help
            VOICE_CRASH -> screen.value = Screen.Crash
            null -> Unit
            else -> screen.value = Screen.Home
        }
        intent?.removeExtra(EXTRA_VOICE_ACTION)
    }

    /** Steering-wheel voice key or a search key, when the head unit passes it to the home app. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (event.repeatCount == 0) app.voice.start()
            return true
        }
        return super.onKeyDown(keyCode, event)
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
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
        )
    }

    companion object {
        const val EXTRA_VOICE_ACTION = "com.vikash.ampli.drive.VOICE_ACTION"
        const val VOICE_OPEN_HELP = "help"
        const val VOICE_CRASH = "crash"
    }
}
