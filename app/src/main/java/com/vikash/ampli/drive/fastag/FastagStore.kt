package com.vikash.ampli.drive.fastag

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** One FASTag SMS, as stored. [time] is when the SMS notification was posted (epoch ms). */
data class TollEvent(
    val time: Long,
    val kind: FastagKind,
    val amount: Double?,
    val plaza: String?,
    val balance: Double?,
    val vehicle: String?,
    val bank: String?,
    /** An Annual Pass crossing (no money taken). */
    val passTrip: Boolean = false,
    /** Trips left on the pass, when the SMS said so. */
    val passTripsLeft: Int? = null,
)

/** Toll totals for the FASTag screen. */
data class TollTotals(val trip: Double, val tripCount: Int, val today: Double, val month: Double)

/**
 * FASTag events and settings, kept in their own SharedPreferences file as JSON (last [MAX_EVENTS]).
 * The notification listener adds events; the home screen and FASTag screen read them.
 * Balance appears only after the first FASTag SMS arrives: banks offer no API for it.
 */
class FastagStore(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _events = MutableStateFlow(load())
    /** Newest first. */
    val events: StateFlow<List<TollEvent>> = _events.asStateFlow()

    private val _arrivals = MutableSharedFlow<TollEvent>(extraBufferCapacity = 4)
    /** New toll debits as they arrive, for the 6-second card on the home screen. */
    val arrivals: SharedFlow<TollEvent> = _arrivals.asSharedFlow()

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _lowBalance = MutableStateFlow(sp.getInt(KEY_LOW, DEFAULT_LOW_BALANCE))
    /** Below this (rupees) the home chip turns red. */
    val lowBalance: StateFlow<Int> = _lowBalance.asStateFlow()

    private val _rechargeUrl = MutableStateFlow(sp.getString(KEY_URL, "") ?: "")
    /** The driver's own bank FASTag recharge page, or "" for the NPCI list of issuers. */
    val rechargeUrl: StateFlow<String> = _rechargeUrl.asStateFlow()

    private val _smsConsent = MutableStateFlow(sp.getBoolean(KEY_SMS_CONSENT, false))

    /**
     * The driver agreed to the FASTag SMS disclosure (Disclosure.FastagSms). Off by default:
     * without it no SMS is read at all, and the balance and pass trips are entered by hand.
     */
    val smsConsent: StateFlow<Boolean> = _smsConsent.asStateFlow()

    /** Toll SMS are parsed only when the feature is on and the disclosure was accepted. */
    val readsSms: Boolean get() = _enabled.value && _smsConsent.value

    fun setSmsConsent(value: Boolean) {
        sp.edit().putBoolean(KEY_SMS_CONSENT, value).apply()
        _smsConsent.value = value
    }

    private val _pass = MutableStateFlow(loadPass())

    /** FASTag Annual Pass: start date and the driver's +1 / −1 corrections. */
    val pass: StateFlow<AnnualPass> = _pass.asStateFlow()

    fun setPassStart(start: java.time.LocalDate?) {
        // A new pass starts a new count.
        savePass(AnnualPass(start, emptyList()))
    }

    /** Manual trip correction: +1 a crossing the SMS missed, −1 one counted by mistake. */
    fun adjustPassTrips(delta: Int, time: Long = System.currentTimeMillis()) {
        val p = _pass.value
        if (p.start == null) return
        savePass(p.copy(adjustments = (p.adjustments + PassAdjustment(time, delta)).takeLast(AnnualPass.MAX_ADJUSTMENTS)))
    }

    private fun loadPass(): AnnualPass = try {
        val o = JSONObject(sp.getString(KEY_PASS, "{}") ?: "{}")
        val arr = o.optJSONArray("adj") ?: JSONArray()
        AnnualPass(
            start = o.optString("start").takeIf { it.isNotEmpty() }?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
            adjustments = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONArray(i)?.let { PassAdjustment(it.optLong(0), it.optInt(1)) }
            },
        )
    } catch (_: Exception) {
        AnnualPass(null, emptyList())
    }

    private fun savePass(p: AnnualPass) {
        val arr = JSONArray()
        for (a in p.adjustments) arr.put(JSONArray().put(a.time).put(a.delta))
        val o = JSONObject().put("adj", arr)
        p.start?.let { o.put("start", it.toString()) }
        sp.edit().putString(KEY_PASS, o.toString()).apply()
        _pass.value = p
    }

    fun setEnabled(value: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setLowBalance(rupees: Int) {
        sp.edit().putInt(KEY_LOW, rupees).apply()
        _lowBalance.value = rupees
    }

    fun setRechargeUrl(url: String) {
        sp.edit().putString(KEY_URL, url.trim()).apply()
        _rechargeUrl.value = url.trim()
    }

    fun clear() {
        sp.edit().remove(KEY_EVENTS).apply()
        _events.value = emptyList()
    }

    /**
     * Records a parsed SMS. SMS apps often re-post the same notification (grouping, read state),
     * so a message equal to one already stored is ignored.
     * [announce] shows the home-screen card; false for old notifications found at startup.
     */
    @Synchronized
    fun add(message: FastagMessage, time: Long, announce: Boolean): Boolean {
        if (!_enabled.value) return false
        val event = TollEvent(
            time = time,
            kind = message.kind,
            amount = message.amount,
            plaza = message.plaza,
            balance = message.balance,
            vehicle = message.vehicle,
            bank = message.bank,
            passTrip = message.passTrip,
            passTripsLeft = message.passTripsLeft,
        )
        val current = _events.value
        // The balance changes with every debit or recharge, so the same amount and balance is the
        // same SMS seen again, however much later. Without a balance, only a nearby time counts.
        // A pass crossing takes no money, so the balance doesn't change: the trips left (or the
        // plaza and a nearby time) tell two crossings apart.
        val duplicate = current.any {
            it.kind == event.kind && it.amount == event.amount && it.balance == event.balance &&
                it.passTrip == event.passTrip && it.passTripsLeft == event.passTripsLeft &&
                if (event.passTrip) {
                    event.passTripsLeft != null || (it.plaza == event.plaza && abs(it.time - time) < DUPLICATE_WINDOW_MS)
                } else {
                    event.balance != null || abs(it.time - time) < DUPLICATE_WINDOW_MS
                }
        }
        if (duplicate) return false
        val updated = (current + event).sortedByDescending { it.time }.take(MAX_EVENTS)
        _events.value = updated
        save(updated)
        if (announce && event.kind == FastagKind.Debit && !event.passTrip) _arrivals.tryEmit(event)
        return true
    }

    private fun load(): List<TollEvent> = try {
        val arr = JSONArray(sp.getString(KEY_EVENTS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            TollEvent(
                time = o.optLong("t"),
                kind = runCatching { FastagKind.valueOf(o.optString("k")) }.getOrDefault(FastagKind.Debit),
                amount = o.optDoubleOrNull("a"),
                plaza = o.optStringOrNull("p"),
                balance = o.optDoubleOrNull("b"),
                vehicle = o.optStringOrNull("v"),
                bank = o.optStringOrNull("n"),
                passTrip = o.optBoolean("pt", false),
                passTripsLeft = if (o.has("pl")) o.optInt("pl") else null,
            )
        }.sortedByDescending { it.time }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<TollEvent>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject().apply {
                    put("t", e.time)
                    put("k", e.kind.name)
                    e.amount?.let { put("a", it) }
                    e.plaza?.let { put("p", it) }
                    e.balance?.let { put("b", it) }
                    e.vehicle?.let { put("v", it) }
                    e.bank?.let { put("n", it) }
                    if (e.passTrip) put("pt", true)
                    e.passTripsLeft?.let { put("pl", it) }
                },
            )
        }
        sp.edit().putString(KEY_EVENTS, arr.toString()).apply()
    }

    companion object {
        const val MAX_EVENTS = 200
        const val DEFAULT_LOW_BALANCE = 200
        val LOW_BALANCE_CHOICES = listOf(100, 200, 300, 500, 1000)

        /** NPCI's NETC FASTag page, listing the issuer banks and their recharge links. */
        const val NPCI_URL = "https://www.npci.org.in/what-we-do/netc-fastag/product-overview"

        /** Tolls closer together than this belong to the same trip. */
        const val TRIP_GAP_MS = 4L * 60 * 60 * 1000

        private const val DUPLICATE_WINDOW_MS = 10L * 60 * 1000
        private const val FILE = "fastag"
        private const val KEY_EVENTS = "events"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LOW = "low_balance"
        private const val KEY_URL = "recharge_url"
        private const val KEY_SMS_CONSENT = "sms_consent"
        private const val KEY_PASS = "annual_pass"

        /** Newest event that carries a balance. */
        fun latestBalance(events: List<TollEvent>): TollEvent? = events.firstOrNull { it.balance != null }

        /**
         * Today, this month, and the current trip: the run of tolls each within [TRIP_GAP_MS]
         * of the next, ending with the newest one (if that is itself recent).
         */
        fun totals(events: List<TollEvent>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): TollTotals {
            val debits = events.filter { it.kind == FastagKind.Debit && it.amount != null && !it.passTrip }
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            fun date(e: TollEvent) = Instant.ofEpochMilli(e.time).atZone(zone).toLocalDate()
            var trip = 0.0
            var tripCount = 0
            var last = now
            for (e in debits) {
                if (last - e.time > TRIP_GAP_MS) break
                trip += e.amount!!
                tripCount++
                last = e.time
            }
            return TollTotals(
                trip = trip,
                tripCount = tripCount,
                today = debits.filter { date(it) == today }.sumOf { it.amount!! },
                month = debits.filter { date(it).let { d -> d.year == today.year && d.month == today.month } }.sumOf { it.amount!! },
            )
        }
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null
