package com.vikash.ampli.drive.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric only for a real org.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class EndpointsTest {

    @Test
    fun parsesHttpsUrlsAndDropsTheRest() {
        val e = Endpoints.parse(
            """{"schema":1,"routing_base_url":"https://route.example.in","geocoder_url":"http://geo.example.in/api/",
               "weather_url":"https://customer-api.open-meteo.com/v1/forecast","weather_api_key":"ab-12_x\"y",
               "map_fallback_style_url":"https://tiles.example.in/style.json","extra":"ignored"}""",
        )!!
        assertEquals("https://route.example.in", e.routingBaseUrl)
        assertEquals("", e.geocoderUrl) // plain HTTP is refused
        assertEquals("ab-12_xy", e.weatherApiKey)
        assertEquals("https://tiles.example.in/style.json", e.mapFallbackStyleUrl)
    }

    @Test
    fun newerSchemaOrGarbageIsIgnored() {
        assertNull(Endpoints.parse("""{"schema":2,"routing_base_url":"https://a.example"}"""))
        assertNull(Endpoints.parse("not json"))
    }

    @Test
    fun remoteOverlaysBuiltInOnlyWherePresent() {
        val built = Endpoints("https://r.built", "https://g.built/api/", "https://w.built", "k1", "")
        val merged = built.overlay(Endpoints(routingBaseUrl = "https://r.remote"))
        assertEquals("https://r.remote", merged.routingBaseUrl)
        assertEquals("https://g.built/api/", merged.geocoderUrl)
        assertEquals("https://w.built", merged.weatherUrl)
        assertEquals("k1", merged.weatherApiKey)
        // A remote weather URL brings its own key (or none).
        assertEquals("", built.overlay(Endpoints(weatherUrl = "https://w.remote")).weatherApiKey)
    }
}
