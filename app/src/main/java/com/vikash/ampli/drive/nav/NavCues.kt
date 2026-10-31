package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.fuel.FuelFlags
import com.vikash.ampli.drive.fuel.FuelStation
import com.vikash.ampli.drive.nav.route.Geo
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.Lanes
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.voice.HaryanviLines
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A Haryanvi line that says a turn better than the plain manoeuvre line (round 3, the owner's
 * navigation list). Car Hub's own navigation only: it takes the place of the turn line in the
 * prompt said ahead of the turn ([NavPrompt.now] false); the "now" prompt stays as it is.
 *
 * @param prefix true: said after the distance ("400 मीटर आगे, पेट्रोल पंप तै…"); false: the line
 *   is a whole sentence of its own ("आग्गै हाईवे तै उतरणा सै…").
 */
enum class TurnHint(val event: HaryanviLines.Event, val prefix: Boolean) {
    PetrolPumpLeft(HaryanviLines.Event.NavPumpLeft, true),
    CngPumpRight(HaryanviLines.Event.NavCngPumpRight, true),
    LaneLeft(HaryanviLines.Event.NavLaneLeft, false),
    RoundaboutSecondExit(HaryanviLines.Event.NavRoundaboutSecondExit, true),
    RoundaboutThirdExit(HaryanviLines.Event.NavRoundaboutThirdExit, true),
    HighwayEnter(HaryanviLines.Event.NavHighwayEnter, true),
    HighwayExitRight(HaryanviLines.Event.NavHighwayExitRight, false),

    /** The arriving prompt, destination on the right: the line takes the metres ("%1$d मीटर पै"). */
    ArrivingRight(HaryanviLines.Event.NavArrivingRight, false),
}

/** Which [TurnHint] fits a route step, from OSRM's step data. Plain Kotlin, unit-tested. */
object NavHints {

    /** [landmark]: a pump hint found for this step ([PumpLandmarks]), if any. First match wins. */
    fun forStep(step: RouteStep, landmark: TurnHint? = null): TurnHint? {
        val type = step.osrmType.lowercase()
        val mod = step.osrmModifier?.lowercase()
        return when {
            landmark != null -> landmark
            (step.maneuver == Maneuver.Left || step.maneuver == Maneuver.SharpLeft) && Lanes.leftOnly(step.lanes) -> TurnHint.LaneLeft
            step.maneuver == Maneuver.Roundabout && step.exitNumber == 2 -> TurnHint.RoundaboutSecondExit
            step.maneuver == Maneuver.Roundabout && step.exitNumber == 3 -> TurnHint.RoundaboutThirdExit
            step.maneuver == Maneuver.Ramp && type == "on ramp" -> TurnHint.HighwayEnter
            step.maneuver == Maneuver.Exit && type == "off ramp" && mod?.contains("right") == true -> TurnHint.HighwayExitRight
            step.maneuver == Maneuver.Arrive && mod?.contains("right") == true -> TurnHint.ArrivingRight
            else -> null
        }
    }
}

/**
 * Pump landmarks for turns, from the offline fuel snapshot (assets/fuel.bin, OpenStreetMap): a
 * left turn with a petrol pump within [RADIUS_M] of the corner ("पेट्रोल पंप तै उल्टे हाथ"), a
 * right turn with a CNG pump within [RADIUS_M] that the car passes before turning ("सीएनजी पंप कै
 * बाद सुल्टे हाथ"). Plain Kotlin, unit-tested.
 */
object PumpLandmarks {
    const val RADIUS_M = 60.0

    /**
     * Step index → hint, for every left / right turn of [route] that has one. [near] lists the
     * stations within [RADIUS_M] of a point (bucket-accurate is fine: distances are re-checked).
     */
    fun find(route: Route, near: (LatLon) -> List<FuelStation>): Map<Int, TurnHint> {
        val out = HashMap<Int, TurnHint>()
        route.steps.forEachIndexed { i, step ->
            if (step.maneuver != Maneuver.Left && step.maneuver != Maneuver.Right) return@forEachIndexed
            val close = near(step.location).filter { Geo.distanceM(LatLon(it.lat, it.lon), step.location) <= RADIUS_M }
            val hint = when (step.maneuver) {
                Maneuver.Left -> if (close.any { it.has(FuelFlags.PETROL) }) TurnHint.PetrolPumpLeft else null
                else -> if (close.any { it.has(FuelFlags.CNG) && before(route, step, LatLon(it.lat, it.lon)) }) TurnHint.CngPumpRight else null
            }
            if (hint != null) out[i] = hint
        }
        return out
    }

    /** True when [p] lies along the road leading up to [step] (the car passes it, then turns). */
    internal fun before(route: Route, step: RouteStep, p: LatLon): Boolean {
        val pts = route.points
        var bestAlong = Double.NaN
        var bestD = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val a = route.cumM[i]
            val b = route.cumM[i + 1]
            if (b < step.alongM - WINDOW_M || a > step.alongM + WINDOW_M) continue
            val (t, d) = Geo.project(p, pts[i], pts[i + 1])
            if (d < bestD) {
                bestD = d
                bestAlong = a + t * (b - a)
            }
        }
        return !bestAlong.isNaN() && bestAlong < step.alongM - CORNER_M
    }

    private const val WINDOW_M = 150.0

    /** Right at the corner counts as neither before nor after. */
    private const val CORNER_M = 5.0
}

/** Something to say between turns (Car Hub's navigation, Haryanvi style only). */
sealed class TripCue {
    /** Remaining distance crossed a milestone: [km] is what is left, rounded. */
    data class DistanceLeft(val km: Int) : TripCue()
    data object Halfway : TripCue()
    data object TenMinutesLeft : TripCue()

    /** A fork straight on where only the middle lane(s) go on ([Route.laneSplitsM]). */
    data object LaneSplit : TripCue()
}

/**
 * When to say the [TripCue]s. Each one at most once per trip; never while a turn prompt is close
 * ([QUIET_TURN_M]) or a line is being said (`canSpeak` false); progress cues at least
 * [MIN_GAP_MS] apart, and when two are due at once only the first is said (halfway, then ten
 * minutes, then kilometres). A cue that couldn't be said in time is dropped, never said late.
 * Plain Kotlin, unit-tested.
 */
class TripCues {
    private var started = false
    private var totalM = 0.0
    private var totalS = 0.0
    private var lastCueMs = Long.MIN_VALUE
    private var halfwayArmed = false
    private var halfwayDone = false
    private var tenArmed = false
    private var tenDone = false
    private val armed = HashSet<Int>()
    private val done = HashSet<Int>()
    private var splits: List<Double> = emptyList()
    private val splitsDone = HashSet<Int>()

    /** A new trip: its first route's length and OSRM time. */
    fun startTrip(totalM: Double, totalS: Double) {
        reset()
        started = true
        this.totalM = totalM
        this.totalS = totalS
    }

    /** Every route of the trip (the first and each reroute). */
    fun onRoute(laneSplitsM: List<Double>) {
        splits = laneSplitsM
        splitsDone.clear()
    }

    fun reset() {
        started = false
        totalM = 0.0
        totalS = 0.0
        lastCueMs = Long.MIN_VALUE
        halfwayArmed = false
        halfwayDone = false
        tenArmed = false
        tenDone = false
        armed.clear()
        done.clear()
        splits = emptyList()
        splitsDone.clear()
    }

    /**
     * One GPS fix on the route. [toTurnM]: metres to the next manoeuvre other than arriving
     * ([Double.MAX_VALUE] when only the destination is ahead). Returns the cue to say now, if any.
     */
    fun onProgress(nowMs: Long, alongM: Double, remainingM: Double, remainingS: Double, toTurnM: Double, canSpeak: Boolean): TripCue? {
        if (!started) return null

        // Lane split: a turn-like hint, said in its window whatever the progress cues did.
        var split: TripCue? = null
        splits.forEachIndexed { i, at ->
            if (i in splitsDone) return@forEachIndexed
            val ahead = at - alongM
            when {
                ahead <= LANE_MIN_M -> splitsDone += i
                ahead <= LANE_MAX_M && split == null && canSpeak -> {
                    splitsDone += i
                    split = TripCue.LaneSplit
                }
            }
        }
        split?.let { return it }

        // Arm each cue once the car has been clearly before it, so a short trip (or a reroute
        // that starts past a milestone) never says it.
        val half = totalM / 2
        if (totalM >= HALFWAY_MIN_M && remainingM > half + ARM_M) halfwayArmed = true
        if (totalS >= TEN_MIN_TRIP_S && remainingS > TEN_MIN_S + TEN_ARM_S) tenArmed = true
        for (km in MILESTONES_KM) if (remainingM > km * 1000.0 + ARM_M) armed += km

        // Due now, or missed (then dropped).
        val halfwayDue = halfwayArmed && !halfwayDone && remainingM <= half &&
            remainingM >= half - max(HALFWAY_SLACK_M, totalM * 0.1)
        if (halfwayArmed && !halfwayDone && remainingM <= half && !halfwayDue) halfwayDone = true
        val tenDue = tenArmed && !tenDone && remainingS <= TEN_MIN_S && remainingS >= TEN_MIN_S - TEN_SLACK_S
        if (tenArmed && !tenDone && remainingS <= TEN_MIN_S && !tenDue) tenDone = true
        val kmDue = ArrayList<Int>()
        for (km in MILESTONES_KM) {
            if (km !in armed || km in done) continue
            val at = km * 1000.0
            if (remainingM > at) continue
            if (remainingM >= at - max(KM_SLACK_MIN_M, at * KM_SLACK_FRACTION)) kmDue += km else done += km
        }
        if (!halfwayDue && !tenDue && kmDue.isEmpty()) return null

        if (!canSpeak || toTurnM < QUIET_TURN_M) return null
        if (lastCueMs != Long.MIN_VALUE && nowMs - lastCueMs < MIN_GAP_MS) return null

        lastCueMs = nowMs
        if (halfwayDue) halfwayDone = true
        if (tenDue) tenDone = true
        done += kmDue
        return when {
            halfwayDue -> TripCue.Halfway
            tenDue -> TripCue.TenMinutesLeft
            else -> TripCue.DistanceLeft(max(1, (remainingM / 1000.0).roundToInt()))
        }
    }

    companion object {
        /** Kilometres-left milestones, largest first. */
        val MILESTONES_KM = listOf(20, 10, 5, 2)

        /** "Halfway" only on trips at least this long. */
        const val HALFWAY_MIN_M = 10_000.0

        /** "Ten minutes left" only on trips that take at least this long (seconds). */
        const val TEN_MIN_TRIP_S = 20 * 60.0
        const val TEN_MIN_S = 10 * 60.0

        /** Before the moment by this much, the cue is armed. */
        const val ARM_M = 500.0
        const val TEN_ARM_S = 60.0

        /** Still worth saying this far past the moment; later it is dropped. */
        const val HALFWAY_SLACK_M = 1_000.0
        const val TEN_SLACK_S = 120.0
        const val KM_SLACK_MIN_M = 300.0
        const val KM_SLACK_FRACTION = 0.2

        /** No progress cue when the next turn is this close: its own prompt comes first. */
        const val QUIET_TURN_M = 800.0

        /** Progress cues at least this far apart. */
        const val MIN_GAP_MS = 90_000L

        /** The lane-split line is said this far before the split. */
        const val LANE_MAX_M = 400.0
        const val LANE_MIN_M = 80.0
    }
}
