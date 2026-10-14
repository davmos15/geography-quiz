package com.geoquiz.app.ui.tapmap

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
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapQuestionGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.map.squareFeature
import com.geoquiz.app.ui.map.squareLayer
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Tap the map's own screen logic (task 5.2, D37). Uses the real [TapMapQuestionGenerator] and
 * [GetCountriesForQuizUseCase] (against a fake [CountryRepository]) so item resolution and the
 * shuffle are exercised, not just the [TapMapGameViewModel]'s own state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TapMapGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
        fun advance(millis: Long) { now += millis }
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val europe = listOf(
        TestQuizData.FRANCE, TestQuizData.GERMANY, TestQuizData.AUSTRIA
    )
    private val allCountries = europe + listOf(TestQuizData.PERU)

    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(allCountries)
    }
    private val getCountriesForQuiz = GetCountriesForQuizUseCase(countryRepository, GetRegionMembersUseCase(countryRepository))

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mapRepository = mockk<MapRepository> {
        coEvery { layer(any()) } throws IllegalStateException("not needed in this test")
    }
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val achievementRepository = mockk<AchievementRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private fun gameModes(seed: Long) = GameModeRegistry(
        setOf(
            TapMapGameMode(
                TapMapQuestionGenerator(getCountriesForQuiz, QuizRandom(Random(seed))),
                mockk<ValidateAnswerUseCase>(relaxed = true),
                CalculateScoreUseCase()
            )
        )
    )

    private val completeQuiz = CompleteQuizUseCase(
        gameModes = gameModes(SEED),
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

    private fun routeHandle(
        variant: String = TapMapGameMode.VARIANT_FIND,
        categoryType: String = "map_region",
        categoryValue: String = "europe",
        difficulty: Difficulty? = Difficulty.NORMAL
    ) = SavedStateHandle(
        buildMap<String, Any?> {
            put("variant", variant)
            put("categoryType", categoryType)
            put("categoryValue", categoryValue)
            if (difficulty != null) put(TapMapGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(handle: SavedStateHandle, seed: Long = SEED): TapMapGameViewModel {
        val vm = TapMapGameViewModel(
            savedStateHandle = handle,
            gameModes = gameModes(seed),
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            countryRepository = countryRepository,
            mapRepository = mapRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(seed)),
            // The same dispatcher as Main (not the real Dispatchers.Default), so the nit's
            // off-main-thread frame computation is still driven by runCurrent()/advanceBy().
            defaultDispatcher = dispatcher
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    /**
     * Advances virtual time by exactly [millis] then runs what that unblocks. Never
     * `advanceUntilIdle()`: while a round is running the VM's ticker collects
     * `quizTimer.ticks()` forever (see `QuizViewModelTest`'s doc), so idle is never reached.
     */
    private fun advanceBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private fun TapMapGameViewModel.active(): TapMapGameState = (uiState.value as TapMapUiState.Active).state

    /** The shuffle order [TapMapQuestionGenerator] gives [countries] for [seed], independent of VM state. */
    private fun expectedOrder(countries: List<Country>, seed: Long): List<String> =
        countries.shuffled(Random(seed)).map { it.code }

    @Test
    fun `starts with a target from the region, every member in the asked list`() {
        val vm = viewModel(routeHandle())
        val state = vm.active()

        assertEquals(europe.map { it.code }.toSet(), state.quizState.quiz.countries.map { it.code }.toSet())
        assertEquals(expectedOrder(europe, SEED).first(), state.currentTargetCode)
        assertFalse(state.quizState.isComplete)
    }

    @Test
    fun `a correct tap finds the country and moves to the next prompt`() {
        val vm = viewModel(routeHandle())
        val order = expectedOrder(europe, SEED)
        val target = order[0]

        vm.onFeatureTap(MapHit(featureId = target, isPlayable = true, viaTapZone = false))
        runCurrent()

        val state = vm.active()
        assertTrue(target in state.quizState.answeredCountries)
        assertEquals(order[1], state.currentTargetCode)
        assertEquals(0, state.quizState.incorrectGuesses)
    }

    @Test
    fun `a wrong tap names the tapped country and counts as a wrong guess`() {
        val vm = viewModel(routeHandle())
        val order = expectedOrder(europe, SEED)
        val target = order[0]
        val wrong = order[1]

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()

        var state = vm.active()
        assertEquals(wrong, state.wrongFlashCode)
        assertEquals(1, state.quizState.incorrectGuesses)
        assertTrue(state.quizState.incorrectGuessStrings.isNotEmpty())
        // The prompt has not advanced: the target is unchanged.
        assertEquals(target, state.currentTargetCode)

        advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        state = vm.active()
        assertNull("the flash clears", state.wrongFlashCode)
        assertEquals(target, state.currentTargetCode)
    }

    @Test
    fun `sea, non-playable and non-member taps are ignored`() {
        val vm = viewModel(routeHandle())
        val before = vm.active()

        vm.onFeatureTap(null)
        vm.onFeatureTap(MapHit(featureId = "ATA", isPlayable = false, viaTapZone = false))
        // PER (Peru) is in allCountries but not a member of the Europe round.
        vm.onFeatureTap(MapHit(featureId = "PER", isPlayable = true, viaTapZone = false))
        runCurrent()

        val after = vm.active()
        assertEquals(before.quizState.incorrectGuesses, after.quizState.incorrectGuesses)
        assertEquals(before.currentTargetCode, after.currentTargetCode)
        assertTrue(after.quizState.answeredCountries.isEmpty())
    }

    @Test
    fun `already-found countries are ignored on a later tap`() {
        val vm = viewModel(routeHandle())
        val order = expectedOrder(europe, SEED)
        val first = order[0]

        vm.onFeatureTap(MapHit(featureId = first, isPlayable = true, viaTapZone = false))
        runCurrent()
        val afterFound = vm.active()
        val guessesAfterFound = afterFound.quizState.incorrectGuesses

        // Tapping the already-found country again must not register as a wrong guess.
        vm.onFeatureTap(MapHit(featureId = first, isPlayable = true, viaTapZone = false))
        runCurrent()

        assertEquals(guessesAfterFound, vm.active().quizState.incorrectGuesses)
    }

    @Test
    fun `normal - 3 wrong taps on one prompt reveal it, mark missed, then move on`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)
        val target = order[0]
        val wrong = order[1]

        // Taps 1 and 2: each schedules a "That's X" flash that fully resolves (clears, prompt
        // unchanged) before the next tap, so only the 3rd tap's flash is still pending after
        // this loop.
        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        }
        assertEquals(target, vm.active().currentTargetCode)

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()
        // Past the flash delay, short of the reveal delay: the target is now being revealed.
        advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        val revealing = vm.active()
        assertEquals(target, revealing.revealedCode)
        assertNull(revealing.wrongFlashCode)

        advanceBy(TapMapGameViewModel.REVEAL_MILLIS)
        val state = vm.active()
        assertNull(state.revealedCode)
        assertTrue(target in state.quizState.missedCountries)
        assertEquals(order[1], state.currentTargetCode)
        assertFalse(state.quizState.isComplete)
    }

    @Test
    fun `hard - 3 wrong taps in total end the round`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))
        val order = expectedOrder(europe, SEED)
        val wrong = order[1]

        // Two wrong taps on the first prompt, then switch targets by also wrongly tapping once
        // more: Hard ends at 3 wrong *in total*, not per prompt.
        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        }
        assertFalse(vm.active().quizState.isComplete)

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)

        val state = vm.active()
        assertTrue(state.quizState.isComplete)
        assertEquals(3, state.quizState.incorrectGuesses)
    }

    @Test
    fun `skip marks the current target missed and moves on without a strike`() {
        val vm = viewModel(routeHandle())
        val order = expectedOrder(europe, SEED)
        val target = order[0]

        vm.onSkip()
        runCurrent()

        val state = vm.active()
        assertTrue(target in state.quizState.missedCountries)
        assertEquals(order[1], state.currentTargetCode)
        assertEquals(0, state.quizState.incorrectGuesses)
    }

    @Test
    fun `hint highlights subregion mates once, and again does nothing more`() {
        val vm = viewModel(routeHandle())

        vm.onHint()
        runCurrent()
        val afterFirst = vm.active()
        assertTrue(afterFirst.hintUsedThisPrompt)
        assertEquals(listOf(HintType.FLASH_REGION), afterFirst.quizState.hintsUsed)

        vm.onHint()
        runCurrent()
        val afterSecond = vm.active()
        assertEquals(1, afterSecond.quizState.hintsUsed.size)

        advanceBy(TapMapGameViewModel.HINT_FLASH_MILLIS)
        assertTrue("hint flash clears", vm.active().hintFlashCodes.isEmpty())
    }

    @Test
    fun `hint is available again on the next prompt`() {
        val vm = viewModel(routeHandle())
        vm.onHint()
        runCurrent()
        assertTrue(vm.active().hintUsedThisPrompt)

        vm.onSkip()
        runCurrent()

        assertFalse(vm.active().hintUsedThisPrompt)
    }

    @Test
    fun `easy - a wrong pick misses the target and moves on, a right pick finds it`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val choice = vm.active().quizState.choice
        requireNotNull(choice)
        val target = choice.targetCode
        val wrongOption = choice.options.first { it.code != target }

        vm.onChoiceSelected(wrongOption.code)
        runCurrent()
        val feedback = vm.active().quizState.choiceFeedback
        assertEquals(false, feedback?.isCorrect)

        advanceBy(TapMapGameViewModel.CHOICE_FEEDBACK_MILLIS)
        val afterWrong = vm.active()
        assertTrue(target in afterWrong.quizState.missedCountries)
        assertEquals(null, afterWrong.quizState.choiceFeedback)

        val nextChoice = requireNotNull(afterWrong.quizState.choice)
        vm.onChoiceSelected(nextChoice.targetCode)
        runCurrent()
        assertEquals(true, vm.active().quizState.choiceFeedback?.isCorrect)
    }

    @Test
    fun `completion request carries the mode id, variant and the full item list`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)

        order.forEach { code ->
            vm.onFeatureTap(MapHit(featureId = code, isPlayable = true, viaTapZone = false))
            runCurrent()
        }

        assertTrue(vm.active().quizState.isComplete)
        val completion = vm.completion.value
        requireNotNull(completion)
        val recorded = requireNotNull(completedQuizzes.stored)
        assertEquals(completion.resultId, recorded.id)
        assertEquals(TapMapGameMode.MODE_ID, recorded.quizModeId)
        assertEquals(TapMapGameMode.VARIANT_FIND, recorded.variantId)
        assertEquals(europe.map { it.code }.toSet(), recorded.countryCodes.toSet())
        assertEquals(europe.map { it.code }.toSet(), recorded.answeredCodes.toSet())
    }

    @Test
    fun `a wrongly tapped country's name is recorded as an incorrect guess`() {
        val vm = viewModel(routeHandle())
        val order = expectedOrder(europe, SEED)
        val wrongCode = order[1]
        val wrongName = europe.first { it.code == wrongCode }.name

        vm.onFeatureTap(MapHit(featureId = wrongCode, isPlayable = true, viaTapZone = false))
        runCurrent()

        assertTrue(wrongName in vm.active().quizState.incorrectGuessStrings)
    }

    @Test
    fun `restores progress from the saved state handle after process death`() {
        val handle = routeHandle()
        val vm = viewModel(handle)
        val order = expectedOrder(europe, SEED)

        vm.onFeatureTap(MapHit(featureId = order[0], isPlayable = true, viaTapZone = false))
        runCurrent()
        clock.advance(5_000)

        val restored = viewModel(afterProcessDeath(handle))
        val state = restored.active()

        assertTrue(order[0] in state.quizState.answeredCountries)
        assertEquals(order[1], state.currentTargetCode)
    }

    // --- Review fixes (task 5.2a) ---------------------------------------------------------

    @Test
    fun `taps, skip and hint are ignored while a reveal is showing`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)
        val target = order[0]
        val wrong = order[1]

        repeat(3) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        }
        val revealing = vm.active()
        assertEquals(target, revealing.revealedCode)

        // None of these should do anything while the reveal is showing (B1).
        vm.onFeatureTap(MapHit(featureId = target, isPlayable = true, viaTapZone = false))
        vm.onSkip()
        vm.onHint()
        runCurrent()

        val stillRevealing = vm.active()
        assertEquals(target, stillRevealing.revealedCode)
        assertFalse(target in stillRevealing.quizState.answeredCountries)
        assertFalse(stillRevealing.hintUsedThisPrompt)

        advanceBy(TapMapGameViewModel.REVEAL_MILLIS)
        val after = vm.active()
        assertTrue(target in after.quizState.missedCountries)
        assertEquals(order[1], after.currentTargetCode)
    }

    @Test
    fun `a correct tap while a wrong-tap flash is pending does not later act on the old prompt`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)
        val target = order[0]
        val wrong = order[1]

        // Two wrong taps on the first prompt (short of Normal's 3-wrong reveal limit).
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent() // the 2nd tap's flash is now pending, not yet resolved

        // Before that flash resolves, the player finds the real target.
        vm.onFeatureTap(MapHit(featureId = target, isPlayable = true, viaTapZone = false))
        runCurrent()
        val afterCorrect = vm.active()
        assertEquals(order[1], afterCorrect.currentTargetCode)
        assertEquals(0, afterCorrect.wrongTapsThisPrompt)

        // The stale flash's delay elapsing must not reveal or miss the new prompt.
        advanceBy(TapMapGameViewModel.REVEAL_MILLIS + TapMapGameViewModel.WRONG_FLASH_MILLIS)
        val after = vm.active()
        assertNull(after.revealedCode)
        assertFalse(order[1] in after.quizState.missedCountries)
        assertEquals(order[1], after.currentTargetCode)
    }

    @Test
    fun `easy - skip and hint do nothing while a pick's feedback is showing`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val choice = requireNotNull(vm.active().quizState.choice)

        vm.onChoiceSelected(choice.targetCode)
        runCurrent()
        assertNotNull(vm.active().quizState.choiceFeedback)

        vm.onSkip()
        vm.onHint()
        runCurrent()

        val state = vm.active()
        assertNotNull("still mid-feedback", state.quizState.choiceFeedback)
        assertTrue("no hint recorded", state.quizState.hintsUsed.isEmpty())
    }

    @Test
    fun `a second tap on the still-flashing wrong country is ignored`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)
        val wrong = order[1]

        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()
        assertEquals(1, vm.active().quizState.incorrectGuesses)

        // Tapping the same still-flashing wrong country again must not count twice (S1).
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()

        assertEquals(1, vm.active().quizState.incorrectGuesses)
        assertEquals(1, vm.active().wrongTapsThisPrompt)
    }

    @Test
    fun `already-missed countries are ignored on a later tap, not counted wrong`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))
        val order = expectedOrder(europe, SEED)
        val target = order[0]

        // Skip the first prompt (missed, no strike), then try tapping it again.
        vm.onSkip()
        runCurrent()
        assertTrue(target in vm.active().quizState.missedCountries)

        vm.onFeatureTap(MapHit(featureId = target, isPlayable = true, viaTapZone = false))
        runCurrent()

        assertEquals(0, vm.active().quizState.incorrectGuesses)
        assertEquals(order[1], vm.active().currentTargetCode)
    }

    @Test
    fun `hard - extra taps once the strike limit is reached are ignored, never exceeding it`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))
        val order = expectedOrder(europe, SEED)
        val wrong = order[1]

        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        }
        // The 3rd wrong tap reaches the limit; its flash is still pending (round not ended yet).
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()
        assertFalse(vm.active().quizState.isComplete)

        // Extra taps while that flash is pending, even on the correct target, must be ignored.
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        vm.onFeatureTap(MapHit(featureId = order[0], isPlayable = true, viaTapZone = false))
        runCurrent()

        advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        val state = vm.active()
        assertTrue(state.quizState.isComplete)
        assertEquals(3, state.quizState.incorrectGuesses)
    }

    @Test
    fun `backgrounding pauses the round, blocking input, and resuming continues it`() {
        val vm = viewModel(routeHandle())
        assertFalse(vm.active().quizState.isPaused)

        vm.onBackgrounded()
        runCurrent()
        assertTrue(vm.active().quizState.isPaused)

        val order = expectedOrder(europe, SEED)
        vm.onFeatureTap(MapHit(featureId = order[0], isPlayable = true, viaTapZone = false))
        runCurrent()
        assertFalse("paused: no input accepted", order[0] in vm.active().quizState.answeredCountries)

        vm.togglePause()
        runCurrent()
        assertFalse(vm.active().quizState.isPaused)

        vm.onFeatureTap(MapHit(featureId = order[0], isPlayable = true, viaTapZone = false))
        runCurrent()
        assertTrue(order[0] in vm.active().quizState.answeredCountries)
    }

    @Test
    fun `the timer does not advance while backgrounded`() {
        val vm = viewModel(routeHandle())
        val before = vm.timerSeconds.value

        vm.onBackgrounded()
        runCurrent()
        clock.advance(5_000)
        vm.togglePause()
        runCurrent()

        // Resuming re-syncs the ticker from the clock, which did not move while paused.
        assertEquals(before, vm.timerSeconds.value)
    }

    @Test
    fun `a restored round comes back paused like classic`() {
        val handle = routeHandle()
        val vm = viewModel(handle)
        val order = expectedOrder(europe, SEED)
        vm.onFeatureTap(MapHit(featureId = order[0], isPlayable = true, viaTapZone = false))
        runCurrent()

        val restored = viewModel(afterProcessDeath(handle))

        assertTrue(restored.active().quizState.isPaused)
    }

    @Test
    fun `a practice round of one tiny country still gets a sensibly sized frame`() {
        // A 1x1 degree box: far smaller, projected, than any real country's frame would be.
        val tinyLayer = squareLayer(squareFeature("FRA", 12f, 13f), id = GeoLayerId.ADMIN0_50M)
        coEvery { mapRepository.layer(GeoLayerId.ADMIN0_50M) } returns tinyLayer

        val vm = viewModel(routeHandle(categoryType = "practice", categoryValue = "FRA"))
        runCurrent()

        val frame = requireNotNull(vm.mapState.value.frame)
        val unclamped = requireNotNull(RegionPreset.WORLD.frame(tinyLayer, setOf("FRA")))
        assertTrue("width is floored, not the tiny raw fit", frame.width > unclamped.width * MIN_FRAME_CLAMP_FACTOR)
        assertTrue("height is floored, not the tiny raw fit", frame.height > unclamped.height * MIN_FRAME_CLAMP_FACTOR)
    }

    companion object {
        private const val SEED = 11L
        private const val MIN_FRAME_CLAMP_FACTOR = 5
    }

    // --- Verification-review fixes (task 5.2a) ---------------------------------------------

    @Test
    fun `easy - backgrounding during a pick's feedback still moves on after resuming`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val first = requireNotNull(vm.active().quizState.choice)

        vm.onChoiceSelected(first.targetCode)
        runCurrent()
        vm.onBackgrounded()
        advanceBy(TapMapGameViewModel.CHOICE_FEEDBACK_MILLIS)
        vm.togglePause()
        runCurrent()

        val state = vm.active().quizState
        assertEquals(null, state.choiceFeedback)
        val next = requireNotNull(state.choice)
        assertTrue(next.targetCode != first.targetCode)
    }

    private fun reachHardLimit(vm: TapMapGameViewModel, wrong: String) {
        repeat(2) {
            vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
            advanceBy(TapMapGameViewModel.WRONG_FLASH_MILLIS)
        }
        // Third strike: its game over is still pending.
        vm.onFeatureTap(MapHit(featureId = wrong, isPlayable = true, viaTapZone = false))
        runCurrent()
    }

    @Test
    fun `hard - skip in the window after the third strike ends the round`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))
        val order = expectedOrder(europe, SEED)
        reachHardLimit(vm, order[1])
        assertFalse(vm.active().quizState.isComplete)

        vm.onSkip()
        runCurrent()

        val qs = vm.active().quizState
        assertTrue(qs.isComplete)
        assertEquals(3, qs.incorrectGuesses)
        // Skip did not mark the prompt missed on top of ending the round.
        assertFalse(order[0] in qs.missedCountries)
    }

    @Test
    fun `hard - a round restored after its third strike comes back finished`() {
        val handle = routeHandle(difficulty = Difficulty.HARD)
        val vm = viewModel(handle)
        val order = expectedOrder(europe, SEED)
        reachHardLimit(vm, order[1])

        val restored = viewModel(afterProcessDeath(handle))
        runCurrent()

        assertTrue(restored.active().quizState.isComplete)
    }
}
