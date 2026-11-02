package com.vikash.ampli.drive.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.voice.Place
import com.vikash.ampli.drive.voice.VoiceUiState
import com.vikash.ampli.drive.voice.VoiceUiState.Phase
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.util.Locale

/**
 * The "Hey Ampli" sheet over any screen: pulsing mic while listening, the live transcript,
 * Ampli's reply, the places to choose from, and a big Cancel. Everything is also spoken,
 * so the driver never has to read it; tapping a place is the touch fallback.
 */
@Composable
fun VoiceOverlay(state: VoiceUiState, onCancel: () -> Unit, onChoose: (Int) -> Unit) {
    if (!state.active) return
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xC7080A14))
            // Swallow taps on the scrim; Cancel is the way out.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        val fill = if (theme.surface.alpha < 1f) theme.background else theme.surface
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.d(3f))
                .themedCard(theme, theme.cardShape(dims, 1.33f), fill = Brush.linearGradient(listOf(fill, fill)), border = theme.accent, blur = false)
                .border(2.dp, theme.accent, theme.cardShape(dims, 1.33f))
                .padding(horizontal = dims.d(5f), vertical = dims.d(4f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.8f)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(5f)),
            ) {
                MicPulse(state.phase)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(1.5f))) {
                    Text(
                        stringResource(R.string.voice_button).uppercase(Locale.ROOT),
                        color = theme.accentSoft,
                        fontSize = dims.t(2.25f, min = 16f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.em,
                        maxLines = 1,
                    )
                    val heading = when {
                        state.phase == Phase.Listening && state.heard.isEmpty() -> stringResource(R.string.voice_listening)
                        state.heard.isNotEmpty() -> "“${state.heard}”"
                        else -> stringResource(R.string.voice_working)
                    }
                    Text(
                        heading,
                        color = theme.text,
                        fontSize = dims.t(6f, min = 26f),
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = dims.t(7.2f, min = 32f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.reply.isNotEmpty() && state.options.isEmpty() && state.answer == null) {
                        Text(
                            state.reply,
                            color = theme.muted,
                            fontSize = dims.t(3f, min = 20f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // "Ruko": a big round outlined key.
                Column(
                    Modifier
                        .size(maxOf(dims.d(15f), MinTouch))
                        .themedTap(CircleShape, Color.Transparent, border = theme.muted, borderWidth = 2.dp, onClick = onCancel),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = theme.text, modifier = Modifier.size(dims.d(4f)))
                    Text(stringResource(R.string.voice_cancel), color = theme.text, fontWeight = FontWeight.Bold, fontSize = dims.t(2.75f, min = 18f), maxLines = 1)
                }
            }
            state.answer?.let { VoiceAnswerCard(it) }
            if (state.options.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    state.options.forEachIndexed { i, place ->
                        PlaceOption(i, place, { onChoose(i) }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MicPulse(phase: Phase) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val listening = phase == Phase.Listening
    val size = maxOf(dims.d(17f), 72.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 1.25f)) {
        if (listening) {
            // Only while listening (the overlay stays still while Ampli works or speaks), and
            // read in the draw layer, so a frame of the pulse never recomposes the overlay.
            // Lite mode (weak units): the halo shows but stands still.
            val still = com.vikash.ampli.drive.prefs.LiteMode.enabled
            val pulse = if (still) {
                remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            } else {
                rememberInfiniteTransition(label = "mic").animateFloat(
                    initialValue = 1f,
                    targetValue = 1.18f,
                    animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "pulse",
                )
            }
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulse.value * 1.1f
                        scaleY = pulse.value * 1.1f
                    }
                    .clip(CircleShape)
                    .background(theme.accent.copy(alpha = 0.25f)),
            )
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (listening) theme.accent else theme.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (phase == Phase.Speaking) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                contentDescription = null,
                tint = if (listening) theme.onAccent else theme.accentSoft,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
private fun PlaceOption(index: Int, place: Place, onClick: () -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = modifier
            .heightIn(min = maxOf(dims.d(14f), MinTouch))
            .themedTap(theme.cardShape(dims, scale = 0.8f), theme.surfaceHigh, onClick = onClick)
            .padding(dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
    ) {
        Box(
            Modifier
                .size(maxOf(dims.d(7f), 44.dp))
                .clip(CircleShape)
                .background(theme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", color = theme.onAccent, fontSize = dims.t(3.4f, min = 20f), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(
                place.name,
                color = theme.text,
                fontSize = dims.t(2.9f, min = 17f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            place.area?.let {
                Text(it, color = theme.muted, fontSize = dims.t(2.3f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            place.distanceKm?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(18.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.1f km", it),
                        color = theme.accentSoft,
                        fontSize = dims.t(2.5f, min = 15f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
