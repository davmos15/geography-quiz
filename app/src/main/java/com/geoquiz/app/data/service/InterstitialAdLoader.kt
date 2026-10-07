package com.geoquiz.app.data.service

import android.content.Context
import com.geoquiz.app.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Loads an interstitial ad. Wrapped so [AdManager]'s gating can be unit-tested. */
interface InterstitialAdLoader {
    fun load(onLoaded: (InterstitialAd) -> Unit, onFailed: () -> Unit)
}

class GoogleInterstitialAdLoader @Inject constructor(
    @ApplicationContext private val context: Context
) : InterstitialAdLoader {

    override fun load(onLoaded: (InterstitialAd) -> Unit, onFailed: () -> Unit) {
        // Child-directed tagging (AdTagging) is applied globally via RequestConfiguration.
        InterstitialAd.load(
            context,
            context.getString(R.string.admob_interstitial_id),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = onLoaded(ad)
                override fun onAdFailedToLoad(error: LoadAdError) = onFailed()
            }
        )
    }
}
