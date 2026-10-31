package com.vikash.ampli.drive.cameras

import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Coming up on this road" (Pro) for the radar screen. Works it out every few seconds while the
 * screen is open ([start] / [stop]), on a background thread, from the camera grid index, the
 * average-speed zones and the live road reports near the car; along Car Hub's route when one is
 * running ([AheadList.alongRoute]), else along the GPS heading ([AheadList.byHeading]).
 */
class RadarAhead(private val app: DriveApp) {
    private val scope = MainScope()
    private var job: Job? = null
    private var users = 0

    private val _items = MutableStateFlow<List<AheadItem>>(emptyList())
    val items: StateFlow<List<AheadItem>> = _items.asStateFlow()

    /** True once a list was worked out with a GPS position (an empty list then means "clear road"). */
    private val _located = MutableStateFlow(false)
    val located: StateFlow<Boolean> = _located.asStateFlow()

    fun start() {
        users++
        if (job != null) return
        app.cameras.ensureLoaded()
        job = scope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_MS)
            }
        }
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            job?.cancel()
            job = null
        }
    }

    private suspend fun refresh() {
        if (!RadarTier.allowed(RadarFeature.AheadList, app.prefs.pro.value.isPro)) {
            _items.value = emptyList()
            return
        }
        val gps = app.speedTracker.state.value
        val here = gps.location
        if (here == null) {
            _located.value = false
            _items.value = emptyList()
            return
        }
        val moving = here.hasBearing() && (gps.speedKmh ?: 0f) >= CameraGeometry.SLOW_KMH
        val heading = if (moving) here.bearing.toDouble() else null
        val session = app.navigator.state.value
        val now = System.currentTimeMillis()
        val reports = app.roadReports.live(now)
        val lat = here.latitude
        val lon = here.longitude
        _items.value = withContext(Dispatchers.Default) {
            val cameras = ArrayList<SpeedCamera>(app.cameras.near(lat, lon, SEARCH_M))
            for (r in reports) cameras += r.asCamera()
            val zones = app.cameras.zonesNear(lat, lon, SEARCH_M)
            val times = reports.associate { it.id to it.time }
            val route = session.route
            val progress = session.progress
            if (session.visible && route != null && progress != null && route.points.size >= 2) {
                AheadList.alongRoute(route.points, route.cumM, progress.alongM, cameras, zones, times, maxAheadM = SEARCH_M)
            } else {
                AheadList.byHeading(lat, lon, heading, cameras, zones, times)
            }
        }
        _located.value = true
    }

    private companion object {
        const val REFRESH_MS = 4_000L
        const val SEARCH_M = 30_000.0
    }
}
