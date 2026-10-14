package com.vikash.ampli.drive.tpms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TpmsDecoderTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun family0100() {
        // 00 01 | 81 EA CA 10 8A 78 | 230 kPa = 230000 Pa = 0x00038270 LE | 25.50 °C = 2550 LE | 95 % | no alarm
        val data = hex("0001" + "81EACA108A78" + "70820300" + "F6090000" + "5F" + "00")
        val r = TpmsDecoders.decode(Advert("AA:BB:CC:DD:EE:FF", null, listOf(data), emptyList()))!!
        assertEquals("TPMS 0x0100", r.family)
        assertEquals("81:EA:CA:10:8A:78", r.sensorId)
        assertEquals(230.0, r.pressureKpa, 1e-9)
        assertEquals(25.5, r.temperatureC!!, 1e-9)
        assertEquals(95, r.batteryPercent)
        assertEquals(1, r.positionHint)
        assertFalse(r.alarm)
        assertEquals(33.36, r.psi, 0.01)
    }

    @Test
    fun family0100NegativeTemperatureAndAlarm() {
        val data = hex("0001" + "80000000000A" + "00000000" + "0CFEFFFF" + "64" + "01") // -5.00 °C
        val r = TpmsDecoders.decode(Advert("x", null, listOf(data), emptyList()))!!
        assertEquals(-5.0, r.temperatureC!!, 1e-9)
        assertTrue(r.alarm)
        assertEquals(0, r.positionHint)
    }

    @Test
    fun brFamily() {
        // From a published capture: 1.3 bar, 17 °C, 3.0 V.
        val adv = TpmsDecoders.parseAdvert("11:22:33:44:55:66", hex("0303A5270308425208FF281E11014D8511"))
        assertEquals("BR", adv.name)
        assertEquals(listOf(0x27A5), adv.serviceUuids16)
        val r = TpmsDecoders.decode(adv)!!
        assertEquals("BR (0x27A5)", r.family)
        assertEquals(17.0, r.temperatureC!!, 1e-9)
        assertEquals(3.0, r.batteryVolts!!, 1e-9)
        assertEquals(1.3, r.pressureKpa / 100.0, 0.02) // bar
        assertEquals("11:22:33:44:55:66", r.sensorId)
    }

    @Test
    fun unrelatedAdvertIgnored() {
        assertNull(TpmsDecoders.decode(Advert("x", "Phone", listOf(hex("4C000215")), listOf(0x180F))))
        assertNull(TpmsDecoders.decode(TpmsDecoders.parseAdvert("x", hex("020106"))))
        assertNotNull(TpmsDecoders.parseAdvert("x", ByteArray(31)))
    }

    @Test
    fun alerts() {
        val e = TpmsAlertEngine()
        fun r(psi: Double, t: Double = 30.0) = TpmsReading("s", "f", psi * TpmsReading.KPA_PER_PSI, t)
        assertTrue(e.evaluate(Wheel.FL, r(32.0), 0L).isEmpty())
        assertEquals(setOf(TyreAlert.FastLeak), e.evaluate(Wheel.FL, r(29.5), 120_000L))
        assertEquals(setOf(TyreAlert.Low, TyreAlert.FastLeak), e.evaluate(Wheel.FL, r(25.0), 240_000L))
        assertEquals(setOf(TyreAlert.High, TyreAlert.Hot), e.evaluate(Wheel.RR, r(46.0, 85.0), 0L))
        // A slow drop over hours is not a fast leak.
        val slow = TpmsAlertEngine()
        var psi = 33.0
        for (i in 0..20) {
            assertFalse(TyreAlert.FastLeak in slow.evaluate(Wheel.RL, r(psi), i * 10 * 60_000L))
            psi -= 0.2
        }
    }
}
