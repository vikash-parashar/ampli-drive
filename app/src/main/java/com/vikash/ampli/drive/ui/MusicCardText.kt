package com.vikash.ampli.drive.ui

import kotlin.math.exp

/**
 * What the Home music card says: the source chip, the title and the line under it. Phone audio
 * over Bluetooth without track info is "Phone se gaana" with the phone's name under it, and
 * "Bluetooth" only in the source chip (it used to fill the title too, so it showed twice). Plain
 * Kotlin, unit-tested.
 */
internal object MusicCardText {
    const val BLUETOOTH = "Bluetooth"
    const val AMPLI_MP3 = "Ampli MP3"

    class Labels(val title: String, val artist: String?, val source: String?, val noTrackInfo: Boolean)

    fun labels(
        bluetooth: Boolean,
        ampli: Boolean,
        title: String?,
        artist: String?,
        appLabel: String?,
        phoneName: String?,
        phoneAudio: String,
        unknownTrack: String,
    ): Labels {
        val t = title?.trim()?.takeIf { it.isNotEmpty() }
        if (bluetooth && t == null) {
            // No metadata from the phone: say what it is, and the phone's name once.
            return Labels(phoneAudio, phoneName?.takeIf { it.isNotBlank() }, BLUETOOTH, noTrackInfo = true)
        }
        val source = when {
            bluetooth -> listOfNotNull(BLUETOOTH, phoneName?.takeIf { it.isNotBlank() }).joinToString(" · ")
            ampli -> AMPLI_MP3
            else -> appLabel
        }
        // An app that sends no title: its own name only in the chip, never again as the title.
        val shownTitle = t ?: unknownTrack
        val shownArtist = artist?.trim()?.takeIf { it.isNotEmpty() && !it.equals(BLUETOOTH, ignoreCase = true) }
        return Labels(shownTitle, shownArtist, source, noTrackInfo = false)
    }
}

/**
 * The record's spin: 20 rpm while playing; on pause it eases to a stop in about a second, and it
 * eases back up on play. With reduced motion it never turns. Plain Kotlin, unit-tested.
 */
internal class DiscSpin {
    /** Degrees, 0..360. */
    var angle = 0f
        private set

    /** Degrees a second. */
    var speed = 0f
        private set

    fun step(dt: Float, playing: Boolean, reduced: Boolean) {
        if (reduced) {
            speed = 0f
            return
        }
        val target = if (playing) FULL_DEG_PER_S else 0f
        speed += (target - speed) * (1f - exp(-dt / EASE_S))
        if (target == 0f && speed < 0.5f) speed = 0f
        angle = (angle + speed * dt) % 360f
    }

    /** Stopped and not asked to play: the frame loop can rest. */
    fun resting(playing: Boolean, reduced: Boolean): Boolean = speed == 0f && (!playing || reduced)

    companion object {
        const val RPM = 20f
        const val FULL_DEG_PER_S = RPM * 6f

        /** Time constant of the ease: about a second to stop. */
        const val EASE_S = 0.32f
    }
}
