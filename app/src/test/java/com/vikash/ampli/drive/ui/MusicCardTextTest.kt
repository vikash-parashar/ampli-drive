package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's music card: what it says, and the record's spin (MusicCardText, DiscSpin). */
class MusicCardTextTest {

    private fun labels(bluetooth: Boolean, title: String?, artist: String? = null, app: String? = "Bluetooth", phone: String? = "OnePlus Nord CE3", ampli: Boolean = false) =
        MusicCardText.labels(bluetooth, ampli, title, artist, app, phone, phoneAudio = "Phone se gaana", unknownTrack = "Unknown track")

    private fun count(haystack: List<String?>, needle: String) = haystack.sumOf { s -> s?.split(needle)?.size?.minus(1) ?: 0 }

    @Test
    fun bluetoothWithoutTrackInfoSaysPhoneSeGaanaAndBluetoothOnce() {
        val l = labels(bluetooth = true, title = null)
        assertEquals("Phone se gaana", l.title)
        assertEquals("OnePlus Nord CE3", l.artist)
        assertEquals("Bluetooth", l.source)
        assertTrue(l.noTrackInfo)
        assertEquals(1, count(listOf(l.title, l.artist, l.source), "Bluetooth"))
        // A blank title is no title.
        assertEquals("Phone se gaana", labels(bluetooth = true, title = "  ").title)
    }

    @Test
    fun bluetoothWithTrackInfo() {
        val l = labels(bluetooth = true, title = "Kesariya", artist = "Arijit Singh · Brahmastra")
        assertEquals("Kesariya", l.title)
        assertEquals("Arijit Singh · Brahmastra", l.artist)
        assertEquals("Bluetooth · OnePlus Nord CE3", l.source)
        assertFalse(l.noTrackInfo)
        // A phone that sends "Bluetooth" as the artist doesn't make it show twice.
        val dup = labels(bluetooth = true, title = "Kesariya", artist = "Bluetooth")
        assertNull(dup.artist)
        assertEquals(1, count(listOf(dup.title, dup.artist, dup.source), "Bluetooth"))
    }

    @Test
    fun otherSources() {
        assertEquals("Ampli MP3", labels(bluetooth = false, title = "Song", app = "Ampli MP3 Player", ampli = true).source)
        val noTitle = labels(bluetooth = false, title = null, app = "Gaana")
        assertEquals("Unknown track", noTitle.title)
        assertEquals("Gaana", noTitle.source)
    }

    @Test
    fun discSpinsAt20RpmAndEasesToAStopInAboutASecond() {
        val d = DiscSpin()
        repeat(120) { d.step(1f / 60f, playing = true, reduced = false) }
        assertEquals(120f, d.speed, 1f) // 20 rpm = 120° a second
        var t = 0f
        while (d.speed > 0f && t < 3f) {
            d.step(1f / 60f, playing = false, reduced = false)
            t += 1f / 60f
        }
        assertEquals(0f, d.speed, 0f)
        assertTrue("stopped after $t s", t in 0.8f..2.2f)
        assertTrue(d.resting(playing = false, reduced = false))
    }

    @Test
    fun discNeverTurnsWithReducedMotion() {
        val d = DiscSpin()
        repeat(60) { d.step(1f / 60f, playing = true, reduced = true) }
        assertEquals(0f, d.angle, 0f)
        assertEquals(0f, d.speed, 0f)
        assertTrue(d.resting(playing = true, reduced = true))
    }
}
