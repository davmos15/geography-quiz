package com.geoquiz.app.data.service

import android.app.Activity
import com.google.android.gms.ads.interstitial.InterstitialAd
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdManagerTest {

    private class FakeLoader : InterstitialAdLoader {
        var loadCount = 0
        override fun load(onLoaded: (InterstitialAd) -> Unit, onFailed: () -> Unit) {
            loadCount++
        }
    }

    private val activity = mockk<Activity>(relaxed = true)
    private val adsRemovedFlow = MutableStateFlow(false)
    private val billingRepository = mockk<BillingRepository> {
        every { adsRemoved } returns adsRemovedFlow
    }
    private val loader = FakeLoader()

    /** Runs AdManager's consent watcher eagerly and cancels it when the test ends. */
    private fun TestScope.adScope() =
        CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `no interstitial is loaded before consent allows ads`() = runTest {
        val gateway = FakeConsentGateway(canRequest = false)
        val consentManager = ConsentManager(gateway)
        val adManager = AdManager(billingRepository, consentManager, loader, adScope())

        adManager.preloadInterstitial()
        consentManager.gatherConsent(activity)
        gateway.completeConsent(error = "no consent")

        assertEquals(0, loader.loadCount)
    }

    @Test
    fun `an earlier preload request runs once consent allows ads`() = runTest {
        val gateway = FakeConsentGateway(canRequest = false)
        val consentManager = ConsentManager(gateway)
        val adManager = AdManager(billingRepository, consentManager, loader, adScope())

        adManager.preloadInterstitial()
        adManager.preloadInterstitial()
        assertEquals(0, loader.loadCount)

        consentManager.gatherConsent(activity)
        gateway.canRequest = true
        gateway.completeConsent()

        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `no deferred preload when none was requested`() = runTest {
        val gateway = FakeConsentGateway(canRequest = false)
        val consentManager = ConsentManager(gateway)
        AdManager(billingRepository, consentManager, loader, adScope())

        consentManager.gatherConsent(activity)
        gateway.canRequest = true
        gateway.completeConsent()

        assertEquals(0, loader.loadCount)
    }

    @Test
    fun `preload loads straight away when consent is already granted`() = runTest {
        val consentManager = ConsentManager(FakeConsentGateway(canRequest = true))
        consentManager.gatherConsent(activity)
        val adManager = AdManager(billingRepository, consentManager, loader, adScope())

        adManager.preloadInterstitial()
        // A second call while the first is still loading does not start another request
        adManager.preloadInterstitial()

        assertEquals(1, loader.loadCount)
    }

    @Test
    fun `no preload when ads are removed, even with consent`() = runTest {
        adsRemovedFlow.value = true
        val consentManager = ConsentManager(FakeConsentGateway(canRequest = true))
        consentManager.gatherConsent(activity)
        val adManager = AdManager(billingRepository, consentManager, loader, adScope())

        adManager.preloadInterstitial()

        assertEquals(0, loader.loadCount)
    }

    @Test
    fun `showInterstitial without a loaded ad dismisses immediately`() = runTest {
        val consentManager = ConsentManager(FakeConsentGateway(canRequest = false))
        val adManager = AdManager(billingRepository, consentManager, loader, adScope())
        var dismissed = false

        adManager.showInterstitial(activity) { dismissed = true }

        assertTrue(dismissed)
    }
}
