package com.vikash.ampli.drive.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WholePriceTest {
    @Test
    fun dropsZeroPaise() {
        assertEquals("₹49", wholePrice("₹49.00"))
        assertEquals("₹1,999", wholePrice("₹1,999.00"))
        assertEquals("49 €", wholePrice("49,00 €"))
    }

    @Test
    fun keepsRealFractions() {
        assertEquals("₹49.50", wholePrice("₹49.50"))
        assertEquals("US$0.99", wholePrice("US$0.99"))
        assertEquals("₹100", wholePrice("₹100"))
    }
}
