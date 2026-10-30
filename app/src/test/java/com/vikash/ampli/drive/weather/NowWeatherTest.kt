package com.vikash.ampli.drive.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NowWeatherTest {
    private fun entry(time: String, temp: Double, symbol: String, mm: Double, prob: Double? = null, six: String = "") = """
        {"time":"$time","data":{"instant":{"details":{"air_temperature":$temp,"wind_speed":2.1}},
         "next_1_hours":{"summary":{"symbol_code":"$symbol"},"details":{"precipitation_amount":$mm${if (prob != null) ",\"probability_of_precipitation\":$prob" else ""}}}$six}}
    """.trimIndent()

    private fun met(vararg entries: String) =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[77.03,28.46,220]},"properties":{"meta":{"updated_at":"2026-10-30T10:00:00Z"},"timeseries":[${entries.joinToString(",")}]}}"""

    private val now = Instant.parse("2026-10-30T10:20:00Z").toEpochMilli()

    @Test
    fun metPicksTheCurrentHour() {
        val json = met(
            entry("2026-10-30T09:00:00Z", 27.0, "clearsky_day", 0.0),
            entry("2026-10-30T10:00:00Z", 31.4, "partlycloudy_day", 0.0, six = ""","next_6_hours":{"summary":{"symbol_code":"partlycloudy_day"},"details":{"precipitation_amount":0.0}}"""),
            entry("2026-10-30T11:00:00Z", 32.0, "fair_day", 0.0),
        )
        val w = NowWeatherApi.parseMet(json, now)!!
        assertEquals(Sky.PartlyCloudy, w.sky)
        assertEquals(31.4, w.temperatureC!!, 0.01)
        assertEquals(WeatherSource.MetNorway, w.source)
        assertFalse(w.rainLikely)
    }

    @Test
    fun metRainLaterCounts() {
        val json = met(
            entry("2026-10-30T10:00:00Z", 26.0, "cloudy", 0.0),
            entry("2026-10-30T11:00:00Z", 25.0, "cloudy", 0.0),
            entry("2026-10-30T12:00:00Z", 24.0, "lightrainshowers_day", 0.3),
        )
        val w = NowWeatherApi.parseMet(json, now)!!
        assertEquals(Sky.Cloudy, w.sky)
        assertTrue(w.rainLikely)
    }

    @Test
    fun metProbabilityWhenGiven() {
        val json = met(entry("2026-10-30T10:00:00Z", 22.0, "rainandthunder", 4.0, prob = 80.0))
        val w = NowWeatherApi.parseMet(json, now)!!
        assertEquals(Sky.Thunder, w.sky)
        assertEquals(80, w.rainChancePct)
        assertTrue(w.rainLikely)
        assertNull(NowWeatherApi.parseMet(met(), now))
    }

    @Test
    fun metSymbols() {
        assertEquals(Sky.HeavyRain, NowWeatherApi.skyFromMet("heavyrainshowers_night"))
        assertEquals(Sky.Rain, NowWeatherApi.skyFromMet("lightsleet"))
        assertEquals(Sky.Fog, NowWeatherApi.skyFromMet("fog"))
        assertEquals(Sky.Clear, NowWeatherApi.skyFromMet("clearsky_polartwilight"))
        assertEquals(Sky.Snow, NowWeatherApi.skyFromMet("heavysnow"))
        assertNull(NowWeatherApi.skyFromMet(null))
    }

    @Test
    fun openMeteoCurrentAndChance() {
        val json = """{"latitude":28.46,"longitude":77.03,"current":{"time":"2026-10-30T10:15","temperature_2m":29.6,"weather_code":2},
            "hourly":{"time":["a","b","c"],"precipitation_probability":[5,20,55],"precipitation":[0.0,0.0,1.2],"weather_code":[2,3,61]}}"""
        val w = NowWeatherApi.parseOpenMeteo(json, now)!!
        assertEquals(Sky.PartlyCloudy, w.sky)
        assertEquals(55, w.rainChancePct)
        assertTrue(w.rainLikely)
        assertEquals(WeatherSource.OpenMeteo, w.source)
        assertEquals(Sky.Thunder, NowWeatherApi.skyFromWmo(95))
        assertEquals(Sky.HeavyRain, NowWeatherApi.skyFromWmo(65))
        assertEquals(Sky.Fog, NowWeatherApi.skyFromWmo(45))
    }

    @Test
    fun sourcesAndUrls() {
        // MET Norway by default; the free Open-Meteo endpoint is never used for this answer.
        assertFalse(NowWeatherApi.useOpenMeteo(""))
        assertFalse(NowWeatherApi.useOpenMeteo(RouteWeather.FREE_URL))
        assertTrue(NowWeatherApi.useOpenMeteo("https://customer-api.open-meteo.com/v1/forecast"))
        assertEquals(
            "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=28.4595&lon=77.0266",
            NowWeatherApi.metUrl(28.459512, 77.026634),
        )
        assertTrue(NowWeatherApi.userAgent("0.1.99").contains("support@ampliapps.in"))
        assertTrue(NowWeatherApi.userAgent("0.1.99").startsWith("AmpliCarHub/0.1.99"))
        assertTrue(NowWeatherApi.openMeteoUrl("https://x.example/v1/forecast", 1.0, 2.0, "k").endsWith("&apikey=k"))
    }

    @Test
    fun cacheRules() {
        val w = NowWeather(Sky.Clear, 30.0, null, 0.0, WeatherSource.MetNorway, fetchedAt = 1_000_000L)
        assertTrue(NowWeatherCache.fresh(w, 1_000_000L + 14 * 60_000L))
        assertFalse(NowWeatherCache.fresh(w, 1_000_000L + 16 * 60_000L))
        assertTrue(NowWeatherCache.usableOffline(w, 1_000_000L + 2 * 3_600_000L))
        assertFalse(NowWeatherCache.usableOffline(w, 1_000_000L + 4 * 3_600_000L))
        assertEquals(NowWeatherCache.key(28.4595, 77.0266), NowWeatherCache.key(28.4601, 77.0270))
    }
}
