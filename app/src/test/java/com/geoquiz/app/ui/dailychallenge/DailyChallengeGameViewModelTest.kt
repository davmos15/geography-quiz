package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeDate
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGenerator
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestionKind
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class DailyChallengeGameViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = MonotonicClock { dispatcher.scheduler.currentTime }

    /** 40 synthetic countries; enough for a full 10-question round with no duplicates. */
    private val countries: List<Country> = (1..40).map { i ->
        Country(
            code = "C%02d".format(i),
            name = "Country $i",
            officialName = "Country $i",
            region = if (i % 2 == 0) "Europe" else "Asia",
            subregion = if (i % 2 == 0) "Northern Europe" else "Eastern Asia",
            nameLength = 9,
            capital = "Capital $i"
        )
    }

    // A fresh round (empty SavedStateHandle) resolves its date via DailyChallengeDate.today()
    // inside the ViewModel itself (see DailyChallengeGameViewModel.init) - this fixture must use
    // the exact same source, not a hardcoded literal, or every assertion below would only agree
    // with the ViewModel by coincidence on the one real calendar date it happened to be written
    // on (code review, task 8.1: a hardcoded "2026-10-16" literal here passed only because that
    // was today's actual date when the test was run).
    private val date = DailyChallengeDate.today()

    /** The exact round this date/country list produces, for the tests to drive deliberately. */
    private val questions = DailyChallengeGenerator.generate(date, countries)

    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
    }

    /** Correct only when the input matches the single target in the synthetic validation state. */
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val validateCapitalAnswer = mockk<ValidateCapitalAnswerUseCase>()

    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>(relaxed = true)
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()

    private fun dailyChallengeMode() = DailyChallengeGameMode()

    private fun completeQuizUseCase(mode: DailyChallengeGameMode) = CompleteQuizUseCase(
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
        coEvery { validateAnswer(any(), any(), any()) } answers {
            val input = firstArg<String>()
            val state = secondArg<QuizState>()
            val target = state.quiz.countries.first()
            if (input.trim().equals(target.name, ignoreCase = true)) {
                AnswerResult.Correct(target.name)
            } else {
                AnswerResult.Incorrect
            }
        }
        coEvery { validateCapitalAnswer(any(), any(), any()) } answers {
            val input = firstArg<String>()
            val state = secondArg<QuizState>()
            val target = state.quiz.countries.first()
            if (input.trim().equals(target.capital, ignoreCase = true)) {
                AnswerResult.Correct(target.name)
            } else {
                AnswerResult.Incorrect
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun advanceByFeedbackDelay() {
        dispatcher.scheduler.advanceTimeBy(DailyChallengeGameViewModel.FEEDBACK_MILLIS)
        runCurrent()
    }

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        mode: DailyChallengeGameMode = dailyChallengeMode(),
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode)
    ): DailyChallengeGameViewModel {
        val vm = DailyChallengeGameViewModel(
            savedStateHandle = handle,
            countryRepository = countryRepository,
            validateAnswer = validateAnswer,
            validateCapitalAnswer = validateCapitalAnswer,
            completeQuiz = completeQuiz,
            quizHistoryRepository = quizHistoryRepository,
            clock = clock
        )
        runCurrent()
        return vm
    }

    private fun DailyChallengeGameViewModel.active(): DailyChallengeRoundState =
        (uiState.value as DailyChallengeUiState.Active).state

    /** Submits one correct attempt for the question on screen, then lets its feedback elapse. */
    private fun answerCurrentCorrectly(vm: DailyChallengeGameViewModel) {
        val question = vm.active().currentQuestion!!
        when (question.kind) {
            DailyChallengeQuestionKind.TYPED_COUNTRY -> {
                vm.onInputChange(question.target.name)
                vm.onSubmitTyped()
            }
            DailyChallengeQuestionKind.TYPED_CAPITAL -> {
                vm.onInputChange(question.target.capital)
                vm.onSubmitTyped()
            }
            DailyChallengeQuestionKind.CHOICE_FLAG, DailyChallengeQuestionKind.CHOICE_CAPITAL ->
                vm.onChoiceSelected(question.choice!!.targetCode)
        }
        runCurrent()
        advanceByFeedbackDelay()
    }

    @Test
    fun `a fresh round loads the ten questions generated for today's date`() {
        val vm = viewModel()
        val state = vm.active()

        assertEquals(questions.map { it.target.code to it.kind }, state.questions.map { it.target.code to it.kind })
        assertEquals(0, state.currentIndex)
        assertFalse(state.isComplete)
    }

    @Test
    fun `answering all ten questions correctly completes the round and records it exactly once`() {
        val vm = viewModel()

        repeat(10) { answerCurrentCorrectly(vm) }

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertEquals(10, finished.correctCodes.size)
        assertEquals(0, finished.incorrectGuesses)
        assertEquals(1, completedQuizzes.saveCount)
        val stored = completedQuizzes.stored!!
        assertEquals(DailyChallengeGameMode.MODE_ID, stored.quizModeId)
        assertEquals("daily", stored.categoryType)
        assertEquals(date, stored.categoryValue)
        assertTrue("every question answered correctly is a perfect round", stored.perfectBonus)
        assertEquals(10, stored.correct)
        assertEquals(10, stored.total)
    }

    @Test
    fun `an incorrect typed attempt still advances the round and counts as a wrong guess`() {
        val vm = viewModel()
        // The kind mix is shuffled per date (DailyChallengeGenerator), so the round may not start
        // on a typed question: answer up to the first one.
        val typed = setOf(DailyChallengeQuestionKind.TYPED_COUNTRY, DailyChallengeQuestionKind.TYPED_CAPITAL)
        var index = 0
        while (vm.active().currentQuestion!!.kind !in typed) {
            answerCurrentCorrectly(vm)
            index++
            check(index < 10) { "fixture has no typed question" }
        }

        vm.onInputChange("definitely wrong")
        vm.onSubmitTyped()
        runCurrent()
        advanceByFeedbackDelay()

        val after = vm.active()
        assertEquals(index + 1, after.currentIndex)
        assertEquals(1, after.incorrectGuesses)
        assertEquals(index, after.correctCodes.size)
    }

    @Test
    fun `a near miss does not advance and keeps the typed input for editing`() {
        val vm = viewModel()
        // This fixture's round is not guaranteed to start on a TYPED_COUNTRY question (the kind
        // mix is shuffled per date, see DailyChallengeGenerator), so advance to whichever one is
        // first: a near miss on TYPED_CAPITAL would exercise validateCapitalAnswer instead, not
        // the validateAnswer mock this test replaces below.
        var index = 0
        while (vm.active().currentQuestion!!.kind != DailyChallengeQuestionKind.TYPED_COUNTRY) {
            answerCurrentCorrectly(vm)
            index++
            check(index < 10) { "fixture has no TYPED_COUNTRY question" }
        }
        val before = vm.active()

        // Replace the mock's answer just for this one call: a near miss never consumes the attempt.
        coEvery { validateAnswer(any(), any(), any()) } returns AnswerResult.NearMiss

        vm.onInputChange("clos")
        vm.onSubmitTyped()
        runCurrent()

        val after = vm.active()
        assertEquals(before.currentIndex, after.currentIndex)
        assertEquals(AnswerResult.NearMiss, after.lastAnswerResult)
        assertFalse(after.pendingAdvance)
        assertEquals("clos", after.currentInput)
    }

    @Test
    fun `a choice question is judged by its own targetCode, never the typed validators`() {
        val vm = viewModel()
        var index = 0
        while (vm.active().currentQuestion!!.kind != DailyChallengeQuestionKind.CHOICE_FLAG &&
            vm.active().currentQuestion!!.kind != DailyChallengeQuestionKind.CHOICE_CAPITAL
        ) {
            answerCurrentCorrectly(vm)
            index++
            check(index < 10) { "fixture has no choice question" }
        }
        val before = vm.active()
        val question = before.currentQuestion!!
        val wrongOption = question.choice!!.options.first { it.code != question.choice!!.targetCode }

        vm.onChoiceSelected(wrongOption.code)
        runCurrent()

        val after = vm.active()
        // The attempt is recorded immediately (currentIndex, the count asked, advances right
        // away - see the class doc), but the screen must keep showing this question's own
        // content and feedback until the feedback delay elapses (displayIndex lags behind).
        assertEquals(before.currentIndex + 1, after.currentIndex)
        assertEquals(before.currentIndex, after.displayIndex)
        assertEquals(question, after.currentQuestion)
        assertEquals(wrongOption.code, after.choiceFeedback?.selectedCode)
        assertFalse(after.choiceFeedback!!.isCorrect)
        assertTrue(after.pendingAdvance)
    }

    @Test
    fun `giving up mid-round ends it early and only the questions actually asked are recorded`() {
        val vm = viewModel()
        answerCurrentCorrectly(vm)
        answerCurrentCorrectly(vm)

        vm.onGiveUp()
        runCurrent()

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertTrue(finished.gaveUp)
        val stored = completedQuizzes.stored!!
        assertEquals(2, stored.total)
        assertEquals(2, stored.correct)
    }

    @Test
    fun `process death mid-round restores asked codes, correct codes and incorrect count`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        answerCurrentCorrectly(vm)
        vm.onInputChange("definitely wrong")
        vm.onSubmitTyped()
        runCurrent()
        advanceByFeedbackDelay()
        val before = vm.active()

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        val after = vm2.active()

        assertEquals(before.askedCodes, after.askedCodes)
        assertEquals(before.correctCodes, after.correctCodes)
        assertEquals(before.incorrectGuesses, after.incorrectGuesses)
        assertEquals(before.currentIndex, after.currentIndex)
        assertTrue("an unfinished restored round comes back paused", after.paused)
    }

    @Test
    fun `a screen recreated after the round already finished records the result only once`() {
        val mode = dailyChallengeMode()
        val completeQuiz = completeQuizUseCase(mode)
        val handle = SavedStateHandle()
        val vm = viewModel(handle, mode, completeQuiz)
        repeat(10) { answerCurrentCorrectly(vm) }
        assertEquals(1, completedQuizzes.saveCount)
        val firstResult = completedQuizzes.stored

        val restoredHandle = afterProcessDeath(handle)
        viewModel(restoredHandle, mode, completeQuiz)
        runCurrent()

        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(firstResult, completedQuizzes.stored)
    }

    @Test
    fun `onBackgrounded pauses and onResume continues, re-arming any pending advance`() {
        val vm = viewModel()
        answerCurrentCorrectly(vm) // first question done, round running on the second

        val question = vm.active().currentQuestion!!
        when (question.kind) {
            DailyChallengeQuestionKind.TYPED_COUNTRY -> {
                vm.onInputChange(question.target.name)
                vm.onSubmitTyped()
            }
            DailyChallengeQuestionKind.TYPED_CAPITAL -> {
                vm.onInputChange(question.target.capital)
                vm.onSubmitTyped()
            }
            DailyChallengeQuestionKind.CHOICE_FLAG, DailyChallengeQuestionKind.CHOICE_CAPITAL ->
                vm.onChoiceSelected(question.choice!!.targetCode)
        }
        runCurrent()
        // Feedback is pending (not yet advanced) when the screen backgrounds.
        assertTrue(vm.active().pendingAdvance)

        vm.onBackgrounded()
        assertTrue(vm.active().paused)

        vm.onResume()
        assertFalse(vm.active().paused)
        advanceByFeedbackDelay()

        assertEquals(2, vm.active().currentIndex)
        assertNull((vm.uiState.value as? DailyChallengeUiState.Active)?.state?.choiceFeedback)
    }

    // Task 8.2's hard one-attempt-per-day guard (D54).

    @Test
    fun `a fresh start for a date already recorded shows AlreadyPlayed and never builds a round`() {
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", date)
        } returns true

        val vm = viewModel()

        assertEquals(DailyChallengeUiState.AlreadyPlayed, vm.uiState.value)
    }

    @Test
    fun `the guard checks history before ever fetching countries or building questions`() {
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", date)
        } returns true

        viewModel()

        io.mockk.verify(exactly = 0) { countryRepository.getAllCountries() }
    }

    @Test
    fun `restoring an in-progress round is never blocked, even for a date already recorded`() {
        // Build a fresh (unblocked) round first, to capture a real in-progress snapshot.
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        answerCurrentCorrectly(vm)
        val before = vm.active()

        // Now simulate that this exact date has since been recorded elsewhere (e.g. a retry of
        // this same brief's scope-cut double-completion path, D54) - a restore must still work.
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", date)
        } returns true

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        val after = vm2.active()

        assertEquals(before.askedCodes, after.askedCodes)
        assertTrue("an unfinished restored round comes back paused", after.paused)
    }

    @Test
    fun `a fresh start for a date not yet recorded builds a round normally`() {
        coEvery {
            quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", date)
        } returns false

        val vm = viewModel()

        assertTrue(vm.uiState.value is DailyChallengeUiState.Active)
        coVerify { quizHistoryRepository.hasCompletedCategory(DailyChallengeGameMode.MODE_ID, "daily", date) }
    }
}
