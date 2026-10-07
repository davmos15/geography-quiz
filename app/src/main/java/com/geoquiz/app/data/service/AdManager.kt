package com.geoquiz.app.data.service

import android.app.Activity
import com.geoquiz.app.di.AdsMainScope
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(
    private val billingRepository: BillingRepository,
    private val consentManager: ConsentManager,
    private val interstitialLoader: InterstitialAdLoader,
    @AdsMainScope scope: CoroutineScope
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var preloadPending = false

    init {
        // A preload asked for before consent completed runs once ads become available.
        scope.launch {
            consentManager.canRequestAds.first { it }
            if (preloadPending) {
                preloadPending = false
                preloadInterstitial()
            }
        }
    }

    fun preloadInterstitial() {
        if (billingRepository.adsRemoved.value) return
        if (!consentManager.canRequestAds.value) {
            preloadPending = true
            return
        }
        if (interstitialAd != null || isLoading) return
        isLoading = true

        interstitialLoader.load(
            onLoaded = { ad ->
                interstitialAd = ad
                isLoading = false
            },
            onFailed = {
                interstitialAd = null
                isLoading = false
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismissed: () -> Unit) {
        if (billingRepository.adsRemoved.value) {
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                interstitialAd = null
                onDismissed()
            }
        }

        ad.show(activity)
    }
}
