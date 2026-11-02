package com.vikash.ampli.drive.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last [MAX] voice events (wake, Ampli speaking, listen start / stop, the microphone, what the
 * recogniser returned and how sure it was, whether the gate kept it, the parsed intent, errors),
 * shown in Settings › Voice › Check voice with Copy and Share, so a driver can send exactly what
 * happened on a unit we do not have. In memory only (gone on restart); plain Kotlin, unit-tested.
 */
object VoiceDebugLog {
    const val MAX = 40

    data class Entry(val atMs: Long, val text: String)

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private val _flow = MutableStateFlow<List<Entry>>(emptyList())
    val flow: StateFlow<List<Entry>> = _flow.asStateFlow()

    /** Clock, replaceable in tests. */
    @Volatile var now: () -> Long = { System.currentTimeMillis() }

    fun add(text: String) {
        val line = text.replace('\n', ' ').take(MAX_CHARS)
        val snapshot = synchronized(lock) {
            entries.addLast(Entry(now(), line))
            while (entries.size > MAX) entries.removeFirst()
            entries.toList()
        }
        _flow.value = snapshot
        try {
            android.util.Log.i(TAG, line)
        } catch (_: Throwable) {
            // Plain JVM (unit tests): no android.util.Log.
        }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        _flow.value = emptyList()
    }

    fun entries(): List<Entry> = synchronized(lock) { entries.toList() }

    /** The log as plain text, oldest first, one "HH:mm:ss.SSS text" line per event. */
    fun text(list: List<Entry> = entries()): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)
        return list.joinToString("\n") { fmt.format(Date(it.atMs)) + "  " + it.text }
    }

    private const val TAG = "AmpliVoice"
    private const val MAX_CHARS = 300
}
