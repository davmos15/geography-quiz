package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tap's own state machine (task 6.2, D36 adapted). See `UsCaNameAllViewModelTest`'s doc for why
 * this test never calls `advanceUntilIdle()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsCaTapViewModelTest {

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
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advanceBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private fun handle(categoryType: String = "us", categoryValue: String = "US states", difficulty: String? = "normal") =
        SavedStateHandle(
            buildMap<String, Any?> {
                put("categoryType", categoryType)
                put("categoryValue", categoryValue)
                if (difficulty != null) put("difficulty", difficulty)
            }
        )

    private fun viewModel(handle: SavedStateHandle): UsCaTapViewModel {
        val vm = UsCaTapViewModel(
            savedStateHandle = handle,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            clock = clock
        )
        runCurrent()
        return vm
    }

    private fun UsCaTapViewModel.active() = (uiState.value as UsCaTapUiState.Active).state

    @Test
    fun `starts with a target from the round's members`() {
        val vm = viewModel(handle())
        val state = vm.active()
        assertEquals(setOf("US-CA", "US-NY", "US-TX"), state.quizState.quiz.countries.map { it.code }.toSet())
        assertTrue(state.currentTargetCode in setOf("US-CA", "US-NY", "US-TX"))
    }

    @Test
    fun `a correct tap finds the region and moves to the next prompt`() {
        val vm = viewModel(handle())
        val target = vm.active().currentTargetCode!!
        vm.onFeatureTap(MapHit(featureId = target, isPlayable = true, viaTapZone = false))
        runCurrent()
        val state = vm.active()
        assertTrue(target in state.quizState.answeredCountries)
        assertFalse(target == state.currentTargetCode)
    }

    @Test
    fun `a wrong tap names the tapped region, counts as a wrong guess, flash clears`() {
        val vm = viewModel(handle())
        val target = vm.active().currentTargetCode!!
        val wrong = setOf("US-CA", "US-NY", "US-TX").first { it != target }

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()
        var state = vm.active()
        assertEquals(wrong, state.wrongFlashCode)
        assertEquals(1, state.quizState.incorrectGuesses)
        assertEquals(target, state.currentTargetCode)

        advanceBy(UsCaTapViewModel.WRONG_FLASH_MILLIS)
        state = vm.active()
        assertNull(state.wrongFlashCode)
        assertEquals(target, state.currentTargetCode)
    }

    @Test
    fun `sea, out-of-group and already-found taps are ignored`() {
        val vm = viewModel(handle())
        val before = vm.active()

        vm.onFeatureTap(null)
        vm.onFeatureTap(MapHit(featureId = "CA-ON", isPlayable = true, viaTapZone = false))
        runCurrent()

        val after = vm.active()
        assertEquals(before.quizState.incorrectGuesses, after.quizState.incorrectGuesses)
        assertEquals(before.currentTargetCode, after.currentTargetCode)
    }

    @Test
    fun `normal - 3 wrong taps on one prompt reveal it, mark missed, then move on`() {
        val vm = viewModel(handle(difficulty = "normal"))
        val target = vm.active().currentTargetCode!!
        val wrong = setOf("US-CA", "US-NY", "US-TX").first { it != target }

        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(UsCaTapViewModel.WRONG_FLASH_MILLIS)
        }
        assertEquals(target, vm.active().currentTargetCode)

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        advanceBy(UsCaTapViewModel.WRONG_FLASH_MILLIS)
        val revealing = vm.active()
        assertEquals(target, revealing.revealedCode)

        advanceBy(UsCaTapViewModel.REVEAL_MILLIS)
        val state = vm.active()
        assertNull(state.revealedCode)
        assertTrue(target in state.quizState.missedCountries)
        assertFalse(state.quizState.isComplete)
    }

    @Test
    fun `hard - 3 wrong taps in total end the round`() {
        val vm = viewModel(handle(difficulty = "hard"))
        val target = vm.active().currentTargetCode!!
        val wrong = setOf("US-CA", "US-NY", "US-TX").first { it != target }

        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(UsCaTapViewModel.WRONG_FLASH_MILLIS)
        }
        assertFalse(vm.active().quizState.isComplete)

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        advanceBy(UsCaTapViewModel.WRONG_FLASH_MILLIS)

        val state = vm.active()
        assertTrue(state.quizState.isComplete)
        assertEquals(3, state.quizState.incorrectGuesses)
        assertEquals(1, completedQuizzes.saveCount)
    }

    @Test
    fun `Easy falls back to Normal - Multiple choice is this mode's Easy-equivalent variant`() {
        val vm = viewModel(handle(difficulty = "easy"))
        assertEquals(Difficulty.NORMAL, vm.difficulty.value)
    }
}
