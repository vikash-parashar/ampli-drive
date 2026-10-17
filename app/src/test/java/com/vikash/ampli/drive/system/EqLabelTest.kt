package com.vikash.ampli.drive.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqLabelTest {
    @Test
    fun wholeWordsMatch() {
        assertTrue(Shell.isEqLabel("EQ"))
        assertTrue(Shell.isEqLabel("Car EQ"))
        assertTrue(Shell.isEqLabel("DSP Settings"))
        assertTrue(Shell.isEqLabel("Equalizer"))
    }

    @Test
    fun partsOfWordsDont() {
        assertFalse(Shell.isEqLabel("Sequence"))
        assertFalse(Shell.isEqLabel("Request"))
        assertFalse(Shell.isEqLabel("Maps"))
    }
}
