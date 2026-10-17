package com.vikash.ampli.drive.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.PaperVoice
import com.vikash.ampli.drive.car.PaperVoicePolicy
import com.vikash.ampli.drive.fastag.AnnualPass
import com.vikash.ampli.drive.fastag.AnnualPassMath
import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.FastagMessage
import com.vikash.ampli.drive.fastag.FastagStore
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.system.RestrictedSettings
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** The official Parivahan e-Challan "check challan" page. Opened in the browser; nothing is scraped. */
const val PARIVAHAN_CHALLAN_URL = "https://echallan.parivahan.gov.in/index/accused-challan"

/**
 * Opens the official e-Challan site. The vehicle number (from the FASTag SMS, when known) is
 * copied first so the driver can paste it there. Parked use only: the caller hides it while moving.
 */
fun openChallanCheck(context: Context, vehicle: String?) {
    if (!vehicle.isNullOrBlank()) {
        try {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("vehicle", vehicle))
            Toast.makeText(context, context.getString(R.string.challan_copied, vehicle), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }
    startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(PARIVAHAN_CHALLAN_URL)))
}

private fun shortDate(context: Context, d: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", context.resources.configuration.locales[0]).format(d)

/**
 * FASTag Annual Pass (₹3,075, 200 trips or one year): trips left with a progress bar, the last
 * valid day, and big −1 / +1 buttons (no typing, so they work while moving). The start date is
 * picked from a calendar, parked only.
 */
@Composable
fun AnnualPassCard(app: DriveApp, modifier: Modifier = Modifier, today: LocalDate = LocalDate.now()) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val pass by app.fastag.pass.collectAsStateWithLifecycle()
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val status = remember(pass, events, today) { AnnualPassMath.status(pass, events, today) }
    val moving = isMoving(app)
    var pickStart by remember { mutableStateOf(false) }

    Pane(modifier) {
        Column(Modifier.padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                Icon(Icons.Filled.Toll, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(
                        stringResource(R.string.pass_title),
                        stringResource(R.string.pass_hint, AnnualPass.TRIPS, rupees(AnnualPass.PRICE_RUPEES.toDouble())),
                    )
                }
            }
            if (status == null) {
                Text(stringResource(R.string.pass_none), color = theme.text, fontSize = dims.t(2.6f, min = 16f))
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                    BigButton(
                        text = stringResource(if (moving) R.string.park_to_set else R.string.pass_add),
                        onClick = { if (!moving) pickStart = true },
                        icon = Icons.Filled.EditCalendar,
                        primary = !moving,
                    )
                    BigButton(
                        text = stringResource(R.string.pass_about),
                        onClick = { startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(AnnualPass.INFO_URL))) },
                        icon = Icons.Filled.Info,
                        primary = false,
                    )
                }
            } else {
                val shown by animateIntAsState(status.remaining, tween(600), label = "passTrips")
                val progress by animateFloatAsState(status.remaining / AnnualPass.TRIPS.toFloat(), tween(600), label = "passBar")
                val color = when {
                    status.expired -> theme.danger
                    status.low -> theme.warning
                    else -> theme.accent
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Text(shown.toString(), style = theme.numberStyle(color), fontSize = dims.t(7.4f, min = 34f))
                    Text(
                        stringResource(R.string.pass_trips_left) + " / " + AnnualPass.TRIPS,
                        color = theme.muted,
                        fontSize = dims.t(2.6f, min = 16f),
                        modifier = Modifier.padding(bottom = dims.d(1.4f)),
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 8.dp),
                    color = color,
                    trackColor = theme.surfaceHigh,
                )
                Text(
                    if (status.expired) {
                        stringResource(R.string.pass_expired)
                    } else {
                        stringResource(R.string.pass_valid_till, shortDate(context, status.expiry), status.daysLeft.toInt())
                    },
                    color = if (status.expired) theme.danger else theme.text,
                    fontSize = dims.t(2.6f, min = 16f),
                    fontWeight = if (status.expired) FontWeight.Bold else FontWeight.Normal,
                )
                AnimatedVisibility(visible = status.low, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Text(stringResource(R.string.pass_low), color = theme.warning, fontSize = dims.t(2.6f, min = 16f), fontWeight = FontWeight.Bold)
                }
                Text(
                    stringResource(if (status.fromSms) R.string.pass_source_sms else R.string.pass_source_count),
                    color = theme.muted,
                    fontSize = dims.t(2.2f, min = 13f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                    BigButton(
                        text = stringResource(R.string.pass_minus),
                        onClick = { app.fastag.adjustPassTrips(-1) },
                        icon = Icons.Filled.Remove,
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                    BigButton(
                        text = stringResource(R.string.pass_plus),
                        onClick = { app.fastag.adjustPassTrips(+1) },
                        icon = Icons.Filled.Add,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!moving) {
                    Text(
                        stringResource(R.string.pass_change_start),
                        color = theme.accentSoft,
                        fontSize = dims.t(2.4f, min = 14f),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable { pickStart = true }
                            .padding(vertical = dims.d(1f)),
                    )
                }
            }
        }
    }

    if (pickStart && !moving) {
        DateDialog(
            title = stringResource(R.string.pass_start_title),
            initial = pass.start ?: today,
            onPick = {
                // A start date in the future is a typo: clamp to today.
                app.fastag.setPassStart(if (it.isAfter(today)) today else it)
                pickStart = false
            },
            onClear = if (pass.start != null) ({ app.fastag.setPassStart(null); pickStart = false }) else null,
            onDismiss = { pickStart = false },
        )
    }
}

/** "Pass 152" for the home FASTag chip, or null without a pass. */
@Composable
fun passChipText(app: DriveApp): String? {
    val pass by app.fastag.pass.collectAsStateWithLifecycle()
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val status = remember(pass, events) { AnnualPassMath.status(pass, events, LocalDate.now()) } ?: return null
    if (status.expired) return null
    return stringResource(R.string.pass_chip, status.remaining)
}

/**
 * FASTag SMS reading, opt-in: the prominent disclosure first, then notification access. Off,
 * the balance is entered by hand ([ManualBalanceButton]) and pass trips with −1 / +1.
 */
@Composable
internal fun FastagSmsRow(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val consent by app.fastag.smsConsent.collectAsStateWithLifecycle()
    var ask by remember { mutableStateOf(false) }

    fun toggle(on: Boolean) {
        if (!on) {
            app.fastag.setSmsConsent(false)
        } else {
            ask = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .clickable { toggle(!consent) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Icon(Icons.Filled.Sms, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
        Box(Modifier.weight(1f)) {
            CardTitle(
                stringResource(R.string.fastag_sms_title),
                stringResource(if (consent) R.string.fastag_sms_on else R.string.fastag_sms_off),
            )
        }
        ThemedSwitch(consent) { toggle(it) }
    }
    if (ask) {
        DisclosureDialog(
            kind = Disclosure.FastagSms,
            onAgree = {
                ask = false
                app.fastag.setSmsConsent(true)
                if (!RestrictedSettings.hasNotificationAccess(context)) Launchers.openNotificationAccess(context)
            },
            onDecline = { ask = false },
        )
    }
}

/** Manual FASTag balance (the fallback without SMS reading). Typing only while parked. */
@Composable
internal fun ManualBalanceButton(app: DriveApp, modifier: Modifier = Modifier) {
    val moving = isMoving(app)
    var open by remember { mutableStateOf(false) }
    BigButton(
        text = stringResource(if (moving) R.string.park_to_set else R.string.fastag_manual_balance),
        onClick = { if (!moving) open = true },
        primary = false,
        modifier = modifier,
    )
    if (open && !moving) {
        val latest = FastagStore.latestBalance(app.fastag.events.value)?.balance
        NumberDialog(
            title = stringResource(R.string.fastag_manual_balance),
            hint = stringResource(R.string.fastag_manual_balance_hint),
            initial = latest?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "",
            onSave = { v ->
                v.replace(",", "").trim().toDoubleOrNull()?.takeIf { it >= 0 && it < 1_000_000 }?.let {
                    app.fastag.add(FastagMessage(FastagKind.Balance, null, null, it, null, null), System.currentTimeMillis(), announce = false)
                }
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/**
 * Settings › Car papers and challan: spoken reminders at car start (on by default, 3 / 7 / 15
 * days before), a "hear it now" sample, and the e-Challan check (parked only).
 */
@Composable
internal fun PapersReminderCard(app: DriveApp) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val on by app.paperVoice.enabled.collectAsStateWithLifecycle()
    val days by app.paperVoice.daysBefore.collectAsStateWithLifecycle()
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val moving = isMoving(app)
    val next = remember(papers, days) { app.paperVoice.pending(ignoreHistory = true).firstOrNull() }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = MinTouch).clickable { app.paperVoice.setEnabled(!on) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.papers_voice_title), stringResource(R.string.papers_voice_hint))
                }
                ThemedSwitch(on) { app.paperVoice.setEnabled(it) }
            }
            AnimatedVisibility(visible = on, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                        Text(stringResource(R.string.papers_voice_days), fontSize = dims.t(2.6f), color = theme.muted)
                        for (d in PaperVoicePolicy.DAYS_CHOICES) Chip(d.toString(), d == days, { app.paperVoice.setDaysBefore(d) })
                    }
                    Text(
                        next?.let { stringResource(R.string.papers_voice_next, PaperVoice.standardLine(context, it)) }
                            ?: stringResource(R.string.papers_voice_none),
                        fontSize = dims.t(2.4f, min = 14f),
                        color = if (next != null) theme.warning else theme.muted,
                    )
                    BigButton(
                        text = stringResource(R.string.papers_voice_hear),
                        onClick = { scope.launch { app.paperVoice.sample() } },
                        icon = Icons.Filled.VolumeUp,
                        primary = false,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Gavel, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(28.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.challan_check), stringResource(R.string.challan_hint))
                }
                BigButton(
                    text = stringResource(if (moving) R.string.park_to_set else R.string.challan_open),
                    onClick = {
                        if (!moving) openChallanCheck(context, app.fastag.events.value.firstNotNullOfOrNull { it.vehicle })
                    },
                    primary = false,
                )
            }
        }
    }
}
