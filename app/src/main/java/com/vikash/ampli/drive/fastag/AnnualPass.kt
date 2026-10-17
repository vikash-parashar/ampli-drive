package com.vikash.ampli.drive.fastag

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A +1 / −1 the driver pressed on the FASTag screen. */
data class PassAdjustment(val time: Long, val delta: Int)

/** The FASTag Annual Pass the driver bought ([start] null: no pass tracked). */
data class AnnualPass(val start: LocalDate?, val adjustments: List<PassAdjustment>) {
    companion object {
        /** NHAI/IHMCL Annual Pass: 200 trips or one year, whichever comes first. */
        const val TRIPS = FastagParser.PASS_TRIPS

        /** Price from 1 Apr 2026 (was ₹3,000 at launch in Aug 2025). Shown, never charged here. */
        const val PRICE_RUPEES = 3075
        const val MAX_ADJUSTMENTS = 400

        /** Below this many trips left, the car mentions it at start. */
        const val LOW_TRIPS = 10

        /** Official page where the pass is bought (Rajmargyatra / NHAI). Nothing is bought in the app. */
        const val INFO_URL = "https://rajmargyatra.nhai.gov.in"
    }
}

data class PassStatus(
    val used: Int,
    val remaining: Int,
    /** Last valid day. */
    val expiry: LocalDate,
    val daysLeft: Long,
    /** [remaining] comes from the latest SMS that stated it (corrected by later crossings). */
    val fromSms: Boolean,
    val expired: Boolean,
) {
    val low: Boolean get() = !expired && remaining in 1..AnnualPass.LOW_TRIPS
}

/** Pure arithmetic for the Annual Pass card. Unit-tested. */
object AnnualPassMath {

    fun expiry(start: LocalDate): LocalDate = start.plusYears(1).minusDays(1)

    /**
     * Trips used and left. Pass crossings come from the parsed SMS ([TollEvent.passTrip]) since
     * [AnnualPass.start]; the newest SMS that states "N trips left" wins over counting; the
     * driver's +1 / −1 apply on top of whichever base is used.
     */
    fun status(pass: AnnualPass, events: List<TollEvent>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): PassStatus? {
        val start = pass.start ?: return null
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val trips = events.filter { it.passTrip && it.time >= startMs }
        val reported = trips.filter { it.passTripsLeft != null }.maxByOrNull { it.time }
        val remaining: Int
        if (reported != null) {
            val laterTrips = trips.count { it.time > reported.time && it.passTripsLeft == null }
            val laterManual = pass.adjustments.filter { it.time > reported.time }.sumOf { it.delta }
            remaining = reported.passTripsLeft!! - laterTrips - laterManual
        } else {
            val manual = pass.adjustments.filter { it.time >= startMs }.sumOf { it.delta }
            remaining = AnnualPass.TRIPS - trips.size - manual
        }
        val left = remaining.coerceIn(0, AnnualPass.TRIPS)
        val end = expiry(start)
        val days = ChronoUnit.DAYS.between(today, end)
        return PassStatus(
            used = AnnualPass.TRIPS - left,
            remaining = left,
            expiry = end,
            daysLeft = days,
            fromSms = reported != null,
            expired = days < 0 || left == 0,
        )
    }

    fun startOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
