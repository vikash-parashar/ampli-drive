package com.vikash.ampli.drive.system

import kotlin.math.roundToInt

/**
 * The rail's Vol + / Vol − keys: one step per tap, and while held, a step after [FIRST_REPEAT_MS]
 * and then every [REPEAT_MS]. Pure, so the timing is unit-tested.
 */
object VolumeHold {
    const val FIRST_REPEAT_MS = 400L
    const val REPEAT_MS = 130L

    /** The level popup ("आवाज़ 9/15") hides this long after the last change. */
    const val POPUP_MS = 1_600L

    /** The wait before repeat number [repeat] (1 = the first repeat after the touch's own step). */
    fun delayBefore(repeat: Int): Long = if (repeat <= 1) FIRST_REPEAT_MS else REPEAT_MS

    /** Steps taken by a key held for [heldMs]: one on touch, then the repeats. */
    fun stepsAfter(heldMs: Long): Int =
        if (heldMs < FIRST_REPEAT_MS) 1 else 2 + ((heldMs - FIRST_REPEAT_MS) / REPEAT_MS).toInt()
}

/** Music volume as the rail shows it. Pure. */
object RailVolume {
    /** Segments in the popup's bar: one per step on a 15-step unit, at most 15 on larger scales. */
    const val MAX_SEGMENTS = 15

    data class Level(val level: Int, val max: Int, val muted: Boolean)

    /** One tap of + ([delta] 1) or − ([delta] -1). Either key un-mutes, as on a phone. */
    fun step(current: Level, delta: Int): Level =
        Level((current.level + delta).coerceIn(0, current.max.coerceAtLeast(0)), current.max, muted = false)

    /** Un-muting a stream that sits at 0 is still silent: bring it to [remembered] or a third. */
    fun unmuteLevel(level: Int, max: Int, remembered: Int? = null): Int = when {
        level > 0 -> level
        remembered != null && remembered > 0 -> remembered.coerceAtMost(max)
        else -> (max / 3).coerceAtLeast(1)
    }

    fun segments(max: Int): Int = max.coerceIn(1, MAX_SEGMENTS)

    /** How many of [segments] are filled for [level] of [max]. */
    fun filled(level: Int, max: Int, segments: Int = segments(max)): Int =
        if (max <= 0) 0 else (level.coerceIn(0, max) * segments.toDouble() / max).roundToInt().coerceIn(0, segments)
}
