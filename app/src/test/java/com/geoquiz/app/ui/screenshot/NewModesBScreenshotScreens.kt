package com.geoquiz.app.ui.screenshot

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopQuestionGenerator
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionSet
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.mode.usca.UsCaRegionsChoiceGenerator
import com.geoquiz.app.domain.mode.usca.toRegionCountry
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import com.geoquiz.app.ui.borderhop.BorderHopGameScreen
import com.geoquiz.app.ui.borderhop.BorderHopGameViewModel
import com.geoquiz.app.ui.borderhop.BorderHopSetupScreen
import com.geoquiz.app.ui.borderhop.BorderHopSetupViewModel
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.map.MapPathCache
import com.geoquiz.app.ui.usca.US_CA_PROJECTION
import com.geoquiz.app.ui.usca.UsCaChoiceContent
import com.geoquiz.app.ui.usca.UsCaNameAllContent
import com.geoquiz.app.ui.usca.UsCaRegionsMapState
import com.geoquiz.app.ui.usca.UsCaRegionsSetupScreen
import com.geoquiz.app.ui.usca.UsCaRegionsSetupViewModel
import com.geoquiz.app.ui.usca.UsCaTapContent
import com.geoquiz.app.ui.usca.UsCaTapGameState
import com.geoquiz.app.ui.usca.loadUsCaRegionsMapState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * The screens task 6.3's screenshot tests capture for Border hop (6.1) and US states and
 * Canadian provinces (6.2), built the same way as [NewModesScreenshotScreens]: fixed state, no
 * Hilt, deterministic clocks/dispatchers/random.
 *
 * Border hop has no stateless content composable ([com.geoquiz.app.ui.borderhop.BorderHopGameScreen]'s
 * `BorderHopActiveContent` is private, D45: no map, so no screenshot-machinery reason to export
 * one), so its game screen goes through the real [BorderHopGameScreen] with a
 * [BorderHopGameViewModel] built from fakes over a small line border graph, the exact same shape
 * `BorderHopGameViewModelTest` uses ("AAA".."GGG", not real cca3 codes): a lead review of the
 * first recording found Spain's flag rendering as a blank rectangle in some captures and not
 * others (one [FlagImage][com.geoquiz.app.ui.components.FlagImage] among several in the same
 * capture intermittently not finishing its async load in time) - the exact flakiness
 * `docs/UPGRADE_PLAN.md` (session 6) already flagged and avoided for the Easy flag prompt.
 * Synthetic codes have no bundled SVG, so [FlagImage] always falls back to the same placeholder
 * painter for both its "still loading" and "error" states (`error = placeholder` in
 * [FlagImage]'s own source), making the capture pixel-identical regardless of timing - unlike a
 * real code, where "loaded in time" and "still loading" render differently. Coroutines run on
 * [com.geoquiz.app.testutil.MainDispatcherRule]'s unconfined test dispatcher, so every ViewModel
 * call below runs eagerly - no `runCurrent()` needed, matching
 * [NewModesScreenshotScreens.silhouettesNormal].
 *
 * US states and Canadian provinces' three variants each have a stateless content composable
 * (`UsCaTapContent`/`UsCaNameAllContent`/`UsCaChoiceContent`, all `internal`, visible here the
 * same way `TapMapActiveContent` is), so their screenshots are built directly from fixed state,
 * bypassing the ViewModels entirely - the Tap the map precedent. Tap and Choice render the real
 * map: the actual bundled `admin1_50m.bin` layer via a real [AssetMapRepository] (no fake; the
 * small fake layer in `UsCaTestFixtures` is for the ViewModel unit tests only), with
 * [MapPathCache.Shared] pre-built and awaited before the composable is returned, exactly like
 * [NewModesScreenshotScreens.warmMapPaths].
 */
object NewModesBScreenshotScreens {

    // ---- Real map data (shared, read-only, safe to reuse across tests) --------------------

    private fun newMapRepository(): AssetMapRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AssetMapRepository(AndroidGeoAssetSource(context.assets), Dispatchers.IO)
    }

    // ---- Border hop (task 6.1) ---------------------------------------------------------------

    /**
     * Seven countries in a line, A-B-C-...-G: the exact same shape as
     * `BorderHopGameViewModelTest`'s own fixture, deliberately *not* real cca3 codes (see the
     * class doc: a real code's flag can intermittently miss its async load in time, which is
     * exactly the flakiness this project's history already avoided for flag prompts). Synthetic
     * codes have no bundled flag SVG, so [com.geoquiz.app.ui.components.FlagImage] always shows
     * the same placeholder, deterministically.
     */
    private val borderHopCodes = listOf("AAA", "BBB", "CCC", "DDD", "EEE", "FFF", "GGG")
    private val borderHopCountries: List<Country> = borderHopCodes.map {
        TestQuizData.country(it, "Country $it", "Capital $it")
    }
    private val borderHopLineGraph = BorderGraph(
        borderHopCodes.indices.associate { i ->
            borderHopCodes[i] to listOfNotNull(borderHopCodes.getOrNull(i - 1), borderHopCodes.getOrNull(i + 1))
        }
    )

    private fun borderHopMode(): BorderHopGameMode {
        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } returns borderHopCountries
        return BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), mockk<ValidateAnswerUseCase>(relaxed = true))
    }

    private fun borderHopViewModel(difficulty: Difficulty): BorderHopGameViewModel {
        val countryRepository = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(borderHopCountries)
        }
        val borderGraphRepository = mockk<BorderGraphRepository> {
            coEvery { graph() } returns borderHopLineGraph
        }
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.showTimer } returns flowOf(true)
        every { settingsRepository.vibration } returns flowOf(true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        val mode = borderHopMode()
        val completeQuiz = CompleteQuizUseCase(
            gameModes = GameModeRegistry(setOf(mode)),
            completedQuizRepository = FakeCompletedQuizRepository(),
            savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true),
            achievementRepository = mockk<AchievementRepository>(relaxed = true),
            quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true),
            playGamesService = mockk<PlayGamesAchievementService>(relaxed = true)
        )
        val handle = SavedStateHandle(
            buildMap<String, Any?> {
                put("categoryType", "all")
                put("categoryValue", "_")
                put(BorderHopGameViewModel.ARG_DIFFICULTY, difficulty.id)
            }
        )
        return BorderHopGameViewModel(
            savedStateHandle = handle,
            gameMode = mode,
            borderGraphRepository = borderGraphRepository,
            countryRepository = countryRepository,
            completeQuiz = completeQuiz,
            settingsRepository = settingsRepository,
            clock = MonotonicClock { 0L },
            quizRandom = QuizRandom(Random(1))
        )
    }

    private fun BorderHopGameViewModel.active() =
        (uiState.value as com.geoquiz.app.ui.borderhop.BorderHopUiState.Active).state

    /** Easy: one hop made along the optimal route - a path chip, a current position and the
     * tappable neighbour list all showing real content. */
    fun borderHopMidRound(): @Composable () -> Unit {
        val vm = borderHopViewModel(Difficulty.EASY)
        val start = vm.active()
        val path = borderHopLineGraph.shortestPath(start.startCode, start.targetCode)!!
        val hopsToMake = (path.size - 1).coerceAtMost(2).coerceAtLeast(1)
        path.drop(1).take(hopsToMake).forEach { vm.onHop(it) }
        return { BorderHopGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /** Easy: the optimal route walked to the end - the target reached, outcome banner showing. */
    fun borderHopReached(): @Composable () -> Unit {
        val vm = borderHopViewModel(Difficulty.EASY)
        val start = vm.active()
        val path = borderHopLineGraph.shortestPath(start.startCode, start.targetCode)!!
        path.drop(1).forEach { vm.onHop(it) }
        return { BorderHopGameScreen(onFinished = {}, onNavigateBack = {}, viewModel = vm) }
    }

    /** The setup screen: difficulty, then the filters Border hop offers. */
    fun borderHopSetup(): @Composable () -> Unit {
        val countryRepository = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        }
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        val viewModel = BorderHopSetupViewModel(
            countryRepository = countryRepository,
            optionsBuilder = CategoryOptionsFixture.builder(),
            settingsRepository = settingsRepository,
            borderHopGameMode = borderHopMode()
        )
        return { BorderHopSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ---- US states and Canadian provinces (task 6.2) -----------------------------------------

    /** [group]'s real members from the bundled `admin1_50m.bin` layer, as synthetic [Country]s. */
    private fun usCaRegionCountries(group: UsCaRegionGroup, includeDc: Boolean): List<Country> {
        val repository = newMapRepository()
        return runBlocking {
            val layer = repository.layer(GeoLayerId.ADMIN1_50M)
            UsCaRegionSet.members(layer, group, includeDc).map { it.toRegionCountry() }
        }
    }

    /** The real admin1 map state (layer + frame) for [members], loaded via a real [AssetMapRepository]. */
    private fun usCaMapState(members: Set<String>): UsCaRegionsMapState {
        val repository = newMapRepository()
        return runBlocking { loadUsCaRegionsMapState(repository, members) }
    }

    /** Pre-builds and awaits [MapPathCache.Shared]'s entry for [mapState]'s layer, so the first
     * composition of the map canvas never meets a still-building (blank) map - the same fix as
     * [NewModesScreenshotScreens.warmMapPaths]. */
    private fun warmUsCaMapPaths(mapState: UsCaRegionsMapState) = runBlocking {
        mapState.layer?.let { MapPathCache.Shared.paths(it, US_CA_PROJECTION).await() }
    }

    /** Tap, Normal, US states (DC included): two found, one missed, one highlighted target. */
    fun usCaTapMidRound(): @Composable () -> Unit {
        val countries = usCaRegionCountries(UsCaRegionGroup.US_STATES, includeDc = true)
        val found = countries.take(2).map { it.code }.toSet()
        val missed = countries[2].code
        val target = countries[3].code
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = true)
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = countries),
            answeredCountries = found,
            missedCountries = setOf(missed)
        )
        val state = UsCaTapGameState(quizState = quizState, currentTargetCode = target)
        val mapState = usCaMapState(countries.map { it.code }.toSet())
        warmUsCaMapPaths(mapState)
        return {
            Surface(Modifier.fillMaxSize()) {
                UsCaTapContent(
                    state = state,
                    mapState = mapState,
                    difficulty = Difficulty.NORMAL,
                    timerVisible = true,
                    timerSeconds = 37,
                    viewMode = com.geoquiz.app.ui.map.MapViewMode.Map,
                    onFeatureTap = {},
                    onListSelect = {},
                    onGiveUp = {}
                )
            }
        }
    }

    /** Name all, Hard, US states: several typed in, one strike, the remaining list showing. */
    fun usCaNameAllMidRound(): @Composable () -> Unit {
        val countries = usCaRegionCountries(UsCaRegionGroup.US_STATES, includeDc = true)
        val answered = countries.take(4).map { it.code }.toSet()
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = true)
        val state = QuizState(
            quiz = Quiz(category = category, countries = countries),
            answeredCountries = answered,
            incorrectGuesses = 1
        )
        return {
            Surface(Modifier.fillMaxSize()) {
                UsCaNameAllContent(
                    state = state,
                    difficulty = Difficulty.HARD,
                    timerSeconds = 91,
                    onInputChange = {},
                    onSubmit = {},
                    onGiveUp = {}
                )
            }
        }
    }

    /** Multiple choice, Easy, US states: the highlighted target and four name options. */
    fun usCaChoiceMidRound(): @Composable () -> Unit {
        val countries = usCaRegionCountries(UsCaRegionGroup.US_STATES, includeDc = true)
        val target = countries.first()
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = true)
        val choice = UsCaRegionsChoiceGenerator.question(target, countries, countries, category, Random(1))
        val state = QuizState(
            quiz = Quiz(category = category, countries = countries),
            choice = choice
        )
        val mapState = usCaMapState(countries.map { it.code }.toSet())
        warmUsCaMapPaths(mapState)
        return {
            Surface(Modifier.fillMaxSize()) {
                UsCaChoiceContent(
                    state = state,
                    mapState = mapState,
                    onChoiceSelected = {},
                    onGiveUp = {}
                )
            }
        }
    }

    // ---- US states and Canadian provinces: setup screen ---------------------------------------

    private fun usCaSetupViewModel(): UsCaRegionsSetupViewModel {
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
        return UsCaRegionsSetupViewModel(savedStateHandle = SavedStateHandle(), settingsRepository = settingsRepository)
    }

    /** US states selected (the default): the DC-optional checkbox shows. */
    fun usCaSetupUs(): @Composable () -> Unit {
        val viewModel = usCaSetupViewModel()
        return { UsCaRegionsSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    /** Canadian provinces selected: no DC checkbox (there is no optional member). */
    fun usCaSetupCa(): @Composable () -> Unit {
        val viewModel = usCaSetupViewModel()
        viewModel.onGroupSelected(UsCaRegionGroup.CANADIAN_PROVINCES)
        return { UsCaRegionsSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }
}
