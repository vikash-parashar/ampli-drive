package com.vikash.ampli.drive.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.feedback.FeatureItem
import com.vikash.ampli.drive.feedback.FeedbackJson
import com.vikash.ampli.drive.feedback.FeedbackRepository
import com.vikash.ampli.drive.feedback.MyRequest
import com.vikash.ampli.drive.feedback.ReportDraft
import com.vikash.ampli.drive.feedback.ReportKind
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.launch

/** Help & feedback is a typing screen: it opens only below this speed (spec: < 5 km/h). */
const val FEEDBACK_MAX_KMH = 5f

private enum class FeedbackTab { Bug, Feature, Support, Mine }

/** Settings › Help & feedback: report a bug, request a feature (with votes), contact support, my requests. */
@Composable
fun FeedbackScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val gps by app.speedTracker.state.collectAsStateWithLifecycle()
    val moving = (gps.speedKmh ?: 0f) >= FEEDBACK_MAX_KMH
    var tab by rememberSaveable { mutableStateOf(FeedbackTab.Bug) }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.feedback_title), stringResource(R.string.feedback_hint), onBack)
        if (moving) {
            StoppedOnly(Modifier.fillMaxWidth().weight(1f))
            return@Column
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(Modifier.width(maxOf(dims.d(34f), 220.dp)).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                TabButton(Icons.Filled.BugReport, stringResource(R.string.feedback_bug), tab == FeedbackTab.Bug) { tab = FeedbackTab.Bug }
                TabButton(Icons.Filled.Lightbulb, stringResource(R.string.feedback_feature), tab == FeedbackTab.Feature) { tab = FeedbackTab.Feature }
                TabButton(Icons.Filled.SupportAgent, stringResource(R.string.feedback_support), tab == FeedbackTab.Support) { tab = FeedbackTab.Support }
                TabButton(Icons.Filled.Inbox, stringResource(R.string.feedback_mine), tab == FeedbackTab.Mine) { tab = FeedbackTab.Mine }
            }
            Pane(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    FeedbackTab.Bug -> ReportForm(app, ReportKind.Bug)
                    FeedbackTab.Feature -> Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { ReportForm(app, ReportKind.Feature) }
                        Box(Modifier.weight(1f)) { FeatureVotes(app) }
                    }
                    FeedbackTab.Support -> ReportForm(app, ReportKind.Support)
                    FeedbackTab.Mine -> MyRequests(app)
                }
            }
        }
    }
}

@Composable
private fun StoppedOnly(modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(
            Modifier.fillMaxSize().padding(dims.d(3f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f), Alignment.CenterVertically),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = theme.warning, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.feedback_stopped_only), fontSize = dims.t(4.4f, min = 24f), fontWeight = FontWeight.Bold, color = theme.text, textAlign = TextAlign.Center)
            Text(stringResource(R.string.feedback_stopped_hint), fontSize = dims.t(2.6f, min = 16f), color = theme.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TabButton(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    BigButton(text = text, onClick = onClick, modifier = Modifier.fillMaxWidth(), icon = icon, primary = selected)
}

@Composable
private fun ReportForm(app: DriveApp, kind: ReportKind) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by rememberSaveable(kind) { mutableStateOf("") }
    var body by rememberSaveable(kind) { mutableStateOf("") }
    var contact by rememberSaveable(kind) { mutableStateOf("") }
    var orderId by rememberSaveable(kind) { mutableStateOf("") }
    // "Attach app logs" is off by default (spec).
    var attachLogs by rememberSaveable(kind) { mutableStateOf(false) }
    val images = remember(kind) { mutableStateListOf<FeedbackRepository.Attachment>() }
    var message by remember(kind) { mutableStateOf<String?>(null) }
    var sending by remember(kind) { mutableStateOf(false) }

    val tooBig = stringResource(R.string.feedback_attach_too_big)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val a = app.feedback.importImage(uri)
            if (a == null) message = tooBig else images += a
        }
    }

    val draft = ReportDraft(kind, title, body, contact, orderId)
    val problems = FeedbackJson.validate(draft)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
    ) {
        Text(
            stringResource(
                when (kind) {
                    ReportKind.Bug -> R.string.feedback_bug_hint
                    ReportKind.Feature -> R.string.feedback_feature_hint
                    else -> R.string.feedback_support_hint
                },
            ),
            fontSize = dims.t(2.3f, min = 14f), color = theme.muted,
        )
        OutlinedTextField(
            title, { title = it.take(FeedbackJson.TITLE_MAX) },
            label = { Text(stringResource(if (kind == ReportKind.Support) R.string.feedback_subject else R.string.feedback_field_title)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            body, { body = it.take(FeedbackJson.BODY_MAX) },
            label = {
                Text(
                    stringResource(
                        when (kind) {
                            ReportKind.Bug -> R.string.feedback_what_happened
                            ReportKind.Feature -> R.string.feedback_description
                            else -> R.string.feedback_message
                        },
                    ),
                )
            },
            minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
        )
        if (kind == ReportKind.Support) {
            OutlinedTextField(orderId, { orderId = it.take(40) }, label = { Text(stringResource(R.string.feedback_order_id)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(contact, { contact = it.take(120) }, label = { Text(stringResource(R.string.feedback_contact)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (kind != ReportKind.Feature) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                BigButton(
                    stringResource(R.string.feedback_add_screenshot, images.size, FeedbackRepository.MAX_ATTACHMENTS),
                    { if (images.size < FeedbackRepository.MAX_ATTACHMENTS) pick.launch("image/*") },
                    icon = Icons.Filled.Photo, primary = false,
                )
                if (images.isNotEmpty()) {
                    BigButton(stringResource(R.string.feedback_clear_screenshots), { images.clear() }, primary = false)
                }
            }
        }
        if (kind == ReportKind.Bug) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { attachLogs = !attachLogs },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(attachLogs, { attachLogs = it }, colors = CheckboxDefaults.colors(checkedColor = theme.accent))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.feedback_attach_logs), color = theme.text, fontSize = dims.t(2.5f, min = 15f))
                    Text(stringResource(R.string.feedback_attach_logs_hint), color = theme.muted, fontSize = dims.t(2.1f, min = 12f))
                }
            }
        }
        if (FeedbackJson.Problem.BadEmail in problems) {
            Text(stringResource(R.string.feedback_bad_email), color = theme.warning, fontSize = dims.t(2.2f, min = 13f))
        }
        message?.let { Text(it, color = theme.accentSoft, fontSize = dims.t(2.5f, min = 15f), fontWeight = FontWeight.SemiBold) }

        val sentFmt = stringResource(R.string.feedback_sent)
        val sentNoRef = stringResource(R.string.feedback_sent_no_ref)
        val queuedText = stringResource(R.string.feedback_queued)
        val limitText = stringResource(R.string.feedback_rate_limited)
        val rejectedText = stringResource(R.string.feedback_rejected)
        BigButton(
            stringResource(if (app.feedback.hasBackend) R.string.feedback_send else R.string.feedback_send_email),
            onClick = {
                if (problems.isNotEmpty() || sending) return@BigButton
                val atts = images.toMutableList()
                if (!app.feedback.hasBackend) {
                    if (attachLogs) app.feedback.captureLogs()?.let { atts += it }
                    startSafely(context, app.feedback.emailIntent(draft, atts))
                    return@BigButton
                }
                sending = true
                scope.launch {
                    if (attachLogs) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.feedback.captureLogs() }?.let { atts += it }
                    }
                    val outcome = app.feedback.submit(draft, atts)
                    sending = false
                    message = when (outcome) {
                        is FeedbackRepository.Outcome.Sent -> if (outcome.ref.isNotBlank()) sentFmt.format(outcome.ref) else sentNoRef
                        FeedbackRepository.Outcome.Queued -> queuedText
                        FeedbackRepository.Outcome.RateLimited -> limitText
                        FeedbackRepository.Outcome.Rejected -> rejectedText
                    }
                    if (outcome is FeedbackRepository.Outcome.Sent || outcome == FeedbackRepository.Outcome.Queued) {
                        title = ""
                        body = ""
                        orderId = ""
                        images.clear()
                        attachLogs = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            primary = problems.isEmpty() && !sending,
        )
        if (!app.feedback.hasBackend) {
            Text(stringResource(R.string.feedback_email_note, FeedbackRepository.SUPPORT_EMAIL), color = theme.muted, fontSize = dims.t(2.1f, min = 12f))
        }
        // Website and support address, also when reports go to the server.
        Text(
            stringResource(R.string.website_title) + ": " + WEBSITE_URL.removePrefix("https://") + " · " + FeedbackRepository.SUPPORT_EMAIL,
            color = theme.muted,
            fontSize = dims.t(2.1f, min = 12f),
        )
    }
}

/** Top requested features with a ▲ vote button. Needs the server; "coming soon" without one. */
@Composable
private fun FeatureVotes(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FeatureItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (app.feedback.hasBackend) {
            items = app.feedback.features()
            failed = items == null
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
        Text(theme.label(stringResource(R.string.feedback_top_features)), style = theme.labelStyle(), fontSize = dims.t(2.1f))
        when {
            !app.feedback.hasBackend -> Text(stringResource(R.string.feedback_coming_soon), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            failed -> Text(stringResource(R.string.feedback_offline), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            items == null -> Text(stringResource(R.string.feedback_loading), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            items!!.isEmpty() -> Text(stringResource(R.string.feedback_no_features), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            else -> for (f in items!!) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (f.voted) return@IconButton
                            scope.launch {
                                if (app.feedback.vote(f.id)) {
                                    items = items?.map { if (it.id == f.id) it.copy(votes = it.votes + 1, voted = true) else it }
                                }
                            }
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            Icons.Filled.ArrowDropUp,
                            contentDescription = stringResource(R.string.feedback_vote),
                            tint = if (f.voted) theme.accent else theme.muted,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(f.votes.toString(), style = theme.numberStyle(glowColor = null), fontSize = dims.t(2.8f, min = 16f), modifier = Modifier.width(48.dp))
                    Text(f.title, color = theme.text, fontSize = dims.t(2.5f, min = 15f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The driver's own tickets with status and replies. Needs the server; "coming soon" without one. */
@Composable
private fun MyRequests(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    var items by remember { mutableStateOf<List<MyRequest>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val waiting = remember { app.feedback.queued().size }
    LaunchedEffect(Unit) {
        if (app.feedback.hasBackend) {
            app.feedback.flush()
            items = app.feedback.myRequests()
            failed = items == null
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(dims.d(2f)), verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
        if (waiting > 0) Text(stringResource(R.string.feedback_waiting, waiting), color = theme.warning, fontSize = dims.t(2.3f, min = 14f))
        when {
            !app.feedback.hasBackend -> Text(stringResource(R.string.feedback_coming_soon), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            failed -> Text(stringResource(R.string.feedback_offline), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            items == null -> Text(stringResource(R.string.feedback_loading), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            items!!.isEmpty() -> Text(stringResource(R.string.feedback_none_yet), color = theme.muted, fontSize = dims.t(2.4f, min = 14f))
            else -> for (r in items!!) {
                Column(Modifier.fillMaxWidth()) {
                    Text(r.ref + " · " + r.title, color = theme.text, fontSize = dims.t(2.6f, min = 15f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.status, color = theme.accentSoft, fontSize = dims.t(2.2f, min = 13f))
                    r.reply?.let { Text(stringResource(R.string.feedback_reply, it), color = theme.muted, fontSize = dims.t(2.3f, min = 14f)) }
                }
            }
        }
    }
}
