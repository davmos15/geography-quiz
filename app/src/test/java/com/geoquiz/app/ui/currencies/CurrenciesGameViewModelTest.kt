package com.geoquiz.app.ui.currencies

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.currencies.CurrencyData
import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
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
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
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
import kotlin.random.Random

/**
 * Task 7.1 code review item 5: there was previously no ViewModel-level test for Currencies at
 * all. Mirrors `SilhouettesGameViewModelTest`'s shape and helpers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrenciesGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    private val france = country("FRA", "France", "Paris", region = "Europe")
    private val germany = country("DEU", "Germany", "Berlin", region = "Europe")
    private val peru = country("PER", "Peru", "Lima", region = "Americas")
    private val allCountries = listOf(france, germany, peru)
    // The round's order follows the pool, not `allCountries` (`CurrencyQuestionGenerator.currencyItems`
    // sorts by the pool's index): Peru (PEN, typed at Normal/Hard) first, then Germany (EUR's
    // in-scope representative, always choice).
    private val pool = listOf(peru, germany, france)

    // EUR is shared (DEU/FRA); PEN is Peru's own currency.
    private val eur = CurrencyEntry("EUR", "Euro", "€", listOf("DEU", "FRA"), shared = true, fact = "Introduced in 1999.")
    private val pen = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("PER"), shared = false, fact = null)
    private val currencyData = CurrencyData(
        entries = listOf(eur, pen),
        primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
    )

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(allCountries)
    }
    private val currencyRepository = mockk<CurrencyRepository> {
        coEvery { currencies() } returns currencyData
        every { cachedOrNull() } returns currencyData
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>(relaxed = true)
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private fun currencyMode(roundPool: List<Country> = pool) = CurrencyGameMode(
        CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
        countryRepository,
        currencyRepository,
        validateAnswer,
        CalculateScoreUseCase()
    ).also { coEvery { getCountriesForQuiz(any()) } returns roundPool }

    private fun completeQuizUseCase(mode: CurrencyGameMode) = CompleteQuizUseCase(
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
        coEvery { validateAnswer(any(), any(), any()) } answers {
            when (firstArg<String>()) {
                "Peru" -> AnswerResult.Correct("Peru")
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

    private fun routeHandle(difficulty: Difficulty? = null) = SavedStateHandle(
        buildMap<String, Any?> {
            put("categoryType", "all")
            put("categoryValue", "_")
            if (difficulty != null) put(CurrenciesGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle,
        mode: CurrencyGameMode,
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode)
    ): CurrenciesGameViewModel {
        val vm = CurrenciesGameViewModel(
            savedStateHandle = handle,
            currencyGameMode = mode,
            countryRepository = countryRepository,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = QuizRandom(Random(9))
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun CurrenciesGameViewModel.active(): CurrencyRoundState =
        (uiState.value as CurrenciesUiState.Active).state

    @Test
    fun `a Hard round's shared currency is always a choice question - typed onSubmit is a no-op for it`() {
        val mode = currencyMode()
        val vm = viewModel(routeHandle(Difficulty.HARD), mode)
        // First item (Peru/PEN) is typed; answer it correctly to reach the shared euro item.
        vm.onInputChange("Peru")
        vm.onSubmit()
        runCurrent()
        vm.onNext()
        runCurrent()

        val state = vm.active()
        assertEquals("EUR", state.current?.entry?.code)
        assertTrue("a shared currency is always a choice question, even at Hard", state.isCurrentChoice)
        assertTrue(state.choice != null)

        // A typed submit is a no-op for a choice item: no feedback, no strike.
        vm.onInputChange("Germany")
        vm.onSubmit()
        runCurrent()
        val afterTypedAttempt = vm.active()
        assertEquals(0, afterTypedAttempt.incorrectGuesses)
        assertEquals(CurrencyFeedback.None, afterTypedAttempt.feedback)
    }

    @Test
    fun `restore catches up past an item left waiting for Next, instead of re-showing it`() {
        val mode = currencyMode()
        val handle = routeHandle(Difficulty.NORMAL)
        val vm = viewModel(handle, mode)
        vm.onInputChange("Peru")
        vm.onSubmit()
        runCurrent()
        // Died after answering Peru correctly, before tapping Next: isAdvancing (pendingAdvance).
        assertTrue(vm.active().isAdvancing)

        val restored = viewModel(afterProcessDeath(handle), mode)
        val state = restored.active()
        assertEquals("EUR", state.current?.entry?.code)
        assertEquals(setOf("PER"), state.answeredCodes)
        assertFalse("already caught up: not left waiting to advance again", state.isAdvancing)
    }

    @Test
    fun `restore rebuilds the identical choice question, not a freshly drawn one`() {
        val mode = currencyMode()
        val handle = routeHandle(Difficulty.HARD)
        val vm = viewModel(handle, mode)
        vm.onInputChange("Peru")
        vm.onSubmit()
        runCurrent()
        vm.onNext()
        runCurrent()
        val before = vm.active().choice!!

        val restored = viewModel(afterProcessDeath(handle), mode)
        val after = restored.active().choice!!

        assertEquals(before.targetCode, after.targetCode)
        assertEquals(before.prompt, after.prompt)
        assertEquals(before.options, after.options)
    }

    @Test
    fun `a Hard round ends after three wrong choice picks, even with items still unanswered`() {
        // Four distinct shared currencies: every item is a choice question at every tier, so
        // three wrong picks can be driven without the round also running out of items.
        val c1 = country("AUT", "Austria", "Vienna", region = "Europe")
        val c2 = country("BEL", "Belgium", "Brussels", region = "Europe")
        val c3 = country("ITA", "Italy", "Rome", region = "Europe")
        val c4 = country("ESP", "Spain", "Madrid", region = "Europe")
        val c5 = country("GRC", "Greece", "Athens", region = "Europe")
        val c6 = country("CYP", "Cyprus", "Nicosia", region = "Europe")
        val c7 = country("PRT", "Portugal", "Lisbon", region = "Europe")
        val c8 = country("IRL", "Ireland", "Dublin", region = "Europe")
        val roundPool = listOf(c1, c2, c3, c4, c5, c6, c7, c8)
        val data = CurrencyData(
            entries = listOf(
                CurrencyEntry("AA1", "A1", "a", listOf("AUT", "BEL"), shared = true, fact = null),
                CurrencyEntry("AA2", "A2", "a", listOf("ITA", "ESP"), shared = true, fact = null),
                CurrencyEntry("AA3", "A3", "a", listOf("GRC", "CYP"), shared = true, fact = null),
                CurrencyEntry("AA4", "A4", "a", listOf("PRT", "IRL"), shared = true, fact = null)
            ),
            primaryCurrency = emptyMap()
        )
        every { countryRepository.getAllCountries() } returns flowOf(roundPool)
        every { currencyRepository.cachedOrNull() } returns data
        coEvery { currencyRepository.currencies() } returns data
        val mode = currencyMode(roundPool)
        val vm = viewModel(routeHandle(Difficulty.HARD), mode)
        assertEquals(4, vm.active().items.size)

        repeat(3) {
            val state = vm.active()
            val wrongCode = state.choice!!.options.first { it.code != state.choice!!.targetCode }.code
            vm.onChoiceSelected(wrongCode)
            runCurrent()
            vm.onNext()
            runCurrent()
        }

        val finalState = vm.active()
        assertEquals(3, finalState.incorrectGuesses)
        assertTrue("three strikes end a Hard round even with an item left unanswered", finalState.isComplete)
        assertTrue(finalState.currentIndex < 4)
    }

    @Test
    fun `give up completes the round immediately, recording what was answered so far`() {
        val mode = currencyMode()
        val vm = viewModel(routeHandle(Difficulty.NORMAL), mode)
        vm.onInputChange("Peru")
        vm.onSubmit()
        runCurrent()
        vm.onNext()
        runCurrent()

        vm.onGiveUp()
        runCurrent()

        val state = vm.active()
        assertTrue(state.isComplete)
        assertEquals(1, completedQuizzes.saveCount)
        val stored = completedQuizzes.stored!!
        assertEquals(listOf("PER"), stored.answeredCodes)
    }

    @Test
    fun `a filter with any currency item never produces an empty 0 of 0 round (review item 1)`() {
        // Micronesia-shaped fixture: no country owns its own currency; two shared currencies
        // (USD-like, AUD-like) only given a representative once scoped to this exact filter.
        val fsm = country("FSM", "Micronesia", "Palikir", region = "Oceania")
        val kir = country("KIR", "Kiribati", "Tarawa", region = "Oceania")
        val roundPool = listOf(fsm, kir)
        val data = CurrencyData(
            entries = listOf(
                CurrencyEntry("USD", "US dollar", "$", listOf("FSM", "ECU"), shared = true, fact = null),
                CurrencyEntry("AUD", "Australian dollar", "$", listOf("KIR", "AUS"), shared = true, fact = null)
            ),
            primaryCurrency = emptyMap()
        )
        every { countryRepository.getAllCountries() } returns flowOf(roundPool)
        every { currencyRepository.cachedOrNull() } returns data
        coEvery { currencyRepository.currencies() } returns data
        val mode = currencyMode(roundPool)

        val vm = viewModel(routeHandle(Difficulty.NORMAL), mode)

        val state = vm.active()
        assertFalse("must never silently complete a 0/0 round", state.isComplete)
        assertEquals(2, state.items.size)
        assertNull(completedQuizzes.stored)
    }
}
