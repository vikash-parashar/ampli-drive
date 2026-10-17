package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import android.app.Activity
import android.os.Build
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
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.ui.theme.AmpliDriveTheme
import com.vikash.ampli.drive.ui.theme.DriveThemes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

enum class Screen {
    Home, Apps, Settings,

    /** Help panel; [Sos] is the same panel opened straight into the SOS countdown. */
    Help, Sos, Fastag, Emergency,

    /** Car hub and its screens; [Crash] is the SOS countdown started by crash detection. */
    Car, Parked, Papers, Expenses, Obd, Tyres, Crash,

    /** Settings › Help & feedback (also "Hey Ampli, report a problem"). */
    Feedback,

    /** Settings › Voice › Check voice, and Settings › About › Diagnostics. */
    VoiceCheck, Diagnostics,

    /** Car Hub's own turn-by-turn, and its "Where to?" search. */
    Navigation, NavSearch,
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
    val chosen = DriveThemes.resolve(themeChoice, night, pro.isPro)
    // Glass themes blur the backdrop behind panels, except with "Reduce glass effects" (on by
    // default on 2 GB units) or below Android 12L (API 32): there is no RenderEffect blur before
    // Android 12, and on 12.0 RenderNode blur is unreliable. Without
    // blur they switch to a stronger tint so text stays readable.
    val reduceGlass by app.prefs.reduceGlass.collectAsStateWithLifecycle()
    val glassBlur = chosen.glass != null && !reduceGlass && Build.VERSION.SDK_INT >= 32
    val theme = if (chosen.glass != null && !glassBlur) chosen.withoutBlur() else chosen
    val haze = remember { HazeState() }
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
      Box(Modifier.fillMaxSize()) {
        // The backdrop is its own layer so glass panels can blur it (hazeSource must come first
        // so the background drawing is recorded into the layer).
        Box(
            Modifier
                .fillMaxSize()
                .then(if (glassBlur) Modifier.hazeSource(haze) else Modifier)
                .themeBackground(theme),
        )
        Box(
            Modifier
                .fillMaxSize()
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
                CompositionLocalProvider(LocalDims provides dims, LocalGlassHaze provides if (glassBlur) haze else null) {
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
                                onOpenNavScreen = { onNavigate(Screen.Navigation) },
                                onOpenNavSearch = { onNavigate(Screen.NavSearch) },
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
                                onOpenFeedback = { onNavigate(Screen.Feedback) },
                                onOpenVoiceCheck = { onNavigate(Screen.VoiceCheck) },
                                onOpenDiagnostics = { onNavigate(Screen.Diagnostics) },
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
                        Screen.Feedback -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Settings) }
                            FeedbackScreen(app = app, onBack = { onNavigate(Screen.Settings) })
                        }
                        Screen.VoiceCheck -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Settings) }
                            VoiceCheckScreen(app = app, onBack = { onNavigate(Screen.Settings) })
                        }
                        Screen.Diagnostics -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Settings) }
                            DiagnosticsScreen(app = app, onBack = { onNavigate(Screen.Settings) })
                        }
                        Screen.Navigation -> {
                            // Back leaves the trip running; the home screen shows its card.
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            NavigationScreen(app = app, onBack = { onNavigate(Screen.Home) })
                        }
                        Screen.NavSearch -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            NavSearchScreen(app = app, onBack = { onNavigate(Screen.Home) })
                        }
                        Screen.Emergency -> {
                            BackHandler(enabled = true) { onNavigate(emergencyReturn) }
                            EmergencyProfileScreen(app = app, onBack = { onNavigate(emergencyReturn) })
                        }
                    }
                    if (showPro) ProUnlockSheet(app = app, onDismiss = { showPro = false })
                    // "Which app is your music / phone app?" asked by the dock, Help panel or voice.
                    val chooser by AppChooser.request.collectAsStateWithLifecycle()
                    chooser?.let { req ->
                        val context = androidx.compose.ui.platform.LocalContext.current
                        AppChooserDialog(
                            role = req.role,
                            onPick = { entry ->
                                AppChooser.done()
                                when (req.role) {
                                    AppRole.Music -> app.prefs.setMusicPackage(entry?.packageName)
                                    AppRole.Phone -> app.prefs.setPhonePackage(entry?.packageName)
                                    AppRole.Mirror -> app.prefs.setAutoStart(app.prefs.autoStart.value.copy(mirrorPackage = entry?.packageName))
                                    AppRole.Nav -> app.prefs.setAutoStart(app.prefs.autoStart.value.copy(navPackage = entry?.packageName))
                                }
                                // Dock / Help panel: open the app right away. Settings choices just save.
                                if (entry != null && (req.role == AppRole.Music || req.role == AppRole.Phone) && !req.settingOnly) {
                                    AppResolver.openChosen(context, entry, req.number)
                                }
                            },
                            onDismiss = { AppChooser.done() },
                        )
                    }
                    // First run: "Thara naam ke sai?" over Home; the location disclosure waits for it.
                    val nameAsked by app.prefs.nameAsked.collectAsStateWithLifecycle()
                    if (!nameAsked && screen == Screen.Home) {
                        FirstRunNameHost(app = app)
                    } else {
                        BackgroundDisclosureHost(app = app, resumeTick = resumeTick)
                    }
                    // "Ram Ram {name} Ji 🙏" while the car-start welcome plays.
                    WelcomeCardHost(app = app)
                    // A trip started (voice, search, retry): show the navigation screen, unless the
                    // SOS countdown is up.
                    val navOpen by app.navigator.openRequest.collectAsStateWithLifecycle()
                    LaunchedEffect(navOpen) {
                        if (navOpen != 0) {
                            app.navigator.consumeOpenRequest()
                            if (screen != Screen.Crash && screen != Screen.Sos) onNavigate(Screen.Navigation)
                        }
                    }
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
}

/**
 * Background alerts (on by default) keep using location after the driver leaves the launcher,
 * so Play's prominent disclosure is shown once, after location is allowed and before the drive
 * monitor first starts. "No thanks" switches background alerts off; Settings › Car turns them
 * back on (and shows this again).
 */
@Composable
private fun BackgroundDisclosureHost(app: DriveApp, resumeTick: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    val accepted by app.prefs.bgDisclosureAccepted.collectAsStateWithLifecycle()
    val hasFine = remember(resumeTick) {
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (!settings.backgroundMonitor || accepted || !hasFine) return
    DisclosureDialog(
        kind = Disclosure.BackgroundLocation,
        onAgree = {
            app.prefs.setBgDisclosureAccepted(true)
            com.vikash.ampli.drive.car.CarMonitorService.start(context)
        },
        onDecline = {
            app.car.store.updateSettings { it.copy(backgroundMonitor = false) }
            app.car.applySettings()
        },
    )
}
