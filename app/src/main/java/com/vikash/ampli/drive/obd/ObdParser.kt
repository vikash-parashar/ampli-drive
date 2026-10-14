package com.vikash.ampli.drive.obd

/**
 * Parses ELM327 replies (echo off, headers off, spaces on or off). Pure, unit-tested.
 *
 * A reply may contain "SEARCHING...", "BUS INIT: ...OK", the ">" prompt, several ECUs answering,
 * and ISO 15765 (CAN) multi-frame replies with a byte-count line ("00A") and frame
 * numbers ("0:", "1:"…).
 */
object ObdParser {

    private val ERRORS = listOf(
        "NO DATA", "UNABLE TO CONNECT", "CAN ERROR", "BUS ERROR", "BUS BUSY", "STOPPED", "ERROR",
        "FB ERROR", "DATA ERROR", "BUFFER FULL", "?",
    )

    /** Hex lines of the reply with noise removed. Each element is upper-case hex without spaces. */
    fun lines(raw: String): List<String> = raw
        .replace(">", "\n")
        .split('\r', '\n')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() && !it.startsWith("SEARCHING") && !it.startsWith("BUS INIT") }
        .map { it.replace(" ", "") }

    /** True when the adapter answered with an error or "NO DATA" (PID not supported). */
    fun isError(raw: String): Boolean {
        val ls = lines(raw)
        return ls.isEmpty() || ls.any { line -> ERRORS.any { line == it.replace(" ", "") || line.startsWith(it.replace(" ", "")) } }
    }

    /**
     * Data bytes after the mode+PID header ("41 0C" for 010C), from the first ECU that answered.
     * Null when the reply is an error or doesn't contain that PID.
     */
    fun pidBytes(raw: String, pid: Int): IntArray? {
        if (isError(raw)) return null
        val header = "41" + "%02X".format(pid)
        for (line in lines(raw)) {
            val hex = line.substringAfter(':', line)
            if (!hex.all { it.isHexDigitChar() }) continue
            var i = hex.indexOf(header)
            while (i >= 0 && i % 2 != 0) i = hex.indexOf(header, i + 1)
            if (i < 0) continue
            val data = hex.substring(i + header.length)
            if (data.length < 2) continue
            return IntArray(data.length / 2) { data.substring(it * 2, it * 2 + 2).toInt(16) }
        }
        return null
    }

    private fun Char.isHexDigitChar() = this in '0'..'9' || this in 'A'..'F'

    private fun two(raw: String, pid: Int): Int? = pidBytes(raw, pid)?.takeIf { it.size >= 2 }?.let { it[0] * 256 + it[1] }
    private fun one(raw: String, pid: Int): Int? = pidBytes(raw, pid)?.firstOrNull()

    /** 010C: engine speed, rpm. */
    fun rpm(raw: String): Double? = two(raw, 0x0C)?.let { it / 4.0 }

    /** 010D: vehicle speed, km/h. */
    fun speedKmh(raw: String): Int? = one(raw, 0x0D)

    /** 0105: engine coolant temperature, °C. */
    fun coolantC(raw: String): Int? = one(raw, 0x05)?.let { it - 40 }

    /** 0142: control module voltage, volts. */
    fun voltage(raw: String): Double? = two(raw, 0x42)?.let { it / 1000.0 }

    /** 0110: mass air flow, g/s. */
    fun mafGs(raw: String): Double? = two(raw, 0x10)?.let { it / 100.0 }

    /** 015E: engine fuel rate, litres/hour. */
    fun fuelRateLph(raw: String): Double? = two(raw, 0x5E)?.let { it / 20.0 }

    /** Petrol fuel flow from MAF: stoichiometric 14.7:1, petrol ≈ 745 g/l. */
    fun lphFromMaf(mafGs: Double): Double = mafGs * 3600.0 / (14.7 * 745.0)

    /** Instant economy in km/l, or null when stopped or no flow. */
    fun kmPerLitre(speedKmh: Double, litresPerHour: Double): Double? =
        if (speedKmh < 3.0 || litresPerHour < 0.05) null else speedKmh / litresPerHour

    /**
     * Mode 03 (stored trouble codes). Handles CAN (first byte after 43 is the code count, odd
     * byte total) and older protocols (codes padded with 0000, even byte total).
     */
    fun dtcs(raw: String): List<String> {
        if (isError(raw)) return emptyList()
        val ls = lines(raw).filter { it.all { c -> c.isHexDigitChar() || c == ':' } }
        val multiFrame = ls.any { it.contains(':') }
        val messages: List<String> = if (multiFrame) {
            // CAN multi-frame: an optional byte-count line ("00A"), then frames "0:", "1:"… in order.
            val total = ls.firstOrNull { !it.contains(':') && it.length == 3 }?.toIntOrNull(16)
            val joined = ls.filter { it.contains(':') }
                .sortedBy { it.substringBefore(':').toIntOrNull(16) ?: 0 }
                .joinToString("") { it.substringAfter(':') }
            listOf(if (total != null && joined.length > total * 2) joined.substring(0, total * 2) else joined)
        } else {
            ls.filter { it.startsWith("43") }
        }
        val out = LinkedHashSet<String>()
        for (m in messages) {
            if (!m.startsWith("43")) continue
            val hex = m.substring(2)
            val bytes = IntArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16) }
            if (bytes.isEmpty()) continue
            var start = 0
            var count = bytes.size / 2
            // CAN replies carry a code count first (odd byte total); multi-frame is always CAN.
            if (multiFrame || bytes.size % 2 == 1) {
                count = minOf(bytes[0], (bytes.size - 1) / 2)
                start = 1
            }
            for (k in 0 until count) {
                val a = bytes[start + k * 2]
                val b = bytes[start + k * 2 + 1]
                if (a == 0 && b == 0) continue
                out += decodeDtc(a, b)
            }
        }
        return out.toList()
    }

    /** Two bytes to "P0301" (SAE J2012). */
    fun decodeDtc(a: Int, b: Int): String {
        val system = "PCBU"[(a shr 6) and 0x3]
        val d1 = (a shr 4) and 0x3
        return "%c%d%X%02X".format(system, d1, a and 0xF, b)
    }
}

/** Warnings from live data. */
enum class EngineWarning { CoolantHot, BatteryLow, NotCharging, Overcharging }

object EngineHealth {
    const val HOT_C = 105
    const val BATTERY_LOW_V = 11.8
    const val CHARGING_MIN_V = 12.8
    const val OVERCHARGE_V = 15.2

    fun warnings(coolantC: Int?, volts: Double?, rpm: Double?): Set<EngineWarning> {
        val out = LinkedHashSet<EngineWarning>()
        if (coolantC != null && coolantC >= HOT_C) out += EngineWarning.CoolantHot
        if (volts != null) {
            val running = (rpm ?: 0.0) > 400.0
            when {
                volts > OVERCHARGE_V -> out += EngineWarning.Overcharging
                running && volts < CHARGING_MIN_V -> out += EngineWarning.NotCharging
                !running && volts < BATTERY_LOW_V -> out += EngineWarning.BatteryLow
            }
        }
        return out
    }
}
