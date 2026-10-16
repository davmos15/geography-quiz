package com.geoquiz.app.ui.flagspeed

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeSet
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeVisibility
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedGameMode
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedQuestionGenerator
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
 * Task 7.3 fix round, item 3: [FlagSpeedGameViewModel]'s own state machine had no coverage at
 * all, despite holding the most bug-prone logic in Phase 7 (a live clock that ends the round
 * itself, an open-ended item count, a combo that resets). [clock] is tied directly to the test
 * dispatcher's virtual time ([QuizTimerTest]'s own precedent), so [QuizTimer.ticks]' `delay()`
 * loop and the clock agree exactly; every advance uses `advanceTimeBy` + `runCurrent` via
 * [advanceBy]/[advanceSchedulerTo], **never** `advanceUntilIdle()` (the ticker never goes idle
 * while the round is running).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlagSpeedGameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MonotonicClock { dispatcher.scheduler.currentTime }

    /** One lookalike set, two members: every draw alternates between these two targets. */
    private val countryA = country("AAA", "Country A", "Capital A")
    private val countryB = country("BBB", "Country B", "Capital B")
    private val setAB = FlagLookalikeSet(
        id = "ab",
        countries = listOf("AAA", "BBB"),
        tier = 1,
        visibility = FlagLookalikeVisibility.CLEAR,
        explanation = "A and B look alike.",
        cues = emptyMap()
    )

    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(listOf(countryA, countryB))
    }
    private val lookalikeRepository = mockk<FlagLookalikeRepository> {
        coEvery { sets() } returns listOf(setAB)
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>(relaxed = true)
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private fun flagSpeedMode() = FlagSpeedGameMode(FlagSpeedQuestionGenerator(lookalikeRepository, countryRepository))

    private fun completeQuizUseCase(mode: FlagSpeedGameMode) = CompleteQuizUseCase(
        gameModes = GameModeRegistry(setOf(mode)),
        completedQuizRepository = completedQuizzes,
        savedQuizRepository = savedQuizRepository,
        achievementRepository = achievementRepository,
        quizHistoryRepository = quizHistoryRepository,
        playGamesService = playGames
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.EASY)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    /** Advances virtual time (and, with it, [clock]) by [millis], then drains the ready queue. */
    private fun advanceBy(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    /** Steps [advanceBy] one second at a time up to [targetMillis], mirroring the real per-second ticker. */
    private fun advanceSchedulerTo(targetMillis: Long) {
        var remaining = targetMillis - dispatcher.scheduler.currentTime
        while (remaining > 0) {
            val step = minOf(remaining, 1_000L)
            advanceBy(step)
            remaining -= step
        }
    }

    private fun routeHandle(difficulty: Difficulty? = null) = SavedStateHandle(
        buildMap<String, Any?> {
            if (difficulty != null) put(FlagSpeedGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle,
        mode: FlagSpeedGameMode = flagSpeedMode(),
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode),
        quizRandom: QuizRandom = QuizRandom(Random(1)),
        lookalikeRepo: FlagLookalikeRepository = lookalikeRepository
    ): FlagSpeedGameViewModel {
        val vm = FlagSpeedGameViewModel(
            savedStateHandle = handle,
            countryRepository = countryRepository,
            lookalikeRepository = lookalikeRepo,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = quizRandom
        )
        runCurrent()
        return vm
    }

    private fun FlagSpeedGameViewModel.active(): FlagSpeedRoundState = (uiState.value as FlagSpeedUiState.Active).state

    /** Answers the current question, correctly or not, and lets the pick commit. */
    private fun FlagSpeedGameViewModel.answer(correct: Boolean) {
        val question = active().question!!
        val code = if (correct) question.targetCode else question.options.first { it.code != question.targetCode }.code
        onChoiceSelected(code)
        runCurrent()
    }

    // --- Combo ---

    @Test
    fun `combo increases on a correct answer and resets to zero on a wrong one`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        assertEquals(0, vm.active().liveCombo)

        vm.answer(correct = true)
        assertEquals(1, vm.active().liveCombo)
        assertEquals(1, vm.active().longestCombo)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)

        vm.answer(correct = true)
        assertEquals(2, vm.active().liveCombo)
        assertEquals(2, vm.active().longestCombo)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)

        vm.answer(correct = false)
        assertEquals(0, vm.active().liveCombo)
        // longestCombo never resets: it keeps the round's best streak so far.
        assertEquals(2, vm.active().longestCombo)
    }

    // --- Discarding the open question ---

    @Test
    fun `a question still open when time runs out is never counted as asked`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        vm.answer(correct = true)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)
        assertEquals(1, vm.active().askedCount)
        val openQuestionTarget = vm.active().question!!.targetCode

        // Let the clock run out without answering the second, now-open question.
        advanceSchedulerTo(FlagSpeedGameMode.ROUND_DURATION_SECONDS * 1_000L)

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertFalse(finished.gaveUp)
        assertEquals(1, finished.askedCount)
        assertEquals(1, finished.correctCount)
        assertEquals(0, finished.incorrectGuesses)
        // The still-open second question was shown but never answered: never added to askedCodes.
        assertFalse(openQuestionTarget in finished.askedCodes)
        // correct == asked - incorrectGuesses always holds.
        assertEquals(finished.askedCount - finished.incorrectGuesses, finished.correctCount)
    }

    @Test
    fun `giving up discards the open question entirely - it is never counted as asked`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        vm.answer(correct = true)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)
        assertEquals(1, vm.active().askedCount)
        assertNotNull("a second question should now be open, unanswered", vm.active().question)

        vm.onGiveUp()
        runCurrent()

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertTrue(finished.gaveUp)
        assertEquals(1, finished.askedCount)
        assertEquals(1, finished.correctCount)
        assertEquals(finished.askedCount - finished.incorrectGuesses, finished.correctCount)
    }

    // --- The clock ends the round even mid-feedback ---

    @Test
    fun `the round ends at the 60-second tick even while a pick's feedback is still pending`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        // Burn through time close to the deadline without answering anything yet.
        advanceSchedulerTo(FlagSpeedGameMode.ROUND_DURATION_SECONDS * 1_000L - 500L)
        assertFalse(vm.active().isComplete)

        vm.answer(correct = true)
        assertTrue("feedback should be showing, with the next draw scheduled 900ms later", vm.active().pendingAdvance)
        assertEquals(1, vm.active().askedCount)

        // Crosses the 60s boundary well before the 900ms feedback delay would otherwise fire.
        advanceBy(500L)

        val finished = vm.active()
        assertTrue("the ticker must end the round the instant 60s elapses, not wait for feedback", finished.isComplete)
        assertFalse(finished.gaveUp)
        // The pick made just before time-up still counts: it was answered, not left open.
        assertEquals(1, finished.askedCount)
        assertEquals(1, finished.correctCount)
    }

    // --- Backgrounding / resuming ---

    @Test
    fun `backgrounding pauses the timer so remaining time does not change, and resuming continues it`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        advanceSchedulerTo(5_000L)
        val remainingBeforeBackground = vm.remainingSeconds.value

        vm.onBackgrounded()
        runCurrent()
        assertTrue(vm.active().paused)

        // Plenty of real time passes while backgrounded; the round must not advance at all.
        advanceSchedulerTo(30_000L)
        assertEquals(remainingBeforeBackground, vm.remainingSeconds.value)
        assertTrue(vm.active().paused)

        vm.onResume()
        runCurrent()
        assertFalse(vm.active().paused)
        assertEquals(remainingBeforeBackground, vm.remainingSeconds.value)

        // The clock counts again from exactly where it left off.
        advanceBy(1_000L)
        assertEquals(remainingBeforeBackground - 1, vm.remainingSeconds.value)
    }

    @Test
    fun `backgrounding while a pick's feedback is pending re-arms the delayed advance on resume`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        vm.answer(correct = true)
        assertTrue(vm.active().pendingAdvance)

        vm.onBackgrounded()
        runCurrent()
        assertTrue(vm.active().paused)

        // The scheduled advance must not fire while backgrounded, however long this takes.
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS * 10)
        assertTrue(vm.active().pendingAdvance)
        assertTrue(vm.active().paused)

        vm.onResume()
        runCurrent()
        assertFalse(vm.active().paused)
        assertTrue("resume re-arms the catch-up advance rather than leaving it stuck", vm.active().pendingAdvance)

        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)
        assertFalse(vm.active().pendingAdvance)
        assertNotNull(vm.active().question)
    }

    @Test
    fun `backgrounding before the asset load finishes starts the round paused`() {
        val gate = CompletableDeferred<Unit>()
        val gatedRepository = object : FlagLookalikeRepository {
            override suspend fun sets(): List<FlagLookalikeSet> {
                gate.await()
                return listOf(setAB)
            }
        }
        val vm = FlagSpeedGameViewModel(
            savedStateHandle = routeHandle(Difficulty.EASY),
            countryRepository = countryRepository,
            lookalikeRepository = gatedRepository,
            completeQuiz = completeQuizUseCase(flagSpeedMode()),
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(1))
        )
        runCurrent()
        assertEquals(FlagSpeedUiState.Loading, vm.uiState.value)

        // Backgrounded while the load is still in flight: there is no Active state to pause yet.
        vm.onBackgrounded()
        gate.complete(Unit)
        runCurrent()

        val state = vm.active()
        assertTrue("a round backgrounded before its load finished must start paused, not running unseen", state.paused)
    }

    // --- Process death restore ---

    @Test
    fun `process death while paused restores the same progress, still paused`() {
        val handle = routeHandle(Difficulty.EASY)
        val vm = viewModel(handle)
        vm.answer(correct = true)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)
        vm.onBackgrounded()
        runCurrent()
        val before = vm.active()

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        val after = vm2.active()

        assertEquals(before.askedCodes, after.askedCodes)
        assertEquals(before.correctCodes, after.correctCodes)
        assertEquals(before.liveCombo, after.liveCombo)
        assertEquals(before.longestCombo, after.longestCombo)
        assertTrue(after.paused)
        // Restore rebuilds the exact same question it was showing (FlagSpeedSavedState's contract).
        assertEquals(before.lastDrawSetId, after.lastDrawSetId)
        assertEquals(before.lastDrawTargetCode, after.lastDrawTargetCode)
        assertEquals(before.lastDrawTargetCode, after.question?.targetCode)
    }

    @Test
    fun `process death with a pick's feedback still pending resolves into a fresh question, still paused`() {
        val handle = routeHandle(Difficulty.EASY)
        val vm = viewModel(handle)
        vm.answer(correct = true)
        assertTrue(vm.active().pendingAdvance)
        val askedBefore = vm.active().askedCodes
        val correctBefore = vm.active().correctCodes

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        val after = vm2.active()

        // The answered pick itself is preserved; only the stale "feedback showing" bit is resolved.
        assertEquals(askedBefore, after.askedCodes)
        assertEquals(correctBefore, after.correctCodes)
        assertFalse(after.pendingAdvance)
        assertNull(after.feedback)
        assertNotNull(after.question)
        assertTrue("an unfinished restored round always comes back paused (D37)", after.paused)
    }

    @Test
    fun `a screen recreated after the round already finished does not record the result again`() {
        val mode = flagSpeedMode()
        val completeQuiz = completeQuizUseCase(mode)
        val handle = routeHandle(Difficulty.EASY)
        val vm = viewModel(handle, mode, completeQuiz)
        vm.answer(correct = true)
        advanceBy(FlagSpeedGameViewModel.CHOICE_FEEDBACK_MILLIS)
        vm.onGiveUp()
        runCurrent()
        assertEquals(1, completedQuizzes.saveCount)
        val firstResult = completedQuizzes.stored

        val restoredHandle = afterProcessDeath(handle)
        viewModel(restoredHandle, mode, completeQuiz)
        runCurrent()

        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(firstResult, completedQuizzes.stored)
    }

    // --- Defensive: a draw that cannot resolve must never stall the round silently ---

    @Test
    fun `a draw whose codes cannot resolve to real countries ends the round instead of stalling`() {
        val brokenSet = FlagLookalikeSet(
            id = "broken",
            countries = listOf("AAA", "ZZZ"),
            tier = 1,
            visibility = FlagLookalikeVisibility.CLEAR,
            explanation = "e",
            cues = emptyMap()
        )
        coEvery { lookalikeRepository.sets() } returns listOf(brokenSet)
        // "ZZZ" never resolves to a real Country, so this set can never produce a usable question.
        every { countryRepository.getAllCountries() } returns flowOf(listOf(countryA))

        val vm = viewModel(routeHandle(Difficulty.EASY))

        val state = vm.active()
        assertTrue(
            "a set with no resolvable distractor must end the round, never leave pendingAdvance stuck forever",
            state.isComplete
        )
        assertEquals(1, completedQuizzes.saveCount)
    }
}
