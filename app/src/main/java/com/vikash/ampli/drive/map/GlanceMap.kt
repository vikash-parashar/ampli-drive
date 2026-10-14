package com.vikash.ampli.drive.map

import android.content.Context
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Everything MapLibre lives in this file, so a MapLibre upgrade or a build problem only
 * touches this one place. The map is a "glance map": no gestures, follows the car.
 */
const val GLANCE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Shown on top of the map in Compose (the MapLibre logo and "i" button are turned off). */
const val GLANCE_ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

private const val FOLLOW_ZOOM = 15.5
private const val MAX_FPS = 30
private val COLOR_WHITE: Int = 0xFFFFFFFF.toInt()

/**
 * MapLibre glance map that follows [location]. Call it only once a first fix exists, so the
 * map never opens zoomed out on a country view. [styleUrl] and [puckColor] follow the
 * home-screen theme; a style that fails to load falls back to [GLANCE_STYLE_URL].
 */
@Composable
fun GlanceMap(
    location: Location,
    modifier: Modifier = Modifier,
    styleUrl: String = GLANCE_STYLE_URL,
    puckColor: Int = 0xFFE11D48.toInt(),
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { MapHolder(context.applicationContext, styleUrl, puckColor) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        val options = MapLibreMapOptions.createFromAttributes(context)
            .attributionEnabled(false)
            .logoEnabled(false)
        MapView(context, options).also { view ->
            view.onCreate(null)
            view.setMaximumFps(MAX_FPS)
            view.addOnDidFailLoadingMapListener { _ -> holder.onStyleFailed() }
            view.getMapAsync { map -> holder.onMapReady(context, map) }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.start(mapView)
                Lifecycle.Event.ON_RESUME -> holder.resume(mapView)
                Lifecycle.Event.ON_PAUSE -> holder.pause(mapView)
                Lifecycle.Event.ON_STOP -> holder.stop(mapView)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.pause(mapView)
            holder.stop(mapView)
            holder.destroy(mapView)
        }
    }

    LaunchedEffect(location) { holder.update(location) }
    LaunchedEffect(styleUrl, puckColor) { holder.setLook(styleUrl, puckColor) }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Keeps MapView lifecycle calls balanced and buffers the location until the style loads. */
private class MapHolder(
    private val appContext: Context,
    private var styleUrl: String,
    private var puckColor: Int,
) {
    private var map: MapLibreMap? = null
    private var loadedUrl: String? = null
    private var tracking = false
    private var pending: Location? = null
    private var started = false
    private var resumed = false
    private var destroyed = false

    fun onMapReady(context: Context, map: MapLibreMap) {
        if (destroyed) return
        this.map = map
        map.uiSettings.setAllGesturesEnabled(false)
        map.uiSettings.setCompassEnabled(false)
        map.uiSettings.setAttributionEnabled(false)
        map.uiSettings.setLogoEnabled(false)
        pending?.let { moveTo(map, it) }
        loadStyle(map)
    }

    /** Theme changed: swap the map style and/or recolour the position puck. */
    fun setLook(url: String, puck: Int) {
        val puckChanged = puck != puckColor
        styleUrl = url
        puckColor = puck
        val m = map ?: return
        if (url != loadedUrl) {
            loadStyle(m)
        } else if (puckChanged && tracking) {
            recolourPuck(m)
        }
    }

    /** A style URL that cannot load (offline or renamed) falls back to the default style once. */
    fun onStyleFailed() {
        if (destroyed || styleUrl == GLANCE_STYLE_URL) return
        styleUrl = GLANCE_STYLE_URL
        map?.let { loadStyle(it) }
    }

    private fun loadStyle(map: MapLibreMap) {
        val url = styleUrl
        loadedUrl = url
        map.setStyle(Style.Builder().fromUri(url)) { style ->
            if (destroyed) return@setStyle
            // The location component re-adds its layers on every new style by itself.
            if (!tracking) enableTracking(appContext, map, style) else recolourPuck(map)
        }
    }

    private fun puckOptions(context: Context): LocationComponentOptions =
        LocationComponentOptions.builder(context)
            .foregroundTintColor(puckColor)
            .bearingTintColor(puckColor)
            .backgroundTintColor(COLOR_WHITE)
            .accuracyAlpha(0f)
            .pulseEnabled(false)
            .build()

    private fun recolourPuck(map: MapLibreMap) {
        try {
            map.locationComponent.applyStyle(puckOptions(appContext))
        } catch (_: Exception) {
        }
    }

    fun update(location: Location) {
        pending = location
        val m = map ?: return
        if (tracking) {
            try {
                m.locationComponent.forceLocationUpdate(location)
            } catch (_: Exception) {
                moveTo(m, location)
            }
        } else {
            moveTo(m, location)
        }
    }

    private fun enableTracking(context: Context, map: MapLibreMap, style: Style) {
        try {
            val look = puckOptions(context)
            val activation = LocationComponentActivationOptions.builder(context, style)
                .locationComponentOptions(look)
                // Our own GPS listener feeds fixes in; no second location client.
                .useDefaultLocationEngine(false)
                .build()
            val lc = map.locationComponent
            lc.activateLocationComponent(activation)
            lc.setLocationComponentEnabled(true)
            lc.setCameraMode(CameraMode.TRACKING_GPS)
            lc.setRenderMode(RenderMode.GPS)
            tracking = true
            pending?.let { lc.forceLocationUpdate(it) }
        } catch (_: Exception) {
            // Without the puck the map still re-centres on each fix.
            tracking = false
        }
    }

    private fun moveTo(map: MapLibreMap, location: Location) {
        val position = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(FOLLOW_ZOOM)
            .bearing(if (location.hasBearing()) location.bearing.toDouble() else 0.0)
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position))
    }

    fun start(view: MapView) {
        if (destroyed || started) return
        view.onStart()
        started = true
    }

    fun resume(view: MapView) {
        if (destroyed || resumed) return
        if (!started) start(view)
        view.onResume()
        resumed = true
    }

    fun pause(view: MapView) {
        if (destroyed || !resumed) return
        view.onPause()
        resumed = false
    }

    fun stop(view: MapView) {
        if (destroyed || !started) return
        if (resumed) pause(view)
        view.onStop()
        started = false
    }

    fun destroy(view: MapView) {
        if (destroyed) return
        destroyed = true
        tracking = false
        map = null
        view.onDestroy()
    }
}
