package com.vikash.ampli.drive.tpms

/** One tyre sensor broadcast, decoded. Pressure is gauge pressure (above atmosphere). */
data class TpmsReading(
    val sensorId: String,
    val family: String,
    val pressureKpa: Double,
    val temperatureC: Double?,
    val batteryPercent: Int? = null,
    val batteryVolts: Double? = null,
    /** The sensor itself reports an alarm (leak / no pressure). */
    val alarm: Boolean = false,
    /** Wheel the sensor was sold for (0 FL, 1 FR, 2 RL, 3 RR), when it says. */
    val positionHint: Int? = null,
) {
    val psi: Double get() = pressureKpa / KPA_PER_PSI

    companion object {
        const val KPA_PER_PSI = 6.894757
    }
}

/** A BLE advertisement split into the fields TPMS decoders need. */
data class Advert(
    val address: String,
    val name: String?,
    /** Each manufacturer-specific (AD type 0xFF) payload, exactly as sent: company-id bytes included. */
    val manufacturerData: List<ByteArray>,
    /** 16-bit service UUIDs listed in the advertisement. */
    val serviceUuids16: List<Int>,
)

/** Decodes one sensor family. Add new families by implementing this and listing them in [TpmsDecoders.ALL]. */
interface TpmsDecoder {
    /** Short name shown in the pairing list. */
    val family: String
    fun decode(advert: Advert): TpmsReading?
}

object TpmsDecoders {

    /**
     * Cheap BLE cap/internal sensors sold with the "TPMS" / "TPMSII" phone apps. Manufacturer data
     * (18 bytes) starts 00 01, so Android reports company id 0x0100:
     *   [0..1] 00 01 · [2] 0x80 + wheel (0 FL, 1 FR, 2 RL, 3 RR) · [2..7] sensor address
     *   [8..11] pressure, Pa, uint32 little-endian · [12..15] temperature, 0.01 °C, int32 LE
     *   [16] battery % · [17] alarm flag (non-zero = alarm)
     */
    object Tpms0100 : TpmsDecoder {
        override val family = "TPMS 0x0100"

        override fun decode(advert: Advert): TpmsReading? {
            for (d in advert.manufacturerData) {
                if (d.size != 18 || d[0].u() != 0x00 || d[1].u() != 0x01) continue
                val pos = d[2].u()
                if (pos !in 0x80..0x8F) continue
                val pa = le32(d, 8)
                val centi = le32(d, 12).toInt()
                val kpa = pa / 1000.0
                if (kpa < 0.0 || kpa > 1000.0) continue
                return TpmsReading(
                    sensorId = hex(d, 2, 6),
                    family = family,
                    pressureKpa = kpa,
                    temperatureC = centi / 100.0,
                    batteryPercent = d[16].u().takeIf { it <= 100 },
                    alarm = d[17].u() != 0,
                    positionHint = (pos - 0x80).takeIf { it in 0..3 },
                )
            }
            return null
        }
    }

    /**
     * "BR" sensors (service UUID 0x27A5), 7 bytes of manufacturer data with no company id:
     *   [0] status · [1] battery, 0.1 V · [2] temperature, °C · [3..4] pressure, 0.1 psi
     *   absolute, big-endian · [5..6] checksum.
     * The sensor id is the Bluetooth address.
     */
    object BrSensor : TpmsDecoder {
        override val family = "BR (0x27A5)"
        private const val ATMOSPHERE_PSI = 14.5

        override fun decode(advert: Advert): TpmsReading? {
            if (0x27A5 !in advert.serviceUuids16 && advert.name?.trim() != "BR") return null
            val d = advert.manufacturerData.firstOrNull { it.size == 7 } ?: return null
            val absPsi = (d[3].u() * 256 + d[4].u()) / 10.0
            val gaugePsi = (absPsi - ATMOSPHERE_PSI).coerceAtLeast(0.0)
            return TpmsReading(
                sensorId = advert.address.uppercase(),
                family = family,
                pressureKpa = gaugePsi * TpmsReading.KPA_PER_PSI,
                temperatureC = d[2].toInt().toDouble(),
                batteryVolts = d[1].u() / 10.0,
            )
        }
    }

    val ALL: List<TpmsDecoder> = listOf(Tpms0100, BrSensor)

    fun decode(advert: Advert): TpmsReading? = ALL.firstNotNullOfOrNull { it.decode(advert) }

    /** Splits raw advertisement bytes (ScanRecord.getBytes()) into AD structures. */
    fun parseAdvert(address: String, bytes: ByteArray): Advert {
        var name: String? = null
        val mfr = ArrayList<ByteArray>()
        val uuids = ArrayList<Int>()
        var i = 0
        while (i < bytes.size) {
            val len = bytes[i].u()
            if (len == 0 || i + len >= bytes.size) break
            val type = bytes[i + 1].u()
            val data = bytes.copyOfRange(i + 2, i + 1 + len)
            when (type) {
                0x02, 0x03 -> for (k in 0 until data.size / 2) uuids += data[k * 2].u() or (data[k * 2 + 1].u() shl 8)
                0x08, 0x09 -> name = String(data, Charsets.UTF_8)
                0xFF -> mfr += data
            }
            i += len + 1
        }
        return Advert(address, name, mfr, uuids)
    }

    private fun Byte.u(): Int = toInt() and 0xFF
    private fun le32(d: ByteArray, at: Int): Long =
        (d[at].u().toLong()) or (d[at + 1].u().toLong() shl 8) or (d[at + 2].u().toLong() shl 16) or (d[at + 3].u().toLong() shl 24)
    private fun hex(d: ByteArray, from: Int, count: Int) = (from until from + count).joinToString(":") { "%02X".format(d[it].u()) }
}
