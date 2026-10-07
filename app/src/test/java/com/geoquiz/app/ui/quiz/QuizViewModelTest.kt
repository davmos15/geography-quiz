package com.geoquiz.app.ui.quiz

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForCapitalQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.coVerify
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

/**
 * Process-death round trips for [QuizViewModel]: a second ViewModel built from what the first one
 * left in its [SavedStateHandle] stands in for the screen recreated after process death or
 * "Don't keep activities". The real [CompleteQuizUseCase] runs against an in-memory result store
 * so "recorded once" covers both.
 *
 * Coroutines run on a [StandardTestDispatcher] driven with `runCurrent()`; virtual time is never
 * advanced, so the timer's display ticker never fires (the timer reads [FakeClock] directly).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
        fun advance(millis: Long) { now += millis }
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val getCountriesForCapitalQuiz = mockk<GetCountriesForCapitalQuizUseCase>()
    private val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val validateCapitalAnswer = mockk<ValidateCapitalAnswerUseCase>()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val adManager = mockk<AdManager>(relaxed = true)

    private val completedQuizzes = FakeCompletedQuizRepository()
    private val achievementRepository = mockk<AchievementRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)
    private val completeQuiz = CompleteQuizUseCase(
        calculateScore = CalculateScoreUseCase(),
        completedQuizRepository = completedQuizzes,
        savedQuizRepository = savedQuizRepository,
        achievementRepository = achievementRepository,
        quizHistoryRepository = quizHistoryRepository,
        playGamesService = playGames
    )

    private val achievement = Achievement.entries.first()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }

        coEvery { getCountriesForQuiz(any()) } returns TestQuizData.THREE
        coEvery { validateAnswer(any(), any(), any()) } answers {
            val input = firstArg<String>()
            val state = secondArg<QuizState>()
            val country = state.quiz.countries.find { it.name.equals(input, ignoreCase = true) }
            when {
                country == null -> AnswerResult.Incorrect
                country.code in state.answeredCountries -> AnswerResult.AlreadyAnswered
                else -> AnswerResult.Correct(country.name)
            }
        }
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.showFlags } returns flowOf(false)
        every { settingsRepository.showCountryHint } returns flowOf(false)
        every { settingsRepository.hardMode } returns flowOf(false)
        coEvery { savedQuizRepository.getSavedQuiz() } returns null
        coEvery { achievementRepository.onQuizCompleted(any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(achievement)
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun routeHandle() = SavedStateHandle(
        mapOf("quizMode" to "countries", "categoryType" to "all", "categoryValue" to "_")
    )

    /** What survives process death: a new handle holding only the saved values. */
    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(handle: SavedStateHandle): QuizViewModel {
        val vm = QuizViewModel(
            savedStateHandle = handle,
            getCountriesForQuiz = getCountriesForQuiz,
            getCountriesForCapitalQuiz = getCountriesForCapitalQuiz,
            getCountriesForFlagQuiz = getCountriesForFlagQuiz,
            validateAnswer = validateAnswer,
            validateCapitalAnswer = validateCapitalAnswer,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            savedQuizRepository = savedQuizRepository,
            adManager = adManager,
            clock = clock
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun QuizViewModel.answer(input: String) {
        onInputChange(input)
        onSubmitAnswer()
        runCurrent()
    }

    private fun QuizViewModel.quizState(): QuizState =
        (uiState.value as QuizUiState.Active).state

    @Test
    fun `answers, guesses, input and time survive process death and the quiz comes back paused`() {
        val handle = routeHandle()
        val first = viewModel(handle)
        first.answer("France")
        first.answer("Narnia")
        first.onInputChange("Germ")
        clock.advance(7_500)
        first.onBackgrounded() // ON_STOP always runs before the process is killed.
        runCurrent()

        val second = viewModel(afterProcessDeath(handle))

        val state = second.quizState()
        assertEquals(setOf("FRA"), state.answeredCountries)
        assertEquals(1, state.incorrectGuesses)
        assertEquals(listOf("Narnia"), state.incorrectGuessStrings)
        assertEquals("Germ", state.currentInput)
        assertTrue(state.isPaused)
        assertFalse(state.isComplete)
        assertEquals(7, second.timerSeconds.value)
        // The restored timer stays paused until the player resumes.
        clock.advance(10_000)
        assertEquals(7, second.timerSeconds.value)
        second.togglePause()
        clock.advance(2_000)
        second.onBackgrounded()
        assertEquals(9, second.timerSeconds.value)
    }

    @Test
    fun `saved state wins over the Room resume save and clears it`() {
        val handle = routeHandle()
        val first = viewModel(handle)
        first.answer("France")
        first.onBackgrounded()
        runCurrent()
        coEvery { savedQuizRepository.getSavedQuiz() } returns SavedQuizEntity(
            categoryType = "all",
            categoryValue = "_",
            answeredCountryCodes = "[\"DEU\"]",
            timeElapsedSeconds = 99,
            savedAtMillis = 0L,
            quizMode = "countries"
        )
        every { savedQuizRepository.parseAnsweredCodes(any()) } returns setOf("DEU")

        val second = viewModel(afterProcessDeath(handle))

        assertEquals(setOf("FRA"), second.quizState().answeredCountries)
        coVerify(atLeast = 1) { savedQuizRepository.clearSavedQuiz() }
    }

    @Test
    fun `without saved state the Room resume save is used as before`() {
        coEvery { savedQuizRepository.getSavedQuiz() } returns SavedQuizEntity(
            categoryType = "all",
            categoryValue = "_",
            answeredCountryCodes = "[\"DEU\"]",
            timeElapsedSeconds = 12,
            savedAtMillis = 0L,
            quizMode = "countries"
        )
        every { savedQuizRepository.parseAnsweredCodes(any()) } returns setOf("DEU")

        val vm = viewModel(routeHandle())

        val state = vm.quizState()
        assertEquals(setOf("DEU"), state.answeredCountries)
        assertFalse(state.isPaused)
        assertEquals(12, vm.timerSeconds.value)
        coVerify(exactly = 1) { savedQuizRepository.clearSavedQuiz() }
    }

    @Test
    fun `finishing records the quiz once and recreation reuses the same result`() {
        val handle = routeHandle()
        val first = viewModel(handle)
        TestQuizData.THREE.forEach { first.answer(it.name) }

        val completion = first.completion.value
        assertNotNull(completion)
        completion!!
        assertEquals(QuizCompletion.Step.SHOW_INTERSTITIAL, completion.step)
        assertEquals(completion.resultId, completedQuizzes.stored?.id)
        assertEquals(3, completedQuizzes.stored?.correct)
        assertEquals(listOf(achievement), first.newAchievements.value)

        // Process death after completion, before or during the interstitial.
        val second = viewModel(afterProcessDeath(handle))

        val restored = second.completion.value!!
        assertEquals(completion.resultId, restored.resultId)
        // Straight to Results: no second interstitial.
        assertEquals(QuizCompletion.Step.NAVIGATE, restored.step)
        assertTrue(second.quizState().isComplete)
        assertTrue(second.newAchievements.value.isEmpty())
        assertEquals(1, completedQuizzes.saveCount)
        coVerify(exactly = 1) {
            quizHistoryRepository.recordQuizResult(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) {
            achievementRepository.onQuizCompleted(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a quiz finished just before process death is recorded once on restore`() {
        // The id was saved but the process died before the result was stored.
        val handle = routeHandle()
        val first = viewModel(handle)
        first.answer("France")
        val saved = QuizSavedState(handle)
        saved.save(first.quizState().copy(isComplete = true), elapsedMillis = 3_000L)
        saved.resultId = "pending-id"

        val second = viewModel(afterProcessDeath(handle))

        assertEquals("pending-id", second.completion.value?.resultId)
        assertEquals(QuizCompletion.Step.NAVIGATE, second.completion.value?.step)
        assertEquals("pending-id", completedQuizzes.stored?.id)
        assertEquals(3, completedQuizzes.stored?.timeSeconds)
        coVerify(exactly = 1) {
            quizHistoryRepository.recordQuizResult(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `giving up records the answers so far`() {
        val vm = viewModel(routeHandle())
        vm.answer("Austria")

        vm.onGiveUp()
        runCurrent()

        assertNotNull(vm.completion.value)
        assertEquals(listOf("AUT"), completedQuizzes.stored?.answeredCodes)
        assertEquals(1, completedQuizzes.stored?.correct)
        assertEquals(3, completedQuizzes.stored?.total)
    }

    @Test
    fun `completion steps go from interstitial to navigation`() {
        val vm = viewModel(routeHandle())
        vm.onGiveUp()
        runCurrent()

        vm.onInterstitialShowing()
        assertEquals(QuizCompletion.Step.SHOWING_INTERSTITIAL, vm.completion.value?.step)
        vm.onInterstitialFinished()
        assertEquals(QuizCompletion.Step.NAVIGATE, vm.completion.value?.step)
        vm.onNavigatedToResults()
        assertEquals(QuizCompletion.Step.DONE, vm.completion.value?.step)
    }

    @Test
    fun `a fresh quiz has no completion and nothing recorded`() {
        val vm = viewModel(routeHandle())

        assertNull(vm.completion.value)
        assertNull(completedQuizzes.stored)
        assertFalse(vm.quizState().isPaused)
    }
}
