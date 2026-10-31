package com.vikash.ampli.drive.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.map.GlanceMap
import com.vikash.ampli.drive.map.MapDiagnostics
import com.vikash.ampli.drive.nav.NavSession
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.nav.NextTurn
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

@Composable
fun MapPane(
    gps: GpsState,
    onOpenNavigation: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    showTurn: Boolean = true,
    /** Parked car (lat, lon), pinned on the map. */
    parked: Pair<Double, Double>? = null,
    textureMode: Boolean = true,
    onNoFrames: () -> Unit = {},
    /** Car Hub's own navigation: route on the map and a compact card instead of Maps' turn. */
    nav: NavSession = NavSession(),
    onOpenNav: () -> Unit = {},
    /** Map First: the map fills the whole screen behind the home cards, with no card frame. */
    fullBleed: Boolean = false,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val turn by NavTurnRepository.turn.collectAsStateWithLifecycle()
    val location = gps.location
    // Bento Grid and Road Sign draw the map tile with the theme's black border.
    val frame = if (theme.borderWidth > 0.dp && !fullBleed) {
        Modifier.border(theme.borderWidth, theme.border, theme.cardShape(dims))
    } else {
        Modifier
    }

    // The map covers the pane, so there is nothing for glass to blur here.
    val body: @Composable (@Composable () -> Unit) -> Unit = { content ->
        // Full-bleed: Map First draws its drawn map underneath, so nothing here paints a background.
        if (fullBleed) Box(modifier) { content() } else Pane(modifier.then(frame), fill = theme.background, blur = false) { content() }
    }
    body {
        Box(Modifier.fillMaxSize()) {
            when {
                !gps.hasPermission -> MapMessage(
                    text = stringResource(R.string.map_allow_text),
                    button = stringResource(R.string.map_allow_button),
                    onClick = onRequestPermissions,
                    card = fullBleed,
                )
                location == null -> MapMessage(
                    text = stringResource(
                        if (gps.providerEnabled || !gps.hasGps) R.string.map_waiting_fix else R.string.map_location_off,
                    ),
                    button = stringResource(R.string.map_open_google_maps),
                    onClick = onOpenNavigation,
                    card = fullBleed,
                )
                LocalMapPreview.current -> {
                    MapPlaceholder(Modifier.fillMaxSize(), route = nav.visible, routeColor = theme.mapPuck)
                    Box(Modifier.fillMaxSize().clickable(onClick = if (nav.visible) onOpenNav else onOpenNavigation))
                }
                else -> {
                    val navigating = nav.phase == NavSession.Phase.Active
                    GlanceMap(
                        location = homeShownLocation(location, nav),
                        styleUrl = theme.map.url,
                        puckColor = theme.mapPuck.toArgb(),
                        parked = parked,
                        textureMode = textureMode,
                        onNoFrames = onNoFrames,
                        route = if (nav.visible) nav.route?.points else null,
                        routeKey = nav.routeVersion,
                        routeColor = theme.mapPuck.toArgb(),
                        navigating = navigating,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Tint so the tiles sit in the theme's colour world (green HUD, magenta synthwave…).
                    if (theme.mapTint.alpha > 0f) Box(Modifier.fillMaxSize().background(theme.mapTint))
                    // Taps anywhere on the glance map: the navigation screen while navigating, else
                    // the destination search (Ampli map) or Google Maps.
                    Box(Modifier.fillMaxSize().clickable(onClick = if (nav.visible) onOpenNav else onOpenNavigation))
                    val mapStatus by MapDiagnostics.status.collectAsStateWithLifecycle()
                    Text(
                        if (mapStatus.basic) stringResource(R.string.map_basic_note) + " · © OpenStreetMap contributors" else GLANCE_ATTRIBUTION,
                        fontSize = dims.t(1.6f, min = 10f),
                        color = Color(0xFF3F3A44),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            // Full-bleed Home: the cards cover the bottom corners, so the credit sits top centre.
                            .align(if (fullBleed) Alignment.TopCenter else Alignment.BottomEnd)
                            .padding(dims.d(1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    // Theme style and fallback both failed: say so instead of a blank or grey pane.
                    if (mapStatus.unavailable) {
                        Box(Modifier.fillMaxSize().then(if (fullBleed) Modifier else Modifier.background(theme.background))) {
                            if (fullBleed) MapPlaceholder(Modifier.fillMaxSize())
                            MapMessage(
                                text = stringResource(R.string.map_tiles_unavailable),
                                button = stringResource(R.string.map_open_google_maps),
                                onClick = onOpenNavigation,
                                card = fullBleed,
                            )
                        }
                    }
                }
            }

            val current = turn
            if (nav.visible && showTurn) {
                NavHomeCard(
                    session = nav,
                    onClick = onOpenNav,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(dims.d(2f)),
                )
            } else if (current != null && showTurn && !nav.visible) {
                NextTurnCard(
                    turn = current,
                    onClick = onOpenNavigation,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(dims.d(2f)),
                )
            }
            if (fullBleed) {
                // Map First draws its own turn card and "Where to?".
            } else if (current?.subText != null) {
                Pill(
                    text = current.subText,
                    background = theme.surface,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = dims.d(2f), bottom = dims.d(5f)),
                )
            } else if (current == null && location != null && !nav.visible) {
                Pill(
                    text = stringResource(R.string.map_tap_to_navigate),
                    icon = Icons.Filled.Navigation,
                    iconTint = theme.accentSoft,
                    background = theme.surface,
                    onClick = onOpenNavigation,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = dims.d(2f), bottom = dims.d(5f)),
                )
            }
        }
    }
}

@Composable
private fun MapMessage(text: String, button: String, onClick: () -> Unit, card: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    if (card) {
        // Map First: a solid card in the middle of the drawn map.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LocationPromptCard(text, button, onClick)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2.4f), Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = theme.accentSoft,
            modifier = Modifier.size(maxOf(dims.d(8f), 40.dp)),
        )
        Text(
            text,
            fontSize = dims.t(2.8f, min = 16f),
            color = theme.text,
            textAlign = TextAlign.Center,
        )
        BigButton(text = button, onClick = onClick)
    }
}

/**
 * The next manoeuvre from Google Maps' notification. Each theme paints it its own way:
 * rose gradient, glass, solid navy, red cut-corner, neon outline, saffron tile, soft shadow…
 */
@Composable
fun NextTurnCard(turn: NextTurn, onClick: () -> Unit, modifier: Modifier = Modifier, large: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = theme.cardShape(dims, scale = 0.8f)
    val arrow = remember(turn.arrow) { turn.arrow?.asImageBitmap() }
    val scale = if (large) 1.6f else 1f
    Row(
        modifier = modifier
            // Sits on the map, which is a SurfaceView and can't be blurred: glass themes use a near-opaque tint.
            .themedCard(theme, shape, fill = theme.turnBackground, border = theme.turnBorder, accentEdge = false, blur = false)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1.8f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(
            Modifier
                .size(maxOf(dims.d(9f * scale), 48.dp))
                .clip(theme.cardShape(dims, scale = 0.45f))
                .background(theme.turnTile),
            contentAlignment = Alignment.Center,
        ) {
            if (arrow != null) {
                Image(
                    arrow,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(theme.turnTileText),
                    modifier = Modifier.size(maxOf(dims.d(6f * scale), 32.dp)),
                )
            } else {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = theme.turnTileText,
                    modifier = Modifier.size(maxOf(dims.d(6f * scale), 32.dp)),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                turn.title,
                style = theme.numberStyle(theme.turnText),
                fontSize = dims.t(4.6f * scale, min = 20f),
                fontWeight = if (theme.numberWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            turn.text?.let {
                Text(
                    it,
                    fontSize = dims.t(2.6f * (if (large) 1.3f else 1f), min = 16f),
                    color = theme.turnMuted,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (dims.compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.map_via_google_maps),
                // Attribution: may stay small.
                fontSize = dims.t(1.9f, min = 11f),
                color = theme.turnMuted.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}
