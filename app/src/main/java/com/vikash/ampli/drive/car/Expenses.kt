package com.vikash.ampli.drive.car

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One fuel fill-up. [time] is epoch ms and also the id. */
data class FuelFill(
    val time: Long,
    val litres: Double,
    val rupees: Double,
    val odometerKm: Double,
    val fullTank: Boolean,
)

enum class ExpenseKind { Service, Repair, Toll, Parking, Other }

/** A non-fuel expense. Tolls come from the FASTag history automatically ([fromFastag]). */
data class Expense(
    val time: Long,
    val kind: ExpenseKind,
    val rupees: Double,
    val note: String = "",
    val fromFastag: Boolean = false,
)

/** Distance between two full-tank fills and the fuel it took. */
data class MileageSegment(val fromKm: Double, val toKm: Double, val litres: Double, val endTime: Long) {
    val km: Double get() = toKm - fromKm
    val kmPerLitre: Double get() = km / litres
}

data class MonthTotal(val month: YearMonth, val fuel: Double, val service: Double, val toll: Double, val other: Double) {
    val total: Double get() = fuel + service + toll + other
}

/** Mileage, running cost and monthly totals. Pure. */
object Mileage {

    /**
     * Full-to-full mileage: from one full tank to the next, the distance driven divided by all
     * the fuel added after the first (partial fills in between included). Fills before the first
     * full tank are ignored, since the tank level then is unknown.
     */
    fun segments(fills: List<FuelFill>): List<MileageSegment> {
        val out = ArrayList<MileageSegment>()
        var anchor: FuelFill? = null
        var litres = 0.0
        for (f in fills.sortedWith(compareBy({ it.odometerKm }, { it.time }))) {
            val a = anchor
            if (a == null) {
                if (f.fullTank) anchor = f
                continue
            }
            litres += f.litres
            if (f.fullTank) {
                if (f.odometerKm > a.odometerKm && litres > 0.0) {
                    out += MileageSegment(a.odometerKm, f.odometerKm, litres, f.time)
                }
                anchor = f
                litres = 0.0
            }
        }
        return out
    }

    /** Overall km/l across all full-to-full segments, or null with fewer than two full tanks. */
    fun average(fills: List<FuelFill>): Double? {
        val segs = segments(fills)
        val litres = segs.sumOf { it.litres }
        return if (segs.isEmpty() || litres <= 0.0) null else segs.sumOf { it.km } / litres
    }

    /**
     * Running cost per km between the first and the last fill: fuel bought after the first fill
     * plus every other expense (tolls included) in that period, over the distance driven.
     */
    fun costPerKm(fills: List<FuelFill>, expenses: List<Expense>): Double? {
        if (fills.size < 2) return null
        val sorted = fills.sortedBy { it.odometerKm }
        val km = sorted.last().odometerKm - sorted.first().odometerKm
        if (km <= 0.0) return null
        val from = fills.minOf { it.time }
        val to = fills.maxOf { it.time }
        val fuel = sorted.drop(1).sumOf { it.rupees }
        val other = expenses.filter { it.time in from..to }.sumOf { it.rupees }
        return (fuel + other) / km
    }

    /** Totals per calendar month, oldest first, for the last [months] months up to [now]. */
    fun monthly(
        fills: List<FuelFill>,
        expenses: List<Expense>,
        now: Long,
        months: Int = 6,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthTotal> {
        fun ym(t: Long) = YearMonth.from(Instant.ofEpochMilli(t).atZone(zone))
        val last = ym(now)
        return (months - 1 downTo 0).map { back ->
            val m = last.minusMonths(back.toLong())
            val ex = expenses.filter { ym(it.time) == m }
            MonthTotal(
                month = m,
                fuel = fills.filter { ym(it.time) == m }.sumOf { it.rupees },
                service = ex.filter { it.kind == ExpenseKind.Service || it.kind == ExpenseKind.Repair }.sumOf { it.rupees },
                toll = ex.filter { it.kind == ExpenseKind.Toll }.sumOf { it.rupees },
                other = ex.filter { it.kind == ExpenseKind.Parking || it.kind == ExpenseKind.Other }.sumOf { it.rupees },
            )
        }
    }

    /** Spreadsheet-friendly CSV (RFC 4180 quoting), newest first. */
    fun csv(fills: List<FuelFill>, expenses: List<Expense>, zone: ZoneId = ZoneId.systemDefault()): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        fun date(t: Long) = fmt.format(Instant.ofEpochMilli(t).atZone(zone))
        fun num(d: Double) = if (d % 1.0 == 0.0) d.toLong().toString() else "%.2f".format(Locale.US, d)
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        data class Row(val time: Long, val cells: List<String>)
        val rows = fills.map {
            Row(it.time, listOf(date(it.time), "Fuel", num(it.rupees), num(it.litres), num(it.odometerKm), if (it.fullTank) "yes" else "no", ""))
        } + expenses.map {
            Row(it.time, listOf(date(it.time), it.kind.name, num(it.rupees), "", "", "", q(it.note)))
        }
        val sb = StringBuilder("Date,Type,Amount (INR),Litres,Odometer (km),Full tank,Note\r\n")
        for (r in rows.sortedByDescending { it.time }) sb.append(r.cells.joinToString(",")).append("\r\n")
        return sb.toString()
    }
}
