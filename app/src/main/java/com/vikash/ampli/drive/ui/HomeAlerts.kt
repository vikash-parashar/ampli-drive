package com.vikash.ampli.drive.ui

import com.vikash.ampli.drive.cameras.CameraAlert

/**
 * Home's one alert slot. Everything that used to be its own status chip (camera, FASTag, car,
 * weather, GPS) is a candidate here, and only the most urgent one shows. Plain Kotlin, tested
 * (HomeAlertsTest).
 */
enum class AlertKind {
    /** Faster than the camera's limit right now. */
    OverLimit,

    /** A tyre pressure / temperature alert (TPMS). */
    Tyre,

    /** An engine warning from the OBD adapter. */
    Engine,

    /** A speed or red-light camera ahead. */
    CameraAhead,

    /** A road report ahead: police check, accident, jam. */
    ReportAhead,

    /** A toll was just taken: "₹65 kata · ₹480 baki". */
    TollPaid,

    /** FASTag balance under the driver's low-balance mark. */
    FastagLow,

    /** Rain, fog or heat on the road ahead. */
    Weather,

    /** Papers or a service due soon or overdue. */
    Due,

    /** No location: GPS off or not allowed. */
    LocationOff,
}

/**
 * One candidate for the slot: a short [title] ("FASTag") and [text] ("₹65 taken · ₹480 left").
 * [danger] paints it in the SOS red instead of amber.
 */
data class HomeAlert(val kind: AlertKind, val title: String, val text: String, val danger: Boolean = false) {
    /**
     * The slot's first line. No location says just "Location off" (its title would only repeat
     * it); everything else leads with its title.
     */
    val headline: String get() = if (kind == AlertKind.LocationOff || title.isBlank()) text else title

    /** The second line, or null when [headline] already says it all. */
    val detail: String? get() = text.takeIf { it != headline }

    /** Both on one line: "FASTag · ₹65 taken · ₹480 left", or just "Location off". */
    val line: String get() = listOfNotNull(headline, detail).joinToString(" · ")
}

/**
 * Ampli's last line on Home: shown for [SHOWN_MS] after it was said, then the slot goes back to
 * its hint (Map First hides its speech bubble). Plain Kotlin, tested (HomeAlertsTest).
 */
object AmpliLine {
    const val SHOWN_MS = 12_000L

    /** How much longer a line said at [saidAt] stays up at [now] (both elapsedRealtime); 0 = gone. */
    fun remainingMs(saidAt: Long, now: Long): Long {
        if (saidAt <= 0L) return 0L
        val age = (now - saidAt).coerceAtLeast(0L)
        return (SHOWN_MS - age).coerceAtLeast(0L)
    }

    /**
     * Map First's speech bubble: only a fresh line, and never while the map asks for location
     * (the bubble would sit on that card's button).
     */
    fun bubbleShown(freshLine: String?, locationPrompt: Boolean): Boolean = freshLine != null && !locationPrompt
}

object HomeAlerts {
    /** How long a toll deduction stays in the slot after the SMS arrives. */
    const val TOLL_SHOWN_MS = 2 * 60_000L

    /** The most urgent candidate (lowest [AlertKind] ordinal); null when nothing needs saying. */
    fun pick(candidates: List<HomeAlert?>): HomeAlert? = candidates.filterNotNull().minByOrNull { it.kind.ordinal }

    /** Which kind a camera / road-report alert counts as. */
    fun kindFor(alert: CameraAlert): AlertKind = when {
        alert.overLimit -> AlertKind.OverLimit
        alert.kind == CameraAlert.Kind.Police || alert.kind == CameraAlert.Kind.Accident || alert.kind == CameraAlert.Kind.Jam ->
            AlertKind.ReportAhead
        else -> AlertKind.CameraAhead
    }

    /** True while a toll SMS from [eventTime] is still fresh enough to show. */
    fun tollFresh(eventTime: Long, now: Long): Boolean = now >= eventTime && now - eventTime <= TOLL_SHOWN_MS
}

/**
 * The large tiles on Home's More sheet, in the designed 4 x 3 order. Everything that left the
 * Home dock lives here (and every one of them can still be opened by voice). Tested (MoreTilesTest).
 */
enum class MoreTile {
    Fastag, Fuel, Radar, Papers,
    Trips, Expenses, CarHealth, Tyres,
    Phone, PhoneLink, Apps, Settings,
    ;

    companion object {
        const val COLUMNS = 4
    }
}
