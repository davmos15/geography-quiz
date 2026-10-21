package com.geoquiz.app.ui.tapmap

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
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.mode.tapmap.TapMapQuestionGenerator
import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Tap the map's "Name all" variant (task 5.2b, D40). Uses a real [ValidateAnswerUseCase] (against
 * a stubbed [CountryRepository]) so typo tolerance, near misses and strikes are exercised exactly
 * as the classic Countries quiz is (see `ValidateAnswerUseCaseTest`), not just the view model's
 * own bookkeeping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TapMapNameAllViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
        fun advance(millis: Long) { now += millis }
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()
    private val normalize = NormalizeInputUseCase()

    // Iceland and Ireland are 7 letters (normalised), long enough for FuzzyAnswerMatcher's >6
    // character rule, same fixtures as ValidateAnswerUseCaseTest.
    private val france = Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6, "Paris")
    private val iceland = Country("ISL", "Iceland", "Iceland", "Europe", "Northern Europe", 7, "Reykjavik")
    private val ireland = Country("IRL", "Ireland", "Republic of Ireland", "Europe", "Northern Europe", 7, "Dublin")
    private val europe = listOf(france, iceland, ireland)

    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(europe)
        coEvery { findCountryByAnswer(any()) } answers {
            val input = normalize(firstArg<String>())
            europe.firstOrNull { normalize(it.name) == input }
        }
        coEvery { getCountryAnswerAliases() } returns europe.map { AnswerAlias(normalize(it.name), it) }
    }
    private val getCountriesForQuiz = GetCountriesForQuizUseCase(countryRepository, GetRegionMembersUseCase(countryRepository))
    private val validateAnswer = ValidateAnswerUseCase(countryRepository, normalize)

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
                validateAnswer,
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
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun routeHandle(
        categoryType: String = "map_region",
        categoryValue: String = "europe",
        difficulty: Difficulty? = Difficulty.NORMAL
    ) = SavedStateHandle(
        buildMap<String, Any?> {
            put("categoryType", categoryType)
            put("categoryValue", categoryValue)
            if (difficulty != null) put(TapMapGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(handle: SavedStateHandle, seed: Long = SEED): TapMapNameAllViewModel {
        val vm = TapMapNameAllViewModel(
            savedStateHandle = handle,
            gameModes = gameModes(seed),
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            clock = clock,
            defaultDispatcher = dispatcher
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun TapMapNameAllViewModel.active() = (uiState.value as TapMapNameAllUiState.Active).state

    private fun TapMapNameAllViewModel.submit(input: String) {
        onInputChange(input)
        onSubmitAnswer()
        runCurrent()
    }

    @Test
    fun `starts with every region member in the quiz, none named`() {
        val vm = viewModel(routeHandle())
        val state = vm.active()

        assertEquals(europe.map { it.code }.toSet(), state.quiz.countries.map { it.code }.toSet())
        assertTrue(state.answeredCountries.isEmpty())
        assertFalse(state.isComplete)
    }

    @Test
    fun `without a route difficulty Difficulty DEFAULT (Normal) is used`() {
        val vm = viewModel(routeHandle(difficulty = null))

        assertEquals(Difficulty.NORMAL, vm.difficulty.value)
    }

    @Test
    fun `a correct name fills the country in and clears the input`() {
        val vm = viewModel(routeHandle())

        vm.submit("France")

        val state = vm.active()
        assertTrue("FRA" in state.answeredCountries)
        assertEquals(AnswerResult.Correct("France"), state.lastAnswerResult)
        assertEquals("", state.currentInput)
    }

    @Test
    fun `normal - a typo is accepted as correct`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))

        vm.submit("Icelnad")

        val state = vm.active()
        assertTrue("ISL" in state.answeredCountries)
        assertEquals(AnswerResult.Correct("Iceland", viaTypo = true), state.lastAnswerResult)
    }

    @Test
    fun `hard - a typo is a near miss, not accepted, not a strike`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        vm.submit("Icelnad")

        val state = vm.active()
        assertEquals(AnswerResult.NearMiss, state.lastAnswerResult)
        assertFalse("ISL" in state.answeredCountries)
        assertEquals(0, state.incorrectGuesses)
        // Kept for editing, exactly like the classic quiz on a NearMiss.
        assertEquals("Icelnad", state.currentInput)
    }

    @Test
    fun `already-named is reported and never a strike`() {
        val vm = viewModel(routeHandle())
        vm.submit("France")

        vm.submit("France")

        val state = vm.active()
        assertEquals(AnswerResult.AlreadyAnswered, state.lastAnswerResult)
        assertEquals(0, state.incorrectGuesses)
    }

    @Test
    fun `an unrecognised answer is incorrect and recorded, a strike at hard`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        vm.submit("Atlantis")

        val state = vm.active()
        assertEquals(AnswerResult.Incorrect, state.lastAnswerResult)
        assertEquals(1, state.incorrectGuesses)
        assertTrue("Atlantis" in state.incorrectGuessStrings)
    }

    @Test
    fun `normal - incorrect guesses never end the round (no strike limit)`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))

        repeat(5) { vm.submit("Atlantis") }

        val state = vm.active()
        assertFalse(state.isComplete)
        assertEquals(5, state.incorrectGuesses)
    }

    @Test
    fun `hard - 3 incorrect guesses end the round`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        repeat(3) { vm.submit("Atlantis") }

        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(3, state.incorrectGuesses)
    }

    @Test
    fun `hard - near misses never count towards the 3-strike limit`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        repeat(5) { vm.submit("Icelnad") }

        val state = vm.active()
        assertFalse(state.isComplete)
        assertEquals(0, state.incorrectGuesses)
    }

    @Test
    fun `an easy route is treated as normal (D40)`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        assertEquals(Difficulty.NORMAL, vm.difficulty.value)
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
    fun `give up ends the round, the rest counting as missed`() {
        val vm = viewModel(routeHandle())
        vm.submit("France")

        vm.onGiveUp()
        runCurrent()

        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(setOf("FRA"), state.answeredCountries)
    }

    @Test
    fun `completion request carries the mode id, name_all variant and the full item list`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL))

        europe.forEach { vm.submit(it.name) }

        assertTrue(vm.active().isComplete)
        val completion = vm.completion.value
        requireNotNull(completion)
        val recorded = requireNotNull(completedQuizzes.stored)
        assertEquals(completion.resultId, recorded.id)
        assertEquals(TapMapGameMode.MODE_ID, recorded.quizModeId)
        assertEquals(TapMapGameMode.VARIANT_NAME_ALL, recorded.variantId)
        assertEquals(europe.map { it.code }.toSet(), recorded.countryCodes.toSet())
        assertEquals(europe.map { it.code }.toSet(), recorded.answeredCodes.toSet())
    }

    @Test
    fun `restores progress from the saved state handle after process death`() {
        val handle = routeHandle()
        val vm = viewModel(handle)
        vm.submit("France")
        clock.advance(5_000)

        val restored = viewModel(afterProcessDeath(handle))
        val state = restored.active()

        assertTrue("FRA" in state.answeredCountries)
        // A restored, incomplete round always comes back paused, same as classic.
        assertTrue(state.isPaused)
    }

    @Test
    fun `backgrounding pauses the round, blocking input, and resuming continues it`() {
        val vm = viewModel(routeHandle())
        assertFalse(vm.active().isPaused)

        vm.onBackgrounded()
        runCurrent()
        assertTrue(vm.active().isPaused)

        vm.submit("France")
        assertFalse("paused: no input accepted", "FRA" in vm.active().answeredCountries)

        vm.togglePause()
        runCurrent()
        assertFalse(vm.active().isPaused)

        vm.submit("France")
        assertTrue("FRA" in vm.active().answeredCountries)
    }

    // --- Submit race (S10) -------------------------------------------------------------------

    @Test
    fun `hard - two submits of one wrong answer before the first finishes give exactly one strike`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        vm.onInputChange("Atlantis")
        vm.onSubmitAnswer()
        // The first submit is still validating (StandardTestDispatcher queues it): this second
        // call must be ignored, not counted as a second guess.
        vm.onSubmitAnswer()
        runCurrent()

        assertEquals(1, vm.active().incorrectGuesses)
    }

    @Test
    fun `a give up while a submit is still validating stays complete`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        vm.onInputChange("Atlantis")
        vm.onSubmitAnswer()
        // Give up runs synchronously before the queued submit's validation does.
        vm.onGiveUp()
        runCurrent()

        assertTrue(vm.active().isComplete)
    }

    // --- Process death and restore --------------------------------------------------------------

    @Test
    fun `a completed round restored after process death reuses the saved result id and records only once`() {
        val handle = routeHandle(difficulty = Difficulty.NORMAL)
        val vm = viewModel(handle)
        europe.forEach { vm.submit(it.name) }
        assertTrue(vm.active().isComplete)
        val resultId = requireNotNull(vm.completion.value).resultId
        assertEquals(1, completedQuizzes.saveCount)

        val restored = viewModel(afterProcessDeath(handle))

        assertEquals(resultId, requireNotNull(restored.completion.value).resultId)
        // CompleteQuizUseCase is itself idempotent per result id: a second run with the same id
        // (the restored view model's own finishQuizIfComplete) records nothing more.
        assertEquals(1, completedQuizzes.saveCount)
    }

    @Test
    fun `a restored round keeps its elapsed time`() {
        val handle = routeHandle()
        val vm = viewModel(handle)
        clock.advance(4_000)
        vm.onBackgrounded()
        runCurrent()

        val restored = viewModel(afterProcessDeath(handle))

        assertEquals(4, restored.timerSeconds.value)
    }

    @Test
    fun `with no route difficulty, a saved Hard tier is restored`() {
        val handle = routeHandle(difficulty = Difficulty.HARD)
        viewModel(handle) // resolving the route difficulty also writes it to the saved state.

        val withoutRouteDifficulty = SavedStateHandle(
            handle.keys()
                .filter { it != TapMapGameViewModel.ARG_DIFFICULTY }
                .associateWith { handle.get<Any?>(it) }
        )
        val restored = viewModel(withoutRouteDifficulty)

        assertEquals(Difficulty.HARD, restored.difficulty.value)
    }

    // --- Practice (D21/D40) -----------------------------------------------------------------

    @Test
    fun `a practice round asks only its own codes, frames World to them, and keeps the practice category`() {
        val tinyLayer = squareLayer(squareFeature("FRA", 0f, 1f), id = GeoLayerId.ADMIN0_50M)
        coEvery { mapRepository.layer(GeoLayerId.ADMIN0_50M) } returns tinyLayer

        val vm = viewModel(routeHandle(categoryType = "practice", categoryValue = "FRA"))

        val state = vm.active()
        assertEquals(setOf("FRA"), state.quiz.countries.map { it.code }.toSet())
        // World is otherwise never framed (TapMapMapLoaderTest), but a practice set always is.
        assertNotNull(vm.mapState.value.frame)

        vm.submit("France")
        val recorded = requireNotNull(completedQuizzes.stored)
        assertEquals(QuizCategory.Practice.TYPE_KEY, recorded.categoryType)
    }

    companion object {
        private const val SEED = 11L
    }
}
