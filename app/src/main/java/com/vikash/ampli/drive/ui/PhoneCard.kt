package com.vikash.ampli.drive.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.system.FavouriteCallsLogic
import com.vikash.ampli.drive.system.FavouriteCallsStore
import com.vikash.ampli.drive.system.FavouriteContact
import com.vikash.ampli.drive.system.Launchers
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

/** Avatar colours for the favourite buttons, in order. */
private val AvatarColors = listOf(Color(0xFFE04B7A), Color(0xFF0A6A86), Color(0xFFB45309), Color(0xFF6D28D9))

/**
 * The Phone card, opened by the dock's Phone button: up to four favourite people as big round
 * buttons (one tap calls), "Add" from the contacts, and "Open phone app" for everything else.
 */
@Composable
fun PhoneCardSheet(app: DriveApp, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val (bg, fg) = dialogColors()
    val moving = isMoving(app)
    var typing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<FavouriteContact?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = FavouriteCallsStore.readPicked(context, result.data?.data)
        if (picked == null) {
            Toast.makeText(context, context.getString(R.string.fav_calls_failed), Toast.LENGTH_SHORT).show()
        } else if (!app.favouriteCalls.add(picked)) {
            Toast.makeText(context, context.getString(R.string.fav_calls_full, FavouriteCallsLogic.MAX), Toast.LENGTH_SHORT).show()
        }
    }
    val add = {
        try {
            picker.launch(FavouriteCallsStore.pickIntent())
        } catch (_: ActivityNotFoundException) {
            // Many head units have no contacts app: type the number instead (parked only).
            typing = true
        } catch (_: SecurityException) {
            typing = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Phone, contentDescription = null, tint = LocalDriveTheme.current.accentSoft, modifier = Modifier.size(30.dp))
                Text(stringResource(R.string.fav_calls_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            FavouriteCallsContent(
                contacts = app.favouriteCalls.contacts.collectAsStateWithLifecycle().value,
                canAdd = !moving,
                onCall = { c ->
                    FavouriteCallsStore.call(context, c)
                    onDismiss()
                },
                onAdd = add,
                onRemove = { removing = it },
                onOpenPhone = {
                    Launchers.openDialer(context)
                    onDismiss()
                },
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )

    removing?.let { c ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = bg, titleContentColor = fg, textContentColor = fg,
            title = { Text(c.name, fontWeight = FontWeight.Bold) },
            text = { Text(c.number) },
            confirmButton = { TextButton(onClick = { app.favouriteCalls.remove(c); removing = null }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (typing && !moving) {
        TypeContactDialog(
            onSave = { c ->
                if (!app.favouriteCalls.add(c)) {
                    Toast.makeText(context, context.getString(R.string.fav_calls_full, FavouriteCallsLogic.MAX), Toast.LENGTH_SHORT).show()
                }
                typing = false
            },
            onDismiss = { typing = false },
        )
    }
}

/** The four buttons and "Open phone app"; also used by the screen previews. */
@Composable
internal fun FavouriteCallsContent(
    contacts: List<FavouriteContact>,
    canAdd: Boolean,
    onCall: (FavouriteContact) -> Unit,
    onAdd: () -> Unit,
    onRemove: (FavouriteContact) -> Unit,
    onOpenPhone: () -> Unit,
) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
        Text(
            stringResource(if (contacts.isEmpty()) R.string.fav_calls_none else R.string.fav_calls_hint, FavouriteCallsLogic.MAX),
            color = theme.muted,
            fontSize = dims.t(2.4f, min = 14f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            contacts.forEachIndexed { i, c ->
                Avatar(c.initial, c.name, AvatarColors[i % AvatarColors.size], onClick = { onCall(c) }, onLongClick = { onRemove(c) })
            }
            if (contacts.size < FavouriteCallsLogic.MAX && canAdd) {
                Avatar(null, stringResource(R.string.fav_calls_add), theme.surfaceHigh, onClick = onAdd, onLongClick = null)
            }
        }
        BigButton(stringResource(R.string.fav_calls_open_phone), onOpenPhone, Modifier.fillMaxWidth(), icon = Icons.Filled.Phone)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Avatar(initial: String?, label: String, color: Color, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    Column(
        Modifier
            .widthIn(max = 120.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (initial == null) Modifier.border(1.dp, theme.border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (initial != null) {
                Text(initial, color = Color.White, fontSize = dims.t(4f, min = 26f), fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Add, contentDescription = null, tint = theme.text, modifier = Modifier.size(36.dp))
            }
        }
        Text(
            label,
            color = theme.text,
            fontSize = dims.t(2.4f, min = 15f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** No contacts app on the unit: type a name and number (parked only). */
@Composable
private fun TypeContactDialog(onSave: (FavouriteContact) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val (bg, fg) = dialogColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg, titleContentColor = fg, textContentColor = fg,
        title = { Text(stringResource(R.string.fav_calls_type_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text(stringResource(R.string.place_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { v -> number = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(20) },
                    label = { Text(stringResource(R.string.fav_calls_number)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (number.isNotBlank()) onSave(FavouriteContact(name, number)) else onDismiss() }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
