package com.vikash.ampli.drive.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A map area to keep on the unit. Bounds in degrees; [maxZoom] 13 shows every road, 14 adds lanes and buildings. */
data class OfflineRegion(
    val key: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Int,
    val maxZoom: Int,
)

/**
 * The offline map regions (Settings › Offline map) and the arithmetic behind the storage check.
 * Plain Kotlin, unit-tested.
 *
 * Tiles are the map the app already shows (OpenFreeMap vector tiles, OpenStreetMap data, ODbL),
 * fetched through MapLibre's own offline database, once, on Wi-Fi, on the driver's tap. Never
 * tile.openstreetmap.org (its policy forbids bulk downloads). See docs/offline-map.md.
 */
object OfflineRegions {
    /** Haryana with Delhi, Gurugram, Faridabad, Noida and Ghaziabad. The default. */
    val HARYANA_NCR = OfflineRegion("haryana_ncr", 27.60, 74.40, 31.00, 77.65, 0, 13)

    /** Delhi NCR only, with street detail. */
    val DELHI_NCR = OfflineRegion("delhi_ncr", 28.20, 76.80, 28.95, 77.65, 0, 14)

    /** Chandigarh, Panchkula and Mohali with street detail. */
    val TRICITY = OfflineRegion("tricity", 30.55, 76.60, 30.85, 76.95, 0, 14)

    val ALL = listOf(HARYANA_NCR, DELHI_NCR, TRICITY)

    fun byKey(key: String?): OfflineRegion = ALL.firstOrNull { it.key == key } ?: HARYANA_NCR

    /** "Around me": a square of about [radiusKm] around the car, street detail. */
    fun aroundMe(lat: Double, lon: Double, radiusKm: Double = 40.0): OfflineRegion {
        val dLat = radiusKm / 111.32
        val dLon = dLat / cos(Math.toRadians(lat)).coerceAtLeast(0.05)
        return OfflineRegion("around_me", lat - dLat, lon - dLon, lat + dLat, lon + dLon, 0, 14)
    }

    /** MapLibre's offline tile limit is raised to this, and no region may need more. */
    const val TILE_LIMIT = 20_000L

    /** Rough vector tile size (OpenMapTiles schema, Indian towns and fields), bytes. */
    const val BYTES_PER_TILE = 22_000L

    /** Style, fonts and icons, downloaded once. */
    const val STYLE_BYTES = 6_000_000L

    /** Keep this much free on top of the download, so the unit never fills up. */
    const val SPARE_BYTES = 300_000_000L

    /** Web Mercator tiles covering the region, over all its zoom levels. */
    fun tileCount(r: OfflineRegion): Long {
        var total = 0L
        for (z in r.minZoom..r.maxZoom) {
            val x0 = lonToTile(r.west, z)
            val x1 = lonToTile(r.east, z)
            val y0 = latToTile(r.north, z)
            val y1 = latToTile(r.south, z)
            total += (x1 - x0 + 1).toLong() * (y1 - y0 + 1).toLong()
        }
        return total
    }

    fun estimatedBytes(r: OfflineRegion): Long = tileCount(r) * BYTES_PER_TILE + STYLE_BYTES

    /** Enough free space for the download plus [SPARE_BYTES]? */
    fun fits(r: OfflineRegion, freeBytes: Long): Boolean = freeBytes >= estimatedBytes(r) + SPARE_BYTES

    fun lonToTile(lon: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTile(lat: Double, z: Int): Int {
        val n = 1 shl z
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * n).toInt().coerceIn(0, n - 1)
    }

    /** "≈ 180 MB". */
    fun megabytes(bytes: Long): Int = ((bytes + 999_999L) / 1_000_000L).toInt()
}
