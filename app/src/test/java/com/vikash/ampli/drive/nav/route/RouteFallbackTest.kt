package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.failureFor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class RouteFallbackTest {
    @Test
    fun demoServerGetsASecondPublicServerOwnServerDoesNot() {
        assertEquals(listOf(OsrmRouteProvider.DEMO_URL, OsrmRouteProvider.FOSSGIS_URL), OsrmRouteProvider.serversFor("https://router.project-osrm.org/"))
        assertEquals(listOf("https://route.example.in"), OsrmRouteProvider.serversFor("https://route.example.in/"))
        assertEquals(emptyList<String>(), OsrmRouteProvider.serversFor(""))
    }

    @Test
    fun tlsAndTimeoutsAreNotNoInternet() {
        assertEquals(RouteResult.Reason.Unreachable, OsrmRouteProvider.classify(javax.net.ssl.SSLHandshakeException("cert")))
        assertEquals(RouteResult.Reason.Busy, OsrmRouteProvider.classify(java.net.SocketTimeoutException()))
        assertEquals(RouteResult.Reason.Network, OsrmRouteProvider.classify(java.net.UnknownHostException("x")))
        assertEquals(RouteResult.Reason.Network, OsrmRouteProvider.classify(IOException("x")))
    }

    @Test
    fun noInternetOnlyWhenTheUnitIsOffline() {
        assertEquals(NavSession.Failure.Network, failureFor(RouteResult.Reason.Network, online = false))
        assertEquals(NavSession.Failure.Unreachable, failureFor(RouteResult.Reason.Network, online = true))
        assertEquals(NavSession.Failure.Unreachable, failureFor(RouteResult.Reason.Unreachable, online = false))
        assertEquals(NavSession.Failure.NoRoute, failureFor(RouteResult.Reason.NoRoute, online = true))
    }
}
