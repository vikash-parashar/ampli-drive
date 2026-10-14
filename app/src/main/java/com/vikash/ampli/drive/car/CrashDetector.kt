package com.vikash.ampli.drive.car

import kotlin.math.sqrt

/**
 * Crash heuristics, pure logic:
 * - an impact of [impactG] or more on the head unit's accelerometer, or
 * - GPS speed falling from [fromKmh] or more to [toKmh] or less within [windowMs].
 *
 * After a trigger it stays quiet for [cooldownMs] so one crash starts one countdown.
 * The SOS countdown that follows has a big Cancel button, so a false alarm costs a tap.
 */
class CrashDetector(
    private val impactG: Double = 4.0,
    private val fromKmh: Float = 30f,
    private val toKmh: Float = 3f,
    private val windowMs: Long = 1500L,
    private val cooldownMs: Long = 60_000L,
) {
    private val gravity = DoubleArray(3)
    private var gravityReady = false
    private var lastTrigger = Long.MIN_VALUE / 2
    private val speeds = ArrayDeque<Pair<Long, Float>>()

    /** Largest impact seen, in g (for the settings screen's "sensor works" check). */
    var peakG: Double = 0.0
        private set

    /**
     * One accelerometer sample in m/s². [includesGravity] is true for TYPE_ACCELEROMETER;
     * gravity is then removed with a low-pass filter. TYPE_LINEAR_ACCELERATION passes false.
     */
    fun onAccel(now: Long, x: Float, y: Float, z: Float, includesGravity: Boolean): Boolean {
        var ax = x.toDouble()
        var ay = y.toDouble()
        var az = z.toDouble()
        if (includesGravity) {
            if (!gravityReady) {
                gravity[0] = ax; gravity[1] = ay; gravity[2] = az
                gravityReady = true
            } else {
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * ax
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * ay
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * az
            }
            ax -= gravity[0]; ay -= gravity[1]; az -= gravity[2]
        }
        val g = sqrt(ax * ax + ay * ay + az * az) / STANDARD_GRAVITY
        if (g > peakG) peakG = g
        return g >= impactG && fire(now)
    }

    /** One raw (unsmoothed) GPS speed sample in km/h. */
    fun onSpeed(now: Long, kmh: Float): Boolean {
        while (speeds.isNotEmpty() && now - speeds.first().first > windowMs) speeds.removeFirst()
        val wasFast = speeds.any { it.second >= fromKmh }
        speeds.addLast(now to kmh)
        return kmh <= toKmh && wasFast && fire(now)
    }

    private fun fire(now: Long): Boolean {
        if (now - lastTrigger < cooldownMs) return false
        lastTrigger = now
        speeds.clear()
        return true
    }

    companion object {
        const val STANDARD_GRAVITY = 9.80665
        private const val ALPHA = 0.8
    }
}
