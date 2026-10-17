package com.vikash.ampli.drive.nav

import android.content.Context
import android.location.Location
import android.os.SystemClock
import com.vikash.ampli.drive.BuildConfig
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.OffRouteDetector
import com.vikash.ampli.drive.nav.route.OsrmRouteProvider
import com.vikash.ampli.drive.nav.route.RerouteLimiter
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteProgress
import com.vikash.ampli.drive.nav.route.RouteProvider
import com.vikash.ampli.drive.nav.route.RouteResult
import com.vikash.ampli.drive.nav.route.RouteTracker
import com.vikash.ampli.drive.voice.Place
import com.vikash.ampli.drive.voice.PlaceSearch
import com.vikash.ampli.drive.voice.VoiceActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Where the driver is going. Coordinates are null when only a name is known (search failed). */
data class NavDestination(val name: String, val area: String? = null, val lat: Double? = null, val lon: Double? = null) {
    val point: LatLon? get() = if (lat != null && lon != null) LatLon(lat, lon) else null

    companion object {
        fun of(place: Place) = NavDestination(place.name, place.area, place.lat, place.lon)
    }
}

/** One in-app navigation session, as the map, banner and bottom bar show it. */
data class NavSession(
    val phase: Phase = Phase.Idle,
    val destination: NavDestination? = null,
    val route: Route? = null,
    /** Bumped on every new route (first route and each reroute). */
    val routeVersion: Int = 0,
    val progress: RouteProgress? = null,
    /** Off the route: a new one is being fetched while the old one stays on screen. */
    val rerouting: Boolean = false,
    val failure: Failure? = null,
) {
    enum class Phase { Idle, Routing, Active, Failed, Arrived }

    enum class Failure { NoLocation, Network, NoRoute, Busy, Server, PlaceNotFound }

    /** The map and the home card show navigation for these phases. */
    val visible: Boolean get() = phase != Phase.Idle
}

/** Settings › About › Diagnostics › Navigation. */
data class NavDiagnostics(
    val server: String,
    val demoServer: Boolean,
    val lastStatus: String = "(no route yet)",
    val lastAtMs: Long = 0L,
    val lastTookMs: Long = 0L,
    val steps: Int = 0,
    val routeKm: Double = 0.0,
    val hasSpeedLimits: Boolean = false,
    val offRouteCount: Int = 0,
    val rerouteCount: Int = 0,
)

/**
 * Turn-by-turn inside Car Hub: routes from [RouteProvider] (OSRM), follows the GPS fixes along
 * the route ([RouteTracker]), reroutes when the car leaves it, and hands each manoeuvre to
 * [NavPrompter] to speak. Main thread; network on Dispatchers.IO.
 */
class InAppNavigator(private val app: DriveApp) {
    private val scope = MainScope()

    val provider: RouteProvider = OsrmRouteProvider(
        baseUrl = BuildConfig.ROUTING_BASE_URL.ifBlank { OsrmRouteProvider.DEMO_URL },
        userAgent = "AmpliCarHub/${BuildConfig.VERSION_NAME} (Android head-unit launcher)",
    )

    private val _state = MutableStateFlow(NavSession())
    val state: StateFlow<NavSession> = _state.asStateFlow()

    /** Voice prompts for in-app navigation (the speaker button on the navigation screen). */
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** Non-zero: the UI should show the navigation screen. DriveRoot resets it. */
    private val _openRequest = MutableStateFlow(0)
    val openRequest: StateFlow<Int> = _openRequest.asStateFlow()

    private val _diag = MutableStateFlow(
        NavDiagnostics(server = provider.serverLabel, demoServer = provider.serverLabel == OsrmRouteProvider.DEMO_URL),
    )
    val diagnostics: StateFlow<NavDiagnostics> = _diag.asStateFlow()

    private var tracker: RouteTracker? = null
    private val offRoute = OffRouteDetector()
    private val limiter = RerouteLimiter()
    private var routeJob: Job? = null
    private var arrivedJob: Job? = null
    private var fixJob: Job? = null

    fun setMuted(value: Boolean) {
        _muted.value = value
    }

    fun consumeOpenRequest() {
        _openRequest.value = 0
    }

    /** Starts navigating to [dest] (replacing any trip in progress) and asks the UI to show it. */
    fun start(dest: NavDestination) {
        val point = dest.point ?: return startByName(dest.name)
        reset()
        _state.value = NavSession(phase = NavSession.Phase.Routing, destination = dest)
        _openRequest.value++
        ensureFollowing()
        fetch(dest, point, reroute = false)
    }

    fun start(place: Place) = start(NavDestination.of(place))

    /** Only a name (the place list failed earlier): search it, take the nearest match, route. */
    fun startByName(query: String) {
        reset()
        val dest = NavDestination(query)
        _state.value = NavSession(phase = NavSession.Phase.Routing, destination = dest)
        _openRequest.value++
        ensureFollowing()
        routeJob = scope.launch {
            val here = app.speedTracker.state.value.location
            val places = try {
                PlaceSearch.search(query, here)
            } catch (e: Exception) {
                fail(dest, NavSession.Failure.Network, "Place search: ${e.message}")
                return@launch
            }
            val best = places.firstOrNull()
            if (best == null) {
                fail(dest, NavSession.Failure.PlaceNotFound, "No place matched \"$query\"")
                return@launch
            }
            val found = NavDestination.of(best)
            _state.value = NavSession(phase = NavSession.Phase.Routing, destination = found)
            fetch(found, LatLon(best.lat, best.lon), reroute = false)
        }
    }

    /** Place search failed before navigation could start: show the failure with Try again. */
    fun failSearch(query: String, network: Boolean) {
        reset()
        val dest = NavDestination(query)
        _state.value = NavSession(phase = NavSession.Phase.Failed, destination = dest,
            failure = if (network) NavSession.Failure.Network else NavSession.Failure.PlaceNotFound)
        _openRequest.value++
    }

    /** "Try again" on the failure card. */
    fun retry() {
        val dest = _state.value.destination ?: return
        start(dest)
    }

    /** The End button, or arriving. */
    fun stop() {
        reset()
        _state.value = NavSession()
    }

    /** "Open in Google Maps": hands this trip to Maps and ends Ampli's own guidance. */
    fun openInGoogleMaps(context: Context): Boolean {
        val dest = _state.value.destination ?: return false
        val ok = if (dest.lat != null && dest.lon != null) {
            VoiceActions.startNavigation(context, Place(dest.name, dest.area, dest.lat, dest.lon, null))
        } else {
            VoiceActions.startNavigationByName(context, dest.name)
        }
        if (ok) stop()
        return ok
    }

    private fun reset() {
        routeJob?.cancel()
        arrivedJob?.cancel()
        tracker = null
        offRoute.reset()
        limiter.reset()
        app.navPrompter.onInAppTurn(null)
    }

    private fun ensureFollowing() {
        if (fixJob?.isActive == true) return
        fixJob = scope.launch {
            app.speedTracker.state.map { it.location }.distinctUntilChanged().collect { loc ->
                if (loc != null) onFix(loc)
            }
        }
    }

    private fun fetch(dest: NavDestination, to: LatLon, reroute: Boolean) {
        routeJob?.cancel()
        routeJob = scope.launch {
            val here = withTimeoutOrNull(FIX_WAIT_MS) {
                app.speedTracker.state.map { it.location }.first { it != null }
            }
            if (here == null) {
                if (!reroute) fail(dest, NavSession.Failure.NoLocation, "No GPS fix")
                return@launch
            }
            val moving = here.hasBearing() && (app.speedTracker.state.value.speedKmh ?: 0f) >= MOVING_KMH
            val started = SystemClock.elapsedRealtime()
            val result = withContext(Dispatchers.IO) {
                provider.route(LatLon(here.latitude, here.longitude), to, if (reroute && moving) here.bearing else null)
            }
            val took = SystemClock.elapsedRealtime() - started
            when (result) {
                is RouteResult.Ok -> {
                    val route = result.route
                    val t = RouteTracker(route)
                    tracker = t
                    offRoute.reset()
                    val progress = t.update(LatLon(here.latitude, here.longitude))
                    val version = _state.value.routeVersion + 1
                    _state.value = NavSession(
                        phase = NavSession.Phase.Active,
                        destination = dest,
                        route = route,
                        routeVersion = version,
                        progress = progress,
                    )
                    _diag.update {
                        it.copy(
                            lastStatus = (if (reroute) "Rerouted" else "OK") + " · ${route.steps.size} steps",
                            lastAtMs = System.currentTimeMillis(),
                            lastTookMs = took,
                            steps = route.steps.size,
                            routeKm = route.distanceM / 1000.0,
                            hasSpeedLimits = route.maxspeedKmh?.any { l -> l != null } == true,
                            rerouteCount = it.rerouteCount + if (reroute) 1 else 0,
                        )
                    }
                    // Haryanvi desi: "चाल, रास्ता मिल गया, 42 किलोमीटर सै।" (first route only).
                    if (!reroute) app.navPrompter.onRouteFound(route.distanceM, route.durationS)
                    speak(progress)
                }
                is RouteResult.Failed -> {
                    _diag.update {
                        it.copy(
                            lastStatus = "FAILED ${result.reason.name}" + (result.detail?.let { d -> " · $d" } ?: ""),
                            lastAtMs = System.currentTimeMillis(),
                            lastTookMs = took,
                        )
                    }
                    if (reroute) {
                        // Keep guiding on the old route; the next off-route check tries again.
                        _state.update { it.copy(rerouting = false) }
                    } else {
                        fail(dest, failureOf(result.reason), result.detail)
                    }
                }
            }
        }
    }

    private fun failureOf(r: RouteResult.Reason) = when (r) {
        RouteResult.Reason.Network -> NavSession.Failure.Network
        RouteResult.Reason.NoRoute -> NavSession.Failure.NoRoute
        RouteResult.Reason.Busy -> NavSession.Failure.Busy
        RouteResult.Reason.Server -> NavSession.Failure.Server
    }

    private fun fail(dest: NavDestination, failure: NavSession.Failure, detail: String?) {
        tracker = null
        _state.value = NavSession(phase = NavSession.Phase.Failed, destination = dest, failure = failure)
        if (failure == NavSession.Failure.NoLocation || failure == NavSession.Failure.PlaceNotFound) {
            _diag.update { it.copy(lastStatus = "FAILED ${failure.name}" + (detail?.let { d -> " · $d" } ?: ""), lastAtMs = System.currentTimeMillis()) }
        }
    }

    private fun onFix(loc: Location) {
        val s = _state.value
        val t = tracker ?: return
        if (s.phase != NavSession.Phase.Active) return
        val p = t.update(LatLon(loc.latitude, loc.longitude))
        if (p.arrived) {
            _state.value = s.copy(phase = NavSession.Phase.Arrived, progress = p, rerouting = false)
            speak(p)
            tracker = null
            // The arrival card stays a while, then the map goes back to normal by itself.
            arrivedJob?.cancel()
            arrivedJob = scope.launch {
                // Parked after arriving (stood still a while): the Haryanvi goodbye, once.
                val end = SystemClock.elapsedRealtime() + ARRIVED_SHOW_MS
                var stillSince = -1L
                while (SystemClock.elapsedRealtime() < end && _state.value.phase == NavSession.Phase.Arrived) {
                    val now = SystemClock.elapsedRealtime()
                    val kmh = app.speedTracker.state.value.speedKmh
                    if (kmh != null && kmh < PARKED_KMH) {
                        if (stillSince < 0) stillSince = now
                        if (now - stillSince >= PARKED_MS) {
                            app.navPrompter.onParkedAfterArrival()
                            break
                        }
                    } else {
                        stillSince = -1L
                    }
                    delay(2_000L)
                }
                delay((end - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                if (_state.value.phase == NavSession.Phase.Arrived) stop()
            }
            return
        }
        val accuracy = if (loc.hasAccuracy()) loc.accuracy else null
        if (offRoute.onFix(p.distanceFromRouteM, accuracy, SystemClock.elapsedRealtime())) {
            _diag.update { it.copy(offRouteCount = it.offRouteCount + 1) }
            val dest = s.destination
            val to = dest?.point
            if (dest != null && to != null && !s.rerouting && limiter.tryAcquire(SystemClock.elapsedRealtime())) {
                _state.value = s.copy(progress = p, rerouting = true)
                // Haryanvi desi: "रास्ता भटक गया भाई, नया रास्ता देख रया सूं।"
                app.navPrompter.onRerouting()
                fetch(dest, to, reroute = true)
                return
            }
        }
        _state.value = s.copy(progress = p)
        if (p.distanceFromRouteM <= RouteTracker.ON_ROUTE_M) speak(p)
    }

    /** The next thing to say, as a [ParsedTurn] for the shared prompt planner. */
    private fun speak(p: RouteProgress) {
        val s = _state.value
        val route = s.route ?: return
        val step = route.steps.getOrNull(p.stepIndex) ?: return
        val d = p.distanceToStepM.roundToInt()
        val turn = if (step.maneuver != Maneuver.Arrive && d > LONG_STRAIGHT_M) {
            // A long way to the next turn: "keep going straight" once.
            ParsedTurn(Maneuver.Continue, d, null, "v${s.routeVersion}|c${p.stepIndex}")
        } else {
            ParsedTurn(step.maneuver, if (s.phase == NavSession.Phase.Arrived) 0 else d, step.exitNumber, "v${s.routeVersion}|s${p.stepIndex}")
        }
        app.navPrompter.onInAppTurn(turn)
    }

    private companion object {
        const val FIX_WAIT_MS = 15_000L
        const val MOVING_KMH = 8f
        const val ARRIVED_SHOW_MS = 60_000L

        /** Arrived and below this speed for [PARKED_MS]: parked, say goodbye. */
        const val PARKED_KMH = 3f
        const val PARKED_MS = 20_000L

        /** Further than this to the next manoeuvre: say "keep going straight" instead. */
        const val LONG_STRAIGHT_M = 2_000
    }
}
