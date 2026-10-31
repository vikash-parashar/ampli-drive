package com.vikash.ampli.drive.fuel

import com.vikash.ampli.drive.cameras.CameraGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class FuelFinderTest {

    private data class S(val lat: Double, val lon: Double, val flags: Int, val name: String?)

    // Around Sonipat (28.99, 77.02).
    private val car = 28.9900 to 77.0200
    private val stations = listOf(
        S(28.9990, 77.0200, FuelFlags.CNG or FuelFlags.OPEN_24H, "IGL CNG Station"), // ~1.0 km north
        S(28.9900, 77.0400, FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.CNG, "Indian Oil"), // ~1.9 km east
        S(28.9700, 77.0200, FuelFlags.EV, "Tata Power EV"), // ~2.2 km south
        S(29.0200, 77.0200, FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.ASSUMED, null), // ~3.3 km
        S(29.2000, 77.0200, FuelFlags.CNG, "Far CNG"), // ~23 km
    )

    private fun snapshot(list: List<S> = stations): FuelSnapshot {
        val names = list.mapNotNull { it.name }.distinct()
        return FuelSnapshot.build(
            IntArray(list.size) { Math.round(list[it].lat * 1e5).toInt() },
            IntArray(list.size) { Math.round(list[it].lon * 1e5).toInt() },
            ByteArray(list.size) { list[it].flags.toByte() },
            ShortArray(list.size) { i -> list[i].name?.let { names.indexOf(it).toShort() } ?: 0xFFFF.toShort() },
            names.toTypedArray(),
        )
    }

    @Test
    fun nearestFirst() {
        val hits = FuelFinder.nearby(snapshot(), car.first, car.second, FuelFilter.All, limit = 4)
        assertEquals(listOf("IGL CNG Station", "Indian Oil", "Tata Power EV", ""), hits.map { it.station.name })
        for (i in 1 until hits.size) assertTrue(hits[i - 1].distanceM <= hits[i].distanceM)
        assertEquals(1_000.0, hits[0].distanceM, 30.0)
    }

    @Test
    fun filtersByFuel() {
        val s = snapshot()
        assertEquals(
            listOf("IGL CNG Station", "Indian Oil", "Far CNG"),
            FuelFinder.nearby(s, car.first, car.second, FuelFilter.Cng).map { it.station.name },
        )
        assertEquals(listOf("Tata Power EV"), FuelFinder.nearby(s, car.first, car.second, FuelFilter.Ev).map { it.station.name })
        assertEquals(2, FuelFinder.nearby(s, car.first, car.second, FuelFilter.Diesel).size)
        assertEquals(2, FuelFinder.nearby(s, car.first, car.second, FuelFilter.Petrol).size)
    }

    @Test
    fun widensTheSearchUntilItFindsEnough() {
        // Only one CNG station inside 5 km; the 23 km one is found by widening.
        val hits = FuelFinder.nearby(snapshot(), car.first, car.second, FuelFilter.Cng, limit = 3)
        assertEquals("Far CNG", hits.last().station.name)
        // ...but never past the limit.
        val near = FuelFinder.nearby(snapshot(), car.first, car.second, FuelFilter.Cng, limit = 3, maxRadiusM = 10_000.0)
        assertEquals(2, near.size)
    }

    @Test
    fun emptyAndFarAway() {
        assertTrue(FuelFinder.nearby(FuelSnapshot.EMPTY, car.first, car.second, FuelFilter.All).isEmpty())
        // Mumbai: nothing within 50 km.
        assertTrue(FuelFinder.nearby(snapshot(), 19.07, 72.88, FuelFilter.All).isEmpty())
    }

    @Test
    fun stationsAcrossCellEdgesAreFound() {
        // 0.05° cells: put the car just south of a cell edge and a pump just north of it.
        val s = snapshot(listOf(S(29.0001, 77.0249, FuelFlags.PETROL, "North"), S(28.9999, 77.0251, FuelFlags.PETROL, "South")))
        val hits = FuelFinder.nearby(s, 28.9998, 77.0250, FuelFilter.All)
        assertEquals(setOf("North", "South"), hits.map { it.station.name }.toSet())
    }

    @Test
    fun readsTheBinaryFormat() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { d ->
            d.write("AFS1".toByteArray())
            d.writeInt(20261031)
            val names = listOf("HP", "भारत पेट्रोलियम")
            d.writeInt(names.size)
            for (n in names) {
                val b = n.toByteArray(Charsets.UTF_8)
                d.writeShort(b.size)
                d.write(b)
            }
            d.writeInt(2)
            for ((lat, lon, fl, nm) in listOf(listOf(2899000, 7702000, 6, 1), listOf(2899500, 7702000, 8, 0xFFFF))) {
                d.writeInt(lat)
                d.writeInt(lon)
                d.writeByte(fl)
                d.writeShort(nm)
            }
        }
        val s = FuelSnapshot.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(2, s.size)
        assertEquals(20261031, s.generated)
        val hits = FuelFinder.nearby(s, 28.99, 77.02, FuelFilter.All)
        assertEquals("भारत पेट्रोलियम", hits[0].station.name)
        assertTrue(hits[0].station.has(FuelFlags.DIESEL))
        assertEquals("", hits[1].station.name)
        assertTrue(hits[1].station.has(FuelFlags.EV))
    }

    @Test
    fun etaAndTypes() {
        assertEquals(1, FuelFinder.etaMinutes(50.0))
        assertEquals(4, FuelFinder.etaMinutes(1_200.0))
        assertEquals(listOf(FuelFilter.Cng, FuelFilter.Petrol, FuelFilter.Diesel), FuelFinder.types(FuelFlags.CNG or FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.OPEN_24H))
        assertEquals(listOf(FuelFilter.Ev), FuelFinder.types(FuelFlags.EV))
    }

    @Test
    fun distancesMatchTheCameraGeometry() {
        val hit = FuelFinder.nearby(snapshot(), car.first, car.second, FuelFilter.Ev).single()
        assertEquals(CameraGeometry.distanceM(car.first, car.second, 28.97, 77.02), hit.distanceM, 1.0)
    }

    /** The snapshot scripts/fetch_fuel.py writes (or the empty seed) must load in the app. */
    @Test
    fun committedAssetLoads() {
        val file = java.io.File("src/main/assets/fuel.bin")
        if (!file.exists()) return // only when tests run from another directory
        val s = file.inputStream().use { FuelSnapshot.read(it) }
        for (i in 0 until s.size) {
            val lat = s.latAt(i)
            val lon = s.lonAt(i)
            assertTrue("station $i outside India: $lat,$lon", lat in 5.0..38.0 && lon in 66.0..99.0)
            assertTrue("station $i sells nothing", s.flagsAt(i) and (FuelFlags.CNG or FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.EV or FuelFlags.LPG) != 0)
        }
    }
}
