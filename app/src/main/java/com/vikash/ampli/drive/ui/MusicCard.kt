package com.vikash.ampli.drive.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.media.MediaSource
import com.vikash.ampli.drive.media.NowPlaying
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.Ampli
import kotlinx.coroutines.delay

@Composable
fun MusicCard(hub: MediaHub, resumeTick: Int, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val state by hub.state.collectAsStateWithLifecycle()

    // Returning from the notification-access screen: pick up the new permission.
    LaunchedEffect(resumeTick) { hub.refresh() }

    Pane(modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dims.d(2.2f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            val np = state.nowPlaying
            when {
                !state.accessGranted -> AccessNeeded(onAllow = { Launchers.openNotificationAccess(context) })
                np == null -> NothingPlaying(onOpen = { Launchers.openMusic(context, null) })
                else -> {
                    AlbumArt(np, Modifier.fillMaxWidth().weight(1f))
                    Column {
                        Text(
                            np.title ?: "Unknown track",
                            fontSize = dims.t(3.2f, min = 18f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!dims.compact) {
                            val sub = listOfNotNull(np.artist, np.album).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub,
                                    fontSize = dims.t(2.4f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (np.durationMs > 0) ProgressBar(np)
                    Controls(
                        playing = np.isPlaying,
                        onPrev = hub::previous,
                        onPlayPause = hub::playPause,
                        onNext = hub::next,
                    )
                }
            }
            if (state.accessGranted && state.sources.isNotEmpty()) {
                SourceChips(state.sources, state.selectedPackage, onSelect = hub::select)
            }
        }
    }
}

@Composable
private fun AccessNeeded(onAllow: () -> Unit) {
    val dims = LocalDims.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2f), Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.NotificationsActive,
            contentDescription = null,
            tint = Ampli.Blush,
            modifier = Modifier.size(maxOf(dims.d(8f), 40.dp)),
        )
        Text(
            "Allow notification access to show and control the music that's playing.",
            fontSize = dims.t(2.5f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BigButton(text = "Allow access", onClick = onAllow, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NothingPlaying(onOpen: () -> Unit) {
    val dims = LocalDims.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dims.d(2f), Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(maxOf(dims.d(8f), 40.dp)),
        )
        Text(
            "Nothing playing",
            fontSize = dims.t(3f, min = 18f),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Start music on your phone or in a player app and it shows up here.",
            fontSize = dims.t(2.3f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BigButton(text = "Open music", onClick = onOpen, primary = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AlbumArt(np: NowPlaying, modifier: Modifier) {
    val dims = LocalDims.current
    val art = remember(np.art) { np.art?.asImageBitmap() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dims.d(2f)))
            .background(Ampli.Gradient),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(maxOf(dims.d(10f), 48.dp)),
            )
        }
    }
}

@Composable
private fun ProgressBar(np: NowPlaying) {
    val dims = LocalDims.current
    // Ticks once a second while playing; frozen while paused.
    val now by produceState(SystemClock.elapsedRealtime(), np) {
        value = SystemClock.elapsedRealtime()
        while (np.isPlaying) {
            delay(1000L)
            value = SystemClock.elapsedRealtime()
        }
    }
    val fraction = (np.positionAt(now).toFloat() / np.durationMs.toFloat()).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(maxOf(dims.d(0.8f), 4.dp))
            .clip(CircleShape)
            .background(Ampli.Slate),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(maxOf(dims.d(0.8f), 4.dp))
                .background(Ampli.Rose),
        )
    }
}

@Composable
private fun Controls(playing: Boolean, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    val dims = LocalDims.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Filled.SkipPrevious, "Previous", onPrev, maxOf(dims.d(8f), MinTouch), filled = false)
        RoundButton(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (playing) "Pause" else "Play",
            onPlayPause,
            maxOf(dims.d(10f), 72.dp),
            filled = true,
        )
        RoundButton(Icons.Filled.SkipNext, "Next", onNext, maxOf(dims.d(8f), MinTouch), filled = false)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Ampli.Rose else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (filled) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** One chip per app with a media session; tapping switches the card to it. */
@Composable
private fun SourceChips(sources: List<MediaSource>, selected: String?, onSelect: (String) -> Unit) {
    val dims = LocalDims.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (source in sources) {
            val on = source.packageName == selected
            // 64 dp tall touch area around a slimmer visible chip.
            Box(
                modifier = Modifier
                    .heightIn(min = MinTouch)
                    .clickable { onSelect(source.packageName) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    source.label,
                    fontSize = dims.t(2.1f, min = 14f),
                    color = if (on) Ampli.Blush else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) Ampli.Rose.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, if (on) Ampli.Blush.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .padding(horizontal = dims.d(1.8f), vertical = dims.d(1f)),
                )
            }
        }
    }
}
