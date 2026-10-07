package com.geoquiz.app.data.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.geoquiz.app.di.ApplicationScope
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over the Google UMP and Mobile Ads SDKs so [ConsentManager]'s state and
 * once-only logic can be unit-tested with a fake.
 *
 * Callbacks are delivered on the main thread (UMP's own behaviour). An error is reported as a
 * non-null message; it never blocks the app.
 */
interface ConsentGateway {
    /** Whether consent obtained so far (this or a previous session) allows ad requests. */
    fun canRequestAds(): Boolean

    /** Whether a privacy options entry point must be shown (e.g. in Settings). */
    fun isPrivacyOptionsRequired(): Boolean

    /** Refreshes consent information, then shows the consent form if one is required. */
    fun requestConsent(activity: Activity, onComplete: (error: String?) -> Unit)

    /** Shows the privacy options form so the user can review or change their choices. */
    fun showPrivacyOptionsForm(activity: Activity, onComplete: (error: String?) -> Unit)

    /** Applies [AdTagging] globally, then initialises the Mobile Ads SDK. */
    fun initialiseMobileAds()
}

@Singleton
class GoogleConsentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ConsentGateway {

    private val consentInformation: ConsentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(context)
    }

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    override fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun requestConsent(activity: Activity, onComplete: (error: String?) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(AdTagging.UMP_TAG_FOR_UNDER_AGE_OF_CONSENT)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    onComplete(formError?.message)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update: ${requestError.message}")
                onComplete(requestError.message)
            }
        )
    }

    override fun showPrivacyOptionsForm(activity: Activity, onComplete: (error: String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) Log.w(TAG, "Privacy options form: ${formError.message}")
            onComplete(formError?.message)
        }
    }

    override fun initialiseMobileAds() {
        // Set synchronously so the tagging is in place before any request can be made.
        MobileAds.setRequestConfiguration(AdTagging.requestConfiguration())
        // Google recommends initialising off the main thread.
        applicationScope.launch {
            MobileAds.initialize(context) {}
        }
    }

    private companion object {
        const val TAG = "ConsentGateway"
    }
}
