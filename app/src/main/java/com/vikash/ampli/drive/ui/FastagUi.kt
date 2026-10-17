package com.vikash.ampli.drive.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.FastagStore
import com.vikash.ampli.drive.fastag.TollEvent
import com.vikash.ampli.drive.ui.theme.CardStyle
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/** "₹1,240.50" with Indian digit grouping; whole rupees drop the decimals. */
fun rupees(value: Double): String {
    val fmt = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        maximumFractionDigits = 2
    }
    return "₹" + fmt.format(value)
}

/** "5 min ago", refreshed every minute by the caller's recomposition. */
private fun ago(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

@Composable
private fun minuteTicker(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** Opens the driver's bank recharge page, or NPCI's list of FASTag banks. */
fun openFastagRecharge(context: Context, userUrl: String) {
    val raw = userUrl.trim().ifEmpty { FastagStore.NPCI_URL }
    val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(
            Intent.createChooser(view, context.getString(R.string.fastag_recharge)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Status-row chip: FASTag balance and when it was last updated. Red below the low-balance
 * threshold. The whole row height is the tap target (at least 56 dp).
 */
@Composable
fun FastagChip(app: DriveApp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    val now = minuteTicker()
    val latest = remember(events) { FastagStore.latestBalance(events) }
    val balance = latest?.balance
    val isLow = balance != null && balance < low
    val shape = if (theme.radius <= 0f || theme.cardStyle == CardStyle.Skewed) RectangleShape else CircleShape
    val fg = if (isLow) theme.onDanger else theme.text

    val text = buildString {
        append(stringResource(R.string.fastag_title))
        append(" · ")
        append(if (balance != null) rupees(balance) else "—")
        if (latest != null && !dims.compact) {
            append(" · ")
            append(ago(latest.time, now))
        }
    }
    Box(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(if (isLow) theme.danger else theme.surface)
                .border(1.dp, if (isLow) theme.danger else theme.border, shape)
                .padding(horizontal = dims.d(1.8f), vertical = dims.d(0.9f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1f)),
        ) {
            Icon(
                Icons.Filled.Toll,
                contentDescription = null,
                tint = if (isLow) theme.onDanger else theme.accentSoft,
                modifier = Modifier.size(dims.d(2.8f)),
            )
            Text(
                text,
                fontSize = dims.t(2.4f, min = 13f),
                color = fg,
                fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "₹95 · Khed Shivapur toll · Balance ₹1,240" */
@Composable
private fun tollLine(event: TollEvent): String = listOfNotNull(
    event.amount?.let { rupees(it) },
    event.plaza?.let { stringResource(R.string.fastag_toll_at, it) } ?: stringResource(R.string.fastag_toll_paid),
    event.balance?.let { stringResource(R.string.fastag_balance_value, rupees(it)) },
).joinToString(" · ")

/** The 6-second card shown on Home when a toll SMS arrives. Tap opens the FASTag screen. */
@Composable
fun TollToast(event: TollEvent, lowBalance: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val isLow = event.balance != null && event.balance < lowBalance
    val shape = theme.cardShape(dims, scale = 0.8f)
    Row(
        modifier = modifier
            .widthIn(max = 720.dp)
            .heightIn(min = MinTouch)
            .themedCard(
                theme,
                shape,
                fill = Brush.linearGradient(listOf(theme.surfaceHigh, theme.surfaceHigh)),
                border = if (isLow) theme.danger else theme.accent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(3f), vertical = dims.d(1.6f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Box(
            Modifier
                .size(maxOf(dims.d(7f), 40.dp))
                .clip(CircleShape)
                .background(if (isLow) theme.danger else theme.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Toll, contentDescription = null, tint = if (isLow) theme.onDanger else theme.onAccent)
        }
        Text(
            tollLine(event),
            fontSize = dims.t(3.2f, min = 18f),
            fontWeight = FontWeight.SemiBold,
            color = theme.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Draws over the home screen: when a toll SMS arrives, its card shows for 6 seconds at the top.
 * Only the card itself takes touches; the rest of the home screen stays usable.
 */
@Composable
fun TollToastHost(app: DriveApp, onOpenFastag: () -> Unit) {
    val dims = LocalDims.current
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    var card by remember { mutableStateOf<TollEvent?>(null) }
    LaunchedEffect(Unit) { app.fastag.arrivals.collect { card = it } }
    LaunchedEffect(card) {
        if (card != null) {
            delay(6_000L)
            card = null
        }
    }
    Box(Modifier.fillMaxSize().padding(top = dims.d(12f)), contentAlignment = Alignment.TopCenter) {
        card?.let { event ->
            TollToast(
                event = event,
                lowBalance = low,
                onClick = {
                    card = null
                    onOpenFastag()
                },
            )
        }
    }
}

/** Balance, trip/today/month totals and the history list. */
@Composable
fun FastagScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val events by app.fastag.events.collectAsStateWithLifecycle()
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    val url by app.fastag.rechargeUrl.collectAsStateWithLifecycle()
    val now = minuteTicker()
    val latest = remember(events) { FastagStore.latestBalance(events) }
    val totals = remember(events, now) { FastagStore.totals(events, now) }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        ScreenHeader(
            title = stringResource(R.string.fastag_title),
            hint = latest?.let { stringResource(R.string.fastag_updated, ago(it.time, now)) },
            onBack = onBack,
        )
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
            ) {
                val balance = latest?.balance
                val isLow = balance != null && balance < low
                Pane(Modifier.fillMaxWidth(), fill = if (isLow) theme.danger else null) {
                    Column(Modifier.padding(dims.d(2.6f))) {
                        Text(
                            theme.label(stringResource(if (isLow) R.string.fastag_low else R.string.fastag_balance)),
                            style = theme.labelStyle(if (isLow) theme.onDanger else theme.muted),
                            fontSize = dims.t(2.4f),
                        )
                        Text(
                            if (balance != null) rupees(balance) else "—",
                            style = theme.numberStyle(if (isLow) theme.onDanger else theme.text),
                            fontSize = dims.t(9f, min = 36f),
                        )
                        val info = listOfNotNull(latest?.vehicle, latest?.bank)
                        if (info.isNotEmpty()) {
                            Text(
                                info.joinToString(" · "),
                                fontSize = dims.t(2.4f),
                                color = if (isLow) theme.onDanger else theme.muted,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                    TotalTile(stringResource(R.string.fastag_trip), totals.trip, Modifier.weight(1f))
                    TotalTile(stringResource(R.string.fastag_today), totals.today, Modifier.weight(1f))
                    TotalTile(stringResource(R.string.fastag_month), totals.month, Modifier.weight(1f))
                }
                BigButton(
                    text = stringResource(R.string.fastag_recharge),
                    onClick = { openFastagRecharge(context, url) },
                    icon = Icons.Filled.Payment,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Pane(Modifier.weight(1.3f).fillMaxHeight()) {
                if (events.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(dims.d(3f)),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.fastag_empty), fontSize = dims.t(3f, min = 18f), color = theme.text)
                        Text(stringResource(R.string.fastag_needs_access), fontSize = dims.t(2.4f), color = theme.muted)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = dims.d(2.4f)),
                        verticalArrangement = Arrangement.spacedBy(dims.d(0.4f)),
                    ) {
                        item {
                            Text(
                                theme.label(stringResource(R.string.fastag_history)),
                                style = theme.labelStyle(),
                                fontSize = dims.t(2.4f),
                                modifier = Modifier.padding(top = dims.d(2f), bottom = dims.d(1f)),
                            )
                        }
                        items(events) { e ->
                            HistoryRow(e, now)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, value: Double, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(Modifier.padding(dims.d(2f))) {
            Text(theme.label(label), style = theme.labelStyle(), fontSize = dims.t(2.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(rupees(value), style = theme.numberStyle(), fontSize = dims.t(4.4f, min = 20f), maxLines = 1)
        }
    }
}

@Composable
private fun HistoryRow(e: TollEvent, now: Long) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val title = when (e.kind) {
        FastagKind.Debit -> e.plaza?.let { stringResource(R.string.fastag_toll_at, it) } ?: stringResource(R.string.fastag_toll_paid)
        FastagKind.Recharge -> stringResource(R.string.fastag_recharged)
        FastagKind.Balance -> stringResource(R.string.fastag_balance_update)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = dims.t(2.8f, min = 16f), color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(ago(e.time, now), e.balance?.let { stringResource(R.string.fastag_balance_value, rupees(it)) })
                    .joinToString(" · "),
                fontSize = dims.t(2.2f),
                color = theme.muted,
                maxLines = 1,
            )
        }
        e.amount?.let {
            Text(
                (if (e.kind == FastagKind.Recharge) "+" else "−") + rupees(it),
                style = theme.numberStyle(if (e.kind == FastagKind.Recharge) theme.ok else theme.text, glowColor = null),
                fontSize = dims.t(3.2f, min = 18f),
            )
        }
    }
}

/** Settings card: on/off, low-balance threshold, recharge page. */
@Composable
internal fun FastagSettingsCard(app: DriveApp, onOpenFastag: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val enabled by app.fastag.enabled.collectAsStateWithLifecycle()
    val low by app.fastag.lowBalance.collectAsStateWithLifecycle()
    val savedUrl by app.fastag.rechargeUrl.collectAsStateWithLifecycle()
    var url by remember(savedUrl) { mutableStateOf(savedUrl) }

    SettingCard {
        Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouch)
                    .clickable { app.fastag.setEnabled(!enabled) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.d(2f)),
            ) {
                Icon(Icons.Filled.Toll, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(32.dp))
                Box(Modifier.weight(1f)) {
                    CardTitle(stringResource(R.string.fastag_settings_title), stringResource(R.string.fastag_settings_hint))
                }
                ThemedSwitch(enabled) { app.fastag.setEnabled(it) }
            }
            if (enabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                ) {
                    Text(stringResource(R.string.fastag_low_title), fontSize = dims.t(2.6f), color = theme.muted)
                    for (v in FastagStore.LOW_BALANCE_CHOICES) {
                        Chip(rupees(v.toDouble()), v == low, { app.fastag.setLowBalance(v) })
                    }
                }
                // No typing while the car moves.
                if (isMoving(app)) {
                    Text(stringResource(R.string.park_to_edit_hint), fontSize = dims.t(2.4f), color = theme.warning)
                } else Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            app.fastag.setRechargeUrl(it)
                        },
                        label = { Text(stringResource(R.string.fastag_url_label)) },
                        supportingText = { Text(stringResource(R.string.fastag_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    )
                    BigButton(
                        text = stringResource(R.string.fastag_recharge),
                        onClick = { openFastagRecharge(context, url) },
                        icon = Icons.Filled.Payment,
                    )
                    BigButton(text = stringResource(R.string.fastag_open), onClick = onOpenFastag, primary = false)
                }
            }
        }
    }
}
