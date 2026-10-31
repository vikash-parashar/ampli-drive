package com.vikash.ampli.drive.media

import com.vikash.ampli.drive.voice.VoiceIntent.VolumeAction
import kotlin.math.roundToInt

/** The music volume after a voice command, pure for tests. */
object VolumeMath {

    /** One "volume up" is about a tenth of the scale (2 steps on a 15-step unit, 3 on a 30-step one). */
    fun step(max: Int): Int = (max / 10.0).roundToInt().coerceAtLeast(1)

    /**
     * The spoken level on the unit's own scale: 0 to 10 is tenths ("volume 5" is half), 11 to 100
     * is percent. Head units use 15, 30 or 40 steps, so a plain step number would mean different
     * things on each.
     */
    fun levelFor(spoken: Int, max: Int): Int {
        val n = spoken.coerceIn(0, 100)
        val fraction = if (n <= 10) n / 10.0 else n / 100.0
        return (fraction * max).roundToInt().coerceIn(0, max)
    }

    /** The new stream index for [action]; null for Mute, Unmute and Show (no index change). */
    fun target(action: VolumeAction, current: Int, max: Int, level: Int? = null): Int? = when (action) {
        VolumeAction.Up -> (current + step(max)).coerceAtMost(max)
        VolumeAction.Down -> (current - step(max)).coerceAtLeast(0)
        VolumeAction.Max -> max
        VolumeAction.Set -> levelFor(level ?: 5, max)
        VolumeAction.Mute, VolumeAction.Unmute, VolumeAction.Show -> null
    }

    /** The level out of ten, as the answer card and the voice say it. */
    fun tenths(current: Int, max: Int): Int = if (max <= 0) 0 else (current * 10.0 / max).roundToInt().coerceIn(0, 10)
}
