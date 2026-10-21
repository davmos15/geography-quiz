package com.geoquiz.app.ui.debug

import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** [DebugMenuViewModel.onShowWhatsNewAgain] (P10-F): the debug-only "Show What's new again" row. */
class DebugMenuViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val featureFlagRepository = mockk<FeatureFlagRepository> {
        every { states } returns flowOf(emptyList())
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxUnitFun = true)

    private fun viewModel() = DebugMenuViewModel(featureFlagRepository, settingsRepository)

    @Test
    fun `onShowWhatsNewAgain stores a version below the current one`() = runTest {
        viewModel().onShowWhatsNewAgain()

        coVerify { settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION - 1) }
    }
}
