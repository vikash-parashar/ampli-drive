package com.vikash.ampli.drive.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
)

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
        if (!hasGps) return

        try {
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            _state.update {
                it.copy(
                    providerEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER),
                    location = last ?: it.location,
                )
            }
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            lm.registerGnssStatusCallback(appContext.mainExecutor, gnssCallback)
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

    private fun handleFix(location: Location) {
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

        val smoothed = raw?.let { smooth(it) }
        _state.update {
            it.copy(
                location = location,
                speedKmh = smoothed ?: it.speedKmh,
                providerEnabled = true,
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
    }
}
