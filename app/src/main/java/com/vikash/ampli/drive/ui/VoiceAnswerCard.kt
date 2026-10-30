package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.VoiceAnswer
import com.vikash.ampli.drive.weather.Sky

/**
 * The "Hey Ampli" answer card: the time, the weather, the volume… in big letters, readable at a
 * glance from the driver's seat. Everything on it is also spoken.
 */
@Composable
fun VoiceAnswerCard(answer: VoiceAnswer, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val problem = answer.kind == VoiceAnswer.Kind.Problem
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Soft themes: a raised answer card, as in the approved sample.
            .themedSurface(theme, theme.cardShape(dims, scale = 0.8f), theme.surfaceHigh, depth = SoftDepth.Key)
            .padding(horizontal = dims.d(2.6f), vertical = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.6f)),
    ) {
        val size = maxOf(dims.d(14f), 72.dp)
        Box(
            Modifier
                .size(size)
                .then(
                    if (theme.isSoft) {
                        Modifier.softWell(theme, CircleShape)
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .background(if (problem) theme.warning.copy(alpha = 0.22f) else theme.accent.copy(alpha = 0.22f))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconFor(answer),
                contentDescription = null,
                tint = if (problem) theme.warning else theme.accentSoft,
                modifier = Modifier.size(size * 0.56f),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(0.4f))) {
            Text(
                answer.title,
                color = theme.muted,
                fontSize = dims.t(2.6f, min = 15f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (answer.value.isNotBlank()) {
                Text(
                    answer.value,
                    style = theme.numberStyle(),
                    fontSize = dims.t(if (problem) 6f else 9f, min = 34f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (answer.detail.isNotBlank()) {
                Text(
                    answer.detail,
                    color = theme.text,
                    fontSize = dims.t(3f, min = 17f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (answer.note.isNotBlank()) {
                Text(answer.note, color = theme.muted, fontSize = dims.t(2.1f, min = 12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun iconFor(a: VoiceAnswer): ImageVector = when (a.kind) {
    VoiceAnswer.Kind.Time -> Icons.Filled.Schedule
    VoiceAnswer.Kind.Date -> Icons.Filled.CalendarToday
    VoiceAnswer.Kind.Volume -> if (a.value.any { it.isDigit() }) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff
    VoiceAnswer.Kind.Speed -> Icons.Filled.Speed
    VoiceAnswer.Kind.Brightness -> Icons.Filled.BrightnessMedium
    VoiceAnswer.Kind.Info -> Icons.Filled.Info
    VoiceAnswer.Kind.Problem -> Icons.Filled.CloudOff
    VoiceAnswer.Kind.Weather -> when (a.sky) {
        Sky.Clear, null -> Icons.Filled.WbSunny
        Sky.PartlyCloudy -> Icons.Filled.WbCloudy
        Sky.Cloudy -> Icons.Filled.Cloud
        Sky.Rain, Sky.HeavyRain -> Icons.Filled.WaterDrop
        Sky.Thunder -> Icons.Filled.Thunderstorm
        Sky.Fog -> Icons.Filled.Dehaze
        Sky.Snow -> Icons.Filled.AcUnit
    }
}
