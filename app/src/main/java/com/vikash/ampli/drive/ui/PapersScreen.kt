package com.vikash.ampli.drive.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.car.DueLevel
import com.vikash.ampli.drive.car.DueStatus
import com.vikash.ampli.drive.car.PaperKind
import com.vikash.ampli.drive.car.Reminders
import com.vikash.ampli.drive.car.ServiceItem
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val DIGILOCKER_PACKAGE = "com.digilocker.android"
private const val DIGILOCKER_URL = "https://www.digilocker.gov.in"

/** DigiLocker app if installed, else the website (PUC, RC, licence and insurance live there). */
fun openDigiLocker(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(DIGILOCKER_PACKAGE)
    if (launch == null || !startSafely(context, launch)) {
        startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(DIGILOCKER_URL)))
    }
}

private fun dateLabel(context: Context, d: LocalDate): String =
    DateTimeFormatter.ofPattern("d MMM yyyy", context.resources.configuration.locales[0]).format(d)

/** Papers (expiry dates) and service schedule, with the GPS odometer. Locked while moving. */
@Composable
fun PapersScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val context = LocalContext.current
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    var editPaper by remember { mutableStateOf<PaperKind?>(null) }
    var editService by remember { mutableStateOf<ServiceItem?>(null) }
    var editOdo by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    // Reminders arrive as notifications; ask for that permission the first time Papers opens.
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.papers_title), stringResource(R.string.papers_hint), onBack)
        ParkToEdit(app, Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
                ) {
                    SectionLabel(stringResource(R.string.papers_section))
                    for (p in papers) {
                        DueRow(
                            title = CarMonitor.paperName(context, p.kind),
                            detail = p.expiry?.let { stringResource(R.string.papers_expires, dateLabel(context, it)) } ?: stringResource(R.string.papers_tap_to_set),
                            status = Reminders.paper(p, today),
                            onClick = { editPaper = p.kind },
                        )
                    }
                    BigButton(stringResource(R.string.papers_digilocker), { openDigiLocker(context) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Folder, primary = false)
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.2f)),
                ) {
                    SectionLabel(stringResource(R.string.service_section))
                    DueRow(
                        title = stringResource(R.string.odometer_title),
                        detail = stringResource(R.string.odometer_hint),
                        status = null,
                        value = "%,d km".format(odo.toLong()),
                        icon = Icons.Filled.Speed,
                        onClick = { editOdo = true },
                    )
                    for (s in services) {
                        DueRow(
                            title = s.name,
                            detail = intervalLabel(context, s) + (s.lastDate?.let { " · " + context.getString(R.string.service_last_done, dateLabel(context, it)) } ?: ""),
                            status = Reminders.service(s, today, odo),
                            onClick = { editService = s },
                        )
                    }
                    BigButton(
                        stringResource(R.string.service_add),
                        { editService = ServiceItem(UUID.randomUUID().toString(), "", 10_000, 12, null, null) },
                        Modifier.fillMaxWidth(), icon = Icons.Filled.Add, primary = false,
                    )
                }
            }
        }
    }

    editPaper?.let { kind ->
        val current = papers.firstOrNull { it.kind == kind }?.expiry
        DateDialog(
            title = CarMonitor.paperName(context, kind),
            initial = current ?: today.plusYears(1),
            onPick = {
                app.car.store.setPaper(kind, it)
                app.car.checkDue()
                editPaper = null
            },
            onClear = if (current != null) ({ app.car.store.setPaper(kind, null); editPaper = null }) else null,
            onDismiss = { editPaper = null },
        )
    }
    if (editOdo) {
        NumberDialog(
            title = stringResource(R.string.odometer_correct),
            hint = stringResource(R.string.odometer_correct_hint),
            initial = odo.toLong().toString(),
            onSave = { v -> v.toDoubleOrNull()?.let { app.car.store.setOdometer(it) }; editOdo = false },
            onDismiss = { editOdo = false },
        )
    }
    editService?.let { item ->
        ServiceDialog(
            item = item,
            odometerKm = odo,
            isNew = services.none { it.id == item.id },
            onSave = { app.car.store.saveService(it); app.car.checkDue(); editService = null },
            onDelete = { app.car.store.deleteService(item.id); editService = null },
            onDismiss = { editService = null },
        )
    }
}

private fun intervalLabel(context: Context, s: ServiceItem): String {
    val parts = listOfNotNull(
        s.everyKm?.let { context.getString(R.string.service_every_km, "%,d".format(it)) },
        s.everyMonths?.let { context.getString(R.string.service_every_months, it) },
    )
    return parts.joinToString(" / ").ifEmpty { context.getString(R.string.due_not_set) }
}

@Composable
internal fun SectionLabel(text: String) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Text(theme.label(text), style = theme.labelStyle(), fontSize = dims.t(2.4f), modifier = Modifier.padding(top = dims.d(0.6f)))
}

@Composable
private fun DueRow(
    title: String,
    detail: String,
    status: DueStatus?,
    onClick: () -> Unit,
    value: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val color = when (status?.level) {
        DueLevel.Overdue -> theme.danger
        DueLevel.Soon -> theme.warning
        DueLevel.Ok -> theme.ok
        else -> theme.muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouch)
            .themedCard(theme, theme.cardShape(dims, scale = 0.6f), border = if (status != null && status.level >= DueLevel.Soon) color else theme.border)
            .clickable(onClick = onClick)
            .padding(horizontal = dims.d(2f), vertical = dims.d(1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Icon(
            icon ?: when (status?.level) {
                DueLevel.Ok -> Icons.Filled.CheckCircle
                DueLevel.Soon, DueLevel.Overdue -> Icons.Filled.Error
                else -> Icons.Filled.Schedule
            },
            contentDescription = null, tint = if (icon != null) theme.accentSoft else color, modifier = Modifier.size(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { "—" }, fontSize = dims.t(2.8f, min = 16f), fontWeight = FontWeight.SemiBold, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = dims.t(2.2f, min = 13f), color = theme.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val right = value ?: status?.let { CarMonitor.dueText(context, it) }
        if (right != null) {
            Text(right, fontSize = dims.t(2.6f, min = 15f), fontWeight = FontWeight.Bold, color = if (value != null) theme.text else color, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------------------ Dialogs

@Composable
internal fun dialogColors(): Pair<Color, Color> {
    val theme = LocalDriveTheme.current
    return (if (theme.surface.alpha < 1f) theme.background else theme.surface) to theme.text
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(title: String, initial: LocalDate, onPick: (LocalDate) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (onClear != null) TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    ) {
        DatePicker(state = state, title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
internal fun NumberDialog(title: String, hint: String?, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val (bg, fg) = dialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg,
        titleContentColor = fg,
        textContentColor = fg,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hint != null) Text(hint)
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ServiceDialog(
    item: ServiceItem,
    odometerKm: Double,
    isNew: Boolean,
    onSave: (ServiceItem) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(item.name) }
    var everyKm by remember { mutableStateOf(item.everyKm?.toString() ?: "") }
    var everyMonths by remember { mutableStateOf(item.everyMonths?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(item.lastKm?.toLong()?.toString() ?: "") }
    var lastDate by remember { mutableStateOf(item.lastDate) }
    var pickDate by remember { mutableStateOf(false) }
    val (bg, fg) = dialogColors()

    fun build() = item.copy(
        name = name.trim().ifEmpty { context.getString(R.string.service_default_name) },
        everyKm = everyKm.toIntOrNull()?.takeIf { it > 0 },
        everyMonths = everyMonths.toIntOrNull()?.takeIf { it > 0 },
        lastKm = lastKm.toDoubleOrNull(),
        lastDate = lastDate,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg,
        titleContentColor = fg,
        textContentColor = fg,
        title = { Text(if (isNew) stringResource(R.string.service_add) else item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.service_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        everyKm, { v -> everyKm = v.filter { it.isDigit() } }, label = { Text(stringResource(R.string.service_every_km_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        everyMonths, { v -> everyMonths = v.filter { it.isDigit() } }, label = { Text(stringResource(R.string.service_every_months_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        lastKm, { v -> lastKm = v.filter { it.isDigit() } }, label = { Text(stringResource(R.string.service_last_km_label)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f)) {
                        TextButton(onClick = { pickDate = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                            Text(lastDate?.let { stringResource(R.string.service_last_done, dateLabel(context, it)) } ?: stringResource(R.string.service_last_date_label))
                        }
                    }
                }
                TextButton(
                    onClick = {
                        lastKm = odometerKm.toLong().toString()
                        lastDate = LocalDate.now()
                    },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(stringResource(R.string.service_done_now)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(build()) }, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_save)) } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_delete)) }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
    if (pickDate) {
        DateDialog(
            title = stringResource(R.string.service_last_date_label),
            initial = lastDate ?: LocalDate.now(),
            onPick = { lastDate = it; pickDate = false },
            onClear = if (lastDate != null) ({ lastDate = null; pickDate = false }) else null,
            onDismiss = { pickDate = false },
        )
    }
}
