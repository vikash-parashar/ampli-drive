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
}
