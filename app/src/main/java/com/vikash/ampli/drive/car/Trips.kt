package com.vikash.ampli.drive.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * One finished drive. Times are epoch ms; [endTime] is the last moment the car moved (not when
 * parking was confirmed a couple of minutes later). Place names are worked out when shown,
 * from the saved places, so saving "Home" later names old trips too.
 */
data class Trip(
    val startTime: Long,
    val endTime: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceM: Double,
    val topKmh: Float,
) {
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0L)
    val distanceKm: Double get() = distanceM / 1000.0

    /** Distance over the whole trip time, short stops included (what a driver means by "average"). */
    val avgKmh: Double get() = if (durationMs <= 0L) 0.0 else distanceKm / (durationMs / 3_600_000.0)
}

/** The drive being recorded now. */
data class ActiveTrip(
    val startTime: Long,
    val startLat: Double,
    val startLon: Double,
    val lastTime: Long,
    val lastMovingTime: Long,
    val lastLat: Double,
    val lastLon: Double,
    val distanceM: Double,
    val topKmh: Float,
)

/**
 * Builds trips from GPS fixes. A trip starts at the first fix at [startKmh] or more and ends when
 * CarMonitor's parking detection fires (or GPS stops, or the ignition goes off). A gap of
 * [gapMs] without fixes also ends it, so a unit that slept overnight never joins two days. Very
 * short trips (shunting in a parking lot) are dropped. Pure; CarMonitor feeds it.
 */
class TripRecorder(
    private val startKmh: Float = 15f,
    private val movingKmh: Float = 5f,
    private val minDistanceM: Double = 500.0,
    private val minDurationMs: Long = 60_000L,
    private val gapMs: Long = 20 * 60_000L,
) {
    var active: ActiveTrip? = null
        private set

    /**
     * Restores a trip saved before the app was closed (see TripStore.saveActive). A recent one
     * carries on; an old one is closed at its last fix and returned, to be saved.
     */
    fun restore(trip: ActiveTrip?, now: Long): Trip? {
        active = trip ?: return null
        if (now - trip.lastTime in 0 until gapMs) return null
        return finish(trip.lastTime, null, null)
    }

    /**
     * One fix: [distM] is the distance since the previous fix (OdometerAccumulator, jitter
     * filtered). Returns a trip that a long gap has just closed, to be saved.
     */
    fun onSample(now: Long, kmh: Float, lat: Double, lon: Double, distM: Double): Trip? {
        var closed: Trip? = null
        val a = active
        if (a != null && now - a.lastTime > gapMs) closed = finish(a.lastTime, null, null)
        val cur = active
        if (cur == null) {
            if (kmh < startKmh) return closed
            active = ActiveTrip(now, lat, lon, now, now, lat, lon, 0.0, kmh)
            return closed
        }
        active = cur.copy(
            lastTime = now,
            lastMovingTime = if (kmh >= movingKmh) now else cur.lastMovingTime,
            lastLat = lat,
            lastLon = lon,
            distanceM = cur.distanceM + distM.coerceAtLeast(0.0),
            topKmh = maxOf(cur.topKmh, kmh),
        )
        return closed
    }

    /** The drive ended (parked at [lat], [lon], or the last fix when null). Null for a too-short one. */
    fun finish(now: Long, lat: Double?, lon: Double?): Trip? {
        val a = active ?: return null
        active = null
        val end = minOf(now, a.lastMovingTime).coerceAtLeast(a.startTime)
        val trip = Trip(a.startTime, end, a.startLat, a.startLon, lat ?: a.lastLat, lon ?: a.lastLon, a.distanceM, a.topKmh)
        return if (trip.distanceM >= minDistanceM && trip.durationMs >= minDurationMs) trip else null
    }
}

/** Week totals and fuel estimates for the trip screen. Pure. */
object TripMath {
    const val KEEP = 200

    data class Totals(val count: Int, val distanceKm: Double, val durationMs: Long)

    /** Trips that started this week (Monday 00:00 local time onwards). */
    fun thisWeek(trips: List<Trip>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Totals {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val week = trips.filter { it.startTime in monday..now }
        return Totals(week.size, week.sumOf { it.distanceKm }, week.sumOf { it.durationMs })
    }

    /** Rupees per litre from the latest fill-up, or null without one. */
    fun pricePerLitre(fills: List<FuelFill>): Double? =
        fills.maxByOrNull { it.time }?.takeIf { it.litres > 0.0 && it.rupees > 0.0 }?.let { it.rupees / it.litres }

    /** Estimated fuel cost of [km] from the Expenses mileage; null when mileage or price is unknown. */
    fun fuelCost(km: Double, kmPerLitre: Double?, rupeesPerLitre: Double?): Double? {
        if (kmPerLitre == null || rupeesPerLitre == null || kmPerLitre <= 0.0 || rupeesPerLitre <= 0.0) return null
        return km / kmPerLitre * rupeesPerLitre
    }

    /** Newest first, at most [KEEP]. */
    fun add(list: List<Trip>, trip: Trip): List<Trip> = (listOf(trip) + list).sortedByDescending { it.startTime }.take(KEEP)

    fun toJson(list: List<Trip>): String {
        val arr = JSONArray()
        for (t in list) {
            arr.put(
                JSONObject().put("s", t.startTime).put("e", t.endTime)
                    .put("a", t.startLat).put("b", t.startLon).put("c", t.endLat).put("d", t.endLon)
                    .put("m", t.distanceM).put("top", t.topKmh.toDouble()),
            )
        }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<Trip> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Trip(
                    it.optLong("s"), it.optLong("e"), it.optDouble("a"), it.optDouble("b"),
                    it.optDouble("c"), it.optDouble("d"), it.optDouble("m"), it.optDouble("top").toFloat(),
                )
            }
        }.sortedByDescending { it.startTime }.take(KEEP)
    } catch (_: Exception) {
        emptyList()
    }

    fun activeToJson(a: ActiveTrip): String = JSONObject()
        .put("s", a.startTime).put("a", a.startLat).put("b", a.startLon).put("t", a.lastTime)
        .put("mt", a.lastMovingTime).put("c", a.lastLat).put("d", a.lastLon).put("m", a.distanceM)
        .put("top", a.topKmh.toDouble()).toString()

    fun activeFromJson(raw: String?): ActiveTrip? = try {
        raw?.let {
            val o = JSONObject(it)
            ActiveTrip(
                o.getLong("s"), o.getDouble("a"), o.getDouble("b"), o.getLong("t"), o.getLong("mt"),
                o.getDouble("c"), o.getDouble("d"), o.getDouble("m"), o.getDouble("top").toFloat(),
            )
        }
    } catch (_: Exception) {
        null
    }
}

/** Trip history on this unit only (SharedPreferences JSON, like CarStore), newest first. */
class TripStore(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _trips = MutableStateFlow(TripMath.fromJson(sp.getString(KEY_TRIPS, null)))
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    fun add(trip: Trip) {
        val list = TripMath.add(_trips.value, trip)
        sp.edit().putString(KEY_TRIPS, TripMath.toJson(list)).apply()
        _trips.value = list
    }

    fun clear() {
        sp.edit().remove(KEY_TRIPS).apply()
        _trips.value = emptyList()
    }

    /** The drive in progress, so a unit that kills the app mid-drive doesn't lose it. */
    fun saveActive(a: ActiveTrip?) {
        if (a == null) sp.edit().remove(KEY_ACTIVE).apply() else sp.edit().putString(KEY_ACTIVE, TripMath.activeToJson(a)).apply()
    }

    fun loadActive(): ActiveTrip? = TripMath.activeFromJson(sp.getString(KEY_ACTIVE, null))

    private companion object {
        const val FILE = "trips"
        const val KEY_TRIPS = "trips"
        const val KEY_ACTIVE = "active"
    }
}
