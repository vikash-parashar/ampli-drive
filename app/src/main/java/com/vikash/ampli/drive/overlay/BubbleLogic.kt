package com.vikash.ampli.drive.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

/** What the floating speed bubble shows. Pure, so it is unit-tested. */
data class BubbleModel(
    /** Rounded km/h, or null without a fresh fix ("--"). */
    val speed: Int?,
    /** Limit of the camera ahead, when known. */
    val limit: Int?,
    /** Distance to the next camera in metres, or null when none is ahead. */
    val cameraM: Int?,
    /** Faster than [limit]: the bubble turns red. */
    val over: Boolean,
)

object BubbleLogic {

    /** Movement below this many pixels is a tap, not a drag. */
    const val TAP_SLOP_PX = 12

    fun model(speedKmh: Float?, limitKmh: Int?, cameraM: Int?): BubbleModel {
        val speed = speedKmh?.roundToInt()
        // Same rule as the speed pane: red as soon as the speed is above the limit.
        val over = speedKmh != null && limitKmh != null && speedKmh > limitKmh
        return BubbleModel(speed, limitKmh, cameraM, over)
    }

    /** "450 m", "1.2 km". */
    fun distanceText(m: Int): String = if (m < 1000) "${(m / 10) * 10} m" else "%.1f km".format(java.util.Locale.US, m / 1000.0)

    /**
     * Keeps the bubble fully on screen. [x], [y] are its top-left corner in pixels; a negative
     * value (never placed) puts it at the default spot: right edge, a third of the way down.
     */
    fun clamp(x: Int, y: Int, width: Int, height: Int, screenW: Int, screenH: Int): Pair<Int, Int> {
        val maxX = (screenW - width).coerceAtLeast(0)
        val maxY = (screenH - height).coerceAtLeast(0)
        if (x < 0 || y < 0) return maxX to (screenH / 3).coerceIn(0, maxY)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }

    fun isTap(dx: Float, dy: Float): Boolean = abs(dx) < TAP_SLOP_PX && abs(dy) < TAP_SLOP_PX
}
