package com.vikash.ampli.drive.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One utterance through Android's SpeechRecognizer, after the wake word (or the Home button).
 * Prefers the on-device recogniser (API 31+) and falls back to the default service when the
 * on-device one is missing or lacks the language. The wake-word engine has already released
 * the microphone by the time this runs. All calls from the main thread.
 *
 * Some head units report a recognition service that is only a vendor stub (it never hears
 * anything). A turn on which the system engine shows no sign of life falls through to the offline
 * Vosk model in the same turn, and after [RecognizerFallback.DEAD_AFTER] such turns the system
 * engine is skipped ([SystemRecognizerHealth]).
 */
class Listener(context: Context, private val offline: VoskCommandListener? = null) {
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
        /** [offline]: heard by the Vosk fallback (English command phrases). */
        data class Heard(val text: String, val offline: Boolean = false) : Result
        data object Silence : Result
        data object Unavailable : Result
    }

    fun isAvailable(): Boolean = systemAvailable() || offline?.isReady() == true

    /** Android's own recogniser (Google or the vendor's), without the offline fallback. */
    fun systemAvailable(): Boolean = systemPresent(appContext)

    private fun onDeviceAvailable(): Boolean = onDevicePresent(appContext)

    /** Which engine served the last [listen] of this listener. */
    var lastEngine: VoiceEngine = VoiceEngine.None
        private set

    /**
     * True when the next [listen] goes to the offline model: there is no system recogniser, or
     * it has been skipped for showing no sign of life. (A system turn can still fall through to
     * the offline model when it fails.)
     */
    fun willServeOffline(): Boolean {
        val vosk = offline ?: return false
        if (!vosk.isReady()) return false
        return !systemAvailable() || RecognizerFallback.skipSystem(SystemRecognizerHealth.deadTurns(appContext), true)
    }

    /**
     * Listens once. [onPartial] gets the live transcript for the overlay. Without a system
     * recogniser (or when it fails), the offline Vosk model listens instead: with [commandsOnly]
     * it hears just the command phrases, else it transcribes freely (for "where to?").
     */
    suspend fun listen(appLanguage: String, onPartial: (String) -> Unit, commandsOnly: Boolean = true): Result {
        audio?.requestAudioFocus(listenFocus)
        try {
            lastEngine = VoiceEngine.None
            val vosk = offline
            val offlineReady = vosk != null && vosk.isReady()
            // A system recogniser that showed no sign of life lately: straight to the offline model.
            if (vosk != null && RecognizerFallback.skipSystem(SystemRecognizerHealth.deadTurns(appContext), offlineReady)) {
                val r = listenOffline(vosk, commandsOnly, onPartial)
                if (r != Result.Unavailable) return r
                // The offline model would not load: give the system recogniser its turn after all.
            }
            val tags = VoiceLocales.recognition(appLanguage)
            val onDevice = onDeviceAvailable()
            val service = SpeechRecognizer.isRecognitionAvailable(appContext)
            // The app language first on either engine, English only as the last resort.
            val attempts = buildList {
                for (tag in tags) {
                    if (onDevice) add(true to tag)
                    if (service) add(false to tag)
                }
            }
            var turn: SystemAttempt? = null
            var systemResult: Result? = null
            for ((device, tag) in attempts) {
                val r = once(device, tag, onPartial)
                val kind = classify(r)
                // The turn counts as alive once any attempt showed life.
                val before = turn
                if (kind != SystemAttempt.LanguageMissing && (before == null || RecognizerFallback.isDead(before))) turn = kind
                if (kind == SystemAttempt.LanguageMissing) continue
                if (RecognizerFallback.fallThrough(kind, offlineReady)) break
                if (r is Attempt.Done) {
                    systemResult = r.result
                    break
                }
                // EngineFailed without an offline model: try the next engine or language.
            }
            turn?.let { SystemRecognizerHealth.record(appContext, it) }
            systemResult?.let {
                lastEngine = VoiceEngine.System
                SystemRecognizerHealth.served(VoiceEngine.System)
                return it
            }
            // No recogniser, every attempt failed (no Google app, no network), or a stub that
            // never heard anything: offline Vosk in the same turn.
            if (vosk != null && offlineReady) return listenOffline(vosk, commandsOnly, onPartial)
            return Result.Unavailable
        } finally {
            audio?.abandonAudioFocusRequest(listenFocus)
        }
    }

    private suspend fun listenOffline(vosk: VoskCommandListener, commandsOnly: Boolean, onPartial: (String) -> Unit): Result {
        val r = vosk.listen(commandsOnly, onPartial)
        if (r != Result.Unavailable) {
            lastEngine = vosk.lastEngine
            SystemRecognizerHealth.served(vosk.lastEngine)
        }
        return r
    }

    private fun classify(a: Attempt): SystemAttempt = when (a) {
        Attempt.LanguageMissing -> SystemAttempt.LanguageMissing
        Attempt.EngineFailed -> SystemAttempt.Failed
        is Attempt.Done -> when {
            a.result is Result.Heard -> SystemAttempt.Heard
            a.activity -> SystemAttempt.QuietWithActivity
            else -> SystemAttempt.QuietNoActivity
        }
    }

    fun cancel() {
        offline?.cancel()
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
        /** [activity]: the engine showed it heard sound (speech start, level, partials). */
        data class Done(val result: Result, val activity: Boolean) : Attempt
        data object LanguageMissing : Attempt
        data object EngineFailed : Attempt
    }

    private suspend fun once(onDevice: Boolean, tag: String, onPartial: (String) -> Unit): Attempt {
        val recognizer = try {
            if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (_: Exception) {
            return Attempt.EngineFailed
        }
        current = recognizer
        val outcome = CompletableDeferred<Attempt>()
        // Any callback at all (even "ready"), and real signs of hearing sound.
        var alive = false
        var activity = false
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                alive = true
            }

            override fun onBeginningOfSpeech() {
                alive = true
                activity = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                alive = true
                activity = true
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                alive = true
            }

            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                alive = true
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
                activity = true
                onPartial(partial)
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                outcome.complete(
                    Attempt.Done(if (text.isNullOrBlank()) Result.Silence else Result.Heard(text), activity),
                )
            }

            override fun onError(error: Int) {
                outcome.complete(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            Attempt.Done(Result.Silence, activity)
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
            // An engine that does not even say "ready" within a few seconds is a stub: don't make
            // the driver wait the full listening time before the offline model takes over.
            withTimeoutOrNull(NO_RESPONSE_MS) { outcome.await() }
                ?: if (!alive) {
                    Attempt.Done(Result.Silence, activity = false)
                } else {
                    withTimeoutOrNull(LISTEN_TIMEOUT_MS - NO_RESPONSE_MS) { outcome.await() } ?: Attempt.Done(Result.Silence, activity)
                }
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

    companion object {
        private const val LISTEN_TIMEOUT_MS = 15_000L
        private const val NO_RESPONSE_MS = 6_000L

        /** Android's own recogniser (Google or the vendor's) is installed, working or not. */
        fun systemPresent(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context) || onDevicePresent(context)

        /** On-device recognition is an Android 12+ API; older units use the recognition service only. */
        private fun onDevicePresent(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        /**
         * A system recogniser that is present and has not been marked dead by recent turns (this
         * run, or the last one before it heard anything).
         */
        fun systemWorks(context: Context): Boolean =
            systemPresent(context) && !SystemRecognizerHealth.lookedDead(context)
    }
}

/**
 * Remembers whether the system recogniser really works on this unit: turns in a row on which it
 * showed no sign of life (in memory for the process, saved for the next start, reset when it
 * hears something or the recognition app changes), and which engine served the last turn.
 * Main thread.
 */
object SystemRecognizerHealth {
    private const val PREFS = "voice_engine"
    private const val KEY_DEAD = "systemDeadTurns"
    private const val KEY_SIGNATURE = "systemSignature"

    private var loaded = false
    private var dead = 0

    /** The count saved by an earlier run (not capped), until the system engine shows life. */
    private var savedAtStart = 0

    private val _lastEngine = MutableStateFlow(VoiceEngine.None)

    /** The engine that served the last voice turn (Settings › Voice › Check voice). */
    val lastEngine: StateFlow<VoiceEngine> = _lastEngine.asStateFlow()

    fun deadTurns(context: Context): Int {
        load(context)
        return dead
    }

    /** Dead now, or dead when the app last ran and not heard from since. */
    fun lookedDead(context: Context): Boolean {
        load(context)
        return maxOf(dead, savedAtStart) >= RecognizerFallback.DEAD_AFTER
    }

    fun record(context: Context, attempt: SystemAttempt) {
        load(context)
        val next = RecognizerFallback.nextDeadTurns(dead, attempt)
        if (next == 0) savedAtStart = 0
        if (next == dead) return
        dead = next
        prefs(context).edit().putInt(KEY_DEAD, next).apply()
    }

    fun served(engine: VoiceEngine) {
        _lastEngine.value = engine
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        val signature = signature(context)
        // A new or updated recognition app (Google installed?) starts with a clean slate.
        val saved = if (p.getString(KEY_SIGNATURE, null) == signature) p.getInt(KEY_DEAD, 0) else 0
        savedAtStart = saved
        dead = RecognizerFallback.restoredDeadTurns(saved)
        // The saved count stays until a turn changes it, so lookedDead() holds across restarts.
        p.edit().putString(KEY_SIGNATURE, signature).putInt(KEY_DEAD, saved).apply()
    }

    /** The installed recognition services and when each was last updated. */
    @Suppress("DEPRECATION")
    private fun signature(context: Context): String = try {
        val pm = context.packageManager
        pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo.packageName }
            .distinct()
            .sorted()
            .joinToString(",") { pkg ->
                val updated = try {
                    pm.getPackageInfo(pkg, 0).lastUpdateTime
                } catch (_: Exception) {
                    0L
                }
                "$pkg@$updated"
            }
    } catch (_: Exception) {
        ""
    }
}
