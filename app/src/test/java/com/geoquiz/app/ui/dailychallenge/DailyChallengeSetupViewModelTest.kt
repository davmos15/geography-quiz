package com.geoquiz.app.ui.dailychallenge

import app.cash.turbine.test
import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DailyChallengeSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val observeStreakUseCase = mockk<ObserveStreakUseCase>()

    @Before
    fun setUp() {
        every { observeStreakUseCase() } returns flowOf(StreakStatus.NONE)
    }

    private fun viewModel() = DailyChallengeSetupViewModel(observeStreakUseCase)

    @Test
    fun `shows the start button when today's challenge has not been played`() = runTest {
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.alreadyCompletedToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the completed state when today's challenge is already recorded`() = runTest {
        every { observeStreakUseCase() } returns flowOf(StreakStatus.NONE.copy(current = 1, best = 1, playedToday = true))
        val viewModel = viewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.alreadyCompletedToday)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishing today's round while the screen is alive hides Start`() = runTest {
        // Back from Results lands on this same ViewModel: the live history must flip the button.
        val streak = MutableStateFlow(StreakStatus.NONE)
        every { observeStreakUseCase() } returns streak
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertFalse(awaitItem().alreadyCompletedToday)

            streak.value = StreakStatus(current = 1, best = 1, freezesHeld = 0, daysToNextFreeze = 6, playedToday = true)

            val after = awaitItem()
            assertTrue(after.alreadyCompletedToday)
            assertEquals(1, after.streak.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows the streak from ObserveStreakUseCase`() = runTest {
        val streak = StreakStatus(current = 4, best = 9, freezesHeld = 1, daysToNextFreeze = 3, playedToday = true)
        every { observeStreakUseCase() } returns flowOf(streak)
        val viewModel = viewModel()

        viewModel.uiState.test {
            assertEquals(streak, awaitItem().streak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stays loading with no streak before ObserveStreakUseCase emits`() = runTest {
        every { observeStreakUseCase() } returns emptyFlow()
        val viewModel = viewModel()

        // No subscriber has appeared yet to start the upstream flow, and nothing has emitted, so
        // the StateFlow still holds its initial value.
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(StreakStatus.NONE, viewModel.uiState.value.streak)
    }

    /**
     * Review fix: [DailyChallengeSetupViewModel.uiState] is now built with `stateIn`/
     * `SharingStarted.WhileSubscribed`, not a `collect` in `init` that runs once, eagerly, for the
     * whole life of the ViewModel. [ObserveStreakUseCase] (and so its "today") must not be read
     * before the screen actually subscribes - unlike the old `collect`-in-`init` version, which
     * started reading "today" the moment the ViewModel was created, however long before the
     * screen itself resumed and subscribed (e.g. after a deep sleep).
     */
    @Test
    fun `ObserveStreakUseCase is not read until something subscribes to uiState`() = runTest {
        var collected = false
        every { observeStreakUseCase() } returns kotlinx.coroutines.flow.flow {
            collected = true
            emit(StreakStatus.NONE)
        }
        val viewModel = viewModel()

        assertFalse("today should not be read before any subscriber appears", collected)

        viewModel.uiState.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(collected)
    }
}
