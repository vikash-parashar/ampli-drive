package com.vikash.ampli.drive.media

import com.vikash.ampli.drive.media.MediaTarget.Action
import com.vikash.ampli.drive.media.MediaTarget.Target
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTargetTest {
    private val ampli = "com.vikash.ampli"
    private val bt = SessionInfo("com.syu.btmusic", playing = false, bluetooth = true)

    private fun pick(
        sessions: List<SessionInfo>,
        action: Action = Action.Next,
        phone: Boolean = false,
        active: Boolean = false,
        last: String? = null,
        installed: Boolean = true,
    ) = MediaTarget.pick(sessions, action, ampli, installed, phone, active, last)

    @Test
    fun ampliWinsWhenItIsPlaying() {
        val s = listOf(SessionInfo("com.spotify.music", playing = true), SessionInfo(ampli, playing = true))
        assertEquals(Target.Session(ampli), pick(s, phone = true))
    }

    @Test
    fun otherwiseWhateverIsPlaying() {
        assertEquals(Target.Session(bt.packageName), pick(listOf(SessionInfo(ampli, false), bt.copy(playing = true))))
        assertEquals(Target.Session("com.spotify.music"), pick(listOf(SessionInfo("com.spotify.music", true))))
    }

    @Test
    fun audibleWithoutASessionIsThePhone() {
        assertEquals(Target.PhoneKeys, pick(emptyList(), phone = true, active = true))
        assertEquals(Target.MediaKey, pick(emptyList(), phone = false, active = true))
    }

    @Test
    fun nothingPlayingGoesBackToTheLastSource() {
        val s = listOf(SessionInfo(ampli, false))
        assertEquals(Target.PhoneKeys, pick(s, action = Action.Play, phone = true, last = MediaTarget.PHONE))
        assertEquals(Target.Session(ampli), pick(s, action = Action.Play, phone = true, last = ampli))
        // Never asked before: Ampli MP3 first, then the phone.
        assertEquals(Target.Session(ampli), pick(s, action = Action.Play, phone = true))
        assertEquals(Target.Session(bt.packageName), pick(listOf(bt), action = Action.Play, phone = true))
        assertEquals(Target.PhoneKeys, pick(emptyList(), action = Action.Play, phone = true))
    }

    @Test
    fun playWithNothingAroundOpensAmpli() {
        assertEquals(Target.OpenAmpli, pick(emptyList(), action = Action.Play))
        assertEquals(Target.MediaKey, pick(emptyList(), action = Action.Play, installed = false))
        assertEquals(Target.MediaKey, pick(emptyList(), action = Action.Next))
    }

    @Test
    fun remembersWhatPlays() {
        assertEquals(MediaTarget.PHONE, MediaTarget.playingSource(listOf(bt.copy(playing = true)), true, true))
        assertEquals(ampli, MediaTarget.playingSource(listOf(SessionInfo(ampli, true)), true, true))
        assertEquals(MediaTarget.PHONE, MediaTarget.playingSource(emptyList(), musicActive = true, phoneConnected = true))
        assertEquals(null, MediaTarget.playingSource(emptyList(), musicActive = false, phoneConnected = true))
    }
}
