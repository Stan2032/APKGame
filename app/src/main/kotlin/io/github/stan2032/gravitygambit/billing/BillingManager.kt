package io.github.stan2032.gravitygambit.billing

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
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
import io.github.stan2032.gravitygambit.core.Economy
import io.github.stan2032.gravitygambit.core.Iap
import io.github.stan2032.gravitygambit.data.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Play Billing Library 8 integration (v8+ is mandatory for new apps since
 * 2026-08-31; see docs/reference/play-policy-snapshot.md).
 *
 * Design constraints:
 *  - The game must be 100% functional with billing unavailable (no Play
 *    services, no configured products): the shop then shows earnable items only.
 *  - Purchases are cosmetics/stardust only — no gameplay power. Grants are
 *    applied through [ProfileRepository] and consumed/acknowledged properly so
 *    Play doesn't auto-refund.
 */
class BillingManager(
    application: Application,
    private val repo: ProfileRepository,
) : PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(application)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products

    private val _subscriptions = MutableStateFlow<List<ProductDetails>>(emptyList())
    val subscriptions: StateFlow<List<ProductDetails>> = _subscriptions

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _available.value = true
                    queryProducts()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                _available.value = false
            }
        })
    }

    private fun queryProducts() {
        val inappParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Iap.ALL_PRODUCT_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }
            )
            .build()
        client.queryProductDetailsAsync(inappParams) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = detailsResult.productDetailsList
            }
        }
        val subsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                Iap.SUBSCRIPTION_IDS.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()
        client.queryProductDetailsAsync(subsParams) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _subscriptions.value = detailsResult.productDetailsList
            }
        }
    }

    fun launchPurchase(activity: Activity, details: ProductDetails) {
        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        // Subscriptions require an offer token; use the first (base) offer.
        details.subscriptionOfferDetails?.firstOrNull()?.let {
            detailsParams.setOfferToken(it.offerToken)
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsParams.build()))
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        purchases.forEach(::handlePurchase)
    }

    private fun restorePurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
        // Subscriptions: reconcile Ad Pass state in BOTH directions, so an
        // expired/refunded pass deactivates and a restored one reactivates.
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
                val hasPass = purchases.any { p ->
                    p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.any { it in Iap.SUBSCRIPTION_IDS }
                }
                repo.setAdPassActive(hasPass)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        for (productId in purchase.products) {
            if (productId in Iap.SUBSCRIPTION_IDS) {
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(ack) { }
                }
                repo.setAdPassActive(true)
                continue
            }
            if (productId == Iap.SEASON_PASS) {
                // Consumable, deliberately: the pass is bought per season, so
                // consuming it is what lets the same product be bought again
                // when the next one starts. Consume first, grant after — a
                // failed consume means Play redelivers rather than us
                // double-granting, same rule as the stardust packs.
                val consume = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.consumeAsync(consume) { r, _ ->
                    if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                        repo.grantSeasonPass(
                            java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay()
                        )
                    }
                }
                continue
            }
            val grant = Iap.CONSUMABLE_GRANTS[productId]
            if (grant != null) {
                // Consumable: consume first, then grant — a failed consume means
                // Play will redeliver the purchase, so we never double-grant.
                val consume = ConsumeParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.consumeAsync(consume) { r, _ ->
                    if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                        repo.update { it.copy(stardust = it.stardust + grant) }
                    }
                }
            } else if (productId == Iap.COSMIC_PACK) {
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(ack) { }
                }
                repo.update { p ->
                    p.copy(ownedTrails = (p.ownedTrails + Economy.TRAILS.map { it.id }).distinct())
                }
            }
        }
    }
}
