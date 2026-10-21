package com.geoquiz.app.ui.borderhop

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopQuestionGenerator
import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.CountryNameSuggester
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class BorderHopGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()
    private val normalize = NormalizeInputUseCase()

    /** South Africa to Sudan in a line (6 hops), plus four countries with no land border here. */
    private val line = listOf("ZAF", "BWA", "ZMB", "TZA", "UGA", "SSD", "SDN")
    private val names = mapOf(
        "ZAF" to "South Africa", "BWA" to "Botswana", "ZMB" to "Zambia", "TZA" to "Tanzania",
        "UGA" to "Uganda", "SSD" to "South Sudan", "SDN" to "Sudan",
        "JPN" to "Japan", "GBR" to "United Kingdom", "FRA" to "France", "AUS" to "Australia"
    )
    private val countries = names.map { (code, name) -> country(code, name, "Capital of $name", region = "Africa") }
    private val byCode = countries.associateBy { it.code }
    private val graph = BorderGraph(
        line.indices.associate { i -> line[i] to listOfNotNull(line.getOrNull(i - 1), line.getOrNull(i + 1)) } +
            mapOf("JPN" to emptyList(), "GBR" to emptyList(), "FRA" to emptyList(), "AUS" to emptyList())
    )
    private val aliases: List<AnswerAlias> =
        countries.map { AnswerAlias(normalize(it.name), it) } + AnswerAlias("uk", byCode.getValue("GBR"))

    /** The category's candidates; the pair decides the par: 2 at Easy, 4 at Normal, 6 at Hard. */
    private fun candidatesFor(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY -> listOf("ZAF", "ZMB")
        Difficulty.NORMAL -> listOf("ZAF", "UGA")
        Difficulty.HARD -> listOf("ZAF", "SDN")
    }.map { byCode.getValue(it) }

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
        coEvery { getCountryAnswerAliases() } returns aliases
    }
    private val borderGraphRepository = mockk<BorderGraphRepository> { coEvery { graph() } returns graph }
    private val mapRepository = mockk<MapRepository> {
        coEvery { layer(any()) } throws IOException("no map in unit tests")
        every { cachedLayer(any()) } returns null
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()

    private fun borderHopMode() =
        BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), mockk<ValidateAnswerUseCase>(relaxed = true))

    private fun completeQuizUseCase(mode: BorderHopGameMode) = CompleteQuizUseCase(
        gameModes = GameModeRegistry(setOf(mode)),
        completedQuizRepository = completedQuizzes,
        savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true),
        achievementRepository = mockk<AchievementRepository>(relaxed = true),
        quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true),
        playGamesService = mockk<PlayGamesAchievementService>(relaxed = true)
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

    private fun routeHandle(difficulty: Difficulty) = SavedStateHandle(
        mapOf("categoryType" to "all", "categoryValue" to "_", BorderHopGameViewModel.ARG_DIFFICULTY to difficulty.id)
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle,
        mode: BorderHopGameMode = borderHopMode(),
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode),
        seed: Int = 9
    ): BorderHopGameViewModel {
        val difficulty = Difficulty.fromIdOrNull(handle[BorderHopGameViewModel.ARG_DIFFICULTY]) ?: Difficulty.NORMAL
        coEvery { getCountriesForQuiz(any()) } returns candidatesFor(difficulty)
        val vm = BorderHopGameViewModel(
            savedStateHandle = handle,
            gameMode = mode,
            borderGraphRepository = borderGraphRepository,
            countryRepository = countryRepository,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            normalizeInput = normalize,
            suggester = CountryNameSuggester(normalize),
            clock = clock,
            quizRandom = QuizRandom(Random(seed)),
            defaultDispatcher = dispatcher
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun advance(millis: Long) {
        dispatcher.scheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private fun BorderHopGameViewModel.active(): BorderHopState = (uiState.value as BorderHopUiState.Active).state

    private fun BorderHopGameViewModel.type(text: String) {
        onInputChange(text)
        onSubmit()
        runCurrent()
    }

    private fun BorderHopGameViewModel.optimalRoute(): List<String> =
        active().let { graph.shortestPath(it.currentCode, it.targetCode)!! }

    /** A country that is not on the route and does not border its end. */
    private fun BorderHopGameViewModel.nonNeighbour(): String = active().let { state ->
        names.keys.first { it !in state.path && it !in graph.neighboursOf(state.currentCode) && it != state.targetCode }
    }

    // ---- Fresh round -----------------------------------------------------------------------------

    @Test
    fun `a fresh round starts on one candidate with the other as target, at the shortest-route par`() {
        val state = viewModel(routeHandle(Difficulty.NORMAL)).active()

        assertEquals(setOf("ZAF", "UGA"), setOf(state.startCode, state.targetCode))
        assertEquals(4, state.parHops)
        assertEquals(listOf(state.startCode), state.path)
        assertTrue(state.options.isEmpty())
    }

    @Test
    fun `no valid puzzle for the requested hop range publishes Unavailable`() {
        val handle = routeHandle(Difficulty.HARD)
        coEvery { getCountriesForQuiz(any()) } returns listOf(byCode.getValue("ZAF"), byCode.getValue("BWA"))
        val vm = BorderHopGameViewModel(
            savedStateHandle = handle,
            gameMode = borderHopMode(),
            borderGraphRepository = borderGraphRepository,
            countryRepository = countryRepository,
            completeQuiz = completeQuizUseCase(borderHopMode()),
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            normalizeInput = normalize,
            suggester = CountryNameSuggester(normalize),
            clock = clock,
            quizRandom = QuizRandom(Random(1)),
            defaultDispatcher = dispatcher
        )
        runCurrent()

        assertEquals(BorderHopUiState.Unavailable, vm.uiState.value)
    }

    // ---- Typed guesses ---------------------------------------------------------------------------

    @Test
    fun `an unrecognised name is not a strike and keeps the text for editing`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))

        vm.type("Atlantis")

        val state = vm.active()
        assertEquals(BorderHopFeedback.NotRecognised("Atlantis"), state.feedback)
        assertTrue(state.wrongGuesses.isEmpty())
        assertEquals("Atlantis", state.input)
        assertEquals(1, state.path.size)
    }

    @Test
    fun `a country already on the route is not a strike`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))
        val start = vm.active().startCode

        vm.type(names.getValue(start))

        val state = vm.active()
        assertEquals(BorderHopFeedback.AlreadyOnRoute(start), state.feedback)
        assertTrue(state.wrongGuesses.isEmpty())
        assertEquals("", state.input)
    }

    @Test
    fun `a recognised non-neighbour is a wrong guess, flashed on the map for a moment`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val current = vm.active().currentCode

        vm.type("Japan")

        val state = vm.active()
        assertEquals(BorderHopFeedback.NotNeighbour("JPN", current), state.feedback)
        assertEquals(listOf("JPN"), state.wrongGuesses)
        assertEquals("JPN", state.flashWrongCode)
        assertEquals(MapFeatureState.Wrong, borderHopFeatureStates(state)["JPN"])
        assertEquals(1, state.path.size)

        advance(BorderHopGameViewModel.WRONG_FLASH_MILLIS)
        assertNull(vm.active().flashWrongCode)
    }

    @Test
    fun `an alias like UK is recognised`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))

        vm.type("UK")

        assertEquals(listOf("GBR"), vm.active().wrongGuesses)
    }

    @Test
    fun `naming a neighbour adds it to the route and clears the field`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val next = vm.optimalRoute()[1]

        vm.type(names.getValue(next).uppercase())

        val state = vm.active()
        assertEquals(next, state.currentCode)
        assertEquals(BorderHopFeedback.Hopped(next), state.feedback)
        assertEquals("", state.input)
        assertEquals(MapFeatureState.Found, borderHopFeatureStates(state)[next])
        assertEquals(MapFeatureState.Start, borderHopFeatureStates(state)[state.startCode])
        assertEquals(MapFeatureState.End, borderHopFeatureStates(state)[state.targetCode])
    }

    @Test
    fun `a country two hops ahead is a wrong guess, not a hop`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val twoAway = vm.optimalRoute()[2]

        vm.type(names.getValue(twoAway))

        assertEquals(listOf(twoAway), vm.active().wrongGuesses)
        assertEquals(1, vm.active().path.size)
    }

    @Test
    fun `reaching the target completes and records a perfect round`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val route = vm.optimalRoute()

        route.drop(1).forEach { vm.type(names.getValue(it)) }

        val state = vm.active()
        assertTrue(state.isComplete)
        assertTrue(state.reachedTarget)
        assertEquals(1, completedQuizzes.saveCount)
        val stored = completedQuizzes.stored!!
        assertEquals(BorderHopGameMode.MODE_ID, stored.quizModeId)
        assertEquals(route, stored.countryCodes)
        assertTrue(stored.perfectBonus)
        assertEquals(0, stored.incorrectGuesses)
        assertTrue(stored.missedCodes().isEmpty())
    }

    @Test
    fun `a wrong guess on the way costs the perfect bonus and is recorded by name`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val route = vm.optimalRoute()

        vm.type("Japan")
        route.drop(1).forEach { vm.type(names.getValue(it)) }

        val stored = completedQuizzes.stored!!
        assertFalse(stored.perfectBonus)
        assertEquals(1, stored.incorrectGuesses)
        assertEquals(listOf("Japan"), stored.incorrectGuessStrings)
        assertEquals(4.0 / 5.0 * 4.0, stored.score, 1e-9)
    }

    @Test
    fun `Hard ends the round at three wrong guesses, scoring zero`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))

        vm.type("Japan")
        vm.type("France")
        assertFalse(vm.active().isComplete)
        assertEquals(1, vm.active().strikesLeft)
        vm.type("UK")

        val state = vm.active()
        assertTrue(state.isComplete)
        assertTrue(state.outOfGuesses)
        assertFalse(state.reachedTarget)
        val stored = completedQuizzes.stored!!
        assertEquals(0.0, stored.score, 1e-9)
        assertEquals(3, stored.incorrectGuesses)
    }

    @Test
    fun `Normal has no strike limit`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))

        // Four distinct, fully isolated non-neighbours: a repeated one no longer counts twice
        // (P10-R2 M1), so these must each be different from one another and from the route.
        listOf("Japan", "United Kingdom", "France", "Australia").forEach { vm.type(it) }

        assertFalse(vm.active().isComplete)
        assertEquals(4, vm.active().wrongGuesses.size)
        assertNull(vm.active().strikesLeft)
    }

    // ---- Repeated wrong guesses (P10-R2 M1) -----------------------------------------------------

    @Test
    fun `naming the same non-neighbour again from the same position repeats the feedback, not a second strike`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))
        val current = vm.active().currentCode

        vm.type("Japan")
        assertEquals(listOf("JPN"), vm.active().wrongGuesses)
        assertEquals(2, vm.active().strikesLeft)

        vm.type("Japan")

        val state = vm.active()
        assertEquals(BorderHopFeedback.NotNeighbour("JPN", current), state.feedback)
        assertEquals("JPN", state.flashWrongCode)
        assertEquals(listOf("JPN"), state.wrongGuesses)
        assertEquals(2, state.strikesLeft)
        assertFalse(state.isComplete)
    }

    @Test
    fun `naming a different non-neighbour from the same position is still a fresh strike`() {
        val vm = viewModel(routeHandle(Difficulty.HARD))

        vm.type("Japan")
        vm.type("France")

        assertEquals(listOf("JPN", "FRA"), vm.active().wrongGuesses)
        assertEquals(1, vm.active().strikesLeft)
    }

    @Test
    fun `a repeated wrong guess is recorded only once at Results, not duplicated`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val route = vm.optimalRoute()

        vm.type("Japan")
        vm.type("Japan")
        route.drop(1).forEach { vm.type(names.getValue(it)) }

        val stored = completedQuizzes.stored!!
        assertEquals(1, stored.incorrectGuesses)
        assertEquals(listOf("Japan"), stored.incorrectGuessStrings)
    }

    @Test
    fun `repeated wrong guesses stay deduplicated after process death`() {
        val handle = routeHandle(Difficulty.HARD)
        val vm = viewModel(handle)
        vm.type("Japan")

        val after = viewModel(afterProcessDeath(handle))
        after.onResume()
        after.type("Japan")

        val state = after.active()
        assertEquals(listOf("JPN"), state.wrongGuesses)
        assertEquals(2, state.strikesLeft)
    }

    @Test
    fun `without a route difficulty Difficulty DEFAULT (Normal) is used`() {
        val handle = SavedStateHandle(mapOf("categoryType" to "all", "categoryValue" to "_"))
        coEvery { getCountriesForQuiz(any()) } returns candidatesFor(Difficulty.NORMAL)

        val vm = viewModel(handle)

        assertEquals(Difficulty.NORMAL, vm.active().difficulty)
    }

    @Test
    fun `Hard accepts no typos, Normal forgives one`() {
        val hard = viewModel(routeHandle(Difficulty.HARD))
        hard.type("United Kingdomm")
        assertEquals(BorderHopFeedback.NotRecognised("United Kingdomm"), hard.active().feedback)
        assertTrue(hard.active().wrongGuesses.isEmpty())

        val normal = viewModel(routeHandle(Difficulty.NORMAL))
        normal.type("United Kingdomm")
        assertEquals(listOf("GBR"), normal.active().wrongGuesses)
    }

    @Test
    fun `Normal suggests countries as you type, Hard does not`() {
        val normal = viewModel(routeHandle(Difficulty.NORMAL))
        normal.onInputChange("bots")
        assertEquals(listOf("BWA"), normal.active().suggestions)

        val hard = viewModel(routeHandle(Difficulty.HARD))
        hard.onInputChange("bots")
        assertTrue(hard.active().suggestions.isEmpty())
    }

    @Test
    fun `tapping a suggestion submits that country`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val next = vm.optimalRoute()[1]
        vm.onInputChange(names.getValue(next).take(3))

        vm.onSuggestionSelected(next)
        runCurrent()

        assertEquals(next, vm.active().currentCode)
        assertTrue(vm.active().suggestions.isEmpty())
    }

    // ---- Undo, hints, give up ------------------------------------------------------------------

    @Test
    fun `undo drops the last hop for free, and does nothing at the start`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val before = vm.active()
        vm.onUndo()
        runCurrent()
        assertEquals(before, vm.active())

        vm.type(names.getValue(vm.optimalRoute()[1]))
        vm.onUndo()
        runCurrent()

        assertEquals(before.path, vm.active().path)
        assertTrue(vm.active().hintsUsed.isEmpty())
    }

    @Test
    fun `a hop followed by a quick undo back to the same codes cancels the hop's in-flight frame (P10-R2 L1)`() {
        // A real map layer, so updateRouteFrame actually computes and (eventually) publishes a
        // rect, rather than returning early for want of map data as in every other test here.
        val features = names.keys.mapIndexed { index, code -> squareFeature(code, index * 2f, index * 2f + 1f) }
        coEvery { mapRepository.layer(GeoLayerId.ADMIN0_50M) } returns
            squareLayer(*features.toTypedArray(), id = GeoLayerId.ADMIN0_50M)

        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val initialFrame = vm.routeFrame.value
        assertNotNull("the initial frame should have resolved from the fake map layer", initialFrame)
        val next = vm.optimalRoute()[1]

        // No runCurrent() between these two: the hop's frame recompute is still in flight
        // (queued on the test dispatcher) when Undo runs and sees the same codes as before.
        vm.onInputChange(names.getValue(next))
        vm.onSubmit()
        vm.onUndo()
        runCurrent()

        assertEquals(initialFrame, vm.routeFrame.value)
    }

    @Test
    fun `the hint reveals a closer neighbour, costs once per position and stops at Normal's cap`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        val route = vm.optimalRoute()

        vm.onHint()
        vm.onHint()
        runCurrent()
        assertEquals(listOf(HintType.SHOW_VALID_HOP), vm.active().hintsUsed)
        assertEquals(route[1], vm.active().revealedHopCode)
        assertEquals(MapFeatureState.Highlighted, borderHopFeatureStates(vm.active())[route[1]])

        vm.type(names.getValue(route[1]))
        assertNull(vm.active().revealedHopCode)
        vm.onHint()
        vm.type(names.getValue(route[2]))
        vm.onHint()
        runCurrent()

        assertEquals(HintCosts.maxHintsPerRound(Difficulty.NORMAL), vm.active().hintsUsed.size)
        assertNull(vm.active().revealedHopCode)
    }

    @Test
    fun `the hint is unavailable, and onHint a no-op, once every closer neighbour is already on the route (P10-R2 L5)`() {
        // BRA--P0--P1--ARG, then ARG and CHL both border BOL (the target) and PER: a detour via
        // CHL and PER (both legal hops, just not the shortest way) leaves PER with both of its
        // closer neighbours (ARG, CHL) already on the route.
        val hintGraph = BorderGraph(
            mapOf(
                "BRA" to listOf("P0"),
                "P0" to listOf("BRA", "P1"),
                "P1" to listOf("P0", "ARG"),
                "ARG" to listOf("P1", "CHL", "BOL", "PER"),
                "CHL" to listOf("ARG", "BOL", "PER"),
                "BOL" to listOf("ARG", "CHL"),
                "PER" to listOf("ARG", "CHL")
            )
        )
        val hintCountries = listOf("BRA", "P0", "P1", "ARG", "CHL", "BOL", "PER").map { country(it, it, "Capital of $it") }
        val hintAliases = hintCountries.map { AnswerAlias(normalize(it.name), it) }
        val hintCountryRepository = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(hintCountries)
            coEvery { getCountryAnswerAliases() } returns hintAliases
        }
        val hintBorderGraphRepository = mockk<BorderGraphRepository> { coEvery { graph() } returns hintGraph }

        // A saved snapshot, so loadRound restores this exact puzzle rather than generating a
        // random one (the tiny graph above has only one feasible pair, at Easy's hop range, which
        // never shows a hint button at all).
        val handle = routeHandle(Difficulty.NORMAL)
        BorderHopSavedState(handle).apply {
            difficulty = Difficulty.NORMAL
            save(
                BorderHopState(
                    category = QuizCategory.AllCountries,
                    difficulty = Difficulty.NORMAL,
                    startCode = "BRA",
                    targetCode = "BOL",
                    parHops = 4,
                    path = listOf("BRA")
                ),
                elapsedMillis = 0L
            )
        }
        val mode = borderHopMode()
        val vm = BorderHopGameViewModel(
            savedStateHandle = handle,
            gameMode = mode,
            borderGraphRepository = hintBorderGraphRepository,
            countryRepository = hintCountryRepository,
            completeQuiz = completeQuizUseCase(mode),
            settingsRepository = settingsRepository,
            mapRepository = mapRepository,
            normalizeInput = normalize,
            suggester = CountryNameSuggester(normalize),
            clock = clock,
            quizRandom = QuizRandom(Random(1)),
            defaultDispatcher = dispatcher
        )
        runCurrent()
        // A restored snapshot starts paused, like process death (the player resumes deliberately).
        vm.onResume()
        assertTrue(vm.active().hintAvailable)

        listOf("P0", "P1", "ARG", "CHL", "PER").forEach { vm.type(it) }

        val state = vm.active()
        assertEquals("PER", state.currentCode)
        assertTrue("no wrong guesses: every hop above is a legal (if not shortest) move", state.wrongGuesses.isEmpty())
        assertFalse(state.hintAvailable)

        vm.onHint()
        runCurrent()
        assertTrue(vm.active().hintsUsed.isEmpty())
        assertNull(vm.active().revealedHopCode)
    }

    @Test
    fun `giving up records a zero score`() {
        val vm = viewModel(routeHandle(Difficulty.NORMAL))
        vm.type(names.getValue(vm.optimalRoute()[1]))

        vm.onGiveUp()
        runCurrent()

        assertTrue(vm.active().gaveUp)
        val stored = completedQuizzes.stored!!
        assertEquals(0, stored.correct)
        assertEquals(0.0, stored.score, 1e-9)
    }

    // ---- Process death -------------------------------------------------------------------------

    @Test
    fun `process death mid-round restores the route, wrong guesses, hints and input, paused`() {
        val handle = routeHandle(Difficulty.NORMAL)
        val vm = viewModel(handle)
        vm.type(names.getValue(vm.optimalRoute()[1]))
        vm.type("Japan")
        vm.onHint()
        vm.onInputChange("Tan")
        runCurrent()
        val before = vm.active()

        val after = viewModel(afterProcessDeath(handle)).active()

        assertEquals(before.startCode, after.startCode)
        assertEquals(before.targetCode, after.targetCode)
        assertEquals(before.parHops, after.parHops)
        assertEquals(before.path, after.path)
        assertEquals(before.wrongGuesses, after.wrongGuesses)
        assertEquals(before.hintsUsed, after.hintsUsed)
        assertEquals(before.revealedHopCode, after.revealedHopCode)
        assertEquals("Tan", after.input)
        assertTrue(after.paused)
    }

    @Test
    fun `a screen recreated after the round finished records it only once`() {
        val mode = borderHopMode()
        val completeQuiz = completeQuizUseCase(mode)
        val handle = routeHandle(Difficulty.NORMAL)
        val vm = viewModel(handle, mode, completeQuiz)
        vm.optimalRoute().drop(1).forEach { vm.type(names.getValue(it)) }
        val first = completedQuizzes.stored

        viewModel(afterProcessDeath(handle), mode, completeQuiz)

        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(first, completedQuizzes.stored)
    }

    // ---- Easy ----------------------------------------------------------------------------------

    private fun BorderHopGameViewModel.validOption(): String = active().let { state ->
        val distances = graph.distancesFrom(state.targetCode)
        state.options.single { distances[it] == distances.getValue(state.currentCode) - 1 && it in graph.neighboursOf(state.currentCode) }
    }

    @Test
    fun `Easy offers four options, exactly one a neighbour on a shortest route`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        val state = vm.active()

        assertEquals(4, state.options.size)
        assertEquals(4, state.options.toSet().size)
        val neighbours = state.options.filter { it in graph.neighboursOf(state.currentCode) }
        assertEquals(listOf(vm.validOption()), neighbours)
        assertTrue(state.options.none { it in state.path || it == state.targetCode })
    }

    @Test
    fun `an Easy wrong pick is a wrong guess and disables that option`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        val wrong = vm.active().options.first { it != vm.validOption() }

        vm.onOptionSelected(wrong)
        runCurrent()
        vm.onOptionSelected(wrong)
        runCurrent()

        val state = vm.active()
        assertEquals(setOf(wrong), state.wrongPicks)
        assertEquals(listOf(wrong), state.wrongGuesses)
        assertEquals(1, state.path.size)
    }

    @Test
    fun `an Easy right pick shows, then new options follow for the next position`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        val valid = vm.validOption()

        vm.onOptionSelected(valid)
        runCurrent()
        val showing = vm.active()
        assertEquals(valid, showing.currentCode)
        assertEquals(valid, showing.correctPick)
        assertFalse(showing.acceptsInput)

        advance(BorderHopGameViewModel.CORRECT_PICK_MILLIS)

        val next = vm.active()
        assertNull(next.correctPick)
        assertTrue(next.wrongPicks.isEmpty())
        assertEquals(4, next.options.size)
        assertNotEquals(showing.options, next.options)
        assertEquals(1, next.options.count { it in graph.neighboursOf(valid) })
    }

    @Test
    fun `Easy walked to the target completes, and the hint stays unavailable`() {
        val vm = viewModel(routeHandle(Difficulty.EASY))
        vm.onHint()
        runCurrent()
        assertTrue(vm.active().hintsUsed.isEmpty())

        repeat(vm.active().parHops) {
            vm.onOptionSelected(vm.validOption())
            runCurrent()
            if (!vm.active().isComplete) advance(BorderHopGameViewModel.CORRECT_PICK_MILLIS)
        }

        assertTrue(vm.active().reachedTarget)
        assertTrue(completedQuizzes.stored!!.perfectBonus)
    }

    @Test
    fun `Easy options survive process death unchanged`() {
        val handle = routeHandle(Difficulty.EASY)
        val vm = viewModel(handle)
        val wrong = vm.active().options.first { it != vm.validOption() }
        vm.onOptionSelected(wrong)
        runCurrent()
        val before = vm.active()

        val after = viewModel(afterProcessDeath(handle), seed = 42).active()

        assertEquals(before.options, after.options)
        assertEquals(before.wrongPicks, after.wrongPicks)
    }
}
