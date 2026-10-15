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
import com.geoquiz.app.domain.mode.QuizRandom
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** Multiple choice's own state machine (task 6.2). See `UsCaNameAllViewModelTest`'s doc for why
 * this test never calls `advanceUntilIdle()`. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsCaChoiceViewModelTest {

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
        every { settingsRepository.vibration } returns flowOf(true)
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

    private fun handle(categoryType: String = "us", categoryValue: String = "US states") =
        SavedStateHandle(mapOf("categoryType" to categoryType, "categoryValue" to categoryValue))

    private fun viewModel(handle: SavedStateHandle, seed: Long = 42L): UsCaChoiceViewModel {
        val vm = UsCaChoiceViewModel(
            savedStateHandle = handle,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(seed))
        )
        runCurrent()
        return vm
    }

    private fun UsCaChoiceViewModel.active() = (uiState.value as UsCaChoiceUiState.Active).state

    @Test
    fun `starts with a question for one of the round's members, options drawn from the group`() {
        // The fixture's US group only has 3 members, so the question has 3 options, not the
        // usual 4 (CHOICE_OPTION_COUNT): UsCaRegionsChoiceGenerator never tops up from outside
        // the round's own group (unlike the real data, always 13+ members).
        val vm = viewModel(handle())
        val choice = vm.active().choice
        requireNotNull(choice)
        assertEquals(3, choice.options.size)
        assertTrue(choice.options.any { it.code == choice.targetCode })
        assertTrue(choice.targetCode in setOf("US-CA", "US-NY", "US-TX"))
    }

    @Test
    fun `a correct pick finds the target and moves to the next question`() {
        val vm = viewModel(handle())
        val choice = requireNotNull(vm.active().choice)
        vm.onChoiceSelected(choice.targetCode)
        runCurrent()
        assertTrue(choice.targetCode in vm.active().answeredCountries)

        advanceBy(UsCaChoiceViewModel.CHOICE_FEEDBACK_MILLIS)
        assertEquals(null, vm.active().choiceFeedback)
    }

    @Test
    fun `a wrong pick misses the target and moves on`() {
        val vm = viewModel(handle())
        val choice = requireNotNull(vm.active().choice)
        val wrongOption = choice.options.first { it.code != choice.targetCode }
        vm.onChoiceSelected(wrongOption.code)
        runCurrent()
        assertEquals(1, vm.active().incorrectGuesses)

        advanceBy(UsCaChoiceViewModel.CHOICE_FEEDBACK_MILLIS)
        assertTrue(choice.targetCode in vm.active().missedCountries)
    }

    @Test
    fun `answering every member completes the round and records it as Easy`() {
        val vm = viewModel(handle())
        repeat(3) {
            val choice = requireNotNull(vm.active().choice)
            vm.onChoiceSelected(choice.targetCode)
            runCurrent()
            advanceBy(UsCaChoiceViewModel.CHOICE_FEEDBACK_MILLIS)
        }
        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(UsCaRegionsGameMode.VARIANT_CHOICE, completedQuizzes.stored?.variantId)
        assertEquals(Difficulty.EASY.id, completedQuizzes.stored?.difficultyId)
    }
}
