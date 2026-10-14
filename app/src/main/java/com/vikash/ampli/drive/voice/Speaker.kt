package com.vikash.ampli.drive.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Android TextToSpeech in the app language (see [VoiceLocales]), falling back to en-IN.
 * While Ampli speaks it holds AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, so music dips instead of stopping.
 * All calls from the main thread.
 */
class Speaker(context: Context) {
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
        val ok = withTimeoutOrNull(INIT_TIMEOUT_MS) { ready?.await() } ?: false
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

    private fun finish(id: String?) {
        // Called on a TTS binder thread; CompletableDeferred is thread-safe.
        synchronized(pending) { pending.remove(id ?: return) }?.complete(Unit)
    }

    /** Speaks [text] and returns when it has finished (or after a safety timeout). */
    suspend fun say(text: String, appLanguage: String) {
        if (text.isBlank()) return
        val engine = engine(appLanguage) ?: return
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        synchronized(pending) { pending[id] = done }
        audio?.requestAudioFocus(duckFocus)
        try {
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) return
            // Roughly 12 characters a second; never wait forever if the engine stays silent.
            withTimeoutOrNull(4_000L + text.length * 120L) { done.await() }
        } finally {
            synchronized(pending) { pending.remove(id) }
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

    fun stop() {
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
    }
}
