package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.weather.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerClipsTest {
    private val ids = VoiceClips.expectedHashes.keys

    private fun assertRecordable(clips: List<String>) {
        assertTrue(clips.isNotEmpty())
        for (c in clips) assertTrue("$c is not in the voice-pack manifest", c in ids)
    }

    @Test
    fun timeIsStitchedFromRecordedPieces() {
        assertEquals(listOf("t_now", "t_evening", "num_5", "t_baj_kar", "num_20", "t_minutes"), AnswerClips.time(17, 20, haryanvi = false))
        assertEquals(listOf("t_now", "t_night", "num_12", "t_oclock_hr"), AnswerClips.time(0, 0, haryanvi = true))
        assertEquals(listOf("t_now", "t_afternoon", "num_12", "t_baj_kar", "num_59", "t_minutes"), AnswerClips.time(12, 59, haryanvi = false))
        assertEquals("t_morning", AnswerClips.time(9, 5, false)[1])
        for (h in 0..23) for (m in 0..59) assertRecordable(AnswerClips.time(h, m, h % 2 == 0))
    }

    @Test
    fun dateAndSpeed() {
        assertEquals(listOf("d_today", "wd_4", "num_30", "mon_10", "d_is"), AnswerClips.date(4, 30, 10, haryanvi = false))
        for (d in 1..31) assertRecordable(AnswerClips.date(1 + d % 7, d, 1 + d % 12, d % 2 == 0))
        assertRecordable(AnswerClips.speed(54, haryanvi = true))
        assertTrue(AnswerClips.speed(72, haryanvi = false).isEmpty())
        assertTrue(AnswerClips.speed(0, haryanvi = false).isEmpty())
    }

    @Test
    fun weatherSaysSkyTemperatureAndRain() {
        val w = AnswerClips.weather(Sky.PartlyCloudy, 31.4, rainLikely = false, rainQuestion = false, haryanvi = false)
        assertEquals(listOf("wx_partly", "wx_temp", "num_31", "wx_degrees", "wx_rain_no"), w)
        val rain = AnswerClips.weather(Sky.Rain, 24.0, rainLikely = true, rainQuestion = true, haryanvi = true)
        assertEquals("wx_rain_yes_hr", rain.first())
        // No temperature outside 1..59 °C.
        assertFalse(AnswerClips.weather(Sky.Snow, -3.0, false, false, false).contains("wx_temp"))
        for (sky in Sky.entries) for (hr in listOf(true, false)) assertRecordable(AnswerClips.weather(sky, 20.0, true, false, hr))
        for (id in listOf("wx_offline", "wx_failed", "no_location", "vol_up", "vol_down", "vol_max", "vol_mute", "vol_unmute", "nav_stopped")) {
            assertRecordable(AnswerClips.line(id, true))
            assertRecordable(AnswerClips.line(id, false))
        }
    }

    @Test
    fun everyPieceIsInTheManifestOnce() {
        val manifest = VoiceClips.manifest.map { it.id }
        assertEquals(manifest.size, manifest.toSet().size)
        for (id in AnswerClips.ALL.keys) assertTrue(id, id in manifest)
        assertEquals(59, (1..59).count { "num_$it" in manifest })
        assertEquals("उनसठ", VoiceClips.manifest.first { it.id == "num_59" }.text)
        // Every Haryanvi piece has its plain twin.
        for (id in AnswerClips.HARYANVI.keys) assertTrue(id, id in AnswerClips.PLAIN)
        // The crash countdown still only counts the last ten seconds.
        assertEquals(null, VoiceClips.countdownClip(11))
    }
}
