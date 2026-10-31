package com.vikash.ampli.drive.fuel

import com.vikash.ampli.drive.voice.HaryanviLines
import com.vikash.ampli.drive.voice.Picked
import com.vikash.ampli.drive.voice.VoiceClips
import kotlin.math.abs

/**
 * What the recorded voice says for "nearest CNG pump" on a unit without a TTS engine. Plain
 * Kotlin, unit-tested.
 *
 * When a recorded distance is close to the real one ("दो किलोमीटर" for 1.9 km) it is stitched to a
 * tail ("पै सीएनजी पंप सै।"). Otherwise a "found, it's on the screen" line plays and the answer card
 * shows the exact distance and name. Nothing is ever said with a wrong distance.
 */
object FuelVoice {
    /** A recorded distance may be off by this share of the real one. */
    const val DISTANCE_TOLERANCE = 0.15

    fun foundEvent(filter: FuelFilter): HaryanviLines.Event = when (filter) {
        FuelFilter.Cng -> HaryanviLines.Event.FuelCngFound
        FuelFilter.Ev -> HaryanviLines.Event.FuelEvFound
        else -> HaryanviLines.Event.FuelPumpFound
    }

    fun tailEvent(filter: FuelFilter): HaryanviLines.Event = when (filter) {
        FuelFilter.Cng -> HaryanviLines.Event.FuelCngTail
        FuelFilter.Ev -> HaryanviLines.Event.FuelEvTail
        else -> HaryanviLines.Event.FuelPumpTail
    }

    /** The recorded distance for [meters] when it is close enough to say, else null. */
    fun spokenDistance(meters: Double): VoiceClips.Distance? {
        if (meters <= 0.0) return null
        val b = VoiceClips.bucket(meters.toInt())
        return b.takeIf { abs(b.meters - meters) <= meters * DISTANCE_TOLERANCE }
    }

    /** Clips for the answer about the nearest station [meters] away, or the "none" line. */
    fun clips(filter: FuelFilter, meters: Double?, pick: (HaryanviLines.Event) -> Picked?): List<String> {
        if (meters == null) return listOfNotNull(pick(HaryanviLines.Event.FuelNone)?.id)
        val d = spokenDistance(meters)
        if (d != null) {
            val tail = pick(tailEvent(filter))
            if (tail != null) return listOf(d.id, tail.id)
        }
        return listOfNotNull(pick(foundEvent(filter))?.id)
    }
}
