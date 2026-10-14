package com.vikash.ampli.drive.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.MainActivity
import com.vikash.ampli.drive.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException

/**
 * Always-on "Hey Ampli" wake word: a microphone foreground service running Vosk offline with a
 * tiny grammar, so only the wake phrases (or "[unk]") can come out. Nothing leaves the unit.
 *
 * Start and stop:
 * - Started from MainActivity.onResume while "Always listen" is on. The launcher is the HOME
 *   app, so it is on screen right after boot anyway. There is deliberately no BOOT_COMPLETED
 *   receiver: Android 14+ forbids starting a microphone foreground service from boot, and on
 *   11–13 a service started that way gets no microphone access (while-in-use rule), so it
 *   would only record silence.
 * - Not sticky: if Android kills it, it comes back the next time the launcher shows.
 *
 * The mic is released (AudioRecord freed, not just paused) while the assistant listens or
 * speaks, during phone calls (AudioManager mode; no READ_PHONE_STATE needed) and while a
 * navigation prompt or a voice call is playing.
 */
class VoiceService : Service() {
    private val app: DriveApp get() = application as DriveApp
    private val main = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val audio: AudioManager? get() = getSystemService(AudioManager::class.java)

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: SpeechService? = null
    private var initialised = false

    private var tuning = WakeSensitivity.tuning(3)
    private var assistantBusy = false
    private var enabled = true
    private var inCall = false
    private var promptPlaying = false

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        inCall = mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_CALL_SCREENING
        evaluate()
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            promptPlaying = configs.any {
                val usage = it.audioAttributes.usage
                usage == AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE ||
                    usage == AudioAttributes.USAGE_VOICE_COMMUNICATION
            }
            evaluate()
        }
    }

    private val rearm = Runnable { evaluate() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (_: Exception) {
            // Android refused (for example started from the background on 14+).
            stopSelf()
            return START_NOT_STICKY
        }
        if (!initialised) {
            initialised = true
            init()
        }
        return START_NOT_STICKY
    }

    private fun init() {
        app.voice.releaseWakeMic = { stopMic() }
        audio?.let {
            it.addOnModeChangedListener(mainExecutor, modeListener)
            it.registerAudioPlaybackCallback(playbackCallback, main)
            modeListener.onModeChanged(it.mode)
        }
        scope.launch {
            combine(app.voice.busy, app.prefs.voiceAlways) { busy, on -> busy to on }.collect { (busy, on) ->
                assistantBusy = busy
                enabled = on
                if (!on) {
                    stopSelf()
                    return@collect
                }
                // Short gap after a session so the end of Ampli's own reply isn't heard as speech.
                main.removeCallbacks(rearm)
                if (busy) evaluate() else main.postDelayed(rearm, REARM_DELAY_MS)
            }
        }
        // A new sensitivity takes effect by reopening the recognizer with the new grammar.
        scope.launch {
            app.prefs.wakeSensitivity.collect { level ->
                if (speech != null && WakeSensitivity.tuning(level) != tuning) {
                    stopMic()
                    evaluate()
                }
            }
        }
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                    Model(app.wakeModel.modelDir.absolutePath)
                } catch (_: Throwable) {
                    null
                }
            }
            if (loaded == null) {
                stopSelf()
                return@launch
            }
            model = loaded
            evaluate()
        }
    }

    private fun evaluate() {
        val listen = model != null && enabled && !assistantBusy && !inCall && !promptPlaying
        if (listen && speech == null) startMic() else if (!listen && speech != null) stopMic()
    }

    private fun startMic() {
        val m = model ?: return
        try {
            tuning = WakeSensitivity.tuning(app.prefs.wakeSensitivity.value)
            val r = Recognizer(m, SAMPLE_RATE, tuning.grammarJson)
            // Per-word confidences, for the sensitivity threshold.
            r.setWords(true)
            val s = SpeechService(r, SAMPLE_RATE)
            recognizer = r
            speech = s
            s.startListening(wakeListener)
        } catch (_: IOException) {
            // Mic held by another app (a call, Google Assistant). Try again shortly.
            stopMic()
            main.postDelayed(rearm, RETRY_MS)
        }
    }

    private fun stopMic() {
        speech?.let {
            try {
                it.cancel()
                it.shutdown()
            } catch (_: Exception) {
            }
        }
        speech = null
        recognizer?.close()
        recognizer = null
    }

    private val wakeListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) = checkWake(hypothesis, partial = true)
        override fun onResult(hypothesis: String?) = checkWake(hypothesis, partial = false)
        override fun onFinalResult(hypothesis: String?) = Unit
        override fun onTimeout() = Unit
        override fun onError(exception: Exception?) {
            stopMic()
            main.postDelayed(rearm, RETRY_MS)
        }
    }

    private fun checkWake(json: String?, partial: Boolean) {
        val wake = try {
            val o = JSONObject(json ?: return)
            if (partial) {
                WakeSensitivity.acceptsPartial(o.optString("partial"), tuning)
            } else {
                val arr = o.optJSONArray("result")
                val words = if (arr == null) {
                    emptyList()
                } else {
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { w -> w.optString("word") to w.optDouble("conf", 1.0) }
                    }
                }
                WakeSensitivity.acceptsFinal(o.optString("text"), words, tuning)
            }
        } catch (_: Exception) {
            return
        }
        if (wake) {
            stopMic()
            app.voice.start()
        }
    }

    override fun onDestroy() {
        main.removeCallbacks(rearm)
        stopMic()
        audio?.let {
            try {
                it.removeOnModeChangedListener(modeListener)
                it.unregisterAudioPlaybackCallback(playbackCallback)
            } catch (_: Exception) {
            }
        }
        if (app.voice.releaseWakeMic != null) app.voice.releaseWakeMic = null
        scope.cancel()
        model?.close()
        model = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.voice_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.voice_notification_title))
            .setContentText(getString(R.string.voice_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4107
        private const val CHANNEL_ID = "hey_ampli"
        private const val SAMPLE_RATE = 16000f
        private const val REARM_DELAY_MS = 700L
        private const val RETRY_MS = 5_000L

        fun start(context: Context): Boolean {
            val app = context.applicationContext as DriveApp
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
            if (!app.prefs.voiceAlways.value || !app.wakeModel.isInstalled()) return false
            return try {
                context.startForegroundService(Intent(context, VoiceService::class.java))
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }
    }
}
