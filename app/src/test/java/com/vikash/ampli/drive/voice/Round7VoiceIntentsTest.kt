package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.voice.VoiceIntent.VolumeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 7: volume, stop, music keys, time, date, weather, speed, maps, brightness, help. */
class Round7VoiceIntentsTest {
    private fun en(t: String) = VoiceIntents.parse(t, "en")
    private fun hi(t: String) = VoiceIntents.parse(t, "hi")
    private fun vol(a: VolumeAction, level: Int? = null) = VoiceIntent.Volume(a, level)

    @Test
    fun volumeUpAndDown() {
        assertEquals(vol(VolumeAction.Up), en("volume up"))
        assertEquals(vol(VolumeAction.Up), en("hey ampli turn the volume up"))
        assertEquals(vol(VolumeAction.Up), en("increase the volume"))
        assertEquals(vol(VolumeAction.Up), en("awaaz badhao"))
        assertEquals(vol(VolumeAction.Up), en("thoda awaaz tez karo"))
        assertEquals(vol(VolumeAction.Up), hi("आवाज़ बढ़ाओ"))
        // The same word with the dot written as a separate sign, and without it.
        assertEquals(vol(VolumeAction.Up), hi("आवाज़ बढ़ाओ"))
        assertEquals(vol(VolumeAction.Up), hi("आवाज बढाओ"))
        assertEquals(vol(VolumeAction.Up), en("louder"))
        assertEquals(vol(VolumeAction.Down), en("volume down"))
        assertEquals(vol(VolumeAction.Down), en("awaaz kam karo"))
        assertEquals(vol(VolumeAction.Down), hi("आवाज़ थोड़ी कम कर दो"))
        assertEquals(vol(VolumeAction.Down), en("too loud"))
        assertEquals(vol(VolumeAction.Down), en("gaana dheere karo"))
    }

    @Test
    fun volumeLevelsFullAndMute() {
        assertEquals(vol(VolumeAction.Max), en("volume full"))
        assertEquals(vol(VolumeAction.Max), en("volume full karo"))
        assertEquals(vol(VolumeAction.Max), hi("आवाज़ पूरी कर दो"))
        assertEquals(vol(VolumeAction.Set, 5), en("volume 5"))
        assertEquals(vol(VolumeAction.Set, 10), en("volume ten"))
        assertEquals(vol(VolumeAction.Set, 8), en("set the volume to 8"))
        assertEquals(vol(VolumeAction.Set, 5), hi("आवाज़ पाँच कर दो"))
        assertEquals(vol(VolumeAction.Set, 7), hi("वॉल्यूम ७"))
        assertEquals(vol(VolumeAction.Set, 5), en("volume paanch kar do"))
        // "kar do" is not the number two.
        assertEquals(vol(VolumeAction.Up), en("volume up kar do"))
        assertEquals(vol(VolumeAction.Mute), en("mute"))
        assertEquals(vol(VolumeAction.Mute), hi("आवाज़ बंद करो"))
        assertEquals(vol(VolumeAction.Unmute), en("unmute"))
        assertEquals(vol(VolumeAction.Unmute), en("awaaz chalu karo"))
    }

    @Test
    fun volumeAloneShowsTheLevel() {
        assertEquals(vol(VolumeAction.Show), en("volume"))
        assertEquals(vol(VolumeAction.Show), hi("वॉल्यूम"))
        assertEquals(vol(VolumeAction.Show), en("awaaz"))
    }

    @Test
    fun moreAfterAVolumeChange() {
        assertEquals(VolumeAction.Up, VoiceIntents.volumeFollowUp("aur", VolumeAction.Up))
        assertEquals(VolumeAction.Down, VoiceIntents.volumeFollowUp("thoda aur", VolumeAction.Down))
        assertEquals(VolumeAction.Up, VoiceIntents.volumeFollowUp("और", VolumeAction.Up))
        assertEquals(VolumeAction.Up, VoiceIntents.volumeFollowUp("more", VolumeAction.Up))
        assertEquals(VolumeAction.Down, VoiceIntents.volumeFollowUp("aur kam", VolumeAction.Up))
        assertEquals(VolumeAction.Up, VoiceIntents.volumeFollowUp("aur tez", VolumeAction.Down))
        // "More" after "volume" alone has no direction yet.
        assertNull(VoiceIntents.volumeFollowUp("aur", VolumeAction.Show))
        assertNull(VoiceIntents.volumeFollowUp("navigate to rohtak", VolumeAction.Up))
        for (p in VoskCommands.FOLLOW_UPS + VoskCommands.HINDI_FOLLOW_UPS) {
            assertTrue(p, VoiceIntents.volumeFollowUp(p, VolumeAction.Up) != null)
        }
    }

    @Test
    fun songNamesWithVolumeWordsStaySongs() {
        assertEquals(VoiceIntent.PlayLocal("awaaz do"), en("play awaaz do"))
        assertEquals(VoiceIntent.PlayLocal("sound of silence"), en("play sound of silence"))
    }

    @Test
    fun stopPausesMusicOrCancels() {
        assertEquals(VoiceIntent.Stop, en("stop"))
        assertEquals(VoiceIntent.Stop, en("hey ampli stop"))
        assertEquals(VoiceIntent.Stop, en("stop please"))
        assertEquals(VoiceIntent.Stop, en("ruko"))
        assertEquals(VoiceIntent.Stop, en("band karo"))
        assertEquals(VoiceIntent.Stop, hi("रुको"))
        assertEquals(VoiceIntent.Stop, hi("बंद कर दो"))
        // Named things keep their own meaning.
        assertEquals(VoiceIntent.Pause, en("stop music"))
        assertEquals(VoiceIntent.Pause, en("stop the song"))
        assertEquals(VoiceIntent.Pause, en("gaana band karo"))
        assertEquals(VoiceIntent.StopNavigation, en("stop navigation"))
        assertEquals(VoiceIntent.StopNavigation, en("navigation band karo"))
        assertEquals(VoiceIntent.StopNavigation, hi("नेविगेशन बंद करो"))
        assertEquals(VoiceIntent.StopNavigation, hi("रास्ता बंद करो"))
        assertEquals(VoiceIntent.Cancel, en("cancel"))
        assertEquals(VoiceIntent.Cancel, en("never mind"))
        assertEquals(VoiceIntent.Cancel, en("rehne do"))
    }

    @Test
    fun musicKeys() {
        assertEquals(VoiceIntent.Resume, en("play"))
        assertEquals(VoiceIntent.Resume, en("play music"))
        assertEquals(VoiceIntent.Resume, en("gaana chalao"))
        assertEquals(VoiceIntent.Resume, hi("गाना बजाओ"))
        assertEquals(VoiceIntent.Resume, en("resume"))
        assertEquals(VoiceIntent.Pause, en("pause"))
        assertEquals(VoiceIntent.Pause, hi("गाना रोको"))
        assertEquals(VoiceIntent.Next, en("play next song"))
        assertEquals(VoiceIntent.Next, en("next song"))
        assertEquals(VoiceIntent.Next, en("next"))
        assertEquals(VoiceIntent.Next, en("agla gaana"))
        assertEquals(VoiceIntent.Next, hi("अगला गाना"))
        assertEquals(VoiceIntent.Next, en("gaana badlo"))
        assertEquals(VoiceIntent.Previous, en("previous"))
        assertEquals(VoiceIntent.Previous, en("play previous song"))
        assertEquals(VoiceIntent.Previous, en("pichla gaana"))
        assertEquals(VoiceIntent.Previous, hi("पिछला गाना"))
        // The recogniser often hears "previous" as "preview".
        assertEquals(VoiceIntent.Previous, en("preview"))
        assertEquals(VoiceIntent.Previous, en("play preview"))
        assertEquals(VoiceIntent.Previous, en("preview song"))
    }

    @Test
    fun playASongByName() {
        assertEquals(VoiceIntent.PlayLocal("kesariya"), en("play kesariya"))
        assertEquals(VoiceIntent.PlayLocal("kesariya"), en("kesariya bajao"))
        assertEquals(VoiceIntent.PlayLocal("kesariya"), en("kesariya chalao"))
        assertEquals(VoiceIntent.PlayLocal("kesariya"), en("kesariya lagao"))
        assertEquals(VoiceIntent.PlayLocal("केसरिया"), hi("केसरिया गाना लगाओ"))
        assertEquals(VoiceIntent.PlayLocal("tum hi ho"), en("play the song tum hi ho"))
        assertTrue(en("play music from my phone") is VoiceIntent.PlayBluetooth)
    }

    @Test
    fun timeAndDate() {
        assertEquals(VoiceIntent.TimeNow, en("what is the time"))
        assertEquals(VoiceIntent.TimeNow, en("what's the time right now"))
        assertEquals(VoiceIntent.TimeNow, en("what time is it"))
        assertEquals(VoiceIntent.TimeNow, en("kitne baje hain"))
        assertEquals(VoiceIntent.TimeNow, en("time kya hua"))
        assertEquals(VoiceIntent.TimeNow, hi("कितने बजे हैं"))
        assertEquals(VoiceIntent.TimeNow, hi("टाइम क्या हुआ"))
        assertEquals(VoiceIntent.TimeNow, hi("कै बजे सैं"))
        assertEquals(VoiceIntent.DateToday, en("aaj kya date hai"))
        assertEquals(VoiceIntent.DateToday, en("what's the date today"))
        assertEquals(VoiceIntent.DateToday, hi("आज क्या तारीख है"))
        assertEquals(VoiceIntent.DateToday, hi("आज कौन सा दिन है"))
    }

    @Test
    fun weather() {
        assertEquals(VoiceIntent.Weather(rain = false), en("how is the weather"))
        assertEquals(VoiceIntent.Weather(rain = false), en("mausam kaisa hai"))
        assertEquals(VoiceIntent.Weather(rain = false), hi("मौसम कैसा है"))
        assertEquals(VoiceIntent.Weather(rain = false), en("what's the temperature outside"))
        assertEquals(VoiceIntent.Weather(rain = true), en("baarish hogi kya"))
        assertEquals(VoiceIntent.Weather(rain = true), en("will it rain today"))
        assertEquals(VoiceIntent.Weather(rain = true), hi("बारिश होगी क्या"))
        // Rain songs are still songs; "how is the engine" is still the engine.
        assertTrue(en("barish ke gaane bajao") is VoiceIntent.PlayLocal)
        assertEquals(VoiceIntent.EngineStatus, en("engine temperature"))
    }

    @Test
    fun speedMapsScreenAndHelp() {
        assertEquals(VoiceIntent.Speed, en("what's my speed"))
        assertEquals(VoiceIntent.Speed, en("speed kitni hai"))
        assertEquals(VoiceIntent.Speed, hi("स्पीड कितनी है"))
        assertEquals(VoiceIntent.NextCamera, en("next speed camera"))
        assertEquals(VoiceIntent.OpenMaps, en("open maps"))
        assertEquals(VoiceIntent.OpenMaps, en("map kholo"))
        assertEquals(VoiceIntent.OpenMaps, hi("नक्शा खोलो"))
        assertEquals(VoiceIntent.Brightness(true), en("brightness up"))
        assertEquals(VoiceIntent.Brightness(true), en("make the screen brighter"))
        assertEquals(VoiceIntent.Brightness(false), en("brightness kam karo"))
        assertEquals(VoiceIntent.Brightness(false), hi("ब्राइटनेस कम करो"))
        assertEquals(VoiceIntent.Brightness(null), en("brightness"))
        assertEquals(VoiceIntent.BluetoothSettings, en("bluetooth settings"))
        assertEquals(VoiceIntent.BluetoothSettings, en("bluetooth kholo"))
        assertEquals(VoiceIntent.WhatCanISay, en("what can i say"))
        assertEquals(VoiceIntent.WhatCanISay, en("kya kya bol sakte hain"))
        assertEquals(VoiceIntent.WhatCanISay, hi("क्या क्या बोल सकते हैं"))
        // Still what they were.
        assertEquals(VoiceIntent.OpenAndroidAuto, en("connect my phone"))
        assertEquals(VoiceIntent.Sos, en("help me"))
        assertTrue(en("navigate to sound city mall") is VoiceIntent.Navigate)
        assertEquals(VoiceIntent.Navigate("weather station"), en("take me to the weather station"))
    }

    @Test
    fun spokenNumbers() {
        assertEquals(5, VoiceIntents.digitsIn("volume 5"))
        assertEquals(7, VoiceIntents.digitsIn("वॉल्यूम ७"))
        assertNull(VoiceIntents.digitsIn("volume 500"))
        assertEquals(15, VoiceIntents.numberWordIn("volume fifteen"))
        assertEquals(4, VoiceIntents.numberWordIn("आवाज़ चार"))
        assertNull(VoiceIntents.numberWordIn("volume kar do"))
    }
}
