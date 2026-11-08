package com.vikash.ampli.drive.ui

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Settings › Speedometer's four styles (Neon Arc, Halo Ring, Road Strip, Retro Dial): colours,
 * bands and motion, from the approved speedometer board. Plain Kotlin (colours are ARGB ints), so
 * it is unit-tested; [Speedometer] only draws what this says.
 */
internal object SpeedoMath {
    /** Every style's scale ends at 140 km/h, as on the board. */
    const val VMAX = 140f

    /** Lit segments of Neon Arc's 270° sweep and LEDs of Retro Dial's ring. */
    const val NEON_SEGMENTS = 56
    const val RETRO_LEDS = 28

    /** Speed bands: 0-40 "Aaram se", 40-80 "Cruise", 80 to the limit "Tez", over it "Dheere karo". */
    enum class Band { Calm, Cruise, Fast, Over }

    /**
     * One palette's speed colours. [over] is the over-limit red: #FF5C47 at night, #E8492C by day.
     * The SOS red (#D7263D) is never used here.
     */
    class Palette(val calm: Int, val cruise: Int, val fast: Int, val over: Int)

    val NIGHT = Palette(calm = 0xFF6FD9A8.toInt(), cruise = 0xFF39C6F4.toInt(), fast = 0xFFFFB13B.toInt(), over = 0xFFFF5C47.toInt())
    val DAY = Palette(calm = 0xFF0F9D77.toInt(), cruise = 0xFF0A72C2.toInt(), fast = 0xFFC97700.toInt(), over = 0xFFE8492C.toInt())

    fun palette(light: Boolean): Palette = if (light) DAY else NIGHT

    /** Glow strength: full at night, half by day (bright glows wash out in sunlight). */
    fun glow(light: Boolean): Float = if (light) 0.5f else 1f

    fun share(kmh: Float): Float = (kmh / VMAX).coerceIn(0f, 1f)

    /** Over the limit once the shown speed passes it by half a km/h; never without a known limit. */
    fun over(kmh: Float, limit: Int?): Boolean = limit != null && kmh > limit + 0.5f

    fun band(kmh: Float, limit: Int?): Band = when {
        over(kmh, limit) -> Band.Over
        kmh >= 80f -> Band.Fast
        kmh >= 40f -> Band.Cruise
        else -> Band.Calm
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * The band colour for [kmh]: teal-green blends into cyan around 40 (36 to 44), cyan into amber
     * around 80 (76 to 84), and everything into the palette's red as [overT] goes 0 to 1.
     */
    fun bandColor(kmh: Float, overT: Float, p: Palette): Int {
        var c = mix(p.calm, p.cruise, smoothstep(36f, 44f, kmh))
        c = mix(c, p.fast, smoothstep(76f, 84f, kmh))
        return mix(c, p.over, overT.coerceIn(0f, 1f))
    }

    /** sRGB blend of two opaque-or-not ARGB colours. */
    fun mix(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The over-limit pulse, 0..1 (about 1.4 a second). Steady at 1 with reduced motion. */
    fun pulse(clockS: Float, reduced: Boolean): Float = if (reduced) 1f else 0.5f + 0.5f * sin(clockS * 8.8f)

    /** The GPS dot's ripple, 0..1 every 1.4 s; none (0) with reduced motion. */
    fun ripple(clockS: Float, reduced: Boolean): Float = if (reduced) 0f else (clockS / 1.4f) % 1f

    /** Neon Arc: how bright segment [i] is lit at [kmh]: 0 dark, 1 the hot head. Null when unlit. */
    fun neonHeat(i: Int, kmh: Float): Float? {
        val segV = (i + 0.5f) / NEON_SEGMENTS * VMAX
        if (segV > kmh) return null
        val d = (kmh - segV) / VMAX * NEON_SEGMENTS
        return max(0f, 1f - d / 10f)
    }

    /** Value at the middle of Neon Arc segment [i] or Retro Dial LED [i] of [count]. */
    fun slotValue(i: Int, count: Int): Float = (i + 0.5f) / count * VMAX

    /** Road Strip's perspective: screen y of the road at [s] (0 near, 1 far) between [bottom] and [top]. */
    fun roadY(s: Float, top: Float, bottom: Float, k: Float = 2.2f): Float = bottom - (bottom - top) * ((1 + k) * s / (1 + k * s))

    /** Road Strip's half width at [y]: [bottomWidth] at the bottom narrowing to [topWidth]. */
    fun roadHalf(y: Float, top: Float, bottom: Float, bottomWidth: Float, topWidth: Float): Float {
        val u = (bottom - y) / (bottom - top)
        return (bottomWidth + (topWidth - bottomWidth) * u) / 2f
    }
}

/**
 * What moves on the speedometer: the shown speed eases towards the GPS speed, Retro Dial's needle
 * follows it on a spring (a slight overshoot), the red fades in over the limit and the road's
 * lane dashes scroll with speed. With reduced motion everything jumps straight to its value.
 * Plain Kotlin, unit-tested; one instance per speedometer, nothing allocated per frame.
 */
internal class SpeedoMotion {
    var speed = 0f
        private set
    var needle = 0f
        private set
    private var needleV = 0f
    var overT = 0f
        private set
    var dash = 0f
        private set
    var clock = 0f
        private set

    /** Jumps to [target] (first frame, reduced motion, previews). */
    fun snap(target: Float, limit: Int?) {
        speed = max(0f, target)
        needle = speed
        needleV = 0f
        overT = if (SpeedoMath.over(speed, limit)) 1f else 0f
    }

    /** One frame of [dt] seconds towards [target] km/h. */
    fun step(dt: Float, target: Float, limit: Int?, reduced: Boolean) {
        val t = max(0f, target)
        if (reduced) {
            snap(t, limit)
            return
        }
        clock += dt
        speed = max(0f, speed + (t - speed) * min(1f, dt * 7f))
        // Spring towards the GPS speed: stiffness 90, damping 13 (the board's needle).
        needleV += ((t - needle) * 90f - needleV * 13f) * dt
        needle += needleV * dt
        val overTarget = if (SpeedoMath.over(speed, limit)) 1f else 0f
        overT += (overTarget - overT) * min(1f, dt * 5f)
        dash += speed * dt * 6f
        if (dash > 1_000_000f) dash = 0f
    }

    /** Nothing left to animate towards [target]: idle frames can slow right down. */
    fun settled(target: Float, limit: Int?): Boolean {
        val overTarget = if (SpeedoMath.over(speed, limit)) 1f else 0f
        return kotlin.math.abs(target - speed) < 0.05f && kotlin.math.abs(needle - target) < 0.05f &&
            kotlin.math.abs(needleV) < 0.05f && kotlin.math.abs(overT - overTarget) < 0.01f
    }
}
