package com.vikash.ampli.drive.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.fuel.FuelFilter
import com.vikash.ampli.drive.fuel.FuelFinder
import com.vikash.ampli.drive.fuel.FuelFlags
import com.vikash.ampli.drive.fuel.FuelHit
import com.vikash.ampli.drive.fuel.FuelStation
import com.vikash.ampli.drive.fuel.FuelStats
import com.vikash.ampli.drive.prefs.NavApp
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.Place
import com.vikash.ampli.drive.voice.VoiceActions
import kotlin.math.cos
import kotlin.math.roundToInt

/** Colours of the fuel tags and map pins (same as the approved design), with dark ink on them. */
internal object FuelColors {
    val Cng = Color(0xFF3FB37F)
    val Petrol = Color(0xFFF2A93B)
    val Diesel = Color(0xFFC9DDD0)
    val Ev = Color(0xFF4FA8FF)
    val Ink = Color(0xFF06110C)

    fun of(filter: FuelFilter): Color = when (filter) {
        FuelFilter.Cng -> Cng
        FuelFilter.Petrol -> Petrol
        FuelFilter.Diesel -> Diesel
        FuelFilter.Ev -> Ev
        FuelFilter.All -> Diesel
    }

    /** A pin takes the chosen filter's colour, else the station's rarest fuel: EV, CNG, petrol, diesel. */
    fun pin(flags: Int, filter: FuelFilter): Color = when {
        filter != FuelFilter.All -> of(filter)
        flags and FuelFlags.EV != 0 -> Ev
        flags and FuelFlags.CNG != 0 -> Cng
        flags and FuelFlags.PETROL != 0 -> Petrol
        else -> Diesel
    }
}

/** Where the fuel screen is: the list is ready, or why it is empty. */
enum class FuelStatus { Loading, NoLocation, NoData, Ready }

/**
 * Fuel, CNG and EV charging near the car (like Nawgati), from the offline OpenStreetMap snapshot:
 * nearest first, filter chips, a small pin map and "Go" into navigation. Free.
 */
@Composable
fun FuelScreen(app: DriveApp, onBack: () -> Unit, initialFilter: FuelFilter = FuelFilter.All) {
    val context = LocalContext.current
    val gps = app.speedTracker.state.collectAsStateWithLifecycle()
    val stats by app.fuel.stats.collectAsStateWithLifecycle()
    var filterName by rememberSaveable(initialFilter) { mutableStateOf(initialFilter.name) }
    val filter = FuelFilter.valueOf(filterName)
    // Look again only when the car moved ~200 m, not on every fix.
    val here by remember {
        derivedStateOf {
            gps.value.location?.let { Pair((it.latitude * 500).roundToInt() / 500.0, (it.longitude * 500).roundToInt() / 500.0) }
        }
    }
    var hits by remember { mutableStateOf<List<FuelHit>?>(null) }
    var selected by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { app.fuel.ensureLoaded() }
    LaunchedEffect(filter, here) {
        if (here == null) return@LaunchedEffect
        // [here] only keys the lookup; distances use the exact fix.
        val loc = gps.value.location ?: return@LaunchedEffect
        hits = app.fuel.nearby(loc.latitude, loc.longitude, filter)
        selected = 0
    }
    val status = when {
        here == null -> FuelStatus.NoLocation
        stats.ready && stats.count == 0 -> FuelStatus.NoData
        hits == null -> FuelStatus.Loading
        else -> FuelStatus.Ready
    }
    val car = gps.value.location?.let { it.latitude to it.longitude }
    FuelContent(
        filter = filter,
        onFilter = {
            filterName = it.name
            hits = null
        },
        status = status,
        hits = hits.orEmpty(),
        car = car,
        selected = selected,
        onSelect = { selected = it },
        stats = stats,
        onGo = { hit ->
            val place = Place(stationName(context, hit.station), null, hit.station.lat, hit.station.lon, hit.distanceM / 1000.0)
            if (app.prefs.navApp.value == NavApp.Ampli) {
                // DriveRoot opens the navigation screen when the trip starts.
                app.navigator.start(place)
            } else if (!VoiceActions.startNavigation(context, place)) {
                Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
            }
        },
        onBack = onBack,
    )
}

/** The station's name, or "CNG station" / "EV charger" / "Fuel station" when OpenStreetMap has none. */
fun stationName(context: android.content.Context, s: FuelStation): String = s.name.ifBlank {
    context.getString(
        when {
            s.has(FuelFlags.EV) -> R.string.fuel_unnamed_ev
            s.has(FuelFlags.CNG) && !s.has(FuelFlags.PETROL) -> R.string.fuel_unnamed_cng
            else -> R.string.fuel_unnamed_pump
        },
    )
}

@Composable
private fun stationLabel(s: FuelStation): String = stationName(LocalContext.current, s)

@Composable
internal fun fuelFilterLabel(f: FuelFilter): String = stringResource(
    when (f) {
        FuelFilter.All -> R.string.fuel_filter_all
        FuelFilter.Cng -> R.string.fuel_filter_cng
        FuelFilter.Petrol -> R.string.fuel_filter_petrol
        FuelFilter.Diesel -> R.string.fuel_filter_diesel
        FuelFilter.Ev -> R.string.fuel_filter_ev
    },
)

@Composable
private fun typeLabel(f: FuelFilter): String = if (f == FuelFilter.Ev) stringResource(R.string.fuel_type_ev) else fuelFilterLabel(f)

@Composable
private fun kmLabel(m: Double): String {
    val locale = LocalContext.current.resources.configuration.locales[0]
    return stringResource(R.string.fuel_km, String.format(locale, if (m < 10_000) "%.1f" else "%.0f", m / 1000.0))
}

/** The fuel screen without the app: what the screenshot tests render. */
@Composable
fun FuelContent(
    filter: FuelFilter,
    onFilter: (FuelFilter) -> Unit,
    status: FuelStatus,
    hits: List<FuelHit>,
    car: Pair<Double, Double>?,
    selected: Int,
    onSelect: (Int) -> Unit,
    stats: FuelStats,
    onGo: (FuelHit) -> Unit,
    onBack: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.fuel_title), if (dims.compact) null else stringResource(R.string.fuel_hint), onBack)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            val sel = hits.getOrNull(selected)
            Pane(Modifier.weight(1.15f).fillMaxHeight()) {
                Box(Modifier.fillMaxSize()) {
                    FuelMap(hits, car, selected, filter, Modifier.fillMaxSize())
                    if (sel != null) {
                        FuelDetail(sel, onGo = { onGo(sel) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(dims.d(1.6f)))
                    }
                }
            }
            Pane(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(dims.d(1.8f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
                        for (f in FuelFilter.entries) {
                            Chip(fuelFilterLabel(f), f == filter, { onFilter(f) }, Modifier.weight(if (f == FuelFilter.Ev) 1.5f else 1f))
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (status) {
                            FuelStatus.Ready -> if (hits.isEmpty()) {
                                EmptyNote(stringResource(R.string.fuel_none))
                            } else {
                                val list = rememberLazyListState()
                                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                                    itemsIndexed(hits, key = { _, h -> h.station.id }) { i, h ->
                                        FuelRow(h, filter, i == selected, onClick = { onSelect(i) })
                                    }
                                }
                            }
                            FuelStatus.Loading -> EmptyNote(stringResource(R.string.fuel_loading))
                            FuelStatus.NoLocation -> EmptyNote(stringResource(R.string.radar_waiting_gps))
                            FuelStatus.NoData -> EmptyNote(stringResource(R.string.fuel_no_data))
                        }
                    }
                    val small = dims.t(1.9f, min = 12f)
                    if (stats.count > 0 && stats.dataDate != null && !dims.compact) {
                        Text(stringResource(R.string.fuel_data_date, stats.dataDate, stats.count), color = theme.muted, fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(R.string.fuel_attribution), color = theme.muted, fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = theme.muted, fontSize = dims.t(2.6f, min = 16f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun FuelRow(hit: FuelHit, filter: FuelFilter, selected: Boolean, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val s = hit.station
    val shape = RoundedCornerShape(dims.d(1.8f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .themedTap(shape, if (selected) theme.surfaceHigh else theme.surface.copy(alpha = 0.6f), border = if (selected) theme.accentSoft else theme.border, active = selected, onClick = onClick)
            .padding(horizontal = dims.d(1.4f), vertical = dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Box(
            Modifier.size(maxOf(dims.d(6f), 36.dp)).clip(RoundedCornerShape(dims.d(1.4f))).background(FuelColors.pin(s.flags, filter)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (s.has(FuelFlags.EV)) Icons.Filled.EvStation else Icons.Filled.LocalGasStation, contentDescription = null, tint = FuelColors.Ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(0.4f))) {
            Text(stationLabel(s), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.5f, min = 15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            FuelTags(s)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(kmLabel(hit.distanceM), color = theme.text, fontWeight = FontWeight.ExtraBold, fontSize = dims.t(2.5f, min = 15f), maxLines = 1)
            Text(stringResource(R.string.fuel_min, FuelFinder.etaMinutes(hit.distanceM)), color = theme.muted, fontSize = dims.t(2f, min = 12f), maxLines = 1)
        }
    }
}

/** "CNG" "Petrol" pills, then "Open 24 h". Assumed fuels get a muted "likely". */
@Composable
private fun FuelTags(s: FuelStation) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
        for (t in FuelFinder.types(s.flags)) {
            Text(
                typeLabel(t),
                color = FuelColors.Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = dims.t(1.8f, min = 11f),
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(FuelColors.of(t)).padding(horizontal = dims.d(0.9f), vertical = dims.d(0.15f)),
            )
        }
        val extra = listOfNotNull(
            stringResource(R.string.fuel_likely).takeIf { s.has(FuelFlags.ASSUMED) },
            stringResource(R.string.fuel_open_24h).takeIf { s.has(FuelFlags.OPEN_24H) },
        ).joinToString(" · ")
        if (extra.isNotEmpty()) Text(extra, color = theme.muted, fontSize = dims.t(1.9f, min = 12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FuelDetail(hit: FuelHit, onGo: () -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val s = hit.station
    val sub = buildList {
        add(FuelFinder.types(s.flags).map { typeLabel(it) }.joinToString(", "))
        if (s.has(FuelFlags.OPEN_24H)) add(stringResource(R.string.fuel_open_24h))
        add(kmLabel(hit.distanceM))
        add(stringResource(R.string.fuel_min, FuelFinder.etaMinutes(hit.distanceM)))
    }.filter { it.isNotBlank() }.joinToString(" · ")
    Pane(modifier, blur = false) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = dims.d(1.8f), vertical = dims.d(1.2f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stationLabel(s), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.7f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = theme.muted, fontSize = dims.t(2.1f, min = 13f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BigButton(stringResource(R.string.fuel_go), onGo, icon = Icons.Filled.Navigation)
        }
    }
}

/**
 * A light pin map drawn on a Canvas (no map tiles: cheap on the head unit and works offline):
 * the car in the middle, distance rings, a pin per station and a line to the selected one.
 */
@Composable
private fun FuelMap(hits: List<FuelHit>, car: Pair<Double, Double>?, selected: Int, filter: FuelFilter, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shown = hits.take(MAP_PINS)
    // Outer ring: the farthest pin shown, rounded up to a whole kilometre.
    val ringM = ((shown.maxOfOrNull { it.distanceM } ?: 1_000.0) / 1000.0).let { kotlin.math.ceil(it).coerceAtLeast(1.0) } * 1000.0
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.42f)
            val radius = minOf(size.width, size.height) * 0.38f
            val ring = theme.border.copy(alpha = 0.7f)
            for (k in 1..3) drawCircle(ring, radius * k / 3f, c, style = Stroke(width = 2f))
            // Two faint "roads" through the car, so it reads as a map.
            val road = theme.muted.copy(alpha = 0.18f)
            drawLine(road, Offset(0f, c.y + radius * 0.35f), Offset(size.width, c.y - radius * 0.5f), strokeWidth = 14f, cap = StrokeCap.Round)
            drawLine(road, Offset(c.x - radius * 0.6f, 0f), Offset(c.x - radius * 0.2f, size.height), strokeWidth = 14f, cap = StrokeCap.Round)
            if (car != null) {
                val scale = radius / ringM
                val mPerDegLat = 111_320.0
                val mPerDegLon = mPerDegLat * cos(Math.toRadians(car.first))
                fun at(s: FuelStation) = Offset(
                    c.x + ((s.lon - car.second) * mPerDegLon * scale).toFloat(),
                    c.y - ((s.lat - car.first) * mPerDegLat * scale).toFloat(),
                )
                shown.getOrNull(selected)?.let { sel ->
                    val p = at(sel.station)
                    val mid = Offset((c.x + p.x) / 2f + 24f, (c.y + p.y) / 2f - 24f)
                    val path = Path().apply {
                        moveTo(c.x, c.y)
                        quadraticTo(mid.x, mid.y, p.x, p.y)
                    }
                    drawPath(path, theme.keys?.primaryTop ?: theme.accent, style = Stroke(width = 7f, cap = StrokeCap.Round))
                }
                shown.forEachIndexed { i, h ->
                    if (i == selected) return@forEachIndexed
                    val p = at(h.station)
                    drawCircle(FuelColors.Ink, 15f, p)
                    drawCircle(FuelColors.pin(h.station.flags, filter), 11f, p)
                }
                shown.getOrNull(selected)?.let { h ->
                    val p = at(h.station)
                    drawCircle(FuelColors.Ink, 22f, p)
                    drawCircle(FuelColors.pin(h.station.flags, filter), 17f, p)
                }
            }
            drawCircle(theme.accentSoft.copy(alpha = 0.22f), 30f, c)
            drawCircle(theme.accentSoft, 15f, c)
        }
        if (car != null && shown.isNotEmpty()) {
            Text(
                kmLabel(ringM),
                color = theme.muted,
                fontSize = dims.t(1.9f, min = 12f),
                modifier = Modifier.align(Alignment.TopEnd).padding(dims.d(1.4f)),
            )
        }
    }
}

private const val MAP_PINS = 12
