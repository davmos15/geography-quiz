package com.geoquiz.app.data.service

import android.app.Activity
import app.cash.turbine.test
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentManagerTest {

    private val activity = mockk<Activity>(relaxed = true)

    @Test
    fun `canRequestAds stays false until the gateway allows ads`() = runTest {
        val gateway = FakeConsentGateway(canRequest = false)
        val manager = ConsentManager(gateway)

        manager.canRequestAds.test {
            assertFalse(awaitItem())

            manager.gatherConsent(activity)
            expectNoEvents()
            assertEquals(0, gateway.initialiseCount)

            gateway.canRequest = true
            gateway.completeConsent()

            assertTrue(awaitItem())
            assertEquals(1, gateway.initialiseCount)
        }
    }

    @Test
    fun `consent from a previous session initialises ads immediately`() {
        val gateway = FakeConsentGateway(canRequest = true)
        val manager = ConsentManager(gateway)

        manager.gatherConsent(activity)

        // Before the consent refresh has completed
        assertEquals(1, gateway.initialiseCount)
        assertTrue(manager.canRequestAds.value)

        gateway.completeConsent()
        assertEquals(1, gateway.initialiseCount)
    }

    @Test
    fun `ads are initialised only once across repeated consent gathering`() {
        val gateway = FakeConsentGateway(canRequest = true)
        val manager = ConsentManager(gateway)

        repeat(3) {
            manager.gatherConsent(activity)
            gateway.completeConsent()
        }
        manager.showPrivacyOptionsForm(activity) {}
        gateway.completePrivacy()

        assertEquals(1, gateway.initialiseCount)
    }

    @Test
    fun `privacyOptionsRequired reflects the gateway`() {
        val gateway = FakeConsentGateway(privacyRequired = false)
        val manager = ConsentManager(gateway)
        assertFalse(manager.privacyOptionsRequired.value)

        manager.gatherConsent(activity)
        assertFalse(manager.privacyOptionsRequired.value)

        gateway.privacyRequired = true
        gateway.completeConsent()
        assertTrue(manager.privacyOptionsRequired.value)

        gateway.privacyRequired = false
        manager.showPrivacyOptionsForm(activity) {}
        gateway.completePrivacy()
        assertFalse(manager.privacyOptionsRequired.value)
    }

    @Test
    fun `consent error without consent leaves ads off and the app usable`() {
        val gateway = FakeConsentGateway(canRequest = false)
        val manager = ConsentManager(gateway)

        manager.gatherConsent(activity)
        gateway.completeConsent(error = "network unavailable")

        assertFalse(manager.canRequestAds.value)
        assertEquals(0, gateway.initialiseCount)
    }

    @Test
    fun `consent error still initialises ads when earlier consent applies`() {
        val gateway = FakeConsentGateway(canRequest = false)
        val manager = ConsentManager(gateway)

        manager.gatherConsent(activity)
        gateway.canRequest = true
        gateway.completeConsent(error = "form failed to load")

        assertTrue(manager.canRequestAds.value)
        assertEquals(1, gateway.initialiseCount)
    }

    @Test
    fun `privacy options dismissal callback always runs, even on error`() {
        val gateway = FakeConsentGateway()
        val manager = ConsentManager(gateway)
        var dismissed = false

        manager.showPrivacyOptionsForm(activity) { dismissed = true }
        gateway.completePrivacy(error = "form unavailable")

        assertTrue(dismissed)
    }
}

/** Records calls and lets tests complete the UMP callbacks by hand. */
internal class FakeConsentGateway(
    var canRequest: Boolean = false,
    var privacyRequired: Boolean = false
) : ConsentGateway {
    var initialiseCount = 0
    private var pendingConsent: ((String?) -> Unit)? = null
    private var pendingPrivacy: ((String?) -> Unit)? = null

    override fun canRequestAds() = canRequest
    override fun isPrivacyOptionsRequired() = privacyRequired

    override fun requestConsent(activity: Activity, onComplete: (String?) -> Unit) {
        pendingConsent = onComplete
    }

    override fun showPrivacyOptionsForm(activity: Activity, onComplete: (String?) -> Unit) {
        pendingPrivacy = onComplete
    }

    override fun initialiseMobileAds() {
        initialiseCount++
    }

    fun completeConsent(error: String? = null) = checkNotNull(pendingConsent).invoke(error)
    fun completePrivacy(error: String? = null) = checkNotNull(pendingPrivacy).invoke(error)
}
