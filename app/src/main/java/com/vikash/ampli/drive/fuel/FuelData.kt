package com.vikash.ampli.drive.fuel

import com.vikash.ampli.drive.cameras.CameraGeometry
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * What a station sells, as bits of [FuelStation.flags]. Written by scripts/fetch_fuel.py from the
 * OpenStreetMap tags (fuel:cng, fuel:diesel, fuel:octane_*, fuel:lpg, amenity=charging_station).
 */
object FuelFlags {
    const val CNG = 1
    const val PETROL = 2
    const val DIESEL = 4
    const val EV = 8
    const val LPG = 16

    /** opening_hours=24/7. */
    const val OPEN_24H = 32

    /** A pump with no fuel:* tags: petrol and diesel are assumed (most Indian pumps sell both). */
    const val ASSUMED = 64
}

/** The filter chips: All / CNG / Petrol / Diesel / EV charge. */
enum class FuelFilter(val mask: Int) {
    All(0),
    Cng(FuelFlags.CNG),
    Petrol(FuelFlags.PETROL),
    Diesel(FuelFlags.DIESEL),
    Ev(FuelFlags.EV);

    fun matches(flags: Int): Boolean = mask == 0 || flags and mask != 0
}

/** One fuel pump, CNG station or EV charger. [name] is empty when OpenStreetMap has none. */
data class FuelStation(val id: Int, val lat: Double, val lon: Double, val flags: Int, val name: String) {
    fun has(flag: Int): Boolean = flags and flag != 0
}

/** A station with its straight-line distance from the car. */
data class FuelHit(val station: FuelStation, val distanceM: Double)

/**
 * The offline snapshot (assets/fuel.bin), kept as primitive arrays so the whole of India costs
 * well under a megabyte of heap on a 1 GB head unit. Records are sorted by grid cell (the same
 * 0.05° cells as the camera index), so a lookup reads a few contiguous runs.
 *
 * File format, big-endian ([java.io.DataOutputStream]):
 * ```
 * "AFS1"            magic
 * int   generated   yyyymmdd, 0 when unknown
 * int   nameCount   then nameCount × (u16 byte length, UTF-8 bytes)
 * int   count       then count × (int latE5, int lonE5, u8 flags, u16 name index, 0xFFFF = none)
 * ```
 * OSM-derived: © OpenStreetMap contributors, ODbL 1.0.
 */
class FuelSnapshot private constructor(
    private val latE5: IntArray,
    private val lonE5: IntArray,
    private val flags: ByteArray,
    private val nameIdx: ShortArray,
    private val names: Array<String>,
    /** yyyymmdd of the Overpass download, or 0. */
    val generated: Int,
) {
    val size: Int get() = latE5.size

    // Unique cell keys in file order, and where each run starts (cellStart[i]..cellStart[i + 1]).
    private val cellKeys: LongArray
    private val cellStart: IntArray

    init {
        val keys = ArrayList<Long>()
        val starts = ArrayList<Int>()
        var last = Long.MIN_VALUE
        for (i in latE5.indices) {
            val k = key(cell(latE5[i] / 1e5), cell(lonE5[i] / 1e5))
            if (k != last) {
                keys += k
                starts += i
                last = k
            }
        }
        starts += latE5.size
        cellKeys = keys.toLongArray()
        cellStart = starts.toIntArray()
    }

    fun station(i: Int): FuelStation {
        val n = nameIdx[i].toInt() and 0xFFFF
        return FuelStation(i, latE5[i] / 1e5, lonE5[i] / 1e5, flags[i].toInt() and 0xFF, if (n == NO_NAME || n >= names.size) "" else names[n])
    }

    /**
     * Calls [visit] with the index of every station in the cells within about [radiusM] of the
     * point (bucket-accurate: callers check the distance).
     */
    internal fun forEachNear(lat: Double, lon: Double, radiusM: Double, visit: (Int) -> Unit) {
        if (cellKeys.isEmpty()) return
        val perLat = CELL_DEG * 111_320.0
        val perLon = perLat * cos(Math.toRadians(lat)).coerceAtLeast(0.05)
        val dy = ceil(radiusM / perLat).toInt()
        val dx = ceil(radiusM / perLon).toInt()
        val cy = cell(lat)
        val cx = cell(lon)
        for (y in cy - dy..cy + dy) {
            for (x in cx - dx..cx + dx) {
                val at = cellKeys.binarySearch(key(y, x))
                if (at < 0) continue
                for (i in cellStart[at] until cellStart[at + 1]) visit(i)
            }
        }
    }

    internal fun flagsAt(i: Int): Int = flags[i].toInt() and 0xFF
    internal fun latAt(i: Int): Double = latE5[i] / 1e5
    internal fun lonAt(i: Int): Double = lonE5[i] / 1e5

    companion object {
        const val CELL_DEG = 0.05
        private const val NO_NAME = 0xFFFF
        private val MAGIC = byteArrayOf('A'.code.toByte(), 'F'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())

        val EMPTY = FuelSnapshot(IntArray(0), IntArray(0), ByteArray(0), ShortArray(0), emptyArray(), 0)

        private fun cell(deg: Double): Int = floor(deg / CELL_DEG).toInt()

        private fun key(y: Int, x: Int): Long = (y.toLong() shl 32) or (x.toLong() and 0xffffffffL)

        /** Reads assets/fuel.bin. Throws on a damaged file; the caller falls back to [EMPTY]. */
        fun read(input: InputStream): FuelSnapshot {
            val d = DataInputStream(input.buffered(64 * 1024))
            val magic = ByteArray(4)
            d.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "not a fuel snapshot" }
            val generated = d.readInt()
            val nameCount = d.readInt()
            require(nameCount in 0..65_534) { "bad name count" }
            val names = Array(nameCount) {
                val len = d.readUnsignedShort()
                val bytes = ByteArray(len)
                d.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
            val count = d.readInt()
            require(count in 0..2_000_000) { "bad station count" }
            val lat = IntArray(count)
            val lon = IntArray(count)
            val fl = ByteArray(count)
            val nm = ShortArray(count)
            for (i in 0 until count) {
                lat[i] = d.readInt()
                lon[i] = d.readInt()
                fl[i] = d.readByte()
                nm[i] = d.readShort()
            }
            return build(lat, lon, fl, nm, names, generated)
        }

        /** For tests and [read]: sorts the records into cell order if they are not already. */
        fun build(lat: IntArray, lon: IntArray, fl: ByteArray, nm: ShortArray, names: Array<String>, generated: Int = 0): FuelSnapshot {
            val n = lat.size
            val keys = LongArray(n) { key(cell(lat[it] / 1e5), cell(lon[it] / 1e5)) }
            val sorted = (1 until n).all { keys[it - 1] <= keys[it] }
            if (sorted) return FuelSnapshot(lat, lon, fl, nm, names, generated)
            val order = (0 until n).sortedBy { keys[it] }
            return FuelSnapshot(
                IntArray(n) { lat[order[it]] },
                IntArray(n) { lon[order[it]] },
                ByteArray(n) { fl[order[it]] },
                ShortArray(n) { nm[order[it]] },
                names,
                generated,
            )
        }
    }
}

/** Nearest-first lookups over a [FuelSnapshot]. Plain Kotlin, unit-tested. */
object FuelFinder {
    /** First ring searched; it widens until [limit] stations are found or [MAX_RADIUS_M]. */
    const val START_RADIUS_M = 5_000.0
    const val MAX_RADIUS_M = 50_000.0

    /** Straight line to road distance, and an average town speed, for the "~4 min" estimate. */
    private const val ROAD_FACTOR = 1.3
    private const val AVG_KMH = 30.0

    /**
     * Up to [limit] stations matching [filter], nearest first. Looks 5 km around the car, then
     * 10, 20 and 50 km, so a town lookup touches a handful of cells and a highway one still
     * finds the next pump.
     */
    fun nearby(
        snapshot: FuelSnapshot,
        lat: Double,
        lon: Double,
        filter: FuelFilter,
        limit: Int = 20,
        maxRadiusM: Double = MAX_RADIUS_M,
    ): List<FuelHit> {
        if (snapshot.size == 0 || limit <= 0) return emptyList()
        var radius = START_RADIUS_M.coerceAtMost(maxRadiusM)
        while (true) {
            val hits = ArrayList<Pair<Int, Double>>()
            snapshot.forEachNear(lat, lon, radius) { i ->
                if (filter.matches(snapshot.flagsAt(i))) {
                    val d = CameraGeometry.distanceM(lat, lon, snapshot.latAt(i), snapshot.lonAt(i))
                    if (d <= radius) hits += i to d
                }
            }
            if (hits.size >= limit || radius >= maxRadiusM) {
                return hits.sortedWith(compareBy({ it.second }, { it.first })).take(limit).map { FuelHit(snapshot.station(it.first), it.second) }
            }
            radius = max(radius * 2, radius + 1).coerceAtMost(maxRadiusM)
        }
    }

    /** Rough minutes to drive [distanceM] of straight line in town: at least one. */
    fun etaMinutes(distanceM: Double): Int = max(1, ceil(distanceM * ROAD_FACTOR / (AVG_KMH * 1000.0 / 60.0)).toInt())

    /** The types a station sells, in chip order, for its tags ("CNG", "Petrol"…). */
    fun types(flags: Int): List<FuelFilter> = listOf(FuelFilter.Cng, FuelFilter.Petrol, FuelFilter.Diesel, FuelFilter.Ev).filter { it.matches(flags) }
}
