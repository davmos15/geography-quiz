package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.usca.UsCaRegionsAnswerValidator
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Name all's own state machine (task 6.2, D40's precedent). Never `advanceUntilIdle()`: while a
 * round is active the VM's ticker collects `quizTimer.ticks()` forever, so idle is never reached
 * (see `TapMapGameViewModelTest`'s doc; this hung a build for an hour previously). Every action
 * is followed by [runCurrent] instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsCaNameAllViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mapRepository = mockk<MapRepository> {
        coEvery { layer(GeoLayerId.ADMIN1_50M) } returns FAKE_ADMIN1_LAYER
    }
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val achievementRepository = mockk<AchievementRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private val completeQuiz = CompleteQuizUseCase(
        gameModes = GameModeRegistry(setOf(UsCaRegionsGameMode(CalculateScoreUseCase()))),
        completedQuizRepository = completedQuizzes,
        savedQuizRepository = savedQuizRepository,
        achievementRepository = achievementRepository,
        quizHistoryRepository = quizHistoryRepository,
        playGamesService = playGames
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun handle(categoryType: String = "us", categoryValue: String = "US states", difficulty: String? = "normal") =
        SavedStateHandle(
            buildMap<String, Any?> {
                put("categoryType", categoryType)
                put("categoryValue", categoryValue)
                if (difficulty != null) put("difficulty", difficulty)
            }
        )

    private fun viewModel(handle: SavedStateHandle): UsCaNameAllViewModel {
        val vm = UsCaNameAllViewModel(
            savedStateHandle = handle,
            validateAnswer = UsCaRegionsAnswerValidator(NormalizeInputUseCase()),
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            clock = clock
        )
        runCurrent()
        return vm
    }

    private fun UsCaNameAllViewModel.active() = (uiState.value as UsCaNameAllUiState.Active).state

    @Test
    fun `loads the round's items from the admin1 layer, excluding other groups`() {
        val vm = viewModel(handle())
        val state = vm.active()
        assertEquals(setOf("US-CA", "US-NY", "US-TX"), state.quiz.countries.map { it.code }.toSet())
    }

    @Test
    fun `typing the correct name fills it in`() {
        val vm = viewModel(handle())
        vm.onInputChange("California")
        vm.onSubmitAnswer()
        runCurrent()
        val state = vm.active()
        assertTrue("US-CA" in state.answeredCountries)
        assertEquals("", state.currentInput)
    }

    @Test
    fun `the postal code is also accepted`() {
        val vm = viewModel(handle())
        vm.onInputChange("NY")
        vm.onSubmitAnswer()
        runCurrent()
        assertTrue("US-NY" in vm.active().answeredCountries)
    }

    @Test
    fun `naming every member completes the round and records it`() {
        val vm = viewModel(handle())
        for (name in listOf("California", "New York", "Texas")) {
            vm.onInputChange(name)
            vm.onSubmitAnswer()
            runCurrent()
        }
        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(UsCaRegionsGameMode.VARIANT_NAME_ALL, completedQuizzes.stored?.variantId)
    }

    @Test
    fun `Easy falls back to Normal - D40's precedent, Name all has no Easy tier`() {
        val vm = viewModel(handle(difficulty = "easy"))
        assertEquals(Difficulty.NORMAL, vm.difficulty.value)
    }

    @Test
    fun `Hard strikes end the round after 3 wrong guesses`() {
        val vm = viewModel(handle(difficulty = "hard"))
        for (guess in listOf("Nope1", "Nope2x", "Nope3y")) {
            vm.onInputChange(guess)
            vm.onSubmitAnswer()
            runCurrent()
        }
        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(3, state.incorrectGuesses)
    }

    @Test
    fun `restores progress from a saved snapshot after process death`() {
        val original = handle()
        val vm = viewModel(original)
        vm.onInputChange("California")
        vm.onSubmitAnswer()
        runCurrent()

        val restoredHandle = SavedStateHandle(original.keys().associateWith { original.get<Any?>(it) })
        val restored = viewModel(restoredHandle)
        assertTrue("US-CA" in restored.active().answeredCountries)
    }

    @Test
    fun `no regions to ask about shows Unavailable and records nothing`() {
        coEvery { mapRepository.layer(GeoLayerId.ADMIN1_50M) } returns fakeAdmin1Layer()

        val vm = viewModel(handle())

        assertEquals(UsCaNameAllUiState.Unavailable, vm.uiState.value)
        assertNull(completedQuizzes.stored)
    }
}
