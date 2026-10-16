package com.geoquiz.app.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.dailychallenge.DailyChallengeGameScreen
import com.geoquiz.app.ui.dailychallenge.DailyChallengeGameViewModel
import com.geoquiz.app.ui.dailychallenge.DailyChallengeSetupScreen
import com.geoquiz.app.ui.dailychallenge.DailyChallengeSetupViewModel
import com.geoquiz.app.ui.results.ResultsScreen
import com.geoquiz.app.ui.results.ResultsViewModel
import com.geoquiz.app.ui.share.ShareCardContent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/**
 * Fixtures for [EngagementScreenshotTest] (Phase 8). Only date-independent states: the daily
 * challenge's questions are seeded from the real local date, so a question screen would change
 * every day; the Setup screen, the "already played" state, the share dialog and the card are
 * stable.
 */
object EngagementScreenshotScreens {

    fun streak(current: Int, freezes: Int, playedToday: Boolean, daysToNextFreeze: Int = 3) =
        StreakStatus(current = current, best = current, freezesHeld = freezes, daysToNextFreeze = daysToNextFreeze, playedToday = playedToday)

    /** Daily challenge Setup with [status]'s streak summary (and no Start once played today). */
    fun dailySetup(status: StreakStatus): @Composable () -> Unit {
        val observe = mockk<ObserveStreakUseCase> { every { this@mockk.invoke() } returns flowOf(status) }
        val viewModel = DailyChallengeSetupViewModel(observe)
        return { DailyChallengeSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** The game screen reached for a date that's already recorded (D54's hard guard). */
    fun dailyAlreadyPlayed(): @Composable () -> Unit {
        val history = mockk<QuizHistoryRepository> {
            coEvery { hasCompletedCategory(any(), any(), any()) } returns true
        }
        val viewModel = DailyChallengeGameViewModel(
            savedStateHandle = SavedStateHandle(),
            countryRepository = mockk<CountryRepository>(relaxed = true),
            validateAnswer = mockk<ValidateAnswerUseCase>(),
            validateCapitalAnswer = mockk<ValidateCapitalAnswerUseCase>(),
            completeQuiz = mockk<CompleteQuizUseCase>(relaxed = true),
            quizHistoryRepository = history,
            clock = MonotonicClock { 0L }
        )
        return { DailyChallengeGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** Results for the classic fixture result, with the share preview already open. */
    fun resultsShareDialog(): Pair<ResultsViewModel, @Composable () -> Unit> {
        val viewModel = ScreenTestFixtures.resultsViewModel()
        return viewModel to {
            ResultsScreen(
                onPlayAgain = { _ -> },
                onGoHome = {},
                onViewAnswers = {},
                onPractiseMissed = { _ -> },
                viewModel = viewModel
            )
        }
    }

    val CARD = ShareCardContent(
        title = "Capitals: Europe",
        date = "16 Oct 2026",
        score = "38/44",
        percentage = 86,
        percentageText = "86%",
        time = "5m 12s",
        timeSeconds = 312,
        metaLine = "16 Oct 2026 · 5m 12s",
        link = "https://geoquiz-app.netlify.app/challenge?id=x",
        displayLink = "geoquiz-app.netlify.app/challenge"
    )

    val CARD_LONG_TITLE = CARD.copy(
        title = "Countries: Countries whose names start and end with the same letter and are longer than eight letters, " +
            "a deliberately long category name that has to be cut off",
        displayLink = "play.google.com/store/apps/details?id=com.geoquiz.app"
    )
}
