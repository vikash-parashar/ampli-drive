package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Recognizer
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Offline command recognition for head units without Google speech services (no
 * SpeechRecognizer at all). In Hindi, Haryanvi, Bhojpuri or Maithili it uses the optional Hindi
 * model ([DriveApp.hindiModel]) when downloaded, else the English wake-word model (whose grammar
 * also holds Roman Hinglish phrases). With [ListenMode.Commands]
 * it only hears our command phrases ([VoskCommands]), which makes it far more accurate; without
 * it (when asking "where to?") it transcribes freely in that model's language. Main thread.
 */
class VoskCommandListener(private val app: DriveApp) {
    /** The recording of the listen in progress, so [cancel] can stop a blocked read. */
    @Volatile private var capture: AudioRecord? = null
    @Volatile private var stopRequested = false

    /** The model that served the last [listen]. */
    var lastEngine: VoiceEngine = VoiceEngine.None
        private set

    /** What the last [listen] heard, kept even when the gate threw it away (for "Heard: …"). */
    @Volatile var lastRaw: String? = null
        private set

    /** The microphone gave nothing but digital silence in the last [listen]. */
    @Volatile var lastMicDead: Boolean = false
        private set

    /** Why the last [listen] was Unavailable, for the overlay and the voice log. */
    @Volatile var lastError: String? = null
        private set

    private fun hindiWanted(): Boolean = VoiceLocales.parserLanguage(app.prefs.language.value) == "hi"

    fun isReady(): Boolean = app.wakeModel.isInstalled() || (hindiWanted() && app.hindiModel.isInstalled())

    /**
     * Listens once. [mode] picks the grammar: command phrases, short answers, or free speech.
     * Only final results count (partials just feed [onPartial] for the overlay), and a result that
     * is mostly "[unk]" or not confident enough ([SpeechGate.accept]) is taken as noise: listening
     * goes on for the rest of the turn instead of ending it, so music or a ringtone neither runs a
     * command nor uses up the driver's answer. In [ListenMode.FollowUp] a word outside the grammar
     * (a place name) ends the turn as [Listener.Result.Unclear] instead ([FollowUp.unclearEndsListen]).
     *
     * [beforeStart] runs once the recognizer is ready and just before recording starts (the listen
     * chime and "Sun raha hoon…"), so nothing the driver says after the chime is lost while the
     * grammar is still being built.
     *
     * The microphone is read here directly (not through Vosk's SpeechService), so every step can be
     * checked and written to [VoiceDebugLog]: the audio source that opened, whether it really
     * records, and how loud it was. A source that gives only digital silence is swapped for the
     * next one ([SOURCES]) in the same turn.
     */
    suspend fun listen(mode: ListenMode, onPartial: (String) -> Unit, beforeStart: suspend () -> Unit = {}): Listener.Result {
        stopRequested = false
        lastRaw = null
        lastMicDead = false
        lastError = null
        // Model load (first use only) and grammar build, for the voice log.
        val built = SystemClock.elapsedRealtime()
        // The Hindi model for Hindi-family app languages, and for answers to Ampli on units with
        // no working system recogniser (drivers there answer in Hindi whatever the app language).
        val useHindi = RecognizerFallback.useHindiModel(
            parserLanguage = VoiceLocales.parserLanguage(app.prefs.language.value),
            followUp = mode == ListenMode.FollowUp,
            hindiInstalled = app.hindiModel.isInstalled(),
            systemWorks = Listener.systemWorks(app),
        )
        val hindiModel = if (useHindi) app.hindiModel.loadModel() else null
        if (useHindi && hindiModel == null) VoiceDebugLog.add("hindi model failed to load: ${app.hindiModel.loadError}")
        val hindi = hindiModel != null
        val model = hindiModel ?: app.wakeModel.loadModel()
        if (model == null) {
            lastError = "offline model not loaded: ${app.wakeModel.loadError ?: "missing"}"
            VoiceDebugLog.add(lastError!!)
            return Listener.Result.Unavailable
        }
        lastEngine = if (hindi) VoiceEngine.OfflineHindi else VoiceEngine.OfflineEnglish
        // Building a grammar of a few hundred phrases takes a moment on a slow head unit: off the
        // main thread, and before the chime, so the microphone is ready when the driver speaks.
        var back: Map<String, String> = emptyMap()
        var phraseCount = 0
        val recognizer = withContext(Dispatchers.Default) {
            try {
                val phrases = when (mode) {
                    ListenMode.Commands -> VoskCommands.phrases(hindi)
                    ListenMode.Replies -> VoskCommands.replyPhrases(hindi)
                    ListenMode.FollowUp -> VoskCommands.followUpPhrases(hindi)
                    ListenMode.Free -> null
                }
                phraseCount = phrases?.size ?: 0
                // The English model drops Roman Hindi words it lacks ("gaana chalao" would vanish):
                // known spellings of them go into the grammar instead.
                val known = if (phrases != null && !hindi) VoskVocabulary.knows(model) else null
                val grammar = when {
                    phrases == null -> null
                    known != null -> HinglishSpelling.adapt(phrases, known).let {
                        back = it.back
                        VoskCommands.grammarOf(it.phrases)
                    }
                    else -> VoskCommands.grammarOf(phrases)
                }
                (if (grammar == null) Recognizer(model, SAMPLE_RATE.toFloat()) else Recognizer(model, SAMPLE_RATE.toFloat(), grammar)).also {
                    // Per-word confidences, for SpeechGate.
                    it.setWords(true)
                }
            } catch (t: Throwable) {
                lastError = "recognizer: ${t.javaClass.simpleName}: ${t.message}"
                null
            }
        }
        if (recognizer == null) {
            VoiceDebugLog.add(lastError ?: "recognizer failed")
            return Listener.Result.Unavailable
        }
        VoiceDebugLog.add(
            "listen $mode on ${lastEngine.label}: $phraseCount phrases, ready in ${SystemClock.elapsedRealtime() - built} ms" +
                (if (back.isNotEmpty()) ", ${back.size} respelled" else ""),
        )
        try {
            beforeStart()
            return withContext(Dispatchers.IO) { record(recognizer, mode, back, onPartial) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "listen: ${e.javaClass.simpleName}: ${e.message}"
            VoiceDebugLog.add(lastError!!)
            return Listener.Result.Unavailable
        } finally {
            stopCapture()
            try {
                recognizer.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** One turn of recording into [recognizer], on a background thread. */
    private suspend fun record(
        recognizer: Recognizer,
        mode: ListenMode,
        back: Map<String, String>,
        onPartial: (String) -> Unit,
    ): Listener.Result {
        val main = Handler(Looper.getMainLooper())
        // Something was said in a follow-up that matched no phrase (a place name, a word the
        // grammar lacks): reported as Unclear when the turn ends without a usable answer.
        var unclear = false

        /** A usable final result, or null for silence / noise. Logs what came back and why. */
        fun judge(json: String?): Listener.Result.Heard? {
            val parsed = parse(json) ?: return null
            if (FollowUp.unclearEndsListen(mode, parsed.words, parsed.spanSeconds)) unclear = true
            val text = HinglishSpelling.restore(VoskCommands.clean(parsed.text), back)
            lastRaw = text.ifEmpty { SpeechGate.UNK }
            val conf = parsed.words.joinToString(" ") { "${it.word}:${"%.2f".format(Locale.ROOT, it.conf)}" }
            val accept = SpeechGate.accept(parsed.text, parsed.words, mode) && text.isNotEmpty()
            VoiceDebugLog.add("heard \"${parsed.text}\" [$conf] → ${if (accept) "kept" else if (unclear) "unclear" else "dropped (noise)"}")
            if (!accept) return null
            val quality = if (parsed.words.isEmpty()) null else SpeechGate.quality(parsed.words)
            return Listener.Result.Heard(text, offline = true, quality = quality)
        }

        fun nothing(): Listener.Result = if (unclear) Listener.Result.Unclear else Listener.Result.Silence

        val stats = MicStats()
        var sourceIndex = 0
        val opened = openMic(sourceIndex) ?: run {
            lastError = "microphone would not open (busy or not allowed)"
            return Listener.Result.Unavailable
        }
        sourceIndex = opened.second
        var record = opened.first
        val started = SystemClock.elapsedRealtime()
        val buffer = ShortArray(SAMPLE_RATE / 10)
        var remaining = SAMPLE_RATE.toLong() * TIMEOUT_MS / 1000
        var lastPartial = 0L
        var swapped = false
        var emptyReads = 0
        var result: Listener.Result? = null
        try {
            while (result == null && !stopRequested && coroutineContext.isActive && remaining > 0) {
                if (SystemClock.elapsedRealtime() - started > TIMEOUT_MS + 3_000L) {
                    VoiceDebugLog.add("mic read too slow: stopped by the clock")
                    break
                }
                val n = record.read(buffer, 0, buffer.size)
                if (n < 0) {
                    VoiceDebugLog.add("mic read error $n on ${SOURCE_NAMES[sourceIndex]}")
                    lastError = "microphone read error $n"
                    if (stats.samples == 0L) return Listener.Result.Unavailable
                    break
                }
                if (n == 0) {
                    if (++emptyReads > 50) {
                        VoiceDebugLog.add("mic returns no samples on ${SOURCE_NAMES[sourceIndex]}")
                        break
                    }
                    continue
                }
                stats.add(buffer, n)
                remaining -= n
                // Pure digital silence after the first second: this source is not the microphone
                // on this unit. Try the next one once, with the same recognizer.
                if (!swapped && stats.deadSilence(SAMPLE_RATE.toLong()) && sourceIndex + 1 < SOURCES.size) {
                    swapped = true
                    VoiceDebugLog.add("mic silent on ${SOURCE_NAMES[sourceIndex]} (${stats.describe(SAMPLE_RATE)}), trying ${SOURCE_NAMES[sourceIndex + 1]}")
                    stopCapture()
                    val next = openMic(sourceIndex + 1)
                    if (next == null) break
                    record = next.first
                    sourceIndex = next.second
                    continue
                }
                if (recognizer.acceptWaveForm(buffer, n)) {
                    val heard = judge(recognizer.result)
                    // Noise: keep listening for the rest of the turn. A follow-up word outside the
                    // grammar ends it at once, so "Rohtak" is asked about instead of waited out.
                    result = heard ?: if (unclear) Listener.Result.Unclear else null
                } else {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastPartial >= PARTIAL_EVERY_MS) {
                        lastPartial = now
                        val partial = HinglishSpelling.restore(VoskCommands.clean(field(recognizer.partialResult, "partial")), back)
                        // On screen only: a partial never runs anything.
                        if (partial.isNotEmpty()) main.post { onPartial(partial) }
                    }
                }
            }
            if (result == null) {
                // Continuous sound (a ringtone, loud music) may never leave the pause Vosk needs to
                // end an utterance: what was heard so far still counts.
                val last = try {
                    recognizer.finalResult
                } catch (_: Throwable) {
                    null
                }
                result = judge(last) ?: nothing()
            }
        } finally {
            lastMicDead = stats.deadSilence(SAMPLE_RATE.toLong() / 2)
            VoiceDebugLog.add(
                "listen end on ${SOURCE_NAMES[sourceIndex]}: ${stats.describe(SAMPLE_RATE)}" +
                    (if (lastMicDead) " — MIC GAVE ONLY SILENCE" else "") + (if (stopRequested) " (cancelled)" else ""),
            )
        }
        return result ?: nothing()
    }

    /**
     * Opens and starts the microphone at 16 kHz mono, trying [SOURCES] from [from] on. Returns the
     * recording and the index of the source that worked, or null. Each failure is logged.
     */
    private fun openMic(from: Int): Pair<AudioRecord, Int>? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) {
            VoiceDebugLog.add("mic: 16 kHz mono not supported (min buffer $min)")
            return null
        }
        val bytes = maxOf(min, SAMPLE_RATE / 5 * 2) * 2
        for (i in from until SOURCES.size) {
            val name = SOURCE_NAMES[i]
            val r = try {
                AudioRecord(SOURCES[i], SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bytes)
            } catch (e: Exception) {
                VoiceDebugLog.add("mic $name: ${e.javaClass.simpleName} ${e.message}")
                continue
            }
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                VoiceDebugLog.add("mic $name: not initialised (in use?)")
                r.release()
                continue
            }
            try {
                r.startRecording()
            } catch (e: Exception) {
                VoiceDebugLog.add("mic $name: start failed ${e.javaClass.simpleName} ${e.message}")
                r.release()
                continue
            }
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                VoiceDebugLog.add("mic $name: did not start recording (held by another recorder?)")
                try {
                    r.stop()
                } catch (_: Exception) {
                }
                r.release()
                continue
            }
            capture = r
            VoiceDebugLog.add("mic open: $name, 16 kHz, buffer $bytes")
            return r to i
        }
        return null
    }

    private fun stopCapture() {
        val r = capture ?: return
        capture = null
        try {
            r.stop()
        } catch (_: Exception) {
        }
        try {
            r.release()
        } catch (_: Exception) {
        }
    }

    private class Parsed(val text: String, val words: List<HeardWord>, val spanSeconds: Double)

    /** Vosk's final-result JSON: the text, the (word, conf) list and how long it lasted, or null when empty. */
    private fun parse(json: String?): Parsed? {
        if (json == null) return null
        return try {
            val o = JSONObject(json)
            val text = o.optString("text")
            if (text.isBlank()) return null
            val arr = o.optJSONArray("result")
            val words = if (arr == null) {
                emptyList()
            } else {
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { w -> w.optString("word") to w.optDouble("conf", 1.0) }
                }
            }
            val first = arr?.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0
            val last = arr?.optJSONObject(arr.length() - 1)?.optDouble("end", 0.0) ?: 0.0
            Parsed(text, SpeechGate.words(words), (last - first).coerceAtLeast(0.0))
        } catch (_: Exception) {
            null
        }
    }

    /** Stops a listen in progress (the session ended or was cancelled). */
    fun cancel() {
        stopRequested = true
        // Unblocks a read that is waiting for audio; the recording thread releases it.
        try {
            capture?.stop()
        } catch (_: Exception) {
        }
    }

    private fun field(json: String?, key: String): String {
        if (json == null) return ""
        return try {
            JSONObject(json).optString(key)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        /** One listen: long enough for an answer that starts a few seconds after the beep. */
        private const val TIMEOUT_MS = 10_000L
        private const val PARTIAL_EVERY_MS = 250L

        /**
         * Audio sources in the order tried: the one Vosk's own SpeechService (and so the wake
         * word) uses first, then the plain microphone, which some head units route instead.
         */
        private val SOURCES = intArrayOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
        private val SOURCE_NAMES = arrayOf("VOICE_RECOGNITION", "MIC")

        /**
         * Starts the Hindi model download by itself when it is wanted and missing: no working
         * system recogniser, and a Hindi-family app language or an unmetered network (Wi-Fi)
         * whatever the language (see [RecognizerFallback.wantHindiModel]). Same automatic rules as
         * the wake-word model (back-off after a failure), never while the English model is still
         * downloading (one at a time on small units), and never after the driver removed it in
         * Settings. Called at app start, on a network change and per session.
         */
        fun ensureHindiModel(app: DriveApp) {
            val model = app.hindiModel
            if (model.isInstalled() || model.busy || app.wakeModel.busy) return
            if (!hindiWantedOffline(app)) return
            VoiceDebugLog.add("hindi model: starting automatic download")
            model.ensure(automatic = true)
        }

        /** [RecognizerFallback.wantHindiModel] for this unit right now. */
        fun hindiWantedOffline(app: DriveApp): Boolean = RecognizerFallback.wantHindiModel(
            VoiceLocales.parserLanguage(app.prefs.language.value),
            Listener.systemWorks(app),
            app.hindiModel.removedByUser(),
            unmetered = unmetered(app),
        )

        /** The active network is unmetered (Wi-Fi, not a phone's mobile data). */
        fun unmetered(app: DriveApp): Boolean = try {
            val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
            cm != null && cm.activeNetwork != null && !cm.isActiveNetworkMetered
        } catch (_: Exception) {
            false
        }
    }
}
