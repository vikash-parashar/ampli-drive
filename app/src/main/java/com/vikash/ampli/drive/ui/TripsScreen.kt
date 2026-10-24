package com.vikash.ampli.drive.ui

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.Mileage
import com.vikash.ampli.drive.car.Trip
import com.vikash.ampli.drive.car.TripMath
import com.vikash.ampli.drive.nav.SavedPlace
import com.vikash.ampli.drive.nav.SavedPlacesLogic
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.util.Locale
import kotlin.math.roundToInt

/** A trip end's name: a saved place within 200 m, else its coordinates. */
private fun endLabel(context: Context, places: List<SavedPlace>, lat: Double, lon: Double): String =
    SavedPlacesLogic.near(places, lat, lon)?.let { savedPlaceLabel(context, it) }
        ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)

/** "12.4 km" with the locale's decimal sign. */
private fun kmLabel(context: Context, km: Double): String =
    context.getString(R.string.car_distance_km, String.format(context.resources.configuration.locales[0], "%.1f", km))

/** Car › Trips: every drive logged by itself, newest first, with this week's total. */
@Composable
fun TripsScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val trips by app.car.trips.trips.collectAsStateWithLifecycle()
    val fills by app.car.store.fills.collectAsStateWithLifecycle()
    val places by app.places.places.collectAsStateWithLifecycle()
    val kmpl = remember(fills) { Mileage.average(fills) }
    val price = remember(fills) { TripMath.pricePerLitre(fills) }
    val week = remember(trips) { TripMath.thisWeek(trips, System.currentTimeMillis()) }
    val weekCost = TripMath.fuelCost(week.distanceKm, kmpl, price)
    val moving = isMoving(app)
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.trips_title), stringResource(R.string.trips_hint), onBack)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(
                Modifier.weight(0.8f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
            ) {
                Pane(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(dims.d(2.2f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
                        Text(theme.label(stringResource(R.string.trips_week)), style = theme.labelStyle(), fontSize = dims.t(2.1f))
                        Text(kmLabel(context, week.distanceKm), style = theme.numberStyle(), fontSize = dims.t(6f, min = 28f), maxLines = 1)
                        Text(
                            stringResource(R.string.trips_count, week.count) + " · " + formatDuration(context, week.durationMs),
                            fontSize = dims.t(2.5f, min = 15f), color = theme.text,
                        )
                        Text(
                            weekCost?.let { stringResource(R.string.trips_fuel, rupees(it.roundToInt().toDouble())) }
                                ?: stringResource(R.string.trips_fuel_unknown),
                            fontSize = dims.t(2.4f, min = 14f),
                            color = if (weekCost != null) theme.accentSoft else theme.muted,
                        )
                    }
                }
                Text(stringResource(R.string.trips_how, TripMath.KEEP), fontSize = dims.t(2.2f), color = theme.muted)
                if (trips.isNotEmpty() && !moving) {
                    BigButton(
                        stringResource(R.string.trips_clear),
                        { confirmClear = true },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Filled.DeleteSweep,
                        primary = false,
                    )
                }
            }
            Pane(Modifier.weight(1.4f).fillMaxHeight()) {
                if (trips.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(dims.d(3f)), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.trips_empty), fontSize = dims.t(2.8f, min = 16f), color = theme.muted)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = dims.d(2f), vertical = dims.d(1f)),
                        verticalArrangement = Arrangement.spacedBy(dims.d(0.6f)),
                    ) {
                        items(trips, key = { it.startTime }) { t -> TripRow(t, places, TripMath.fuelCost(t.distanceKm, kmpl, price)) }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        val (bg, fg) = dialogColors()
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = bg, titleContentColor = fg, textContentColor = fg,
            title = { Text(stringResource(R.string.trips_clear)) },
            text = { Text(stringResource(R.string.trips_clear_confirm)) },
            confirmButton = { TextButton(onClick = { app.car.trips.clear(); confirmClear = false }) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TripRow(t: Trip, places: List<SavedPlace>, cost: Double?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Icon(Icons.Filled.Route, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(clockLabel(context, t.startTime), fontSize = dims.t(2.6f, min = 15f), color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                endLabel(context, places, t.startLat, t.startLon) + " → " + endLabel(context, places, t.endLat, t.endLon),
                fontSize = dims.t(2.1f, min = 12f), color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                kmLabel(context, t.distanceKm) + " · " + formatDuration(context, t.durationMs),
                style = theme.numberStyle(glowColor = null), fontSize = dims.t(2.6f, min = 15f), maxLines = 1,
            )
            Text(
                stringResource(R.string.trips_avg_top, t.avgKmh.roundToInt(), t.topKmh.roundToInt()) +
                    (cost?.let { " · " + stringResource(R.string.trips_fuel, rupees(it.roundToInt().toDouble())) } ?: ""),
                fontSize = dims.t(2.1f, min = 12f), color = theme.muted, maxLines = 1,
            )
        }
    }
}
