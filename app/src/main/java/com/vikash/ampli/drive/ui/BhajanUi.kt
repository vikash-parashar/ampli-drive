package com.vikash.ampli.drive.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.bhajan.BhajanPlan
import com.vikash.ampli.drive.bhajan.BhajanPlayer
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** "जय बजरंगबली! · मंगलवार की राम राम" while the car-start bhajan plays, with "छोड़ दे". */
@Composable
fun BhajanCardHost(app: DriveApp) {
    val now by app.bhajan.now.collectAsStateWithLifecycle()
    val last = remember { arrayOfNulls<BhajanPlayer.Now>(1) }
    now?.let { last[0] = it }
    val dims = LocalDims.current
    Box(Modifier.fillMaxSize().padding(top = dims.d(6f)), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = now != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it / 2 } + fadeOut()) {
            last[0]?.let { BhajanCard(it, onStop = app.bhajan::stop) }
        }
    }
}

@Composable
fun BhajanCard(now: BhajanPlayer.Now, onStop: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val day = now.day.getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0])
    Row(
        Modifier
            .padding(horizontal = dims.d(3f))
            .widthIn(max = 820.dp)
            .themedCard(
                theme, RoundedCornerShape(24.dp),
                fill = Brush.horizontalGradient(listOf(theme.surfaceHigh, theme.surface)),
                border = theme.accent.copy(alpha = 0.5f), accentEdge = false, blur = false,
            )
            .padding(horizontal = dims.d(3f), vertical = dims.d(2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f)),
    ) {
        GlowBadge { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = theme.text, modifier = Modifier.size(26.dp)) }
        Column(Modifier.weight(1f)) {
            Text(now.bhajan.greeting, color = theme.text, fontSize = dims.t(4.4f, min = 22f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.bhajan_card_day, day), color = theme.text, fontSize = dims.t(2.8f, min = 16f), maxLines = 1)
            Text(
                stringResource(R.string.bhajan_card_playing, now.bhajan.seconds.coerceAtMost((BhajanPlan.MAX_MS / 1000).toInt())),
                color = theme.muted, fontSize = dims.t(2.3f, min = 14f), maxLines = 1,
            )
        }
        BigButton(stringResource(R.string.bhajan_card_stop), onStop, primary = false)
    }
}

/**
 * Settings › Bhajan when the car starts (off by default). The switch, a one-line summary and
 * "Choose", which opens [BhajanSettingsSheet].
 */
@Composable
fun BhajanSettingsCard(app: DriveApp, isPro: Boolean, onLocked: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val s by app.bhajan.settings.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    val locale = context.resources.configuration.locales[0]
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { app.bhajan.update { it.copy(enabled = !it.enabled) } },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    val days = DayOfWeek.entries.filter { it in s.days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
                    CardTitle(
                        stringResource(R.string.bhajan_title),
                        when {
                            !s.enabled -> stringResource(R.string.bhajan_hint_off)
                            s.everyMorning -> stringResource(R.string.bhajan_hint_every_morning)
                            else -> stringResource(R.string.bhajan_hint_days, days)
                        },
                    )
                }
                ThemedSwitch(s.enabled) { on -> app.bhajan.update { it.copy(enabled = on) } }
            }
            if (s.enabled) BigButton(stringResource(R.string.bhajan_choose), { sheet = true }, Modifier.fillMaxWidth(), primary = false)
            Text(stringResource(R.string.bhajan_rules), color = theme.muted, fontSize = dims.t(2.2f))
        }
    }
    if (sheet) BhajanSettingsSheet(app = app, isPro = isPro, onLocked = { sheet = false; onLocked() }, onDismiss = { sheet = false })
}

/** Days, every morning, the bhajan for each day (Pro ones locked on the free tier) and the volume. */
@Composable
fun BhajanSettingsSheet(app: DriveApp, isPro: Boolean, onLocked: () -> Unit, onDismiss: () -> Unit) {
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val s by app.bhajan.settings.collectAsStateWithLifecycle()
    val catalog = app.bhajan.catalog
    val (bg, fg) = dialogColors()
    // The day whose bhajan is being picked: today when it is one of the chosen days.
    var day by rememberSaveable { mutableStateOf(LocalDate.now().dayOfWeek.takeIf { it in s.days } ?: s.days.minOrNull() ?: DayOfWeek.TUESDAY) }
    val chosen = BhajanPlan.pickFor(s, catalog, day, isPro)
    AlertDialog(
        onDismissRequest = { app.bhajan.stop(); onDismiss() },
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(stringResource(R.string.bhajan_sheet_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.bhajan_when), fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in DayOfWeek.entries) {
                        Chip(d.getDisplayName(TextStyle.SHORT, locale), d in s.days, {
                            app.bhajan.update { st -> st.copy(days = if (d in st.days) st.days - d else st.days + d) }
                            day = d
                        })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { app.bhajan.update { it.copy(everyMorning = !it.everyMorning) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.bhajan_every_morning), modifier = Modifier.weight(1f))
                    ThemedSwitch(s.everyMorning) { on -> app.bhajan.update { it.copy(everyMorning = on) } }
                }
                Text(stringResource(R.string.bhajan_for_day, day.getDisplayName(TextStyle.FULL, locale)), fontWeight = FontWeight.SemiBold)
                if (catalog.isEmpty()) Text(stringResource(R.string.bhajan_none_bundled), color = theme.muted)
                for (b in catalog) {
                    val locked = BhajanPlan.locked(b, isPro)
                    val present = remember(b.id) { app.bhajan.exists(b) }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                            if (locked) onLocked() else app.bhajan.update { it.copy(choice = it.choice + (day to b.id)) }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = chosen?.id == b.id, enabled = !locked,
                            onClick = { if (locked) onLocked() else app.bhajan.update { it.copy(choice = it.choice + (day to b.id)) } },
                            colors = RadioButtonDefaults.colors(selectedColor = theme.accent),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(if (locale.language == "en") b.title else b.titleHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(if (present) R.string.bhajan_original else R.string.bhajan_not_in_build),
                                color = theme.muted, maxLines = 1,
                            )
                        }
                        if (locked) {
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.theme_locked), tint = Color(0xFFFBBF24), modifier = Modifier.size(22.dp))
                        } else if (present) {
                            IconButton(onClick = { app.bhajan.preview(b) }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.bhajan_preview), tint = theme.accentSoft)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.bhajan_volume, (s.volume * 100).roundToInt()), fontWeight = FontWeight.SemiBold)
                ThemedSlider(
                    value = s.volume,
                    onValueChange = { v -> app.bhajan.update { it.copy(volume = v) } },
                    valueRange = 0.1f..1f,
                    steps = 8,
                )
            }
        },
        confirmButton = { TextButton(onClick = { app.bhajan.stop(); onDismiss() }) { Text(stringResource(R.string.action_done)) } },
    )
}
