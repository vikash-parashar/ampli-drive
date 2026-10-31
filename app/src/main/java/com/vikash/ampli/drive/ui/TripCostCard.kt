package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.tripcost.TripCost
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Pro "trip cost before you drive": tolls, fuel and FASTag Annual Pass savings for the planned
 * route. Parked only: while [moving] it draws nothing (the summary is spoken instead). Behind
 * [com.vikash.ampli.drive.tripcost.TripCostFlags.ENABLED], which is off; no screen uses it yet.
 */
@Composable
fun TripCostCard(cost: TripCost, moving: Boolean, modifier: Modifier = Modifier) {
    if (moving) return
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(Modifier.padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                Text(
                    stringResource(R.string.trip_cost_title),
                    fontSize = dims.t(3.2f, min = 18f),
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                if (cost.example) {
                    Text(
                        stringResource(R.string.trip_cost_example),
                        color = theme.warning,
                        fontWeight = FontWeight.Bold,
                        fontSize = dims.t(2.2f, min = 13f),
                        modifier = Modifier
                            .border(2.dp, theme.warning, RoundedCornerShape(50))
                            .padding(horizontal = dims.d(1.4f), vertical = dims.d(0.4f)),
                    )
                }
            }
            Text(
                stringResource(R.string.trip_cost_route, km(cost.distanceKm)),
                color = theme.muted,
                fontSize = dims.t(2.4f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)), modifier = Modifier.fillMaxWidth()) {
                CostTile(
                    icon = Icons.Filled.Toll,
                    label = stringResource(R.string.trip_cost_tolls, cost.tolls),
                    value = rupees(cost.tollRupees),
                    modifier = Modifier.weight(1f),
                )
                CostTile(
                    icon = Icons.Filled.LocalGasStation,
                    label = stringResource(R.string.trip_cost_fuel),
                    value = cost.fuelRupees?.let { "≈ " + rupees(it) } ?: stringResource(R.string.trip_cost_fuel_unknown),
                    modifier = Modifier.weight(1f),
                )
                CostTile(
                    icon = Icons.Filled.Savings,
                    label = stringResource(if (cost.hasPass) R.string.trip_cost_pass_saves else R.string.trip_cost_pass_would_save),
                    value = rupees(cost.passSavingRupees),
                    sub = stringResource(R.string.trip_cost_pass_trips, cost.passTrips),
                    highlight = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.trip_cost_total),
                    color = theme.text,
                    fontSize = dims.t(2.8f, min = 16f),
                    modifier = Modifier.weight(1f),
                )
                Text(rupees(cost.totalRupees), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.4f, min = 18f))
            }
            Text(
                stringResource(if (cost.example) R.string.trip_cost_example_note else R.string.trip_cost_note),
                color = theme.muted,
                fontSize = dims.t(2.1f, min = 13f),
            )
        }
    }
}

@Composable
private fun CostTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    highlight: Boolean = false,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier
            .background(if (highlight) theme.accent.copy(alpha = 0.18f) else theme.surfaceHigh, RoundedCornerShape(dims.d(1.6f)))
            .padding(dims.d(1.6f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(0.6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
            Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(24.dp))
            Text(label, color = theme.muted, fontSize = dims.t(2.2f, min = 13f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(3.6f, min = 20f), maxLines = 1)
        if (sub != null) Text(sub, color = theme.muted, fontSize = dims.t(2f, min = 12f), maxLines = 1)
    }
}

private fun rupees(v: Int): String = "₹" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(v)

private fun km(v: Double): String = if (v >= 10) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
