package com.vikash.ampli.drive.fastag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Sample SMS in the formats Indian banks and wallets send. Run with ./gradlew testDebugUnitTest. */
class FastagParserTest {

    private fun debit(text: String, sender: String? = null): FastagMessage {
        val m = FastagParser.parse(text, sender)
        requireNotNull(m) { "Not parsed: $text" }
        assertEquals(FastagKind.Debit, m.kind)
        return m
    }

    @Test
    fun icici_debitWithPlazaAndBalance() {
        val m = debit(
            "Rs.95.00 debited from your FASTag linked to MH12AB1234 at KHED SHIVAPUR toll plaza on 12-03-2024 10:32. Avl Bal Rs.1,240.50 - ICICI Bank",
        )
        assertEquals(95.0, m.amount!!, 0.001)
        assertEquals("Khed Shivapur", m.plaza)
        assertEquals(1240.50, m.balance!!, 0.001)
        assertEquals("MH12AB1234", m.vehicle)
        assertEquals("ICICI", m.bank)
    }

    @Test
    fun hdfc_vehicleInBrackets() {
        val m = debit(
            "Your FASTag (Vehicle MH12AB1234) has been debited with Rs 65 at Vashi Toll Plaza on 05/06/24. Balance: Rs 432",
            sender = "VM-HDFCBK",
        )
        assertEquals(65.0, m.amount!!, 0.001)
        assertEquals("Vashi", m.plaza)
        assertEquals(432.0, m.balance!!, 0.001)
        assertEquals("MH12AB1234", m.vehicle)
        assertEquals("HDFC", m.bank)
    }

    @Test
    fun inrTollFee() {
        val m = debit(
            "Dear Customer, toll fee of INR 120 deducted for vehicle KA 05 MN 4321 at NELAMANGALA on 01-02-2024. Your available balance INR 880. -Axis Bank",
        )
        assertEquals(120.0, m.amount!!, 0.001)
        assertEquals(880.0, m.balance!!, 0.001)
        assertEquals("Nelamangala", m.plaza)
        assertEquals("KA05MN4321", m.vehicle)
        assertEquals("Axis", m.bank)
    }

    @Test
    fun paytmRupeeSign() {
        val m = debit(
            "₹155 paid from Paytm FASTag for DL3CAB1234 at Kherki Daula Toll Plaza. Updated balance ₹2,045.00",
        )
        assertEquals(155.0, m.amount!!, 0.001)
        assertEquals("Kherki Daula", m.plaza)
        assertEquals(2045.0, m.balance!!, 0.001)
        assertEquals("DL3CAB1234", m.vehicle)
        assertEquals("Paytm", m.bank)
    }

    @Test
    fun sbiNetcPlazaColon() {
        val m = debit(
            "NETC FASTag txn of Rs 40.00 for UP16BT5678, Toll Plaza: DND FLYWAY, Bal Rs 210.00. SBI",
        )
        assertEquals(40.0, m.amount!!, 0.001)
        assertEquals("Dnd Flyway", m.plaza)
        assertEquals(210.0, m.balance!!, 0.001)
        assertEquals("SBI", m.bank)
    }

    @Test
    fun hindi() {
        val m = debit("आपके फास्टैग से ₹95.00 खेड शिवापुर टोल प्लाजा पर काटे गए। शेष राशि ₹1,240.50")
        assertEquals(95.0, m.amount!!, 0.001)
        assertEquals("खेड शिवापुर", m.plaza)
        assertEquals(1240.50, m.balance!!, 0.001)
    }

    @Test
    fun bharatSeriesPlate() {
        assertEquals("22BH1234AA", FastagParser.findVehicle("FASTag 22 BH 1234 AA debited Rs 50"))
    }

    @Test
    fun recharge() {
        val m = FastagParser.parse("Your FASTag has been recharged with Rs 500.00. Avl bal Rs 1,740.50. Kotak Bank")
        assertEquals(FastagKind.Recharge, m!!.kind)
        assertEquals(500.0, m.amount!!, 0.001)
        assertEquals(1740.5, m.balance!!, 0.001)
        assertEquals("Kotak", m.bank)
    }

    @Test
    fun lowBalanceAlert() {
        val m = FastagParser.parse("Low balance alert: your IDFC FIRST Bank FASTag balance is Rs 150.00. Recharge now to avoid penalty.")
        assertEquals(150.0, m!!.balance!!, 0.001)
        assertEquals("IDFC First", m.bank)
    }

    @Test
    fun ignoresOtpAndOtherSms() {
        assertNull(FastagParser.parse("123456 is your OTP for FASTag recharge of Rs 500. Do not share."))
        assertNull(FastagParser.parse("Rs 500 debited from A/c XX1234 for Swiggy order. Avl Bal Rs 5,000"))
        assertNull(FastagParser.parse("Hi, are we meeting at the toll gate?"))
    }
}
