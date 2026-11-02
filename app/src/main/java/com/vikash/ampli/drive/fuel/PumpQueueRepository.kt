package com.vikash.ampli.drive.fuel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * "CNG pe line lambi hai" reports, on this unit only (a small SharedPreferences file), forgotten
 * after [PumpQueues.TTL_MS]. The fuel screen shows them on the pump cards.
 */
class PumpQueueRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _reports = MutableStateFlow(load())
    val reports: StateFlow<List<QueueReport>> = _reports.asStateFlow()

    fun add(stationId: Int, long: Boolean, fuel: FuelFilter, now: Long = System.currentTimeMillis()): QueueReport {
        val r = QueueReport(stationId, long, now, fuel)
        synchronized(lock) { save(PumpQueues.add(_reports.value, r, now)) }
        return r
    }

    /** Live reports by station id, for the pump cards. */
    fun liveByStation(now: Long = System.currentTimeMillis()): Map<Int, QueueReport> =
        _reports.value.filter { PumpQueues.isLive(it, now) }.groupBy { it.stationId }.mapValues { (_, v) -> v.maxBy { it.time } }

    private fun save(list: List<QueueReport>) {
        _reports.value = list
        val arr = JSONArray()
        for (r in list) arr.put(JSONObject().put("s", r.stationId).put("l", r.long).put("t", r.time).put("f", r.fuel.name))
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<QueueReport> {
        val arr = try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val now = System.currentTimeMillis()
        val out = ArrayList<QueueReport>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val f = FuelFilter.entries.firstOrNull { it.name == o.optString("f") } ?: FuelFilter.All
            out += QueueReport(o.optInt("s", -1), o.optBoolean("l"), o.optLong("t"), f)
        }
        return out.filter { it.stationId >= 0 && PumpQueues.isLive(it, now) }
    }

    private companion object {
        const val FILE = "pump_queues"
        const val KEY = "reports"
    }
}
