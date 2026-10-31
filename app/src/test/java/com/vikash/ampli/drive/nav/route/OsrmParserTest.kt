package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmParserTest {

    private fun sample(): String =
        javaClass.classLoader!!.getResource("osrm/route_sample.json")!!.readText()

    private fun route(): Route = (OsrmParser.parse(sample()) as OsrmParse.Ok).route

    @Test
    fun readsGeometryAndTotals() {
        val r = route()
        assertEquals(10, r.points.size)
        assertEquals(LatLon(28.4595, 77.0266), r.points.first())
        assertEquals(LatLon(28.4635, 77.0326), r.destination)
        assertEquals(947.8, r.distanceM, 0.01)
        assertEquals(130.2, r.durationS, 0.01)
        // Geometry length is close to what OSRM reports.
        assertEquals(r.distanceM, r.lengthM, 30.0)
    }

    @Test
    fun foldsDepartAndNewNameIntoAnnouncedSteps() {
        val steps = route().steps
        assertEquals(listOf(Maneuver.Left, Maneuver.Roundabout, Maneuver.Flyover, Maneuver.Arrive), steps.map { it.maneuver })
        assertEquals(2, steps[1].exitNumber)
        assertEquals("Old Delhi Road", steps[0].label)
        assertEquals("Sohna Road", steps[1].label)
        assertEquals("SH 13", steps[1].ref)
        assertEquals("fork", steps[2].osrmType)
        assertEquals("slight left", steps[2].osrmModifier)
    }

    @Test
    fun stepsSitOnTheGeometryInOrder() {
        val r = route()
        val along = r.steps.map { it.alongM }
        assertEquals(along.sorted(), along)
        // The left turn is at the third vertex.
        assertEquals(r.cumM[2], r.steps[0].alongM, 0.01)
        assertEquals(r.cumM[5], r.steps[1].alongM, 0.01)
        assertEquals(r.cumM[7], r.steps[2].alongM, 0.01)
        assertEquals(r.lengthM, r.steps.last().alongM, 0.01)
    }

    @Test
    fun readsSpeedLimitsPerSegment() {
        val limits = route().maxspeedKmh!!
        assertEquals(9, limits.size)
        assertEquals(50, limits[0])
        assertNull(limits[2])
        assertEquals(64, limits[5]) // 40 mph
        assertNull(limits[6]) // "none"
        assertEquals(60, limits[8])
    }

    @Test
    fun timedSpansCoverTheRoute() {
        val r = route()
        assertEquals(6, r.spans.size)
        assertEquals(130.2, r.spans.sumOf { it.durationS }, 0.01)
        assertEquals(r.lengthM, r.spans.sumOf { it.distanceM }, 0.01)
    }

    @Test
    fun errorsAreReported() {
        val e = OsrmParser.parse("""{"code":"NoRoute","message":"Impossible route between points","routes":[]}""")
        assertTrue(e is OsrmParse.Error)
        assertEquals("NoRoute", (e as OsrmParse.Error).code)
        assertTrue(OsrmParser.parse("<html>502</html>") is OsrmParse.Error)
        assertTrue(OsrmParser.parse("") is OsrmParse.Error)
    }

    @Test
    fun routeWithoutAnnotationsHasNoLimits() {
        val body = """{"code":"Ok","routes":[{"geometry":{"type":"LineString","coordinates":[[77.0,28.0],[77.001,28.0]]},
            "legs":[{"steps":[{"maneuver":{"type":"depart","location":[77.0,28.0]},"name":"A","distance":98,"duration":10},
            {"maneuver":{"type":"arrive","location":[77.001,28.0]},"name":"A","distance":0,"duration":0}]}],
            "distance":98,"duration":10}]}"""
        val r = (OsrmParser.parse(body) as OsrmParse.Ok).route
        assertNull(r.maxspeedKmh)
        assertEquals(listOf(Maneuver.Arrive), r.steps.map { it.maneuver })
    }

    @Test
    fun miniJsonHandlesEscapes() {
        val v = MiniJson.parse("""{"a":"x\"y\\z\/र","b":[1,-2.5e2,true,false,null],"c":{}}""").obj()!!
        assertEquals("x\"y\\z/र", v["a"])
        assertEquals(listOf(1.0, -250.0, true, false, null), v["b"])
        assertEquals(emptyMap<String, Any?>(), v["c"])
    }

    /** A small OSRM answer with lane data: a left turn from the left lane, then a three-way split. */
    private val lanesBody = """
        {"code": "Ok", "routes": [{"distance": 560.0, "duration": 60.0,
          "geometry": {"type": "LineString", "coordinates": [[77.0, 28.0], [77.001, 28.0], [77.001, 28.001], [77.001, 28.002], [77.001, 28.003]]},
          "legs": [{"steps": [
            {"maneuver": {"type": "depart", "location": [77.0, 28.0]}, "name": "A Road", "distance": 98.0, "duration": 10.0,
             "intersections": [{"location": [77.0, 28.0]}]},
            {"maneuver": {"type": "turn", "modifier": "left", "location": [77.001, 28.0]}, "name": "B Road", "distance": 111.0, "duration": 15.0,
             "intersections": [{"location": [77.001, 28.0], "lanes": [{"indications": ["left"], "valid": true}, {"indications": ["straight"], "valid": false}]}]},
            {"maneuver": {"type": "fork", "modifier": "straight", "location": [77.001, 28.001]}, "name": "B Road", "distance": 222.0, "duration": 25.0,
             "intersections": [{"location": [77.001, 28.001], "lanes": [{"indications": ["slight left"], "valid": false}, {"indications": ["straight"], "valid": true}, {"indications": ["slight right"], "valid": false}]}]},
            {"maneuver": {"type": "arrive", "modifier": "right", "location": [77.001, 28.003]}, "name": "B Road", "distance": 0.0, "duration": 0.0,
             "intersections": [{"location": [77.001, 28.003]}]}
          ]}]}]}
    """.trimIndent()

    @Test
    fun readsLanesAndLaneSplits() {
        val r = (OsrmParser.parse(lanesBody) as OsrmParse.Ok).route
        assertEquals(listOf(Maneuver.Left, Maneuver.Arrive), r.steps.map { it.maneuver })
        assertEquals(listOf(true, false), r.steps[0].lanes)
        assertNull(r.steps[1].lanes)
        assertEquals("right", r.steps[1].osrmModifier)
        // The fork straight on is no manoeuvre, but its middle-lane split is kept.
        assertEquals(1, r.laneSplitsM.size)
        assertEquals(r.cumM[2], r.laneSplitsM[0], 0.01)
        // The sample answer has no lane data.
        assertTrue(route().steps.all { it.lanes == null })
        assertTrue(route().laneSplitsM.isEmpty())
    }
}
