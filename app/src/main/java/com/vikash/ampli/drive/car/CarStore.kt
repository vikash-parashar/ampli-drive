package com.vikash.ampli.drive.car

import android.content.Context
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.tpms.TpmsLimits
import com.vikash.ampli.drive.tpms.Wheel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Car-feature switches. Defaults: everything on that is free and safe to run. */
data class CarSettings(
    val breakReminder: Boolean = true,
    val breakAfterMin: Int = 120,
    val crashDetection: Boolean = true,
    val weatherAlerts: Boolean = true,
    /** Keeps GPS, crash detection and the break timer running while other apps are open. */
    val backgroundMonitor: Boolean = true,
    val tpmsLowPsi: Int = 26,
    val tpmsHighPsi: Int = 44,
) {
    val tpmsLimits: TpmsLimits get() = TpmsLimits(lowPsi = tpmsLowPsi.toDouble(), highPsi = tpmsHighPsi.toDouble())
}

/**
 * Papers, service schedule, odometer, fuel and expense log, parking spot, TPMS pairing and
 * settings. Kept on this unit only, as JSON in SharedPreferences (like FastagStore).
 */
class CarStore(private val context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<CarSettings> = _settings.asStateFlow()

    private val _papers = MutableStateFlow(loadPapers())
    val papers: StateFlow<List<Paper>> = _papers.asStateFlow()

    private val _services = MutableStateFlow(loadServices())
    val services: StateFlow<List<ServiceItem>> = _services.asStateFlow()

    private val _odometer = MutableStateFlow(sp.getLong(KEY_ODO, 0L) / 1000.0)
    /** Kilometres. GPS-built, corrected by hand from the dashboard odometer. */
    val odometerKm: StateFlow<Double> = _odometer.asStateFlow()

    private val _fills = MutableStateFlow(loadFills())
    val fills: StateFlow<List<FuelFill>> = _fills.asStateFlow()

    private val _expenses = MutableStateFlow(loadExpenses())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _parked = MutableStateFlow(loadParked())
    val parked: StateFlow<ParkedSpot?> = _parked.asStateFlow()

    private val _wheels = MutableStateFlow(loadWheels())
    /** TPMS sensor id for each paired wheel. */
    val wheels: StateFlow<Map<Wheel, String>> = _wheels.asStateFlow()

    /** Last OBD dongle used (Bluetooth address). */
    var obdAddress: String?
        get() = sp.getString(KEY_OBD, null)
        set(value) { sp.edit().putString(KEY_OBD, value).apply() }

    /** Day (epoch day) the papers/service notification was last shown. */
    var lastDueNotice: Long
        get() = sp.getLong(KEY_DUE_NOTICE, 0L)
        set(value) { sp.edit().putLong(KEY_DUE_NOTICE, value).apply() }

    // ------------------------------------------------------------------ Settings

    fun updateSettings(change: (CarSettings) -> CarSettings) {
        val s = change(_settings.value)
        sp.edit().putString(
            KEY_SETTINGS,
            JSONObject()
                .put("break", s.breakReminder).put("breakMin", s.breakAfterMin)
                .put("crash", s.crashDetection).put("weather", s.weatherAlerts)
                .put("bg", s.backgroundMonitor).put("low", s.tpmsLowPsi).put("high", s.tpmsHighPsi)
                .toString(),
        ).apply()
        _settings.value = s
    }

    private fun loadSettings(): CarSettings = try {
        val o = JSONObject(sp.getString(KEY_SETTINGS, "{}") ?: "{}")
        val d = CarSettings()
        CarSettings(
            breakReminder = o.optBoolean("break", d.breakReminder),
            breakAfterMin = o.optInt("breakMin", d.breakAfterMin),
            crashDetection = o.optBoolean("crash", d.crashDetection),
            weatherAlerts = o.optBoolean("weather", d.weatherAlerts),
            backgroundMonitor = o.optBoolean("bg", d.backgroundMonitor),
            tpmsLowPsi = o.optInt("low", d.tpmsLowPsi),
            tpmsHighPsi = o.optInt("high", d.tpmsHighPsi),
        )
    } catch (_: Exception) {
        CarSettings()
    }

    // ------------------------------------------------------------------ Papers and service

    fun setPaper(kind: PaperKind, expiry: LocalDate?) {
        val list = PaperKind.entries.map { k -> if (k == kind) Paper(k, expiry) else _papers.value.first { it.kind == k } }
        val o = JSONObject()
        list.forEach { p -> p.expiry?.let { o.put(p.kind.name, it.toString()) } }
        sp.edit().putString(KEY_PAPERS, o.toString()).apply()
        _papers.value = list
    }

    private fun loadPapers(): List<Paper> {
        val o = try { JSONObject(sp.getString(KEY_PAPERS, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        return PaperKind.entries.map { k -> Paper(k, o.optString(k.name).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    }

    fun saveService(item: ServiceItem) {
        val list = _services.value.toMutableList()
        val i = list.indexOfFirst { it.id == item.id }
        if (i >= 0) list[i] = item else list += item
        writeServices(list)
    }

    fun deleteService(id: String) = writeServices(_services.value.filterNot { it.id == id })

    private fun writeServices(list: List<ServiceItem>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id); put("name", s.name)
                    s.everyKm?.let { put("km", it) }
                    s.everyMonths?.let { put("months", it) }
                    s.lastKm?.let { put("lastKm", it) }
                    s.lastDate?.let { put("lastDate", it.toString()) }
                },
            )
        }
        sp.edit().putString(KEY_SERVICES, arr.toString()).apply()
        _services.value = list
    }

    private fun loadServices(): List<ServiceItem> {
        val raw = sp.getString(KEY_SERVICES, null) ?: return defaultServices()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ServiceItem(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    everyKm = if (o.has("km")) o.optInt("km") else null,
                    everyMonths = if (o.has("months")) o.optInt("months") else null,
                    lastKm = if (o.has("lastKm")) o.optDouble("lastKm") else null,
                    lastDate = o.optString("lastDate").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                )
            }
        } catch (_: Exception) {
            defaultServices()
        }
    }

    /** Typical Indian petrol-car schedule; the driver edits the intervals to match the manual. */
    private fun defaultServices() = listOf(
        ServiceItem("oil", context.getString(R.string.svc_oil), 10_000, 12, null, null),
        ServiceItem("air", context.getString(R.string.svc_air_filter), 20_000, 24, null, null),
        ServiceItem("tyres", context.getString(R.string.svc_tyre_rotation), 10_000, null, null, null),
        ServiceItem("coolant", context.getString(R.string.svc_coolant), 40_000, 24, null, null),
        ServiceItem("brake", context.getString(R.string.svc_brake_fluid), null, 24, null, null),
    )

    // ------------------------------------------------------------------ Odometer

    private var unsavedM = 0.0

    fun addDistance(meters: Double) {
        if (meters <= 0.0) return
        _odometer.value = _odometer.value + meters / 1000.0
        unsavedM += meters
        if (unsavedM >= 200.0) flushOdometer()
    }

    fun setOdometer(km: Double) {
        _odometer.value = km.coerceAtLeast(0.0)
        flushOdometer()
    }

    fun flushOdometer() {
        unsavedM = 0.0
        sp.edit().putLong(KEY_ODO, (_odometer.value * 1000.0).toLong()).apply()
    }

    // ------------------------------------------------------------------ Fuel and expenses

    fun addFill(f: FuelFill) = writeFills((_fills.value + f).sortedByDescending { it.time })
    fun deleteFill(time: Long) = writeFills(_fills.value.filterNot { it.time == time })

    private fun writeFills(list: List<FuelFill>) {
        val arr = JSONArray()
        list.take(MAX_ROWS).forEach {
            arr.put(JSONObject().put("t", it.time).put("l", it.litres).put("r", it.rupees).put("km", it.odometerKm).put("full", it.fullTank))
        }
        sp.edit().putString(KEY_FILLS, arr.toString()).apply()
        _fills.value = list.take(MAX_ROWS)
    }

    private fun loadFills(): List<FuelFill> = try {
        val arr = JSONArray(sp.getString(KEY_FILLS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { FuelFill(it.optLong("t"), it.optDouble("l"), it.optDouble("r"), it.optDouble("km"), it.optBoolean("full")) }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun addExpense(e: Expense) = writeExpenses((_expenses.value + e).sortedByDescending { it.time })
    fun deleteExpense(time: Long) = writeExpenses(_expenses.value.filterNot { it.time == time })

    private fun writeExpenses(list: List<Expense>) {
        val arr = JSONArray()
        list.take(MAX_ROWS).forEach {
            arr.put(JSONObject().put("t", it.time).put("k", it.kind.name).put("r", it.rupees).put("n", it.note))
        }
        sp.edit().putString(KEY_EXPENSES, arr.toString()).apply()
        _expenses.value = list.take(MAX_ROWS)
    }

    private fun loadExpenses(): List<Expense> = try {
        val arr = JSONArray(sp.getString(KEY_EXPENSES, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                Expense(
                    time = it.optLong("t"),
                    kind = runCatching { ExpenseKind.valueOf(it.optString("k")) }.getOrDefault(ExpenseKind.Other),
                    rupees = it.optDouble("r"),
                    note = it.optString("n"),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    // ------------------------------------------------------------------ Parking

    fun setParked(spot: ParkedSpot?) {
        if (spot == null) {
            sp.edit().remove(KEY_PARKED).apply()
        } else {
            val o = JSONObject().put("lat", spot.lat).put("lon", spot.lon).put("t", spot.time)
            spot.accuracyM?.let { o.put("acc", it.toDouble()) }
            sp.edit().putString(KEY_PARKED, o.toString()).apply()
        }
        _parked.value = spot
    }

    private fun loadParked(): ParkedSpot? = try {
        sp.getString(KEY_PARKED, null)?.let {
            val o = JSONObject(it)
            ParkedSpot(o.getDouble("lat"), o.getDouble("lon"), o.getLong("t"), if (o.has("acc")) o.getDouble("acc").toFloat() else null)
        }
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------------ TPMS

    fun pairWheel(wheel: Wheel, sensorId: String?) {
        val map = _wheels.value.toMutableMap()
        // One sensor belongs to one wheel.
        if (sensorId != null) map.entries.removeAll { it.value == sensorId }
        if (sensorId == null) map.remove(wheel) else map[wheel] = sensorId
        val o = JSONObject()
        map.forEach { (w, id) -> o.put(w.name, id) }
        sp.edit().putString(KEY_WHEELS, o.toString()).apply()
        _wheels.value = map
    }

    private fun loadWheels(): Map<Wheel, String> = try {
        val o = JSONObject(sp.getString(KEY_WHEELS, "{}") ?: "{}")
        Wheel.entries.mapNotNull { w -> o.optString(w.name).takeIf { it.isNotEmpty() }?.let { w to it } }.toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private companion object {
        const val FILE = "car"
        const val MAX_ROWS = 500
        const val KEY_SETTINGS = "settings"
        const val KEY_PAPERS = "papers"
        const val KEY_SERVICES = "services"
        const val KEY_ODO = "odometer_m"
        const val KEY_FILLS = "fills"
        const val KEY_EXPENSES = "expenses"
        const val KEY_PARKED = "parked"
        const val KEY_WHEELS = "tpms_wheels"
        const val KEY_OBD = "obd_address"
        const val KEY_DUE_NOTICE = "due_notice_day"
    }
}
