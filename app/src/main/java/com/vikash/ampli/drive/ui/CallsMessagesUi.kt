package com.vikash.ampli.drive.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.calls.CallAnnouncer
import com.vikash.ampli.drive.calls.VoiceReplies
import com.vikash.ampli.drive.messages.WhatsAppReader
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

// ------------------------------------------------------------------------------ Overlay cards

/** "मम्मी · हाँ, ठा ले / बाद मैं" over the map while a call rings (Settings › Announce calls). */
@Composable
fun CallCardHost(app: DriveApp) {
    val card by app.calls.card.collectAsStateWithLifecycle()
    val last = remember { arrayOfNulls<CallAnnouncer.Card>(1) }
    card?.let { last[0] = it }
    OverlayTop(visible = card != null) {
        last[0]?.let { c ->
            CallCard(
                card = c,
                onAnswer = { app.calls.answer() },
                onLater = { app.calls.later() },
                onClose = { app.calls.dismiss() },
            )
        }
    }
}

/** Stateless, for the screenshot tests too. */
@Composable
fun CallCard(card: CallAnnouncer.Card, onAnswer: () -> Unit, onLater: () -> Unit, onClose: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    OverlayCard(onClick = if (card.phase == CallAnnouncer.Card.Phase.Answered) onClose else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f))) {
            GlowBadge { Icon(Icons.Filled.Call, contentDescription = null, tint = theme.text, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(stringResource(R.string.call_card_label), card.number).joinToString(" · "),
                    color = theme.muted, fontSize = dims.t(2.3f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(card.title, color = theme.text, fontSize = dims.t(4.4f, min = 22f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (card.phase) {
                    CallAnnouncer.Card.Phase.Answered -> Text(stringResource(R.string.call_card_answered), color = theme.ok, fontSize = dims.t(2.5f, min = 15f))
                    CallAnnouncer.Card.Phase.TapToAnswer -> Text(stringResource(R.string.call_card_tap), color = theme.warning, fontSize = dims.t(2.5f, min = 15f))
                    else -> Unit
                }
            }
        }
        if (card.phase == CallAnnouncer.Card.Phase.Asking || card.phase == CallAnnouncer.Card.Phase.Listening) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                BigButton(stringResource(R.string.call_card_answer), onAnswer, Modifier.weight(1f), icon = Icons.Filled.Call)
                BigButton(stringResource(R.string.call_card_later), onLater, Modifier.weight(1f), icon = Icons.Filled.CallEnd, primary = false)
                if (card.phase == CallAnnouncer.Card.Phase.Listening) ListeningPill(stringResource(R.string.call_card_say))
            }
        } else if (card.phase == CallAnnouncer.Card.Phase.TapToAnswer) {
            BigButton(stringResource(R.string.action_close), onClose, Modifier.fillMaxWidth(), primary = false)
        }
    }
}

/**
 * "Ramesh · WhatsApp — सुणाऊं?" while a message waits. The message text is never drawn: it is only
 * spoken, after "सुणा दे". A dictated reply is read back and sent only on "हाँ, भेज दे".
 */
@Composable
fun MessageCardHost(app: DriveApp) {
    val prompt by app.messages.prompt.collectAsStateWithLifecycle()
    val call by app.calls.card.collectAsStateWithLifecycle()
    val last = remember { arrayOfNulls<WhatsAppReader.Prompt>(1) }
    prompt?.let { last[0] = it }
    // A ringing call's card wins; the message waits.
    OverlayTop(visible = prompt != null && call == null) {
        last[0]?.let { p -> MessageCard(p, onTap = app.messages::onTap) }
    }
}

@Composable
fun MessageCard(prompt: WhatsAppReader.Prompt, onTap: (VoiceReplies.Message) -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val step = prompt.step
    val listening = prompt.phase == WhatsAppReader.Prompt.Phase.Listening
    OverlayCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f))) {
            GlowBadge { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = theme.text, modifier = Modifier.size(26.dp)) }
            Column(Modifier.weight(1f)) {
                Text(prompt.appName + " · " + prompt.sender, color = theme.muted, fontSize = dims.t(2.3f, min = 14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (step) {
                        WhatsAppReader.Prompt.Step.Offer -> stringResource(R.string.chat_card_offer, prompt.sender)
                        WhatsAppReader.Prompt.Step.OfferReply -> stringResource(R.string.chat_card_offer_reply)
                        WhatsAppReader.Prompt.Step.Dictate -> stringResource(R.string.chat_card_dictate)
                        WhatsAppReader.Prompt.Step.Confirm -> stringResource(R.string.chat_card_confirm)
                        WhatsAppReader.Prompt.Step.Sent -> stringResource(R.string.chat_card_sent)
                    },
                    color = if (step == WhatsAppReader.Prompt.Step.Sent) theme.ok else theme.text,
                    fontSize = dims.t(3.4f, min = 19f), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (prompt.phase == WhatsAppReader.Prompt.Phase.Reading) {
                    Text(stringResource(R.string.chat_card_reading), color = theme.muted, fontSize = dims.t(2.3f, min = 14f))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            when (step) {
                WhatsAppReader.Prompt.Step.Offer -> {
                    BigButton(stringResource(R.string.chat_card_read), { onTap(VoiceReplies.Message.Read) }, Modifier.weight(1f))
                    BigButton(stringResource(R.string.chat_card_skip), { onTap(VoiceReplies.Message.Skip) }, Modifier.weight(1f), primary = false)
                    if (prompt.canReply) {
                        BigButton(stringResource(R.string.chat_card_reply), { onTap(VoiceReplies.Message.Reply) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.Reply, primary = false)
                    }
                }
                WhatsAppReader.Prompt.Step.OfferReply -> {
                    BigButton(stringResource(R.string.chat_card_reply), { onTap(VoiceReplies.Message.Reply) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.Reply)
                    BigButton(stringResource(R.string.chat_card_skip), { onTap(VoiceReplies.Message.Skip) }, Modifier.weight(1f), primary = false)
                }
                WhatsAppReader.Prompt.Step.Dictate -> {
                    ListeningPill(stringResource(R.string.chat_card_listening))
                    BigButton(stringResource(R.string.chat_card_skip), { onTap(VoiceReplies.Message.Skip) }, Modifier.weight(1f), primary = false)
                }
                WhatsAppReader.Prompt.Step.Confirm -> {
                    BigButton(stringResource(R.string.chat_card_send), { onTap(VoiceReplies.Message.Read) }, Modifier.weight(1f))
                    BigButton(stringResource(R.string.chat_card_dont_send), { onTap(VoiceReplies.Message.Skip) }, Modifier.weight(1f), primary = false)
                }
                WhatsAppReader.Prompt.Step.Sent -> Unit
            }
            if (listening && step != WhatsAppReader.Prompt.Step.Dictate) ListeningPill(stringResource(R.string.call_card_say))
        }
    }
}

@Composable
private fun ListeningPill(text: String) {
    val theme = LocalDriveTheme.current
    Pill(text = text, icon = Icons.Filled.Mic, iconTint = theme.accent)
}

@Composable
private fun OverlayTop(visible: Boolean, content: @Composable () -> Unit) {
    val dims = LocalDims.current
    Box(Modifier.fillMaxSize().padding(top = dims.d(6f)), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it / 2 } + fadeOut(),
        ) { content() }
    }
}

@Composable
private fun OverlayCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .padding(horizontal = dims.d(3f))
            .widthIn(max = 820.dp)
            .themedCard(
                theme, shape,
                fill = Brush.horizontalGradient(listOf(theme.surfaceHigh, theme.surface)),
                border = theme.accent.copy(alpha = 0.5f),
                accentEdge = false,
                blur = false,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dims.d(3f), vertical = dims.d(2f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) { content() }
}

// ------------------------------------------------------------------------------ Settings

/**
 * Settings › Announce incoming calls (free, off by default). Switching on asks for the phone state,
 * contacts and answer-calls permissions here, from this switch; each one is optional (without
 * them the ringing notification and its own buttons are used, through notification access).
 */
@Composable
fun CallSettingsCard(app: DriveApp, resumeTick: Int) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val prefs = app.calls.prefs
    val enabled by prefs.enabled.collectAsStateWithLifecycle()
    val listen by prefs.listen.collectAsStateWithLifecycle()
    var tick by remember { mutableStateOf(0) }
    val missing = remember(resumeTick, tick) {
        CallAnnouncer.PERMISSIONS.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    val hasAccess = remember(resumeTick) { app.mediaHub.hasAccess() }
    val moving = isMoving(app)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick++
        app.calls.refresh()
    }

    fun toggle(on: Boolean) {
        prefs.setEnabled(on)
        app.calls.refresh()
        if (on && missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { toggle(!enabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.calls_title), stringResource(if (enabled) R.string.calls_hint_on else R.string.calls_hint_off))
                }
                ThemedSwitch(enabled) { toggle(it) }
            }
            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { prefs.setListen(!listen) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
                ) {
                    Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.calls_listen_title), stringResource(R.string.calls_listen_hint)) }
                    ThemedSwitch(listen, prefs::setListen)
                }
                if (missing.isNotEmpty()) {
                    NeedsRow(stringResource(R.string.calls_needs_permission), stringResource(R.string.calls_allow)) {
                        launcher.launch(missing.toTypedArray())
                    }
                }
                if (!hasAccess) {
                    NeedsRow(stringResource(R.string.calls_needs_access), stringResource(if (moving) R.string.park_to_set else R.string.allow_access)) {
                        if (!moving) Launchers.allowNotificationAccess(context)
                    }
                }
            }
            Text(stringResource(R.string.calls_rules), color = theme.muted, fontSize = dims.t(2.2f))
        }
    }
}

@Composable
private fun NeedsRow(text: String, action: String, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Text(text, color = theme.warning, fontSize = dims.t(2.4f), modifier = Modifier.weight(1f))
        BigButton(text = action, onClick = onClick)
    }
}

/**
 * Inside the WhatsApp card, once it is on: Telegram too, and reply by voice. Either one shows
 * [ChatReplyDisclosureDialog] first (once), and needs a parked car to switch on.
 */
@Composable
fun ChatExtrasSection(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val prefs = app.messages.prefs
    val telegram by prefs.telegram.collectAsStateWithLifecycle()
    val reply by prefs.voiceReply.collectAsStateWithLifecycle()
    val moving = isMoving(app)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }

    fun turnOn(which: String) {
        if (moving) return
        if (!prefs.chatDisclosureAccepted.value) {
            pending = which
            return
        }
        if (which == TELEGRAM) prefs.setTelegram(true) else prefs.setVoiceReply(true)
    }

    Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(enabled = telegram || !moving) {
                if (telegram) prefs.setTelegram(false) else turnOn(TELEGRAM)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.chat_telegram_title), stringResource(R.string.chat_telegram_hint)) }
            ThemedSwitch(telegram, enabled = telegram || !moving) { on -> if (on) turnOn(TELEGRAM) else prefs.setTelegram(false) }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable(enabled = reply || !moving) {
                if (reply) prefs.setVoiceReply(false) else turnOn(REPLY)
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
        ) {
            Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.chat_reply_title), stringResource(R.string.chat_reply_hint)) }
            ThemedSwitch(reply, enabled = reply || !moving) { on -> if (on) turnOn(REPLY) else prefs.setVoiceReply(false) }
        }
        if (reply) Text(stringResource(R.string.chat_reply_rules), color = theme.muted, fontSize = dims.t(2.2f))
    }

    pending?.let { which ->
        ChatReplyDisclosureDialog(
            moving = moving,
            onAgree = {
                pending = null
                prefs.setChatDisclosureAccepted(true)
                if (which == TELEGRAM) prefs.setTelegram(true) else prefs.setVoiceReply(true)
            },
            onDecline = { pending = null },
        )
    }
}

private const val TELEGRAM = "telegram"
private const val REPLY = "reply"

/**
 * Play's prominent disclosure for Telegram read-out and voice replies: Telegram notifications are
 * read like WhatsApp's, and a reply is sent through the chat app's own notification reply button
 * after it is read back and confirmed. Not dismissable by tapping outside.
 */
@Composable
fun ChatReplyDisclosureDialog(onAgree: () -> Unit, onDecline: () -> Unit, moving: Boolean = false) {
    val (bg, fg) = dialogColors()
    val theme = LocalDriveTheme.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.disclosure_chat2_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (moving) {
                Text(stringResource(R.string.disclosure_park_hint))
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.disclosure_chat2_body))
                Text(stringResource(R.string.disclosure_chat2_uses))
                Text(stringResource(R.string.disclosure_chat2_stays), fontWeight = FontWeight.SemiBold)
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
