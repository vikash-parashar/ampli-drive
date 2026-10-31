package com.vikash.ampli.drive.cameras

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Road reports (police check, accident, jam, mobile camera) saved on this unit only, in a small
 * SharedPreferences file. There is no Ampli server yet, so other drivers do not see them; each
 * one is forgotten after [RoadReports.TTL_MS].
 *
 * TODO: share reports (with confirm / gone votes) once an Ampli backend exists.
 */
class RoadReportRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _reports = MutableStateFlow(load())

    /** Live reports, newest first. Call [prune] now and then to drop expired ones. */
    val reports: StateFlow<List<RoadReport>> = _reports.asStateFlow()

    /** The live reports right now. */
    fun live(now: Long = System.currentTimeMillis()): List<RoadReport> = RoadReports.live(_reports.value, now)

    /** Drops expired reports (cheap; does nothing when none expired). */
    fun prune(now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val live = RoadReports.live(_reports.value, now)
            if (live.size != _reports.value.size) save(live)
        }
    }

    /**
     * Saves a report at the car's position. The GPS bearing becomes its direction when the car is
     * moving, so it only alerts on this side of the road.
     */
    fun add(kind: ReportKind, location: Location, speedKmh: Float?, now: Long = System.currentTimeMillis()): RoadReport {
        val moving = location.hasBearing() && (speedKmh ?: 0f) >= CameraGeometry.SLOW_KMH
        val report = RoadReport(
            id = "r$now",
            kind = kind,
            lat = location.latitude,
            lon = location.longitude,
            dir = if (moving) location.bearing.toDouble() else null,
            time = now,
        )
        synchronized(lock) { save(RoadReports.add(_reports.value, report, now)) }
        return report
    }

    /** Removes one report ("gone"). */
    fun remove(id: String) {
        synchronized(lock) { save(_reports.value.filterNot { it.id == id }) }
    }

    private fun save(list: List<RoadReport>) {
        _reports.value = list
        val arr = JSONArray()
        for (r in list) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("kind", r.kind.name)
                    .put("lat", r.lat)
                    .put("lon", r.lon)
                    .put("dir", r.dir ?: JSONObject.NULL)
                    .put("time", r.time),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<RoadReport> {
        val arr = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val out = ArrayList<RoadReport>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kind = ReportKind.entries.firstOrNull { it.name == o.optString("kind") } ?: continue
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            out += RoadReport(
                id = o.optString("id", "r$i"),
                kind = kind,
                lat = lat,
                lon = lon,
                dir = if (o.isNull("dir")) null else o.optDouble("dir", Double.NaN).takeIf { !it.isNaN() },
                time = o.optLong("time", 0L),
            )
        }
        return RoadReports.live(out, System.currentTimeMillis())
    }

    private companion object {
        const val FILE = "road_reports"
        const val KEY = "reports"
    }
}
