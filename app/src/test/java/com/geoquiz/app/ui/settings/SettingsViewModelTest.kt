package com.geoquiz.app.ui.settings

import android.app.Activity
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.service.BillingRepository
import com.geoquiz.app.data.service.ConsentManager
import com.geoquiz.app.data.service.FakeConsentGateway
import com.geoquiz.app.domain.usecase.ResetAllDataUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val activity = mockk<Activity>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val billingRepository = mockk<BillingRepository>(relaxed = true) {
        every { adsRemoved } returns MutableStateFlow(false)
        every { price } returns MutableStateFlow(null)
    }
    private val resetAllData = mockk<ResetAllDataUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(gateway: FakeConsentGateway) = SettingsViewModel(
        settingsRepository = settingsRepository,
        billingRepository = billingRepository,
        consentManager = ConsentManager(gateway),
        resetAllData = resetAllData
    )

    @Test
    fun `privacy options are hidden until consent says they are required`() {
        val gateway = FakeConsentGateway(canRequest = true, privacyRequired = false)
        val consentManager = ConsentManager(gateway)
        val viewModel = SettingsViewModel(settingsRepository, billingRepository, consentManager, resetAllData)

        assertFalse(viewModel.privacyOptionsRequired.value)

        gateway.privacyRequired = true
        consentManager.gatherConsent(activity)

        assertTrue(viewModel.privacyOptionsRequired.value)
    }

    @Test
    fun `showPrivacyOptions opens the UMP privacy options form`() {
        val gateway = FakeConsentGateway(canRequest = true, privacyRequired = true)
        val viewModel = viewModel(gateway)

        viewModel.showPrivacyOptions(activity)

        // The form was requested: completing it must not throw.
        gateway.completePrivacy()
    }

    @Test
    fun `successful reset reports DONE then returns to IDLE once shown`() = runTest(dispatcher) {
        coEvery { resetAllData() } just runs
        val viewModel = viewModel(FakeConsentGateway())

        viewModel.onResetAllData()
        assertEquals(ResetStatus.IN_PROGRESS, viewModel.resetStatus.value)
        advanceUntilIdle()

        coVerify(exactly = 1) { resetAllData() }
        assertEquals(ResetStatus.DONE, viewModel.resetStatus.value)

        viewModel.onResetMessageShown()
        assertEquals(ResetStatus.IDLE, viewModel.resetStatus.value)
    }

    @Test
    fun `failed reset reports FAILED`() = runTest(dispatcher) {
        coEvery { resetAllData() } throws IllegalStateException("disk full")
        val viewModel = viewModel(FakeConsentGateway())

        viewModel.onResetAllData()
        advanceUntilIdle()

        assertEquals(ResetStatus.FAILED, viewModel.resetStatus.value)
    }

    @Test
    fun `a second reset request while one is running is ignored`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { resetAllData() } coAnswers { gate.await() }
        val viewModel = viewModel(FakeConsentGateway())

        viewModel.onResetAllData()
        advanceUntilIdle()
        viewModel.onResetAllData()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { resetAllData() }
        assertEquals(ResetStatus.DONE, viewModel.resetStatus.value)
    }
}
