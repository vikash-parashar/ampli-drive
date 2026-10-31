package com.vikash.ampli.drive.ui

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.media.MediaHub
import com.vikash.ampli.drive.media.MediaSource
import com.vikash.ampli.drive.media.NowPlaying
import com.vikash.ampli.drive.system.Launchers
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape as Rounded
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.FontChoice
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay

@Composable
fun MusicCard(
    hub: MediaHub,
    resumeTick: Int,
    modifier: Modifier = Modifier,
    /** A phone connected over Bluetooth: with no player session, the card shows "Phone music". */
    phoneName: String? = null,
    onPhoneKey: (Int) -> Unit = {},
) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val state by hub.state.collectAsStateWithLifecycle()

    // Returning from the notification-access screen: pick up the new permission.
    LaunchedEffect(resumeTick) { hub.refresh() }

    // Saffron themes draw this card on cream "paper"; everything inside reads the paper colours.
    val outer = LocalDriveTheme.current
    val paper = remember(outer) { outer.forMusicCard() }
    Pane(modifier, fill = paper.surface) {
      androidx.compose.runtime.CompositionLocalProvider(LocalDriveTheme provides paper) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                onSurface = paper.text,
                onSurfaceVariant = paper.muted,
                surfaceContainerHigh = paper.surfaceHigh,
            ),
            typography = MaterialTheme.typography,
        ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dims.d(2.2f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            val np = state.nowPlaying
            when {
                np == null && phoneName != null -> PhoneMusic(phoneName, onPhoneKey)
                !state.accessGranted -> AccessNeeded(onAllow = { Launchers.openNotificationAccess(context) })
                np == null -> NothingPlaying(onOpen = { Launchers.openMusic(context) })
                else -> {
                    AlbumArt(np, Modifier.fillMaxWidth().weight(1f))
                    Column {
                        Text(
                            np.title ?: stringResource(R.string.music_unknown_track),
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
                                    fontSize = dims.t(2.4f, min = 16f),
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
    }
}

/**
 * "Phone music": songs from the phone over Bluetooth when no player shares its session. The
 * buttons send media keys, which the unit's Bluetooth stack passes to the phone (AVRCP).
 */
@Composable
internal fun PhoneMusic(phoneName: String, onKey: (Int) -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val playing by produceState(false) {
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        while (true) {
            value = audio?.isMusicActive == true
            delay(1_000L)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Short card (map-on-top layouts): just the name and the buttons.
        val roomy = maxHeight >= 250.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterVertically),
        ) {
            if (roomy) {
                Icon(
                    Icons.Filled.BluetoothAudio,
                    contentDescription = null,
                    tint = theme.accentSoft,
                    modifier = Modifier.size(maxOf(dims.d(8f), 40.dp)),
                )
            }
            Text(
                stringResource(R.string.phone_music_title),
                fontSize = dims.t(3f, min = 18f),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.phone_music_from, phoneName),
                fontSize = dims.t(2.3f, min = 16f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = if (roomy) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Controls(
                playing = playing,
                onPrev = { onKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
                onPlayPause = { onKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                onNext = { onKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) },
            )
        }
    }
}

@Composable
private fun AccessNeeded(onAllow: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val density = LocalDensity.current
    val gap = dims.d(2f)
    val textSize = dims.t(2.5f, min = 16f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The button keeps a full touch target; the icon goes first and then text lines when the
        // card is short (e.g. while the default-home banner takes space), ending with "…".
        // The icon is decoration only (not tappable); the one touch target is the button, which
        // keeps the 64 dp driving minimum.
        val iconSize = maxOf(dims.d(8f), 40.dp)
        val buttonH = MinTouch
        val lineH = with(density) { (textSize * 1.45f).toDp() }
        val withIcon = maxHeight - buttonH - gap >= iconSize + gap + lineH * 2
        val forText = maxHeight - buttonH - gap - if (withIcon) iconSize + gap else 0.dp
        val lines = (forText / lineH).toInt().coerceIn(0, 3)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
        ) {
            if (withIcon) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = theme.accentSoft,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (lines > 0) {
                Text(
                    stringResource(R.string.music_access_text),
                    fontSize = textSize,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BigButton(
                text = stringResource(R.string.allow_access),
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().heightIn(min = buttonH),
            )
        }
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
            stringResource(R.string.music_nothing_playing),
            fontSize = dims.t(3f, min = 18f),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.music_nothing_hint),
            fontSize = dims.t(2.3f, min = 16f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        BigButton(
            text = stringResource(R.string.music_open),
            onClick = onOpen,
            primary = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
        )
    }
}

@Composable
private fun AlbumArt(np: NowPlaying, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val art = remember(np.art) { np.art?.asImageBitmap() }
    Box(
        modifier = modifier
            .clip(theme.cardShape(dims, scale = 0.7f))
            .background(Brush.linearGradient(listOf(theme.accent, theme.gauge.last(), theme.background))),
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
    val theme = LocalDriveTheme.current
    if (theme.font == FontChoice.Mono) {
        // HUD: segmented bar like an LED meter.
        Canvas(Modifier.fillMaxWidth().height(maxOf(dims.d(2.4f), 12.dp))) {
            val seg = 9.dp.toPx()
            val gap = 3.dp.toPx()
            var x = 0f
            while (x + seg <= size.width) {
                val lit = (x + seg / 2f) / size.width <= fraction
                drawRect(if (lit) theme.accent else theme.accent.copy(alpha = 0.2f), Offset(x, 0f), Size(seg, size.height))
                x += seg + gap
            }
        }
        return
    }
    val barShape: Shape = if (theme.radius <= 0f) RectangleShape else CircleShape
    val h = if (theme.cardStyle == CardStyle.Neumorphic) maxOf(dims.d(1.6f), 8.dp) else maxOf(dims.d(0.8f), 4.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .then(if (theme.isSoft) Modifier.softWell(theme, barShape, SoftDepth.Groove) else Modifier.clip(barShape).background(theme.track)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(h)
                .background(theme.accent2),
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
        RoundButton(Icons.Filled.SkipPrevious, stringResource(R.string.music_previous), onPrev, maxOf(dims.d(8f), MinTouch), filled = false)
        RoundButton(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (playing) R.string.music_pause else R.string.music_play),
            onPlayPause,
            maxOf(dims.d(10f), 72.dp),
            filled = true,
            active = playing,
        )
        RoundButton(Icons.Filled.SkipNext, stringResource(R.string.music_next), onNext, maxOf(dims.d(8f), MinTouch), filled = false)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    active: Boolean = false,
) {
    val theme = LocalDriveTheme.current
    val dims = LocalDims.current
    val keys = theme.keys
    if (keys != null && filled && theme.isSoft) {
        // Crimson Drive: play/pause is the crimson key, white icon.
        Box(
            modifier = Modifier.size(size).primaryKey(theme, keys, CircleShape, onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = keys.onPrimary, modifier = Modifier.size(size * 0.5f))
        }
        return
    }
    if (theme.isSoft) {
        // Soft key; the play/pause key stays pressed in (cyan) while music plays.
        Box(
            modifier = Modifier.size(size).softClickable(theme, CircleShape, onClick, active = active),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = if (active) theme.accentSoft else theme.text, modifier = Modifier.size(size * 0.5f))
        }
        return
    }
    val shape: Shape = when {
        theme.cardStyle == CardStyle.Skewed -> skewShape()
        theme.cardStyle == CardStyle.Solid && theme.isLight -> Rounded(dims.d(theme.radius * 0.6f))
        theme.radius <= 0f -> RectangleShape
        else -> CircleShape
    }
    val outlined = !filled && (theme.cardStyle == CardStyle.Outline || theme.cardStyle == CardStyle.NeonGlow)
    val outlineColor = if (theme.cardStyle == CardStyle.NeonGlow) theme.accent2 else theme.border
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (theme.cardStyle == CardStyle.Neumorphic) {
                    Modifier.softShadow(shape, theme.surface, theme.shadowDark, theme.shadowLight, 4.dp, 8.dp)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(
                when {
                    filled && theme.cardStyle == CardStyle.Neumorphic -> theme.surface
                    filled -> theme.accent
                    theme.cardStyle == CardStyle.Neumorphic || theme.cardStyle == CardStyle.Skewed -> theme.surfaceHigh
                    else -> Color.Transparent
                },
            )
            .then(if (outlined) Modifier.border(2.dp, outlineColor, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = when {
                filled && theme.cardStyle == CardStyle.Neumorphic -> theme.accent
                filled -> theme.onAccent
                theme.cardStyle == CardStyle.NeonGlow -> theme.accent2
                else -> theme.text
            },
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** One chip per app with a media session; tapping switches the card to it. */
@Composable
private fun SourceChips(sources: List<MediaSource>, selected: String?, onSelect: (String) -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val chipShape: Shape = if (theme.radius <= 0f) RectangleShape else CircleShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (source in sources) {
            val on = source.packageName == selected
            // 64 x 64 dp minimum touch area around a slimmer visible chip (short labels too).
            Box(
                modifier = Modifier
                    .heightIn(min = MinTouch)
                    .widthIn(min = MinTouch)
                    .clickable { onSelect(source.packageName) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    source.label,
                    fontSize = dims.t(2.1f, min = 16f),
                    color = if (on) theme.accentSoft else theme.muted,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(chipShape)
                        .background(if (on) theme.accent.copy(alpha = 0.16f) else theme.surfaceHigh)
                        .border(1.dp, if (on) theme.accentSoft.copy(alpha = 0.45f) else theme.border, chipShape)
                        .padding(horizontal = dims.d(1.8f), vertical = dims.d(1f)),
                )
            }
        }
    }
}
