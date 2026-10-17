package com.vikash.ampli.drive.messages

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Settings › Read WhatsApp messages aloud (Pro). Off by default; it can only be switched on after
 * the driver agreed to the disclosure. Its own small file, so it never touches the main prefs.
 */
class MessagePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _disclosure = MutableStateFlow(sp.getBoolean(KEY_DISCLOSURE, false))
    val disclosureAccepted: StateFlow<Boolean> = _disclosure.asStateFlow()

    private val _groups = MutableStateFlow(sp.getBoolean(KEY_GROUPS, false))
    val allowGroups: StateFlow<Boolean> = _groups.asStateFlow()

    /** Switching on without the disclosure is refused (returns false). */
    fun setEnabled(value: Boolean): Boolean {
        if (value && !_disclosure.value) return false
        _enabled.value = value
        sp.edit().putBoolean(KEY_ENABLED, value).apply()
        return true
    }

    fun setDisclosureAccepted(value: Boolean) {
        _disclosure.value = value
        sp.edit().putBoolean(KEY_DISCLOSURE, value).apply()
        if (!value) setEnabled(false)
    }

    fun setAllowGroups(value: Boolean) {
        _groups.value = value
        sp.edit().putBoolean(KEY_GROUPS, value).apply()
    }

    private companion object {
        const val FILE = "ampli_messages"
        const val KEY_ENABLED = "read_whatsapp"
        const val KEY_DISCLOSURE = "read_whatsapp_disclosure"
        const val KEY_GROUPS = "read_whatsapp_groups"
    }
}
