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
        // Try the richest request first. Some OSRM builds reject the maxspeed annotation or a
        // bearing that doesn't fit the nearest road with HTTP 400; then ask again with less.
        val attempts = buildList {
            if (bearing != null) add(url(from, to, bearing, limits = true))
            add(url(from, to, null, limits = true))
            add(url(from, to, null, limits = false))
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

    fun url(from: LatLon, to: LatLon, bearing: Float?, limits: Boolean): String = buildString {
        append(base)
        append("/route/v1/driving/")
        append(String.format(Locale.US, "%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat))
        append("?overview=full&geometries=geojson&steps=true")
        if (limits) append("&annotations=maxspeed")
        if (bearing != null) append(String.format(Locale.US, "&bearings=%d,60;", ((bearing.toInt() % 360) + 360) % 360))
    }

    private fun fetch(u: String): RouteResult {
        val conn = try {
            URL(u).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return RouteResult.Failed(RouteResult.Reason.Network, e.message)
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
            RouteResult.Failed(RouteResult.Reason.Network, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** The OSRM project's public demo server: testing only, never for production. */
        const val DEMO_URL = "https://router.project-osrm.org"
    }
}
