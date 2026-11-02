package com.vikash.ampli.drive.voice.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Ampli's own offline voice engine, shipped inside the app so units without any text-to-speech
 * engine (the TS7) still hear names, numbers and titles that have no recording. It runs CMU
 * Flite (native, app/src/main/cpp) with a Hindi and an Indian English voice ([TtsVoiceFiles])
 * and plays the samples itself through an AudioTrack. [com.vikash.ampli.drive.voice.Speaker]
 * decides when to use it ([BundledTts.pick]); callers never talk to it directly.
 *
 * Text is made one sentence at a time on one background thread, and each sentence plays while
 * the next is made, so the first words come quickly even on a 1.3 GHz Cortex-A7.
 */
class AmpliVoice(context: Context) {
    val files = TtsVoiceFiles(context)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val synthThread = Dispatchers.IO.limitedParallelism(1)

    /** The one voice kept in memory (~30-60 MB for Hindi), swapped when the language changes. */
    private var loaded: TtsVoiceSpec? = null
    private var handle = 0L

    @Volatile private var track: AudioTrack? = null

    @Volatile private var generation = 0

    @Volatile private var speaking = false

    /** Languages the engine can speak right now (voice in place and native library present). */
    fun languages(): Set<String> {
        val have = files.installedLanguages()
        return if (have.isEmpty() || !FliteNative.loaded) emptySet() else have
    }

    /** At least one voice is ready. */
    fun ready(): Boolean = languages().isNotEmpty()

    /** True while it is talking. */
    fun isSpeaking(): Boolean = speaking

    /**
     * Says [text] for a line in [parserLanguage] ("hi", "en"…) and returns when it has been heard.
     * False when there is no voice for it or synthesis failed.
     */
    suspend fun speak(text: String, parserLanguage: String, attributes: AudioAttributes, stretch: Float = 1f): Boolean {
        if (text.isBlank()) return false
        val lang = BundledTts.voiceFor(text, parserLanguage, languages()) ?: return false
        val spec = TtsVoiceSpec.entries.first { it.language == lang }
        val my = ++generation
        return withContext(synthThread) {
            val voice = voiceHandle(spec)
            if (voice == 0L || generation != my) return@withContext false
            var out: AudioTrack? = null
            var written = 0L
            try {
                for (piece in BundledTts.chunks(text)) {
                    if (generation != my) break
                    val rate = IntArray(1)
                    val pcm = FliteNative.synth(voice, piece.toByteArray(Charsets.UTF_8), stretch, rate) ?: continue
                    if (pcm.isEmpty() || generation != my) continue
                    val t = out ?: newTrack(rate[0].takeIf { it > 0 } ?: 16_000, attributes)?.also {
                        out = it
                        track = it
                        it.play()
                        speaking = true
                    } ?: return@withContext false
                    // Small writes, so stop() cuts in within ~0.1 s.
                    var off = 0
                    while (off < pcm.size && generation == my) {
                        val n = minOf(WRITE_SAMPLES, pcm.size - off)
                        val w = t.write(pcm, off, n)
                        if (w <= 0) break
                        off += w
                    }
                    written += off
                }
                // Let the last of it play out.
                val t = out
                if (t != null) {
                    // A streaming track may hold back sound until its buffer has filled once:
                    // a buffer of silence after the speech pushes every real sample out.
                    val pad = ShortArray(WRITE_SAMPLES)
                    var padded = 0
                    while (padded < t.bufferSizeInFrames && generation == my) {
                        val w = t.write(pad, 0, pad.size)
                        if (w <= 0) break
                        padded += w
                    }
                    val rate = t.sampleRate.coerceAtLeast(1)
                    var waited = 0L
                    val limit = written * 1000 / rate + 1_000
                    while (generation == my && (t.playbackHeadPosition.toLong() and 0xffffffffL) < written && waited < limit) {
                        delay(30)
                        waited += 30
                    }
                }
                written > 0 && generation == my
            } finally {
                out?.let {
                    try {
                        it.stop()
                    } catch (_: IllegalStateException) {
                    }
                    it.release()
                }
                if (track === out) {
                    track = null
                    speaking = false
                }
            }
        }
    }

    /** Stops talking now (the current [speak] returns false). */
    fun stop() {
        generation++
        track?.let {
            try {
                it.pause()
                it.flush()
            } catch (_: IllegalStateException) {
            }
        }
        speaking = false
    }

    /** Frees the voice in memory (it loads again on the next line). */
    suspend fun release() {
        stop()
        withContext(synthThread) { unload() }
    }

    private fun voiceHandle(spec: TtsVoiceSpec): Long {
        if (loaded == spec && handle != 0L) return handle
        unload()
        if (!FliteNative.loaded) return 0L
        val path = files.file(spec)
        if (!path.isFile) return 0L
        handle = FliteNative.loadVoice(path.absolutePath)
        loaded = if (handle != 0L) spec else null
        return handle
    }

    private fun unload() {
        if (handle != 0L) FliteNative.freeVoice(handle)
        handle = 0L
        loaded = null
    }

    private fun newTrack(rate: Int, attributes: AudioAttributes): AudioTrack? = try {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            // About half a second of sound in hand, so the next sentence is made while this plays.
            .setBufferSizeInBytes(maxOf(min, rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val WRITE_SAMPLES = 1_600
    }
}
