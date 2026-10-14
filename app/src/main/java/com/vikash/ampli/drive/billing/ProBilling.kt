package com.vikash.ampli.drive.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
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
 * One-time "Ampli Drive Pro" purchase through Google Play Billing (unlocks the Pro themes).
 * Create a one-time in-app product with id [PRODUCT_ID] in Play Console under
 * Monetize › Products › In-app products before release.
 *
 * The result is cached in [DrivePrefs] so Pro works offline; Play is re-checked on every
 * launch, so a refund turns Pro off again. Sideloaded test builds can't reach Play Billing:
 * they use the debug-only "Test build: unlock Pro" switch in Settings instead.
 *
 * TODO(before launch): verify purchase tokens on a server (Play Developer API) rather than
 *  trusting the client, and handle PENDING purchases with a "payment pending" message.
 */
class ProBilling(context: Context, private val prefs: DrivePrefs) : PurchasesUpdatedListener {

    /** Localised price such as "₹149.00", or null until Play answers. */
    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var productDetails: ProductDetails? = null

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
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            productDetails = list.firstOrNull()
            _price.value = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
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
    }
}
