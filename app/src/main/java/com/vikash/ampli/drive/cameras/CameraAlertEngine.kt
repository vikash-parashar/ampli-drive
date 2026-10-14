package com.vikash.ampli.drive.cameras

import kotlin.math.roundToInt

/**
 * The alert state machine, free of Android types so it can be unit-tested.
 *
 * Each GPS fix goes through [update]. A camera is shown once it is ahead and within
 * [Config.alertDistanceM]; it is announced once per pass, beeps once at [BEEP_M] when the car is
 * over its limit, and is cleared as soon as it is no longer ahead (the car went past it). A camera
 * can only be announced again after the car has been more than [RESET_M] away from it.
 */
class CameraAlertEngine(var config: Config = Config()) {

    data class Config(
        val alertDistanceM: Int = 500,
        /** Announce only when the car is faster than the camera's limit. */
        val onlyOverLimit: Boolean = false,
    )

    data class Fix(val lat: Double, val lon: Double, val speedKmh: Double, val heading: Double?)

    data class Target(val camera: SpeedCamera, val distanceM: Double, val overLimit: Boolean)

    sealed interface Event {
        /** "Speed camera in [roundedMeters] metres, limit …". */
        data class Announce(val camera: SpeedCamera, val roundedMeters: Int) : Event

        /** The car is over this camera's limit: say "Slow down". */
        data class SlowDown(val camera: SpeedCamera) : Event

        /** Short beep close to the camera while still over the limit. */
        data class Beep(val camera: SpeedCamera) : Event

        data class Cleared(val cameraId: String) : Event
    }

    data class Result(val target: Target?, val events: List<Event>)

    private class Pass(val camera: SpeedCamera) {
        var announced = false
        var slowDown = false
        var beeped = false
    }

    private val passes = HashMap<String, Pass>()
    private var shownId: String? = null

    fun update(fix: Fix, candidates: Collection<SpeedCamera>): Result {
        val events = ArrayList<Event>(2)

        // Forget passes once the car is well away, so the next drive past alerts again.
        passes.values.removeAll {
            CameraGeometry.distanceM(fix.lat, fix.lon, it.camera.lat, it.camera.lon) > RESET_M
        }

        val ahead = CameraGeometry.nearestAhead(
            candidates, fix.lat, fix.lon, fix.heading, fix.speedKmh, CameraGeometry.LOOKAHEAD_M,
        )
        val shown = ahead?.takeIf { it.distanceMeters <= config.alertDistanceM }

        val previous = shownId
        if (previous != null && previous != shown?.camera?.id) events += Event.Cleared(previous)
        shownId = shown?.camera?.id
        if (shown == null) return Result(null, events)

        val cam = shown.camera
        val over = cam.maxspeed != null && fix.speedKmh > cam.maxspeed
        val pass = passes.getOrPut(cam.id) { Pass(cam) }
        if (!pass.announced && (!config.onlyOverLimit || over)) {
            pass.announced = true
            events += Event.Announce(cam, roundMeters(shown.distanceMeters))
        }
        if (over && !pass.slowDown) {
            pass.slowDown = true
            events += Event.SlowDown(cam)
        }
        if (over && shown.distanceMeters <= BEEP_M && !pass.beeped) {
            pass.beeped = true
            events += Event.Beep(cam)
        }
        return Result(Target(cam, shown.distanceMeters, over), events)
    }

    fun reset() {
        passes.clear()
        shownId = null
    }

    companion object {
        const val BEEP_M = 150.0
        const val RESET_M = 2_000.0

        /** Spoken distances in steps of 50 m ("500 metres", not "487 metres"). */
        fun roundMeters(m: Double): Int = ((m / 50.0).roundToInt() * 50).coerceAtLeast(50)
    }
}
