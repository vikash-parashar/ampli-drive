package com.vikash.ampli.drive.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.fastag.FastagStore
import com.vikash.ampli.drive.obd.ObdState
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.HomeStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Home's More sheet: every feature that left the Home dock, as twelve large tiles (4 x 3).
 * Drawn in the active theme; no slide animation and no blur, so it opens at once on slow units.
 * Back, Close or a tap above the sheet closes it.
 */
@Composable
fun MoreSheet(app: DriveApp, onDismiss: () -> Unit, onTile: (MoreTile) -> Unit, onPhoneLong: () -> Unit = {}) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    BackHandler(enabled = true, onBack = onDismiss)
    val hints = moreHints(app)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val radius = if (theme.radius <= 0f) 0.dp else dims.d(4.4f)
        val shape = if (radius == 0.dp) RectangleShape else RoundedCornerShape(topStart = radius, topEnd = radius)
        // Map First's sheet is the slate card colour, solid: it sits over the live map.
        val sheetColor = if (theme.style == HomeStyle.MapFirst) theme.background else theme.background
        Column(
            Modifier
                .padding(horizontal = dims.d(3f))
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .then(if (theme.cardStyle == CardStyle.Brutal) Modifier.hardShadow(shape, theme.border) else Modifier)
                .themedSurface(theme, shape, sheetColor, border = if (theme.borderWidth > 0.dp) theme.border else null, borderWidth = theme.borderWidth)
                // Taps inside the sheet must not close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(dims.d(3.2f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(2.2f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(3f))) {
                Text(
                    stringResource(R.string.more_title),
                    color = theme.text,
                    fontFamily = theme.numberFont.family,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = dims.t(5.4f, min = 26f),
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.more_say),
                    color = theme.muted,
                    fontSize = dims.t(2.8f, min = 16f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .heightIn(min = MinTouch)
                        .widthIn(min = dims.d(18f))
                        .themedTap(if (theme.radius <= 0f) RectangleShape else CircleShape, theme.accent, onClick = onDismiss)
                        .padding(horizontal = dims.d(3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.action_close), color = theme.onAccent, fontWeight = FontWeight.Bold, fontSize = dims.t(3.2f, min = 18f))
                }
            }
            val gap = dims.d(2f)
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in MoreTile.entries.chunked(MoreTile.COLUMNS)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (tile in row) {
                            MoreTileView(
                                title = moreTitle(tile),
                                hint = hints[tile],
                                icon = moreIcon(tile),
                                onClick = { onTile(tile) },
                                onLongClick = if (tile == MoreTile.Phone) onPhoneLong else null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreTileView(title: String, hint: String?, icon: ImageVector, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, 0.6f)
    // Tile colours per theme, as designed: white tiles with a black line on Bento and Road Sign,
    // a lighter well on Big Glance, a faint white well on Map First.
    val fill = when (theme.style) {
        HomeStyle.BigGlance -> theme.surfaceHigh
        HomeStyle.MapFirst -> Color.White.copy(alpha = 0.08f)
        HomeStyle.Bento, HomeStyle.RoadSign -> Color.White
    }
    val edge = when (theme.style) {
        HomeStyle.MapFirst -> theme.border
        HomeStyle.BigGlance -> null
        else -> theme.border
    }
    Row(
        modifier
            .heightIn(min = MinTouch)
            .then(if (theme.cardStyle == CardStyle.Brutal) Modifier.hardShadow(shape, theme.border) else Modifier)
            .themedTap(shape, fill, border = edge, borderWidth = if (theme.borderWidth > 0.dp) theme.borderWidth else 2.dp, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = dims.d(2.4f), vertical = dims.d(1.4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Icon(icon, contentDescription = null, tint = if (theme.style == HomeStyle.RoadSign || theme.style == HomeStyle.Bento) theme.accent else theme.accentSoft, modifier = Modifier.size(maxOf(dims.d(5.6f), 32.dp)))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = theme.text,
                fontFamily = theme.font.family,
                fontWeight = FontWeight.Bold,
                fontSize = dims.t(3.3f, min = 20f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = dims.t(3.6f, min = 22f),
            )
            if (hint != null) {
                Text(hint, color = theme.muted, fontSize = dims.t(2.6f, min = 16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.size(0.dp))
    }
}

@Composable
private fun moreTitle(tile: MoreTile): String = stringResource(
    when (tile) {
        MoreTile.Fastag -> R.string.fastag_title
        MoreTile.Fuel -> R.string.more_fuel
        MoreTile.Radar -> R.string.more_radar
        MoreTile.Papers -> R.string.more_papers
        MoreTile.Trips -> R.string.trips_title
        MoreTile.Expenses -> R.string.more_expenses
        MoreTile.CarHealth -> R.string.more_health
        MoreTile.Tyres -> R.string.tyres_title
        MoreTile.Phone -> R.string.dock_phone
        MoreTile.PhoneLink -> R.string.dock_phone_link
        MoreTile.Apps -> R.string.dock_apps
        MoreTile.Settings -> R.string.dock_settings
    },
)

private fun moreIcon(tile: MoreTile): ImageVector = when (tile) {
    MoreTile.Fastag -> Icons.Filled.Toll
    MoreTile.Fuel -> Icons.Filled.LocalGasStation
    MoreTile.Radar -> Icons.Filled.Radar
    MoreTile.Papers -> Icons.Filled.Description
    MoreTile.Trips -> Icons.Filled.Route
    MoreTile.Expenses -> Icons.Filled.CurrencyRupee
    MoreTile.CarHealth -> Icons.Filled.DirectionsCar
    MoreTile.Tyres -> Icons.Filled.TireRepair
    MoreTile.Phone -> Icons.Filled.Phone
    MoreTile.PhoneLink -> Icons.Filled.PhonelinkRing
    MoreTile.Apps -> Icons.Filled.Apps
    MoreTile.Settings -> Icons.Filled.Settings
}

/** The small second line on each tile: live where it is cheap to know, else what is inside. */
@Composable
private fun moreHints(app: DriveApp): Map<MoreTile, String> {
    val context = LocalContext.current
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val cameras by app.prefs.cameraAlerts.collectAsStateWithLifecycle()
    val obd by app.obd.state.collectAsStateWithLifecycle()
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val devices by app.phoneBt.devices.collectAsStateWithLifecycle()
    val due = remember(papers, services) { app.car.dueItems(context).firstOrNull() }
    val balance = FastagStore.latestBalance(events)?.balance
    val phone = app.phoneBt.connectedPhone(devices)?.name
    return buildMap {
        put(MoreTile.Fastag, balance?.let { stringResource(R.string.fastag_balance_value, rupees(it)) } ?: stringResource(R.string.more_fastag_hint))
        put(MoreTile.Fuel, stringResource(R.string.more_fuel_hint))
        put(MoreTile.Radar, stringResource(if (cameras) R.string.more_alerts_on else R.string.more_alerts_off))
        put(MoreTile.Papers, due?.let { CarMonitor.dueLine(context, it) } ?: stringResource(R.string.more_papers_hint))
        put(MoreTile.Trips, stringResource(R.string.more_trips_hint))
        put(MoreTile.Expenses, stringResource(R.string.more_expenses_hint))
        put(MoreTile.CarHealth, if (obd.phase == ObdState.Phase.Connected) obd.deviceName ?: stringResource(R.string.obd_title) else stringResource(R.string.more_health_link))
        put(MoreTile.Tyres, stringResource(R.string.more_tyres_hint))
        put(MoreTile.Phone, stringResource(R.string.more_phone_hint))
        put(MoreTile.PhoneLink, phone ?: stringResource(R.string.more_link_hint))
        put(MoreTile.Apps, stringResource(R.string.more_apps_hint))
        put(MoreTile.Settings, stringResource(R.string.more_settings_hint))
    }
}
