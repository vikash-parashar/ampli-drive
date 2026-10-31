package com.vikash.ampli.drive.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.car.Expense
import com.vikash.ampli.drive.car.ExpenseKind
import com.vikash.ampli.drive.car.FuelFill
import com.vikash.ampli.drive.car.FuelType
import com.vikash.ampli.drive.car.CarMonitor
import com.vikash.ampli.drive.car.Mileage
import com.vikash.ampli.drive.car.MonthTotal
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme
import java.io.File
import java.time.format.TextStyle

private sealed interface LogRow {
    val time: Long
    data class Fuel(val fill: FuelFill) : LogRow { override val time get() = fill.time }
    data class Cost(val expense: Expense) : LogRow { override val time get() = expense.time }
}

@Composable
fun ExpensesScreen(app: DriveApp, onBack: () -> Unit, onUnlockPro: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val fills by app.car.store.fills.collectAsStateWithLifecycle()
    // FASTag tolls are already in here (TollSync), so nothing is merged at display time.
    val expenses by app.car.store.expenses.collectAsStateWithLifecycle()
    val odo by app.car.store.odometerKm.collectAsStateWithLifecycle()
    val pro by app.prefs.pro.collectAsStateWithLifecycle()
    // Mileage of the fuel most of the fills were (a CNG car's petrol top-ups don't mix in).
    val main = remember(fills) { Mileage.mainFuel(fills) }
    val avg = remember(fills, main) { main?.let { Mileage.average(fills, it) } ?: Mileage.average(fills) }
    val last = remember(fills, main) {
        (main?.let { Mileage.segments(fills, it) } ?: Mileage.segments(fills)).maxByOrNull { it.endTime }?.kmPerLitre
    }
    val trend = remember(fills, main) { main?.let { Mileage.trend(fills, it) }.orEmpty() }
    val mileageUnit = stringResource(if (main?.byWeight == true) R.string.unit_kmkg else R.string.unit_kmpl)
    val papers by app.car.store.papers.collectAsStateWithLifecycle()
    val services by app.car.store.services.collectAsStateWithLifecycle()
    val due = remember(papers, services, (odo / 10).toInt()) { app.car.dueItems(context) }
    val perKm = remember(fills, expenses) { Mileage.costPerKm(fills, expenses) }
    val months = remember(fills, expenses) { Mileage.monthly(fills, expenses, System.currentTimeMillis()) }
    val rows = remember(fills, expenses) { (fills.map { LogRow.Fuel(it) } + expenses.map { LogRow.Cost(it) }).sortedByDescending { it.time } }
    var addFuel by rememberSaveable { mutableStateOf(false) }
    var addExpense by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(dims.d(2.4f)), verticalArrangement = Arrangement.spacedBy(dims.d(2f))) {
        ScreenHeader(stringResource(R.string.expenses_title), stringResource(R.string.expenses_hint), onBack)
        ParkToEdit(app, Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(dims.d(2f))) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                        StatTile(stringResource(R.string.expenses_avg), avg?.let { "%.1f".format(it) } ?: "—", mileageUnit, Modifier.weight(1f))
                        StatTile(stringResource(R.string.expenses_last), last?.let { "%.1f".format(it) } ?: "—", mileageUnit, Modifier.weight(1f))
                        StatTile(stringResource(R.string.expenses_per_km), perKm?.let { rupees(Math.round(it * 100) / 100.0) } ?: "—", stringResource(R.string.unit_per_km), Modifier.weight(1f))
                    }
                    if (trend.size >= 2) TrendLine(trend, mileageUnit, Modifier.fillMaxWidth())
                    RemindersPanel(due.map { CarMonitor.dueLine(context, it) to (it.status.level == com.vikash.ampli.drive.car.DueLevel.Overdue) }, Modifier.fillMaxWidth())
                    MonthBars(months, Modifier.fillMaxWidth().heightIn(min = 170.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                        BigButton(stringResource(R.string.expenses_add_fuel), { addFuel = true }, Modifier.weight(1f), icon = Icons.Filled.LocalGasStation)
                        BigButton(stringResource(R.string.expenses_add_other), { addExpense = true }, Modifier.weight(1f), icon = Icons.Filled.Receipt, primary = false)
                    }
                    BigButton(
                        stringResource(if (pro.isPro) R.string.expenses_export else R.string.expenses_export_pro),
                        { if (pro.isPro) exportCsv(context, fills, expenses) else onUnlockPro() },
                        Modifier.fillMaxWidth(), icon = Icons.Filled.FileDownload, primary = false,
                    )
                    Text(stringResource(R.string.expenses_tolls_auto), fontSize = dims.t(2.2f), color = theme.muted)
                }
                Pane(Modifier.weight(1.1f).fillMaxHeight()) {
                    if (rows.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(dims.d(3f)), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.expenses_empty), fontSize = dims.t(2.8f, min = 16f), color = theme.muted)
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = dims.d(2f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.4f))) {
                            items(rows) { row ->
                                LogLine(row, onDelete = {
                                    when (row) {
                                        is LogRow.Fuel -> app.car.store.deleteFill(row.fill.time)
                                        is LogRow.Cost -> app.car.store.deleteExpense(row.expense)
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    if (addFuel) {
        FuelDialog(
            odometerKm = odo,
            // Most cars fill the same fuel every time: start on the last one.
            lastFuel = fills.maxByOrNull { it.time }?.fuel ?: FuelType.Petrol,
            onSave = {
                app.car.store.addFill(it)
                // A fill-up odometer from the dashboard is better than GPS: adopt it.
                if (it.odometerKm > 0) app.car.store.setOdometer(it.odometerKm)
                addFuel = false
            },
            onDismiss = { addFuel = false },
        )
    }
    if (addExpense) {
        ExpenseDialog(onSave = { app.car.store.addExpense(it); addExpense = false }, onDismiss = { addExpense = false })
    }
}

private fun exportCsv(context: Context, fills: List<FuelFill>, expenses: List<Expense>) {
    try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ampli-car-expenses.csv")
        file.writeText(Mileage.csv(fills, expenses))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.expenses_title))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startSafely(context, Intent.createChooser(send, context.getString(R.string.expenses_export)))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(Modifier.padding(dims.d(1.8f))) {
            Text(theme.label(label), style = theme.labelStyle(), fontSize = dims.t(2.1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = theme.numberStyle(), fontSize = dims.t(4.6f, min = 22f), maxLines = 1)
            Text(unit, fontSize = dims.t(2f, min = 12f), color = theme.muted, maxLines = 1)
        }
    }
}

/** Six months of spending as stacked bars: fuel, service, tolls, other. */
@Composable
private fun MonthBars(months: List<MonthTotal>, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val colors = listOf(theme.accent, theme.warning, theme.ok, theme.muted)
    val max = (months.maxOfOrNull { it.total } ?: 0.0).coerceAtLeast(1.0)
    val locale = context.resources.configuration.locales[0]
    Pane(modifier) {
        Column(Modifier.padding(dims.d(1.8f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.8f))) {
            Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.label(stringResource(R.string.expenses_monthly)), style = theme.labelStyle(), fontSize = dims.t(2.1f), modifier = Modifier.weight(1f))
                for ((i, label) in listOf(R.string.expense_fuel, R.string.expense_service, R.string.expense_toll, R.string.expense_other).withIndex()) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                    Text(stringResource(label), fontSize = dims.t(1.9f, min = 11f), color = theme.muted)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 110.dp), horizontalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                for (m in months) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (m.total > 0) rupeesShort(m.total) else "", fontSize = dims.t(1.9f, min = 11f), color = theme.text, maxLines = 1)
                        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                            var y = size.height
                            val parts = listOf(m.fuel, m.service, m.toll, m.other)
                            for ((i, v) in parts.withIndex()) {
                                if (v <= 0.0) continue
                                val h = (v / max * size.height).toFloat()
                                y -= h
                                drawRect(colors[i], Offset(size.width * 0.15f, y), Size(size.width * 0.7f, h))
                            }
                        }
                        Text(m.month.month.getDisplayName(TextStyle.SHORT, locale), fontSize = dims.t(2f, min = 12f), color = theme.muted)
                    }
                }
            }
        }
    }
}

/** Km/l (or km/kg) of the last few fills of the main fuel, oldest first, as a small line. */
@Composable
private fun TrendLine(values: List<Double>, unit: String, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val lo = values.min()
    val hi = values.max().coerceAtLeast(lo + 0.5)
    Pane(modifier) {
        Row(
            Modifier.padding(dims.d(1.8f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            Column(Modifier.weight(1f)) {
                Text(theme.label(stringResource(R.string.expenses_trend, values.size)), style = theme.labelStyle(), fontSize = dims.t(2.1f), maxLines = 1)
                Text("%.1f → %.1f %s".format(values.first(), values.last(), unit), fontSize = dims.t(2.4f, min = 15f), color = theme.text, maxLines = 1)
            }
            Canvas(Modifier.weight(1f).height(48.dp)) {
                val step = size.width / (values.size - 1)
                fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
                for (i in 1 until values.size) {
                    drawLine(theme.accent, Offset(step * (i - 1), y(values[i - 1])), Offset(step * i, y(values[i])), strokeWidth = 5f)
                }
                drawCircle(theme.accent, 7f, Offset(step * (values.size - 1), y(values.last())))
            }
        }
    }
}

/** PUC, insurance and service jobs due soon (the same list the car-start voice and home card use). */
@Composable
private fun RemindersPanel(lines: List<Pair<String, Boolean>>, modifier: Modifier) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Pane(modifier) {
        Column(Modifier.padding(dims.d(1.8f)), verticalArrangement = Arrangement.spacedBy(dims.d(0.6f))) {
            Text(theme.label(stringResource(R.string.expenses_reminders)), style = theme.labelStyle(), fontSize = dims.t(2.1f))
            if (lines.isEmpty()) {
                Text(stringResource(R.string.papers_all_ok), fontSize = dims.t(2.4f, min = 15f), color = theme.ok)
            }
            for ((line, overdue) in lines.take(3)) {
                Text(
                    line, fontSize = dims.t(2.4f, min = 15f), color = if (overdue) theme.warning else theme.text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun fuelLabel(f: FuelType): String = stringResource(
    when (f) {
        FuelType.Petrol -> R.string.fuel_filter_petrol
        FuelType.Diesel -> R.string.fuel_filter_diesel
        FuelType.Cng -> R.string.fuel_filter_cng
    },
)

private fun quantity(fill: FuelFill): String = if (fill.fuel.byWeight) "%.1f kg".format(fill.litres) else "%.1f L".format(fill.litres)

/** "₹12.4k" for chart labels. */
private fun rupeesShort(v: Double): String = if (v >= 1000) "₹" + "%.1fk".format(v / 1000.0) else rupees(Math.round(v).toDouble())

@Composable
private fun LogLine(row: LogRow, onDelete: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val (icon, title, detail, amount) = when (row) {
        is LogRow.Fuel -> Quad(
            Icons.Filled.LocalGasStation,
            fuelLabel(row.fill.fuel) + " · " + quantity(row.fill) + if (row.fill.fullTank) " · " + stringResource(R.string.expenses_full_short) else "",
            clockLabel(context, row.time) + " · " + "%,d km".format(row.fill.odometerKm.toLong()),
            row.fill.rupees,
        )
        is LogRow.Cost -> Quad(
            if (row.expense.kind == ExpenseKind.Toll) Icons.Filled.Toll else Icons.Filled.Receipt,
            stringResource(kindLabel(row.expense.kind)) + (if (row.expense.note.isNotBlank()) " · " + row.expense.note else ""),
            clockLabel(context, row.time) + if (row.expense.fromFastag) " · FASTag" else "",
            row.expense.rupees,
        )
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
    ) {
        Icon(icon, contentDescription = null, tint = theme.accentSoft, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = dims.t(2.6f, min = 15f), color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = dims.t(2.1f, min = 12f), color = theme.muted, maxLines = 1)
        }
        Text(rupees(amount), style = theme.numberStyle(glowColor = null), fontSize = dims.t(3f, min = 17f))
        // A FASTag toll can be deleted too: it is then hidden and never copied again.
        IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = theme.muted)
        }
    }
}

private data class Quad(val a: androidx.compose.ui.graphics.vector.ImageVector, val b: String, val c: String, val d: Double)

private fun kindLabel(k: ExpenseKind): Int = when (k) {
    ExpenseKind.Service -> R.string.expense_service
    ExpenseKind.Repair -> R.string.expense_repair
    ExpenseKind.Toll -> R.string.expense_toll
    ExpenseKind.Parking -> R.string.expense_parking
    ExpenseKind.Other -> R.string.expense_other
}

@Composable
private fun FuelDialog(odometerKm: Double, lastFuel: FuelType, onSave: (FuelFill) -> Unit, onDismiss: () -> Unit) {
    var litres by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var odo by remember { mutableStateOf(if (odometerKm > 0) odometerKm.toLong().toString() else "") }
    var full by remember { mutableStateOf(true) }
    var fuel by remember { mutableStateOf(lastFuel) }
    val (bg, fg) = dialogColors()
    val ok = (litres.toDoubleOrNull() ?: 0.0) > 0.0 && (amount.toDoubleOrNull() ?: 0.0) > 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(stringResource(R.string.expenses_add_fuel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (f in FuelType.entries) Chip(fuelLabel(f), fuel == f, { fuel = f })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField(litres, { litres = it }, stringResource(if (fuel.byWeight) R.string.fuel_kg else R.string.fuel_litres), Modifier.weight(1f))
                    DecimalField(amount, { amount = it }, stringResource(R.string.fuel_amount), Modifier.weight(1f))
                }
                DecimalField(odo, { odo = it }, stringResource(R.string.fuel_odometer), Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { full = !full },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.fuel_full_tank), modifier = Modifier.weight(1f))
                    ThemedSwitch(full) { full = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(FuelFill(System.currentTimeMillis(), litres.toDouble(), amount.toDouble(), odo.toDoubleOrNull() ?: odometerKm, full, fuel))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ExpenseDialog(onSave: (Expense) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(ExpenseKind.Service) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val (bg, fg) = dialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(stringResource(R.string.expenses_add_other)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (line in listOf(ExpenseKind.Service, ExpenseKind.Repair, ExpenseKind.Parking, ExpenseKind.Toll, ExpenseKind.Other).chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (k in line) Chip(stringResource(kindLabel(k)), kind == k, { kind = k })
                    }
                }
                DecimalField(amount, { amount = it }, stringResource(R.string.fuel_amount), Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it.take(80) }, label = { Text(stringResource(R.string.expense_note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0, onClick = {
                onSave(Expense(System.currentTimeMillis(), kind, amount.toDouble(), note.trim()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DecimalField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.heightIn(min = 56.dp),
    )
}
