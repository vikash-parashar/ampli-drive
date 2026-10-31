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
 * model ([DriveApp.hindiModel]) when downloaded, else the English wake-word model. With [grammar]
 * it only hears our command phrases ([VoskCommands]), which makes it far more accurate; without
 * it (when asking "where to?") it transcribes freely in that model's language. Main thread.
 */
class VoskCommandListener(private val app: DriveApp) {
    private var speech: SpeechService? = null

    private fun hindiWanted(): Boolean = VoiceLocales.parserLanguage(app.prefs.language.value) == "hi"

    fun isReady(): Boolean = app.wakeModel.isInstalled() || (hindiWanted() && app.hindiModel.isInstalled())

    suspend fun listen(grammar: Boolean, onPartial: (String) -> Unit): Listener.Result {
        // Hindi model first for Hindi-family app languages; the English model stays the fallback.
        val hindiModel = if (hindiWanted() && app.hindiModel.isInstalled()) app.hindiModel.loadModel() else null
        val hindi = hindiModel != null
        val model = hindiModel ?: app.wakeModel.loadModel() ?: return Listener.Result.Unavailable
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

    private companion object {
        const val SAMPLE_RATE = 16000f
        const val TIMEOUT_MS = 10_000L
    }
}

/** The phrases the offline recogniser listens for. Each must parse in [VoiceIntents] (unit-tested). */
object VoskCommands {
    val COMMANDS = listOf(
        "navigate", "navigate to", "take me home", "take me to",
        "go home", "go to office", "take me to office", "ghar chalo", "office chalo",
        "next camera", "speed camera",
        "play music", "play", "pause", "stop music", "next song", "previous song", "resume",
        "call", "android auto", "connect my phone",
        "where is my car", "tyre pressure", "next service", "engine status",
        "sos", "help me", "emergency", "report a problem",
        "cancel", "stop", "hello",
        "how are you", "who are you", "tell me a joke",
        // Round 7: volume, stop, time, weather, maps, brightness, Bluetooth, help.
        "volume", "volume up", "volume down", "volume full", "volume five", "volume ten", "mute", "unmute",
        "louder", "quieter", "play next song", "next", "previous", "preview", "stop navigation",
        "what time is it", "what is the time", "what is the date", "how is the weather", "weather",
        "will it rain", "what is my speed", "open maps", "brightness up", "brightness down",
        "bluetooth settings", "what can i say",
        // Fuel finder and road reports.
        "nearest cng pump", "cng pump", "petrol pump", "diesel pump", "charging station", "fuel station",
        "police ahead", "accident ahead", "traffic jam ahead", "mobile camera ahead",
    )

    /** Only right after a volume change ("aur" / "more"); see VoiceIntents.volumeFollowUp. */
    val FOLLOW_UPS = listOf("more", "a bit more", "again")
    val HINDI_FOLLOW_UPS = listOf("और", "थोड़ा और", "और तेज", "और कम")

    /** Only while a list of places is showing. */
    val CHOICES = listOf("first", "second", "third", "first one", "second one", "third one", "one", "two", "three")

    /**
     * Hindi / Haryanvi phrases for the Hindi model (Devanagari, as that model writes). Words the
     * model doesn't know are skipped by Vosk with a log warning, so plain Hindi words are used.
     */
    val HINDI_COMMANDS = listOf(
        "रास्ता दिखाओ", "ले चलो", "घर ले चलो", "चलो", "ले चल", "नेविगेट करो",
        "घर चलो", "ऑफिस चलो", "ऑफिस ले चलो", "दफ्तर चलो",
        "कैमरा", "अगला कैमरा",
        "गाना बजाओ", "गाना चलाओ", "बजाओ", "गाना बंद करो", "गाना रोको", "अगला गाना", "पिछला गाना", "गाना चालू करो",
        "फोन करो", "कॉल करो", "फोन जोड़ो",
        "गाड़ी कहाँ है", "अगली सर्विस कब", "टायर प्रेशर", "इंजन कैसा है",
        "मदद", "बचाओ", "इमरजेंसी", "शिकायत",
        "रद्द करो", "रहने दो", "बस", "नमस्ते",
        "के हाल सै", "क्या हाल है", "तू कौन सै", "तुम कौन हो", "चुटकला सुणा", "चुटकुला सुनाओ",
        // Round 7.
        "आवाज बढ़ाओ", "आवाज कम करो", "आवाज पूरी करो", "आवाज बंद", "वॉल्यूम", "म्यूट", "अनम्यूट",
        "रुको", "बंद करो", "नेविगेशन बंद करो", "रास्ता बंद करो", "कितने बजे हैं", "टाइम क्या हुआ",
        "आज क्या तारीख है", "मौसम कैसा है", "बारिश होगी", "स्पीड कितनी है", "मैप खोलो", "नक्शा खोलो",
        "ब्लूटूथ खोलो", "ब्राइटनेस बढ़ाओ", "ब्राइटनेस कम करो", "क्या बोल सकता हूँ",
        // Fuel finder and road reports.
        "सीएनजी पंप कहाँ है", "सीएनजी पंप", "पेट्रोल पंप", "डीजल पंप", "चार्जिंग स्टेशन",
        "पुलिस आगे है", "एक्सीडेंट आगे है", "आगे जाम है", "मोबाइल कैमरा आगे है",
    )

    val HINDI_CHOICES = listOf("पहला", "दूसरा", "तीसरा", "पहली", "दूसरी", "तीसरी", "एक", "दो", "तीन")

    fun grammarJson(hindi: Boolean = false): String =
        ((if (hindi) HINDI_COMMANDS + HINDI_CHOICES + HINDI_FOLLOW_UPS else COMMANDS + CHOICES + FOLLOW_UPS) + "[unk]").joinToString(",", "[", "]") { phrase ->
        "\"" + phrase.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }

    /** Drops Vosk's "[unk]" tokens and extra spaces. */
    fun clean(text: String): String = text.replace("[unk]", " ").replace(Regex("\\s+"), " ").trim()
}
