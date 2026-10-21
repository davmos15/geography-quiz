package com.geoquiz.app.ui.physicalfeatures

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
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesCategory
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureFilter
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureTab
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Task 7.2's own state machine (D51). Never calls `advanceUntilIdle()` against the timer ticker
 * (`QuizTimer.ticks()` never goes idle); every advance uses `advanceTimeBy` + `runCurrent`, the
 * same rule the Phase 5/6 modes' tests follow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhysicalFeaturesGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mapRepository = mockk<MapRepository> {
        coEvery { layer(GeoLayerId.RIVERS_50M) } returns FAKE_RIVERS_LAYER
    }
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val achievementRepository = mockk<AchievementRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private val completeQuiz = CompleteQuizUseCase(
        gameModes = GameModeRegistry(setOf(PhysicalFeaturesGameMode(CalculateScoreUseCase()))),
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
    private fun advanceBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private fun handle(difficulty: String? = null): SavedStateHandle {
        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS))
        val args = mutableMapOf("categoryType" to category.typeKey, "categoryValue" to category.valueKey)
        difficulty?.let { args["difficulty"] = it }
        return SavedStateHandle(args)
    }

    private fun viewModel(
        handle: SavedStateHandle,
        seed: Long = 42L,
        repository: FakePhysicalFeatureRepository = FakePhysicalFeatureRepository()
    ): PhysicalFeaturesGameViewModel {
        val vm = PhysicalFeaturesGameViewModel(
            savedStateHandle = handle,
            physicalFeatureRepository = repository,
            mapRepository = mapRepository,
            answerValidator = com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureAnswerValidator(
                com.geoquiz.app.domain.usecase.NormalizeInputUseCase()
            ),
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(seed))
        )
        runCurrent()
        return vm
    }

    private fun PhysicalFeaturesGameViewModel.active() = (uiState.value as PhysicalFeaturesUiState.Active).state

    @Test
    fun `a Normal round starts typed, with all three fake rivers and no choice`() {
        val vm = viewModel(handle())
        val state = vm.active()
        assertEquals(3, state.features.size)
        assertEquals(Difficulty.NORMAL, state.difficulty)
        assertNull(state.choice)
        assertEquals(state.features[0].id, state.current?.id)
    }

    @Test
    fun `typing the current feature's exact name advances after a short pause`() {
        val vm = viewModel(handle())
        val target = vm.active().current!!
        vm.onInputChange(target.name)
        vm.onSubmit()
        runCurrent()
        assertTrue(target.id in vm.active().answeredIds)

        advanceBy(PhysicalFeaturesGameViewModel.ADVANCE_DELAY_MILLIS)
        assertEquals(1, vm.active().currentIndex)
    }

    @Test
    fun `a typo one edit away is accepted at Normal`() {
        val vm = viewModel(handle())
        val target = vm.active().current!!
        // Drop the last letter: still within one edit of the real name.
        vm.onInputChange(target.name.dropLast(1))
        vm.onSubmit()
        runCurrent()
        assertTrue(target.id in vm.active().answeredIds)
    }

    @Test
    fun `an unrelated wrong answer is a strike but does not advance`() {
        val vm = viewModel(handle())
        val target = vm.active().current!!
        vm.onInputChange("Not a real river")
        vm.onSubmit()
        runCurrent()

        val state = vm.active()
        assertEquals(1, state.incorrectGuesses)
        assertEquals(target.id, state.current?.id)
        assertEquals(PhysicalFeatureFeedback.Incorrect, state.feedback)
    }

    @Test
    fun `Hard ends the round after 3 strikes and records it`() {
        val vm = viewModel(handle(difficulty = Difficulty.HARD.id))
        repeat(3) {
            vm.onInputChange("Not a real river")
            vm.onSubmit()
            runCurrent()
        }
        val state = vm.active()
        assertTrue(state.isComplete)
        runCurrent()
        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(Difficulty.HARD.id, completedQuizzes.stored?.difficultyId)
    }

    @Test
    fun `Easy shows a 4-option choice and a correct pick advances`() {
        val vm = viewModel(handle(difficulty = Difficulty.EASY.id))
        val choice = requireNotNull(vm.active().choice)
        vm.onChoiceSelected(choice.targetCode)
        runCurrent()
        assertTrue(choice.targetCode in vm.active().answeredIds)

        advanceBy(PhysicalFeaturesGameViewModel.CHOICE_FEEDBACK_MILLIS)
        assertEquals(1, vm.active().currentIndex)
    }

    @Test
    fun `Give up ends the round early and records it once`() {
        val vm = viewModel(handle())
        vm.onGiveUp()
        runCurrent()
        assertTrue(vm.active().isComplete)
        assertEquals(1, completedQuizzes.saveCount)
    }

    @Test
    fun `answering every feature completes the round and records it as Normal`() {
        val vm = viewModel(handle())
        repeat(3) {
            val target = vm.active().current!!
            vm.onInputChange(target.name)
            vm.onSubmit()
            runCurrent()
            advanceBy(PhysicalFeaturesGameViewModel.ADVANCE_DELAY_MILLIS)
        }
        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(3, state.answeredIds.size)
        runCurrent()
        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(Difficulty.NORMAL.id, completedQuizzes.stored?.difficultyId)
    }

    @Test
    fun `no features to ask about shows Unavailable and records nothing`() {
        val vm = viewModel(handle(), repository = FakePhysicalFeatureRepository(emptyList()))

        assertEquals(PhysicalFeaturesUiState.Unavailable, vm.uiState.value)
        assertNull(completedQuizzes.stored)
    }
}
