package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class HaryanviNavIntentsTest {

    private fun dest(text: String, lang: String = "en") = (VoiceIntents.parse(text, lang) as? VoiceIntent.Navigate)?.destination

    @Test
    fun haryanviAndHindiNavigation() {
        assertEquals("rohtak", dest("Hey Ampli, Rohtak le chal"))
        assertEquals("sonipat", dest("hey ampli sonipat chalo"))
        assertEquals("panipat", dest("Panipat jaana sai", "hi"))
        assertEquals("रोहतक", dest("रोहतक ले चल", "hi"))
        assertEquals("हिसार", dest("हिसार चालणा सै", "hi"))
        assertEquals("india gate", dest("take me to India Gate"))
    }
}
