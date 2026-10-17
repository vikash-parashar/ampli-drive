package com.vikash.ampli.drive.config

import org.json.JSONObject

/**
 * The servers Car Hub talks to. Nothing here is a free public server in a release build:
 * routing and place search must be our own (the release build fails without them), weather is
 * off until a paid or self-hosted Open-Meteo is set, and the map falls back to our own style only.
 *
 * Values come from, in order: the cached remote config ([RemoteConfig]), then BuildConfig.
 * Blank means "not configured".
 */
data class Endpoints(
    /** OSRM HTTP API base, e.g. https://route.ampliapps.in. */
    val routingBaseUrl: String = "",
    /** Photon /api endpoint, e.g. https://geo.ampliapps.in/api/. */
    val geocoderUrl: String = "",
    /** Open-Meteo forecast endpoint (customer API or self-hosted), e.g. https://customer-api.open-meteo.com/v1/forecast. */
    val weatherUrl: String = "",
    /** Open-Meteo customer API key; blank for a self-hosted server. */
    val weatherApiKey: String = "",
    /** A second map style (our own tiles) tried when the theme's style fails. Blank: none. */
    val mapFallbackStyleUrl: String = "",
) {
    val routingConfigured: Boolean get() = routingBaseUrl.isNotBlank()
    val geocoderConfigured: Boolean get() = geocoderUrl.isNotBlank()
    val weatherConfigured: Boolean get() = weatherUrl.isNotBlank()

    /** Remote values override these where they are present and valid. */
    fun overlay(remote: Endpoints): Endpoints = Endpoints(
        routingBaseUrl = remote.routingBaseUrl.ifBlank { routingBaseUrl },
        geocoderUrl = remote.geocoderUrl.ifBlank { geocoderUrl },
        weatherUrl = remote.weatherUrl.ifBlank { weatherUrl },
        // A key only makes sense with the URL it came with.
        weatherApiKey = if (remote.weatherUrl.isNotBlank()) remote.weatherApiKey else weatherApiKey,
        mapFallbackStyleUrl = remote.mapFallbackStyleUrl.ifBlank { mapFallbackStyleUrl },
    )

    companion object {
        /** The JSON schema version this build understands. */
        const val SCHEMA = 1

        /**
         * Parses the remote config JSON. Unknown keys are ignored; a value that is not an https
         * URL is dropped (so a typo can't point the app at plain HTTP). Returns null when the
         * document is not a JSON object or has a newer [SCHEMA] than this build understands.
         *
         * ```
         * {"schema":1,"routing_base_url":"https://route.example.in","geocoder_url":"https://geo.example.in/api/",
         *  "weather_url":"https://customer-api.open-meteo.com/v1/forecast","weather_api_key":"...",
         *  "map_fallback_style_url":"https://tiles.example.in/styles/ampli.json"}
         * ```
         */
        fun parse(json: String): Endpoints? {
            val o = try {
                JSONObject(json)
            } catch (_: Exception) {
                return null
            }
            if (o.optInt("schema", SCHEMA) > SCHEMA) return null
            fun url(key: String): String = o.optString(key, "").trim().takeIf { isHttps(it) } ?: ""
            return Endpoints(
                routingBaseUrl = url("routing_base_url"),
                geocoderUrl = url("geocoder_url"),
                weatherUrl = url("weather_url"),
                weatherApiKey = o.optString("weather_api_key", "").trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' },
                mapFallbackStyleUrl = url("map_fallback_style_url"),
            )
        }

        fun isHttps(url: String): Boolean =
            url.startsWith("https://") && url.length > "https://x".length && url.none { it.isWhitespace() || it == '"' }
    }
}
