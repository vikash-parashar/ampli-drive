package com.vikash.ampli.drive.ui.theme

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** WCAG 2 contrast maths on ARGB colours (Long, as written in the theme tables). No Compose needed. */
object Contrast {

    /** WCAG AA for normal text. */
    const val AA_TEXT = 4.5

    /** WCAG AA for large text and UI parts (gauges, icons). */
    const val AA_LARGE = 3.0

    private fun channel(argb: Long, shift: Int): Double = ((argb shr shift) and 0xFF).toDouble() / 255.0

    private fun linear(c: Double): Double = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    /** Relative luminance of the colour's RGB (alpha ignored). */
    fun luminance(argb: Long): Double =
        0.2126 * linear(channel(argb, 16)) + 0.7152 * linear(channel(argb, 8)) + 0.0722 * linear(channel(argb, 0))

    /** Contrast ratio between two opaque colours, 1.0 to 21.0. */
    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [fg] painted over opaque [bg] with fg's alpha (source-over). The result is opaque. */
    fun over(fg: Long, bg: Long): Long {
        val a = channel(fg, 24)
        var rgb = 0L
        for (shift in intArrayOf(16, 8, 0)) {
            val v = (channel(fg, shift) * a + channel(bg, shift) * (1 - a)) * 255.0
            rgb = rgb or (v.roundToInt().coerceIn(0, 255).toLong() shl shift)
        }
        return 0xFF000000L or rgb
    }

    /**
     * Lowest contrast of [text] on a translucent [panel] over any of [backdrop]'s colours.
     * A blur only averages backdrop colours, so this is a safe lower bound for frosted glass too.
     */
    fun worstOnGlass(text: Long, panel: Long, backdrop: List<Long>): Double =
        backdrop.minOf { ratio(over(text, over(panel, it)), over(panel, it)) }
}
