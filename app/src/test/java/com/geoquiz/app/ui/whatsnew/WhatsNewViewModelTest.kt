package com.geoquiz.app.ui.whatsnew

import app.cash.turbine.test
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A fake [AppInstallInfo] for tests, set per scenario instead of faking [android.content.pm.PackageManager]. */
private class FakeAppInstallInfo(override val wasUpdated: Boolean) : AppInstallInfo

/**
 * [WhatsNewViewModel] (P10-F): show/hide logic for the one-off "What's new" screen, and the
 * feature-flag filtering of its "New ways to play" list.
 */
class WhatsNewViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settingsRepository = mockk<SettingsRepository>(relaxUnitFun = true)
    private val featureFlagRepository = mockk<FeatureFlagRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>()

    private fun allFlagsEnabled() =
        FeatureFlag.entries.map { FeatureFlagState(it, enabled = true, overridden = false) }

    private fun stub(storedVersion: Int?, totalQuizzesCompleted: Int = 0) {
        every { settingsRepository.whatsNewSeenVersion } returns flowOf(storedVersion)
        every { featureFlagRepository.states } returns flowOf(allFlagsEnabled())
        every { quizHistoryRepository.totalQuizzesCompleted } returns flowOf(totalQuizzesCompleted)
    }

    private fun viewModel(wasUpdated: Boolean) = WhatsNewViewModel(
        settingsRepository = settingsRepository,
        featureFlagRepository = featureFlagRepository,
        quizHistoryRepository = quizHistoryRepository,
        installInfo = FakeAppInstallInfo(wasUpdated)
    )

    @Test
    fun `a fresh install with no history and no update signal is never shown and records the version`() = runTest {
        stub(storedVersion = null, totalQuizzesCompleted = 0)

        val viewModel = viewModel(wasUpdated = false)

        viewModel.uiState.test {
            assertEquals(WhatsNewUiState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION) }
    }

    @Test
    fun `an upgrade detected by AppInstallInfo is shown with no stored version`() = runTest {
        stub(storedVersion = null, totalQuizzesCompleted = 0)

        val viewModel = viewModel(wasUpdated = true)

        viewModel.uiState.test {
            assertTrue(awaitItem() is WhatsNewUiState.Shown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `existing quiz history counts as an upgrade even when AppInstallInfo looks fresh`() = runTest {
        stub(storedVersion = null, totalQuizzesCompleted = 3)

        val viewModel = viewModel(wasUpdated = false)

        viewModel.uiState.test {
            assertTrue(awaitItem() is WhatsNewUiState.Shown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored version at or above the current one is never shown again`() = runTest {
        stub(storedVersion = SettingsRepository.WHATS_NEW_VERSION, totalQuizzesCompleted = 0)

        val viewModel = viewModel(wasUpdated = false)

        viewModel.uiState.test {
            assertEquals(WhatsNewUiState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a stored version below the current one is shown again`() = runTest {
        stub(storedVersion = SettingsRepository.WHATS_NEW_VERSION - 1, totalQuizzesCompleted = 0)

        val viewModel = viewModel(wasUpdated = false)

        viewModel.uiState.test {
            assertTrue(awaitItem() is WhatsNewUiState.Shown)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss hides the screen and records the current version`() = runTest {
        stub(storedVersion = null, totalQuizzesCompleted = 0)
        val viewModel = viewModel(wasUpdated = true)

        viewModel.uiState.test {
            assertTrue(awaitItem() is WhatsNewUiState.Shown)

            viewModel.onDismiss()

            assertEquals(WhatsNewUiState.Hidden, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION) }
    }

    @Test
    fun `shown sections only list modes whose flag is enabled`() = runTest {
        every { settingsRepository.whatsNewSeenVersion } returns flowOf(null)
        every { featureFlagRepository.states } returns flowOf(
            FeatureFlag.entries.map {
                FeatureFlagState(it, enabled = it == FeatureFlag.COUNTRY_SILHOUETTES, overridden = false)
            }
        )
        every { quizHistoryRepository.totalQuizzesCompleted } returns flowOf(0)

        val viewModel = viewModel(wasUpdated = true)

        viewModel.uiState.test {
            val shown = awaitItem() as WhatsNewUiState.Shown
            val newModes = shown.sections.first { it.headingRes == com.geoquiz.app.R.string.whats_new_section_new_modes }
            assertEquals(1, newModes.items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Dismiss is idempotent (task P10-R1) ----------------------------------------------------

    @Test
    fun `a second dismiss call does not write the version again`() = runTest {
        stub(storedVersion = null, totalQuizzesCompleted = 0)
        val viewModel = viewModel(wasUpdated = true)

        viewModel.uiState.test {
            assertTrue(awaitItem() is WhatsNewUiState.Shown)

            viewModel.onDismiss()
            assertEquals(WhatsNewUiState.Hidden, awaitItem())
            viewModel.onDismiss()

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { settingsRepository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION) }
    }

    @Test
    fun `onDismiss before anything is shown does nothing`() = runTest {
        stub(storedVersion = SettingsRepository.WHATS_NEW_VERSION, totalQuizzesCompleted = 0)
        val viewModel = viewModel(wasUpdated = false)

        viewModel.uiState.test {
            assertEquals(WhatsNewUiState.Hidden, awaitItem())
            viewModel.onDismiss()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { settingsRepository.setWhatsNewSeenVersion(any()) }
    }
}
