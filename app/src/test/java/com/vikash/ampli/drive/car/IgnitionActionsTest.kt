package com.vikash.ampli.drive.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnitionActionsTest {
    private fun none(@Suppress("UNUSED_PARAMETER") key: String): String? = null

    @Test
    fun fixedActions() {
        assertEquals(IgnitionEvent.On, IgnitionActions.classify("com.fyt.boot.ACCON", ::none))
        assertEquals(IgnitionEvent.Off, IgnitionActions.classify("com.fyt.boot.ACCOFF", ::none))
        assertEquals(IgnitionEvent.Off, IgnitionActions.classify("android.intent.action.ACTION_SHUTDOWN_IPO", ::none))
        assertNull(IgnitionActions.classify("android.intent.action.SCREEN_OFF", ::none))
        assertNull(IgnitionActions.classify(null, ::none))
    }

    @Test
    fun microntekStateInExtra() {
        val a = "com.microntek.acc"
        assertEquals(IgnitionEvent.Off, IgnitionActions.classify(a) { if (it == "accstate") "accoff" else null })
        assertEquals(IgnitionEvent.On, IgnitionActions.classify(a) { if (it == "accstate") "ACC_ON" else null })
        assertNull(IgnitionActions.classify(a, ::none))
        assertNull(IgnitionActions.classify(a) { "sleeping" })
    }

    @Test
    fun listIsUniqueAndHasConfirmedEntries() {
        val actions = IgnitionActions.ALL.map { it.action }
        assertEquals(actions.size, actions.toSet().size)
        assertTrue(IgnitionActions.ALL.count { it.confirmed } >= 5)
        assertTrue(IgnitionActions.ALL.all { it.event != null || it.extraKey != null })
    }
}
