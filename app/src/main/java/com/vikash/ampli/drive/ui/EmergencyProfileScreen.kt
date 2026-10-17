package com.vikash.ampli.drive.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vikash.ampli.drive.DriveApp
import com.vikash.ampli.drive.R
import com.vikash.ampli.drive.emergency.EmergencyContact
import com.vikash.ampli.drive.emergency.EmergencyProfile
import com.vikash.ampli.drive.ui.theme.LocalDriveTheme

private val BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

/**
 * Settings › Emergency profile. Edits a draft and saves it when the screen closes (Back, Home)
 * or on Save. Contacts are typed in, or picked with the system contact picker, which hands
 * back the one chosen number without READ_CONTACTS.
 */
@Composable
fun EmergencyProfileScreen(app: DriveApp, onBack: () -> Unit) {
    val dims = LocalDims.current
    val theme = LocalDriveTheme.current
    val context = LocalContext.current
    val saved = remember { app.emergency.profile.value }

    var owner by remember { mutableStateOf(saved.ownerName) }
    val contacts = remember { mutableStateListOf<EmergencyContact>().apply { addAll(saved.contacts) } }
    var blood by remember { mutableStateOf(saved.bloodGroup) }
    var notes by remember { mutableStateOf(saved.medicalNotes) }
    var vehicle by remember { mutableStateOf(saved.vehicleNumber) }
    var model by remember { mutableStateOf(saved.vehicleModel) }
    var insurance by remember { mutableStateOf(saved.insuranceNote) }

    val draft = EmergencyProfile(
        ownerName = owner.trim(),
        contacts = contacts.toList(),
        bloodGroup = blood,
        medicalNotes = notes.trim(),
        vehicleNumber = vehicle.trim(),
        vehicleModel = model.trim(),
        insuranceNote = insurance.trim(),
    )
    val latestDraft by rememberUpdatedState(draft)
    DisposableEffect(Unit) {
        onDispose { app.emergency.save(latestDraft) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst() && contacts.size < EmergencyProfile.MAX_CONTACTS) {
                    contacts += EmergencyContact(c.getString(0) ?: "", c.getString(1) ?: "")
                }
            }
        } catch (_: Exception) {
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(dims.d(2.4f)),
        verticalArrangement = Arrangement.spacedBy(dims.d(2f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                ScreenHeader(title = stringResource(R.string.em_title), hint = stringResource(R.string.em_hint), onBack = onBack)
            }
            BigButton(text = stringResource(R.string.em_save), onClick = onBack)
        }
        // No typing while the car moves (Play driver-distraction rules).
        ParkToEdit(app, Modifier.fillMaxWidth().weight(1f)) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dims.d(1.6f)),
        ) {
            // What SOS can and can't do, said plainly.
            SettingCard {
                Text(stringResource(R.string.sos_limits_note), fontSize = dims.t(2.4f, min = 14f), color = theme.warning)
            }
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    CardTitle(stringResource(R.string.help_your_contacts), stringResource(R.string.em_contacts_hint))
                    contacts.forEachIndexed { i, c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f)),
                        ) {
                            OutlinedTextField(
                                value = c.name,
                                onValueChange = { contacts[i] = c.copy(name = it) },
                                label = { Text(stringResource(R.string.em_contact_name)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = c.phone,
                                onValueChange = { contacts[i] = c.copy(phone = it) },
                                label = { Text(stringResource(R.string.em_contact_phone)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                Modifier
                                    .size(MinTouch)
                                    .clip(CircleShape)
                                    .background(theme.surfaceHigh)
                                    .clickable { contacts.removeAt(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.em_remove), tint = theme.danger)
                            }
                        }
                    }
                    if (contacts.size < EmergencyProfile.MAX_CONTACTS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.6f))) {
                            BigButton(
                                text = stringResource(R.string.em_add_contact),
                                onClick = { contacts += EmergencyContact("", "") },
                                icon = Icons.Filled.Add,
                                primary = false,
                            )
                            BigButton(
                                text = stringResource(R.string.em_pick_contact),
                                onClick = {
                                    try {
                                        picker.launch(
                                            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                                        )
                                    } catch (_: Exception) {
                                    }
                                },
                                icon = Icons.Filled.Contacts,
                                primary = false,
                            )
                        }
                    }
                }
            }
            SettingCard {
                Column(verticalArrangement = Arrangement.spacedBy(dims.d(1.2f))) {
                    CardTitle(stringResource(R.string.med_title), null)
                    ProfileField(owner, { owner = it }, stringResource(R.string.em_owner), KeyboardCapitalization.Words)
                    Text(stringResource(R.string.em_blood_group), color = theme.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1f))) {
                        for (g in BLOOD_GROUPS) {
                            Chip(g, blood == g, { blood = if (blood == g) "" else g }, Modifier.weight(1f))
                        }
                    }
                    ProfileField(notes, { notes = it }, stringResource(R.string.em_notes), KeyboardCapitalization.Sentences, singleLine = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(dims.d(1.4f))) {
                        Box(Modifier.weight(1f)) {
                            ProfileField(vehicle, { vehicle = it.uppercase() }, stringResource(R.string.em_vehicle_number), KeyboardCapitalization.Characters)
                        }
                        Box(Modifier.weight(1f)) {
                            ProfileField(model, { model = it }, stringResource(R.string.em_vehicle_model), KeyboardCapitalization.Words)
                        }
                    }
                    ProfileField(insurance, { insurance = it }, stringResource(R.string.em_insurance), KeyboardCapitalization.Sentences)
                }
            }
        }
        }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    capitalization: KeyboardCapitalization,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
