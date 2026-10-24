package com.vikash.ampli.drive.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
import com.vikash.ampli.drive.nav.route.LatLon
import org.maplibre.android.location.LocationComponentConstants
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Everything MapLibre lives in this file, so a MapLibre upgrade or a build problem only
 * touches this one place. The map is a "glance map": no gestures, follows the car.
 */
const val GLANCE_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Shown on top of the map in Compose (the MapLibre logo and "i" button are turned off). */
const val GLANCE_ATTRIBUTION = "© OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

private const val FOLLOW_ZOOM = 15.5

/** While navigating: closer, tilted, and the car low on the screen so more road ahead shows. */
private const val NAV_ZOOM = 17.0
private const val NAV_TILT = 45.0
private const val NAV_CAR_FROM_TOP = 0.72
private const val NAV_ANIM_MS = 700L
private const val ROUTE_SOURCE = "ampli-route"
private const val ROUTE_CASING = "ampli-route-casing"
private const val ROUTE_LINE = "ampli-route-line"
private const val EMPTY_FEATURES = """{"type":"FeatureCollection","features":[]}"""

/** The route as one LineString feature (empty collection without a route). */
internal fun routeGeoJson(points: List<LatLon>?): String {
    if (points == null || points.size < 2) return EMPTY_FEATURES
    val sb = StringBuilder(points.size * 24 + 96)
    sb.append("""{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[""")
    points.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.append('[').append(String.format(java.util.Locale.US, "%.6f,%.6f", p.lon, p.lat)).append(']')
    }
    sb.append("]}}]}")
    return sb.toString()
}
private const val PARKED_SOURCE = "ampli-parked"
private const val PARKED_LAYER = "ampli-parked-pin"
private const val PARKED_ICON = "ampli-parked-icon"

/** A one-point FeatureCollection (empty when not parked). */
internal fun parkedGeoJson(spot: Pair<Double, Double>?): String {
    if (spot == null) return """{"type":"FeatureCollection","features":[]}"""
    val (lat, lon) = spot
    return String.format(
        java.util.Locale.US,
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[%.7f,%.7f]},"properties":{}}]}""",
        lon, lat,
    )
}

/** A map pin with a white "P", drawn in code so it needs no font from the map style. */
private fun parkedPinBitmap(context: Context, color: Int): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (34 * d).toInt()
    val h = (46 * d).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val r = w / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = COLOR_WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    val path = Path().apply {
        moveTo(r, h.toFloat() - d)
        lineTo(r - r * 0.55f, r * 1.6f)
        lineTo(r + r * 0.55f, r * 1.6f)
        close()
    }
    c.drawPath(path, fill)
    c.drawCircle(r, r, r - 2 * d, fill)
    c.drawCircle(r, r, r - 2 * d, edge)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = COLOR_WHITE
        textSize = r * 1.1f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText("P", r, r + text.textSize * 0.36f, text)
    return bmp
}
private const val MAX_FPS = 30
private val COLOR_WHITE: Int = 0xFFFFFFFF.toInt()

/**
 * MapLibre glance map that follows [location]. Call it only once a first fix exists, so the
 * map never opens zoomed out on a country view. [styleUrl] and [puckColor] follow the
 * home-screen theme; a style that fails to load falls back to [GLANCE_STYLE_URL], then to our own
 * fallback style when one is configured (RemoteConfig.mapFallbackStyleUrl), and else the pane says
 * the map tiles are unavailable. There is no tile.openstreetmap.org fallback: the OSMF tile policy
 * does not allow distributed apps to use those tiles.
 */
@Composable
fun GlanceMap(
    location: Location,
    modifier: Modifier = Modifier,
    styleUrl: String = GLANCE_STYLE_URL,
    puckColor: Int = 0xFFE11D48.toInt(),
    /** Where the car is parked (lat, lon): drawn as a "P" pin. */
    parked: Pair<Double, Double>? = null,
    /** Draw through a TextureView (true) or a GLSurfaceView (false); see DrivePrefs.mapTextureMode. */
    textureMode: Boolean = true,
    /** Nothing drawn [NO_FRAME_TIMEOUT_MS] after the map was made: the caller may try the other mode. */
    onNoFrames: () -> Unit = {},
    /** In-app navigation route, drawn as a line; [routeKey] changes when the route does. */
    route: List<LatLon>? = null,
    routeKey: Int = 0,
    routeColor: Int = puckColor,
    /** Turn-by-turn camera: zoomed in, tilted, car low on the screen. */
    navigating: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember(textureMode) { MapHolder(context.applicationContext, styleUrl, puckColor) }
    val mapView = remember(textureMode) {
        MapLibre.getInstance(context.applicationContext)
        MapLogs.install()
        MapDiagnostics.update { MapStatus(textureMode = textureMode, phase = MapStatus.Phase.NotStarted) }
        val options = MapLibreMapOptions.createFromAttributes(context)
            .attributionEnabled(false)
            .logoEnabled(false)
            // TextureView composes like a normal view: Compose clips and glass layers can't hide it.
            .textureMode(textureMode)
        MapView(context, options).also { view ->
            view.onCreate(null)
            view.setMaximumFps(MAX_FPS)
            view.addOnDidFailLoadingMapListener { message ->
                MapDiagnostics.error("Style failed: $message")
                holder.onStyleFailed()
            }
            view.addOnDidFinishLoadingStyleListener { holder.onStyleLoaded() }
            view.addOnDidFinishLoadingMapListener { MapDiagnostics.update { it.copy(phase = MapStatus.Phase.MapLoaded) } }
            view.addOnDidFinishRenderingMapListener { _ ->
                if (!MapDiagnostics.status.value.drewFrame) MapDiagnostics.update { it.copy(drewFrame = true) }
            }
            view.getMapAsync { map -> holder.onMapReady(context, map) }
        }
    }

    // GL trouble: some head-unit GPUs draw nothing in one of the two surface types.
    LaunchedEffect(mapView) {
        kotlinx.coroutines.delay(NO_FRAME_TIMEOUT_MS)
        if (!MapDiagnostics.status.value.drewFrame) {
            MapDiagnostics.error("No map frame drawn in ${NO_FRAME_TIMEOUT_MS / 1000} s (textureMode=$textureMode)")
            onNoFrames()
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
        // Launcher hidden or memory low: MapLibre drops its tile and glyph caches (reloaded on return).
        val trim: () -> Unit = { mapView.onLowMemory() }
        com.vikash.ampli.drive.system.MemoryTrim.add(trim)
        onDispose {
            com.vikash.ampli.drive.system.MemoryTrim.remove(trim)
            lifecycle.removeObserver(observer)
            holder.pause(mapView)
            holder.stop(mapView)
            holder.destroy(mapView)
        }
    }

    LaunchedEffect(location) { holder.update(location) }
    LaunchedEffect(styleUrl, puckColor) { holder.setLook(styleUrl, puckColor) }
    LaunchedEffect(parked) { holder.setParked(parked) }
    LaunchedEffect(routeKey, route == null) { holder.setRoute(routeGeoJson(route)) }
    LaunchedEffect(routeColor) { holder.setRouteColor(routeColor) }
    LaunchedEffect(navigating, mapView) { mapView.post { holder.setNavigating(navigating, mapView.height) } }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Nothing drawn this long after the map view was made: try the other surface type once. */
const val NO_FRAME_TIMEOUT_MS = 20_000L

/** The vector style must load within this long, or the next fallback takes over. */
private const val STYLE_TIMEOUT_MS = 10_000L

/** After the style loads, tiles that keep failing for this long also move to the next fallback. */
private const val TILES_TIMEOUT_MS = 15_000L

/**
 * Routes MapLibre's own log (tile and style HTTP errors, shader errors) to logcat under
 * [MapDiagnostics.TAG] and counts warnings and errors for Settings › About › Diagnostics.
 */
private object MapLogs : org.maplibre.android.log.LoggerDefinition {
    private var installed = false

    fun install() {
        if (installed) return
        installed = true
        try {
            org.maplibre.android.log.Logger.setLoggerDefinition(this)
            org.maplibre.android.log.Logger.setVerbosity(org.maplibre.android.log.Logger.WARN)
        } catch (_: Throwable) {
        }
    }

    private fun record(tag: String, msg: String, tr: Throwable?) {
        MapDiagnostics.error("$tag: $msg" + (tr?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""))
    }

    override fun v(tag: String, msg: String) = Unit
    override fun v(tag: String, msg: String, tr: Throwable) = Unit
    override fun d(tag: String, msg: String) = Unit
    override fun d(tag: String, msg: String, tr: Throwable) = Unit
    override fun i(tag: String, msg: String) = Unit
    override fun i(tag: String, msg: String, tr: Throwable) = Unit
    override fun w(tag: String, msg: String) = record(tag, msg, null)
    override fun w(tag: String, msg: String, tr: Throwable) = record(tag, msg, tr)
    override fun e(tag: String, msg: String) = record(tag, msg, null)
    override fun e(tag: String, msg: String, tr: Throwable) = record(tag, msg, tr)
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
    private var parked: Pair<Double, Double>? = null
    private var routeJson: String = EMPTY_FEATURES
    private var routeColor: Int = puckColor
    private var navigating = false
    private var viewHeight = 0

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
        // Once on the fallback, stay on it for this map view: the theme's host didn't work.
        if (url != loadedUrl && !basic && !unavailable) {
            loadStyle(m)
        } else if (puckChanged && tracking) {
            recolourPuck(m)
        }
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** On our own fallback style ([fallbackUrl]) after the theme's style failed. */
    private var basic = false

    /** Nothing loaded: no fallback configured, or it failed too. */
    private var unavailable = false
    private var styleLoaded = false
    private var errorsAtStyleLoad = 0

    /** Vector style too slow: no style at all after [STYLE_TIMEOUT_MS]. */
    private val styleWatchdog = Runnable {
        if (!destroyed && !unavailable && !styleLoaded) {
            MapDiagnostics.error("Style not loaded after ${STYLE_TIMEOUT_MS / 1000} s: $loadedUrl")
            useBasic()
        }
    }

    /** Style loaded but tiles keep failing (blocked host, TLS): no full map and new errors. */
    private val tilesWatchdog = Runnable {
        val st = MapDiagnostics.status.value
        if (!destroyed && !unavailable && st.phase != MapStatus.Phase.MapLoaded && st.netErrorCount > errorsAtStyleLoad) {
            MapDiagnostics.error("Vector tiles not loading; trying the next fallback")
            useBasic()
        }
    }

    /**
     * A style that cannot load: a theme style falls back to the default vector style once, and
     * the default one falls back to the basic raster map.
     */
    fun onStyleFailed() {
        if (destroyed || unavailable) return
        if (!basic && styleUrl != GLANCE_STYLE_URL) {
            styleUrl = GLANCE_STYLE_URL
            map?.let { loadStyle(it) }
        } else {
            useBasic()
        }
    }

    fun onStyleLoaded() {
        styleLoaded = true
        errorsAtStyleLoad = MapDiagnostics.status.value.netErrorCount
        main.removeCallbacks(styleWatchdog)
        MapDiagnostics.update { if (it.phase == MapStatus.Phase.MapLoaded) it else it.copy(phase = MapStatus.Phase.StyleLoaded) }
        if (!unavailable) {
            main.removeCallbacks(tilesWatchdog)
            main.postDelayed(tilesWatchdog, TILES_TIMEOUT_MS)
        }
    }

    /** Our own fallback style once, then the "map tiles unavailable" state. */
    private fun useBasic() {
        val m = map ?: return
        main.removeCallbacks(styleWatchdog)
        main.removeCallbacks(tilesWatchdog)
        val fallback = com.vikash.ampli.drive.config.RemoteConfig.current().mapFallbackStyleUrl
        if (!basic && fallback.isNotBlank()) {
            basic = true
            MapDiagnostics.update { it.copy(basic = true) }
            main.postDelayed(styleWatchdog, STYLE_TIMEOUT_MS)
            applyStyle(m, Style.Builder().fromUri(fallback), fallback)
            return
        }
        unavailable = true
        MapDiagnostics.error("Map tiles unavailable: theme style failed and no working fallback style is configured")
        MapDiagnostics.update { it.copy(unavailable = true, phase = MapStatus.Phase.Failed) }
    }

    private fun loadStyle(map: MapLibreMap) {
        main.removeCallbacks(styleWatchdog)
        main.postDelayed(styleWatchdog, STYLE_TIMEOUT_MS)
        applyStyle(map, Style.Builder().fromUri(styleUrl), styleUrl)
    }

    private fun applyStyle(map: MapLibreMap, builder: Style.Builder, label: String) {
        loadedUrl = label
        styleLoaded = false
        MapDiagnostics.update { it.copy(styleUrl = label, phase = MapStatus.Phase.LoadingStyle) }
        try {
            map.setStyle(builder) { style ->
                if (destroyed) return@setStyle
                // A new style drops our own layers: put the route and the parking pin back.
                addRouteLayers(style)
                addParkedLayer(style)
                // The location component re-adds its layers on every new style by itself.
                if (!tracking) enableTracking(appContext, map, style) else recolourPuck(map)
            }
        } catch (e: Exception) {
            MapDiagnostics.error("setStyle: ${e.message}")
            MapDiagnostics.update { it.copy(phase = MapStatus.Phase.Failed) }
        }
    }

    /** Parked car moved or cleared. */
    fun setParked(spot: Pair<Double, Double>?) {
        parked = spot
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        (style.getSource(PARKED_SOURCE) as? GeoJsonSource)?.setGeoJson(parkedGeoJson(spot)) ?: addParkedLayer(style)
    }

    /** New route (or none). */
    fun setRoute(json: String) {
        routeJson = json
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        (style.getSource(ROUTE_SOURCE) as? GeoJsonSource)?.setGeoJson(json) ?: addRouteLayers(style)
    }

    fun setRouteColor(color: Int) {
        if (color == routeColor) return
        routeColor = color
        try {
            (map?.style?.getLayer(ROUTE_LINE) as? LineLayer)?.setProperties(PropertyFactory.lineColor(color))
        } catch (_: Exception) {
        }
    }

    /** The route line under the parking pin and the car, with a white casing so it reads on any map. */
    private fun addRouteLayers(style: Style) {
        try {
            if (style.getSource(ROUTE_SOURCE) == null) style.addSource(GeoJsonSource(ROUTE_SOURCE, routeJson))
            val below = when {
                style.getLayer(PARKED_LAYER) != null -> PARKED_LAYER
                style.getLayer(LocationComponentConstants.SHADOW_LAYER) != null -> LocationComponentConstants.SHADOW_LAYER
                style.getLayer(LocationComponentConstants.ACCURACY_LAYER) != null -> LocationComponentConstants.ACCURACY_LAYER
                else -> null
            }
            fun add(layer: LineLayer) = if (below != null) style.addLayerBelow(layer, below) else style.addLayer(layer)
            if (style.getLayer(ROUTE_CASING) == null) {
                add(
                    LineLayer(ROUTE_CASING, ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(COLOR_WHITE),
                        PropertyFactory.lineWidth(13f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
            }
            if (style.getLayer(ROUTE_LINE) == null) {
                add(
                    LineLayer(ROUTE_LINE, ROUTE_SOURCE).withProperties(
                        PropertyFactory.lineColor(routeColor),
                        PropertyFactory.lineWidth(8f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
            }
        } catch (_: Exception) {
            // Without the line the banner and voice still guide.
        }
    }

    /** Turn-by-turn camera on or off. [heightPx] places the car low on the screen. */
    fun setNavigating(on: Boolean, heightPx: Int) {
        navigating = on
        if (heightPx > 0) viewHeight = heightPx
        val m = map ?: return
        if (tracking) applyNavCamera(m) else pending?.let { moveTo(m, it) }
    }

    private fun applyNavCamera(map: MapLibreMap) {
        try {
            val lc = map.locationComponent
            val top = if (navigating && viewHeight > 0) viewHeight * (2 * NAV_CAR_FROM_TOP - 1) else 0.0
            lc.paddingWhileTracking(doubleArrayOf(0.0, top, 0.0, 0.0), NAV_ANIM_MS)
            lc.zoomWhileTracking(if (navigating) NAV_ZOOM else FOLLOW_ZOOM, NAV_ANIM_MS)
            lc.tiltWhileTracking(if (navigating) NAV_TILT else 0.0, NAV_ANIM_MS)
        } catch (_: Exception) {
        }
    }

    private fun addParkedLayer(style: Style) {
        try {
            if (style.getImage(PARKED_ICON) == null) style.addImage(PARKED_ICON, parkedPinBitmap(appContext, puckColor))
            if (style.getSource(PARKED_SOURCE) == null) style.addSource(GeoJsonSource(PARKED_SOURCE, parkedGeoJson(parked)))
            if (style.getLayer(PARKED_LAYER) == null) {
                style.addLayer(
                    SymbolLayer(PARKED_LAYER, PARKED_SOURCE).withProperties(
                        PropertyFactory.iconImage(PARKED_ICON),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    ),
                )
            }
        } catch (_: Exception) {
            // Without the pin the parked pill still shows where the car is.
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
            if (navigating) applyNavCamera(map)
        } catch (_: Exception) {
            // Without the puck the map still re-centres on each fix.
            tracking = false
        }
    }

    private fun moveTo(map: MapLibreMap, location: Location) {
        val position = CameraPosition.Builder()
            .target(LatLng(location.latitude, location.longitude))
            .zoom(if (navigating) NAV_ZOOM else FOLLOW_ZOOM)
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
        main.removeCallbacks(styleWatchdog)
        main.removeCallbacks(tilesWatchdog)
        tracking = false
        map = null
        view.onDestroy()
    }
}
