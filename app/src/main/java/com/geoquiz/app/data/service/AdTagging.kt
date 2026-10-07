package com.geoquiz.app.data.service

import com.google.android.gms.ads.RequestConfiguration

/**
 * The ad tagging applied to every ad request (decision D8).
 *
 * GeoQuiz has a mixed audience that includes children (D1) and does not ask for an age, so every
 * user is treated as a child under the age of consent:
 * - child-directed treatment (TFCD) is on, which disables personalised ads and the advertising ID;
 * - under-age-of-consent treatment (TFUA) is on, so no consent-based personalisation is applied;
 * - the maximum ad content rating is G (suitable for general audiences).
 *
 * These values are applied once, globally, through [MobileAds.setRequestConfiguration] before the
 * Mobile Ads SDK is initialised, so every banner and interstitial request carries them.
 */
object AdTagging {
    const val TAG_FOR_CHILD_DIRECTED_TREATMENT: Int =
        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE

    const val TAG_FOR_UNDER_AGE_OF_CONSENT: Int =
        RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE

    const val MAX_AD_CONTENT_RATING: String = RequestConfiguration.MAX_AD_CONTENT_RATING_G

    /** UMP is also told the user is under the age of consent. */
    const val UMP_TAG_FOR_UNDER_AGE_OF_CONSENT: Boolean = true

    fun requestConfiguration(): RequestConfiguration =
        RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(TAG_FOR_CHILD_DIRECTED_TREATMENT)
            .setTagForUnderAgeOfConsent(TAG_FOR_UNDER_AGE_OF_CONSENT)
            .setMaxAdContentRating(MAX_AD_CONTENT_RATING)
            .build()
}
