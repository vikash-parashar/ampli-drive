package com.vikash.ampli.drive.ui

import android.location.Location
import android.os.CancellationSignal
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.emergency.EmergencyProfile
import com.vikash.ampli.drive.emergency.Sos
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the Help button must be held to start SOS. */
internal const val HOLD_MS = 3000

/** Seconds before the SOS composer opens, so a mistaken press can be cancelled. */
private const val COUNTDOWN_S = 5

/** After a detected crash the driver gets longer to answer "Are you okay?". */
private const val CRASH_COUNTDOWN_S = com.vikash.ampli.drive.car.CarMonitor.CRASH_COUNTDOWN_S

/**
 * The red Help button in the dock. Tap opens the Help panel; holding it for 3 seconds
 * (a ring fills around the icon) starts the SOS countdown.
 */
@Composable
fun HelpDockButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    val shape = theme.cardShape(dims, scale = 0.9f)
    val label = stringResource(R.string.dock_help)
    val iconSize = maxOf(dims.d(5f), 28.dp)

    Column(
        modifier = modifier
            .themedCard(theme, shape, fill = Brush.linearGradient(listOf(theme.danger, theme.danger)), border = theme.danger, blur = false)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var fired = false
                        val job = scope.launch {
                            progress.snapTo(0f)
                            progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            fired = true
                            hold()
                        }
                        tryAwaitRelease()
                        if (!fired) {
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                            tap()
                        }
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Sos, contentDescription = label, tint = theme.onDanger, modifier = Modifier.size(iconSize))
            // Hold progress ring.
            Canvas(Modifier.size(iconSize + 16.dp)) {
                if (progress.value > 0f) {
                    drawArc(
                        color = theme.onDanger,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        Spacer(Modifier.height(dims.d(0.4f)))
        Text(
            theme.label(label),
            style = theme.labelStyle(theme.onDanger),
            fontSize = dims.t(2.2f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

private enum class SosPhase { Idle, Countdown, Ready }

/**
 * Full-screen Help panel: helpline numbers, the driver's own emergency contacts, SOS and the
 * responder card. [startSos] opens straight into the SOS countdown (Help button held 3 s).
 * [crash]: opened by crash detection; the countdown is 30 s and Ampli keeps asking aloud
 * "Are you okay?" (CarMonitor) until it is cancelled or runs out.
 */
@Composable
fun HelpScreen(app: DriveApp, startSos: Boolean, onBack: () -> Unit, onEditProfile: () -> Unit, crash: Boolean = false) {
    val context = LocalContext.current
    val profile by app.emergency.profile.collectAsStateWithLifecycle()
    var phase by rememberSaveable { mutableStateOf(if (startSos) SosPhase.Countdown else SosPhase.Idle) }
    var showMedical by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf("") }
    var fix by remember { mutableStateOf<Location?>(null) }

    // A fresh GPS fix is requested as soon as the panel opens, so it is ready for SOS.
    DisposableEffect(Unit) {
        val signal: CancellationSignal = Sos.requestFreshLocation(context) { fix = it }
        onDispose { signal.cancel() }
    }

    fun bestLocation(): Location? {
        val known = Sos.lastKnownLocation(context, app.speedTracker.state.value.location)
        return listOfNotNull(fix, known).maxByOrNull { it.time }
    }

    fun sendNow() {
        if (crash) {
            // CarMonitor owns the crash countdown; it may already have opened the message.
            app.car.sendCrashSos(bestLocation())?.let { message = it }
        } else {
            message = Sos.buildMessage(profile, bestLocation())
            Sos.openSmsComposer(context, profile.contacts.map { it.phone }, message)
        }
        phase = SosPhase.Ready
    }

    // The crash countdown ran out in CarMonitor (it opens the message there): show it here too.
    val crashSos by app.car.crashSos.collectAsStateWithLifecycle()
    LaunchedEffect(crash, crashSos) {
        val sent = crashSos
        if (crash && sent != null && phase == SosPhase.Countdown) {
            message = sent
            phase = SosPhase.Ready
        }
    }
    // A crash countdown shows the time CarMonitor has left, so screen and voice agree.
    val crashSecondsLeft = remember {
        app.car.crashAt.value?.let { at ->
            ((at + CRASH_COUNTDOWN_S * 1000L - System.currentTimeMillis() + 999) / 1000).toInt().coerceIn(1, CRASH_COUNTDOWN_S)
        } ?: CRASH_COUNTDOWN_S
    }

    when (phase) {
        SosPhase.Countdown -> SosCountdown(
            onCancel = {
                if (crash) app.car.clearCrash()
                phase = SosPhase.Idle
            },
            onSendNow = { sendNow() },
            onFinished = { sendNow() },
            hasFix = fix != null,
            seconds = if (crash) crashSecondsLeft else COUNTDOWN_S,
            crash = crash,
        )
        SosPhase.Ready -> SosReady(
            profile = profile,
            message = message,
            onSmsAgain = { Sos.openSmsComposer(context, profile.contacts.map { it.phone }, message) },
            onShare = { Sos.share(context, message) },
            onDial = { Sos.dial(context, it) },
            onDone = { phase = SosPhase.Idle },
        )
        SosPhase.Idle -> if (showMedical) {
            MedicalScreen(profile = profile, onDial = { Sos.dial(context, it) }, onClose = { showMedical = false })
        } else {
            HelpPanel(
                profile = profile,
                onBack = onBack,
                onDial = { Sos.dial(context, it) },
                onSos = { phase = SosPhase.Countdown },
                onMedical = { showMedical = true },
                onEditProfile = onEditProfile,
            )
        }
    }
}

@Composable
private fun HelpPanel(
    profile: EmergencyProfile,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onSos: () -> Unit,
    onMedical: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(title = stringResource(R.string.help_title), hint = stringResource(R.string.help_hint), onBack = onBack)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(
                Modifier.weight(1.7f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                for (row in Sos.HELPLINES.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                        for (h in row) {
                            HelplineTile(
                                number = h.number,
                                label = stringResource(h.labelRes),
                                primary = h.number == "112",
                                onClick = { onDial(h.number) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    theme.label(stringResource(R.string.help_your_contacts)),
                    style = theme.labelStyle(),
                    fontSize = dims.t(2.4f),
                    modifier = Modifier.padding(top = dims.d(0.8f)),
                )
                if (profile.contacts.isEmpty()) {
                    Text(stringResource(R.string.help_no_contacts), fontSize = dims.t(2.6f, min = 16f), color = theme.muted)
                } else {
                    for (row in profile.contacts.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            for (c in row) {
                                HelplineTile(
                                    number = c.phone,
                                    label = c.name.ifBlank { c.phone },
                                    primary = false,
                                    onClick = { onDial(c.phone) },
                                    modifier = Modifier.weight(1f),
                                    small = true,
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                // Big SOS button.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 120.dp)
                        .themedCard(theme, theme.cardShape(dims), fill = Brush.linearGradient(listOf(theme.danger, theme.danger)), border = theme.danger, blur = false)
                        .clickable(onClick = onSos)
                        .padding(dims.d(2f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Sos, contentDescription = null, tint = theme.onDanger, modifier = Modifier.size(maxOf(dims.d(12f), 56.dp)))
                    Text(
                        stringResource(R.string.sos_button),
                        color = theme.onDanger,
                        fontSize = dims.t(5f, min = 26f),
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.sos_button_hint),
                        color = theme.onDanger,
                        fontSize = dims.t(2.2f),
                        textAlign = TextAlign.Center,
                    )
                }
                BigButton(
                    text = stringResource(R.string.help_medical_info),
                    onClick = onMedical,
                    icon = Icons.Filled.MedicalServices,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                BigButton(
                    text = stringResource(R.string.em_title),
                    onClick = onEditProfile,
                    icon = Icons.Filled.Edit,
                    primary = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HelplineTile(
    number: String,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val fill = if (primary) theme.danger else theme.surface
    val fg = if (primary) theme.onDanger else theme.text
    Column(
        modifier = modifier
            .heightIn(min = if (small) 72.dp else 96.dp)
            .themedCard(theme, theme.cardShape(dims, scale = 0.8f), fill = Brush.linearGradient(listOf(fill, fill)), border = if (primary) theme.danger else theme.border)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(1.6f), vertical = dims.d(1.2f)),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = if (primary) theme.onDanger else theme.danger, modifier = Modifier.size(22.dp))
            Text(
                if (small) label else number,
                style = theme.numberStyle(fg, glowColor = null),
                fontSize = if (small) dims.t(3f, min = 18f) else dims.t(5f, min = 26f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (small) number else label,
            fontSize = dims.t(2.3f, min = 14f),
            color = if (primary) theme.onDanger else theme.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SosCountdown(
    onCancel: () -> Unit,
    onSendNow: () -> Unit,
    onFinished: () -> Unit,
    hasFix: Boolean,
    seconds: Int = COUNTDOWN_S,
    crash: Boolean = false,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    var left by remember { mutableIntStateOf(seconds) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000L)
            left--
        }
        finished()
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.danger)
            .padding(dims.d(4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(4f)),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (crash) R.string.crash_title else R.string.sos_countdown_title),
                color = theme.onDanger, fontSize = dims.t(4.4f, min = 22f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text("$left", color = theme.onDanger, fontSize = dims.t(if (crash) 24f else 30f, min = 88f), fontWeight = FontWeight.Black)
            Text(
                stringResource(if (hasFix) R.string.sos_location_ready else R.string.sos_location_searching),
                color = theme.onDanger,
                fontSize = dims.t(2.6f, min = 16f),
                textAlign = TextAlign.Center,
            )
            if (crash) {
                // Until there is a server, nothing is sent without a tap (no SEND_SMS permission).
                Text(
                    stringResource(R.string.crash_send_needs_tap),
                    color = theme.onDanger,
                    fontSize = dims.t(2.3f, min = 14f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dims.d(3f))) {
            // Cancel is the biggest thing on screen: a mistaken press must be easy to undo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .clip(theme.buttonShape(dims))
                    .background(theme.onDanger)
                    .clickable(onClick = onCancel),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f), Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = theme.danger, modifier = Modifier.size(48.dp))
                Text(
                    stringResource(if (crash) R.string.crash_im_ok else R.string.sos_cancel),
                    color = theme.danger, fontSize = dims.t(7f, min = 32f), fontWeight = FontWeight.Black,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clip(theme.buttonShape(dims))
                    .border(2.dp, theme.onDanger, theme.buttonShape(dims))
                    .clickable(onClick = onSendNow),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.sos_send_now), color = theme.onDanger, fontSize = dims.t(3.2f, min = 18f), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun SosReady(
    profile: EmergencyProfile,
    message: String,
    onSmsAgain: () -> Unit,
    onShare: () -> Unit,
    onDial: (String) -> Unit,
    onDone: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(title = stringResource(R.string.sos_ready_title), hint = stringResource(R.string.sos_ready_hint), onBack = onDone)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                BigButton(stringResource(R.string.sos_send_again), onSmsAgain, Modifier.fillMaxWidth(), icon = Icons.Filled.Sms)
                BigButton(stringResource(R.string.sos_share), onShare, Modifier.fillMaxWidth(), icon = Icons.Filled.Share, primary = false)
                ActionTile(
                    text = "112 · " + stringResource(R.string.help_112),
                    icon = Icons.Filled.Call,
                    onClick = { onDial("112") },
                )
                Pane(Modifier.fillMaxWidth()) {
                    Text(message, fontSize = dims.t(2.3f), color = theme.muted, modifier = Modifier.padding(dims.d(2f)))
                }
            }
            MedicalCard(profile, onDial, Modifier.weight(1.3f).fillMaxHeight())
        }
    }
}

@Composable
private fun ActionTile(text: String, icon: ImageVector, onClick: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clip(theme.buttonShape(dims))
            .background(theme.danger)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f), Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = theme.onDanger, modifier = Modifier.size(28.dp))
        Text(text, color = theme.onDanger, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MedicalScreen(profile: EmergencyProfile, onDial: (String) -> Unit, onClose: () -> Unit) {
    val dims = LocalDims.current
    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(title = stringResource(R.string.med_title), hint = null, onBack = onClose)
        MedicalCard(profile, onDial, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Big, high-contrast card for paramedics and police: who, blood group, notes, vehicle, contacts. */
@Composable
private fun MedicalCard(profile: EmergencyProfile, onDial: (String) -> Unit, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2.6f)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Icon(Icons.Filled.MedicalServices, contentDescription = null, tint = theme.danger, modifier = Modifier.size(40.dp))
                Text(
                    profile.ownerName.ifBlank { stringResource(R.string.med_title) },
                    fontSize = dims.t(4.4f, min = 22f),
                    fontWeight = FontWeight.Bold,
                    color = theme.text,
                    modifier = Modifier.weight(1f),
                )
                if (profile.bloodGroup.isNotBlank()) {
                    Box(
                        Modifier.clip(CircleShape).background(theme.danger).padding(horizontal = dims.d(2.4f), vertical = dims.d(1f)),
                    ) {
                        Text(profile.bloodGroup, color = theme.onDanger, fontSize = dims.t(5f, min = 26f), fontWeight = FontWeight.Black)
                    }
                }
            }
            if (!profile.hasMedicalInfo && profile.contacts.isEmpty()) {
                Text(stringResource(R.string.em_status_empty), fontSize = dims.t(2.8f, min = 16f), color = theme.muted)
            }
            InfoLine(stringResource(R.string.med_blood), profile.bloodGroup)
            InfoLine(stringResource(R.string.med_notes), profile.medicalNotes)
            InfoLine(
                stringResource(R.string.med_vehicle),
                listOf(profile.vehicleNumber, profile.vehicleModel).filter { it.isNotBlank() }.joinToString(" · "),
            )
            InfoLine(stringResource(R.string.med_insurance), profile.insuranceNote)
            if (profile.contacts.isNotEmpty()) {
                Text(theme.label(stringResource(R.string.help_your_contacts)), style = theme.labelStyle(), fontSize = dims.t(2.4f))
                for (c in profile.contacts) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouch)
                            .clip(theme.buttonShape(dims))
                            .background(theme.surfaceHigh)
                            .clickable { onDial(c.phone) }
                            .padding(horizontal = dims.d(2f)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null, tint = theme.danger)
                        Text(c.name.ifBlank { c.phone }, fontSize = dims.t(3f, min = 18f), color = theme.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.phone, fontSize = dims.t(3f, min = 18f), color = theme.text, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column {
        Text(theme.label(label), style = theme.labelStyle(), fontSize = dims.t(2.2f))
        Text(value, fontSize = dims.t(3.4f, min = 20f), color = theme.text, fontWeight = FontWeight.SemiBold)
    }
}
