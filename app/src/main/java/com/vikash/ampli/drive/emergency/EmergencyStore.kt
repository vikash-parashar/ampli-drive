package com.vikash.ampli.drive.emergency

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class EmergencyContact(val name: String, val phone: String)

/** What the SOS message and the responder card show. Everything is optional. */
data class EmergencyProfile(
    val ownerName: String = "",
    val contacts: List<EmergencyContact> = emptyList(),
    val bloodGroup: String = "",
    val medicalNotes: String = "",
    val vehicleNumber: String = "",
    val vehicleModel: String = "",
    val insuranceNote: String = "",
) {
    val hasMedicalInfo: Boolean
        get() = bloodGroup.isNotBlank() || medicalNotes.isNotBlank() || insuranceNote.isNotBlank() ||
            vehicleNumber.isNotBlank() || vehicleModel.isNotBlank() || ownerName.isNotBlank()

    companion object {
        const val MAX_CONTACTS = 5
    }
}

/** The emergency profile, kept on this unit only (SharedPreferences, JSON). */
class EmergencyStore(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<EmergencyProfile> = _profile.asStateFlow()

    fun save(profile: EmergencyProfile) {
        val clean = profile.copy(
            contacts = profile.contacts
                .map { EmergencyContact(it.name.trim(), it.phone.trim()) }
                .filter { it.phone.isNotEmpty() }
                .take(EmergencyProfile.MAX_CONTACTS),
        )
        val o = JSONObject()
            .put("owner", clean.ownerName)
            .put("blood", clean.bloodGroup)
            .put("notes", clean.medicalNotes)
            .put("vehicle", clean.vehicleNumber)
            .put("model", clean.vehicleModel)
            .put("insurance", clean.insuranceNote)
            .put(
                "contacts",
                JSONArray().apply {
                    clean.contacts.forEach { put(JSONObject().put("name", it.name).put("phone", it.phone)) }
                },
            )
        sp.edit().putString(KEY, o.toString()).apply()
        _profile.value = clean
    }

    private fun load(): EmergencyProfile = try {
        val o = JSONObject(sp.getString(KEY, "{}") ?: "{}")
        val arr = o.optJSONArray("contacts") ?: JSONArray()
        EmergencyProfile(
            ownerName = o.optString("owner"),
            bloodGroup = o.optString("blood"),
            medicalNotes = o.optString("notes"),
            vehicleNumber = o.optString("vehicle"),
            vehicleModel = o.optString("model"),
            insuranceNote = o.optString("insurance"),
            contacts = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { EmergencyContact(it.optString("name"), it.optString("phone")) }
            },
        )
    } catch (_: Exception) {
        EmergencyProfile()
    }

    private companion object {
        const val FILE = "emergency"
        const val KEY = "profile"
    }
}
