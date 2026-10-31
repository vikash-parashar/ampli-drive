package com.vikash.ampli.drive.weather

import com.vikash.ampli.drive.nav.route.MiniJson
import com.vikash.ampli.drive.nav.route.arr
import com.vikash.ampli.drive.nav.route.num
import com.vikash.ampli.drive.nav.route.obj
import com.vikash.ampli.drive.nav.route.str
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

/** The sky right now, as the voice answer and its card say it. */
enum class Sky { Clear, PartlyCloudy, Cloudy, Rain, HeavyRain, Thunder, Fog, Snow }

/** Where the numbers came from; the card credits it (both are CC BY 4.0). */
enum class WeatherSource { MetNorway, OpenMeteo }

/** "Hey Ampli, how is the weather?" for where the car is. */
data class NowWeather(
    val sky: Sky,
    val temperatureC: Double?,
    /** Highest chance of rain in the next hours, when the source gives one. */
    val rainChancePct: Int?,
    /** Rain expected over the next hours, mm. */
    val rainMm: Double?,
    val source: WeatherSource,
    val fetchedAt: Long,
) {
    /** Rain is likely in the next few hours. */
    val rainLikely: Boolean
        get() = when {
            sky == Sky.Rain || sky == Sky.HeavyRain || sky == Sky.Thunder -> true
            rainChancePct != null -> rainChancePct >= RAIN_LIKELY_PCT
            else -> (rainMm ?: 0.0) >= RAIN_LIKELY_MM
        }

    companion object {
        const val RAIN_LIKELY_PCT = 40
        const val RAIN_LIKELY_MM = 0.5
    }
}

/**
 * The current-weather answer's sources, pure for tests.
 *
 * Default: MET Norway Locationforecast 2.0 (api.met.no), free including commercial use under
 * CC BY 4.0, which asks for a User-Agent that says who we are with a contact, coordinates of at
 * most four decimals and no more than one request per place every few minutes (we cache 15 min).
 * When a paid or self-hosted Open-Meteo server is configured (RemoteConfig weather_url, not the
 * free non-commercial endpoint) that is used instead.
 */
object NowWeatherApi {
    const val MET_URL = "https://api.met.no/weatherapi/locationforecast/2.0/complete"
    const val CONTACT = "support@ampliapps.in"

    /** Hours ahead that count for "will it rain". */
    const val AHEAD_HOURS = 6

    fun userAgent(version: String): String = "AmpliCarHub/$version (Ampli Studio; $CONTACT)"

    private fun f(v: Double) = "%.4f".format(Locale.US, v)

    fun metUrl(lat: Double, lon: Double): String = "$MET_URL?lat=${f(lat)}&lon=${f(lon)}"

    fun openMeteoUrl(base: String, lat: Double, lon: Double, apiKey: String = ""): String =
        base.trimEnd('?') + "?latitude=${f(lat)}&longitude=${f(lon)}" +
            "&current=temperature_2m,weather_code" +
            "&hourly=precipitation_probability,precipitation,weather_code&forecast_hours=$AHEAD_HOURS&timezone=GMT" +
            (if (apiKey.isNotBlank()) "&apikey=$apiKey" else "")

    /** Open-Meteo only when it is a configured server, never the free non-commercial endpoint. */
    fun useOpenMeteo(configuredUrl: String): Boolean =
        configuredUrl.isNotBlank() && configuredUrl.trimEnd('/', '?') != RouteWeather.FREE_URL

    /** MET Norway symbol codes ("lightrainshowers_day", "partlycloudy_night", "rainandthunder"). */
    fun skyFromMet(symbol: String?): Sky? {
        val s = symbol?.substringBefore('_')?.lowercase(Locale.ROOT) ?: return null
        return when {
            s.contains("thunder") -> Sky.Thunder
            s.startsWith("heavyrain") || s.startsWith("heavysleet") -> Sky.HeavyRain
            s.contains("rain") || s.contains("sleet") -> Sky.Rain
            s.contains("snow") -> Sky.Snow
            s == "fog" -> Sky.Fog
            s == "cloudy" -> Sky.Cloudy
            s == "partlycloudy" || s == "fair" -> Sky.PartlyCloudy
            s == "clearsky" -> Sky.Clear
            else -> null
        }
    }

    /** WMO weather codes as Open-Meteo sends them. */
    fun skyFromWmo(code: Int?): Sky? = when (code) {
        null -> null
        0 -> Sky.Clear
        1, 2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        65, 67, 82 -> Sky.HeavyRain
        in 51..67, 80, 81 -> Sky.Rain
        in 71..77, 85, 86 -> Sky.Snow
        in 95..99 -> Sky.Thunder
        else -> null
    }

    /** Parses MET Norway's Locationforecast (compact or complete). Null when it has no data. */
    fun parseMet(json: String, now: Long): NowWeather? {
        val root = MiniJson.parse(json).obj() ?: return null
        val series = root["properties"].obj()?.get("timeseries").arr()?.mapNotNull { it.obj() } ?: return null
        if (series.isEmpty()) return null
        fun timeOf(e: Map<String, Any?>): Long = try {
            Instant.parse(e.str("time")).toEpochMilli()
        } catch (_: Exception) {
            Long.MIN_VALUE
        }
        // The latest entry at or before now (the current hour); the first when all are ahead.
        val startIndex = series.indexOfLast { timeOf(it) <= now }.coerceAtLeast(0)
        val first = series[startIndex]
        val data = first["data"].obj() ?: return null
        val temp = data["instant"].obj()?.get("details").obj()?.num("air_temperature")
        val next1 = data["next_1_hours"].obj()
        val next6 = data["next_6_hours"].obj()
        val sky = skyFromMet(next1?.get("summary").obj()?.str("symbol_code"))
            ?: skyFromMet(next6?.get("summary").obj()?.str("symbol_code"))
            ?: Sky.Cloudy
        // Rain over the next hours: the six-hour block, else the hourly entries added up.
        val ahead = series.drop(startIndex).take(AHEAD_HOURS)
        val hourly = ahead.mapNotNull { it["data"].obj()?.get("next_1_hours").obj()?.get("details").obj() }
        val mm = next6?.get("details").obj()?.num("precipitation_amount")
            ?: hourly.mapNotNull { it.num("precipitation_amount") }.takeIf { it.isNotEmpty() }?.sum()
        val chances = (hourly.mapNotNull { it.num("probability_of_precipitation") } +
            listOfNotNull(next6?.get("details").obj()?.num("probability_of_precipitation")))
        // Showers or thunder forecast in the coming hours also count as a real chance.
        val laterWet = ahead.drop(1).any { e ->
            skyFromMet(e["data"].obj()?.get("next_1_hours").obj()?.get("summary").obj()?.str("symbol_code")) in WET
        }
        val chance = chances.maxOrNull()?.toInt()
        return NowWeather(
            sky = sky,
            temperatureC = temp,
            rainChancePct = chance,
            rainMm = if (laterWet && (mm ?: 0.0) < NowWeather.RAIN_LIKELY_MM) NowWeather.RAIN_LIKELY_MM else mm,
            source = WeatherSource.MetNorway,
            fetchedAt = now,
        )
    }

    private val WET = setOf(Sky.Rain, Sky.HeavyRain, Sky.Thunder)

    /** Parses the Open-Meteo answer of [openMeteoUrl]. */
    fun parseOpenMeteo(json: String, now: Long): NowWeather? {
        val root = MiniJson.parse(json).obj() ?: return null
        val current = root["current"].obj()
        val hourly = root["hourly"].obj()
        fun list(key: String): List<Double> = hourly?.get(key).arr()?.mapNotNull { it as? Double }.orEmpty()
        val code = current?.num("weather_code")?.toInt() ?: list("weather_code").firstOrNull()?.toInt()
        val sky = skyFromWmo(code) ?: return null
        val chances = list("precipitation_probability").take(AHEAD_HOURS)
        val mm = list("precipitation").take(AHEAD_HOURS)
        return NowWeather(
            sky = sky,
            temperatureC = current?.num("temperature_2m"),
            rainChancePct = chances.maxOrNull()?.toInt(),
            rainMm = if (mm.isEmpty()) null else mm.sum(),
            source = WeatherSource.OpenMeteo,
            fetchedAt = now,
        )
    }
}

/** Cache rules for the voice weather answer, pure for tests. */
object NowWeatherCache {
    /** A fresh answer is reused for this long (MET Norway asks clients not to hammer it). */
    const val FRESH_MS = 15L * 60_000L

    /** Offline, an older answer is still better than none, up to this age. */
    const val STALE_OK_MS = 3L * 60 * 60_000L

    /** About 2 km: moving a street over doesn't need a new forecast. */
    fun key(lat: Double, lon: Double): String = "${(lat * 50).roundToInt()},${(lon * 50).roundToInt()}"

    fun fresh(w: NowWeather?, now: Long): Boolean = w != null && now - w.fetchedAt in 0 until FRESH_MS

    fun usableOffline(w: NowWeather?, now: Long): Boolean = w != null && now - w.fetchedAt in 0 until STALE_OK_MS
}
