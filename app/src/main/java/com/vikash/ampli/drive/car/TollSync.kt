package com.vikash.ampli.drive.car

import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.TollEvent
import java.util.Locale
import kotlin.math.abs

/**
 * Copies FASTag toll debits into the expense log, once each. Pure, so it is unit-tested.
 *
 * FASTag SMS carry no transaction id the parser can rely on, so a toll is known by its time,
 * amount and plaza ([sourceId]). On top of that, a toll is skipped when the log already has:
 * - the same toll under a slightly different time (the SMS app re-posted the notification):
 *   same amount and plaza within [REPOST_WINDOW_MS];
 * - a toll the driver typed in by hand: same amount within [MANUAL_WINDOW_MS];
 * - a toll the driver deleted: its id is in the hidden set, so it never comes back.
 *
 * Running it over the whole stored FASTag history is the backfill for tolls from before this
 * version.
 */
object TollSync {
    const val PREFIX = "fastag:"
    const val REPOST_WINDOW_MS = 10L * 60 * 1000
    const val MANUAL_WINDOW_MS = 30L * 60 * 1000

    fun sourceId(e: TollEvent): String =
        PREFIX + e.time + ":" + money(e.amount ?: 0.0) + ":" + plazaKey(e.plaza)

    /** The expenses to add for [events] (FASTag history, any order). */
    fun newExpenses(existing: List<Expense>, events: List<TollEvent>, hidden: Set<String>): List<Expense> {
        val tolls = existing.filter { it.kind == ExpenseKind.Toll }.toMutableList()
        val ids = existing.mapNotNullTo(HashSet()) { it.sourceId }
        val out = ArrayList<Expense>()
        for (e in events.sortedBy { it.time }) {
            val amount = e.amount ?: continue
            if (e.kind != FastagKind.Debit || amount <= 0.0) continue
            val id = sourceId(e)
            if (id in ids || id in hidden) continue
            val plaza = plazaKey(e.plaza)
            val dup = tolls.any { t ->
                val dt = abs(t.time - e.time)
                same(t.rupees, amount) && if (t.fromFastag) {
                    dt < REPOST_WINDOW_MS && (plazaKey(t.note) == plaza || t.note.isBlank() || plaza.isEmpty())
                } else {
                    dt < MANUAL_WINDOW_MS
                }
            } || hidden.any { h -> nearHidden(h, e.time, amount, plaza) }
            if (dup) continue
            val x = Expense(e.time, ExpenseKind.Toll, amount, e.plaza ?: "", fromFastag = true, sourceId = id)
            out += x
            tolls += x
            ids += id
        }
        return out
    }

    /** A hidden toll seen again under a re-posted time still stays hidden. */
    private fun nearHidden(h: String, time: Long, amount: Double, plaza: String): Boolean {
        if (!h.startsWith(PREFIX)) return false
        val parts = h.removePrefix(PREFIX).split(':', limit = 3)
        if (parts.size < 3) return false
        val t = parts[0].toLongOrNull() ?: return false
        return abs(t - time) < REPOST_WINDOW_MS && parts[1] == money(amount) && parts[2] == plaza
    }

    private fun same(a: Double, b: Double) = abs(a - b) < 0.005

    private fun money(v: Double) = String.format(Locale.US, "%.2f", v)

    private fun plazaKey(p: String?): String = p?.lowercase(Locale.ROOT)?.filter { it.isLetterOrDigit() } ?: ""
}
