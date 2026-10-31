package com.vikash.ampli.drive.system

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A contact pinned on the Phone card. */
data class FavouriteContact(val name: String, val number: String) {
    /** "P" for Papa: the round button's letter. */
    val initial: String get() = name.trim().firstOrNull()?.uppercase() ?: "#"
}

object FavouriteCallsLogic {
    const val MAX = 4

    /** [list] with [c] added (same number replaces), or null when the four places are taken. */
    fun add(list: List<FavouriteContact>, c: FavouriteContact): List<FavouriteContact>? {
        val number = c.number.trim()
        if (number.isEmpty()) return list
        val key = digits(number)
        val existing = list.indexOfFirst { digits(it.number) == key }
        val clean = FavouriteContact(c.name.trim().ifEmpty { number }.take(30), number)
        if (existing >= 0) return list.toMutableList().also { it[existing] = clean }
        if (list.size >= MAX) return null
        return list + clean
    }

    /** Last 10 digits, so "+91 98123 45678" and "098123 45678" are the same person. */
    fun digits(number: String): String = number.filter { it.isDigit() }.takeLast(10)

    fun toJson(list: List<FavouriteContact>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("n", it.name).put("p", it.number)) }
        return arr.toString()
    }

    fun fromJson(raw: String?): List<FavouriteContact> = try {
        val arr = JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { o -> o.optString("p").takeIf { it.isNotBlank() }?.let { FavouriteContact(o.optString("n"), it) } }
        }.take(MAX)
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Up to four favourite contacts, picked with the system contact picker (no READ_CONTACTS: the
 * picker grants access to the one row picked). Kept on this unit only.
 */
class FavouriteCallsStore(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _contacts = MutableStateFlow(FavouriteCallsLogic.fromJson(sp.getString(KEY, null)))
    val contacts: StateFlow<List<FavouriteContact>> = _contacts.asStateFlow()

    /** False when four are already pinned. */
    fun add(c: FavouriteContact): Boolean {
        val next = FavouriteCallsLogic.add(_contacts.value, c) ?: return false
        write(next)
        return true
    }

    fun remove(c: FavouriteContact) = write(_contacts.value.filterNot { it == c })

    private fun write(list: List<FavouriteContact>) {
        sp.edit().putString(KEY, FavouriteCallsLogic.toJson(list)).apply()
        _contacts.value = list
    }

    companion object {
        private const val FILE = "fav_calls"
        private const val KEY = "contacts"

        /** The system picker for one phone number (ACTION_PICK on Phone.CONTENT_URI). */
        fun pickIntent(): Intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

        /** Name and number of the row the picker returned, or null. */
        fun readPicked(context: Context, uri: Uri?): FavouriteContact? {
            if (uri == null) return null
            return try {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null,
                )?.use { c ->
                    if (!c.moveToFirst()) return null
                    val number = c.getString(1)?.trim().orEmpty()
                    if (number.isEmpty()) null else FavouriteContact(c.getString(0).orEmpty(), number)
                }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Places the call straight away when the app already holds CALL_PHONE; otherwise the
         * number opens in the dialer (the head unit's BT phone app, see AppResolver.dial) and the
         * driver presses Call there.
         */
        fun call(context: Context, c: FavouriteContact) {
            if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    return
                } catch (_: Exception) {
                }
            }
            AppResolver.dial(context, c.number)
        }
    }
}
