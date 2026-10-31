package com.vikash.ampli.drive.ui

import com.vikash.ampli.drive.cameras.CameraAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's one alert slot: the most urgent candidate wins. */
class HomeAlertsTest {

    private fun a(kind: AlertKind) = HomeAlert(kind, kind.name, "text")

    @Test
    fun nothingToSay() {
        assertNull(HomeAlerts.pick(emptyList()))
        assertNull(HomeAlerts.pick(listOf(null, null)))
    }

    @Test
    fun highestPriorityWins() {
        val all = AlertKind.entries.map { a(it) }
        assertEquals(AlertKind.OverLimit, HomeAlerts.pick(all.shuffled(java.util.Random(7)))?.kind)
        assertEquals(AlertKind.CameraAhead, HomeAlerts.pick(listOf(a(AlertKind.TollPaid), a(AlertKind.CameraAhead), a(AlertKind.Due)))?.kind)
        assertEquals(AlertKind.TollPaid, HomeAlerts.pick(listOf(null, a(AlertKind.FastagLow), a(AlertKind.TollPaid), a(AlertKind.LocationOff)))?.kind)
        assertEquals(AlertKind.Tyre, HomeAlerts.pick(listOf(a(AlertKind.ReportAhead), a(AlertKind.Tyre), a(AlertKind.Engine)))?.kind)
    }

    @Test
    fun safetyComesBeforeMoney() {
        val order = AlertKind.entries
        assertTrue(order.indexOf(AlertKind.CameraAhead) < order.indexOf(AlertKind.TollPaid))
        assertTrue(order.indexOf(AlertKind.ReportAhead) < order.indexOf(AlertKind.FastagLow))
        assertTrue(order.indexOf(AlertKind.Engine) < order.indexOf(AlertKind.Weather))
        assertTrue(order.indexOf(AlertKind.Due) < order.indexOf(AlertKind.LocationOff))
    }

    @Test
    fun cameraKinds() {
        fun cam(kind: CameraAlert.Kind, over: Boolean = false) = CameraAlert("x", kind, 400, 80, "", overLimit = over)
        assertEquals(AlertKind.CameraAhead, HomeAlerts.kindFor(cam(CameraAlert.Kind.FixedCamera)))
        assertEquals(AlertKind.CameraAhead, HomeAlerts.kindFor(cam(CameraAlert.Kind.MobileCamera)))
        assertEquals(AlertKind.ReportAhead, HomeAlerts.kindFor(cam(CameraAlert.Kind.Police)))
        assertEquals(AlertKind.ReportAhead, HomeAlerts.kindFor(cam(CameraAlert.Kind.Jam)))
        assertEquals(AlertKind.OverLimit, HomeAlerts.kindFor(cam(CameraAlert.Kind.FixedCamera, over = true)))
    }

    @Test
    fun tollShowsForTwoMinutes() {
        val t = 1_000_000L
        assertTrue(HomeAlerts.tollFresh(t, t))
        assertTrue(HomeAlerts.tollFresh(t, t + 119_000L))
        assertFalse(HomeAlerts.tollFresh(t, t + 121_000L))
        assertFalse(HomeAlerts.tollFresh(t, t - 1L))
    }

    @Test
    fun locationOffSaysItOnce() {
        val off = HomeAlert(AlertKind.LocationOff, "Location", "Location off")
        assertEquals("Location off", off.headline)
        assertNull(off.detail)
        assertEquals("Location off", off.line)
        val toll = HomeAlert(AlertKind.TollPaid, "FASTag", "₹65 taken · ₹480 left")
        assertEquals("FASTag", toll.headline)
        assertEquals("FASTag · ₹65 taken · ₹480 left", toll.line)
        assertEquals("GPS off", HomeAlert(AlertKind.LocationOff, "Location", "GPS off").line)
    }

    @Test
    fun ampliLineGoesAfterTwelveSeconds() {
        val t = 5_000_000L
        assertEquals(AmpliLine.SHOWN_MS, AmpliLine.remainingMs(t, t))
        assertEquals(12_000L, AmpliLine.SHOWN_MS)
        assertEquals(2_000L, AmpliLine.remainingMs(t, t + 10_000L))
        assertEquals(0L, AmpliLine.remainingMs(t, t + 12_000L))
        assertEquals(0L, AmpliLine.remainingMs(t, t + 60_000L))
        // Never said: nothing to show.
        assertEquals(0L, AmpliLine.remainingMs(0L, t))
    }

    @Test
    fun bubbleNeverCoversTheLocationCard() {
        assertTrue(AmpliLine.bubbleShown("Bhai, aage camera sai", locationPrompt = false))
        assertFalse(AmpliLine.bubbleShown("Bhai, aage camera sai", locationPrompt = true))
        assertFalse(AmpliLine.bubbleShown(null, locationPrompt = false))
    }
}
