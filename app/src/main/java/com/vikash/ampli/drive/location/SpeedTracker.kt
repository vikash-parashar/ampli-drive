package com.vikash.ampli.drive.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vikash.ampli.drive.cameras.CameraAlerts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.CopyOnWriteArrayList

data class GpsState(
    /** ACCESS_FINE_LOCATION granted (GPS_PROVIDER needs it). */
    val hasPermission: Boolean = false,
    /** The unit has a GPS provider at all. */
    val hasGps: Boolean = true,
    /** Location is switched on in system settings. */
    val providerEnabled: Boolean = false,
    /** Latest fix (may be the last known one right after start). */
    val location: Location? = null,
    /** Smoothed speed in km/h, or null when there is no fresh fix. */
    val speedKmh: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesInView: Int = 0,
    /** Where [location] came from. Network fixes only place the map; they never drive the speedometer. */
    val source: LocationSource = LocationSource.None,
    /** No GPS fix since GPS started, for long enough to say so (indoors, garage, bad antenna). */
    val noFixLong: Boolean = false,
    /** Trip average over moving time (see [TripAverage]), km/h. */
    val avgKmh: Float? = null,
    /** Last speed-giving fix: elapsedRealtime ms, provider name and whether it carried a speed. */
    val lastFixElapsedMs: Long = 0L,
    val lastFixProvider: String? = null,
    val lastFixHasSpeed: Boolean = false,
)

enum class LocationSource { None, LastKnown, Gps, Network, Passive, Fused }

/** Gets every GPS fix: [smoothedKmh] is what the speedometer shows, [rawKmh] the unsmoothed reading. */
fun interface FixListener {
    fun onFix(location: Location, smoothedKmh: Float, rawKmh: Float?)
}

/**
 * Reads the head unit's own GPS through LocationManager (no Play Services needed).
 * Start it when the home screen becomes visible and stop it when it hides.
 */
class SpeedTracker(
    context: Context,
    private val cameraAlerts: CameraAlerts,
) {
    private val appContext = context.applicationContext
    private val lm: LocationManager? = appContext.getSystemService(LocationManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(GpsState())
    val state: StateFlow<GpsState> = _state.asStateFlow()

    private var running = false
    private var lastFix: Location? = null
    private var lastFixAt = 0L
    private val samples = ArrayDeque<Float>()
    private val fixListeners = CopyOnWriteArrayList<FixListener>()

    /** Called when GPS stops (launcher hidden without the drive monitor), with the last fix. */
    var onStopped: ((Location?) -> Unit)? = null

    /** True between [start] and [stop]. */
    val isRunning: Boolean get() = running

    fun addFixListener(listener: FixListener) {
        fixListeners.addIfAbsent(listener)
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleFix(location)

        override fun onProviderEnabled(provider: String) {
            _state.update { it.copy(providerEnabled = true) }
        }

        override fun onProviderDisabled(provider: String) {
            samples.clear()
            _state.update { it.copy(providerEnabled = false, speedKmh = null, satellitesUsed = 0) }
        }

        // Abstract before Android 11: without it, an Android 10 unit that reports a GPS status
        // change would throw AbstractMethodError.
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            val inView = status.satelliteCount
            _state.update { it.copy(satellitesUsed = used, satellitesInView = inView) }
        }

        override fun onStopped() {
            _state.update { it.copy(satellitesUsed = 0, satellitesInView = 0) }
        }
    }

    private var gpsFixSinceStart = false
    private var networkOn = false

    /** Network fixes for the map while GPS has none: indoors, or a unit with a weak GPS antenna. */
    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // A fresh GPS fix always wins.
            if (lastFixAt != 0L && SystemClock.elapsedRealtime() - lastFixAt < STALE_MS) return
            _state.update { it.copy(location = location, source = LocationSource.Network) }
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    /** Two minutes without a GPS fix: say so, and place the map from the network provider. */
    private val noFixCheck = Runnable {
        if (!running || gpsFixSinceStart) return@Runnable
        _state.update { it.copy(noFixLong = true) }
        startNetwork()
    }

    private val trip = TripAverage()
    private var passiveOn = false
    private var fusedOn = false

    /**
     * Fixes other apps (Google Maps, the unit's own navigation) get through any path, including
     * vendor GPS stacks. A "gps" fix here counts as our own; others only place the map.
     */
    private val passiveListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                val prev = lastFix
                if (prev == null || location.elapsedRealtimeNanos > prev.elapsedRealtimeNanos) handleFix(location, LocationSource.Passive)
            } else {
                networkListener.onLocationChanged(location)
            }
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    /** "fused" from LocationManager (Play services or the vendor), when the unit lists it. Used only without GPS fixes. */
    private val fusedListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (lastFixAt != 0L && SystemClock.elapsedRealtime() - lastFixAt < STALE_MS && _state.value.source == LocationSource.Gps) return
            handleFix(location, LocationSource.Fused)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    }

    /** Ignition on: a new trip for the average speed. */
    fun resetTrip() {
        trip.reset()
        _state.update { it.copy(avgKmh = null) }
    }

    @SuppressLint("MissingPermission")
    private fun startPassive() {
        val m = lm ?: return
        if (passiveOn) return
        try {
            if (!m.allProviders.contains(LocationManager.PASSIVE_PROVIDER)) return
            m.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, passiveListener, Looper.getMainLooper())
            passiveOn = true
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFused() {
        val m = lm ?: return
        if (fusedOn) return
        try {
            if (!m.allProviders.contains(FUSED) || !m.isProviderEnabled(FUSED)) return
            m.requestLocationUpdates(FUSED, UPDATE_INTERVAL_MS, 0f, fusedListener, Looper.getMainLooper())
            fusedOn = true
        } catch (_: Exception) {
        }
    }

    private fun stopExtras() {
        try {
            if (passiveOn) lm?.removeUpdates(passiveListener)
            if (fusedOn) lm?.removeUpdates(fusedListener)
        } catch (_: Exception) {
        }
        passiveOn = false
        fusedOn = false
    }

    @SuppressLint("MissingPermission")
    private fun startNetwork() {
        val m = lm ?: return
        startFused()
        if (networkOn) return
        try {
            if (!m.allProviders.contains(LocationManager.NETWORK_PROVIDER) || !m.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return
            if (_state.value.location == null) {
                m.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { loc ->
                    _state.update { it.copy(location = loc, source = LocationSource.Network) }
                }
            }
            m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, NETWORK_INTERVAL_MS, 0f, networkListener, Looper.getMainLooper())
            networkOn = true
        } catch (_: Exception) {
            // No network provider permission or provider on this ROM: the map waits for GPS.
        }
    }

    private fun stopNetwork() {
        if (!networkOn) return
        networkOn = false
        try {
            lm?.removeUpdates(networkListener)
        } catch (_: Exception) {
        }
    }

    /** Names of the location providers this unit has, for Settings › About › Diagnostics. */
    fun providers(): List<String> = try {
        lm?.allProviders.orEmpty().map { p -> p + if (lm?.isProviderEnabled(p) == true) "" else " (off)" }
    } catch (_: Exception) {
        emptyList()
    }

    /** Clears the speed when fixes stop arriving (tunnel, parking garage). */
    private val staleCheck = object : Runnable {
        override fun run() {
            if (!running) return
            val age = SystemClock.elapsedRealtime() - lastFixAt
            if (lastFixAt != 0L && age > STALE_MS && _state.value.speedKmh != null) {
                samples.clear()
                _state.update { it.copy(speedKmh = null) }
            }
            main.postDelayed(this, STALE_CHECK_MS)
        }
    }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        val granted = hasPermission()
        _state.update { it.copy(hasPermission = granted) }
        if (running || !granted || lm == null) return

        val hasGps = lm.allProviders.contains(LocationManager.GPS_PROVIDER)
        _state.update { it.copy(hasGps = hasGps) }
        if (!hasGps) {
            // No GPS chip at all: the map can still follow the network position.
            running = true
            startPassive()
            startNetwork()
            return
        }

        try {
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            _state.update {
                it.copy(
                    providerEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER),
                    location = last ?: it.location,
                    source = if (last != null) LocationSource.LastKnown else it.source,
                    noFixLong = false,
                )
            }
            gpsFixSinceStart = false
            main.postDelayed(noFixCheck, NO_FIX_FALLBACK_MS)
            startPassive()
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssStatusCallback(appContext.mainExecutor, gnssCallback)
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssStatusCallback(gnssCallback, main)
            }
            running = true
            main.postDelayed(staleCheck, STALE_CHECK_MS)
        } catch (e: SecurityException) {
            _state.update { it.copy(hasPermission = false) }
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(hasGps = false) }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        main.removeCallbacks(staleCheck)
        main.removeCallbacks(noFixCheck)
        stopNetwork()
        stopExtras()
        try {
            lm?.removeUpdates(listener)
            lm?.unregisterGnssStatusCallback(gnssCallback)
        } catch (_: Exception) {
            // Some ROMs throw if the provider vanished; nothing to clean up then.
        }
        samples.clear()
        cameraAlerts.reset()
        _state.update { it.copy(speedKmh = null, satellitesUsed = 0, satellitesInView = 0) }
        onStopped?.invoke(lastFix)
    }

    private fun handleFix(location: Location, source: LocationSource = LocationSource.Gps) {
        val prev = lastFix
        val raw: Float? = when {
            location.hasSpeed() -> location.speed * MS_TO_KMH
            prev != null -> {
                val dt = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9f
                if (dt in 0.5f..5f) prev.distanceTo(location) / dt * MS_TO_KMH else null
            }
            else -> null
        }
        lastFix = location
        lastFixAt = SystemClock.elapsedRealtime()
        if (!gpsFixSinceStart && source != LocationSource.Fused) {
            gpsFixSinceStart = true
            main.removeCallbacks(noFixCheck)
            stopNetwork()
        }

        val smoothed = raw?.let { smooth(it) }
        if (smoothed != null) trip.onSpeed(lastFixAt, smoothed)
        val avg = trip.averageKmh
        _state.update {
            it.copy(
                location = location,
                speedKmh = smoothed ?: it.speedKmh,
                providerEnabled = true,
                source = source,
                noFixLong = false,
                avgKmh = avg,
                lastFixElapsedMs = lastFixAt,
                lastFixProvider = location.provider,
                lastFixHasSpeed = location.hasSpeed(),
            )
        }
        if (smoothed != null) {
            cameraAlerts.onLocation(location, smoothed)
            for (l in fixListeners) l.onFix(location, smoothed, raw)
        }
    }

    /** Median of the last three readings removes single-fix spikes; tiny values read as 0. */
    private fun smooth(kmh: Float): Float {
        samples.addLast(kmh.coerceIn(0f, 300f))
        while (samples.size > 3) samples.removeFirst()
        val median = samples.sorted()[samples.size / 2]
        return if (median < STANDSTILL_KMH) 0f else median
    }

    private companion object {
        const val MS_TO_KMH = 3.6f
        const val UPDATE_INTERVAL_MS = 1000L
        const val STALE_MS = 5000L
        const val STALE_CHECK_MS = 2000L
        const val STANDSTILL_KMH = 2f
        const val NO_FIX_FALLBACK_MS = 120_000L
        /** LocationManager.FUSED_PROVIDER is API 31, but many older ROMs list a "fused" provider too. */
        const val FUSED = "fused"
        const val NETWORK_INTERVAL_MS = 10_000L
    }
}
