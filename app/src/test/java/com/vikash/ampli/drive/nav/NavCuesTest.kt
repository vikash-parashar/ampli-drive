package com.vikash.ampli.drive.nav

import com.vikash.ampli.drive.fuel.FuelFlags
import com.vikash.ampli.drive.fuel.FuelStation
import com.vikash.ampli.drive.nav.route.Geo
import com.vikash.ampli.drive.nav.route.LatLon
import com.vikash.ampli.drive.nav.route.Lanes
import com.vikash.ampli.drive.nav.route.Route
import com.vikash.ampli.drive.nav.route.RouteStep
import com.vikash.ampli.drive.nav.route.TimedSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** Round-3 Haryanvi navigation triggers: trip cues, turn hints, pump landmarks, lanes. */
class NavCuesTest {

    // ------------------------------------------------------------------ TripCues

    /**
     * Drives a trip at 60 km/h (50 m every 3 s) from [totalM] down to 0 and returns the cues said.
     * [remainingS] gives the OSRM time left for a distance left.
     */
    private fun drive(
        cues: TripCues,
        totalM: Double,
        startM: Double = totalM,
        remainingS: (Double) -> Double = { it / 16.0 },
        toTurn: (Double) -> Double = { Double.MAX_VALUE },
        canSpeak: (Double) -> Boolean = { true },
    ): List<Pair<Double, TripCue>> {
        val out = ArrayList<Pair<Double, TripCue>>()
        var t = 0L
        var left = startM
        while (left >= 0) {
            cues.onProgress(t, totalM - left, left, remainingS(left), toTurn(left), canSpeak(left))?.let { out += left to it }
            left -= 50.0
            t += 3_000L
        }
        return out
    }

    @Test
    fun kilometresLeftAndHalfwayOnceEach() {
        val cues = TripCues()
        cues.startTrip(25_000.0, 900.0) // 15 minutes: no "ten minutes left"
        val said = drive(cues, 25_000.0, remainingS = { it / 30.0 }).map { it.second }
        assertEquals(
            listOf(TripCue.DistanceLeft(20), TripCue.Halfway, TripCue.DistanceLeft(10), TripCue.DistanceLeft(5), TripCue.DistanceLeft(2)),
            said,
        )
    }

    @Test
    fun shortTripStaysQuiet() {
        val cues = TripCues()
        cues.startTrip(1_500.0, 300.0)
        assertTrue(drive(cues, 1_500.0).isEmpty())
        // Not started: nothing at all.
        val idle = TripCues()
        assertNull(idle.onProgress(0, 0.0, 5_000.0, 600.0, Double.MAX_VALUE, true))
    }

    @Test
    fun aMilestoneTheTripStartsAtIsNeverSaid() {
        val cues = TripCues()
        cues.startTrip(4_800.0, 600.0)
        assertEquals(listOf<TripCue>(TripCue.DistanceLeft(2)), drive(cues, 4_800.0).map { it.second })
    }

    @Test
    fun halfwayAndAMilestoneTogetherSayOnlyHalfway() {
        val cues = TripCues()
        cues.startTrip(20_000.0, 900.0)
        assertEquals(
            listOf(TripCue.Halfway, TripCue.DistanceLeft(5), TripCue.DistanceLeft(2)),
            drive(cues, 20_000.0).map { it.second },
        )
    }

    @Test
    fun quietNearATurnAndDroppedWhenTooLate() {
        val cues = TripCues()
        cues.startTrip(8_000.0, 600.0)
        // A turn prompt is close from 5.2 km to 3.9 km: the 5 km cue would be too late after it.
        val said = drive(cues, 8_000.0, toTurn = { if (it in 3_900.0..5_200.0) 500.0 else Double.MAX_VALUE })
        assertEquals(listOf<TripCue>(TripCue.DistanceLeft(2)), said.map { it.second })
    }

    @Test
    fun waitsForTheVoiceThenSaysTheRoundedDistance() {
        val cues = TripCues()
        cues.startTrip(8_000.0, 600.0)
        val said = drive(cues, 8_000.0, canSpeak = { it !in 4_600.0..5_000.0 })
        assertEquals(TripCue.DistanceLeft(5), said.first().second)
        assertEquals(4_550.0, said.first().first, 0.1)
    }

    @Test
    fun aMilestoneIsNeverSaidTwice() {
        val cues = TripCues()
        cues.startTrip(30_000.0, 900.0)
        var t = 0L
        assertNull(cues.onProgress(t, 0.0, 30_000.0, 900.0, Double.MAX_VALUE, true))
        t += 1_000
        assertEquals(TripCue.DistanceLeft(20), cues.onProgress(t, 10_000.0, 20_000.0, 600.0, Double.MAX_VALUE, true))
        // A reroute that makes the trip longer again: 20 km is never said twice.
        t += 120_000
        assertNull(cues.onProgress(t, 0.0, 21_000.0, 650.0, Double.MAX_VALUE, true))
        t += 120_000
        assertNull(cues.onProgress(t, 1_000.0, 19_950.0, 600.0, Double.MAX_VALUE, true))
    }

    @Test
    fun cuesAreSpacedOut() {
        val cues = TripCues()
        cues.startTrip(30_000.0, 3_000.0)
        assertNull(cues.onProgress(0, 0.0, 30_000.0, 3_000.0, Double.MAX_VALUE, true))
        assertEquals(TripCue.DistanceLeft(20), cues.onProgress(1_000, 10_000.0, 20_000.0, 2_000.0, Double.MAX_VALUE, true))
        // Halfway is due 30 s later: too soon, it waits.
        assertNull(cues.onProgress(31_000, 15_000.0, 14_900.0, 1_500.0, Double.MAX_VALUE, true))
        assertEquals(TripCue.Halfway, cues.onProgress(100_000, 15_500.0, 14_500.0, 1_450.0, Double.MAX_VALUE, true))
    }

    @Test
    fun tenMinutesLeftOnALongTrip() {
        val cues = TripCues()
        cues.startTrip(9_000.0, 1_800.0) // slow, 30 minutes, under 10 km: no halfway
        val said = ArrayList<TripCue>()
        var s = 1_800.0
        var t = 0L
        while (s >= 0) {
            cues.onProgress(t, 0.0, 9_000.0, s, Double.MAX_VALUE, true)?.let { said += it }
            s -= 10.0
            t += 10_000L
        }
        assertEquals(listOf<TripCue>(TripCue.TenMinutesLeft), said)
        // A short trip never says it.
        val short = TripCues()
        short.startTrip(9_000.0, 900.0)
        assertNull(short.onProgress(0, 0.0, 9_000.0, 900.0, Double.MAX_VALUE, true))
        assertNull(short.onProgress(1, 0.0, 9_000.0, 590.0, Double.MAX_VALUE, true))
    }

    @Test
    fun laneSplitSaidOnceInItsWindow() {
        val cues = TripCues()
        cues.startTrip(3_000.0, 300.0)
        cues.onRoute(listOf(1_000.0, 2_000.0))
        assertNull(cues.onProgress(0, 500.0, 2_500.0, 250.0, Double.MAX_VALUE, true))
        assertEquals(TripCue.LaneSplit, cues.onProgress(1, 650.0, 2_350.0, 240.0, 300.0, true))
        assertNull(cues.onProgress(2, 700.0, 2_300.0, 230.0, 300.0, true))
        // The second split: the voice is busy all through its window, so it is dropped.
        assertNull(cues.onProgress(3, 1_700.0, 1_300.0, 130.0, Double.MAX_VALUE, false))
        assertNull(cues.onProgress(4, 1_950.0, 1_050.0, 105.0, Double.MAX_VALUE, true))
        // A new route brings its own splits.
        cues.onRoute(listOf(2_300.0))
        assertEquals(TripCue.LaneSplit, cues.onProgress(5, 2_000.0, 1_000.0, 100.0, Double.MAX_VALUE, true))
        cues.reset()
        cues.onRoute(listOf(2_300.0))
        assertNull(cues.onProgress(6, 2_000.0, 1_000.0, 100.0, Double.MAX_VALUE, true))
    }

    // ------------------------------------------------------------------ Turn hints and lanes

    private val here = LatLon(28.4595, 77.0266)

    private fun step(m: Maneuver, type: String, mod: String?, exit: Int? = null, lanes: List<Boolean>? = null) =
        RouteStep(m, exit, here, null, null, 100.0, type, mod, lanes)

    @Test
    fun hintsFromOsrmStepData() {
        assertEquals(TurnHint.LaneLeft, NavHints.forStep(step(Maneuver.Left, "turn", "left", lanes = listOf(true, false, false))))
        assertNull(NavHints.forStep(step(Maneuver.Left, "turn", "left", lanes = listOf(true, true))))
        assertNull(NavHints.forStep(step(Maneuver.Left, "turn", "left")))
        assertNull(NavHints.forStep(step(Maneuver.Right, "turn", "right", lanes = listOf(true, false))))
        // A pump landmark wins over the lanes.
        assertEquals(TurnHint.PetrolPumpLeft, NavHints.forStep(step(Maneuver.Left, "turn", "left", lanes = listOf(true, false)), TurnHint.PetrolPumpLeft))
        assertEquals(TurnHint.RoundaboutSecondExit, NavHints.forStep(step(Maneuver.Roundabout, "roundabout", "right", exit = 2)))
        assertEquals(TurnHint.RoundaboutThirdExit, NavHints.forStep(step(Maneuver.Roundabout, "rotary", "straight", exit = 3)))
        assertNull(NavHints.forStep(step(Maneuver.Roundabout, "roundabout", "right", exit = 1)))
        assertNull(NavHints.forStep(step(Maneuver.Roundabout, "roundabout", "right")))
        assertEquals(TurnHint.HighwayEnter, NavHints.forStep(step(Maneuver.Ramp, "on ramp", "slight left")))
        assertNull(NavHints.forStep(step(Maneuver.Flyover, "on ramp", "slight left")))
        assertEquals(TurnHint.HighwayExitRight, NavHints.forStep(step(Maneuver.Exit, "off ramp", "slight right")))
        assertNull(NavHints.forStep(step(Maneuver.Exit, "off ramp", "slight left")))
        assertEquals(TurnHint.ArrivingRight, NavHints.forStep(step(Maneuver.Arrive, "arrive", "right")))
        assertNull(NavHints.forStep(step(Maneuver.Arrive, "arrive", "left")))
        assertNull(NavHints.forStep(step(Maneuver.Arrive, "arrive", null)))
    }

    @Test
    fun laneShapes() {
        assertTrue(Lanes.leftOnly(listOf(true, false)))
        assertTrue(Lanes.leftOnly(listOf(true, true, false)))
        assertFalse(Lanes.leftOnly(listOf(true, false, true)))
        assertFalse(Lanes.leftOnly(listOf(false, true)))
        assertFalse(Lanes.leftOnly(listOf(true)))
        assertFalse(Lanes.leftOnly(null))
        assertTrue(Lanes.middleOnly(listOf(false, true, false)))
        assertTrue(Lanes.middleOnly(listOf(false, true, true, false)))
        assertFalse(Lanes.middleOnly(listOf(true, true, false)))
        assertFalse(Lanes.middleOnly(listOf(false, false, false)))
        assertFalse(Lanes.middleOnly(listOf(false, true)))
    }

    // ------------------------------------------------------------------ Pump landmarks

    private fun east(p: LatLon, m: Double) = LatLon(p.lat, p.lon + m / (111_195.0 * cos(Math.toRadians(p.lat))))
    private fun north(p: LatLon, m: Double) = LatLon(p.lat + m / 111_195.0, p.lon)

    /** 300 m east, then a turn ([turn]: north for left, south for right) and 300 m on. */
    private fun route(turn: Maneuver): Route {
        val a = here
        val b = east(a, 300.0)
        val c = north(b, if (turn == Maneuver.Left) 300.0 else -300.0)
        val pts = listOf(a, east(a, 150.0), b, north(b, if (turn == Maneuver.Left) 150.0 else -150.0), c)
        val geo = Route(pts, emptyList(), emptyList(), 0.0, 0.0, null)
        val steps = listOf(
            RouteStep(turn, null, b, null, null, geo.cumM[2], "turn", if (turn == Maneuver.Left) "left" else "right"),
            RouteStep(Maneuver.Arrive, null, c, null, null, geo.lengthM, "arrive", null),
        )
        return Route(pts, steps, listOf(TimedSpan(0.0, geo.lengthM, 60.0)), geo.lengthM, 60.0, null)
    }

    private fun station(p: LatLon, flags: Int) = FuelStation(0, p.lat, p.lon, flags, "")

    @Test
    fun petrolPumpAtALeftTurn() {
        val r = route(Maneuver.Left)
        val corner = r.steps[0].location
        val pump = station(north(east(corner, 20.0), 15.0), FuelFlags.PETROL or FuelFlags.DIESEL or FuelFlags.ASSUMED)
        assertTrue(Geo.distanceM(LatLon(pump.lat, pump.lon), corner) < PumpLandmarks.RADIUS_M)
        assertEquals(mapOf(0 to TurnHint.PetrolPumpLeft), PumpLandmarks.find(r) { listOf(pump) })
        // Too far from the corner, or only EV charging: nothing.
        assertTrue(PumpLandmarks.find(r) { listOf(station(east(corner, 90.0), FuelFlags.PETROL)) }.isEmpty())
        assertTrue(PumpLandmarks.find(r) { listOf(station(east(corner, 20.0), FuelFlags.EV)) }.isEmpty())
        // A CNG-only pump at a left turn has no line.
        assertTrue(PumpLandmarks.find(r) { listOf(station(east(corner, 20.0), FuelFlags.CNG)) }.isEmpty())
    }

    @Test
    fun cngPumpBeforeARightTurn() {
        val r = route(Maneuver.Right)
        val corner = r.steps[0].location
        // 35 m before the corner, beside the road: passed, then the turn.
        val before = station(north(east(corner, -35.0), 10.0), FuelFlags.CNG)
        assertEquals(mapOf(0 to TurnHint.CngPumpRight), PumpLandmarks.find(r) { listOf(before) })
        // On the road after the turn: "after the CNG pump" would be wrong.
        val after = station(east(north(corner, -35.0), 10.0), FuelFlags.CNG)
        assertTrue(PumpLandmarks.find(r) { listOf(after) }.isEmpty())
        // A petrol pump at a right turn has no line.
        assertTrue(PumpLandmarks.find(r) { listOf(station(east(corner, -35.0), FuelFlags.PETROL)) }.isEmpty())
    }

    @Test
    fun turnsGetTheirHintInThePromptAhead() {
        // The in-app planner keeps the hint on the turn it hands over.
        val planner = NavPromptPlanner()
        val far = planner.onTurn(ParsedTurn(Maneuver.Left, 500, null, "k", TurnHint.PetrolPumpLeft))!!
        assertFalse(far.now)
        assertEquals(TurnHint.PetrolPumpLeft, far.turn.hint)
    }
}
