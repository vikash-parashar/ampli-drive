package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.location.SunTimes
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppResolver
import com.vikash.ampli.drive.system.AppRole
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
    Car, Parked, Papers, Expenses, Trips, Obd, Tyres, Crash,

    /** Ampli Store: Pro, the MP3 Player and voice packs (from Apps). */
    Store,

    /** Settings › Help & feedback (also "Hey Ampli, report a problem"). */
    Feedback,

    /** Settings › Voice › Check voice, and Settings › About › Diagnostics. */
    VoiceCheck, Diagnostics,

    /** Settings › Voice › What can I say? (also "Hey Ampli, what can I say?"). */
    VoiceCommands,

    /** Car Hub's own turn-by-turn, and its "Where to?" search. */
    Navigation, NavSearch,

    /** Fuel / CNG / EV finder and the speed-camera radar (from the Car hub, or by voice). */
    Fuel, Radar,

    /** Car hub › Dashcam (Pro; "No camera found" on units without one). */
    Dashcam,

    /** Apps › "Saaf karo": heaviest apps, uninstall, App info, hide. */
    Cleaner,
}

/** App languages written in Devanagari: their words use the Hind font. */
private val DEVANAGARI = setOf("hi", "mr", "ne", "mai", "bho", "kok", "sa", "bgc", "doi")

/** Car Hub dimmed the screen for the night (so sunrise undoes only its own change). */
private var nightDimmed = false

/** The brightness step before the sunset dim, put back at sunrise (null: Car Hub's window only). */
private var nightRestoreStep: Int? = null

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
    // Nothing is blurred in any theme: Map First's cards are a tint (solid below Android 9).
    // Hindi and the other Devanagari languages read in Hind; everything else in Barlow.
    val language = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language
    val resolved = DriveThemes.resolve(themeChoice, night, pro.isPro)
    val theme = remember(resolved, language) {
        if (language in DEVANAGARI) resolved.copy(font = com.vikash.ampli.drive.ui.theme.FontChoice.Hind) else resolved
    }
    // After sunset the screen dims by itself (Settings › Theme can turn that off) to brightness
    // step 2 of 6, through the same path as the quick panel's Brightness control (never a
    // near-black window value). Sunrise puts back the step from before, unless the driver chose
    // another step during the night.
    val dimAtNight by app.prefs.dimAtNight.collectAsStateWithLifecycle()
    val dimView = LocalView.current
    LaunchedEffect(night, dimAtNight) {
        val activity = dimView.context as? Activity ?: return@LaunchedEffect
        val steps = com.vikash.ampli.drive.system.BrightnessSteps
        val quick = com.vikash.ampli.drive.system.QuickActions
        if (night && dimAtNight) {
            if (!nightDimmed) {
                val before = quick.brightnessStep(activity)
                if (before == null || before > steps.NIGHT_STEP) {
                    nightRestoreStep = before
                    quick.setBrightnessStep(activity, activity, steps.NIGHT_STEP)
                    nightDimmed = true
                }
            }
        } else if (nightDimmed) {
            val back = nightRestoreStep
            val now = quick.brightnessStep(activity)
            if (back != null && (now == null || now == steps.NIGHT_STEP)) {
                quick.setBrightnessStep(activity, activity, back)
            } else {
                quick.setWindowBrightness(activity, null)
            }
            nightDimmed = false
            nightRestoreStep = null
        }
    }
    // Where Back goes from the emergency profile (opened from Settings or the Help panel).
    var emergencyReturn by remember { mutableStateOf(Screen.Settings) }
    // Where Back goes from Settings (Home, or the Car hub when opened from there).
    var settingsReturn by remember { mutableStateOf(Screen.Home) }
    // Settings opened from Home's "Getting Ampli ready" strip scrolls to the Voice card.
    var settingsFocusVoice by remember { mutableStateOf(false) }
    // Where Back goes from the Car hub's screens: Home when opened from Home's More sheet.
    var subReturn by remember { mutableStateOf(Screen.Car) }
    var showPro by remember { mutableStateOf(false) }
    var showQuick by remember { mutableStateOf(false) }
    // A detected crash opens the SOS countdown over whatever is showing.
    val crashAt by app.car.crashAt.collectAsStateWithLifecycle()
    // Keyed on the screen too: anything that leaves the countdown while it runs comes back to it.
    LaunchedEffect(crashAt, screen) { if (crashAt != null && screen != Screen.Crash) onNavigate(Screen.Crash) }
    // The overlay quick strip stays off while Car Hub is in front: every screen has its own
    // pull-down handle (and the crash / SOS screens never get the overlay).
    LaunchedEffect(screen) {
        com.vikash.ampli.drive.overlay.QuickStripGate.hubScreenBlocks.value =
            true
    }

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
        Box(Modifier.fillMaxSize().themeBackground(theme))
        Box(
            Modifier
                .fillMaxSize()
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
                  val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                  val railVolume = remember { RailVolumeState() }
                  val voice by app.voice.state.collectAsStateWithLifecycle()
                  // Every screen sits left of the control rail; the rail is on the driver's (right) side.
                  Row(Modifier.fillMaxSize()) {
                   Box(Modifier.weight(1f).fillMaxHeight()) {
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
                                    onOpenParked = {
                                        subReturn = Screen.Home
                                        onNavigate(Screen.Parked)
                                    },
                                    // The More sheet: the Car hub's screens, which then go back to Home.
                                    onOpenScreen = { target ->
                                        subReturn = Screen.Home
                                        if (target == Screen.Fuel) app.fuel.requestedFilter.value = com.vikash.ampli.drive.fuel.FuelFilter.All
                                        onNavigate(target)
                                    },
                                    onRequestPermissions = onRequestPermissions,
                                    onOpenNavScreen = { onNavigate(Screen.Navigation) },
                                    onOpenNavSearch = { onNavigate(Screen.NavSearch) },
                                    onOpenQuick = { showQuick = true },
                                    // Ampli Shell: "Make Ampli your home screen" (until done or "Not now").
                                    // First start: "Getting Ampli ready… 40%" while the speech models arrive.
                                    banner = {
                                        VoiceSetupStrip(app = app, onOpen = {
                                            settingsFocusVoice = true
                                            onNavigate(Screen.Settings)
                                        })
                                        // No speech engine on this unit: once, "Bolne ka engine nahi hai · Install karo".
                                        TtsMissingStrip(app = app, onOpen = { onNavigate(Screen.VoiceCheck) })
                                        DefaultHomeBannerHost(app = app, resumeTick = resumeTick)
                                    },
                                )
                                // The FASTag toll pop-up over Home (left of the rail).
                                TollToastHost(app = app, onOpenFastag = { onNavigate(Screen.Fastag) })
                            CarAlertHost(app = app)
                        }
                        Screen.Apps -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Home) }
                            AppsScreen(
                                app = app,
                                onBack = { onNavigate(Screen.Home) },
                                onOpenStore = { onNavigate(Screen.Store) },
                                onOpenCleaner = { onNavigate(Screen.Cleaner) },
                            )
                        }
                        Screen.Cleaner -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Apps) }
                            CleanerScreen(app = app, resumeTick = resumeTick, onBack = { onNavigate(Screen.Apps) })
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
                                onOpenVoiceCommands = { onNavigate(Screen.VoiceCommands) },
                                onOpenDiagnostics = { onNavigate(Screen.Diagnostics) },
                                focusVoice = settingsFocusVoice,
                                onVoiceFocused = { settingsFocusVoice = false },
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
                            LaunchedEffect(Unit) { subReturn = Screen.Car }
                            CarHubScreen(app = app, onBack = { onNavigate(Screen.Home) }, onOpen = { dest ->
                                when (dest) {
                                    CarDest.Parked -> onNavigate(Screen.Parked)
                                    CarDest.Papers -> onNavigate(Screen.Papers)
                                    CarDest.Expenses -> onNavigate(Screen.Expenses)
                                    CarDest.Trips -> onNavigate(Screen.Trips)
                                    CarDest.Obd -> onNavigate(Screen.Obd)
                                    CarDest.Tyres -> onNavigate(Screen.Tyres)
                                    CarDest.Fuel -> {
                                        app.fuel.requestedFilter.value = com.vikash.ampli.drive.fuel.FuelFilter.All
                                        onNavigate(Screen.Fuel)
                                    }
                                    CarDest.Radar -> onNavigate(Screen.Radar)
                                    CarDest.Dashcam -> onNavigate(Screen.Dashcam)
                                    CarDest.Settings -> {
                                        settingsReturn = Screen.Car
                                        onNavigate(Screen.Settings)
                                    }
                                }
                            })
                        }
                        Screen.Parked -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            ParkedScreen(app = app, onBack = { onNavigate(subReturn) })
                        }
                        Screen.Papers -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            PapersScreen(app = app, onBack = { onNavigate(subReturn) })
                        }
                        Screen.Expenses -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            ExpensesScreen(app = app, onBack = { onNavigate(subReturn) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Trips -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            TripsScreen(app = app, onBack = { onNavigate(subReturn) })
                        }
                        Screen.Store -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Apps) }
                            StoreScreen(app = app, onBack = { onNavigate(Screen.Apps) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Obd -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            ObdScreen(app = app, onBack = { onNavigate(subReturn) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Tyres -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            TyresScreen(app = app, onBack = { onNavigate(subReturn) }, onUnlockPro = { showPro = true })
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
                        Screen.VoiceCommands -> {
                            BackHandler(enabled = true) { onNavigate(Screen.Settings) }
                            VoiceCommandsScreen(onBack = { onNavigate(Screen.Settings) })
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
                        Screen.Fuel -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            val asked by app.fuel.requestedFilter.collectAsStateWithLifecycle()
                            FuelScreen(app = app, onBack = { onNavigate(subReturn) }, initialFilter = asked)
                        }
                        Screen.Radar -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            RadarScreen(app = app, onBack = { onNavigate(subReturn) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Dashcam -> {
                            BackHandler(enabled = true) { onNavigate(subReturn) }
                            DashcamScreen(app = app, onBack = { onNavigate(subReturn) }, onUnlockPro = { showPro = true })
                        }
                        Screen.Emergency -> {
                            BackHandler(enabled = true) { onNavigate(emergencyReturn) }
                            EmergencyProfileScreen(app = app, onBack = { onNavigate(emergencyReturn) })
                        }
                    }
                    // The glowing pull-down handle along the top (every screen), the quick panel (it
                    // or the rail's Controls key) and the volume popup beside the rail's volume keys.
                    TopHandle(open = showQuick, onOpen = { showQuick = true }, modifier = Modifier.align(Alignment.TopCenter))
                    ControlsPanel(app = app, open = showQuick, onClose = { showQuick = false }, onOpenSettings = { onNavigate(Screen.Settings) })
                    VolumePopup(railVolume, Modifier.align(Alignment.TopEnd).padding(end = dims.d(1.2f), top = dims.d(42f)))
                    if (showPro) ProUnlockSheet(app = app, onDismiss = { showPro = false })
                    // "Which app is your music / phone app?" asked by the dock, Help panel or voice.
                    val chooser by AppChooser.request.collectAsStateWithLifecycle()
                    chooser?.let { req ->
                        val context = androidx.compose.ui.platform.LocalContext.current
                        AppChooserDialog(
                            role = req.role,
                            automaticLabel = if (req.phoneLink) androidx.compose.ui.res.stringResource(com.vikash.ampli.drive.R.string.mirror_app_none) else null,
                            onPick = { entry ->
                                AppChooser.done()
                                when (req.role) {
                                    AppRole.Music -> app.prefs.pickMusicPackage(entry?.packageName)
                                    AppRole.Phone -> app.prefs.setPhonePackage(entry?.packageName)
                                    AppRole.Mirror -> if (req.phoneLink) {
                                        app.prefs.setAutoPackage(entry?.packageName)
                                    } else {
                                        app.prefs.setAutoStart(app.prefs.autoStart.value.copy(mirrorPackage = entry?.packageName))
                                    }
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
                    // Music button without Ampli MP3 Player: "Get Ampli MP3 Player".
                    GetAmpliHost()
                    // First run: "Thara naam ke sai?" over Home; the location disclosure waits for it.
                    val nameAsked by app.prefs.nameAsked.collectAsStateWithLifecycle()
                    if (!nameAsked && screen == Screen.Home) {
                        FirstRunNameHost(app = app)
                    } else {
                        BackgroundDisclosureHost(app = app, resumeTick = resumeTick, onHome = screen == Screen.Home)
                    }
                    // Quick controls, pulled down from Home's status row.
                    if (showQuick) BackHandler(enabled = true) { showQuick = false }
                    LaunchedEffect(screen) { showQuick = false }
                    // Android 13+: notification access still off after "Allow access" (sideloaded
                    // APK, restricted settings): show how to allow it.
                    RestrictedSettingsGuideHost(resumeTick = resumeTick)
                    // "Jai Baba Ki {name} Ji 🙏" while the car-start welcome plays.
                    WelcomeCardHost(app = app)
                    // "जय बजरंगबली!" while the car-start bhajan plays ("छोड़ दे" stops it).
                    BhajanCardHost(app = app)
                    // Incoming call and WhatsApp / Telegram cards over the map; never over the SOS screens.
                    if (screen != Screen.Crash && screen != Screen.Sos) {
                        CallCardHost(app = app)
                        MessageCardHost(app = app)
                    }
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
                    // "Hey Ampli" sheet over whichever screen is showing (the rail stays usable).
                    VoiceOverlay(state = voice, onCancel = app.voice::cancel, onChoose = app.voice::choose)
                   }
                   PowerRail(
                       app = app,
                       resumeTick = resumeTick,
                       volume = railVolume,
                       homeActive = screen == Screen.Home,
                       controlsOpen = showQuick,
                       onBack = { backDispatcher?.onBackPressed() },
                       onHome = {
                           showQuick = false
                           if (com.vikash.ampli.drive.system.PowerControl.state.value == com.vikash.ampli.drive.system.PowerUi.Sheet) {
                               com.vikash.ampli.drive.system.PowerControl.close()
                           }
                           // The crash countdown keeps running: Home would only bring it back (and restart it).
                           if (screen != Screen.Home && screen != Screen.Crash) onNavigate(Screen.Home)
                       },
                       onOpenApps = { if (screen != Screen.Apps) onNavigate(Screen.Apps) },
                       onControls = { showQuick = !showQuick },
                       modifier = Modifier.width(dims.d(RAIL_UNITS)).fillMaxHeight(),
                   )
                  }
                    // Power sheet, countdowns and the Screen off cover, over the whole screen (rail too).
                    PowerHost(resumeTick = resumeTick, inset = 0.dp)
                    if (voice.active) BackHandler(enabled = true) { app.voice.cancel() }
                    // After boot / ignition: the Ampli startup animation over everything.
                    BootSplashHost()
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
private fun BackgroundDisclosureHost(app: DriveApp, resumeTick: Int, onHome: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    val accepted by app.prefs.bgDisclosureAccepted.collectAsStateWithLifecycle()
    val hasFine = remember(resumeTick) {
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    if (!settings.backgroundMonitor || accepted || !hasFine) {
        // Next in line on first start: the microphone for "Hey Ampli" (on by default).
        if (onHome) FirstRunMicHost(app = app, resumeTick = resumeTick)
        return
    }
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
