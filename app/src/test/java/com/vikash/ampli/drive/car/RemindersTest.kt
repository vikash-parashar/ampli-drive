package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RemindersTest {
    private val today = LocalDate.of(2026, 10, 14)

    @Test
    fun paperLevels() {
        assertEquals(DueLevel.Unknown, Reminders.paper(Paper(PaperKind.Puc, null), today).level)
        assertEquals(DueLevel.Ok, Reminders.paper(Paper(PaperKind.Puc, today.plusDays(16)), today).level)
        assertEquals(DueLevel.Soon, Reminders.paper(Paper(PaperKind.Puc, today.plusDays(15)), today).level)
        assertEquals(DueLevel.Soon, Reminders.paper(Paper(PaperKind.Insurance, today), today).level)
        val overdue = Reminders.paper(Paper(PaperKind.Insurance, today.minusDays(3)), today)
        assertEquals(DueLevel.Overdue, overdue.level)
        assertEquals(-3L, overdue.daysLeft)
    }

    @Test
    fun serviceDueByKm() {
        val oil = ServiceItem("oil", "Engine oil", everyKm = 10_000, everyMonths = 12, lastKm = 20_000.0, lastDate = today.minusMonths(2))
        assertEquals(DueLevel.Ok, Reminders.service(oil, today, 29_000.0).level)
        val soon = Reminders.service(oil, today, 29_600.0)
        assertEquals(DueLevel.Soon, soon.level)
        assertEquals(400.0, soon.kmLeft!!, 1e-6)
        assertEquals(DueLevel.Overdue, Reminders.service(oil, today, 30_001.0).level)
    }

    @Test
    fun serviceDueByMonthsWinsWhenEarlier() {
        val oil = ServiceItem("oil", "Engine oil", everyKm = 10_000, everyMonths = 6, lastKm = 20_000.0, lastDate = today.minusMonths(6).plusDays(10))
        val s = Reminders.service(oil, today, 21_000.0)
        assertEquals(DueLevel.Soon, s.level)
        assertEquals(10L, s.daysLeft)
    }

    @Test
    fun unscheduledServiceIsUnknown() {
        val tyre = ServiceItem("tyre", "Tyre rotation", everyKm = 10_000, everyMonths = null, lastKm = null, lastDate = null)
        assertEquals(DueLevel.Unknown, Reminders.service(tyre, today, 5_000.0).level)
        assertNull(Reminders.nextService(listOf(tyre), today, 5_000.0))
    }

    @Test
    fun nextServiceIsTheNearestByKm() {
        val oil = ServiceItem("oil", "Engine oil", 10_000, null, 20_000.0, null)
        val tyre = ServiceItem("tyre", "Tyre rotation", 5_000, null, 24_000.0, null)
        val next = Reminders.nextService(listOf(oil, tyre), today, 27_000.0)!!
        assertEquals("tyre", next.first.id)
        assertEquals(2_000.0, next.second.kmLeft!!, 1e-6)
    }

    @Test
    fun dueItemsListsOnlySoonAndOverdue() {
        val papers = listOf(Paper(PaperKind.Puc, today.plusDays(5)), Paper(PaperKind.Insurance, today.plusDays(90)), Paper(PaperKind.Rc, today.minusDays(1)))
        val due = Reminders.dueItems(papers, emptyList(), today, 0.0) { it.name }
        assertEquals(listOf("Rc", "Puc"), due.map { it.title })
    }

    @Test
    fun odometerSkipsNoiseAndJumps() {
        val o = OdometerAccumulator()
        val lat = 18.5
        val dLat = 100.0 / 111_195.0 // 100 m north
        assertEquals(0.0, o.onFix(lat, 73.8, 5f, 36f, 0L), 1e-9)
        assertEquals(100.0, o.onFix(lat + dLat, 73.8, 5f, 36f, 10_000L), 1.0)
        // Poor accuracy: ignored.
        assertEquals(0.0, o.onFix(lat + 2 * dLat, 73.8, 80f, 36f, 20_000L), 1e-9)
        // Parked, position wandering: ignored.
        assertEquals(0.0, o.onFix(lat + dLat + 0.00005, 73.8, 5f, 0f, 30_000L), 1e-9)
        // Teleport (10 km in 10 s): ignored and re-anchored.
        assertEquals(0.0, o.onFix(lat + 100 * dLat, 73.8, 5f, 36f, 40_000L), 1e-9)
        assertEquals(100.0, o.onFix(lat + 101 * dLat, 73.8, 5f, 36f, 50_000L), 1.0)
    }
}
