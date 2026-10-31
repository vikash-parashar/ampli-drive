package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ServiceVoicePolicyTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val oil = ServiceItem("oil", "Engine oil", everyKm = 10_000, everyMonths = 12, lastKm = 38_290.0, lastDate = LocalDate.of(2026, 3, 1))
    private val coolant = ServiceItem("coolant", "Coolant", everyKm = null, everyMonths = 24, lastKm = null, lastDate = LocalDate.of(2024, 11, 10))
    private val tyres = ServiceItem("tyres", "Tyre rotation", everyKm = 10_000, everyMonths = null, lastKm = 30_000.0, lastDate = null)
    private val unknown = ServiceItem("air", "Air filter", 20_000, 24, null, null)

    @Test
    fun dueByKmSaysTheKilometresLeft() {
        val r = ServiceVoicePolicy.pick(listOf(oil, unknown), today, odometerKm = 47_870.0, lastSaid = emptyMap()).single()
        assertEquals("oil", r.id)
        assertEquals(420, r.kmLeft) // 48,290 - 47,870
        assertNull(r.daysLeft)
        assertTrue(!r.overdue)
    }

    @Test
    fun dueByDateSaysTheDays() {
        val r = ServiceVoicePolicy.pick(listOf(coolant), today, 10_000.0, emptyMap()).single()
        assertEquals(10L, r.daysLeft)
        assertNull(r.kmLeft)
    }

    @Test
    fun mostUrgentFirstAndOnlyOnePerStart() {
        // Tyres overdue by 1,000 km beats coolant in 10 days.
        val list = ServiceVoicePolicy.pick(listOf(coolant, tyres), today, 41_000.0, emptyMap())
        assertEquals(listOf("tyres"), list.map { it.id })
        assertTrue(list.single().overdue)
        assertEquals(-1000, list.single().kmLeft)
    }

    @Test
    fun notTwiceADayAndOverdueEveryFewDays() {
        val day = today.toEpochDay()
        assertTrue(ServiceVoicePolicy.pick(listOf(oil), today, 47_870.0, mapOf("oil" to day)).isEmpty())
        assertEquals(1, ServiceVoicePolicy.pick(listOf(oil), today, 47_870.0, mapOf("oil" to day - 1)).size)
        assertTrue(ServiceVoicePolicy.pick(listOf(tyres), today, 41_000.0, mapOf("tyres" to day - 2)).isEmpty())
        assertEquals(1, ServiceVoicePolicy.pick(listOf(tyres), today, 41_000.0, mapOf("tyres" to day - 3)).size)
    }

    @Test
    fun farOffOrUnscheduledStaysQuiet() {
        assertTrue(ServiceVoicePolicy.pick(listOf(oil, unknown), today, 40_000.0, emptyMap()).isEmpty())
        assertEquals(440, ServiceVoicePolicy.roundKm(437.0))
        assertEquals(-10, ServiceVoicePolicy.roundKm(-12.0))
    }
}
