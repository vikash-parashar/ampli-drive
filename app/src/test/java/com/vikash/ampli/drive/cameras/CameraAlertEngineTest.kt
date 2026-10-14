package com.vikash.ampli.drive.cameras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Ahead" geometry and once-per-pass debounce. Run with ./gradlew testDebugUnitTest. */
class CameraAlertEngineTest {

    // A camera on a north-south road near Bengaluru; the car drives north (heading 0°).
    private val camLat = 12.9716
    private val camLon = 77.5946
    private val metresPerDegLat = 111_195.0

    private fun cam(id: String = "n1", maxspeed: Int? = 60, dir: Double? = null) =
        SpeedCamera(id, camLat, camLon, maxspeed, dir, "speed_camera")

    /** A fix [metresSouth] south of the camera (negative = already past it, to the north). */
    private fun fix(metresSouth: Double, speed: Double = 50.0, heading: Double? = 0.0) =
        CameraAlertEngine.Fix(camLat - metresSouth / metresPerDegLat, camLon, speed, heading)

    private fun ahead(f: CameraAlertEngine.Fix, c: SpeedCamera): Boolean {
        val d = CameraGeometry.distanceM(f.lat, f.lon, c.lat, c.lon)
        val b = CameraGeometry.bearingDeg(f.lat, f.lon, c.lat, c.lon)
        return CameraGeometry.isAhead(d, b, f.heading, f.speedKmh, c.dir)
    }

    @Test
    fun distanceAndBearing() {
        val f = fix(1000.0)
        assertEquals(1000.0, CameraGeometry.distanceM(f.lat, f.lon, camLat, camLon), 2.0)
        assertEquals(0.0, CameraGeometry.bearingDeg(f.lat, f.lon, camLat, camLon), 0.01)
        assertEquals(20.0, CameraGeometry.angleDiff(350.0, 10.0), 1e-9)
        assertEquals(180.0, CameraGeometry.angleDiff(0.0, 180.0), 1e-9)
    }

    @Test
    fun cameraInFrontIsAhead() {
        assertTrue(ahead(fix(1000.0), cam()))
        // Heading 25° off the road still counts (within ±30°).
        assertTrue(ahead(fix(1000.0, heading = 25.0), cam()))
    }

    @Test
    fun cameraBehindOrBesideIsNotAhead() {
        assertFalse(ahead(fix(-300.0), cam())) // already passed
        assertFalse(ahead(fix(1000.0, heading = 90.0), cam())) // driving east
        assertFalse(ahead(fix(1000.0, heading = 40.0), cam())) // outside ±30°
    }

    @Test
    fun cameraBeyondLookaheadIsNotAhead() {
        assertFalse(ahead(fix(1600.0), cam()))
    }

    @Test
    fun cameraDirectionMustMatchTravel() {
        assertTrue(ahead(fix(800.0), cam(dir = 10.0))) // faces our way (within ±45°)
        assertFalse(ahead(fix(800.0), cam(dir = 180.0))) // enforces the other carriageway
        assertFalse(ahead(fix(800.0), cam(dir = 60.0)))
    }

    @Test
    fun slowOrNoBearingFallsBackTo150m() {
        assertTrue(ahead(fix(120.0, speed = 5.0), cam()))
        assertFalse(ahead(fix(400.0, speed = 5.0), cam()))
        assertTrue(ahead(fix(-100.0, heading = null), cam())) // any direction when bearing is unknown
        assertFalse(ahead(fix(300.0, heading = null), cam()))
    }

    @Test
    fun announcesOncePerPassThenClears() {
        val engine = CameraAlertEngine(CameraAlertEngine.Config(alertDistanceM = 500))
        val cams = listOf(cam())
        var announces = 0
        var clears = 0
        var shown = 0
        var d = 1400.0
        while (d > -400.0) {
            val r = engine.update(fix(d), cams)
            announces += r.events.count { it is CameraAlertEngine.Event.Announce }
            clears += r.events.count { it is CameraAlertEngine.Event.Cleared }
            if (r.target != null) {
                shown++
                assertTrue(r.target!!.distanceM <= 500.0)
            }
            if (d > 520.0) assertNull(r.target)
            d -= 20.0
        }
        assertEquals(1, announces)
        assertEquals(1, clears)
        assertTrue(shown > 10)
    }

    @Test
    fun firstAnnouncementIsRoundedToTheAlertDistance() {
        val engine = CameraAlertEngine(CameraAlertEngine.Config(alertDistanceM = 500))
        assertTrue(engine.update(fix(510.0), listOf(cam())).events.isEmpty())
        val e = engine.update(fix(490.0), listOf(cam())).events.single() as CameraAlertEngine.Event.Announce
        assertEquals(500, e.roundedMeters)
    }

    @Test
    fun gpsJitterDoesNotRepeatTheAnnouncement() {
        val engine = CameraAlertEngine()
        val cams = listOf(cam())
        assertEquals(1, engine.update(fix(450.0), cams).events.size)
        // A bad bearing hides the camera for one fix, then it is back: no second announcement.
        assertNull(engine.update(fix(430.0, heading = 90.0), cams).target)
        val back = engine.update(fix(410.0), cams)
        assertNotNull(back.target)
        assertTrue(back.events.none { it is CameraAlertEngine.Event.Announce })
    }

    @Test
    fun alertsAgainAfterDrivingAway() {
        val engine = CameraAlertEngine()
        val cams = listOf(cam())
        engine.update(fix(400.0), cams)
        engine.update(fix(-2500.0), cams) // far past: the pass is forgotten
        val again = engine.update(fix(400.0), cams)
        assertEquals(1, again.events.count { it is CameraAlertEngine.Event.Announce })
    }

    @Test
    fun overLimitSaysSlowDownAndBeepsOnceAt150m() {
        val engine = CameraAlertEngine()
        val cams = listOf(cam(maxspeed = 60))
        val first = engine.update(fix(450.0, speed = 80.0), cams)
        assertTrue(first.target!!.overLimit)
        assertEquals(1, first.events.count { it is CameraAlertEngine.Event.Announce })
        assertEquals(1, first.events.count { it is CameraAlertEngine.Event.SlowDown })
        assertTrue(first.events.none { it is CameraAlertEngine.Event.Beep })

        var beeps = 0
        var d = 400.0
        while (d > 0.0) {
            beeps += engine.update(fix(d, speed = 80.0), cams).events.count { it is CameraAlertEngine.Event.Beep }
            d -= 25.0
        }
        assertEquals(1, beeps)
    }

    @Test
    fun noBeepWithinTheLimit() {
        val engine = CameraAlertEngine()
        val cams = listOf(cam(maxspeed = 60))
        var d = 450.0
        while (d > 0.0) {
            val r = engine.update(fix(d, speed = 55.0), cams)
            assertFalse(r.target!!.overLimit)
            assertTrue(r.events.none { it is CameraAlertEngine.Event.Beep || it is CameraAlertEngine.Event.SlowDown })
            d -= 25.0
        }
    }

    @Test
    fun onlyOverLimitStaysQuietUntilSpeeding() {
        val engine = CameraAlertEngine(CameraAlertEngine.Config(alertDistanceM = 500, onlyOverLimit = true))
        val cams = listOf(cam(maxspeed = 60))
        val calm = engine.update(fix(450.0, speed = 55.0), cams)
        assertNotNull(calm.target)
        assertTrue(calm.events.isEmpty())
        val fast = engine.update(fix(400.0, speed = 75.0), cams)
        assertEquals(1, fast.events.count { it is CameraAlertEngine.Event.Announce })
    }

    @Test
    fun picksTheNearestCameraAhead() {
        val near = cam("near")
        val far = SpeedCamera("far", camLat + 300 / metresPerDegLat, camLon, 80, null, "speed_camera")
        val behind = SpeedCamera("behind", camLat - 900 / metresPerDegLat, camLon, 40, null, "speed_camera")
        val r = CameraAlertEngine().update(fix(400.0), listOf(far, behind, near))
        assertEquals("near", r.target!!.camera.id)
    }

    @Test
    fun gridIndexFindsNearbyCamerasOnly() {
        val index = CameraIndex(
            listOf(
                cam("a"),
                SpeedCamera("b", camLat + 0.01, camLon + 0.01, null, null, "speed_camera"),
                SpeedCamera("mumbai", 19.07, 72.87, null, null, "speed_camera"),
            ),
        )
        val ids = index.near(camLat - 0.005, camLon, 1_500.0).map { it.id }.toSet()
        assertTrue("a" in ids)
        assertFalse("mumbai" in ids)
        // 20 km reach picks up the neighbour a couple of buckets away.
        assertTrue("b" in index.near(camLat, camLon, 20_000.0).map { it.id })
    }
}
