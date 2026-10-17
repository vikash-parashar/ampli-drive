package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaperVoicePolicyTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val papers = listOf(
        Paper(PaperKind.Puc, today.plusDays(5)),
        Paper(PaperKind.Insurance, today.plusDays(2)),
        Paper(PaperKind.Rc, today.plusYears(3)),
        Paper(PaperKind.Licence, today.minusDays(1)),
        Paper(PaperKind.FastagKyc, null),
    )

    @Test
    fun mostUrgentFirstAtMostTwo() {
        val r = PaperVoicePolicy.pick(papers, today, 7, emptyMap())
        assertEquals(listOf("licence", "insurance"), r.map { it.key })
        assertTrue(r[0].expired)
    }

    @Test
    fun onceADayAndExpiredEveryFewDays() {
        val said = mapOf("licence" to today.toEpochDay() - 1, "insurance" to today.toEpochDay())
        assertEquals(listOf("puc"), PaperVoicePolicy.pick(papers, today, 7, said).map { it.key })
        val later = mapOf("licence" to today.toEpochDay() - 3, "insurance" to today.toEpochDay(), "puc" to today.toEpochDay())
        assertEquals(listOf("licence"), PaperVoicePolicy.pick(papers, today, 7, later).map { it.key })
    }

    @Test
    fun windowAndPass() {
        assertEquals(listOf("licence"), PaperVoicePolicy.pick(papers, today, 1, emptyMap()).map { it.key })
        val pass = PaperVoicePolicy.pick(emptyList(), today, 7, emptyMap(), passTripsLeft = 6, passDaysLeft = 200)
        assertEquals(6, pass.single().tripsLeft)
        assertTrue(PaperVoicePolicy.pick(emptyList(), today, 7, emptyMap(), passTripsLeft = 60, passDaysLeft = 200).isEmpty())
        assertEquals(PaperVoicePolicy.PASS_KEY, PaperVoicePolicy.pick(emptyList(), today, 7, emptyMap(), passTripsLeft = 60, passDaysLeft = 4).single().key)
    }
}
