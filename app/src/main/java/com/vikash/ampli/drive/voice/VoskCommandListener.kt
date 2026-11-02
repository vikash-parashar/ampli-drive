package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline command recognition for head units without Google speech services (no
 * SpeechRecognizer at all). In Hindi, Haryanvi, Bhojpuri or Maithili it uses the optional Hindi
 * model ([DriveApp.hindiModel]) when downloaded, else the English wake-word model (whose grammar
 * also holds Roman Hinglish phrases). With [ListenMode.Commands]
 * it only hears our command phrases ([VoskCommands]), which makes it far more accurate; without
 * it (when asking "where to?") it transcribes freely in that model's language. Main thread.
 */
class VoskCommandListener(private val app: DriveApp) {
    private var speech: SpeechService? = null

    /** The model that served the last [listen]. */
    var lastEngine: VoiceEngine = VoiceEngine.None
        private set

    private fun hindiWanted(): Boolean = VoiceLocales.parserLanguage(app.prefs.language.value) == "hi"

    fun isReady(): Boolean = app.wakeModel.isInstalled() || (hindiWanted() && app.hindiModel.isInstalled())

    /**
     * Listens once. [mode] picks the grammar: command phrases, short answers, or free speech.
     * Only final results count (partials just feed [onPartial] for the overlay), and a result that
     * is mostly "[unk]" or not confident enough ([SpeechGate.accept]) is taken as noise: listening
     * goes on for the rest of the turn instead of ending it, so music or a ringtone neither runs a
     * command nor uses up the driver's answer.
     */
    suspend fun listen(mode: ListenMode, onPartial: (String) -> Unit): Listener.Result {
        // Hindi model first for Hindi-family app languages; the English model stays the fallback.
        val hindiModel = if (hindiWanted() && app.hindiModel.isInstalled()) app.hindiModel.loadModel() else null
        val hindi = hindiModel != null
        val model = hindiModel ?: app.wakeModel.loadModel() ?: return Listener.Result.Unavailable
        lastEngine = if (hindi) VoiceEngine.OfflineHindi else VoiceEngine.OfflineEnglish
        val recognizer = try {
            when (mode) {
                ListenMode.Commands -> Recognizer(model, SAMPLE_RATE, VoskCommands.grammarJson(hindi))
                ListenMode.Replies -> Recognizer(model, SAMPLE_RATE, VoskCommands.replyGrammarJson(hindi))
                ListenMode.Free -> Recognizer(model, SAMPLE_RATE)
            }.also {
                // Per-word confidences, for SpeechGate.
                it.setWords(true)
            }
        } catch (_: Throwable) {
            return Listener.Result.Unavailable
        }
        val outcome = CompletableDeferred<Listener.Result>()
        val service = try {
            SpeechService(recognizer, SAMPLE_RATE)
        } catch (_: Throwable) {
            recognizer.close()
            return Listener.Result.Unavailable
        }
        speech = service

        /** A usable final result, or null for silence / noise. */
        fun judge(json: String?): Listener.Result.Heard? {
            val parsed = parse(json) ?: return null
            if (!SpeechGate.accept(parsed.first, parsed.second, mode)) return null
            val text = VoskCommands.clean(parsed.first)
            if (text.isEmpty()) return null
            val quality = if (parsed.second.isEmpty()) null else SpeechGate.quality(parsed.second)
            return Listener.Result.Heard(text, offline = true, quality = quality)
        }

        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                // On screen only: a partial never runs anything.
                VoskCommands.clean(field(hypothesis, "partial")).takeIf { it.isNotEmpty() }?.let(onPartial)
            }

            override fun onResult(hypothesis: String?) {
                // Noise: keep listening for the rest of the turn.
                judge(hypothesis)?.let { outcome.complete(it) }
            }

            override fun onFinalResult(hypothesis: String?) {
                outcome.complete(judge(hypothesis) ?: Listener.Result.Silence)
            }

            override fun onError(exception: Exception?) {
                outcome.complete(Listener.Result.Unavailable)
            }

            override fun onTimeout() {
                // Continuous sound (a ringtone, loud music) may never leave the pause Vosk needs to
                // end an utterance: what was heard so far still counts. The recording thread has
                // stopped by now, so the recognizer is free to read.
                val last = try {
                    recognizer.finalResult
                } catch (_: Throwable) {
                    null
                }
                outcome.complete(judge(last) ?: Listener.Result.Silence)
            }
        }
        return try {
            if (!service.startListening(listener, TIMEOUT_MS.toInt())) return Listener.Result.Unavailable
            withTimeoutOrNull(TIMEOUT_MS + 2_000L) { outcome.await() } ?: Listener.Result.Silence
        } catch (_: Exception) {
            Listener.Result.Unavailable
        } finally {
            cancel()
            try {
                recognizer.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** Vosk's final-result JSON: the text and the (word, conf) list, or null when empty. */
    private fun parse(json: String?): Pair<String, List<HeardWord>>? {
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
            text to SpeechGate.words(words)
        } catch (_: Exception) {
            null
        }
    }

    fun cancel() {
        speech?.let {
            try {
                it.cancel()
                it.shutdown()
            } catch (_: Throwable) {
            }
        }
        speech = null
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
        private const val SAMPLE_RATE = 16000f
        /** One listen: long enough for an answer that starts a few seconds after the beep. */
        private const val TIMEOUT_MS = 10_000L

        /**
         * Starts the Hindi model download by itself when it is wanted and missing: a Hindi-family
         * app language and no working system recogniser (see [RecognizerFallback.wantHindiModel]).
         * Same automatic rules as the wake-word model (back-off after a failure), and never after
         * the driver removed it in Settings. Called at app start / wake word on, and per session.
         */
        fun ensureHindiModel(app: DriveApp) {
            val model = app.hindiModel
            if (model.isInstalled()) return
            val parser = VoiceLocales.parserLanguage(app.prefs.language.value)
            if (!RecognizerFallback.wantHindiModel(parser, Listener.systemWorks(app), model.removedByUser())) return
            model.ensure(automatic = true)
        }
    }
}
