package com.vikash.ampli.drive.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.HaryanviLines
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Settings › Voice › Hear samples: one row per event of the Haryanvi personality, grouped like
 * the review file. A tap plays a line of that event (whatever the voice style), so the owner can
 * hear the car's lines without driving. The list unfolds with a spring, rows slide in one after
 * another, and the row that is speaking glows and shows moving sound bars.
 */
@Composable
internal fun DesiSamplesCard(app: DriveApp, initiallyOpen: Boolean = false) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    val playing by app.desi.sampling.collectAsStateWithLifecycle()
    val arrow by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "arrow")
    val groups = remember {
        HaryanviLines.Event.entries
            .filter { HaryanviLines.forEvent(it) != null }
            .groupBy { it.group }
    }
    SettingCard {
        Column(
            Modifier.animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.voice_samples_title), stringResource(R.string.voice_samples_hint))
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (open) R.string.voice_samples_hide else R.string.voice_samples_show),
                    tint = theme.text,
                    modifier = Modifier.size(32.dp).rotate(arrow),
                )
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
                    var index = 0
                    for ((group, events) in groups) {
                        Text(
                            group.title,
                            color = theme.accentSoft,
                            fontSize = dims.t(2.3f, min = 13f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = dims.d(1f)),
                        )
                        for (event in events) {
                            SampleRow(
                                event = event,
                                order = index++,
                                speaking = playing?.takeIf { it.first == event }?.second,
                                onPlay = { app.desi.sample(event) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleRow(event: HaryanviLines.Event, order: Int, speaking: String?, onPlay: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(18.dp)
    // Staggered entrance: each row rises and fades in a beat after the one above.
    val still = com.vikash.ampli.drive.prefs.LiteMode.enabled
    val enter = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay((order.coerceAtMost(18) * 28).toLong())
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    val active = speaking != null
    val edge by animateColorAsState(if (active) theme.accent else theme.border.copy(alpha = 0.35f), tween(240), label = "edge")
    val preview = remember(event) { preview(event) }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 24.dp.toPx()
            }
            .clip(shape)
            .background(
                if (active) {
                    Brush.horizontalGradient(listOf(theme.accent.copy(alpha = 0.22f), theme.accent2.copy(alpha = 0.08f)))
                } else {
                    Brush.horizontalGradient(listOf(theme.surfaceHigh.copy(alpha = 0.55f), theme.surfaceHigh.copy(alpha = 0.25f)))
                },
            )
            .border(1.dp, edge, shape)
            .clickable(onClick = onPlay)
            .heightIn(min = MinTouch)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
    ) {
        Column(Modifier.weight(1f)) {
            Text(event.title, color = theme.text, fontSize = dims.t(2.5f, min = 15f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            AnimatedContent(
                targetState = speaking ?: preview,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "line",
            ) { line ->
                Text(line, color = if (active) theme.text else theme.muted, fontSize = dims.t(2.3f, min = 13f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        PlayBadge(active)
    }
}

/** Round play button that turns into three bouncing sound bars while the line is spoken. */
@Composable
private fun PlayBadge(active: Boolean) {
    val theme = LocalDriveTheme.current
    val scale by animateFloatAsState(if (active) 1.08f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "badge")
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (active) Brush.linearGradient(listOf(theme.accent, theme.accent2)) else Brush.linearGradient(listOf(theme.surfaceHigh, theme.surfaceHigh))),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            val bars = rememberInfiniteTransition(label = "bars")
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until 3) {
                    val h by bars.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(320 + i * 90), RepeatMode.Reverse),
                        label = "bar$i",
                    )
                    Box(Modifier.width(4.dp).height((20 * h).dp).clip(RoundedCornerShape(2.dp)).background(theme.onAccent))
                }
            }
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
        }
    }
}

/** The first line of [event] with its example values, in Devanagari. */
private fun preview(event: HaryanviLines.Event): String {
    val line = HaryanviLines.forEvent(event)?.firstOrNull() ?: return ""
    return try {
        String.format(Locale.US, line.deva, *HaryanviLines.sampleArgs(event))
    } catch (_: Exception) {
        line.deva
    }
}
