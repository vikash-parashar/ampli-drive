package com.vikash.ampli.drive.car

import com.vikash.ampli.drive.fastag.FastagKind
import com.vikash.ampli.drive.fastag.TollEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TollSyncTest {
    private val min = 60_000L
    private fun toll(t: Long, amount: Double = 120.0, plaza: String? = "Khed Shivapur", kind: FastagKind = FastagKind.Debit) =
        TollEvent(t, kind, amount, plaza, 500.0, "MH12AB1234", "ICICI")

    @Test
    fun backfillCopiesDebitsOnly() {
        val events = listOf(toll(1_000 * min), toll(2_000 * min, 65.0, "Vashi"), toll(3_000 * min, 500.0, null, FastagKind.Recharge))
        val add = TollSync.newExpenses(emptyList(), events, emptySet())
        assertEquals(2, add.size)
        assertTrue(add.all { it.kind == ExpenseKind.Toll && it.fromFastag && it.sourceId != null })
        assertEquals("Khed Shivapur", add.first().note)
    }

    @Test
    fun runningTwiceAddsNothing() {
        val events = listOf(toll(1_000 * min))
        val first = TollSync.newExpenses(emptyList(), events, emptySet())
        assertTrue(TollSync.newExpenses(first, events, emptySet()).isEmpty())
    }

    @Test
    fun repostedSmsIsADuplicate() {
        val existing = TollSync.newExpenses(emptyList(), listOf(toll(1_000 * min)), emptySet())
        assertTrue(TollSync.newExpenses(existing, listOf(toll(1_003 * min)), emptySet()).isEmpty())
        // Same plaza and amount, hours later: a new crossing.
        assertEquals(1, TollSync.newExpenses(existing, listOf(toll(1_300 * min)), emptySet()).size)
    }

    @Test
    fun manualTollWithSameAmountIsNotDoubled() {
        val manual = listOf(Expense(1_010 * min, ExpenseKind.Toll, 120.0, "typed"))
        assertTrue(TollSync.newExpenses(manual, listOf(toll(1_000 * min)), emptySet()).isEmpty())
        assertEquals(1, TollSync.newExpenses(manual, listOf(toll(1_000 * min, 95.0)), emptySet()).size)
    }

    @Test
    fun hiddenTollsStayHidden() {
        val e = toll(1_000 * min)
        val hidden = setOf(TollSync.sourceId(e))
        assertTrue(TollSync.newExpenses(emptyList(), listOf(e), hidden).isEmpty())
        // Re-posted a few minutes later: still hidden.
        assertTrue(TollSync.newExpenses(emptyList(), listOf(toll(1_004 * min)), hidden).isEmpty())
    }

    @Test
    fun twoTollsInOneBatchAtDifferentPlazas() {
        val add = TollSync.newExpenses(emptyList(), listOf(toll(1_000 * min, 120.0, "A"), toll(1_002 * min, 120.0, "B")), emptySet())
        assertEquals(2, add.size)
    }
}
