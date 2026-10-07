package com.geoquiz.app.data.service

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gates all advertising behind Google's User Messaging Platform (UMP) consent flow.
 *
 * Decision D8: every user is treated as child-directed and under the age of consent. There is no
 * age screen, so no age data is collected. Every ad request is tagged TFCD + TFUA with a maximum
 * content rating of G (see [AdTagging]), which means no personalised ads, and the `AD_ID`
 * permission is removed from the manifest. UMP is still used so regional consent rules (e.g. the
 * EEA, UK and Switzerland) are honoured and a Privacy options entry can be offered.
 *
 * Flow: [gatherConsent] is called from `MainActivity.onCreate`. If a previous session already
 * allows ads, the Mobile Ads SDK is initialised straight away; consent is then refreshed and the
 * form is shown if required. Ads are initialised exactly once, and only when UMP allows it. A
 * consent error never blocks the app: it just leaves [canRequestAds] false (no ads) unless an
 * earlier consent still applies.
 */
@Singleton
class ConsentManager @Inject constructor(
    private val gateway: ConsentGateway
) {
    private val adsInitialised = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(false)
    /** True only once the Mobile Ads SDK has been tagged and initialised. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** True when a Privacy options entry must be shown to the user. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun gatherConsent(activity: Activity) {
        // Google's recommended pattern: consent from a previous session lets ads start
        // immediately while the consent information is refreshed in the background.
        if (gateway.canRequestAds()) initialiseAdsOnce()
        refreshState()

        gateway.requestConsent(activity) { _ ->
            // Success or error: either way, act on whatever consent UMP now reports.
            if (gateway.canRequestAds()) initialiseAdsOnce()
            refreshState()
        }
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit) {
        gateway.showPrivacyOptionsForm(activity) { _ ->
            if (gateway.canRequestAds()) initialiseAdsOnce()
            refreshState()
            onDismissed()
        }
    }

    private fun initialiseAdsOnce() {
        if (adsInitialised.compareAndSet(false, true)) {
            gateway.initialiseMobileAds()
        }
    }

    private fun refreshState() {
        _privacyOptionsRequired.value = gateway.isPrivacyOptionsRequired()
        _canRequestAds.value = adsInitialised.get() && gateway.canRequestAds()
    }
}
