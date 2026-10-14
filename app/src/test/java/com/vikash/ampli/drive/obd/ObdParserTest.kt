package com.vikash.ampli.drive.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdParserTest {
    @Test
    fun rpmSpeedCoolantVoltage() {
        assertEquals(1726.0, ObdParser.rpm("41 0C 1A F8\r\r>")!!, 1e-9)
        assertEquals(1726.0, ObdParser.rpm("SEARCHING...\r410C1AF8\r>")!!, 1e-9)
        assertEquals(88, ObdParser.speedKmh("41 0D 58 \r>"))
        assertEquals(90, ObdParser.coolantC("41 05 82\r>"))
        assertEquals(14.2, ObdParser.voltage("41 42 37 78\r>")!!, 1e-9)
        assertEquals(3.5, ObdParser.fuelRateLph("41 5E 00 46\r>")!!, 1e-9)
        assertEquals(10.0, ObdParser.mafGs("41 10 03 E8\r>")!!, 1e-9)
    }

    @Test
    fun errorsAndUnsupported() {
        assertNull(ObdParser.rpm("NO DATA\r>"))
        assertNull(ObdParser.rpm("UNABLE TO CONNECT\r>"))
        assertNull(ObdParser.rpm("?\r>"))
        assertNull(ObdParser.rpm(""))
        // A reply for another PID doesn't count.
        assertNull(ObdParser.rpm("41 0D 58\r>"))
        assertTrue(ObdParser.isError("CAN ERROR\r>"))
    }

    @Test
    fun firstEcuWinsWhenSeveralAnswer() {
        assertEquals(30, ObdParser.speedKmh("41 0D 1E\r41 0D 1F\r>"))
    }

    @Test
    fun economy() {
        assertEquals(3.29, ObdParser.lphFromMaf(10.0), 0.01)
        assertEquals(20.0, ObdParser.kmPerLitre(70.0, 3.5)!!, 1e-9)
        assertNull(ObdParser.kmPerLitre(0.0, 1.0))
    }

    @Test
    fun dtcDecoding() {
        assertEquals("P0301", ObdParser.decodeDtc(0x03, 0x01))
        assertEquals("C0300", ObdParser.decodeDtc(0x43, 0x00))
        assertEquals("B1234", ObdParser.decodeDtc(0x92, 0x34))
        assertEquals("U0100", ObdParser.decodeDtc(0xC1, 0x00))
    }

    @Test
    fun dtcsLegacyProtocol() {
        assertEquals(listOf("P0133", "P0420"), ObdParser.dtcs("43 01 33 04 20 00 00\r>"))
        assertTrue(ObdParser.dtcs("43 00 00 00 00 00 00\r>").isEmpty())
    }

    @Test
    fun dtcsCan() {
        assertEquals(listOf("P0133"), ObdParser.dtcs("43 01 01 33\r>"))
        assertEquals(listOf("P0133", "P0171"), ObdParser.dtcs("43 02 01 33 01 71\r>"))
        assertTrue(ObdParser.dtcs("43 00\r>").isEmpty())
        assertTrue(ObdParser.dtcs("NO DATA\r>").isEmpty())
    }

    @Test
    fun dtcsCanMultiFrame() {
        val raw = "00A\r0: 43 04 01 33 01 71\r1: 03 01 04 20 00 00 00\r>"
        assertEquals(listOf("P0133", "P0171", "P0301", "P0420"), ObdParser.dtcs(raw))
    }

    @Test
    fun descriptions() {
        assertEquals("Engine misfire in cylinder 1", DtcCatalog.describe("P0301", hindi = false))
        assertEquals("सिलिंडर 1 में इंजन मिसफायर", DtcCatalog.describe("P0301", hindi = true))
        assertEquals("Catalytic converter not working well, bank 1", DtcCatalog.describe("P0420", false))
        assertEquals("Manufacturer-specific engine code", DtcCatalog.describe("P1234", false))
        assertEquals("Wiring or module communication problem", DtcCatalog.describe("U0100", false))
    }

    @Test
    fun warnings() {
        assertEquals(setOf(EngineWarning.CoolantHot), EngineHealth.warnings(110, 14.0, 800.0))
        assertEquals(setOf(EngineWarning.NotCharging), EngineHealth.warnings(90, 12.2, 800.0))
        assertEquals(setOf(EngineWarning.BatteryLow), EngineHealth.warnings(30, 11.5, 0.0))
        assertTrue(EngineHealth.warnings(90, 12.5, 0.0).isEmpty())
        assertTrue(EngineHealth.warnings(null, null, null).isEmpty())
    }
}
