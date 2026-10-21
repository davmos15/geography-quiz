package com.geoquiz.app.ui.whatsnew

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A fake [AppInstallInfo] always reporting an upgrade, so the view model resolves to [WhatsNewUiState.Shown]. */
private class FakeUpgradeInstallInfo : AppInstallInfo {
    override val wasUpdated: Boolean = true
}

/**
 * [WhatsNewHost]'s `visible` gate (task P10-R1 review fix): `AppNavigation` passes `false`
 * whenever the current destination is not Play (e.g. a cold start over a challenge deep link),
 * so the dialog must never render even though [WhatsNewViewModel]'s own state is
 * [WhatsNewUiState.Shown].
 */
@RunWith(RobolectricTestRunner::class)
class WhatsNewHostUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun shownViewModel(): WhatsNewViewModel {
        val settingsRepository = mockk<SettingsRepository>(relaxUnitFun = true) {
            every { whatsNewSeenVersion } returns flowOf(null)
        }
        val featureFlagRepository = mockk<FeatureFlagRepository> {
            every { states } returns flowOf(
                FeatureFlag.entries.map { FeatureFlagState(it, enabled = true, overridden = false) }
            )
        }
        val quizHistoryRepository = mockk<QuizHistoryRepository> {
            every { totalQuizzesCompleted } returns flowOf(0)
        }
        return WhatsNewViewModel(
            settingsRepository = settingsRepository,
            featureFlagRepository = featureFlagRepository,
            quizHistoryRepository = quizHistoryRepository,
            installInfo = FakeUpgradeInstallInfo()
        )
    }

    private fun launch(visible: Boolean, viewModel: WhatsNewViewModel) {
        compose.setContent {
            GeographyQuizTheme {
                WhatsNewHost(visible = visible, viewModel = viewModel)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `renders nothing while visible is false, even though the view model has content to show`() {
        val viewModel = shownViewModel()
        check(viewModel.uiState.value is WhatsNewUiState.Shown)

        launch(visible = false, viewModel = viewModel)

        compose.onNodeWithText(getString(R.string.whats_new_title)).assertDoesNotExist()
    }

    @Test
    fun `renders the dialog once visible is true`() {
        val viewModel = shownViewModel()

        launch(visible = true, viewModel = viewModel)

        compose.onNodeWithText(getString(R.string.whats_new_title)).assertIsDisplayed()
    }

    private fun getString(resId: Int): String =
        ApplicationProvider.getApplicationContext<android.app.Application>().getString(resId)
}
