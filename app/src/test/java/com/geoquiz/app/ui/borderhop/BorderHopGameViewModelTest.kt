package com.geoquiz.app.ui.borderhop

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopQuestionGenerator
import com.geoquiz.app.domain.mode.borderhop.BorderHopScoring
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
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

@OptIn(ExperimentalCoroutinesApi::class)
class BorderHopGameViewModelTest {

    private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
        override fun elapsedRealtimeMillis(): Long = now
    }

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeClock()

    /** A-B-C-D-E-F-G in a line: diameter 6, matching every difficulty's hop range (D44). */
    private val codes = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF", "GGG")
    private val countries = codes.map { country(it, "Country $it", "Capital $it") }
    private val lineGraph = BorderGraph(
        codes.indices.associate { i ->
            codes[i] to listOfNotNull(codes.getOrNull(i - 1), codes.getOrNull(i + 1))
        }
    )

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>(relaxed = true)
    private val countryRepository = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
    }
    private val borderGraphRepository = mockk<BorderGraphRepository> {
        coEvery { graph() } returns lineGraph
    }
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val completedQuizzes = FakeCompletedQuizRepository()
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>(relaxed = true)
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private fun borderHopMode() = BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), validateAnswer)

    private fun completeQuizUseCase(mode: BorderHopGameMode) = CompleteQuizUseCase(
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
        coEvery { getCountriesForQuiz(any()) } returns countries
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
            if (difficulty != null) put(BorderHopGameViewModel.ARG_DIFFICULTY, difficulty.id)
        }
    )

    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun viewModel(
        handle: SavedStateHandle,
        mode: BorderHopGameMode = borderHopMode(),
        completeQuiz: CompleteQuizUseCase = completeQuizUseCase(mode),
        quizRandom: QuizRandom = QuizRandom(Random(9))
    ): BorderHopGameViewModel {
        val vm = BorderHopGameViewModel(
            savedStateHandle = handle,
            gameMode = mode,
            borderGraphRepository = borderGraphRepository,
            countryRepository = countryRepository,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = clock,
            quizRandom = quizRandom
        )
        runCurrent()
        return vm
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun BorderHopGameViewModel.active(): BorderHopState =
        (uiState.value as BorderHopUiState.Active).state

    @Test
    fun `a fresh round picks a start and target whose distance is this seed's randomPuzzle result`() {
        val seed = 9
        val expected = lineGraph.randomPuzzle(codes, BorderHopGameMode.hopRangeFor(Difficulty.NORMAL), Random(seed))!!

        val vm = viewModel(routeHandle(difficulty = Difficulty.NORMAL), quizRandom = QuizRandom(Random(seed)))
        val state = vm.active()

        assertEquals(expected.startCode, state.startCode)
        assertEquals(expected.targetCode, state.targetCode)
        assertEquals(expected.parHops, state.parHops)
        assertEquals(listOf(expected.startCode), state.path)
    }

    @Test
    fun `neighbourOptions lists the current country's land-border neighbours as full countries`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val state = vm.active()

        val expectedCodes = lineGraph.neighboursOf(state.currentCode).toSet()
        assertEquals(expectedCodes, vm.neighbourOptions().map { it.code }.toSet())
    }

    @Test
    fun `hopping to a valid neighbour extends the path and moves the current position`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val before = vm.active()
        val next = lineGraph.neighboursOf(before.currentCode).first()

        vm.onHop(next)
        runCurrent()

        val after = vm.active()
        assertEquals(before.path + next, after.path)
        assertEquals(next, after.currentCode)
    }

    @Test
    fun `hopping to a country that is not a neighbour is ignored`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val before = vm.active()
        val nonNeighbour = codes.first { it != before.currentCode && it !in lineGraph.neighboursOf(before.currentCode) }

        vm.onHop(nonNeighbour)
        runCurrent()

        assertEquals(before, vm.active())
    }

    @Test
    fun `reaching the target completes the round and records it via CompleteQuizUseCase`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val start = vm.active()
        val path = lineGraph.shortestPath(start.startCode, start.targetCode)!!

        // Walk the optimal path, one hop at a time.
        path.drop(1).forEach { code ->
            vm.onHop(code)
            runCurrent()
        }

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertTrue(finished.reachedTarget)
        assertEquals(1, completedQuizzes.saveCount)
        val stored = completedQuizzes.stored!!
        assertEquals(BorderHopGameMode.MODE_ID, stored.quizModeId)
        assertEquals(path, stored.countryCodes)
        assertEquals(path.toSet(), stored.answeredCodes.toSet())
        assertTrue("an optimal path is a perfect run", stored.perfectBonus)
        assertEquals(0, stored.incorrectGuesses)
        assertTrue("no missed items for a path puzzle", stored.missedCodes().isEmpty())
    }

    @Test
    fun `undo drops the last hop and returns to the previous country, free of charge`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val before = vm.active()
        val next = lineGraph.neighboursOf(before.currentCode).first()
        vm.onHop(next)
        runCurrent()

        vm.onUndo()
        runCurrent()

        val after = vm.active()
        assertEquals(before.path, after.path)
        assertEquals(before.currentCode, after.currentCode)
        assertTrue(after.hintsUsed.isEmpty())
    }

    @Test
    fun `undo at the very start of the round does nothing`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val before = vm.active()
        assertFalse(before.canUndo)

        vm.onUndo()
        runCurrent()

        assertEquals(before, vm.active())
    }

    @Test
    fun `a detour that is undone before finishing still gives a perfect run`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val start = vm.active()
        val optimal = lineGraph.shortestPath(start.startCode, start.targetCode)!!
        require(optimal.size >= 2) { "need at least one hop to make" }

        // Hop one step along the optimal route, then undo it (free), then walk it for real.
        vm.onHop(optimal[1])
        runCurrent()
        vm.onUndo()
        runCurrent()
        optimal.drop(1).forEach { code ->
            vm.onHop(code)
            runCurrent()
        }

        val stored = completedQuizzes.stored!!
        assertEquals(optimal, stored.countryCodes)
        assertTrue("undo is free: no wasted hop remains in the recorded path", stored.perfectBonus)
        assertEquals(0, stored.incorrectGuesses)
    }

    @Test
    fun `hopping backward and forward again without undo is a real extra hop and costs efficiency`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val start = vm.active()
        val optimal = lineGraph.shortestPath(start.startCode, start.targetCode)!!
        require(optimal.size >= 2) { "need at least one hop to make" }

        // Hop forward, then back to the start (a real hop, not Undo), then forward again, then
        // finish along the rest of the optimal route: two hops wasted.
        vm.onHop(optimal[1])
        runCurrent()
        vm.onHop(optimal[0])
        runCurrent()
        optimal.drop(1).forEach { code ->
            vm.onHop(code)
            runCurrent()
        }

        val stored = completedQuizzes.stored!!
        val parHops = optimal.size - 1
        assertEquals(parHops + 2, stored.countryCodes.size - 1)
        assertFalse("two wasted hops is not a perfect run", stored.perfectBonus)
        assertEquals(2, stored.incorrectGuesses)
    }

    @Test
    fun `onHint reveals a neighbour still on a shortest path to the target, costing nothing to check twice`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))
        val start = vm.active()

        vm.onHint()
        runCurrent()
        val afterFirst = vm.active()
        assertEquals(1, afterFirst.hintsUsed.size)
        assertEquals(HintType.SHOW_VALID_HOP, afterFirst.hintsUsed.single())
        val hinted = afterFirst.revealedHopCode!!
        assertEquals(
            lineGraph.shortestPathLength(start.currentCode, start.targetCode)!! - 1,
            lineGraph.shortestPathLength(hinted, start.targetCode)
        )

        // Tapping Hint again at the same position reveals the same thing, at no extra cost.
        vm.onHint()
        runCurrent()
        assertEquals(1, vm.active().hintsUsed.size)
    }

    @Test
    fun `hopping clears the hint reveal so the next position can be hinted again`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))
        vm.onHint()
        runCurrent()
        val hinted = vm.active().revealedHopCode!!

        vm.onHop(hinted)
        runCurrent()

        assertNull(vm.active().revealedHopCode)
    }

    @Test
    fun `giving up ends the round without reaching the target and scores zero`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val start = vm.active()
        val next = lineGraph.neighboursOf(start.currentCode).first()
        vm.onHop(next)
        runCurrent()

        vm.onGiveUp()
        runCurrent()

        val finished = vm.active()
        assertTrue(finished.isComplete)
        assertTrue(finished.gaveUp)
        assertFalse(finished.reachedTarget)
        val stored = completedQuizzes.stored!!
        assertEquals(0, stored.correct)
        assertEquals(0.0, stored.score, 1e-9)
        assertFalse(stored.perfectBonus)
    }

    @Test
    fun `a screen recreated after the round already finished records the result only once`() {
        val mode = borderHopMode()
        val completeQuiz = completeQuizUseCase(mode)
        val handle = routeHandle(difficulty = Difficulty.EASY)
        val vm = viewModel(handle, mode, completeQuiz)
        val start = vm.active()
        val path = lineGraph.shortestPath(start.startCode, start.targetCode)!!
        path.drop(1).forEach {
            vm.onHop(it)
            runCurrent()
        }
        assertEquals(1, completedQuizzes.saveCount)
        val firstResult = completedQuizzes.stored

        // The same finished round's snapshot (including its result id) comes back on a fresh
        // ViewModel, as it would after process death.
        val restoredHandle = afterProcessDeath(handle)
        viewModel(restoredHandle, mode, completeQuiz)
        runCurrent()

        assertEquals(1, completedQuizzes.saveCount)
        assertEquals(firstResult, completedQuizzes.stored)
    }

    @Test
    fun `process death mid-round restores the path, puzzle and hints used`() {
        val handle = routeHandle(difficulty = Difficulty.EASY)
        val vm = viewModel(handle)
        val start = vm.active()
        val next = lineGraph.neighboursOf(start.currentCode).first()
        vm.onHop(next)
        runCurrent()
        vm.onHint()
        runCurrent()
        val before = vm.active()

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        val after = vm2.active()

        assertEquals(before.startCode, after.startCode)
        assertEquals(before.targetCode, after.targetCode)
        assertEquals(before.parHops, after.parHops)
        assertEquals(before.path, after.path)
        assertEquals(before.hintsUsed, after.hintsUsed)
        assertEquals(before.revealedHopCode, after.revealedHopCode)
        assertTrue("an unfinished restored round comes back paused", after.paused)
    }

    @Test
    fun `a hint reveal survives process death, so re-tapping Hint after restore does not double-charge`() {
        val handle = routeHandle(difficulty = Difficulty.EASY)
        val vm = viewModel(handle)
        vm.onHint()
        runCurrent()
        val hintedBefore = vm.active().revealedHopCode!!
        assertEquals(1, vm.active().hintsUsed.size)

        val restoredHandle = afterProcessDeath(handle)
        val vm2 = viewModel(restoredHandle)
        assertEquals(hintedBefore, vm2.active().revealedHopCode)
        assertEquals(1, vm2.active().hintsUsed.size)

        // Tapping Hint again after restore must not add a second, already-paid-for entry.
        vm2.onHint()
        runCurrent()
        assertEquals(1, vm2.active().hintsUsed.size)
        assertEquals(hintedBefore, vm2.active().revealedHopCode)
    }

    @Test
    fun `no valid puzzle for the requested hop range publishes Unavailable`() {
        // The candidate pool only reaches as far as a 3-hop distance, but Hard needs 6+.
        coEvery { getCountriesForQuiz(any()) } returns countries.take(4)

        val vm = viewModel(routeHandle(difficulty = Difficulty.HARD))

        assertEquals(BorderHopUiState.Unavailable, vm.uiState.value)
    }

    @Test
    fun `hints used reduce the score and drop the perfect bonus even on an optimal path`() {
        val vm = viewModel(routeHandle(difficulty = Difficulty.EASY))
        val start = vm.active()
        val path = lineGraph.shortestPath(start.startCode, start.targetCode)!!

        vm.onHint()
        runCurrent()
        path.drop(1).forEach {
            vm.onHop(it)
            runCurrent()
        }

        val stored = completedQuizzes.stored!!
        assertFalse("any hint drops the perfect bonus (D37)", stored.perfectBonus)
        val withoutHint = BorderHopScoring.score(
            com.geoquiz.app.domain.model.QuizState(
                quiz = com.geoquiz.app.domain.model.Quiz(
                    category = com.geoquiz.app.domain.model.QuizCategory.AllCountries,
                    countries = path.map { code -> countries.first { it.code == code } }
                ),
                answeredCountries = path.toSet(),
                incorrectGuesses = 0,
                currentInput = BorderHopScoring.FLAG_REACHED,
                isComplete = true
            )
        )
        val expected = com.geoquiz.app.domain.mode.HintCosts.applyTo(withoutHint, listOf(HintType.SHOW_VALID_HOP))
        assertEquals(expected.score, stored.score, 1e-9)
    }
}
