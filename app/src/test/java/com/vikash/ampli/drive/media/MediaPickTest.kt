package com.vikash.ampli.drive.media

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPickTest {
    private fun c(pkg: String, playing: Boolean = false, title: Boolean = true) = MediaPick.Candidate(pkg, playing, title)

    @Test
    fun idleVendorSessionNoLongerHidesAmpli() {
        // TS7: the stock BT music session (no metadata) is listed first; Ampli MP3 is paused with a title.
        val list = listOf(c("com.tw.btmusic", title = false), c("com.vikash.ampli"))
        assertEquals(1, MediaPick.choose(list, pinned = null, preferred = listOf("com.vikash.ampli")))
        // Even without a preference, a session with a title wins over one without.
        assertEquals(1, MediaPick.choose(list, pinned = null, preferred = emptyList()))
    }

    @Test
    fun playingAndPinnedWin() {
        val list = listOf(c("com.vikash.ampli"), c("com.android.bluetooth", playing = true))
        assertEquals(1, MediaPick.choose(list, null, listOf("com.vikash.ampli")))
        assertEquals(0, MediaPick.choose(list, "com.vikash.ampli", emptyList()))
        assertNull(MediaPick.choose(emptyList(), null, emptyList()))
        assertEquals(0, MediaPick.choose(listOf(c("a", title = false)), null, emptyList()))
    }

    @Test
    fun onlyStatePlayingCountsAsPlaying() {
        assertTrue(MediaHub.playingState(PlaybackState.STATE_PLAYING))
        assertFalse(MediaHub.playingState(PlaybackState.STATE_PAUSED))
        assertFalse(MediaHub.playingState(PlaybackState.STATE_STOPPED))
        assertFalse(MediaHub.playingState(null))
    }

    @Test
    fun bluetoothSourcesAreRecognised() {
        assertTrue(MediaSources.isBluetooth("com.android.bluetooth"))
        assertTrue(MediaSources.isBluetooth("com.syu.bt"))
        assertTrue(MediaSources.isBluetooth("com.tw.btmusic"))
        assertTrue(MediaSources.isBluetooth("com.acme.bluetooth.music"))
        assertFalse(MediaSources.isBluetooth("com.vikash.ampli"))
        assertFalse(MediaSources.isBluetooth("com.spotify.music"))
        assertFalse(MediaSources.isBluetooth(null))
    }
}
