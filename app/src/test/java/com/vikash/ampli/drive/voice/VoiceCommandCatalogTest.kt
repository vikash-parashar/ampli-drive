package com.vikash.ampli.drive.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Voice › What can I say?: every example on that screen must really work. */
class VoiceCommandCatalogTest {

    @Test
    fun everyExampleParses() {
        for (group in VoiceCommandCatalog.GROUPS) {
            for (item in group.items) {
                assertTrue(item.examples.isNotEmpty())
                for (e in item.examples) {
                    if (item.followUp) {
                        assertTrue(e, VoiceIntents.volumeFollowUp(e, VoiceIntent.VolumeAction.Up) != null)
                    } else {
                        assertFalse(e, VoiceIntents.parse(e, "hi") is VoiceIntent.Unknown)
                        assertFalse(e, VoiceIntents.parse(e, "en") is VoiceIntent.Unknown)
                    }
                }
            }
        }
    }

    @Test
    fun coversTheOwnersAsk() {
        val all = VoiceCommandCatalog.GROUPS.flatMap { g -> g.items.flatMap { it.examples } }
        val intents = all.map { VoiceIntents.parse(it, "hi") }
        assertTrue(intents.any { it is VoiceIntent.Volume })
        assertTrue(intents.any { it == VoiceIntent.Stop })
        assertTrue(intents.any { it == VoiceIntent.Next })
        assertTrue(intents.any { it == VoiceIntent.Previous })
        assertTrue(intents.any { it is VoiceIntent.PlayLocal })
        assertTrue(intents.any { it == VoiceIntent.TimeNow })
        assertTrue(intents.any { it is VoiceIntent.Weather })
    }
}
