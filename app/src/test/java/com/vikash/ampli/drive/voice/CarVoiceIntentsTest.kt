package com.vikash.ampli.drive.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarVoiceIntentsTest {
    @Test
    fun carQuestions() {
        assertEquals(VoiceIntent.ServiceDue, VoiceIntents.parse("how many km to next service"))
        assertEquals(VoiceIntent.ServiceDue, VoiceIntents.parse("अगली सर्विस कब है", "hi"))
        assertEquals(VoiceIntent.TyrePressure, VoiceIntents.parse("what's the tyre pressure"))
        assertEquals(VoiceIntent.EngineStatus, VoiceIntents.parse("engine status"))
        assertEquals(VoiceIntent.EngineStatus, VoiceIntents.parse("इंजन कैसा है", "hi"))
        assertEquals(VoiceIntent.WhereParked, VoiceIntents.parse("where did I park"))
        assertEquals(VoiceIntent.WhereParked, VoiceIntents.parse("find my car"))
        assertEquals(VoiceIntent.WhereParked, VoiceIntents.parse("meri gaadi kahan hai", "hi"))
        assertEquals(VoiceIntent.WhereParked, VoiceIntents.parse("गाड़ी कहाँ खड़ी है", "hi"))
    }

    @Test
    fun placesStillNavigate() {
        assertTrue(VoiceIntents.parse("navigate to MRF tyres") is VoiceIntent.Navigate)
        assertTrue(VoiceIntents.parse("take me to the service centre") is VoiceIntent.Navigate)
    }

    @Test
    fun reportAProblem() {
        assertEquals(VoiceIntent.ReportProblem, VoiceIntents.parse("hey ampli report a problem"))
        assertEquals(VoiceIntent.ReportProblem, VoiceIntents.parse("send feedback"))
        assertEquals(VoiceIntent.ReportProblem, VoiceIntents.parse("मुझे शिकायत करनी है", "hi"))
        // Still an engine question, not a report.
        assertEquals(VoiceIntent.EngineStatus, VoiceIntents.parse("engine problem"))
    }
}
