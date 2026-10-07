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

    /**
     * Shows the preloaded interstitial, if there is one and ads are not removed.
     *
     * [onShown] runs only when the ad actually appeared on screen (so callers can count it
     * against a frequency cap). [onDismissed] runs once the ad was closed, failed to show or
     * was skipped (no ad loaded, ads removed).
     */
    fun showInterstitial(
        activity: Activity,
        onShown: () -> Unit = {},
        onDismissed: () -> Unit = {}
    ) {
        if (billingRepository.adsRemoved.value) {
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            onDismissed()
            return
        }
        // An ad object can only be shown once.
        interstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                onShown()
            }

            override fun onAdDismissedFullScreenContent() {
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                onDismissed()
            }
        }

        ad.show(activity)
    }
}
