package com.vikash.ampli.drive.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vikash.ampli.drive.voice.tts.AmpliVoice
import com.vikash.ampli.drive.voice.tts.BundledTts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Speaks text in the app language (see [VoiceLocales]), falling back to en-IN: through the
 * unit's own Android TextToSpeech when it works and has the language, else through Ampli's
 * bundled voice engine ([AmpliVoice]: offline Hindi and Indian English, shipped with the app), so
 * units without any TTS engine (the TS7) still speak. [BundledTts.pick] makes the choice.
 * While Ampli speaks it holds AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, so music dips instead of stopping.
 * All calls from the main thread.
 */
class Speaker(context: Context, val bundled: AmpliVoice = AmpliVoice(context)) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var localeFor: String? = null
    private val pending = HashMap<String, CompletableDeferred<Unit>>()

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val duckFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .build()

    private suspend fun engine(appLanguage: String): TextToSpeech? {
        val existing = tts
        // No engine installed at all (the TS7): don't start one and wait for it on every line.
        if (existing == null && !enginesInstalled()) return null
        if (existing == null) {
            val done = CompletableDeferred<Boolean>()
            ready = done
            tts = TextToSpeech(appContext) { status -> done.complete(status == TextToSpeech.SUCCESS) }.also {
                it.setAudioAttributes(speechAttributes)
                it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = finish(utteranceId)

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = finish(utteranceId)
                    override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
                })
            }
            localeFor = null
        }
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready?.await() } ?: run {
            // An engine that never answered counts as failed, so the next line doesn't wait again
            // (warmUp() still starts a fresh one).
            ready?.complete(false)
            false
        }
        val engine = tts ?: return null
        if (!ok) return null
        if (localeFor != appLanguage) {
            localeFor = appLanguage
            val pick = VoiceLocales.speech(appLanguage).firstOrNull {
                engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            } ?: Locale.getDefault()
            engine.setLanguage(pick)
        }
        return engine
    }

    /**
     * True when a voice for [locale] can speak: the unit's TTS engine has one (any country variant
     * counts), or Ampli's bundled engine does.
     */
    suspend fun hasVoice(locale: Locale): Boolean =
        systemHasVoice(locale) || BundledTts.covers(locale.language, bundled.languages())

    /** The unit's own TTS engine started and has a voice for [locale]. */
    private suspend fun systemHasVoice(locale: Locale): Boolean {
        val engine = engine(localeFor ?: "") ?: return false
        return try {
            engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE
        } catch (_: Exception) {
            false
        }
    }

    /** True while either engine is talking. */
    fun isSpeaking(): Boolean = bundled.isSpeaking() || try {
        tts?.isSpeaking == true
    } catch (_: Exception) {
        false
    }

    /** Any text-to-speech service is installed on the unit (Google's, the vendor's, eSpeak…). */
    @Suppress("DEPRECATION")
    fun enginesInstalled(): Boolean = try {
        appContext.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
    } catch (_: Exception) {
        // Can't tell: try the engine as before.
        true
    }

    /** False when nothing can speak (no working TTS engine and no bundled voice yet). */
    suspend fun isAvailable(): Boolean = bundled.ready() || systemAvailable()

    /** The unit's own TTS engine answered (the bundled engine aside). */
    suspend fun systemAvailable(): Boolean = engine(localeFor ?: "") != null

    /** Which engine would speak a line in [appLanguage] now (Settings › Voice › Check voice). */
    suspend fun engineFor(appLanguage: String): BundledTts.Engine {
        val system = engine(appLanguage)
        val wanted = VoiceLocales.speech(appLanguage).first()
        return BundledTts.pick(
            systemReady = system != null,
            systemHasLanguage = system != null && systemHasVoice(wanted),
            bundledHasLanguage = BundledTts.covers(wanted.language, bundled.languages()),
            bundledReady = bundled.ready(),
        )
    }

    /**
     * Gets the engine ready right after boot, when the TTS service may still be starting: waits
     * for onInit (up to [INIT_TIMEOUT_MS]), and if it failed or never answered, frees it, waits
     * [BOOT_RETRY_MS] and starts a fresh one once more. True when an engine is ready.
     */
    suspend fun warmUp(): Boolean {
        if (isAvailable()) return true
        shutdown()
        delay(BOOT_RETRY_MS)
        return isAvailable()
    }

    /** A phone call is ringing or in progress (also Bluetooth calls through the head unit). */
    fun inCall(): Boolean = when (audio?.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private fun finish(id: String?) {
        // Called on a TTS binder thread; CompletableDeferred is thread-safe.
        synchronized(pending) { pending.remove(id ?: return) }?.complete(Unit)
    }

    /**
     * Speaks [text] and returns when it has finished (or after a safety timeout). With
     * [needFocus], nothing is said when Android refuses audio focus (a call is on).
     */
    suspend fun say(text: String, appLanguage: String, needFocus: Boolean = false) {
        sayChecked(text, appLanguage, needFocus)
    }

    /** [say] that returns false when nothing could be spoken (no engine, focus refused, speak failed). */
    suspend fun sayChecked(text: String, appLanguage: String, needFocus: Boolean = false): Boolean {
        if (text.isBlank()) return false
        when (engineFor(appLanguage)) {
            BundledTts.Engine.Bundled -> return sayBundled(text, appLanguage, needFocus)
            BundledTts.Engine.None, BundledTts.Engine.System -> Unit
        }
        val engine = engine(appLanguage) ?: run {
            // No TTS engine on this unit: leave the reply on screen a moment to read, but never
            // so long that a question's answer window (call, message) is lost waiting.
            delay(SpeechPacing.silentHoldMs(text.length))
            return false
        }
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        synchronized(pending) { pending[id] = done }
        val focus = audio?.requestAudioFocus(duckFocus)
        if (needFocus && focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            synchronized(pending) { pending.remove(id) }
            return false
        }
        try {
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) return false
            // Roughly 12 characters a second; never wait forever if the engine stays silent.
            withTimeoutOrNull(4_000L + text.length * 120L) { done.await() }
            return true
        } finally {
            synchronized(pending) { pending.remove(id) }
            audio?.abandonAudioFocusRequest(duckFocus)
        }
    }

    /**
     * Says [text] with Ampli's bundled engine whatever the unit has (Check voice's test, and
     * [sayChecked] when [BundledTts.pick] chose it). False when it could not speak.
     */
    suspend fun sayBundled(text: String, appLanguage: String, needFocus: Boolean = false): Boolean {
        if (text.isBlank() || !bundled.ready()) return false
        val focus = audio?.requestAudioFocus(duckFocus)
        if (needFocus && focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return false
        return try {
            bundled.speak(text, VoiceLocales.parserLanguage(appLanguage), speechAttributes)
        } finally {
            audio?.abandonAudioFocusRequest(duckFocus)
        }
    }

    /** Short "I'm listening" beep before the microphone opens. */
    suspend fun earcon() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            delay(220)
            tone.release()
        } catch (_: RuntimeException) {
            // Some units refuse a ToneGenerator; the on-screen mic is the cue then.
        }
    }

    /** Short warning beep for speed-camera alerts, with music ducked meanwhile. */
    suspend fun alertBeep() {
        audio?.requestAudioFocus(duckFocus)
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            delay(380)
            tone.release()
        } catch (_: RuntimeException) {
            // No ToneGenerator on this unit: the red chip is the cue then.
        } finally {
            audio?.abandonAudioFocusRequest(duckFocus)
        }
    }

    fun stop() {
        bundled.stop()
        tts?.stop()
        synchronized(pending) {
            pending.values.forEach { it.complete(Unit) }
            pending.clear()
        }
        audio?.abandonAudioFocusRequest(duckFocus)
    }

    /** Frees the engine; the next [say] starts it again. */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = null
        localeFor = null
    }

    private companion object {
        const val INIT_TIMEOUT_MS = 5_000L
        const val BOOT_RETRY_MS = 5_000L
    }
}

/** Pure timing for lines that cannot be spoken (unit-tested). */
object SpeechPacing {
    /** Shortest and longest time an unspoken line stays before Ampli moves on (and listens). */
    const val SILENT_MIN_MS = 1_200L
    const val SILENT_MAX_MS = 3_500L

    /** How long an unspoken line of [chars] characters holds the conversation. */
    fun silentHoldMs(chars: Int): Long = (SILENT_MIN_MS + chars.coerceAtLeast(0) * 30L).coerceAtMost(SILENT_MAX_MS)
}

/** Home's one-time "Bolne ka engine nahi hai · Install karo" card (unit-tested). */
object TtsNotice {
    /**
     * Show it: no speech engine, never shown on this unit before, and parked. Not while Ampli's
     * bundled voice is still being installed ([bundledComing]): it will speak in a moment.
     */
    fun showCard(ttsMissing: Boolean, shownBefore: Boolean, moving: Boolean, bundledComing: Boolean = false): Boolean =
        ttsMissing && !shownBefore && !moving && !bundledComing
}
