package com.vikash.ampli.drive.voice

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vikash.ampli.drive.DriveApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Speaks a [Spoken] line: the recorded voice pack (Settings › Voice: Male "Ampli Bhai" / Female
 * "Ampli Behen") when every clip of the line is bundled and up to date, otherwise the phone's TTS
 * ([Speaker]). Clips are mono Opus in Ogg, played back to back by ExoPlayer (its own Ogg reader
 * plus the platform Opus decoder, so it works on Android 8). Like the TTS path it holds
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK while talking, so music dips instead of stopping.
 * All calls from the main thread.
 */
class VoicePlayer(private val app: DriveApp) {
    private val audio = app.getSystemService(AudioManager::class.java)
    private var player: ExoPlayer? = null
    private var playing: CompletableDeferred<Boolean>? = null

    /** What is bundled in this build. Loaded once; an APK never changes its assets. */
    val library: ClipLibrary by lazy { load() }

    private val focusAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val duckFocus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(focusAttributes)
        .build()

    /** The voice in use (Settings › Voice), with the default applied. */
    val voice: VoicePack get() = VoicePack.resolve(app.prefs.voicePack.value, library.count(VoicePack.Male))

    /**
     * Says [spoken] and returns when done. With [needFocus], nothing is said when Android refuses
     * audio focus (a call is on).
     */
    suspend fun say(spoken: Spoken, needFocus: Boolean = false) {
        val v = voice
        val clips = library.plan(v, spoken)
        if (clips != null && v.folder != null) {
            val uris = clips.map { Uri.parse("asset:///voice/${v.folder}/$it.ogg") }
            when (play(uris, needFocus)) {
                Result.Played, Result.NoFocus -> return
                Result.Failed -> Unit // Fall through to TTS below.
            }
        }
        app.speaker.say(spoken.text, spoken.language, needFocus)
    }

    private enum class Result { Played, NoFocus, Failed }

    private suspend fun play(uris: List<Uri>, needFocus: Boolean): Result {
        stopPlayer()
        val focus = audio?.requestAudioFocus(duckFocus)
        if (needFocus && focus == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return Result.NoFocus
        val done = CompletableDeferred<Boolean>()
        playing = done
        val exo = try {
            ExoPlayer.Builder(app).build()
        } catch (e: Exception) {
            Log.w(TAG, "no player", e)
            audio?.abandonAudioFocusRequest(duckFocus)
            return Result.Failed
        }
        player = exo
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_ASSISTANT)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            // Focus is ours (may-duck, like TTS), so ExoPlayer must not ask for its own.
            exo.setAudioAttributes(attrs, false)
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) done.complete(true)
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "clip failed", error)
                    done.complete(false)
                }
            })
            exo.setMediaItems(uris.map { MediaItem.fromUri(it) })
            exo.prepare()
            exo.play()
            // Clips are a few seconds each; never wait forever.
            val ok = withTimeoutOrNull(5_000L + uris.size * 8_000L) { done.await() } ?: false
            return if (ok) Result.Played else Result.Failed
        } finally {
            if (player === exo) player = null
            if (playing === done) playing = null
            exo.release()
            audio?.abandonAudioFocusRequest(duckFocus)
        }
    }

    private fun stopPlayer() {
        // A newer line cuts the one playing, like TTS's QUEUE_FLUSH. Counts as played, no TTS after.
        playing?.complete(true)
        player?.stop()
    }

    /** Stops the clip and the TTS. */
    fun stop() {
        stopPlayer()
        app.speaker.stop()
    }

    private fun load(): ClipLibrary {
        val recorded = HashMap<VoicePack, Map<String, String>>()
        for (v in VoicePack.entries) {
            val folder = v.folder ?: continue
            val index = try {
                app.assets.open("voice/$folder/clips.json").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                continue // Not in this build yet.
            }
            try {
                val clips = JSONObject(index).getJSONObject("clips")
                val map = HashMap<String, String>()
                for (id in clips.keys()) map[id] = clips.getString(id)
                recorded[v] = map
            } catch (e: Exception) {
                Log.w(TAG, "bad clips.json for $folder", e)
            }
        }
        return ClipLibrary(recorded)
    }

    private companion object {
        const val TAG = "VoicePlayer"
    }
}
