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
import com.geoquiz.app.domain.map.GeoLayerId
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
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.borderhop.BorderHopActions
import com.geoquiz.app.ui.borderhop.BorderHopActiveContent
import com.geoquiz.app.ui.borderhop.BorderHopFeedback
import com.geoquiz.app.ui.borderhop.BorderHopGameViewModel
import com.geoquiz.app.ui.borderhop.BorderHopMapState
import com.geoquiz.app.ui.borderhop.BorderHopRouteFrame
import com.geoquiz.app.ui.borderhop.BorderHopState
import com.geoquiz.app.ui.tapmap.withMinimumFrameSize
import com.geoquiz.app.ui.borderhop.BorderHopSetupScreen
import com.geoquiz.app.ui.borderhop.BorderHopSetupViewModel
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.map.MapPathCache
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
 * Border hop (map version, task P10-C) renders its stateless `BorderHopActiveContent` from fixed
 * state over the real bundled admin0 layers (pre-built in [MapPathCache.Shared]), with real cca3
 * codes: the route is text only, so no [FlagImage][com.geoquiz.app.ui.components.FlagImage]
 * timing can make a capture flaky (the reason the old list fixture used synthetic codes).
 *
 * States and territories' three variants each have a stateless content composable
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

    // ---- Border hop (task 6.1; map version task P10-C) ----------------------------------------

    /** Real cca3 codes and names: the route is text only (no flags), so real codes are safe here. */
    private val borderHopNames = mapOf(
        "ZAF" to "South Africa", "BWA" to "Botswana", "ZMB" to "Zambia", "TZA" to "Tanzania",
        "UGA" to "Uganda", "AGO" to "Angola", "MWI" to "Malawi", "KEN" to "Kenya"
    )

    private fun borderHopName(code: String) = borderHopNames[code] ?: code

    /** The real bundled admin0 layers, loaded and their paths pre-built like the Tap the map shots. */
    private fun borderHopMapState(): BorderHopMapState {
        val repository = newMapRepository()
        val state = runBlocking {
            BorderHopMapState(
                coarse = repository.layer(GeoLayerId.ADMIN0_110M),
                detail = repository.layer(GeoLayerId.ADMIN0_50M),
                tapZones = repository.layer(GeoLayerId.TAP_ZONES)
            )
        }
        runBlocking {
            listOfNotNull(state.coarse, state.detail, state.tapZones).forEach {
                MapPathCache.Shared.paths(it, RegionPreset.WORLD.projection).await()
            }
        }
        return state
    }

    private fun borderHopFrame(mapState: BorderHopMapState, state: BorderHopState): BorderHopRouteFrame {
        val codes = (state.path + state.targetCode).toSet()
        val rect = RegionPreset.WORLD.frame(mapState.detail!!, codes)!!
            .withMinimumFrameSize(BorderHopGameViewModel.MIN_FRAME_WIDTH, BorderHopGameViewModel.MIN_FRAME_HEIGHT)
        return BorderHopRouteFrame(codes, codes, rect)
    }

    private fun borderHopContent(state: BorderHopState): @Composable () -> Unit {
        val mapState = borderHopMapState()
        val frame = borderHopFrame(mapState, state)
        return {
            Surface(Modifier.fillMaxSize()) {
                BorderHopActiveContent(
                    state = state,
                    mapState = mapState,
                    routeFrame = frame,
                    countryName = ::borderHopName,
                    timerVisible = true,
                    timerSeconds = 42,
                    actions = BorderHopActions()
                )
            }
        }
    }

    private fun borderHopState(difficulty: Difficulty, target: String, path: List<String>, par: Int) = BorderHopState(
        category = QuizCategory.AllCountries,
        difficulty = difficulty,
        startCode = "ZAF",
        targetCode = target,
        parHops = par,
        path = path
    )

    /** Normal: South Africa to Uganda, one hop made, typing with a suggestion showing. */
    fun borderHopMidRound(): @Composable () -> Unit = borderHopContent(
        borderHopState(Difficulty.NORMAL, target = "UGA", path = listOf("ZAF", "BWA"), par = 4).copy(
            input = "Zam",
            suggestions = listOf("ZMB"),
            feedback = BorderHopFeedback.Hopped("BWA")
        )
    )

    /** Easy: four options, one wrong pick (orange + cross, disabled, flashed on the map). */
    fun borderHopEasy(): @Composable () -> Unit = borderHopContent(
        borderHopState(Difficulty.EASY, target = "ZMB", path = listOf("ZAF"), par = 2).copy(
            options = listOf("MWI", "BWA", "AGO", "TZA"),
            wrongPicks = setOf("AGO"),
            wrongGuesses = listOf("AGO"),
            flashWrongCode = "AGO",
            feedback = BorderHopFeedback.NotNeighbour("AGO", "ZAF")
        )
    )

    /** Normal: the target reached along the shortest route. */
    fun borderHopReached(): @Composable () -> Unit = borderHopContent(
        borderHopState(Difficulty.NORMAL, target = "UGA", path = listOf("ZAF", "BWA", "ZMB", "TZA", "UGA"), par = 4).copy(
            isComplete = true,
            feedback = BorderHopFeedback.Hopped("UGA")
        )
    )

    private fun borderHopMode(): BorderHopGameMode {
        val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
        coEvery { getCountriesForQuiz(any()) } returns emptyList()
        return BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), mockk<ValidateAnswerUseCase>(relaxed = true))
    }

    /** The setup screen: difficulty, then the filters Border hop offers. */
    fun borderHopSetup(): @Composable () -> Unit {
        val countryRepository = mockk<CountryRepository> {
            every { getAllCountries() } returns flowOf(CategoryOptionsFixture.COUNTRIES)
        }
        val viewModel = BorderHopSetupViewModel(
            countryRepository = countryRepository,
            optionsBuilder = CategoryOptionsFixture.builder(),
            borderHopGameMode = borderHopMode(),
            // Every filter counts as playable here; feasibility has its own tests.
            borderGraphRepository = mockk<BorderGraphRepository> {
                coEvery { graph() } returns mockk { every { hasPuzzle(any(), any()) } returns true }
            }
        )
        return { BorderHopSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }

    // ---- States and territories (task 6.2) -----------------------------------------

    /** [group]'s real members from the bundled `admin1_50m.bin` layer, as synthetic [Country]s. */
    private fun usCaRegionCountries(group: UsCaRegionGroup, includeDc: Boolean): List<Country> {
        val repository = newMapRepository()
        return runBlocking {
            val layer = repository.layer(GeoLayerId.ADMIN1_50M)
            UsCaRegionSet.members(layer, group, includeDc).map { it.toRegionCountry() }
        }
    }

    /** The real admin1 map state (layer + frame) for [members] of [group], loaded via a real
     * [AssetMapRepository]. */
    private fun usCaMapState(members: Set<String>, group: UsCaRegionGroup = UsCaRegionGroup.US_STATES): UsCaRegionsMapState {
        val repository = newMapRepository()
        return runBlocking { loadUsCaRegionsMapState(repository, members, group) }
    }

    /** Pre-builds and awaits [MapPathCache.Shared]'s entry for [mapState]'s layer, so the first
     * composition of the map canvas never meets a still-building (blank) map - the same fix as
     * [NewModesScreenshotScreens.warmMapPaths]. */
    private fun warmUsCaMapPaths(mapState: UsCaRegionsMapState) = runBlocking {
        mapState.layer?.let { MapPathCache.Shared.paths(it, mapState.projection).await() }
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

    /**
     * Tap, Normal, Australian states and territories (P10-R2 L6): two found, one missed, one
     * highlighted target, Australia's own LAEA projection (D74). No DC-style optional member.
     */
    fun usCaTapMidRoundAu(): @Composable () -> Unit {
        val countries = usCaRegionCountries(UsCaRegionGroup.AU_STATES, includeDc = false)
        val found = countries.take(2).map { it.code }.toSet()
        val missed = countries[2].code
        val target = countries[3].code
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.AU_STATES, includeDc = false)
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = countries),
            answeredCountries = found,
            missedCountries = setOf(missed)
        )
        val state = UsCaTapGameState(quizState = quizState, currentTargetCode = target)
        val mapState = usCaMapState(countries.map { it.code }.toSet(), group = UsCaRegionGroup.AU_STATES)
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

    // ---- States and territories: setup screen ---------------------------------------

    private fun usCaSetupViewModel(): UsCaRegionsSetupViewModel =
        UsCaRegionsSetupViewModel(savedStateHandle = SavedStateHandle())

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

    /** Australian states and territories selected (P10-R2 L6, D74): no DC checkbox either. */
    fun usCaSetupAu(): @Composable () -> Unit {
        val viewModel = usCaSetupViewModel()
        viewModel.onGroupSelected(UsCaRegionGroup.AU_STATES)
        return { UsCaRegionsSetupScreen(onStart = {}, onNavigateBack = {}, viewModel = viewModel) }
    }
}
