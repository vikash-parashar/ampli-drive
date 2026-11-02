package com.vikash.ampli.drive.voice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * How loud the microphone was during one listen (unit-tested). Some head units hand an app a
 * recording that is pure digital silence (every sample 0) when the input is routed elsewhere or
 * the audio source is not supported: [deadSilence] spots that, so the listener can try another
 * audio source and the voice log ([VoiceDebugLog]) can say "the mic gave nothing" instead of
 * leaving the driver with a silent Ampli.
 */
class MicStats {
    var samples = 0L
        private set
    var peak = 0
        private set
    private var sumSquares = 0.0
    private var nonZero = 0L

    fun add(buffer: ShortArray, n: Int) {
        for (i in 0 until n.coerceAtMost(buffer.size)) {
            val v = buffer[i].toInt()
            val a = abs(v)
            if (a > peak) peak = a
            if (v != 0) nonZero++
            sumSquares += v.toDouble() * v
        }
        samples += n.coerceAtLeast(0)
    }

    /** Root-mean-square level, 0..32767. */
    val rms: Int get() = if (samples == 0L) 0 else sqrt(sumSquares / samples).toInt()

    /** At least [minSamples] read and every one of them was exactly zero (or nearly so). */
    fun deadSilence(minSamples: Long): Boolean = samples >= minSamples && peak <= DEAD_PEAK

    /** "peak 1234 rms 210 over 3.2 s" for the voice log. */
    fun describe(sampleRate: Int): String {
        val secs = if (sampleRate > 0) samples.toDouble() / sampleRate else 0.0
        val pct = if (samples == 0L) 0 else (nonZero * 100 / samples).toInt()
        return "peak $peak rms $rms over ${"%.1f".format(java.util.Locale.ROOT, secs)} s ($pct% non-zero)"
    }

    companion object {
        /** A peak this low is no microphone at all (real rooms and cars never get this quiet). */
        const val DEAD_PEAK = 2
    }
}
