package com.vikash.ampli.drive.car

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One start signal for Settings › Diagnostics › Start log. [reason] says why nothing was said. */
data class StartEntry(val atMs: Long, val trigger: String, val greeted: Boolean, val reason: String)

/** Keeping, writing and reading the start log. Plain Kotlin, unit-tested. */
object StartLogRules {
    const val MAX = 10

    /** Newest first, at most [MAX]. */
    fun add(list: List<StartEntry>, entry: StartEntry): List<StartEntry> = (listOf(entry) + list).take(MAX)

    private fun clean(s: String) = s.replace('\n', ' ').replace('|', '/').take(120)

    fun encode(list: List<StartEntry>): String =
        list.take(MAX).joinToString("\n") { "${it.atMs}|${clean(it.trigger)}|${if (it.greeted) 1 else 0}|${clean(it.reason)}" }

    fun decode(text: String?): List<StartEntry> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 4) return@mapNotNull null
            val at = p[0].toLongOrNull() ?: return@mapNotNull null
            StartEntry(at, p[1], p[2] == "1", p.subList(3, p.size).joinToString("/"))
        }.take(MAX)
    }
}

/** The last [StartLogRules.MAX] start signals, kept across reboots (that is the point). */
class StartLog(context: Context) {
    private val sp = context.getSharedPreferences("start_log", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(StartLogRules.decode(sp.getString(KEY, null)))
    val entries: StateFlow<List<StartEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(trigger: String, greeted: Boolean, reason: String = "") {
        val next = StartLogRules.add(_entries.value, StartEntry(System.currentTimeMillis(), trigger, greeted, reason))
        // commit(): a power cut right after must not lose the line.
        sp.edit().putString(KEY, StartLogRules.encode(next)).commit()
        _entries.value = next
    }

    private companion object {
        const val KEY = "entries"
    }
}
