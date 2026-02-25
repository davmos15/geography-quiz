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
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val purchased = result.purchasesList.any { purchase ->
                purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (purchased) {
                _adsRemoved.value = true
                settingsRepository.setAdsRemoved(true)
                Log.d(TAG, "Purchase restored")
            }
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
            for (purchase in purchases) {
                if (purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                ) {
                    scope.launch {
                        acknowledgePurchase(purchase)
                        _adsRemoved.value = true
                        settingsRepository.setAdsRemoved(true)
                        Log.d(TAG, "Purchase successful")
                    }
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "Purchase cancelled")
        } else {
            Log.w(TAG, "Purchase failed: ${result.debugMessage}")
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = billingClient ?: return

        val params = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        val result = client.acknowledgePurchase(params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Purchase acknowledged")
        }
    }
}
