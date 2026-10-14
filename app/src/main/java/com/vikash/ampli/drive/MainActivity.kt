package com.vikash.ampli.drive

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.vikash.ampli.drive.i18n.AppLanguages
import com.vikash.ampli.drive.ui.DriveRoot
import com.vikash.ampli.drive.ui.Screen
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

        if (!app.prefs.askedPermissions) {
            app.prefs.askedPermissions = true
            askPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        startSensors()
    }

    override fun onResume() {
        super.onResume()
        // Notification access may have just been granted in Settings.
        app.mediaHub.start()
        resumeTick.intValue++
    }

    override fun onStop() {
        // Nothing runs while the launcher is hidden: keeps 2 GB units responsive.
        app.speedTracker.stop()
        app.mediaHub.stop()
        app.bluetooth.stop()
        super.onStop()
    }

    /** Pressing Home while the launcher is open returns to the main screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen.value = Screen.Home
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
}
