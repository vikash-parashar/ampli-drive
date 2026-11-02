package com.vikash.ampli.drive.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.addOutline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.cameras.CameraAlert
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.car.DueLevel
import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.FastagStore
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.nav.NextTurn
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.obd.ObdState
import com.vikash.ampli.drive.prefs.NavApp
import com.vikash.ampli.drive.system.AppChooser
import com.vikash.ampli.drive.system.AppRole
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.StartupTiming
import com.vikash.ampli.drive.ui.theme.HomePalettes
import com.vikash.ampli.drive.ui.theme.HomeStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.em
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Low-end units: how long after the first Home frame the map is added. */
private const val HEAVY_DELAY_MS = 250L

/** The map surface type was switched once this run after drawing nothing; don't flip back and forth. */
private var mapModeFlipped = false

/**
 * Home: only what a driver needs at a glance. Time, speed (and the limit when known), the next
 * turn or map, what is playing, the big "Hey Ampli" key, one alert slot, Ampli's last line and
 * SOS. Everything else is on the More sheet (and by voice). Each of the four themes lays these
 * out its own way: [HomeStyle].
 */
@Composable
fun HomeScreen(
    app: DriveApp,
    resumeTick: Int,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHelp: (sos: Boolean) -> Unit,
    onOpenFastag: () -> Unit,
    onOpenCar: () -> Unit,
    onOpenParked: () -> Unit,
    onRequestPermissions: () -> Unit,
    /** Car Hub's own navigation: the full navigation screen, and "Where to?". */
    onOpenNavScreen: () -> Unit = {},
    onOpenNavSearch: () -> Unit = {},
    /** Ampli Shell quick controls (pull down on the top row). */
    onOpenQuick: () -> Unit = {},
    /** A screen opened from the More sheet (Fuel, Radar, Papers, Trips, Expenses, OBD, Tyres). */
    onOpenScreen: (Screen) -> Unit = {},
    /** Shown above the layout; pushes it down instead of covering it. */
    banner: @Composable () -> Unit = {},
) {
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    // GPS and the assistant change many times a second (fixes, satellite counts, the live
    // transcript). Home only recomposes when what it shows changes: the speed in whole km/h,
    // over the limit or not, location there or not, listening or not. The map reads the full
    // state in its own scope below.
    val gpsState = app.speedTracker.state.collectAsStateWithLifecycle()
    val nav by app.navigator.state.collectAsStateWithLifecycle()
    val navApp by app.prefs.navApp.collectAsStateWithLifecycle()
    val turn by NavTurnRepository.turn.collectAsStateWithLifecycle()
    val limitState = app.cameraAlerts.speedLimitKmh.collectAsStateWithLifecycle()
    val limit = limitState.value
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val voiceState = app.voice.state.collectAsStateWithLifecycle()
    val speed by remember { derivedStateOf { gpsState.value.speedKmh?.roundToInt() } }
    val over by remember {
        derivedStateOf {
            val l = limitState.value
            l != null && (gpsState.value.speedKmh ?: 0f) > l
        }
    }
    val locationPrompt by remember { derivedStateOf { !gpsState.value.hasPermission || gpsState.value.location == null } }
    val listening by remember {
        derivedStateOf { voiceState.value.phase == com.vikash.ampli.drive.voice.VoiceUiState.Phase.Listening }
    }
    val caption by app.voicePlayer.lastLine.collectAsStateWithLifecycle()
    val captionAt by app.voicePlayer.lastLineAt.collectAsStateWithLifecycle()
    // Ampli's line stays up for AmpliLine.SHOWN_MS after it was said, then the slot shows its hint.
    val freshCaption by produceState<String?>(null, caption, captionAt) {
        val left = AmpliLine.remainingMs(captionAt, android.os.SystemClock.elapsedRealtime())
        value = caption.takeIf { left > 0L }
        if (value != null) {
            delay(left)
            value = null
        }
    }
    val places by app.places.places.collectAsStateWithLifecycle()
    val savedHome = places.firstOrNull { it.kind == com.vikash.ampli.drive.nav.SavedKind.Home }

    // Map taps and the turn tile: the trip in progress, else "Where to?" on the Ampli map, else
    // Google Maps (Settings › Navigate with).
    val openNav = {
        when {
            nav.visible -> onOpenNavScreen()
            navApp == NavApp.Ampli -> onOpenNavSearch()
            else -> Launchers.openNavigation(context, gpsState.value.location)
        }
        Unit
    }

    var more by remember { mutableStateOf(false) }
    var phoneCard by remember { mutableStateOf(false) }
    var phoneLink by remember { mutableStateOf(false) }
    // Phone Link: Car Hub's own sheet. Voice "connect my phone" opens it too.
    val linkAsked by app.phoneBt.sheetRequested.collectAsStateWithLifecycle()
    LaunchedEffect(linkAsked) {
        if (linkAsked) {
            app.phoneBt.sheetShown()
            phoneLink = true
        }
    }

    // Fast start on 1-2 GB units: the first frame has no map; it comes right after. The
    // cold-start time (Diagnostics) is taken at that first frame.
    var heavyReady by remember { mutableStateOf(!app.lowEnd || StartupTiming.homeShown) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        StartupTiming.onFirstHomeFrame(context.applicationContext)
        if (!heavyReady) {
            delay(HEAVY_DELAY_MS)
            heavyReady = true
        }
    }

    val alert = homeAlert(app, onRequestPermissions, onOpenFastag, onOpenCar)
    val ui = HomeUi(
        speed = speed,
        limit = limit,
        over = over,
        rpm = obd.rpm?.roundToInt()?.takeIf { obd.phase == ObdState.Phase.Connected },
        turn = turnUi(nav, turn),
        music = musicUi(app),
        alert = alert,
        caption = freshCaption,
        locationPrompt = locationPrompt,
        onGoHome = savedHome?.let { home -> { goToSavedPlace(app, context, home) } },
        listening = listening,
        onMic = { app.voice.start() },
        onMore = { more = true },
        onHelp = { onOpenHelp(false) },
        onSos = { onOpenHelp(true) },
        onNav = openNav,
        onMusicApp = { Launchers.openMusic(context) },
        onMusicLong = { AppChooser.ask(AppRole.Music) },
        fastag = fastagChip(app),
        onFastag = onOpenFastag,
        trip = tripUi(app, nav),
        onPhone = { phoneCard = true },
        onPhoneLong = { AppChooser.ask(AppRole.Phone) },
        onFuel = { onOpenScreen(Screen.Fuel) },
    )
    // Pull down from the top row, like a phone's quick settings.
    val pullDown = Modifier.pointerInput(Unit) {
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onVerticalDrag = { _, dy ->
                pulled += dy
                if (pulled > 48.dp.toPx()) {
                    pulled = Float.NEGATIVE_INFINITY
                    onOpenQuick()
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        banner()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (theme.style) {
                HomeStyle.BigGlance -> BigGlanceHome(ui, pullDown)
                HomeStyle.Bento -> BentoHome(ui, pullDown)
                HomeStyle.RoadSign -> RoadSignHome(ui, pullDown)
                HomeStyle.MapFirst -> MapFirstHome(
                    ui,
                    pullDown,
                    // Office and favourites under the turn card while not navigating (Home is the card's own key).
                    places = { if (!nav.visible && ui.turn == null) SavedPlaceChips(app = app, skipHome = true) },
                ) {
                    if (heavyReady) {
                        val parkedSpot by app.car.store.parked.collectAsStateWithLifecycle()
                        val textureMode by app.prefs.mapTextureMode.collectAsStateWithLifecycle()
                        MapPane(
                            gps = gpsState.value,
                            onOpenNavigation = openNav,
                            onRequestPermissions = onRequestPermissions,
                            modifier = Modifier.fillMaxSize(),
                            showTurn = false,
                            parked = parkedSpot?.let { it.lat to it.lon },
                            textureMode = textureMode,
                            onNoFrames = {
                                // Nothing drawn: try the other surface type once per app run.
                                if (!mapModeFlipped) {
                                    mapModeFlipped = true
                                    app.prefs.setMapTextureMode(!textureMode)
                                }
                            },
                            nav = nav,
                            onOpenNav = onOpenNavScreen,
                            fullBleed = true,
                        )
                    }
                }
            }
            if (theme.style == HomeStyle.MapFirst) {
                val dims = LocalDims.current
                // Over the map card's lower left, clear of the dock and the right-hand cards.
                ParkedPill(app = app, onClick = onOpenParked, modifier = Modifier.align(Alignment.BottomStart).padding(start = dims.d(5.5f), bottom = dims.d(20.5f)))
            }
        }
    }

    if (more) {
        MoreSheet(
            app = app,
            onDismiss = { more = false },
            onTile = { tile ->
                more = false
                when (tile) {
                    MoreTile.Fastag -> onOpenFastag()
                    MoreTile.Fuel -> onOpenScreen(Screen.Fuel)
                    MoreTile.Radar -> onOpenScreen(Screen.Radar)
                    MoreTile.Papers -> onOpenScreen(Screen.Papers)
                    MoreTile.Trips -> onOpenScreen(Screen.Trips)
                    MoreTile.Expenses -> onOpenScreen(Screen.Expenses)
                    MoreTile.CarHealth -> onOpenScreen(Screen.Obd)
                    MoreTile.Tyres -> onOpenScreen(Screen.Tyres)
                    MoreTile.Phone -> phoneCard = true
                    MoreTile.PhoneLink -> phoneLink = true
                    MoreTile.Apps -> onOpenApps()
                    MoreTile.Settings -> onOpenSettings()
                }
            },
            onPhoneLong = { AppChooser.ask(AppRole.Phone) },
        )
    }
    if (phoneCard) PhoneCardSheet(app = app, onDismiss = { phoneCard = false })
    if (phoneLink) PhoneLinkSheet(app = app, onDismiss = { phoneLink = false }, onRequestPermissions = onRequestPermissions)
}

// ------------------------------------------------------------------------------ Home state

/** Everything the four layouts show, worked out once per frame by [HomeScreen]. */
internal class HomeUi(
    val speed: Int?,
    val limit: Int?,
    val over: Boolean,
    /** Engine rpm, only while an OBD adapter is connected (nothing at all otherwise). */
    val rpm: Int?,
    val turn: TurnUi?,
    val music: MusicUi,
    val alert: AlertUi?,
    /** The last line Ampli said (bank lines only, so always family-safe), while still fresh. */
    val caption: String?,
    /** No location for the map yet: Map First shows its "Allow location" card instead. */
    val locationPrompt: Boolean,
    /** "Go home", when Home is saved. */
    val onGoHome: (() -> Unit)?,
    /** The mic ring animates only while listening: nothing else on Home moves. */
    val listening: Boolean,
    val onMic: () -> Unit,
    val onMore: () -> Unit,
    val onHelp: () -> Unit,
    val onSos: () -> Unit,
    val onNav: () -> Unit,
    val onMusicApp: () -> Unit,
    val onMusicLong: () -> Unit,
    /** FASTag balance for the top bar ("₹855") and whether it is under the alert level. */
    val fastag: Pair<String, Boolean>? = null,
    val onFastag: () -> Unit = {},
    /** The trip bar while Car Hub navigates. */
    val trip: TripUi? = null,
    val onPhone: () -> Unit = {},
    val onPhoneLong: () -> Unit = {},
    val onFuel: () -> Unit = {},
)

/** Map First's trip bar: arrival time, "18 km · 26 min" and End route. */
internal class TripUi(val eta: String, val left: String, val onEnd: () -> Unit)

/** "₹855" and low or not, for Map First's top bar; null when FASTag is off or no balance is known. */
@Composable
private fun fastagChip(app: DriveApp): Pair<String, Boolean>? {
    val on by app.fastag.enabled.collectAsStateWithLifecycle()
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    if (!on) return null
    val balance = remember(events) { FastagStore.latestBalance(events)?.balance } ?: return null
    return rupees(kotlin.math.floor(balance)) to (balance < low)
}

/** The trip bar's text while Car Hub's own navigation runs. */
@Composable
private fun tripUi(app: DriveApp, nav: NavSession): TripUi? {
    val context = LocalContext.current
    val p = nav.progress ?: return null
    if (!nav.visible || nav.phase == NavSession.Phase.Arrived) return null
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val eta = java.time.LocalTime.now().plusSeconds(p.remainingS.toLong())
        .format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()))
    val left = navDistance(context, p.remainingM) + " · " + navDuration(context, p.remainingS)
    return TripUi(eta, left) { app.navigator.stop() }
}

/** The next manoeuvre: Car Hub's own route, or Google Maps' notification. */
internal class TurnUi(val distance: String, val text: String?, val step: RouteStep?, val arrow: ImageBitmap?)

@Composable
private fun turnUi(nav: NavSession, turn: NextTurn?): TurnUi? {
    val context = LocalContext.current
    val progress = nav.progress
    val step = progress?.let { nav.route?.steps?.getOrNull(it.stepIndex) }
    val arrow = remember(turn?.arrow) { turn?.arrow?.asImageBitmap() }
    if (nav.visible && step != null && progress != null) {
        return TurnUi(navDistance(context, progress.distanceToStepM), step.label ?: maneuverText(context, step), step, null)
    }
    if (turn == null) return null
    return TurnUi(turn.title, turn.text, null, arrow)
}

/** What is playing, or what the music area offers instead (allow access, open music). */
internal class MusicUi(
    val title: String,
    val artist: String?,
    val playing: Boolean,
    val art: ImageBitmap?,
    /** Play / pause / skip keys make sense (a player or the phone is there). */
    val controls: Boolean,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onOpen: () -> Unit,
)

@Composable
private fun musicUi(app: DriveApp): MusicUi {
    val context = LocalContext.current
    val state by app.mediaHub.state.collectAsStateWithLifecycle()
    val bt by app.bluetooth.state.collectAsStateWithLifecycle()
    val devices by app.phoneBt.devices.collectAsStateWithLifecycle()
    val phoneName = app.phoneBt.connectedPhone(devices)?.name ?: bt.connectedName
    val np = state.nowPlaying
    val art = remember(np?.art) { np?.art?.asImageBitmap() }
    val openMusic = { Launchers.openMusic(context); Unit }
    return when {
        np != null -> MusicUi(
            title = np.title ?: stringResource(R.string.music_unknown_track),
            artist = np.artist,
            playing = np.isPlaying,
            art = art,
            controls = true,
            onPlayPause = app.mediaHub::playPause,
            onNext = app.mediaHub::next,
            onPrevious = app.mediaHub::previous,
            onOpen = openMusic,
        )
        phoneName != null -> MusicUi(
            title = stringResource(R.string.phone_music_title),
            artist = phoneName,
            playing = false,
            art = null,
            controls = true,
            onPlayPause = { app.phoneBt.key(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
            onNext = { app.phoneBt.key(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) },
            onPrevious = { app.phoneBt.key(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
            onOpen = openMusic,
        )
        !state.accessGranted -> MusicUi(
            title = stringResource(R.string.allow_access),
            artist = stringResource(R.string.music_access_text),
            playing = false,
            art = null,
            controls = false,
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onOpen = { Launchers.allowNotificationAccess(context) },
        )
        else -> MusicUi(
            title = stringResource(R.string.music_nothing_playing),
            artist = stringResource(R.string.music_open),
            playing = false,
            art = null,
            controls = false,
            onPlayPause = {},
            onNext = {},
            onPrevious = {},
            onOpen = openMusic,
        )
    }
}

/** What the location alert needs from the GPS state. */
private data class LocationFlags(val hasPermission: Boolean, val hasGps: Boolean, val providerEnabled: Boolean)

/** The alert slot's content and where a tap on it goes. */
internal class AlertUi(val alert: HomeAlert, val onClick: () -> Unit)

/**
 * Every status chip of the old Home as a candidate for the one alert slot; [HomeAlerts.pick]
 * keeps the most urgent.
 */
@Composable
private fun homeAlert(app: DriveApp, onRequestPermissions: () -> Unit, onOpenFastag: () -> Unit, onOpenCar: () -> Unit): AlertUi? {
    val context = LocalContext.current
    // Only the three location switches matter here, not every fix.
    val gpsState = app.speedTracker.state.collectAsStateWithLifecycle()
    val gps by remember { derivedStateOf { LocationFlags(gpsState.value.hasPermission, gpsState.value.hasGps, gpsState.value.providerEnabled) } }
    val camerasOn by app.prefs.cameraAlerts.collectAsStateWithLifecycle()
    val camera by app.cameraAlerts.current.collectAsStateWithLifecycle()
    val fastagOn by app.fastag.enabled.collectAsStateWithLifecycle()
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    val weather by app.car.weather.collectAsStateWithLifecycle()
    val tyres by app.tpms.alerts.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    val settings by app.car.store.settings.collectAsStateWithLifecycle()
    val now = minuteTicker()
    val odoKm = (odo / 10).toInt()
    val due = remember(papers, services, odoKm) { app.car.dueItems(context) }

    val cam = camera?.takeIf { camerasOn }?.let { a ->
        val title = when (a.kind) {
            CameraAlert.Kind.Police, CameraAlert.Kind.Accident, CameraAlert.Kind.Jam -> cameraKindLabel(a.kind)
            else -> stringResource(R.string.camera_chip_label)
        }
        val text = stringResource(R.string.camera_distance, a.distanceMeters / 10 * 10) + (a.limitKmh?.let { " · $it" } ?: "")
        HomeAlert(HomeAlerts.kindFor(a), title, text, danger = a.overLimit)
    }
    val fastagTitle = stringResource(R.string.fastag_title)
    val lastToll = events.firstOrNull()?.takeIf { it.kind == FastagKind.Debit && !it.passTrip && it.amount != null }
    val toll = lastToll?.takeIf { fastagOn && HomeAlerts.tollFresh(it.time, now) }?.let { e ->
        val text = if (e.balance != null) {
            stringResource(R.string.home_toll, rupees(e.amount!!), rupees(e.balance))
        } else {
            rupees(e.amount!!)
        }
        HomeAlert(AlertKind.TollPaid, fastagTitle, text, danger = e.balance != null && e.balance < low)
    }
    val balance = FastagStore.latestBalance(events)?.balance
    val fastagLow = if (fastagOn && balance != null && balance < low) {
        HomeAlert(AlertKind.FastagLow, fastagTitle, stringResource(R.string.fastag_low) + " · " + rupees(balance), danger = true)
    } else {
        null
    }
    val tyre = if (pro.isPro) tyres.entries.firstOrNull { it.value.isNotEmpty() } else null
    val tyreAlert = tyre?.let {
        val reading = app.tpms.wheelReadings()[it.key]?.reading?.psi ?: 0.0
        HomeAlert(AlertKind.Tyre, stringResource(R.string.tyres_title), CarMonitor.tyreAlertLine(context, it.key, it.value.first(), reading), danger = true)
    }
    val engine = obd.warnings.firstOrNull()?.let {
        HomeAlert(AlertKind.Engine, stringResource(R.string.obd_title), CarMonitor.engineWarningLine(context, it), danger = true)
    }
    val hazard = if (settings.weatherAlerts) weather?.hazards?.firstOrNull() else null
    val weatherAlert = hazard?.let {
        HomeAlert(AlertKind.Weather, stringResource(R.string.status_attention), app.car.hazardLine(context, it.first, it.second))
    }
    val dueAlert = due.firstOrNull()?.let {
        HomeAlert(AlertKind.Due, stringResource(R.string.papers_title), CarMonitor.dueLine(context, it), danger = it.status.level == DueLevel.Overdue)
    }
    val location = when {
        !gps.hasPermission -> HomeAlert(AlertKind.LocationOff, stringResource(R.string.location_title), stringResource(R.string.gps_location_off))
        gps.hasGps && !gps.providerEnabled -> HomeAlert(AlertKind.LocationOff, stringResource(R.string.location_title), stringResource(R.string.gps_off))
        else -> null
    }
    val picked = HomeAlerts.pick(listOf(cam, tyreAlert, engine, toll, fastagLow, weatherAlert, dueAlert, location)) ?: return null
    return AlertUi(picked) {
        when (picked.kind) {
            AlertKind.TollPaid, AlertKind.FastagLow -> onOpenFastag()
            AlertKind.Tyre, AlertKind.Engine, AlertKind.Weather, AlertKind.Due -> onOpenCar()
            AlertKind.LocationOff -> if (!gps.hasPermission) onRequestPermissions() else Launchers.openLocationSettings(context)
            AlertKind.OverLimit, AlertKind.CameraAhead, AlertKind.ReportAhead -> Unit
        }
    }
}

// ------------------------------------------------------------------------------ Shared parts

/** "6:42" and "pm" (nothing in 24-hour mode), refreshed on the minute. */
@Composable
private fun clock(): Pair<String, String?> {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val is24h = remember { DateFormat.is24HourFormat(context) }
    return if (is24h) {
        now.format(DateTimeFormatter.ofPattern("HH:mm")) to null
    } else {
        now.format(DateTimeFormatter.ofPattern("h:mm")) to now.format(DateTimeFormatter.ofPattern("a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    }
}

/** The largest font size (sp) that fits [chars] glyphs into [w] x [h]. */
@Composable
private fun fitSize(w: Dp, h: Dp, chars: Int, widthPerChar: Float = 0.6f, heightFactor: Float = 0.9f): TextUnit {
    val density = LocalDensity.current
    val byH = h.value * heightFactor
    val byW = w.value / (chars.coerceAtLeast(1) * widthPerChar)
    return with(density) { minOf(byH, byW).dp.toSp() }
}

/**
 * The speed numeral: "62", or a dimmed "0" in the same size and font before the first GPS fix
 * (never dashes, which the number fonts draw as two bars).
 */
@Composable
private fun SpeedNumber(speed: Int?, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val theme = LocalDriveTheme.current
    Text(
        speed?.toString() ?: "0",
        style = theme.numberStyle(if (speed == null) color.copy(alpha = 0.35f) else color),
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * Round speed-limit sign: red ring and the limit in black on white (on Big Glance, light text
 * on the dark tile, as designed). Also on the radar screen.
 */
@Composable
internal fun LimitBadge(limitKmh: Int?, size: Dp, modifier: Modifier = Modifier, dark: Boolean = false) {
    val theme = LocalDriveTheme.current
    val density = LocalDensity.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) Color(0xFF0B0B0D) else Color.White)
            .border(size * 0.11f, if (dark) Color(0xFFA8332E) else Color(HomePalettes.LIMIT_RING), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            limitKmh?.toString() ?: "--",
            color = if (dark) theme.text else Color(0xFF111111),
            fontFamily = theme.font.family,
            fontWeight = FontWeight.ExtraBold,
            fontSize = with(density) { (size * 0.36f).toSp() },
            maxLines = 1,
        )
    }
}

@Composable
private fun TurnArrow(turn: TurnUi?, color: Color, modifier: Modifier) {
    when {
        turn?.step != null -> ManeuverArrow(turn.step, color, modifier)
        turn?.arrow != null -> Image(turn.arrow, contentDescription = null, colorFilter = ColorFilter.tint(color), modifier = modifier)
        else -> Icon(Icons.Filled.Navigation, contentDescription = null, tint = color, modifier = modifier)
    }
}

/** A themed label ("SPEED", "NEXT TURN") in the theme's label style. */
@Composable
private fun TileLabel(text: String, color: Color, size: Float = 2.6f) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Text(
        theme.label(text),
        style = theme.labelStyle(color),
        fontSize = dims.t(size, min = 14f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** "km/h · limit 80 · 2400 rpm" under the speed. */
@Composable
private fun speedFoot(ui: HomeUi, withLimit: Boolean = true): String = listOfNotNull(
    stringResource(R.string.speed_unit),
    ui.limit?.takeIf { withLimit }?.let { stringResource(R.string.home_limit, it) },
    ui.rpm?.let { stringResource(R.string.speed_rpm, it) },
).joinToString(" · ")

/**
 * The SOS key: tap opens the Help panel, holding 3 s starts the SOS countdown (a ring fills
 * while held). Same behaviour as before in every theme; the look follows the theme.
 */
@Composable
private fun SosKey(ui: HomeUi, modifier: Modifier, shape: Shape, hint: Boolean = false, border: Color? = null, shadow: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    val tap by androidx.compose.runtime.rememberUpdatedState(ui.onHelp)
    val hold by androidx.compose.runtime.rememberUpdatedState(ui.onSos)
    Box(
        modifier
            .heightIn(min = MinTouch)
            .then(if (shadow) Modifier.hardShadow(shape, theme.border) else Modifier)
            .clip(shape)
            .background(theme.danger)
            .then(if (border != null) Modifier.border(theme.borderWidth, border, shape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var fired = false
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, androidx.compose.animation.core.tween(HOLD_MS, easing = androidx.compose.animation.core.LinearEasing))
                            fired = true
                            hold()
                        }
                        tryAwaitRelease()
                        if (!fired) {
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                            tap()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Hold progress: a bar filling along the bottom edge.
        if (progress.value > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.value)
                    .height(6.dp)
                    .background(theme.onDanger),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.sos_button),
                color = theme.onDanger,
                fontFamily = theme.numberFont.family,
                fontWeight = FontWeight.Black,
                fontSize = dims.t(4.4f, min = 24f),
                letterSpacing = 0.04.em,
                maxLines = 1,
            )
            if (hint) {
                Text(
                    stringResource(R.string.home_sos_hold),
                    color = theme.onDanger,
                    fontSize = dims.t(2.3f, min = 15f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A dock key: icon above or beside its label. */
@Composable
private fun DockKey(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    fill: Color,
    content: Color,
    border: Color? = null,
    stacked: Boolean = true,
    shadow: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    textSize: Float = 2.8f,
    iconTint: Color = content,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val inner = @Composable {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(maxOf(dims.d(4.6f), 28.dp)))
        Text(
            theme.label(label),
            color = content,
            fontFamily = theme.font.family,
            fontWeight = FontWeight.Bold,
            fontSize = dims.t(textSize, min = 22f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = if (theme.upperLabels) 0.04.em else androidx.compose.ui.unit.TextUnit.Unspecified,
        )
    }
    Box(
        modifier
            .heightIn(min = MinTouch)
            .then(if (shadow) Modifier.hardShadow(shape, theme.border) else Modifier)
            .themedTap(shape, fill, border = border, borderWidth = if (border != null) maxOf(theme.borderWidth, 1.dp) else 1.dp, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) { inner() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) { inner() }
        }
    }
}

/** The big mic key. Its ring pulses only while Ampli is listening (the one animation on Home). */
@Composable
private fun MicRing(listening: Boolean, color: Color, modifier: Modifier) {
    if (!listening) return
    // Lite mode (weak units): the ring shows but stands still.
    if (com.vikash.ampli.drive.prefs.LiteMode.enabled) {
        Box(modifier.border(6.dp, color.copy(alpha = 0.5f), CircleShape))
        return
    }
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "mic")
    val s = pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ring",
    )
    Box(
        modifier
            // Read in the draw layer only: the pulse redraws the ring without recomposing Home.
            .graphicsLayer {
                scaleX = s.value
                scaleY = s.value
            }
            .border(6.dp, color.copy(alpha = 0.5f), CircleShape),
    )
}

/** Round play / pause and skip keys for the music tile. */
@Composable
private fun MusicKey(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    fill: Color,
    tint: Color,
    border: Color?,
    shape: Shape = CircleShape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(maxOf(size, MinTouch))
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(maxOf(size, MinTouch) * 0.45f))
    }
}

@Composable
private fun playLabel(playing: Boolean) = stringResource(if (playing) R.string.music_pause else R.string.music_play)

// ------------------------------------------------------------------------------ Big Glance

/** Style 3: three giant tiles on true black; Hey Ampli, More and SOS below. */
@Composable
private fun BigGlanceHome(ui: HomeUi, pullDown: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val gap = dims.d(2.2f)
    val tile = theme.cardShape(dims)
    Column(Modifier.fillMaxSize().padding(horizontal = dims.d(3.2f), vertical = dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(gap)) {
        // Time, the alert slot and Ampli's last line.
        Row(
            Modifier.fillMaxWidth().height(maxOf(dims.d(8f), MinTouch)).then(pullDown),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f)),
        ) {
            val (time, ampm) = clock()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, style = theme.numberStyle(theme.text), fontSize = dims.t(6.6f, min = 30f), maxLines = 1)
                if (ampm != null) Text(" $ampm", color = theme.muted, fontSize = dims.t(2.8f, min = 16f), modifier = Modifier.padding(bottom = dims.d(0.6f)))
            }
            ui.alert?.let { AlertPill(it, Modifier.widthIn(max = dims.d(44f))) }
            Spacer(Modifier.weight(0.01f))
            CaptionLine(ui.caption, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            // Speed.
            Box(Modifier.weight(1f).fillMaxHeight().clip(tile).background(if (ui.over) theme.danger else theme.surface).padding(dims.d(3.4f))) {
                val fg = if (ui.over) theme.onDanger else theme.text
                TileLabel(stringResource(R.string.speed_label), if (ui.over) theme.onDanger else theme.muted, 2.8f)
                if (ui.limit != null) LimitBadge(ui.limit, dims.d(11f), Modifier.align(Alignment.TopEnd).offset(y = -dims.d(1f)), dark = true)
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = dims.d(5f), bottom = dims.d(4.4f)), contentAlignment = Alignment.CenterStart) {
                    SpeedNumber(ui.speed, fg, fitSize(maxWidth, maxHeight, 2, 0.5f, 1.05f))
                }
                Text(
                    speedFoot(ui),
                    color = if (ui.over) theme.onDanger else theme.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = dims.t(3f, min = 18f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
            // Next turn.
            Box(Modifier.weight(1f).fillMaxHeight().clip(tile).background(theme.surface).clickable(onClick = ui.onNav).padding(dims.d(3.4f))) {
                TileLabel(stringResource(R.string.home_next_turn), theme.muted, 2.8f)
                val t = ui.turn
                if (t == null) {
                    Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = theme.accent, modifier = Modifier.size(dims.d(12f)))
                        Text(stringResource(R.string.nav_search_title), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(4.6f, min = 24f))
                    }
                } else {
                    TurnArrow(t, theme.accent, Modifier.padding(top = dims.d(5.4f)).size(dims.d(18f)))
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(t.distance, style = theme.numberStyle(theme.text), fontSize = dims.t(13f, min = 40f), maxLines = 1)
                        t.text?.let { Text(it, color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(3f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            // Playing.
            Box(Modifier.weight(1f).fillMaxHeight().clip(tile).background(theme.surface).clickable(onClick = ui.music.onOpen).padding(dims.d(3.4f))) {
                TileLabel(stringResource(R.string.home_playing), theme.muted, 2.8f)
                if (ui.music.controls) {
                    MusicKey(
                        if (ui.music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        playLabel(ui.music.playing),
                        ui.music.onPlayPause,
                        dims.d(11f),
                        Color.Transparent,
                        theme.accent,
                        theme.accent,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
                // Still bars: a picture of music, not an animation.
                Row(Modifier.align(Alignment.CenterStart).padding(bottom = dims.d(6f)), horizontalArrangement = Arrangement.spacedBy(dims.d(1f)), verticalAlignment = Alignment.Bottom) {
                    for (h in listOf(5f, 10f, 7f, 12f, 4f, 9f, 6f)) {
                        Box(Modifier.width(dims.d(1.8f)).height(dims.d(h)).clip(RoundedTop).background(theme.surfaceHigh.copy(alpha = 1f)))
                    }
                }
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text(ui.music.title, color = theme.text, fontWeight = FontWeight.ExtraBold, fontFamily = theme.font.family, fontSize = dims.t(5f, min = 24f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = dims.t(5.4f, min = 26f))
                    ui.music.artist?.let { Text(it, color = theme.muted, fontSize = dims.t(3f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(maxOf(dims.d(15f), MinTouch)), horizontalArrangement = Arrangement.spacedBy(gap)) {
            val keyShape = theme.cardShape(dims, 0.8f)
            Box(Modifier.weight(2.15f).fillMaxHeight()) {
                DockKey(stringResource(R.string.voice_button), Icons.Filled.Mic, ui.onMic, Modifier.fillMaxSize(), keyShape, theme.accent, theme.onAccent, stacked = false, textSize = 3.6f)
                MicRing(ui.listening, theme.accent, Modifier.matchParentSize())
            }
            DockKey(stringResource(R.string.more_sheet_title), Icons.Filled.GridView, ui.onMore, Modifier.weight(1f).fillMaxHeight(), keyShape, theme.surface, theme.text, stacked = false, textSize = 3.4f)
            SosKey(ui, Modifier.weight(1.1f).fillMaxHeight(), keyShape)
        }
    }
}

private val RoundedTop = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

/** Big Glance's top line: a dot and Ampli's last line. */
@Composable
private fun CaptionLine(caption: String?, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f), Alignment.End)) {
        Box(Modifier.size(dims.d(1.4f)).clip(CircleShape).background(theme.accent))
        Text(
            caption ?: stringResource(R.string.home_mic_hint),
            color = theme.text,
            fontSize = dims.t(2.8f, min = 22f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The alert slot as a pill (Big Glance, Map First). */
@Composable
private fun AlertPill(a: AlertUi, modifier: Modifier = Modifier, background: Color? = null) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val danger = a.alert.danger
    val bg = when {
        danger -> theme.danger
        background != null -> background
        else -> theme.warning
    }
    val fg = when {
        danger -> theme.onDanger
        background != null -> theme.text
        theme.isLight -> Color.White
        else -> Color(0xFF111111)
    }
    val shape = if (theme.radius <= 0f) RectangleShape else theme.cardShape(dims, 0.7f)
    Row(
        modifier
            .heightIn(min = MinTouch)
            .clip(shape)
            .background(bg)
            .clickable(onClick = a.onClick)
            .padding(horizontal = dims.d(2.2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = if (background != null && !danger) theme.accentSoft else fg, modifier = Modifier.size(maxOf(dims.d(3.6f), 24.dp)))
        Text(
            a.alert.line,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = dims.t(2.8f, min = 22f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------------------------------------------------------ Map First

/**
 * Map First (default, free) and Calm, as on the "Main" design board: a fixed frame of top bar
 * (clock and date, the "Hey Ampli" pill, the FASTag chip), the map card with the turn banner and
 * the trip bar, the speed card and the music card on the right, and the dock (Go, Music, Phone,
 * Fuel, More, SOS) along the bottom. Flat fills only: nothing is blurred or shadowed.
 */
@Composable
private fun MapFirstHome(ui: HomeUi, pullDown: Modifier, places: @Composable () -> Unit, map: @Composable () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val gap = dims.d(2f)
    Column(Modifier.fillMaxSize().padding(dims.d(3f)), verticalArrangement = Arrangement.spacedBy(gap)) {
        MapFirstTopBar(ui, Modifier.fillMaxWidth().height(dims.d(8f)).then(pullDown))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            MapFirstMapCard(ui, places, map, Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.width(dims.d(49f)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                MapFirstSpeedCard(ui, Modifier.fillMaxWidth().weight(0.42f))
                MapFirstMusicCard(ui, Modifier.fillMaxWidth().weight(0.58f))
            }
        }
        MapFirstDock(ui, Modifier.fillMaxWidth().height(dims.d(13f)))
    }
}

/** Clock and date, the "Hey Ampli" pill and the FASTag balance chip. Pull down here for Controls. */
@Composable
private fun MapFirstTopBar(ui: HomeUi, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        val (time, ampm) = clock()
        val date = remember(time) {
            try {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault()))
            } catch (_: Exception) {
                ""
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dims.d(1.5f))) {
            Text(time, style = theme.numberStyle(theme.text), fontSize = dims.t(6f, min = 32f), maxLines = 1)
            Column {
                ampm?.let { Text(it, color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.2f, min = 16f), maxLines = 1) }
                if (!dims.compact) Text(date, color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.5f, min = 16f), maxLines = 1)
            }
        }
        // "Bolo Hey Ampli": the whole pill starts listening; mint edge while Ampli listens.
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(theme.surface)
                .border(2.dp, if (ui.listening) theme.accent else theme.border, CircleShape)
                .clickable(onClick = ui.onMic)
                .padding(horizontal = dims.d(3f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.5f), Alignment.CenterHorizontally),
        ) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(3.5f)))
            Text(
                stringResource(R.string.home_say_hey_ampli),
                color = theme.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = dims.t(3f, min = 20f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ui.fastag?.let { (balance, low) ->
            val shape = theme.cardShape(dims, 0.67f)
            Row(
                Modifier
                    .fillMaxHeight()
                    .themedTap(shape, if (low) theme.danger else theme.surface, border = if (low) null else theme.border, borderWidth = 2.dp, onClick = ui.onFastag)
                    .padding(horizontal = dims.d(2.5f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(1.25f)),
            ) {
                Text(
                    "FASTAG",
                    color = if (low) theme.onDanger else theme.muted,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.t(2f, min = 14f),
                    letterSpacing = 0.08.em,
                    maxLines = 1,
                )
                Text(balance, style = theme.numberStyle(if (low) theme.onDanger else theme.text), fontSize = dims.t(4f, min = 24f), maxLines = 1)
            }
        }
    }
}

/** The map in a rounded card: the turn banner top left, Ampli's bubble, and the trip bar. */
@Composable
private fun MapFirstMapCard(ui: HomeUi, places: @Composable () -> Unit, map: @Composable () -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims)
    Box(modifier.clip(shape).background(theme.mapBase).border(2.dp, theme.border, shape)) {
        // The drawn map: behind the live map while it loads, and on its own without a location.
        MapPlaceholder(Modifier.fillMaxSize())
        map()
        Column(
            Modifier.align(Alignment.TopStart).padding(dims.d(2.5f)).widthIn(max = dims.d(52.5f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.5f)),
        ) {
            // Turn banner (Soft Indigo at night, Navy by day), or "Where to?" and "Go home".
            Row(
                Modifier
                    .heightIn(min = MinTouch)
                    .clip(theme.cardShape(dims, 0.83f))
                    .background(theme.banner)
                    .clickable(onClick = ui.onNav)
                    .padding(horizontal = dims.d(3f), vertical = dims.d(2.25f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2.5f)),
            ) {
                val t = ui.turn
                TurnArrow(t, theme.onBanner, Modifier.size(dims.d(8f)))
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(0.25f))) {
                    if (t != null) {
                        Text(t.distance, style = theme.numberStyle(theme.onBanner), fontSize = dims.t(7f, min = 32f), maxLines = 1)
                        t.text?.let { Text(it, color = theme.onBanner, fontWeight = FontWeight.SemiBold, fontSize = dims.t(3f, min = 20f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Text(
                            stringResource(R.string.nav_search_title),
                            style = theme.numberStyle(theme.onBanner),
                            fontSize = dims.t(5f, min = 28f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ui.onGoHome?.let { goHome ->
                            Row(
                                Modifier
                                    .heightIn(min = MinTouch)
                                    .clip(CircleShape)
                                    .background(theme.accent)
                                    .clickable(onClick = goHome)
                                    .padding(horizontal = dims.d(2.5f)),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
                            ) {
                                Icon(Icons.Filled.Home, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(maxOf(dims.d(3.5f), 24.dp)))
                                Text(stringResource(R.string.home_go_home), color = theme.onAccent, fontWeight = FontWeight.Bold, fontSize = dims.t(2.75f, min = 20f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            places()
        }
        // Ampli's line, in its own slot above the trip bar, only while fresh.
        val line = ui.caption
        if (line != null && AmpliLine.bubbleShown(line, ui.locationPrompt)) {
            AmpliBubble(line, Modifier.align(Alignment.BottomCenter).padding(bottom = dims.d(if (ui.trip != null) 13f else 3f)).widthIn(max = dims.d(56f)))
        }
        // Trip bar: arrival time, distance and time left, "End route".
        ui.trip?.let { trip ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(dims.d(2.5f))
                    .fillMaxWidth()
                    .height(dims.d(10f))
                    .clip(theme.cardShape(dims, 0.83f))
                    .background(theme.overMap)
                    .padding(horizontal = dims.d(3f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(3f)),
            ) {
                Text(trip.eta, style = theme.numberStyle(theme.text), fontSize = dims.t(5f, min = 26f), maxLines = 1)
                Text(trip.left, color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.75f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .heightIn(min = dims.d(7f))
                        .clip(CircleShape)
                        .border(2.dp, theme.text, CircleShape)
                        .clickable(onClick = trip.onEnd)
                        .padding(horizontal = dims.d(3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.home_end_route), color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.75f, min = 18f), maxLines = 1)
                }
            }
        }
    }
}

/** Speed in Barlow Condensed at 128 px with the limit sign, and the alert strip under it. */
@Composable
private fun MapFirstSpeedCard(ui: HomeUi, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims)
    val fill = if (ui.over) theme.danger else theme.surface
    val fg = if (ui.over) theme.onDanger else theme.text
    BoxWithConstraints(modifier.themedCard(theme, shape, fill = androidx.compose.ui.graphics.SolidColor(fill), border = if (ui.over) theme.danger else theme.border)) {
        val tall = maxHeight
        Column(
            Modifier.fillMaxSize().padding(horizontal = dims.d(3f), vertical = dims.d(2.5f)),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                    val bySpace = with(LocalDensity.current) { (tall * 0.5f).toSp().value }
                    SpeedNumber(ui.speed, fg, minOf(dims.uSp * 16f, bySpace).coerceAtLeast(48f).sp)
                    Text(speedFoot(ui, withLimit = false), color = if (ui.over) theme.onDanger else theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.75f, min = 18f), maxLines = 1, modifier = Modifier.padding(bottom = dims.d(1.5f)))
                }
                if (ui.limit != null) LimitBadge(ui.limit, dims.d(12f))
            }
            ui.alert?.let { a ->
                val danger = a.alert.danger
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = dims.d(6f))
                        .clip(RoundedCornerShape(dims.d(1.5f)))
                        .background(if (danger && !ui.over) theme.danger else theme.warning.copy(alpha = 0.16f))
                        .clickable(onClick = a.onClick)
                        .padding(horizontal = dims.d(2f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.25f)),
                ) {
                    val c = when {
                        danger && !ui.over -> theme.onDanger
                        ui.over -> theme.onDanger
                        else -> theme.warning
                    }
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = c, modifier = Modifier.size(dims.d(3f)))
                    Text(a.alert.line, color = c, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.5f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Now playing: art, title, artist, and 80 px keys with the mint play key in the middle. */
@Composable
private fun MapFirstMusicCard(ui: HomeUi, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims)
    Column(
        modifier
            .themedCard(theme, shape, fill = androidx.compose.ui.graphics.SolidColor(theme.surface))
            .padding(horizontal = dims.d(3f), vertical = dims.d(2.5f)),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = ui.music.onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Box(Modifier.size(dims.d(11f)).clip(RoundedCornerShape(dims.d(2f))).background(theme.surfaceHigh), contentAlignment = Alignment.Center) {
                val art = ui.music.art
                if (art != null) {
                    Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(dims.d(5f)))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(0.5f))) {
                Text(ui.music.title, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.5f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                ui.music.artist?.let { Text(it, color = theme.muted, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.5f, min = 18f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (ui.music.controls) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                val key = dims.d(10f)
                MusicKey(Icons.Filled.SkipPrevious, stringResource(R.string.music_previous), ui.music.onPrevious, key, theme.surfaceHigh, theme.text, null)
                MusicKey(
                    if (ui.music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    playLabel(ui.music.playing),
                    ui.music.onPlayPause,
                    key,
                    theme.accent,
                    theme.onAccent,
                    null,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.width(dims.d(14f)),
                )
                MusicKey(Icons.Filled.SkipNext, stringResource(R.string.music_next), ui.music.onNext, key, theme.surfaceHigh, theme.text, null)
            }
        }
    }
}

/** The dock: Go, Music, Phone, Fuel, More and SOS, 104 px tall. */
@Composable
private fun MapFirstDock(ui: HomeUi, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.buttonShape(dims)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        val key = Modifier.weight(1f).fillMaxHeight()
        DockKey(stringResource(R.string.dock_navigate), Icons.Filled.Navigation, ui.onNav, key, shape, theme.surface, theme.text, border = theme.border, iconTint = theme.accentSoft, textSize = 3f)
        DockKey(stringResource(R.string.dock_music), Icons.Filled.MusicNote, ui.onMusicApp, key, shape, theme.surface, theme.text, border = theme.border, onLongClick = ui.onMusicLong, iconTint = theme.accentSoft, textSize = 3f)
        DockKey(stringResource(R.string.dock_phone), Icons.Filled.Phone, ui.onPhone, key, shape, theme.surface, theme.text, border = theme.border, onLongClick = ui.onPhoneLong, iconTint = theme.accentSoft, textSize = 3f)
        DockKey(stringResource(R.string.more_fuel), Icons.Filled.LocalGasStation, ui.onFuel, key, shape, theme.surface, theme.text, border = theme.border, iconTint = theme.accentSoft, textSize = 3f)
        DockKey(stringResource(R.string.more_sheet_title), Icons.Filled.GridView, ui.onMore, key, shape, theme.surface, theme.text, border = theme.border, iconTint = theme.accentSoft, textSize = 3f)
        SosKey(ui, key, shape)
    }
}

/** Map First's speech bubble: Ampli's line on a light card with a tail pointing down at the car. */
@Composable
private fun AmpliBubble(line: String, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val fill = if (theme.isLight) theme.banner else Color(HomePalettes.MAP_FIRST_BUBBLE)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .clip(theme.cardShape(dims, 0.6f))
                .background(fill)
                .padding(horizontal = dims.d(2.6f), vertical = dims.d(1.8f)),
        ) {
            Text(stringResource(R.string.home_ampli), color = if (theme.isLight) theme.accent else theme.accentSoft, fontWeight = FontWeight.Bold, fontSize = dims.t(2.4f, min = 16f))
            Text(line, color = if (theme.isLight) theme.onBanner else Color(HomePalettes.MAP_FIRST_BUBBLE_INK), fontWeight = FontWeight.SemiBold, fontSize = dims.t(3f, min = 22f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.foundation.Canvas(Modifier.width(dims.d(3.2f)).height(dims.d(1.8f))) {
            val tail = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, fill)
        }
    }
}

// ------------------------------------------------------------------------------ Bento Grid

/** Style 6: a magazine grid where the size of a tile is how much it matters. */
@Composable
private fun BentoHome(ui: HomeUi, pullDown: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val gap = dims.d(1.8f)
    val shape = theme.cardShape(dims)
    val edge = theme.borderWidth
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth().weight(0.66f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            // Speed: black tile.
            Box(Modifier.weight(1f).fillMaxHeight().clip(shape).background(if (ui.over) theme.danger else theme.ink).padding(dims.d(3f))) {
                val fg = if (ui.over) theme.onDanger else theme.onInk
                TileLabel(stringResource(R.string.speed_label), fg.copy(alpha = 0.75f), 2.4f)
                if (ui.limit != null) LimitBadge(ui.limit, dims.d(11.5f), Modifier.align(Alignment.TopEnd).offset(y = -dims.d(0.8f)))
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = dims.d(6f), bottom = dims.d(5f)), contentAlignment = Alignment.CenterStart) {
                    SpeedNumber(ui.speed, fg, fitSize(maxWidth, maxHeight, 2, 0.62f, 1f))
                }
                Text(listOfNotNull(stringResource(R.string.speed_unit), ui.rpm?.let { stringResource(R.string.speed_rpm, it) }).joinToString(" · "), color = fg, fontWeight = FontWeight.SemiBold, fontSize = dims.t(3f, min = 22f), modifier = Modifier.align(Alignment.BottomStart))
                if (ui.limit != null) Text(stringResource(R.string.home_limit, ui.limit), color = fg.copy(alpha = 0.7f), fontSize = dims.t(3f, min = 22f), modifier = Modifier.align(Alignment.BottomEnd))
            }
            Column(Modifier.weight(1.04f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                // Next turn: blue tile.
                Row(
                    Modifier.fillMaxWidth().weight(1f).clip(shape).background(theme.accent).clickable(onClick = ui.onNav).padding(dims.d(3f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(3f)),
                ) {
                    TurnArrow(ui.turn, Color.White, Modifier.size(dims.d(11f)))
                    Column {
                        val t = ui.turn
                        Text(t?.distance ?: stringResource(R.string.nav_search_title), style = theme.numberStyle(Color.White), fontSize = dims.t(7.4f, min = 28f), maxLines = 2, lineHeight = dims.t(7.4f, min = 28f))
                        t?.text?.let { Text(it, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.8f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                // Playing: white tile with the three keys.
                Column(Modifier.fillMaxWidth().weight(1f).clip(shape).background(theme.surface).clickable(onClick = ui.music.onOpen).padding(horizontal = dims.d(3f), vertical = dims.d(2f))) {
                    TileLabel(stringResource(R.string.home_playing), theme.muted, 2.4f)
                    Text(ui.music.title, color = theme.text, fontWeight = FontWeight.ExtraBold, fontFamily = theme.font.family, fontSize = dims.t(3.8f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ui.music.artist?.let { Text(it, color = theme.muted, fontSize = dims.t(2.6f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.weight(1f))
                    if (ui.music.controls) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            MusicKey(Icons.Filled.SkipPrevious, stringResource(R.string.music_previous), ui.music.onPrevious, dims.d(8f), Color.Transparent, theme.ink, theme.ink)
                            MusicKey(if (ui.music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, playLabel(ui.music.playing), ui.music.onPlayPause, dims.d(8f), theme.ink, theme.onInk, null)
                            MusicKey(Icons.Filled.SkipNext, stringResource(R.string.music_next), ui.music.onNext, dims.d(8f), Color.Transparent, theme.ink, theme.ink)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                // Clock.
                Column(Modifier.fillMaxWidth().weight(1f).clip(shape).border(edge, theme.border, shape).then(pullDown).padding(dims.d(3f))) {
                    val (time, ampm) = clock()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(time, style = theme.numberStyle(theme.text), fontSize = dims.t(10f, min = 36f), maxLines = 1)
                        if (ampm != null) Text(ampm, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f), modifier = Modifier.padding(bottom = dims.d(1.4f)))
                    }
                    Spacer(Modifier.weight(1f))
                    val date = remember(time) { LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) }
                    Text(date, color = theme.text, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.6f, min = 16f))
                }
                // Ampli's last line.
                Column(Modifier.fillMaxWidth().weight(1f).clip(shape).background(theme.surfaceHigh).padding(dims.d(3f))) {
                    Text("“", color = theme.accent, fontWeight = FontWeight.Black, fontSize = dims.t(7f, min = 28f), lineHeight = dims.t(5f, min = 22f))
                    Text(ui.caption ?: stringResource(R.string.home_mic_hint), color = theme.text, fontWeight = FontWeight.Bold, fontFamily = theme.font.family, fontSize = dims.t(3.2f, min = 22f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.home_ampli), color = theme.muted, fontSize = dims.t(2.4f, min = 15f))
                }
            }
        }
        // Hey Ampli, the alert tile, More and SOS.
        Row(Modifier.fillMaxWidth().weight(0.34f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Box(Modifier.weight(2.3f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxSize().clip(shape).background(theme.accent).clickable(onClick = ui.onMic).padding(horizontal = dims.d(4f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(3f)),
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(dims.d(7f)))
                    Column {
                        Text(stringResource(R.string.voice_button), color = theme.onAccent, fontFamily = theme.numberFont.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(5.4f, min = 26f), maxLines = 1)
                        Text(stringResource(R.string.home_mic_hint), color = theme.onAccent.copy(alpha = 0.85f), fontSize = dims.t(2.6f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                MicRing(ui.listening, theme.accent, Modifier.matchParentSize())
            }
            // The alert slot; "Where to?" when nothing needs attention.
            val a = ui.alert
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (a?.alert?.danger == true) theme.danger else theme.background)
                    .dashedBorder(theme.border, shape, edge)
                    .clickable(onClick = a?.onClick ?: ui.onNav)
                    .padding(dims.d(2.2f)),
                verticalArrangement = Arrangement.Center,
            ) {
                val fg = if (a?.alert?.danger == true) theme.onDanger else theme.text
                Icon(if (a != null) Icons.Filled.Warning else Icons.Filled.Navigation, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(4.4f)))
                Text(a?.alert?.headline ?: stringResource(R.string.nav_search_title), color = fg, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                a?.alert?.detail?.let { Text(it, color = fg.copy(alpha = 0.8f), fontSize = dims.t(2.4f, min = 16f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            DockKey(stringResource(R.string.more_sheet_title), Icons.Filled.GridView, ui.onMore, Modifier.weight(0.9f).fillMaxHeight(), shape, theme.ink, theme.onInk, textSize = 3f)
            SosKey(ui, Modifier.weight(0.9f).fillMaxHeight(), shape, hint = true)
        }
    }
}

/** A dashed outline (Bento's alert tile), drawn once, no animation. */
private fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
    drawPath(
        path,
        color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = width.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        ),
    )
}

// ------------------------------------------------------------------------------ Road Sign

/** Style 8: Indian road-sign language, thick black lines and hard shadows. */
@Composable
private fun RoadSignHome(ui: HomeUi, pullDown: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val gap = dims.d(3f)
    val bw = theme.borderWidth
    Column(Modifier.fillMaxSize()) {
        // Top band: time, hazard stripes, the alert slot.
        Row(Modifier.fillMaxWidth().height(maxOf(dims.d(8.6f), MinTouch)).background(theme.ink).then(pullDown), verticalAlignment = Alignment.CenterVertically) {
            val (time, ampm) = clock()
            Text(
                (time + (ampm?.let { " $it" } ?: "")).uppercase(Locale.ENGLISH),
                color = theme.onInk,
                fontFamily = theme.numberFont.family,
                fontSize = dims.t(4.4f, min = 22f),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = dims.d(2.6f)),
            )
            HazardStripes(Modifier.weight(1f).fillMaxHeight())
            ui.alert?.let { a ->
                Row(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = dims.d(52f))
                        .background(if (a.alert.danger) theme.danger else theme.background)
                        .border(bw, theme.border)
                        .clickable(onClick = a.onClick)
                        .padding(horizontal = dims.d(2.6f)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                ) {
                    val fg = if (a.alert.danger) theme.onDanger else theme.text
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(dims.d(4.4f)))
                    Text(a.alert.line.uppercase(), color = fg, fontFamily = theme.font.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(start = dims.d(3f), end = dims.d(3.6f), top = dims.d(3.6f), bottom = dims.d(1.6f)), horizontalArrangement = Arrangement.spacedBy(gap)) {
            // Speed sign.
            Box(Modifier.weight(1f).fillMaxHeight().hardShadow(RectangleShape, theme.border).background(if (ui.over) theme.danger else theme.surface).border(bw, theme.border).padding(dims.d(3.2f))) {
                val fg = if (ui.over) theme.onDanger else theme.text
                TileLabel(stringResource(R.string.speed_label), fg, 3f)
                if (ui.limit != null) LimitBadge(ui.limit, dims.d(13f), Modifier.align(Alignment.TopEnd).offset(y = -dims.d(1f)))
                BoxWithConstraints(Modifier.fillMaxSize().padding(top = dims.d(7f), bottom = dims.d(5f)), contentAlignment = Alignment.CenterStart) {
                    SpeedNumber(ui.speed, fg, fitSize(maxWidth, maxHeight, 2, 0.72f, 0.9f))
                }
                Text(speedFoot(ui, withLimit = false).uppercase(Locale.ENGLISH), color = fg, fontFamily = theme.font.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3.2f, min = 22f), modifier = Modifier.align(Alignment.BottomStart))
            }
            // The blue direction sign.
            Box(Modifier.weight(1f).fillMaxHeight().hardShadow(RectangleShape, theme.border).background(theme.accent).border(bw, theme.border).clickable(onClick = ui.onNav).padding(dims.d(1.4f))) {
                BoxWithConstraints(Modifier.fillMaxSize().border(3.dp, Color.White).padding(dims.d(3f))) {
                    // As on the mock: a big arrow, about half the sign's width, above the distance.
                    TurnArrow(ui.turn, Color.White, Modifier.size(minOf(maxWidth * 0.5f, maxHeight * 0.44f)))
                    Column(Modifier.align(Alignment.BottomStart)) {
                        val t = ui.turn
                        Text((t?.distance ?: stringResource(R.string.nav_search_title)).uppercase(), style = theme.numberStyle(Color.White), fontSize = dims.t(if (t != null) 9f else 5f, min = 26f), maxLines = 2)
                        t?.text?.let {
                            Text(it.uppercase(), color = Color.White, fontFamily = theme.font.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3f, min = 22f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.weight(0.84f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                // Now playing: black panel, yellow title and square keys.
                Column(Modifier.fillMaxWidth().weight(1f).hardShadow(RectangleShape, theme.border).background(theme.ink).clickable(onClick = ui.music.onOpen).padding(dims.d(2.4f))) {
                    TileLabel(stringResource(R.string.home_now_playing), Color.White, 2.2f)
                    Text(ui.music.title.uppercase(), color = theme.onInk, fontFamily = theme.font.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3.8f, min = 22f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = dims.t(4.2f, min = 24f))
                    ui.music.artist?.let { Text(it, color = Color.White, fontFamily = theme.font.family, fontWeight = FontWeight.SemiBold, fontSize = dims.t(2.4f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.weight(1f))
                    if (ui.music.controls) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            MusicKey(Icons.Filled.SkipPrevious, stringResource(R.string.music_previous), ui.music.onPrevious, dims.d(7f), theme.onInk, theme.ink, null, RectangleShape)
                            MusicKey(if (ui.music.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, playLabel(ui.music.playing), ui.music.onPlayPause, dims.d(7f), theme.onInk, theme.ink, null, RectangleShape)
                            MusicKey(Icons.Filled.SkipNext, stringResource(R.string.music_next), ui.music.onNext, dims.d(7f), theme.onInk, theme.ink, null, RectangleShape)
                        }
                    }
                }
                // Ampli's last line.
                Column(Modifier.fillMaxWidth().weight(1f).hardShadow(RectangleShape, theme.border).background(theme.surface).border(bw, theme.border).padding(dims.d(2.4f))) {
                    Text(stringResource(R.string.home_ampli).uppercase(Locale.ENGLISH) + ":", color = theme.accent, fontFamily = theme.numberFont.family, fontSize = dims.t(2.6f, min = 16f))
                    Text(ui.caption ?: stringResource(R.string.home_mic_hint), color = theme.text, fontFamily = theme.font.family, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(3f, min = 22f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(maxOf(dims.d(13.5f), MinTouch)).padding(start = dims.d(3f), end = dims.d(3.6f)), horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f))) {
            DockKey(stringResource(R.string.home_map), Icons.Filled.Map, ui.onNav, Modifier.weight(1f).fillMaxHeight(), RectangleShape, theme.surface, theme.text, theme.border, stacked = false, shadow = true)
            DockKey(stringResource(R.string.dock_music), Icons.Filled.MusicNote, ui.onMusicApp, Modifier.weight(1f).fillMaxHeight(), RectangleShape, theme.surface, theme.text, theme.border, stacked = false, shadow = true, onLongClick = ui.onMusicLong)
            Box(Modifier.weight(1.9f).fillMaxHeight()) {
                DockKey(stringResource(R.string.voice_button), Icons.Filled.Mic, ui.onMic, Modifier.fillMaxSize(), RectangleShape, theme.accent, Color.White, theme.border, stacked = false, shadow = true, textSize = 3.6f)
                MicRing(ui.listening, theme.accent, Modifier.matchParentSize())
            }
            DockKey(stringResource(R.string.more_sheet_title), Icons.Filled.GridView, ui.onMore, Modifier.weight(1f).fillMaxHeight(), RectangleShape, theme.surface, theme.text, theme.border, stacked = false, shadow = true)
            SosKey(ui, Modifier.weight(1f).fillMaxHeight(), RectangleShape, border = theme.border, shadow = true)
        }
        Spacer(Modifier.height(dims.d(2.4f)))
    }
}

/** Black-and-yellow hazard stripes in the top band: drawn once, no animation. */
@Composable
private fun HazardStripes(modifier: Modifier) {
    val theme = LocalDriveTheme.current
    androidx.compose.foundation.Canvas(modifier.clip(RectangleShape)) {
        val step = size.height * 0.9f
        var x = -size.height
        while (x < size.width + size.height) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, size.height)
                lineTo(x + size.height, 0f)
                lineTo(x + size.height + step / 2f, 0f)
                lineTo(x + step / 2f, size.height)
                close()
            }
            drawPath(path, theme.onInk)
            x += step
        }
        drawLine(theme.ink, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f)
    }
}
