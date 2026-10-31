package com.vikash.ampli.drive.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPricingTest {

    @Test
    fun fallbackFollowsTheLaunchOffer() {
        assertEquals("₹1,499", ProPricing.fallbackPrice(launchOffer = true))
        assertEquals("₹2,500", ProPricing.fallbackPrice(launchOffer = false))
    }

    /** The launch line is never a fake discount: flag on AND Play really charges ₹1,499. */
    @Test
    fun launchLineOnlyWhenPlayChargesTheLaunchPrice() {
        assertTrue(ProPricing.showLaunchLine(true, 1_499_000_000L, "INR"))
        assertFalse(ProPricing.showLaunchLine(false, 1_499_000_000L, "INR"))
        assertFalse(ProPricing.showLaunchLine(true, 2_500_000_000L, "INR"))
        assertFalse(ProPricing.showLaunchLine(true, 499_000_000L, "INR"))
        assertFalse(ProPricing.showLaunchLine(true, 1_499_000_000L, "USD"))
        assertFalse(ProPricing.showLaunchLine(true, null, null))
    }
}
