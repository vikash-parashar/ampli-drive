package com.vikash.ampli.drive.billing

import java.text.NumberFormat
import java.util.Locale

/**
 * Car Hub Pro (`ampli_drive_pro`) pricing as set in Play Console: ₹1,499 at launch, ₹2,500 once
 * the launch offer ends. Play's own price is always what the app shows when Play has answered;
 * these numbers are only a fallback for the buy button before then, and the "launch price"
 * line is shown only while the offer flag is on and Play really charges the launch price, so it
 * can never announce a discount that isn't there.
 */
object ProPricing {
    const val LAUNCH_PRICE_INR = 1_499
    const val REGULAR_PRICE_INR = 2_500

    /** "₹1,499" while the launch offer runs, else "₹2,500". */
    fun fallbackPrice(launchOffer: Boolean): String = rupees(if (launchOffer) LAUNCH_PRICE_INR else REGULAR_PRICE_INR)

    /** True when Play's price is exactly the launch price in rupees. */
    fun isLaunchPrice(priceMicros: Long?, currency: String?): Boolean =
        priceMicros != null && currency == "INR" && priceMicros == LAUNCH_PRICE_INR * 1_000_000L

    /** Show "Launch price ₹1,499 (later ₹2,500)" only while the flag is on and Play charges the launch price. */
    fun showLaunchLine(launchOffer: Boolean, priceMicros: Long?, currency: String?): Boolean =
        launchOffer && isLaunchPrice(priceMicros, currency)

    fun rupees(amount: Int): String = "₹" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-IN")).format(amount)
}
