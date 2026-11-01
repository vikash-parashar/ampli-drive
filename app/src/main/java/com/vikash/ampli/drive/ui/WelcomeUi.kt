package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import com.vikash.ampli.drive.voice.Listener
import com.vikash.ampli.drive.voice.OwnerName
import com.vikash.ampli.drive.voice.WelcomeGreeter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Motion follows the owner's UI rule (the look and motion of 21st.dev components), rebuilt with
// Compose animation: soft springs for things arriving, short tweens for things leaving.
private val SoftSpring = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
private val SoftSpringOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

/**
 * First run: "Thara naam ke sai?" over the home screen until the driver saves a name or skips.
 * The card springs in over a fading scrim; the name is used by the welcome on every car start.
 */
@Composable
fun FirstRunNameHost(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    // Saved once the card has animated away, so the exit plays before the host leaves the screen.
    var commit by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(shown.isIdle, shown.currentState) {
        if (shown.isIdle && !shown.currentState) commit?.invoke()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(260)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    // The scrim takes every touch, so the home screen waits behind it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInVertically(SoftSpringOffset) { it / 3 } + scaleIn(SoftSpring, initialScale = 0.9f) + fadeIn(tween(240)),
            exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180)),
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .padding(dims.d(3f))
                    .widthIn(max = 760.dp)
                    .themedCard(
                        theme,
                        shape,
                        fill = Brush.verticalGradient(listOf(theme.surfaceHigh, theme.surface)),
                        border = theme.accent.copy(alpha = 0.45f),
                        accentEdge = false,
                        blur = false,
                    )
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    .padding(horizontal = dims.d(4f), vertical = dims.d(3.2f)),
                verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                    GlowBadge { Text("🙏", fontSize = dims.t(4f, min = 22f)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.name_title),
                            color = theme.text,
                            fontSize = dims.t(4.6f, min = 24f),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.name_hint), color = theme.muted, fontSize = dims.t(2.5f, min = 14f))
                    }
                }
                NameEntry(
                    app = app,
                    initial = app.prefs.ownerName.value,
                    onSave = { name ->
                        commit = {
                            app.prefs.setOwnerName(name)
                            // Let the driver hear it straight away: "Jai Baba Ki {name} Ji…".
                            app.welcome.playSoon()
                        }
                        shown.targetState = false
                    },
                    onSkip = {
                        commit = { app.prefs.setNameAsked() }
                        shown.targetState = false
                    },
                )
            }
        }
    }
}

/**
 * The name field with an optional mic button (the system recogniser, en-IN so the name comes back
 * in Latin letters as on the card), plus Save and an optional Skip.
 */
@Composable
internal fun NameEntry(
    app: DriveApp,
    initial: String,
    onSave: (String) -> Unit,
    onSkip: (() -> Unit)?,
    skipLabel: String = stringResource(R.string.name_skip),
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initial) }
    var listening by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val listener = remember { Listener(app) }
    DisposableEffect(Unit) { onDispose { listener.cancel() } }
    val notHeard = stringResource(R.string.name_not_heard)
    val denied = stringResource(R.string.name_mic_denied)
    val listeningText = stringResource(R.string.name_listening)

    fun listen() {
        if (listening) return
        listening = true
        note = listeningText
        scope.launch {
            val result = app.voice.withMic {
                listener.listen("en-IN", onPartial = { note = "… $it" }, commandsOnly = false)
            }
            listening = false
            val heard = (result as? Listener.Result.Heard)?.let { OwnerName.fromSpeech(it.text) }.orEmpty()
            if (heard.isNotEmpty()) {
                text = heard
                note = null
            } else {
                note = notHeard
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else note = denied
    }

    Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            // While moving the keyboard is off; the mic still works.
            val moving = isMoving(app)
            OutlinedTextField(
                value = text,
                enabled = !moving,
                supportingText = if (moving) ({ Text(stringResource(R.string.park_to_edit_hint)) }) else null,
                onValueChange = { text = it.take(OwnerName.MAX_LENGTH) },
                label = { Text(stringResource(R.string.name_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onSave(text) }),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            )
            MicButton(listening = listening, onClick = {
                val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            })
        }
        AnimatedVisibility(
            visible = note != null,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
        ) {
            Text(note.orEmpty(), color = if (listening) theme.accentSoft else theme.muted, fontSize = dims.t(2.4f, min = 14f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f), Alignment.End), modifier = Modifier.fillMaxWidth()) {
            if (onSkip != null) BigButton(text = skipLabel, onClick = onSkip, primary = false)
            BigButton(text = stringResource(R.string.action_save), onClick = { if (text.isNotBlank()) onSave(text) })
        }
    }
}

/** Round mic button that breathes while listening. */
@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val theme = LocalDriveTheme.current
    // Breathes only while listening (an idle infinite transition still draws every frame), and
    // not at all in lite mode (weak units).
    val scale = if (listening && !com.vikash.ampli.drive.prefs.LiteMode.enabled) {
        rememberInfiniteTransition(label = "mic").animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
            label = "micScale",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(MinTouch)
            .scale(scale)
            .clip(CircleShape)
            .background(if (listening) theme.accent else theme.surfaceHigh)
            .border(1.dp, theme.accent.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = stringResource(R.string.name_mic),
            tint = if (listening) theme.onAccent else theme.accentSoft,
            modifier = Modifier.size(30.dp),
        )
    }
}

/** Accent-gradient circle with a soft halo, for the 🙏 on the name step and the welcome card. */
@Composable
internal fun GlowBadge(content: @Composable () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Box(
        Modifier
            .size(maxOf(dims.d(9f), 52.dp))
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.55f), theme.accent2.copy(alpha = 0.18f))))
            .border(1.dp, theme.accent.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * "Jai Baba Ki {name} Ji 🙏" for [WelcomeGreeter.CARD_MS] at the top of the screen when the welcome
 * plays: springs in from above, a thin bar runs down the time, then it slides away. Only the card
 * takes touches (a tap closes it).
 */
@Composable
fun WelcomeCardHost(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val card by app.welcome.card.collectAsStateWithLifecycle()
    // Keeps the last card (not as state) so it can animate out after the state is cleared.
    val last = remember { arrayOfNulls<WelcomeGreeter.Card>(1) }
    card?.let { last[0] = it }
    LaunchedEffect(card?.id) {
        if (card != null) {
            delay(WelcomeGreeter.CARD_MS)
            app.welcome.dismissCard()
        }
    }
    Box(Modifier.fillMaxSize().padding(top = dims.d(6f)), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = card != null,
            enter = slideInVertically(SoftSpringOffset) { -it } + scaleIn(SoftSpring, initialScale = 0.9f) + fadeIn(tween(220)),
            exit = slideOutVertically(tween(280)) { -it / 2 } + scaleOut(tween(280), targetScale = 0.96f) + fadeOut(tween(220)),
        ) {
            last[0]?.let { shown -> WelcomeCard(app, shown) }
        }
    }
}

@Composable
internal fun WelcomeCard(app: DriveApp, shown: WelcomeGreeter.Card) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val shape = RoundedCornerShape(24.dp)
    // Countdown bar: full to empty over the card's time on screen.
    val progress = remember(shown.id) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(shown.id) { progress.animateTo(0f, tween(WelcomeGreeter.CARD_MS.toInt(), easing = LinearEasing)) }
    Column(
        Modifier
            .padding(horizontal = dims.d(3f))
            .widthIn(max = 760.dp)
            .themedCard(
                theme,
                shape,
                fill = Brush.horizontalGradient(listOf(theme.surfaceHigh, theme.surface)),
                border = theme.accent.copy(alpha = 0.5f),
                accentEdge = false,
                blur = false,
            )
            .clickable { app.welcome.dismissCard() }
            .animateContentSize(),
    ) {
        Row(
            Modifier.padding(horizontal = dims.d(3f), vertical = dims.d(2f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(2.4f)),
        ) {
            GlowBadge { Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = theme.text, modifier = Modifier.size(26.dp)) }
            Column {
                Text(
                    shown.title,
                    color = theme.text,
                    fontSize = dims.t(4.2f, min = 22f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(shown.line, color = theme.muted, fontSize = dims.t(2.5f, min = 14f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(theme.border.copy(alpha = 0.3f))) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value)
                    .height(3.dp)
                    .background(Brush.horizontalGradient(listOf(theme.accent, theme.accent2))),
            )
        }
    }
}

/**
 * Settings › Voice: "Your name" (edit inline, with the mic) and "Play welcome now". The editor
 * opens with a spring and the card resizes smoothly around it.
 */
@Composable
internal fun WelcomeSettingsCard(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val name by app.prefs.ownerName.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    SettingCard {
        Column(
            Modifier.animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.name_settings_title), name.ifEmpty { stringResource(R.string.name_settings_empty) })
                }
                if (!editing) BigButton(text = stringResource(R.string.action_edit), onClick = { editing = true }, primary = false)
            }
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
            ) {
                NameEntry(
                    app = app,
                    initial = name,
                    onSave = {
                        app.prefs.setOwnerName(it)
                        editing = false
                    },
                    onSkip = { editing = false },
                    skipLabel = stringResource(R.string.action_cancel),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Box(Modifier.weight(1f)) { CardTitle(stringResource(R.string.welcome_title), stringResource(R.string.welcome_hint)) }
                BigButton(
                    text = stringResource(R.string.welcome_play),
                    icon = Icons.Filled.VolumeUp,
                    primary = false,
                    onClick = { app.welcome.playSoon() },
                )
            }
        }
    }
}
