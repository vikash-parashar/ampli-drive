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
     * CIE76 colour difference (ΔE*ab, D65) between two opaque colours. About 2.3 is the smallest
     * difference people notice; 10 or more reads as a different colour at a glance.
     */
    fun deltaE(a: Long, b: Long): Double {
        val la = lab(a)
        val lb = lab(b)
        return kotlin.math.sqrt((la[0] - lb[0]).pow(2) + (la[1] - lb[1]).pow(2) + (la[2] - lb[2]).pow(2))
    }

    private fun lab(argb: Long): DoubleArray {
        val r = linear(channel(argb, 16))
        val g = linear(channel(argb, 8))
        val b = linear(channel(argb, 0))
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) Math.cbrt(t) else (24389.0 / 27.0 * t + 16.0) / 116.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /**
     * Lowest contrast of [text] on a translucent [panel] over any of [backdrop]'s colours.
     * A blur only averages backdrop colours, so this is a safe lower bound for frosted glass too.
     */
    fun worstOnGlass(text: Long, panel: Long, backdrop: List<Long>): Double =
        backdrop.minOf { ratio(over(text, over(panel, it)), over(panel, it)) }
}
