package com.vikash.ampli.drive.bluetooth

import com.vikash.ampli.drive.bluetooth.PhoneBtRules.AutoPlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBtRulesTest {

    private fun decide(
        enabled: Boolean = true,
        phone: Boolean = true,
        inCall: Boolean = false,
        musicActive: Boolean = false,
        pausedAt: Long = 0L,
        now: Long = 1_000_000L,
    ) = PhoneBtRules.autoPlay(enabled, phone, inCall, musicActive, pausedAt, now)

    @Test
    fun playsWhenAPhoneConnects() {
        assertEquals(AutoPlay.Play, decide())
    }

    @Test
    fun doesNotFightTheDriver() {
        assertEquals(AutoPlay.Off, decide(enabled = false))
        assertEquals(AutoPlay.NotPhone, decide(phone = false))
        assertEquals(AutoPlay.InCall, decide(inCall = true))
        assertEquals(AutoPlay.UserPaused, decide(pausedAt = 1_000_000L - 30_000L))
        assertEquals(AutoPlay.AlreadyPlaying, decide(musicActive = true))
    }

    @Test
    fun aPauseOverAMinuteAgoNoLongerBlocks() {
        assertEquals(AutoPlay.Play, decide(pausedAt = 1_000_000L - PhoneBtRules.PAUSE_RESPECT_MS))
        assertEquals(AutoPlay.Play, decide(pausedAt = 0L))
    }

    @Test
    fun phoneClass() {
        assertTrue(PhoneBtRules.isPhoneClass(PhoneBtRules.MAJOR_PHONE))
        assertTrue(PhoneBtRules.isPhoneClass(null))
        assertTrue(PhoneBtRules.isPhoneClass(PhoneBtRules.MAJOR_UNCATEGORIZED))
        assertFalse(PhoneBtRules.isPhoneClass(0x0400)) // audio/video (a speaker or the OBD dongle's class)
        assertFalse(PhoneBtRules.isPhoneClass(0x0100)) // computer
    }

    @Test
    fun findsTheUnitsBluetoothApp() {
        assertEquals(100, PhoneBtRules.vendorBtScore("Bluetooth", "com.ts.bt"))
        assertEquals(100, PhoneBtRules.vendorBtScore("蓝牙", "com.example.car"))
        assertTrue(PhoneBtRules.vendorBtScore("BT Music", "com.syu.bt") >= PhoneBtRules.VENDOR_GOOD_ENOUGH)
        assertTrue(PhoneBtRules.vendorBtScore("Car phone", "com.goodocom.gocsdk.btphone") >= PhoneBtRules.VENDOR_GOOD_ENOUGH)
        assertEquals(0, PhoneBtRules.vendorBtScore("Bluetooth", "com.android.bluetooth"))
        assertEquals(0, PhoneBtRules.vendorBtScore("Settings", "com.android.settings"))
        assertEquals(0, PhoneBtRules.vendorBtScore("phonelink", "com.ts.phonelink"))
        assertEquals(0, PhoneBtRules.vendorBtScore("Music", "com.android.music"))
        assertEquals(0, PhoneBtRules.vendorBtScore("Subtitles", "com.example.subtitle"))
    }

    @Test
    fun picksItsMusicScreen() {
        assertEquals(
            "com.ts.bt.MusicActivity",
            PhoneBtRules.musicActivity(listOf("com.ts.bt.MainActivity", "com.ts.bt.MusicActivity", "com.ts.bt.DialActivity")),
        )
        assertEquals("com.x.bt.A2dpPlayer", PhoneBtRules.musicActivity(listOf("com.x.bt.Main", "com.x.bt.A2dpPlayer")))
        assertNull(PhoneBtRules.musicActivity(listOf("com.x.bt.Main", "com.x.bt.Dial")))
    }
}
