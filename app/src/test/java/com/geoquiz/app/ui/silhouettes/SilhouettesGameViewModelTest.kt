package com.geoquiz.app.ui.silhouettes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.OutlineNeighbour
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountryOutlineUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SilhouettesGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")
    private val italy = country("ITA", "Italy", "Rome")
    private val spain = country("ESP", "Spain", "Madrid")
    private val austria = country("AUT", "Austria", "Vienna")
    private val allCountries = listOf(france, germany, italy, spain, austria)

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(allCountries)
    }
    private val getCountryOutline = mockk<GetCountryOutlineUseCase>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>(relaxed = true)
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    /** The round always asks about exactly [pool] (fewer than 15, so the whole pool is asked). */
    private fun silhouettesMode(pool: List<Country>) = SilhouettesGameMode(
        CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
        validateAnswer,
        CalculateScoreUseCase(),
        QuizRandom(Random(3))
    ).also { coEvery { getCountriesForQuiz(any()) } returns pool }

    private fun completeQuizUseCase(mode: SilhouettesGameMode) = CompleteQuizUseCase(
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
        mockkStatic(Uri::class)
        every { Uri.decode(any()) } answers { firstArg() }
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        // "France"/"Germany"/"Italy"/"Spain" resolve to that real country; anything else is either
        // a near miss ("typo") or not recognised at all ("gibberish" and everything else).
        coEvery { validateAnswer(any(), any(), any()) } answers {
            when (firstArg<String>()) {
                "France" -> AnswerResult.Correct("France")
                "Germany" -> AnswerResult.Correct("Germany")
                "Italy" -> AnswerResult.Correct("Italy")
                "Spain" -> AnswerResult.Correct("Spain")
                "typo" -> AnswerResult.NearMiss
                else -> AnswerResult.Incorrect
            }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun routeHandle(
        categoryType: String = "all",
        categoryValue: String = "_",
        difficulty: Difficulty? = null
    ) = SavedStateHandle(
        buildMap<String, Any?> {
            put("categoryType", categoryType)
            put("categoryValue", categoryValue)
            if (difficulty != null) put(SilhouettesGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle,
        mode: SilhouettesGameMode,
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode)
    ): SilhouettesGameViewModel {
        val vm = SilhouettesGameViewModel(
            savedStateHandle = handle,
            silhouettesGameMode = mode,
            countryRepository = countryRepository,
            getCountryOutline = getCountryOutline,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(9))
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advanceTime(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        dispatcher.scheduler.runCurrent()
    }

    private fun SilhouettesGameViewModel.active(): SilhouetteState =
        (uiState.value as SilhouettesUiState.Active).state

    private fun SilhouettesGameViewModel.type(input: String) {
        onInputChange(input)
        onSubmit()
        runCurrent()
    }

    @Test
    fun `a correct typed answer is accepted and moves to the next silhouette after a short pause`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.type("France")
        var state = vm.active()
        assertEquals(setOf("FRA"), state.answeredCodes)
        assertTrue(state.feedback is SilhouetteFeedback.Correct)
        assertTrue(state.isAdvancing)

        advanceTime(SilhouettesGameViewModel.ADVANCE_DELAY_MILLIS)
        state = vm.active()
        assertTrue("the only silhouette is done: the round finishes", state.isComplete)
        assertEquals(1, completedQuizzes.saveCount)
        val stored = completedQuizzes.stored!!
        assertEquals("silhouettes", stored.quizModeId)
        assertEquals(listOf("FRA"), stored.answeredCodes)
        assertTrue(stored.missedCodes().isEmpty())
    }

    @Test
    fun `a wrong but real country guess costs one life and is never accepted`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.type("Germany")

        val state = vm.active()
        assertEquals(emptySet<String>(), state.answeredCodes)
        assertEquals(2, state.lives)
        assertEquals(listOf("Germany"), state.incorrectGuessStrings)
        assertEquals(listOf("Germany"), state.wrongGuessNamesForCurrent)
        assertTrue(state.feedback is SilhouetteFeedback.WrongCountry)
        assertFalse("a wrong guess never advances on its own", state.isAdvancing)
    }

    @Test
    fun `guessing the same wrong country again costs nothing more`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.type("Germany")
        vm.type("Germany")

        val state = vm.active()
        assertEquals(2, state.lives)
        assertEquals(listOf("Germany"), state.incorrectGuessStrings)
        assertEquals(listOf("Germany"), state.wrongGuessNamesForCurrent)
    }

    @Test
    fun `a near miss and an unrecognised answer cost no life`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.type("typo")
        assertEquals(3, vm.active().lives)
        assertTrue(vm.active().feedback is SilhouetteFeedback.NearMiss)

        vm.type("gibberish")
        assertEquals(3, vm.active().lives)
        assertTrue(vm.active().feedback is SilhouetteFeedback.NotRecognised)
        assertTrue("no wrong-country chip for either", vm.active().wrongGuessNamesForCurrent.isEmpty())
    }

    @Test
    fun `pass reveals the name, costs no life and counts the silhouette as missed`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.onPass()
        runCurrent()
        var state = vm.active()
        assertTrue(state.feedback is SilhouetteFeedback.Revealed)
        assertEquals(3, state.lives)

        advanceTime(SilhouettesGameViewModel.REVEAL_DELAY_MILLIS)
        state = vm.active()
        assertTrue(state.isComplete)
        val stored = completedQuizzes.stored!!
        assertTrue(stored.answeredCodes.isEmpty())
        assertEquals(listOf("FRA"), stored.missedCodes())
    }

    @Test
    fun `three different wrong guesses exhaust lives and end the round early`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.type("Germany")
        assertEquals(2, vm.active().lives)
        vm.type("Italy")
        assertEquals(1, vm.active().lives)
        vm.type("Spain")

        val state = vm.active()
        assertEquals(0, state.lives)
        assertTrue("lives gone ends the round immediately, no further pause needed", state.isComplete)
        val stored = completedQuizzes.stored!!
        assertTrue(stored.answeredCodes.isEmpty())
        assertEquals(listOf("FRA"), stored.missedCodes())
        assertEquals(3, stored.incorrectGuesses)
    }

    @Test
    fun `each hint type is usable once per silhouette and recorded for scoring`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)

        vm.onHint(HintType.SHOW_REGION)
        vm.onHint(HintType.SHOW_REGION) // ignored: already used on this silhouette
        vm.onHint(HintType.FIRST_LETTER)
        vm.onHint(HintType.SHOW_NEIGHBOURS)

        val state = vm.active()
        assertEquals(listOf(HintType.SHOW_REGION, HintType.FIRST_LETTER, HintType.SHOW_NEIGHBOURS), state.hintsUsed)
        assertEquals(setOf(HintType.SHOW_REGION, HintType.FIRST_LETTER, HintType.SHOW_NEIGHBOURS), state.hintsUsedForCurrent)
        assertTrue(state.showNeighbours)
    }

    @Test
    fun `Easy is four choices, a wrong pick costs a life and both options show their result`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY), mode)

        val question = vm.active().choice!!
        assertEquals(4, question.options.size)
        val wrongOption = question.options.first { it.code != question.targetCode }

        vm.onChoiceSelected(wrongOption.code)
        runCurrent()

        val state = vm.active()
        assertEquals(2, state.lives)
        assertEquals(wrongOption.code, state.choiceFeedback?.selectedCode)
        assertFalse(state.choiceFeedback!!.isCorrect)

        advanceTime(SilhouettesGameViewModel.CHOICE_FEEDBACK_MILLIS)
        assertTrue("the only item is done: the round finishes", vm.active().isComplete)
    }

    @Test
    fun `a correct Easy pick is accepted and advances like a typed correct answer`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY), mode)
        val question = vm.active().choice!!

        vm.onChoiceSelected(question.targetCode)
        runCurrent()

        val state = vm.active()
        assertEquals(setOf("FRA"), state.answeredCodes)
        assertTrue(state.choiceFeedback!!.isCorrect)
    }

    @Test
    fun `completion records the round under the silhouettes mode id with the right answered and missed codes`() {
        val mode = silhouettesMode(listOf(france, germany))
        val vm = viewModel(routeHandle(), mode)

        val first = vm.active().current!!
        vm.type(first.name)
        advanceTime(SilhouettesGameViewModel.ADVANCE_DELAY_MILLIS)
        val second = vm.active().current!!
        vm.onPass()
        runCurrent()
        advanceTime(SilhouettesGameViewModel.REVEAL_DELAY_MILLIS)

        assertTrue(vm.active().isComplete)
        val stored = completedQuizzes.stored!!
        assertEquals("silhouettes", stored.quizModeId)
        assertEquals(setOf(first.code, second.code), stored.countryCodes.toSet())
        assertEquals(listOf(first.code), stored.answeredCodes)
        assertEquals(listOf(second.code), stored.missedCodes())
    }

    @Test
    fun `progress, lives, hints and guesses survive process death (SavedStateHandle restore)`() {
        val mode = silhouettesMode(listOf(france))
        val handle = routeHandle()
        val first = viewModel(handle, mode)
        first.type("Germany")
        first.onHint(HintType.FIRST_LETTER)
        clock.now += 5_000

        val second = viewModel(afterProcessDeath(handle), mode)

        val state = second.active()
        assertEquals("FRA", state.current?.code)
        assertEquals(2, state.lives)
        assertEquals(listOf("Germany"), state.incorrectGuessStrings)
        assertEquals(listOf("Germany"), state.wrongGuessNamesForCurrent)
        assertEquals(setOf(HintType.FIRST_LETTER), state.hintsUsedForCurrent)
        assertEquals(listOf(HintType.FIRST_LETTER), state.hintsUsed)
        assertFalse(state.isComplete)
    }

    // ---- Review fixes (task 5.1 review) ----

    @Test
    fun `Hard passes allowFuzzy = false and Normal passes true to the validator`() {
        val hardVm = viewModel(routeHandle(difficulty = Difficulty.HARD), silhouettesMode(listOf(france)))
        hardVm.type("France")
        coVerify { validateAnswer("France", any(), false) }

        val normalVm = viewModel(routeHandle(difficulty = Difficulty.NORMAL), silhouettesMode(listOf(germany)))
        normalVm.type("Germany")
        coVerify { validateAnswer("Germany", any(), true) }
    }

    @Test
    fun `Hard always shows the timer with show_timer off, Normal follows the setting`() {
        every { settingsRepository.showTimer } returns flowOf(false)

        val hardVm = viewModel(routeHandle(difficulty = Difficulty.HARD), silhouettesMode(listOf(france)))
        assertTrue(hardVm.timerVisible.value)

        val normalVm = viewModel(routeHandle(difficulty = Difficulty.NORMAL), silhouettesMode(listOf(germany)))
        assertFalse(normalVm.timerVisible.value)
    }

    @Test
    fun `give up records answered countries as correct and the rest as missed`() {
        val mode = silhouettesMode(listOf(france, germany))
        val vm = viewModel(routeHandle(), mode)
        vm.type("France")
        advanceTime(SilhouettesGameViewModel.ADVANCE_DELAY_MILLIS)
        assertEquals("DEU", vm.active().current?.code)

        vm.onGiveUp()
        runCurrent()

        val stored = completedQuizzes.stored!!
        assertEquals(listOf("FRA"), stored.answeredCodes)
        assertEquals(listOf("DEU"), stored.missedCodes())
    }

    @Test
    fun `a second submit while advancing is ignored`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)
        vm.type("France")
        val afterFirst = vm.active()
        assertTrue(afterFirst.isAdvancing)

        vm.onInputChange("France")
        vm.onSubmit()
        runCurrent()

        assertEquals(afterFirst.answeredCodes, vm.active().answeredCodes)
        assertEquals(1, vm.active().answeredCodes.size)
        // The round has not advanced yet (no advanceTime call): nothing is recorded early.
        assertEquals(0, completedQuizzes.saveCount)
    }

    @Test
    fun `pass works while a wrong-country guess's feedback is still showing`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)
        vm.type("Germany")
        assertTrue(vm.active().feedback is SilhouetteFeedback.WrongCountry)

        vm.onPass()
        runCurrent()

        assertTrue(vm.active().feedback is SilhouetteFeedback.Revealed)
        assertTrue(vm.active().isAdvancing)
    }

    @Test
    fun `lives exhausted at Easy keeps the choice feedback and delays the published completion`() {
        val mode = silhouettesMode(listOf(france, germany, italy))
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY), mode)

        repeat(2) {
            val choice = vm.active().choice!!
            val wrong = choice.options.first { it.code != choice.targetCode }
            vm.onChoiceSelected(wrong.code)
            runCurrent()
            assertFalse(vm.active().isComplete)
            advanceTime(SilhouettesGameViewModel.CHOICE_FEEDBACK_MILLIS)
        }

        val lastChoice = vm.active().choice!!
        val lastWrong = lastChoice.options.first { it.code != lastChoice.targetCode }
        vm.onChoiceSelected(lastWrong.code)
        runCurrent()

        val state = vm.active()
        assertTrue("recorded immediately once lives are gone", state.isComplete)
        assertFalse(state.choiceFeedback!!.isCorrect)
        assertEquals(1, completedQuizzes.saveCount)
        assertNull("navigation waits for the reveal", vm.completion.value)

        advanceTime(SilhouettesGameViewModel.REVEAL_DELAY_MILLIS)
        assertEquals(SilhouettesCompletion.Step.NAVIGATE, vm.completion.value?.step)
    }

    @Test
    fun `lives exhausted when typing shows the final wrong guess and the revealed name, then delays completion`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)
        vm.type("Germany")
        vm.type("Italy")
        vm.type("Spain")

        val state = vm.active()
        assertTrue(state.isComplete)
        val feedback = state.feedback
        assertTrue(feedback is SilhouetteFeedback.LivesExhausted)
        feedback as SilhouetteFeedback.LivesExhausted
        assertEquals("Spain", feedback.wrongName)
        assertEquals("France", feedback.revealedName)
        assertEquals(1, completedQuizzes.saveCount)
        assertNull(vm.completion.value)

        advanceTime(SilhouettesGameViewModel.REVEAL_DELAY_MILLIS)
        assertEquals(SilhouettesCompletion.Step.NAVIGATE, vm.completion.value?.step)
    }

    @Test
    fun `hints used lower the stored score via CompleteQuizUseCase`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)
        vm.onHint(HintType.SHOW_REGION)
        vm.type("France")
        advanceTime(SilhouettesGameViewModel.ADVANCE_DELAY_MILLIS)

        val stored = completedQuizzes.stored!!
        // Perfect 1/1 -> 1 * 1.2 = 1.2; the region hint drops the bonus (unbonused 1.0) and costs
        // 0.25 (HintCosts, D37): 1.0 - 0.25 = 0.75.
        assertEquals(0.75, stored.score, 1e-9)
        assertFalse(stored.perfectBonus)
    }

    @Test
    fun `Show region is hidden for a region or subregion round`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(categoryType = "region", categoryValue = "Europe"), mode)

        assertFalse(HintType.SHOW_REGION in vm.active().availableHints)

        vm.onHint(HintType.SHOW_REGION)
        assertTrue("a hidden hint can't be used", vm.active().hintsUsed.isEmpty())
    }

    @Test
    fun `Show neighbours also lists the land neighbours' names, sorted (D38)`() {
        coEvery { getCountryOutline(any(), any(), any()) } returns outlineWithNeighbours(listOf("ESP", "DEU"))
        val vm = viewModel(routeHandle(), silhouettesMode(listOf(france)))
        runCurrent()

        assertEquals(listOf("Germany", "Spain"), vm.active().neighbourNames)
    }

    @Test
    fun `no land neighbours is an empty list, not a crash`() {
        coEvery { getCountryOutline(any(), any(), any()) } returns outlineWithNeighbours(emptyList())
        val vm = viewModel(routeHandle(), silhouettesMode(listOf(france)))
        runCurrent()

        assertTrue(vm.active().neighbourNames.isEmpty())
    }

    @Test
    fun `a missing outline is reported as unavailable, not left loading forever`() {
        coEvery { getCountryOutline(any(), any(), any()) } returns null
        val vm = viewModel(routeHandle(), silhouettesMode(listOf(france)))
        runCurrent()

        val state = vm.active()
        assertFalse(state.isOutlineLoading)
        assertTrue(state.isOutlineUnavailable)
        assertNull(state.outline)
    }

    @Test
    fun `an exception loading the outline is caught, not crashed`() {
        coEvery { getCountryOutline(any(), any(), any()) } throws RuntimeException("boom")
        val vm = viewModel(routeHandle(), silhouettesMode(listOf(france)))
        runCurrent()

        val state = vm.active()
        assertTrue(state.isOutlineUnavailable)
        assertFalse(state.isOutlineLoading)
    }

    @Test
    fun `a delayed advance firing while backgrounded does not resume the timer`() {
        val mode = silhouettesMode(listOf(france, germany))
        val vm = viewModel(routeHandle(), mode)

        vm.type("France")
        vm.onBackgrounded()
        assertTrue(vm.active().paused)

        clock.now += 10_000
        advanceTime(SilhouettesGameViewModel.ADVANCE_DELAY_MILLIS)

        val state = vm.active()
        assertEquals("DEU", state.current?.code)
        assertTrue("stays paused through the commit", state.paused)
    }

    @Test
    fun `onResume unpauses the round`() {
        val mode = silhouettesMode(listOf(france))
        val vm = viewModel(routeHandle(), mode)
        vm.onBackgrounded()
        assertTrue(vm.active().paused)

        vm.onResume()

        assertFalse(vm.active().paused)
    }

    @Test
    fun `a restored round comes back paused, like the classic quiz`() {
        val mode = silhouettesMode(listOf(france))
        val handle = routeHandle()
        val first = viewModel(handle, mode)
        first.onHint(HintType.FIRST_LETTER)

        val second = viewModel(afterProcessDeath(handle), mode)

        assertTrue(second.active().paused)
    }

    @Test
    fun `process death mid Pass's reveal restores already advanced to the next silhouette`() {
        val mode = silhouettesMode(listOf(france, germany))
        val handle = routeHandle()
        val first = viewModel(handle, mode)
        first.onPass()

        val second = viewModel(afterProcessDeath(handle), mode)

        val state = second.active()
        assertEquals("DEU", state.current?.code)
        assertTrue(state.answeredCodes.isEmpty())
        assertFalse(state.isAdvancing)
    }

    @Test
    fun `process death mid a correct answer's pause restores already advanced`() {
        val mode = silhouettesMode(listOf(france, germany))
        val handle = routeHandle()
        val first = viewModel(handle, mode)
        first.type("France")

        val second = viewModel(afterProcessDeath(handle), mode)

        val state = second.active()
        assertEquals(setOf("FRA"), state.answeredCodes)
        assertEquals("DEU", state.current?.code)
        assertFalse(state.isAdvancing)
    }

    @Test
    fun `process death mid an Easy wrong pick restores already advanced`() {
        val mode = silhouettesMode(listOf(france, germany))
        val handle = routeHandle(difficulty = Difficulty.EASY)
        val first = viewModel(handle, mode)
        val choice = first.active().choice!!
        val wrong = choice.options.first { it.code != choice.targetCode }
        first.onChoiceSelected(wrong.code)

        val second = viewModel(afterProcessDeath(handle), mode)

        val state = second.active()
        assertEquals("DEU", state.current?.code)
        assertFalse(state.isAdvancing)
    }

    private fun outlineWithNeighbours(neighbourCodes: List<String>) = OutlineModel(
        cca3 = "FRA",
        includeRemote = false,
        projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0),
        rings = emptyList(),
        neighbours = neighbourCodes.map { OutlineNeighbour(it, emptyList()) },
        frame = PlaneRect(-1f, -1f, 1f, 1f),
        scale = 1f
    )
}
