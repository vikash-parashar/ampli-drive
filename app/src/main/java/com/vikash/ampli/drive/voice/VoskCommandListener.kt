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
 * also holds Roman Hinglish phrases). With [grammar]
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

    suspend fun listen(grammar: Boolean, onPartial: (String) -> Unit): Listener.Result {
        // Hindi model first for Hindi-family app languages; the English model stays the fallback.
        val hindiModel = if (hindiWanted() && app.hindiModel.isInstalled()) app.hindiModel.loadModel() else null
        val hindi = hindiModel != null
        val model = hindiModel ?: app.wakeModel.loadModel() ?: return Listener.Result.Unavailable
        lastEngine = if (hindi) VoiceEngine.OfflineHindi else VoiceEngine.OfflineEnglish
        val recognizer = try {
            if (grammar) Recognizer(model, SAMPLE_RATE, VoskCommands.grammarJson(hindi)) else Recognizer(model, SAMPLE_RATE)
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
        val listener = object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                VoskCommands.clean(field(hypothesis, "partial")).takeIf { it.isNotEmpty() }?.let(onPartial)
            }

            override fun onResult(hypothesis: String?) {
                val text = VoskCommands.clean(field(hypothesis, "text"))
                if (text.isNotEmpty()) outcome.complete(Listener.Result.Heard(text, offline = true))
            }

            override fun onFinalResult(hypothesis: String?) {
                val text = VoskCommands.clean(field(hypothesis, "text"))
                outcome.complete(if (text.isNotEmpty()) Listener.Result.Heard(text, offline = true) else Listener.Result.Silence)
            }

            override fun onError(exception: Exception?) {
                outcome.complete(Listener.Result.Unavailable)
            }

            override fun onTimeout() {
                outcome.complete(Listener.Result.Silence)
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
