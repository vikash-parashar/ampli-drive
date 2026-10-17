package com.vikash.ampli.drive.fastag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnnualPassTest {
    private val zone = ZoneOffset.UTC
    private val start = LocalDate.of(2026, 9, 1)
    private fun ms(d: LocalDate, hour: Int = 10) = d.atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun trip(d: LocalDate, left: Int? = null, hour: Int = 10) =
        TollEvent(ms(d, hour), FastagKind.Debit, null, "Kherki Daula", 500.0, null, null, passTrip = true, passTripsLeft = left)

    @Test
    fun parsesPassTripWithTripsLeft() {
        val m = FastagParser.parse(
            "Dear Customer, Annual Pass trip availed at Kherki Daula Toll Plaza on 12-10-2026 for HR26AB1234. Remaining trips: 185. -IHMCL",
            "VM-NHAIFT",
        )
        assertNotNull(m)
        assertTrue(m!!.passTrip)
        assertEquals(185, m.passTripsLeft)
        assertEquals(FastagKind.Debit, m.kind)
        assertNull(m.amount)
    }

    @Test
    fun parsesTripsLeftWordings() {
        for ((text, want) in listOf(
            "FASTag Annual Pass used at Panipat toll plaza. 12 trips left." to 12,
            "Annual Pass: trips remaining 7 for your FASTag" to 7,
            "FASTag वार्षिक पास: शेष ट्रिप 150" to 150,
        )) {
            assertEquals(text, want, FastagParser.parse(text)?.passTripsLeft)
        }
    }

    @Test
    fun activationIsNotATrip() {
        assertNull(FastagParser.parse("Your FASTag Annual Pass has been activated. Valid for 1 year."))
    }

    @Test
    fun paidTollStaysAPaidToll() {
        val m = FastagParser.parse("Rs 145 debited from your FASTag at Khed Shivapur toll plaza. Avl Bal Rs 855")!!
        assertFalse(m.passTrip)
        assertEquals(145.0, m.amount!!, 0.0)
    }

    @Test
    fun countsTripsSinceStartWithManualCorrections() {
        val events = listOf(trip(start.minusDays(3)), trip(start.plusDays(1)), trip(start.plusDays(2)), trip(start.plusDays(5)))
        val pass = AnnualPass(start, listOf(PassAdjustment(ms(start.plusDays(6)), +1), PassAdjustment(ms(start.plusDays(7)), -1), PassAdjustment(ms(start.plusDays(8)), +1)))
        val s = AnnualPassMath.status(pass, events, start.plusDays(10), zone)!!
        assertEquals(200 - 3 - 1, s.remaining)
        assertFalse(s.fromSms)
        assertEquals(LocalDate.of(2027, 8, 31), s.expiry)
        assertFalse(s.expired)
    }

    @Test
    fun reportedTripsLeftWinsThenLaterTripsCount() {
        val events = listOf(trip(start.plusDays(1)), trip(start.plusDays(2), left = 150), trip(start.plusDays(3)))
        val pass = AnnualPass(start, listOf(PassAdjustment(ms(start.plusDays(1), 11), +5), PassAdjustment(ms(start.plusDays(4)), +1)))
        val s = AnnualPassMath.status(pass, events, start.plusDays(5), zone)!!
        // 150 from the SMS, minus one crossing and one tap after it; the earlier +5 is already in the SMS figure.
        assertEquals(148, s.remaining)
        assertTrue(s.fromSms)
    }

    @Test
    fun lowAndExpired() {
        val low = AnnualPassMath.status(AnnualPass(start, emptyList()), listOf(trip(start, left = 4)), start.plusDays(1), zone)!!
        assertTrue(low.low)
        val old = AnnualPassMath.status(AnnualPass(start, emptyList()), emptyList(), start.plusYears(1), zone)!!
        assertTrue(old.expired)
        assertNull(AnnualPassMath.status(AnnualPass(null, emptyList()), emptyList(), start, zone))
    }

    @Test
    fun passTripsAreNotPaidTolls() {
        val t = FastagStore.totals(listOf(trip(start)), ms(start, 11), zone)
        assertEquals(0, t.tripCount)
    }
}
