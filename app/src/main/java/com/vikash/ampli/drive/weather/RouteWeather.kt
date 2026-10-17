package com.vikash.ampli.drive.weather

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class Hazard { Thunderstorm, HeavyRain, Fog, Heat }

/** Weather for one point: [aheadKm] 0 is where the car is. */
data class PointWeather(
    val aheadKm: Int,
    val visibilityM: Double?,
    val precipitationMm: Double?,
    val weatherCode: Int?,
    /** Air temperature at 2 m, °C. */
    val temperatureC: Double? = null,
) {
    val hazards: Set<Hazard> get() = RouteWeather.hazards(visibilityM, precipitationMm, weatherCode, temperatureC)
}

data class WeatherReport(val time: Long, val points: List<PointWeather>) {
    /** The nearest point with each hazard. */
    val hazards: List<Pair<Hazard, Int>>
        get() = Hazard.entries.mapNotNull { hz -> points.filter { hz in it.hazards }.minByOrNull { it.aheadKm }?.let { hz to it.aheadKm } }
}

/** Open-Meteo (https://open-meteo.com, CC BY 4.0): no key, free for non-commercial use. Pure helpers. */
object RouteWeather {
    const val REFRESH_MS = 15L * 60_000L
    val AHEAD_KM = listOf(0, 25, 50)

    /** mm in an hour; IMD calls 7.6 mm/h and above heavy rain. */
    const val HEAVY_RAIN_MM = 7.6
    const val FOG_VISIBILITY_M = 200.0

    /** IMD declares a heatwave in the plains from 40 °C; a little above that before speaking up. */
    const val HEAT_C = 42.0

    private val THUNDER = setOf(95, 96, 99)
    private val HEAVY_RAIN_CODES = setOf(65, 67, 82)

    fun hazards(visibilityM: Double?, precipitationMm: Double?, code: Int?, temperatureC: Double? = null): Set<Hazard> {
        val out = LinkedHashSet<Hazard>()
        if (code != null && code in THUNDER) out += Hazard.Thunderstorm
        if ((precipitationMm != null && precipitationMm >= HEAVY_RAIN_MM) || (code != null && code in HEAVY_RAIN_CODES)) out += Hazard.HeavyRain
        if (visibilityM != null && visibilityM < FOG_VISIBILITY_M) out += Hazard.Fog
        if (temperatureC != null && temperatureC >= HEAT_C) out += Hazard.Heat
        return out
    }

    /** The car's position, then points 25 and 50 km along [bearingDeg] (when moving). */
    fun points(lat: Double, lon: Double, bearingDeg: Double?): List<Triple<Int, Double, Double>> =
        if (bearingDeg == null) {
            listOf(Triple(0, lat, lon))
        } else {
            AHEAD_KM.map { km ->
                if (km == 0) Triple(0, lat, lon) else destination(lat, lon, bearingDeg, km * 1000.0).let { Triple(km, it.first, it.second) }
            }
        }

    /** Great-circle point [distanceM] from (lat, lon) along [bearingDeg]. */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distanceM: Double): Pair<Double, Double> {
        val r = 6_371_000.0
        val d = distanceM / r
        val b = Math.toRadians(bearingDeg)
        val p1 = Math.toRadians(lat)
        val l1 = Math.toRadians(lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        val lonDeg = (Math.toDegrees(l2) + 540.0) % 360.0 - 180.0
        return Math.toDegrees(p2) to lonDeg
    }

    /** One request for all points; the current hour and the next. */
    fun url(points: List<Triple<Int, Double, Double>>): String {
        fun f(v: Double) = "%.4f".format(Locale.US, v)
        val lats = points.joinToString(",") { f(it.second) }
        val lons = points.joinToString(",") { f(it.third) }
        return "https://api.open-meteo.com/v1/forecast?latitude=$lats&longitude=$lons" +
            "&hourly=visibility,precipitation,weather_code,temperature_2m&forecast_hours=2&timezone=GMT"
    }
}
