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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.location.GpsState
import com.vikash.ampli.drive.map.GLANCE_ATTRIBUTION
import com.vikash.ampli.drive.map.GlanceMap
import com.vikash.ampli.drive.nav.NavTurnRepository
import com.vikash.ampli.drive.nav.NextTurn
import com.vikash.ampli.drive.ui.theme.Ampli

@Composable
fun MapPane(
    gps: GpsState,
    night: Boolean,
    onOpenNavigation: () -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dims = LocalDims.current
    val turn by NavTurnRepository.turn.collectAsStateWithLifecycle()
    val location = gps.location

    Pane(modifier) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0A10))) {
            when {
                !gps.hasPermission -> MapMessage(
                    text = "Allow location to see the map and your speed",
                    button = "Allow location",
                    onClick = onRequestPermissions,
                )
                location == null -> MapMessage(
                    text = if (gps.providerEnabled || !gps.hasGps) "Waiting for a GPS fix…" else "Turn on location to see the map",
                    button = "Open Google Maps",
                    onClick = onOpenNavigation,
                )
                else -> {
                    GlanceMap(location = location, modifier = Modifier.fillMaxSize())
                    // A light map at night glares; dim it instead of loading a second style.
                    if (night) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                    // Taps anywhere on the glance map hand off to Google Maps.
                    Box(Modifier.fillMaxSize().clickable(onClick = onOpenNavigation))
                    Text(
                        GLANCE_ATTRIBUTION,
                        fontSize = dims.t(1.6f, min = 10f),
                        color = Color(0xFF3F3A44),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(dims.d(1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.75f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            val current = turn
            if (current != null) {
                NextTurnCard(
                    turn = current,
                    onClick = onOpenNavigation,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(dims.d(2f)),
                )
                current.subText?.let { eta ->
                    Pill(
                        text = eta,
                        background = Ampli.NightRaised.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = dims.d(2f), bottom = dims.d(5f)),
                    )
                }
            } else if (location != null) {
                Pill(
                    text = "Tap to navigate",
                    icon = Icons.Filled.Navigation,
                    iconTint = Ampli.Blush,
                    background = Ampli.NightRaised.copy(alpha = 0.92f),
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
private fun MapMessage(text: String, button: String, onClick: () -> Unit) {
    val dims = LocalDims.current
    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(3f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2.4f), Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = Ampli.Blush,
            modifier = Modifier.size(maxOf(dims.d(8f), 40.dp)),
        )
        Text(
            text,
            fontSize = dims.t(2.8f, min = 16f),
            color = Ampli.Text,
            textAlign = TextAlign.Center,
        )
        BigButton(text = button, onClick = onClick)
    }
}

/** The next manoeuvre from Google Maps' notification, styled like the mockup's turn card. */
@Composable
private fun NextTurnCard(turn: NextTurn, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val shape = RoundedCornerShape(dims.d(2.2f))
    val arrow = remember(turn.arrow) { turn.arrow?.asImageBitmap() }
    Row(
        modifier = modifier
            .clip(shape)
            .background(Ampli.NightRaised.copy(alpha = 0.94f))
            .border(1.dp, Color(0x12FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1.8f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(
            Modifier
                .size(maxOf(dims.d(9f), 48.dp))
                .clip(RoundedCornerShape(dims.d(1.8f)))
                .background(Ampli.Rose),
            contentAlignment = Alignment.Center,
        ) {
            if (arrow != null) {
                Image(arrow, contentDescription = null, modifier = Modifier.size(maxOf(dims.d(6f), 32.dp)))
            } else {
                Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(maxOf(dims.d(6f), 32.dp)))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                turn.title,
                fontSize = dims.t(4.6f, min = 20f),
                fontWeight = FontWeight.ExtraBold,
                color = Ampli.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            turn.text?.let {
                Text(
                    it,
                    fontSize = dims.t(2.6f),
                    color = Ampli.Muted,
                    maxLines = if (dims.compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "via Google Maps",
                fontSize = dims.t(1.9f, min = 11f),
                color = Ampli.Muted.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
    }
}
