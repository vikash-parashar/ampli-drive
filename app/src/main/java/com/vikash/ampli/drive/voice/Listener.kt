package com.vikash.ampli.drive.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One utterance through Android's SpeechRecognizer, after the wake word (or the Home button).
 * Prefers the on-device recogniser (API 31+) and falls back to the default service when the
 * on-device one is missing or lacks the language. The wake-word engine has already released
 * the microphone by the time this runs. All calls from the main thread.
 */
class Listener(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private var current: SpeechRecognizer? = null

    /** Music pauses (not just ducks) while the driver talks, so the recogniser hears them. */
    private val listenFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()

    sealed interface Result {
        data class Heard(val text: String) : Result
        data object Silence : Result
        data object Unavailable : Result
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext) ||
        SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

    /** Listens once. [onPartial] gets the live transcript for the overlay. */
    suspend fun listen(appLanguage: String, onPartial: (String) -> Unit): Result {
        audio?.requestAudioFocus(listenFocus)
        try {
            val tags = VoiceLocales.recognition(appLanguage)
            val onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            val service = SpeechRecognizer.isRecognitionAvailable(appContext)
            // The app language first on either engine, English only as the last resort.
            val attempts = buildList {
                for (tag in tags) {
                    if (onDevice) add(true to tag)
                    if (service) add(false to tag)
                }
            }
            if (attempts.isEmpty()) return Result.Unavailable
            for ((device, tag) in attempts) {
                when (val r = once(device, tag, onPartial)) {
                    Attempt.LanguageMissing, Attempt.EngineFailed -> continue
                    is Attempt.Done -> return r.result
                }
            }
            return Result.Unavailable
        } finally {
            audio?.abandonAudioFocusRequest(listenFocus)
        }
    }

    fun cancel() {
        current?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        current = null
    }

    private sealed interface Attempt {
        data class Done(val result: Result) : Attempt
        data object LanguageMissing : Attempt
        data object EngineFailed : Attempt
    }

    private suspend fun once(onDevice: Boolean, tag: String, onPartial: (String) -> Unit): Attempt {
        val recognizer = try {
            if (onDevice) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (_: Exception) {
            return Attempt.EngineFailed
        }
        current = recognizer
        val outcome = CompletableDeferred<Attempt>()
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                outcome.complete(
                    Attempt.Done(if (text.isNullOrBlank()) Result.Silence else Result.Heard(text)),
                )
            }

            override fun onError(error: Int) {
                outcome.complete(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            Attempt.Done(Result.Silence)
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            Attempt.LanguageMissing
                        else -> Attempt.EngineFailed
                    },
                )
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        return try {
            recognizer.startListening(intent)
            withTimeoutOrNull(LISTEN_TIMEOUT_MS) { outcome.await() } ?: Attempt.Done(Result.Silence)
        } catch (_: Exception) {
            Attempt.EngineFailed
        } finally {
            try {
                recognizer.cancel()
                recognizer.destroy()
            } catch (_: Exception) {
            }
            if (current === recognizer) current = null
        }
    }

    private companion object {
        const val LISTEN_TIMEOUT_MS = 15_000L
    }
}
