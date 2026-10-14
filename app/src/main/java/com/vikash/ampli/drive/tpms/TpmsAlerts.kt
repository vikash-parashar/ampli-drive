package com.vikash.ampli.drive.tpms

enum class Wheel { FL, FR, RL, RR }

enum class TyreAlert { Low, High, FastLeak, Hot, SensorAlarm }

/** Thresholds in psi / °C; defaults suit most Indian hatchbacks and sedans (30–35 psi). */
data class TpmsLimits(
    val lowPsi: Double = 26.0,
    val highPsi: Double = 44.0,
    val hotC: Double = 80.0,
    /** A drop of this much within [leakWindowMs] is a fast leak. */
    val leakPsi: Double = 2.0,
    val leakWindowMs: Long = 5L * 60_000L,
)

/** Pure alert rules over successive readings of one wheel. */
class TpmsAlertEngine(var limits: TpmsLimits = TpmsLimits()) {
    private val history = HashMap<Wheel, ArrayDeque<Pair<Long, Double>>>()

    fun evaluate(wheel: Wheel, reading: TpmsReading, now: Long): Set<TyreAlert> {
        val psi = reading.psi
        val h = history.getOrPut(wheel) { ArrayDeque() }
        while (h.isNotEmpty() && now - h.first().first > limits.leakWindowMs) h.removeFirst()
        h.addLast(now to psi)
        val out = LinkedHashSet<TyreAlert>()
        if (psi < limits.lowPsi) out += TyreAlert.Low
        if (psi > limits.highPsi) out += TyreAlert.High
        val peak = h.maxOf { it.second }
        if (peak - psi >= limits.leakPsi) out += TyreAlert.FastLeak
        val t = reading.temperatureC
        if (t != null && t >= limits.hotC) out += TyreAlert.Hot
        if (reading.alarm) out += TyreAlert.SensorAlarm
        return out
    }

    fun forget(wheel: Wheel) {
        history.remove(wheel)
    }
}
