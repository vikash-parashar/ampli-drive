package com.vikash.ampli.drive.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/**
 * Play's prominent disclosure for the Pro "read WhatsApp messages aloud" feature, shown before it
 * can be switched on: what is read (sender and text from WhatsApp notifications), that it is only
 * spoken, never shown, stored, replied to or uploaded. Not dismissable by tapping outside.
 */
@Composable
fun WhatsAppDisclosureDialog(onAgree: () -> Unit, onDecline: () -> Unit, moving: Boolean = false) {
    val (bg, fg) = dialogColors()
    val theme = LocalDriveTheme.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.disclosure_wa_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            // Too long to read while driving, and agreeing needs a stopped car.
            if (moving) {
                Text(stringResource(R.string.disclosure_park_hint))
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.disclosure_wa_body))
                Text(stringResource(R.string.disclosure_wa_uses))
                Text(stringResource(R.string.disclosure_wa_stays), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.disclosure_change_later), color = theme.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!moving) onAgree() }, enabled = !moving) {
                Text(stringResource(if (moving) R.string.park_to_set else R.string.disclosure_agree))
            }
        },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.disclosure_decline)) } },
    )
}

/**
 * Settings › Read WhatsApp messages aloud (Pro, off by default). Switching on shows
 * [WhatsAppDisclosureDialog] first, then opens notification access if it is missing. The card
 * itself never shows any message text.
 *
 * Switching on needs a parked car (switching off works any time). On a unit without a TTS
 * engine only the recorded opener clip could be heard, never the sender or the text, so the
 * toggle stays off and disabled there and the card says why.
 */
@Composable
fun WhatsAppReadCard(app: DriveApp, isPro: Boolean, resumeTick: Int, onLocked: () -> Unit) {
    val prefs = app.messages.prefs
    val enabled by prefs.enabled.collectAsStateWithLifecycle()
    val groups by prefs.allowGroups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasAccess = remember(resumeTick) { app.mediaHub.hasAccess() }
    var askDisclosure by rememberSaveable { mutableStateOf(false) }
    val moving = isMoving(app)
    val ttsMissing by app.voicePlayer.ttsMissing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { app.voicePlayer.ttsAvailable() }

    fun turnOn() {
        if (moving || ttsMissing) return
        if (!isPro) {
            onLocked()
        } else if (!prefs.disclosureAccepted.value) {
            askDisclosure = true
        } else {
            prefs.setEnabled(true)
            if (!hasAccess) Launchers.allowNotificationAccess(context)
        }
    }

    WhatsAppReadCardContent(
        isPro = isPro,
        enabled = enabled && isPro && !ttsMissing,
        allowGroups = groups,
        hasAccess = hasAccess,
        ttsMissing = ttsMissing,
        moving = moving,
        onToggle = { on -> if (on) turnOn() else prefs.setEnabled(false) },
        onGroups = prefs::setAllowGroups,
        // Android's notification-access page is a settings form: not while moving.
        onAllowAccess = { if (!moving) Launchers.allowNotificationAccess(context) },
        // Telegram too, and reply by voice (their own disclosure first).
        extras = { ChatExtrasSection(app) },
    )

    if (askDisclosure) {
        WhatsAppDisclosureDialog(
            moving = moving,
            onAgree = {
                if (moving) return@WhatsAppDisclosureDialog
                askDisclosure = false
                prefs.setDisclosureAccepted(true)
                prefs.setEnabled(true)
                if (!hasAccess) Launchers.allowNotificationAccess(context)
            },
            onDecline = { askDisclosure = false },
        )
    }
}

/** The card's layout, stateless so the screenshot tests can draw every state. */
@Composable
fun WhatsAppReadCardContent(
    isPro: Boolean,
    enabled: Boolean,
    allowGroups: Boolean,
    hasAccess: Boolean,
    onToggle: (Boolean) -> Unit,
    onGroups: (Boolean) -> Unit,
    onAllowAccess: () -> Unit,
    ttsMissing: Boolean = false,
    moving: Boolean = false,
    extras: @Composable () -> Unit = {},
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    // Off can always be chosen; on needs a speech engine and a parked car.
    val canToggle = enabled || (!ttsMissing && !moving)
    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(enabled = canToggle) { onToggle(!enabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(
                        stringResource(R.string.wa_title),
                        stringResource(
                            when {
                                !isPro -> R.string.wa_hint_locked
                                enabled -> R.string.wa_hint_on
                                else -> R.string.wa_hint_off
                            },
                        ),
                    )
                }
                ProTag(locked = !isPro)
                ThemedSwitch(checked = enabled, enabled = canToggle, onChange = onToggle)
            }
            if (ttsMissing) {
                Text(stringResource(R.string.wa_tts_missing), color = theme.warning, fontSize = dims.t(2.4f, min = 16f))
            } else if (moving && !enabled) {
                Text(stringResource(R.string.wa_park_to_turn_on), color = theme.warning, fontSize = dims.t(2.4f, min = 16f))
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { onGroups(!allowGroups) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                ) {
                    Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.wa_groups_title), stringResource(R.string.wa_groups_hint)) }
                    ThemedSwitch(allowGroups, onGroups)
                }
                extras()
                if (!hasAccess) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                    ) {
                        Text(
                            stringResource(R.string.wa_needs_access),
                            color = theme.warning,
                            fontSize = dims.t(2.4f),
                            modifier = Modifier.weight(1f),
                        )
                        BigButton(
                            text = stringResource(if (moving) R.string.park_to_set else R.string.allow_access),
                            onClick = { if (!moving) onAllowAccess() },
                            primary = !moving,
                        )
                    }
                }
            }
            Text(stringResource(R.string.wa_rules), color = theme.muted, fontSize = dims.t(2.2f))
        }
    }
}

@Composable
private fun ProTag(locked: Boolean) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier
            .background(theme.accent, RoundedCornerShape(50))
            .padding(horizontal = dims.d(1.4f), vertical = dims.d(0.5f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (locked) Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.onAccent, modifier = Modifier.size(16.dp))
        Text(stringResource(R.string.wa_pro_badge), color = theme.onAccent, fontSize = dims.t(2f, min = 12f), fontWeight = FontWeight.Bold)
    }
}
