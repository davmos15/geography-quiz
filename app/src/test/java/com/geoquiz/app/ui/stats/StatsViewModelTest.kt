package com.geoquiz.app.ui.stats

import app.cash.turbine.test
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.TestGameModes
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Task 9.2: Stats' By Mode rows, classic-only Highest Score and Reset Statistics. */
class StatsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val countsByMode = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val history = mockk<QuizHistoryRepository>(relaxed = true) {
        every { totalQuizzesCompleted } returns flowOf(0)
        every { totalCorrectAnswers } returns flowOf(0)
        every { totalIncorrectGuesses } returns flowOf(0)
        every { totalPerfectQuizzes } returns flowOf(0)
        every { totalQuestionsAnswered } returns flowOf(0)
        every { totalTimeSpent } returns flowOf(0)
        every { uniqueQuizzesCompleted } returns flowOf(0)
        every { highestClassicScore } returns flowOf(42.0)
        every { averageAccuracy } returns flowOf(0.0)
        every { quizCountsByMode } returns countsByMode
    }
    private val achievements = mockk<AchievementRepository> {
        every { unlockedAchievements } returns flowOf(emptySet())
    }
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private val registry = TestGameModes.registry(
        extraModes = setOf(
            TestGameModes.silhouettes(),
            TestGameModes.flaggedMode(BorderHopGameMode.MODE_ID, featureFlag = null, sortOrder = 12),
            DailyChallengeGameMode()
        )
    )

    private fun viewModel() = StatsViewModel(history, achievements, playGames, registry)

    @Test
    fun `with no history By Mode shows the three classic modes at zero`() = runTest {
        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(
                listOf(
                    ModeQuizCount("countries", R.string.mode_countries_name, 0),
                    ModeQuizCount("capitals", R.string.mode_capitals_name, 0),
                    ModeQuizCount("flags", R.string.mode_flags_name, 0)
                ),
                state.modeCounts
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `By Mode lists every mode with history, classic first then registry order`() = runTest {
        countsByMode.value = mapOf(
            DailyChallengeGameMode.MODE_ID to 4,
            "flags" to 2,
            SilhouettesGameMode.MODE_ID to 3,
            BorderHopGameMode.MODE_ID to 1,
            // No longer registered: left out (still counted in the totals).
            "retired_mode" to 9
        )

        viewModel().uiState.test {
            val state = expectMostRecentItem()
            assertEquals(
                listOf("countries", "capitals", "flags", SilhouettesGameMode.MODE_ID, BorderHopGameMode.MODE_ID, DailyChallengeGameMode.MODE_ID),
                state.modeCounts.map { it.modeId }
            )
            assertEquals(listOf(0, 0, 2, 3, 1, 4), state.modeCounts.map { it.count })
            assertEquals(SilhouettesGameMode.SPEC.labels.name, state.modeCounts[3].nameRes)
            assertEquals(DailyChallengeGameMode.SPEC.labels.name, state.modeCounts[5].nameRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `By Mode updates live as history changes`() = runTest {
        viewModel().uiState.test {
            assertEquals(3, expectMostRecentItem().modeCounts.size)
            countsByMode.value = mapOf(SilhouettesGameMode.MODE_ID to 1)
            assertEquals(4, awaitItem().modeCounts.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Highest Score comes from the classic-only maximum`() = runTest {
        viewModel().uiState.test {
            assertEquals(42.0, expectMostRecentItem().highestScore, 0.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Reset Statistics keeps the daily challenge's history`() = runTest {
        viewModel().resetStatistics()

        coVerify(exactly = 1) { history.clearHistoryExceptMode(DailyChallengeGameMode.MODE_ID) }
        coVerify(exactly = 0) { history.clearAllHistory() }
    }
}
