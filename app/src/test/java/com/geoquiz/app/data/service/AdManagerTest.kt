package com.geoquiz.app.data.service

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        var shown = false

        adManager.showInterstitial(activity, onShown = { shown = true }, onDismissed = { dismissed = true })

        assertTrue(dismissed)
        assertFalse(shown)
    }

    /** AdManager with consent granted and one interstitial loaded. */
    private fun TestScope.adManagerWithLoadedAd(ad: InterstitialAd): AdManager {
        val consentManager = ConsentManager(FakeConsentGateway(canRequest = true))
        consentManager.gatherConsent(activity)
        val loader = object : InterstitialAdLoader {
            override fun load(onLoaded: (InterstitialAd) -> Unit, onFailed: () -> Unit) = onLoaded(ad)
        }
        return AdManager(billingRepository, consentManager, loader, adScope()).also { it.preloadInterstitial() }
    }

    @Test
    fun `onShown runs only once the ad is actually on screen`() = runTest {
        val callback = slot<FullScreenContentCallback>()
        val ad = mockk<InterstitialAd>(relaxed = true) {
            every { fullScreenContentCallback = capture(callback) } just Runs
        }
        val adManager = adManagerWithLoadedAd(ad)
        var shown = 0
        var dismissed = 0

        adManager.showInterstitial(activity, onShown = { shown++ }, onDismissed = { dismissed++ })

        verify(exactly = 1) { ad.show(activity) }
        assertEquals(0, shown)
        callback.captured.onAdShowedFullScreenContent()
        assertEquals(1, shown)
        callback.captured.onAdDismissedFullScreenContent()
        assertEquals(1, dismissed)
    }

    @Test
    fun `an ad that fails to show never reports onShown`() = runTest {
        val callback = slot<FullScreenContentCallback>()
        val ad = mockk<InterstitialAd>(relaxed = true) {
            every { fullScreenContentCallback = capture(callback) } just Runs
        }
        val adManager = adManagerWithLoadedAd(ad)
        var shown = false
        var dismissed = false

        adManager.showInterstitial(activity, onShown = { shown = true }, onDismissed = { dismissed = true })
        callback.captured.onAdFailedToShowFullScreenContent(mockk<AdError>(relaxed = true))

        assertFalse(shown)
        assertTrue(dismissed)
    }

    @Test
    fun `a loaded ad is shown only once`() = runTest {
        val ad = mockk<InterstitialAd>(relaxed = true)
        val adManager = adManagerWithLoadedAd(ad)
        var dismissed = 0

        adManager.showInterstitial(activity)
        adManager.showInterstitial(activity, onDismissed = { dismissed++ })

        verify(exactly = 1) { ad.show(activity) }
        assertEquals(1, dismissed)
    }

    @Test
    fun `no interstitial when ads are removed, even if one is loaded`() = runTest {
        val ad = mockk<InterstitialAd>(relaxed = true)
        val adManager = adManagerWithLoadedAd(ad)
        adsRemovedFlow.value = true
        var shown = false
        var dismissed = false

        adManager.showInterstitial(activity, onShown = { shown = true }, onDismissed = { dismissed = true })

        verify(exactly = 0) { ad.show(any()) }
        assertFalse(shown)
        assertTrue(dismissed)
    }
}
