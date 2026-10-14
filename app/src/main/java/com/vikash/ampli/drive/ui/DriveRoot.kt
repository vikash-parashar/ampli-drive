package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.location.SunTimes
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Screen {
    Home, Apps, Settings,

    /** Help panel; [Sos] is the same panel opened straight into the SOS countdown. */
    Help, Sos, Fastag, Emergency,

    /** Car hub and its screens; [Crash] is the SOS countdown started by crash detection. */
    Car, Parked, Papers, Expenses, Obd, Tyres, Crash,
}

@Composable
fun DriveRoot(
    app: DriveApp,
    screen: Screen,
    resumeTick: Int,
    onNavigate: (Screen) -> Unit,
    onRequestPermissions: () -> Unit,
) {
    val themeChoice by app.prefs.theme.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val gpsState = app.speedTracker.state.collectAsStateWithLifecycle()

    // Only a coarse position matters for sunrise/sunset; avoids recomposing on every fix.
    val coarse by remember {
        derivedStateOf {
            gpsState.value.location?.let {
                Pair((it.latitude * 10).roundToInt() / 10.0, (it.longitude * 10).roundToInt() / 10.0)
            }
        }
    }
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    // Sun position decides day/night; before the first GPS fix, the clock does.
    val night = coarse?.let { SunTimes.isNight(minute, it.first, it.second) }
        ?: SunTimes.isNightByClock(LocalTime.now())
    val theme = DriveThemes.resolve(themeChoice, night, pro.isPro)
    // Where Back goes from the emergency profile (opened from Settings or the Help panel).
    var emergencyReturn by remember { mutableStateOf(Screen.Settings) }
    // Where Back goes from Settings (Home, or the Car hub when opened from there).
    var settingsReturn by remember { mutableStateOf(Screen.Home) }
    var showPro by remember { mutableStateOf(false) }
    // A detected crash opens the SOS countdown over whatever is showing.
    val crashAt by app.car.crashAt.collectAsStateWithLifecycle()
    LaunchedEffect(crashAt) { if (crashAt != null && screen != Screen.Crash) onNavigate(Screen.Crash) }

    // Dark status-bar icons on the light themes.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = theme.isLight
            controller.isAppearanceLightNavigationBars = theme.isLight
        }
    }

    AmpliDriveTheme(theme) {
        Box(
            Modifier
                .fillMaxSize()
                .themeBackground(theme)
                .themeOverlay(theme)
                .safeDrawingPadding(),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val unit = maxHeight / 100f
                val dims = Dims(
                    u = unit,
                    uSp = with(density) { unit.toSp().value },
                    compact = maxWidth / maxHeight < 1.72f,
                )
                CompositionLocalProvider(LocalDims provides dims) {
                    when (screen) {
                        Screen.Home -> {
                            // A home screen has nowhere to go back to.
                            BackHandler(enabled = true) {}
                            HomeScreen(
                                app = app,
                                resumeTick = resumeTick,
                                onOpenApps = { onNavigate(Screen.Apps) },
                                onOpenSettings = { onNavigate(Screen.Settings) },
                                onOpenHelp = { sos -> onNavigate(if (sos) Screen.Sos else Screen.Help) },
                                onOpenFastag = { onNavigate(Screen.Fastag) },
                                onOpenCar = { onNavigate(Screen.Car) },
                                onOpenParked = { onNavigate(Screen.Parked) },
                                onRequestPermissions = onRequestPermissions,
                            )
                            TollToastHost(app = app, onOpenFastag = { onNavigate(Screen.Fastag) })
                            CarAlertHost(app = app)
                        }
                        Screen.Apps -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            AppsScreen(app = app, onBack = { onNavigate(Screen.Home) })
                        }
                        Screen.Settings -> {
                            BackHandler(enabled = true) { onNavigate(settingsReturn) }
                            SettingsScreen(
                                app = app,
                                night = night,
                                resumeTick = resumeTick,
                                onBack = { onNavigate(settingsReturn) },
                                onOpenCar = { onNavigate(Screen.Car) },
                                onRequestPermissions = onRequestPermissions,
                                onOpenFastag = { onNavigate(Screen.Fastag) },
                                onOpenEmergency = {
                                    emergencyReturn = Screen.Settings
                                    onNavigate(Screen.Emergency)
                                },
                            )
                        }
                        Screen.Help, Screen.Sos, Screen.Crash -> {
                            BackHandler(enabled = true) {
                                if (screen == Screen.Crash) app.car.clearCrash()
                                onNavigate(Screen.Home)
                            }
                            // A fresh panel per entry, so a crash always starts at the countdown.
                            key(screen) {
                                HelpScreen(
                                    app = app,
                                    startSos = screen == Screen.Sos || screen == Screen.Crash,
                                    crash = screen == Screen.Crash,
                                    onBack = { onNavigate(Screen.Home) },
                                    onEditProfile = {
                                        emergencyReturn = Screen.Help
                                        onNavigate(Screen.Emergency)
                                    },
                                )
                            }
                        }
                        Screen.Car -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            CarHubScreen(app = app, onBack = { onNavigate(Screen.Home) }, onOpen = { dest ->
                                when (dest) {
                                    CarDest.Parked -> onNavigate(Screen.Parked)
                                    CarDest.Papers -> onNavigate(Screen.Papers)
                                    CarDest.Expenses -> onNavigate(Screen.Expenses)
                                    CarDest.Obd -> onNavigate(Screen.Obd)
                                    CarDest.Tyres -> onNavigate(Screen.Tyres)
                                    CarDest.Settings -> {
                                        settingsReturn = Screen.Car
                                        onNavigate(Screen.Settings)
                                    }
                                }
                            })
                        }
                        Screen.Parked -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Car) }
                            ParkedScreen(app = app, onBack = { onNavigate(Screen.Car) })
                        }
                        Screen.Papers -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Car) }
                            PapersScreen(app = app, onBack = { onNavigate(Screen.Car) })
                        }
                        Screen.Expenses -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Car) }
                            ExpensesScreen(app = app, onBack = { onNavigate(Screen.Car) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Obd -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Car) }
                            ObdScreen(app = app, onBack = { onNavigate(Screen.Car) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Tyres -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Car) }
                            TyresScreen(app = app, onBack = { onNavigate(Screen.Car) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Fastag -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            FastagScreen(app = app, onBack = { onNavigate(Screen.Home) })
                        }
                        Screen.Emergency -> {
                            BackHandler(enabled = true) { onNavigate(emergencyReturn) }
                            EmergencyProfileScreen(app = app, onBack = { onNavigate(emergencyReturn) })
                        }
                    }
                    if (showPro) ProUnlockSheet(app = app, onDismiss = { showPro = false })
                    // Settings opened from the Home dock returns to Home.
                    LaunchedEffect(screen) { if (screen == Screen.Home) settingsReturn = Screen.Home }
                    // "Hey Ampli" sheet over whichever screen is showing.
                    val voice by app.voice.state.collectAsStateWithLifecycle()
                    if (voice.active) BackHandler(enabled = true) { app.voice.cancel() }
                    VoiceOverlay(state = voice, onCancel = app.voice::cancel, onChoose = app.voice::choose)
                }
            }
        }
    }
}
