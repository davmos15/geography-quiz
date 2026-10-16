package com.geoquiz.app.ui.dailychallenge

import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DailyChallengeSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val observeStreakUseCase = mockk<ObserveStreakUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { observeStreakUseCase() } returns flowOf(StreakStatus.NONE)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun viewModel() = DailyChallengeSetupViewModel(observeStreakUseCase)

    @Test
    fun `shows the start button when today's challenge has not been played`() {
        val viewModel = viewModel()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.alreadyCompletedToday)
    }

    @Test
    fun `shows the completed state when today's challenge is already recorded`() {
        every { observeStreakUseCase() } returns flowOf(StreakStatus.NONE.copy(current = 1, best = 1, playedToday = true))

        val viewModel = viewModel()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.alreadyCompletedToday)
    }

    @Test
    fun `finishing today's round while the screen is alive hides Start`() {
        // Back from Results lands on this same ViewModel: the live history must flip the button.
        val streak = MutableStateFlow(StreakStatus.NONE)
        every { observeStreakUseCase() } returns streak
        val viewModel = viewModel()
        runCurrent()
        assertFalse(viewModel.uiState.value.alreadyCompletedToday)

        streak.value = StreakStatus(current = 1, best = 1, freezesHeld = 0, daysToNextFreeze = 6, playedToday = true)
        runCurrent()

        assertTrue(viewModel.uiState.value.alreadyCompletedToday)
        assertEquals(1, viewModel.uiState.value.streak.current)
    }

    @Test
    fun `shows the streak from ObserveStreakUseCase`() {
        val streak = StreakStatus(current = 4, best = 9, freezesHeld = 1, daysToNextFreeze = 3, playedToday = true)
        every { observeStreakUseCase() } returns flowOf(streak)

        val viewModel = viewModel()
        runCurrent()

        assertEquals(streak, viewModel.uiState.value.streak)
    }

    @Test
    fun `stays loading with no streak before ObserveStreakUseCase emits`() {
        every { observeStreakUseCase() } returns emptyFlow()

        val viewModel = viewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(StreakStatus.NONE, viewModel.uiState.value.streak)
    }
}
