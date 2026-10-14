package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline command recognition with the same small Vosk model as the wake word, for head units
 * without Google speech services (no SpeechRecognizer at all). With [grammar] it only hears our
 * command phrases ([VoskCommands]), which makes it far more accurate; without it (when asking
 * "where to?") it transcribes freely, English only. Main thread.
 */
class VoskCommandListener(private val app: DriveApp) {
    private var speech: SpeechService? = null

    fun isReady(): Boolean = app.wakeModel.isInstalled()

    suspend fun listen(grammar: Boolean, onPartial: (String) -> Unit): Listener.Result {
        val model = app.wakeModel.loadModel() ?: return Listener.Result.Unavailable
        val recognizer = try {
            if (grammar) Recognizer(model, SAMPLE_RATE, VoskCommands.grammarJson()) else Recognizer(model, SAMPLE_RATE)
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

    private fun field(json: String?, key: String): String = try {
        JSONObject(json ?: return "").optString(key)
    } catch (_: Exception) {
        ""
    }

    private companion object {
        const val SAMPLE_RATE = 16000f
        const val TIMEOUT_MS = 10_000L
    }
}

/** The phrases the offline recogniser listens for. Each must parse in [VoiceIntents] (unit-tested). */
object VoskCommands {
    val COMMANDS = listOf(
        "navigate", "navigate to", "take me home", "take me to",
        "next camera", "speed camera",
        "play music", "play", "pause", "stop music", "next song", "previous song", "resume",
        "call", "android auto", "connect my phone",
        "where is my car", "tyre pressure", "next service", "engine status",
        "sos", "help me", "emergency", "report a problem",
        "cancel", "stop", "hello",
    )

    /** Only while a list of places is showing. */
    val CHOICES = listOf("first", "second", "third", "first one", "second one", "third one", "one", "two", "three")

    fun grammarJson(): String = JSONArray(COMMANDS + CHOICES + "[unk]").toString()

    /** Drops Vosk's "[unk]" tokens and extra spaces. */
    fun clean(text: String): String = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
}
