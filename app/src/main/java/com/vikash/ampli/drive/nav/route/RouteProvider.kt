package com.vikash.ampli.drive.nav.route

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Result of one routing request. */
sealed class RouteResult {
    data class Ok(val route: Route) : RouteResult()

    data class Failed(val reason: Reason, val detail: String?) : RouteResult()

    enum class Reason {
        /** No internet, DNS or TLS trouble, timeout. */
        Network,

        /** The server answered but has no road between the two points. */
        NoRoute,

        /** Rate limited or overloaded (HTTP 429 / 5xx). */
        Busy,

        /** Anything else the server said, or a body we couldn't read. */
        Server,

        /**
         * The route server could not be reached although the internet itself works: a TLS
         * handshake it refused (an old Android's certificates or protocols), a host that answers
         * nothing. Never shown as "no internet".
         */
        Unreachable,
    }
}

/**
 * Where routes come from. Today only [OsrmRouteProvider]; a self-hosted OSRM (or another engine)
 * slots in behind the same call.
 */
interface RouteProvider {
    /** Human-readable server, for Settings › About › Diagnostics. */
    val serverLabel: String

    /** Blocking: call from a background thread. [bearing] = the car's heading, for reroutes. */
    fun route(from: LatLon, to: LatLon, bearing: Float?): RouteResult
}

/**
 * OSRM HTTP API (`/route/v1/driving`). [baseUrl] comes from RemoteConfig (BuildConfig.ROUTING_BASE_URL
 * underneath). Release builds fail to compile without our own server (see deploy/osrm-photon).
 *
 * IMPORTANT: debug builds default to https://router.project-osrm.org, the OSRM project's public DEMO
 * server. It is for testing only: no SLA, about one request per second, and its usage policy does
 * not allow production apps. Before release, point ROUTING_BASE_URL (a CI repository variable) at
 * our own OSRM server (osrm-backend in Docker with an India extract from Geofabrik).
 */
class OsrmRouteProvider(
    /** Read on every request, so a remote-config change applies without a restart. */
    private val baseUrl: () -> String,
    private val userAgent: String,
    private val timeoutMs: Int = 12_000,
) : RouteProvider {
    private val base: String get() = baseUrl().trim().trimEnd('/')

    override val serverLabel: String get() = base.ifEmpty { "not configured" }

    override fun route(from: LatLon, to: LatLon, bearing: Float?): RouteResult {
        // Release builds never fall back to a public server: no URL, no route.
        if (base.isEmpty()) return RouteResult.Failed(RouteResult.Reason.Server, "Routing server not configured")
        var last: RouteResult = RouteResult.Failed(RouteResult.Reason.Server, null)
        for (server in serversFor(base)) {
            last = routeOn(server, from, to, bearing)
            // A route, or "no road there" (the next server would say the same): done.
            if (last is RouteResult.Ok || (last as RouteResult.Failed).reason == RouteResult.Reason.NoRoute) return last
        }
        return last
    }

    private fun routeOn(server: String, from: LatLon, to: LatLon, bearing: Float?): RouteResult {
        // Try the richest request first. Some OSRM builds reject the maxspeed annotation or a
        // bearing that doesn't fit the nearest road with HTTP 400; then ask again with less.
        val attempts = buildList {
            if (bearing != null) add(url(from, to, bearing, limits = true, server = server))
            add(url(from, to, null, limits = true, server = server))
            add(url(from, to, null, limits = false, server = server))
        }
        var last: RouteResult = RouteResult.Failed(RouteResult.Reason.Server, null)
        for (u in attempts) {
            last = fetch(u)
            val retry = last is RouteResult.Failed &&
                (last.reason == RouteResult.Reason.Server || (last.reason == RouteResult.Reason.NoRoute && bearing != null))
            if (!retry) return last
        }
        return last
    }

    fun url(from: LatLon, to: LatLon, bearing: Float?, limits: Boolean, server: String = base): String = buildString {
        append(server)
        append("/route/v1/driving/")
        append(String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat))
        append("?overview=full&geometries=geojson&steps=true")
        if (limits) append("&annotations=maxspeed")
        if (bearing != null) append(String.format(Locale.US, "&bearings=%d,60;", ((bearing.toInt() % 360) + 360) % 360))
    }

    private fun fetch(u: String): RouteResult {
        val conn = try {
            (URL(u).openConnection() as HttpURLConnection).also { com.vikash.ampli.drive.system.ModernTls.apply(it) }
        } catch (e: IOException) {
            return RouteResult.Failed(classify(e), e.message)
        }
        return try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            when {
                code == 429 || code >= 500 -> RouteResult.Failed(RouteResult.Reason.Busy, "HTTP $code")
                else -> when (val parsed = OsrmParser.parse(body)) {
                    is OsrmParse.Ok -> RouteResult.Ok(parsed.route)
                    is OsrmParse.Error -> RouteResult.Failed(
                        if (parsed.code == "NoRoute" || parsed.code == "NoSegment") RouteResult.Reason.NoRoute else RouteResult.Reason.Server,
                        "HTTP $code ${parsed.code}" + (parsed.message?.let { ": $it" } ?: ""),
                    )
                }
            }
        } catch (e: IOException) {
            RouteResult.Failed(classify(e), "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** The OSRM project's public demo server: testing only, never for production. */
        const val DEMO_URL = "https://router.project-osrm.org"

        /**
         * FOSSGIS's public OSRM (car profile, same API), the second server for TEST builds only,
         * when the demo server can't be reached or is busy. Fair use, no SLA: like [DEMO_URL] it is
         * never used by a release build (those have their own server and no fallback).
         */
        const val FOSSGIS_URL = "https://routing.openstreetmap.de/routed-car"

        /** The servers tried in order: our own server alone, or the demo server then FOSSGIS's. */
        fun serversFor(primary: String): List<String> {
            val p = primary.trim().trimEnd('/')
            if (p.isEmpty()) return emptyList()
            return if (p == DEMO_URL) listOf(DEMO_URL, FOSSGIS_URL) else listOf(p)
        }

        /**
         * What a failed request means. Only "this name doesn't resolve" and "nothing answers" are
         * network trouble; a TLS handshake the server refused is that server's problem, and a
         * timeout is a slow server. (The navigator also checks the unit is online before it
         * says "no internet".)
         */
        fun classify(e: IOException): RouteResult.Reason = when (e) {
            is javax.net.ssl.SSLException -> RouteResult.Reason.Unreachable
            is java.net.SocketTimeoutException -> RouteResult.Reason.Busy
            is java.net.UnknownHostException, is java.net.ConnectException, is java.net.NoRouteToHostException -> RouteResult.Reason.Network
            else -> RouteResult.Reason.Network
        }
    }
}
