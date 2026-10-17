package com.vikash.ampli.drive.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.vikash.ampli.drive.prefs.DrivePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time "Ampli Car Hub Pro" purchase through Google Play Billing (unlocks the Pro themes).
 * Create a one-time in-app product with id [PRODUCT_ID] in Play Console under
 * Monetize › Products › In-app products before release.
 *
 * The result is cached in [DrivePrefs] so Pro works offline; Play is re-checked on every
 * launch, so a refund turns Pro off again. Sideloaded test builds can't reach Play Billing:
 * they use the debug-only "Test build: unlock Pro" switch in Settings instead.
 *
 * Also sells the Settings › "Buy the developer a chai" tips ([TIP_IDS]): consumable one-time
 * products, consumed right after payment so they can be given again. They unlock nothing.
 *
 * TODO(before launch): verify purchase tokens on a server (Play Developer API) rather than
 *  trusting the client, and handle PENDING purchases with a "payment pending" message.
 */
class ProBilling(context: Context, private val prefs: DrivePrefs) : PurchasesUpdatedListener {

    /** Localised price such as "₹149.00", or null until Play answers. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var productDetails: ProductDetails? = null

    /** Tip product id to its localised price; empty while Play has no tips to sell (sideloaded, not created yet). */
    private val _tipPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val tipPrices: StateFlow<Map<String, String>> = _tipPrices.asStateFlow()

    /** Goes up by one after each tip is paid and consumed, for the thank-you. */
    private val _tipsThanked = MutableStateFlow(0)
    val tipsThanked: StateFlow<Int> = _tipsThanked.asStateFlow()

    private val details = java.util.concurrent.ConcurrentHashMap<String, ProductDetails>()

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) {
            refreshPurchases()
            return
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) return
                    queryProduct()
                    refreshPurchases()
                }

                override fun onBillingServiceDisconnected() {
                    // Reconnected lazily on the next start() or purchase attempt.
                }
            })
        } catch (_: Exception) {
            // Head units without Play services: Pro stays as cached.
        }
    }

    /** Opens Google Play's payment sheet. Returns false when Play has nothing to sell (yet). */
    fun purchase(activity: Activity): Boolean {
        val details = productDetails ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Opens Google Play's payment sheet for a tip. Returns false when Play can't sell it (yet). */
    fun tip(activity: Activity, productId: String): Boolean {
        val d = details[productId] ?: run { start(); return false }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        return try {
            client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
        } catch (_: Exception) {
            false
        }
    }

    /** "Restore purchase": asks Play again which products this account owns. */
    fun restore(): Boolean {
        if (!client.isReady) {
            start()
            return false
        }
        refreshPurchases()
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach(::handlePurchase)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases()
            else -> Unit
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                (listOf(PRODUCT_ID) + TIP_IDS).map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(ProductType.INAPP)
                        .build()
                },
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            list.forEach { details[it.productId] = it }
            productDetails = details[PRODUCT_ID]
            _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
            _tipPrices.value = TIP_IDS.mapNotNull { id ->
                details[id]?.oneTimePurchaseOfferDetails?.formattedPrice?.let { id to it }
            }.toMap()
        }
    }

    private fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build(),
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            prefs.setProPurchased(purchases.any { it.ownsPro() })
            purchases.forEach(::handlePurchase)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val tip = purchase.products.firstOrNull { it in TIP_IDS }
        if (tip != null) {
            // Consumed so the same tip can be given again; also catches a tip paid just before
            // the app was closed (found again by refreshPurchases).
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.consumeAsync(params) { result, _ ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) _tipsThanked.value += 1
            }
            return
        }
        if (!purchase.ownsPro()) return
        prefs.setProPurchased(true)
        // Purchases not acknowledged within 3 days are refunded automatically by Play.
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { }
        }
    }

    private fun Purchase.ownsPro() =
        purchaseState == Purchase.PurchaseState.PURCHASED && PRODUCT_ID in products

    companion object {
        const val PRODUCT_ID = "ampli_drive_pro"

        /**
         * Consumable tips (Settings › Buy the developer a chai), the same ids in every Ampli app
         * (in-app-hook-spec v1 §5). Create them in Play Console.
         */
        const val TIP_49 = "ampli_tip_49"
        const val TIP_99 = "ampli_tip_99"
        const val TIP_199 = "ampli_tip_199"
        val TIP_IDS = listOf(TIP_49, TIP_99, TIP_199)
    }
}
