package com.vikash.ampli.drive.nav.route

import com.vikash.ampli.drive.nav.Maneuver
import kotlin.math.roundToInt

/** What came back from the routing server. */
sealed class OsrmParse {
    data class Ok(val route: Route) : OsrmParse()

    /** OSRM's own error ("NoRoute", "InvalidOptions"…) or an unreadable body. */
    data class Error(val code: String, val message: String?) : OsrmParse()
}

/**
 * Reads an OSRM `/route/v1/driving` response requested with `overview=full&geometries=geojson&
 * steps=true` (and optionally `annotations=maxspeed`). Plain Kotlin, unit-tested against
 * a sample response in src/test/resources.
 */
object OsrmParser {

    fun parse(body: String): OsrmParse {
        val root = try {
            MiniJson.parse(body).obj()
        } catch (e: MiniJson.ParseException) {
            return OsrmParse.Error("BadJson", e.message)
        } ?: return OsrmParse.Error("BadJson", "not an object")
        val code = root.str("code") ?: "Unknown"
        if (code != "Ok") return OsrmParse.Error(code, root.str("message"))
        val r = root["routes"].arr()?.firstOrNull().obj() ?: return OsrmParse.Error("NoRoute", "no routes")

        val coords = r["geometry"].obj()?.get("coordinates").arr() ?: return OsrmParse.Error("BadJson", "no geometry")
        val points = coords.mapNotNull { c ->
            val pair = c.arr() ?: return@mapNotNull null
            val lon = pair.getOrNull(0) as? Double ?: return@mapNotNull null
            val lat = pair.getOrNull(1) as? Double ?: return@mapNotNull null
            LatLon(lat, lon)
        }
        if (points.size < 2) return OsrmParse.Error("NoRoute", "geometry too short")

        val raw = ArrayList<RawStep>()
        val limits = ArrayList<Int?>()
        var hasLimits = false
        for (leg in r["legs"].arr().orEmpty()) {
            val l = leg.obj() ?: continue
            for (s in l["steps"].arr().orEmpty()) {
                val step = s.obj() ?: continue
                val m = step["maneuver"].obj() ?: continue
                val loc = m["location"].arr() ?: continue
                val lon = loc.getOrNull(0) as? Double ?: continue
                val lat = loc.getOrNull(1) as? Double ?: continue
                raw += RawStep(
                    type = m.str("type") ?: "turn",
                    modifier = m.str("modifier"),
                    exit = m.num("exit")?.roundToInt(),
                    location = LatLon(lat, lon),
                    name = step.str("name"),
                    ref = step.str("ref"),
                    distanceM = step.num("distance") ?: 0.0,
                    durationS = step.num("duration") ?: 0.0,
                )
            }
            val maxspeed = l["annotation"].obj()?.get("maxspeed").arr()
            if (maxspeed != null) {
                hasLimits = true
                for (v in maxspeed) limits += speedKmh(v.obj())
            }
        }

        val geometry = Route(points, emptyList(), emptyList(), 0.0, 0.0, null)
        val cum = geometry.cumM
        val along = DoubleArray(raw.size)
        var from = 0
        for ((i, s) in raw.withIndex()) {
            val idx = nearestVertex(points, s.location, from)
            along[i] = cum[idx]
            from = idx
        }
        val total = geometry.lengthM
        val spans = raw.indices.map { i ->
            val start = along[i]
            val end = if (i + 1 < raw.size) along[i + 1] else total
            TimedSpan(start, (end - start).coerceAtLeast(0.0), raw[i].durationS)
        }

        val steps = ArrayList<RouteStep>()
        var prevRoad: String? = null
        for ((i, s) in raw.withIndex()) {
            val mapped = OsrmManeuvers.map(s.type, s.modifier, s.exit, s.name ?: s.ref, prevRoad)
            if (mapped != null) {
                steps += RouteStep(
                    maneuver = mapped.first,
                    exitNumber = mapped.second,
                    location = s.location,
                    name = s.name,
                    ref = s.ref,
                    alongM = if (mapped.first == Maneuver.Arrive) total else along[i],
                    osrmType = s.type,
                    osrmModifier = s.modifier,
                )
            }
            prevRoad = s.name ?: s.ref
        }
        if (steps.isEmpty() || steps.last().maneuver != Maneuver.Arrive) {
            val end = points.last()
            steps += RouteStep(Maneuver.Arrive, null, end, null, null, total, "arrive", null)
        }

        return OsrmParse.Ok(
            Route(
                points = points,
                steps = steps,
                spans = spans,
                distanceM = r.num("distance") ?: total,
                durationS = r.num("duration") ?: spans.sumOf { it.durationS },
                maxspeedKmh = if (hasLimits) limits else null,
            ),
        )
    }

    /** {"speed":50,"unit":"km/h"} → 50; mph converted; {"unknown":true} / {"none":true} → null. */
    internal fun speedKmh(v: Map<String, Any?>?): Int? {
        if (v == null) return null
        val speed = v.num("speed") ?: return null
        if (speed <= 0) return null
        val kmh = if (v.str("unit") == "mph") speed * 1.609344 else speed
        return kmh.roundToInt()
    }

    /** OSRM manoeuvre locations are geometry vertices: the first close match from [from] onwards. */
    private fun nearestVertex(points: List<LatLon>, p: LatLon, from: Int): Int {
        var best = from.coerceIn(0, points.size - 1)
        var bestD = Double.MAX_VALUE
        for (i in best until points.size) {
            val d = Geo.distanceM(points[i], p)
            if (d < bestD) {
                bestD = d
                best = i
                if (d < 0.5) break
            }
        }
        return best
    }

    private data class RawStep(
        val type: String,
        val modifier: String?,
        val exit: Int?,
        val location: LatLon,
        val name: String?,
        val ref: String?,
        val distanceM: Double,
        val durationS: Double,
    )
}

/**
 * OSRM maneuver type + modifier → the [Maneuver] Ampli speaks and draws. Returns null for steps
 * that need no instruction (depart, straight on along a renamed road, leaving a roundabout).
 * Plain Kotlin, unit-tested.
 */
object OsrmManeuvers {

    /**
     * @param road the road after the manoeuvre (name or ref), for the flyover heuristic.
     * @param prevRoad the road before it: already on the flyover means nothing new to say.
     */
    fun map(type: String, modifier: String?, exit: Int?, road: String?, prevRoad: String?): Pair<Maneuver, Int?>? {
        val mod = modifier?.lowercase()
        // OSRM has no "flyover" type: OpenStreetMap names them ("Rajiv Chowk Flyover").
        val ontoFlyover = isFlyover(road) && !isFlyover(prevRoad)
        val gentle = mod == null || mod == "straight" || mod.startsWith("slight")
        return when (type.lowercase()) {
            "depart", "exit roundabout", "exit rotary" -> null
            "arrive" -> Maneuver.Arrive to null
            "roundabout", "rotary" -> Maneuver.Roundabout to exit?.takeIf { it > 0 }
            "on ramp" -> (if (ontoFlyover) Maneuver.Flyover else Maneuver.Ramp) to null
            "off ramp" -> Maneuver.Exit to null
            "fork" -> when {
                ontoFlyover && gentle -> Maneuver.Flyover to null
                mod?.contains("left") == true -> Maneuver.KeepLeft to null
                mod?.contains("right") == true -> Maneuver.KeepRight to null
                else -> null
            }
            "merge" -> when {
                mod?.contains("left") == true -> Maneuver.KeepLeft to null
                mod?.contains("right") == true -> Maneuver.KeepRight to null
                else -> null
            }
            "new name", "notification", "use lane" -> when {
                ontoFlyover && gentle -> Maneuver.Flyover to null
                // A bend where the road changes name isn't worth a sentence.
                mod == null || gentle -> null
                else -> byModifier(mod)?.let { it to null }
            }
            // "turn", "continue", "end of road", "roundabout turn" and anything newer.
            else -> when {
                ontoFlyover && gentle -> Maneuver.Flyover to null
                else -> byModifier(mod)?.let { it to null }
            }
        }
    }

    fun byModifier(mod: String?): Maneuver? = when (mod?.lowercase()) {
        "uturn" -> Maneuver.UTurn
        "sharp right" -> Maneuver.SharpRight
        "right" -> Maneuver.Right
        "slight right" -> Maneuver.SlightRight
        "slight left" -> Maneuver.SlightLeft
        "left" -> Maneuver.Left
        "sharp left" -> Maneuver.SharpLeft
        else -> null // "straight" or missing
    }

    fun isFlyover(road: String?): Boolean {
        if (road == null) return false
        val r = road.lowercase()
        return "flyover" in r || "fly over" in r || "fly-over" in r || "फ्लाईओवर" in r || "फ्लायओवर" in r
    }
}
