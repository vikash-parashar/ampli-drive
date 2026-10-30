package com.vikash.ampli.drive.media

import android.media.AudioManager
import com.vikash.ampli.drive.voice.VoiceIntent.VolumeAction

/** What the volume is after a command, for the answer card. */
data class VolumeResult(val level: Int, val max: Int, val muted: Boolean)

/**
 * Music volume (STREAM_MUSIC) by voice. Android's own volume panel shows the change
 * (FLAG_SHOW_UI), over whatever app is in front. Main thread.
 */
object VoiceVolume {

    fun apply(audio: AudioManager?, action: VolumeAction, level: Int? = null): VolumeResult? {
        val am = audio ?: return null
        return try {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val flags = AudioManager.FLAG_SHOW_UI
            when (action) {
                VolumeAction.Mute -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, flags)
                VolumeAction.Unmute -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, flags)
                    // Unmuting a stream that sits at 0 is still silent: bring it to a third.
                    if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max / 3).coerceAtLeast(1), flags)
                    }
                }
                VolumeAction.Show -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, flags)
                else -> {
                    val target = VolumeMath.target(action, current, max, level) ?: current
                    // A muted stream stays muted on some ROMs after setStreamVolume.
                    if (target > 0) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, flags)
                }
            }
            val now = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            VolumeResult(now, max, muted = action == VolumeAction.Mute || isMuted(am) || now == 0)
        } catch (_: SecurityException) {
            // Do Not Disturb can refuse volume changes.
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun isMuted(am: AudioManager): Boolean = try {
        am.isStreamMute(AudioManager.STREAM_MUSIC)
    } catch (_: Exception) {
        false
    }
}
