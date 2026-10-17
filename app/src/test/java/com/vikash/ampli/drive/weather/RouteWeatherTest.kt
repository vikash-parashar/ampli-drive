package com.vikash.ampli.drive.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWeatherTest {
    @Test
    fun hazardRules() {
        assertTrue(RouteWeather.hazards(10_000.0, 0.0, 0).isEmpty())
        assertEquals(setOf(Hazard.Fog), RouteWeather.hazards(150.0, 0.0, 45))
        assertEquals(setOf(Hazard.HeavyRain), RouteWeather.hazards(5_000.0, 8.0, 63))
        assertEquals(setOf(Hazard.HeavyRain), RouteWeather.hazards(5_000.0, 2.0, 82))
        assertEquals(setOf(Hazard.Thunderstorm, Hazard.HeavyRain), RouteWeather.hazards(3_000.0, 12.0, 95))
        assertTrue(RouteWeather.hazards(null, null, null).isEmpty())
        assertEquals(setOf(Hazard.Heat), RouteWeather.hazards(10_000.0, 0.0, 0, 44.0))
        assertTrue(RouteWeather.hazards(10_000.0, 0.0, 0, 38.0).isEmpty())
    }

    @Test
    fun pointsAhead() {
        val pts = RouteWeather.points(18.5204, 73.8567, 0.0)
        assertEquals(listOf(0, 25, 50), pts.map { it.first })
        assertEquals(18.5204 + 25_000.0 / 111_195.0, pts[1].second, 1e-3)
        assertEquals(73.8567, pts[1].third, 1e-6)
        val east = RouteWeather.destination(0.0, 179.9, 90.0, 50_000.0)
        assertTrue(east.second < -179.0) // wraps the antimeridian
        assertEquals(1, RouteWeather.points(1.0, 2.0, null).size)
    }

    @Test
    fun reportPicksNearestPointPerHazard() {
        val r = WeatherReport(0L, listOf(PointWeather(0, 5000.0, 0.0, 1), PointWeather(25, 120.0, 0.0, 45), PointWeather(50, 100.0, 9.0, 95)))
        assertEquals(listOf(Hazard.Thunderstorm to 50, Hazard.HeavyRain to 50, Hazard.Fog to 25), r.hazards)
    }

    @Test
    fun url() {
        val u = RouteWeather.url(listOf(Triple(0, 18.5, 73.8), Triple(25, 18.7, 73.8)))
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=18.5000,18.7000&longitude=73.8000,73.8000" +
                "&hourly=visibility,precipitation,weather_code,temperature_2m&forecast_hours=2&timezone=GMT",
            u,
        )
    }
}
