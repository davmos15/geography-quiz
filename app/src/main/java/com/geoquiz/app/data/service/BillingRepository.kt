package com.geoquiz.app.data.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.geoquiz.app.data.local.preferences.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingRepository"
        const val PRODUCT_ID = "remove_ads"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _adsRemoved = MutableStateFlow(false)
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()

    private val _price = MutableStateFlow<String?>(null)
    val price: StateFlow<String?> = _price.asStateFlow()

    private var billingClient: BillingClient? = null
    private var productDetails: com.android.billingclient.api.ProductDetails? = null

    init {
        scope.launch {
            settingsRepository.adsRemoved.collect { removed ->
                _adsRemoved.value = removed
            }
        }
    }

    fun connect() {
        if (billingClient?.isReady == true) return

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing connected")
                    scope.launch {
                        queryProductDetails()
                        restorePurchases()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    private suspend fun queryProductDetails() {
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        val result: ProductDetailsResult = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            productDetails = result.productDetailsList?.firstOrNull()
            _price.value = productDetails
                ?.oneTimePurchaseOfferDetails
                ?.formattedPrice
            Log.d(TAG, "Product details loaded, price: ${_price.value}")
        }
    }

    suspend fun restorePurchases() {
        // Not connected yet (e.g. onResume during startup): onBillingSetupFinished restores once connected
        val client = billingClient?.takeIf { it.isReady } ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // Also acknowledges purchases that completed while pending or whose
            // earlier acknowledgement failed; unacknowledged purchases are refunded after 3 days
            result.purchasesList.forEach { handlePurchase(it) }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            Log.w(TAG, "Product details not loaded yet")
            return
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()

        billingClient?.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                purchases.forEach { handlePurchase(it) }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            scope.launch { restorePurchases() }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Purchase cancelled")
        } else {
            Log.w(TAG, "Purchase failed: ${result.debugMessage}")
        }
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchaseActionFor(purchase)) {
            PurchaseAction.ACKNOWLEDGE_AND_GRANT -> {
                acknowledgePurchase(purchase)
                grantAdsRemoved()
            }
            PurchaseAction.GRANT -> grantAdsRemoved()
            // Granted once it completes, via onPurchasesUpdated or restorePurchases()
            PurchaseAction.PENDING -> Log.d(TAG, "Purchase pending")
            PurchaseAction.IGNORE -> Unit
        }
    }

    private suspend fun grantAdsRemoved() {
        _adsRemoved.value = true
        settingsRepository.setAdsRemoved(true)
        Log.d(TAG, "Ads removed")
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return

        val params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val result = client.acknowledgePurchase(params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Purchase acknowledged")
        } else {
            Log.w(TAG, "Acknowledge failed, will retry on next restore: ${result.debugMessage}")
        }
    }
}

internal enum class PurchaseAction { ACKNOWLEDGE_AND_GRANT, GRANT, PENDING, IGNORE }

internal fun purchaseActionFor(purchase: Purchase): PurchaseAction {
    if (!purchase.products.contains(BillingRepository.PRODUCT_ID)) return PurchaseAction.IGNORE
    return when (purchase.purchaseState) {
        Purchase.PurchaseState.PURCHASED ->
            if (purchase.isAcknowledged) PurchaseAction.GRANT else PurchaseAction.ACKNOWLEDGE_AND_GRANT
        Purchase.PurchaseState.PENDING -> PurchaseAction.PENDING
        else -> PurchaseAction.IGNORE
    }
}
