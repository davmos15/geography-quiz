package com.geoquiz.app.ui.dailychallenge

import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyChallengeSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    @Test
    fun `shows the start button when today's challenge has not been played`() {
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", any())
        } returns false

        val viewModel = DailyChallengeSetupViewModel(quizHistoryRepository)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.alreadyCompletedToday)
    }

    @Test
    fun `shows the completed state when today's challenge is already recorded`() {
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", any())
        } returns true

        val viewModel = DailyChallengeSetupViewModel(quizHistoryRepository)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.alreadyCompletedToday)
    }

    @Test
    fun `queries history with today's date and this mode's id`() {
        coEvery { quizHistoryRepository.hasCompletedCategory(any(), any(), any()) } returns false

        DailyChallengeSetupViewModel(quizHistoryRepository)
        runCurrent()

        val today = com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeDate.today()
        io.mockk.coVerify { quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", today) }
    }
}
