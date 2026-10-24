package com.vikash.ampli.drive.media

import android.media.AudioManager
import com.vikash.ampli.drive.DriveApp
import kotlin.math.floor
import kotlin.math.roundToInt

/** Settings › Volume by speed. */
enum class SpeedVolumeLevel(val key: String) {
    Off("off"), Low("low"), Medium("medium"), High("high");

    companion object {
        fun from(key: String?): SpeedVolumeLevel = entries.firstOrNull { it.key == key } ?: Off
    }
}

/** How many volume steps to add at a speed. Pure. */
object VolumeCurve {
    /** Below this nothing changes (town traffic). */
    const val START_KMH = 30f

    /** Speed drop needed before a step is taken back, so the volume doesn't flap at a boundary. */
    const val HYSTERESIS_KMH = 4f

    /** The curves are written for a 15-step volume scale and scaled to the unit's own. */
    private const val REFERENCE_STEPS = 15f

    /** (km/h per step, most steps) on a 15-step scale. */
    private fun shape(level: SpeedVolumeLevel): Pair<Float, Int> = when (level) {
        SpeedVolumeLevel.Off -> 1f to 0
        SpeedVolumeLevel.Low -> 30f to 2
        SpeedVolumeLevel.Medium -> 20f to 4
        SpeedVolumeLevel.High -> 12f to 6
    }

    /** Steps on a 15-step scale at [kmh], without hysteresis. */
    fun referenceSteps(level: SpeedVolumeLevel, kmh: Float): Int {
        val (perStep, most) = shape(level)
        if (most == 0 || kmh < START_KMH + perStep) return 0
        return floor((kmh - START_KMH) / perStep).toInt().coerceIn(0, most)
    }

    /** [referenceSteps] scaled to a stream with [maxVolume] steps (at least one step when boosting). */
    fun steps(level: SpeedVolumeLevel, kmh: Float, maxVolume: Int): Int {
        val ref = referenceSteps(level, kmh)
        if (ref == 0 || maxVolume <= 0) return 0
        return (ref * maxVolume / REFERENCE_STEPS).roundToInt().coerceAtLeast(1)
    }

    /** [steps] with hysteresis: a step comes back off only [HYSTERESIS_KMH] below where it went on. */
    fun stepsWithHysteresis(level: SpeedVolumeLevel, kmh: Float, maxVolume: Int, previous: Int): Int {
        val now = steps(level, kmh, maxVolume)
        if (now >= previous) return now
        return maxOf(now, steps(level, kmh + HYSTERESIS_KMH, maxVolume).coerceAtMost(previous))
    }
}

/**
 * The volume decisions, pure for tests. The driver's own level is the base; at speed a few
 * steps go on top, never above the maximum. A volume change that this logic did not make is
 * the driver's (or another app's): it becomes the new base, and the boost already applied at
 * that speed counts as included, so nothing jumps. When the speed falls the volume returns to
 * the base.
 */
class SpeedVolumeLogic {
    private var base: Int? = null
    private var lastSet: Int? = null
    private var boost = 0
    private var included = 0

    /** Returns the volume to set now, or null to leave it. */
    fun onSample(level: SpeedVolumeLevel, kmh: Float, current: Int, max: Int): Int? {
        if (level == SpeedVolumeLevel.Off) return reset(current)
        if (base == null || lastSet == null) {
            base = current
            lastSet = current
            included = 0
        } else if (current != lastSet) {
            // Manual change: the new base, with today's boost already in it.
            base = current
            lastSet = current
            included = boost
        }
        boost = VolumeCurve.stepsWithHysteresis(level, kmh, max, boost)
        if (included > boost) included = boost
        val target = ((base ?: current) + boost - included).coerceIn(0, max)
        if (target == current) return null
        lastSet = target
        return target
    }

    /** Off (or the feature switched off): back to the base when the volume is still ours. */
    fun reset(current: Int): Int? {
        val b = base
        val l = lastSet
        base = null
        lastSet = null
        boost = 0
        included = 0
        return if (b != null && l != null && current == l && current != b) b else null
    }
}

/** Applies [SpeedVolumeLogic] to STREAM_MUSIC from CarMonitor's GPS speed. Main thread. */
class SpeedVolume(private val app: DriveApp) {
    private val logic = SpeedVolumeLogic()
    private val audio: AudioManager? by lazy { app.getSystemService(AudioManager::class.java) }
    private var wasOn = false

    fun onSpeed(kmh: Float) {
        val level = app.prefs.speedVolume.value
        if (level == SpeedVolumeLevel.Off && !wasOn) return
        wasOn = level != SpeedVolumeLevel.Off
        val am = audio ?: return
        try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            logic.onSample(level, kmh, cur, max)?.let { am.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
        } catch (_: Exception) {
            // Some head units refuse volume changes from apps (fixed volume): nothing to do.
        }
    }
}
