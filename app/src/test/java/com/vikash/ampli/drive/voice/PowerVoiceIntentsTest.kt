package com.vikash.ampli.drive.voice

import com.vikash.ampli.drive.system.PowerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ampli, screen band karo", no Restart / Power off, and what must stay something else. */
class PowerVoiceIntentsTest {

    private fun power(text: String, lang: String = "hi"): PowerAction? = (VoiceIntents.parse(text, lang) as? VoiceIntent.Power)?.action

    @Test
    fun screenOff() {
        for (p in listOf(
            "screen band karo", "Ampli, screen band karo", "screen off", "turn off the screen", "screen off karo",
            "display band karo", "स्क्रीन बंद करो", "स्क्रीन ऑफ करो", "hey ampli screen band kar do",
        )) {
            assertEquals(p, PowerAction.ScreenOff, power(p))
            assertEquals(p, PowerAction.ScreenOff, power(p, "en"))
        }
    }

    @Test
    fun restartAndPowerOffAreGone() {
        // Removed on purpose: these do nothing, and never turn into Screen off, mute or stop.
        for (p in listOf(
            "restart karo", "Ampli, restart karo", "restart", "reboot", "रीस्टार्ट करो", "रिस्टार्ट कर दो",
            "power off karo", "Ampli, power off karo", "power off", "shut down", "gaadi ki screen band karo",
            "पावर ऑफ करो", "गाड़ी की स्क्रीन बंद करो",
        )) {
            for (lang in listOf("hi", "en")) {
                val intent = VoiceIntents.parse(p, lang)
                assertTrue("$p ($lang) -> $intent", intent is VoiceIntent.Unknown)
            }
        }
    }

    @Test
    fun otherCommandsStayTheirOwn() {
        assertTrue(VoiceIntents.parse("band karo", "hi").let { it == VoiceIntent.Stop || it == VoiceIntent.Cancel })
        assertTrue(VoiceIntents.parse("gaana band karo", "hi") is VoiceIntent.Pause)
        assertEquals(VoiceIntent.StopNavigation, VoiceIntents.parse("navigation band karo", "hi"))
        assertTrue(VoiceIntents.parse("awaaz band", "hi") is VoiceIntent.Volume)
        assertTrue(VoiceIntents.parse("volume off", "en") is VoiceIntent.Volume)
        assertFalse(VoiceIntents.parse("restart the song", "en") is VoiceIntent.Power)
        assertFalse(VoiceIntents.parse("gaana restart karo", "hi") is VoiceIntent.Power)
        assertFalse(VoiceIntents.parse("restart navigation", "en") is VoiceIntent.Power)
        assertTrue(VoiceIntents.parse("gaadi kahan hai", "hi") is VoiceIntent.WhereParked)
    }

    @Test
    fun raddKaroCancels() {
        assertEquals(VoiceIntent.Cancel, VoiceIntents.parse("radd karo", "hi"))
        assertEquals(VoiceIntent.Cancel, VoiceIntents.parse("radd karo", "en"))
        assertEquals(VoiceIntent.Cancel, VoiceIntents.parse("रद्द करो", "hi"))
    }

    @Test
    fun grammarsHoldScreenOffButNotRestartOrPowerOff() {
        val en = VoskCommands.phrases(hindi = false)
        for (p in listOf("screen band karo", "screen off", "radd karo")) assertTrue(p, p in en)
        for (p in listOf("restart karo", "power off karo", "restart", "reboot", "power off", "shut down")) assertFalse(p, p in en)
        val hi = VoskCommands.phrases(hindi = true)
        assertTrue("स्क्रीन बंद करो" in hi)
        for (p in listOf("रीस्टार्ट करो", "पावर ऑफ करो")) assertFalse(p, p in hi)
    }

    @Test
    fun catalogShowsScreenOffOnly() {
        val all = VoiceCommandCatalog.GROUPS.flatMap { g -> g.items.flatMap { it.examples } }
        val actions = all.mapNotNull { (VoiceIntents.parse(it, "hi") as? VoiceIntent.Power)?.action }.toSet()
        assertEquals(setOf(PowerAction.ScreenOff), actions)
        assertFalse(all.any { it.contains("restart", ignoreCase = true) || it.contains("power off", ignoreCase = true) })
    }
}
