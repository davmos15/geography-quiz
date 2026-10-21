package com.geoquiz.app.ui.borderhop

import android.net.Uri
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopQuestionGenerator
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.category.QuizOptionInfo
import com.geoquiz.app.ui.navigation.ModeRoutes
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [BorderHopSetupViewModel]'s filter list: only the three groups
 * [BorderHopGameMode.offers] accepts (All countries, By region, By subregion), mirroring
 * `SilhouettesSetupViewModelTest`'s shape, plus the task 9.2 review fix: an option that passes
 * `offers()` is still hidden unless [BorderGraph.hasPuzzle] says a puzzle actually exists for it
 * at the selected difficulty.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BorderHopSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /**
     * A small hand-built border graph over [CategoryOptionsFixture.COUNTRIES] (task 9.2 review),
     * a single path in this order: GBR-KAZ-FRA-PHL-ISL-MAC-DEU-ZAF-MOZ-TCD-DJI-MEX-ARG, with
     * PNG/SLB/AUS/NZL left as islands (no edges at all, same exclusion D44 gives the real graph).
     * Shortest-path distance is just the position difference along the path, chosen so that:
     * - All countries spans every tier (e.g. GBR-PHL = 2 hops, GBR-ISL = 4, GBR-DEU = 6).
     * - Europe (GBR, FRA, ISL, DEU) also spans every tier (GBR-DEU = 6).
     * - Asia (KAZ, PHL, MAC) only reaches Easy/Normal (KAZ-PHL = 2, KAZ-MAC = 4).
     * - Africa (ZAF, MOZ, TCD, DJI) only reaches Easy (its widest pair, ZAF-DJI, is 3 hops).
     * - Americas (MEX, ARG, 1 hop) and Oceania (all islands) never have a puzzle.
     * - Subregions Western Europe (FRA-DEU = 4) and Northern Europe (GBR-ISL = 4) are Normal-only;
     *   Eastern Africa (MOZ-DJI = 2) is Easy-only; every other subregion in the fixture is a
     *   single country, so it never has a puzzle (same as a too-small component in the real data).
     */
    private val borderGraph = BorderGraph(
        mapOf(
            "GBR" to listOf("KAZ"),
            "KAZ" to listOf("GBR", "FRA"),
            "FRA" to listOf("KAZ", "PHL"),
            "PHL" to listOf("FRA", "ISL"),
            "ISL" to listOf("PHL", "MAC"),
            "MAC" to listOf("ISL", "DEU"),
            "DEU" to listOf("MAC", "ZAF"),
            "ZAF" to listOf("DEU", "MOZ"),
            "MOZ" to listOf("ZAF", "TCD"),
            "TCD" to listOf("MOZ", "DJI"),
            "DJI" to listOf("TCD", "MEX"),
            "MEX" to listOf("DJI", "ARG"),
            "ARG" to listOf("MEX"),
            "PNG" to emptyList(),
            "SLB" to emptyList(),
            "AUS" to emptyList(),
            "NZL" to emptyList()
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
        Dispatchers.resetMain()
    }

    private fun countryRepository(countries: List<Country>) = mockk<CountryRepository> {
        every { getAllCountries() } returns flowOf(countries)
    }

    /** The real [BorderHopGameMode], wired to the real [GetCountriesForQuizUseCase] filters. */
    private fun gameMode(countries: List<Country>): BorderHopGameMode {
        val getCountriesForQuiz = GetCountriesForQuizUseCase(
            countryRepository(countries),
            mockk<GetRegionMembersUseCase>(relaxed = true)
        )
        return BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), mockk<ValidateAnswerUseCase>())
    }

    private fun viewModel(
        countries: List<Country> = CategoryOptionsFixture.COUNTRIES,
        optionsBuilder: CategoryOptionsBuilder = CategoryOptionsFixture.builder(),
        graph: BorderGraph = borderGraph
    ): BorderHopSetupViewModel {
        val vm = BorderHopSetupViewModel(
            countryRepository = countryRepository(countries),
            optionsBuilder = optionsBuilder,
            borderHopGameMode = gameMode(countries),
            borderGraphRepository = mockk<BorderGraphRepository> { coEvery { this@mockk.graph() } returns graph },
            defaultDispatcher = Dispatchers.Unconfined
        )
        dispatcher.scheduler.runCurrent()
        return vm
    }

    private fun optionsOf(state: BorderHopSetupUiState, group: CategoryGroup): List<QuizOptionInfo> =
        state.sections.firstOrNull { section ->
            section.titleRes == when (group) {
                CategoryGroup.ALL_COUNTRIES -> com.geoquiz.app.R.string.border_hop_filter_all_countries
                CategoryGroup.REGIONS -> com.geoquiz.app.R.string.border_hop_filter_by_region
                CategoryGroup.SUBREGIONS -> com.geoquiz.app.R.string.border_hop_filter_by_subregion
                else -> -1
            }
        }?.options.orEmpty()

    private fun names(options: List<QuizOptionInfo>): Set<String> = options.map { it.name }.toSet()

    @Test
    fun `only builds sections for the groups Border hop offers, leaving out letter, word and flag filters`() {
        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals(Difficulty.NORMAL, state.difficulty)
        // BorderHopGameMode.offers accepts AllCountries/ByRegion/BySubregion only: no starting
        // letter, ending letter, containing letter or island-countries sections, unlike
        // Silhouettes which offers all of those.
        val groupTitles = setOf(
            com.geoquiz.app.R.string.border_hop_filter_all_countries,
            com.geoquiz.app.R.string.border_hop_filter_by_region,
            com.geoquiz.app.R.string.border_hop_filter_by_subregion
        )
        assertTrue(state.sections.all { it.titleRes in groupTitles })
        assertEquals(CategoryOptionsFixture.COUNTRIES.size, optionsOf(state, CategoryGroup.ALL_COUNTRIES).first().countryCount)
    }

    @Test
    fun `All countries is always offered, at every difficulty`() {
        val vm = viewModel()
        for (difficulty in Difficulty.entries) {
            vm.onDifficultySelected(difficulty)
            dispatcher.scheduler.runCurrent()
            assertEquals(1, optionsOf(vm.uiState.value, CategoryGroup.ALL_COUNTRIES).size)
        }
    }

    @Test
    fun `hides a region or subregion with no puzzle at the selected difficulty - Easy`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.EASY)
        dispatcher.scheduler.runCurrent()
        val state = vm.uiState.value

        // Europe (span 6) and Asia (span 4) and Africa (span 3) all reach Easy's 2-3 range.
        // Americas (1 hop) and Oceania (all islands) never have a puzzle, at any tier.
        assertEquals(setOf("Europe", "Asia", "Africa"), names(optionsOf(state, CategoryGroup.REGIONS)))
        // Only Eastern Africa (2 hops) is in Easy's range; Western/Northern Europe are 4 hops
        // (Normal-only) and every other fixture subregion is a single country.
        assertEquals(setOf("Eastern Africa"), names(optionsOf(state, CategoryGroup.SUBREGIONS)))
    }

    @Test
    fun `hides a region or subregion with no puzzle at the selected difficulty - Normal`() {
        // Normal is the setup screen's starting tier (P10-G): no selection needed.
        val vm = viewModel()
        val state = vm.uiState.value

        // Africa's widest pair is 3 hops, short of Normal's 4-5: Africa drops out here.
        assertEquals(setOf("Europe", "Asia"), names(optionsOf(state, CategoryGroup.REGIONS)))
        assertEquals(setOf("Western Europe", "Northern Europe"), names(optionsOf(state, CategoryGroup.SUBREGIONS)))
    }

    @Test
    fun `hides every subregion at Hard, matching the review brief's real-data finding`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()
        val state = vm.uiState.value

        // Only Europe's 6-hop pair (GBR-DEU) reaches Hard's 6+ range.
        assertEquals(setOf("Europe"), names(optionsOf(state, CategoryGroup.REGIONS)))
        // No fixture subregion has a 6+ hop pair: the whole section is left out, like an empty
        // classic category.
        assertTrue(optionsOf(state, CategoryGroup.SUBREGIONS).isEmpty())
    }

    @Test
    fun `onDifficultySelected recomputes which options are offered`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.EASY)
        dispatcher.scheduler.runCurrent()
        assertEquals(setOf("Eastern Africa"), names(optionsOf(vm.uiState.value, CategoryGroup.SUBREGIONS)))

        vm.onDifficultySelected(Difficulty.NORMAL)
        dispatcher.scheduler.runCurrent()

        assertEquals(Difficulty.NORMAL, vm.uiState.value.difficulty)
        assertEquals(
            setOf("Western Europe", "Northern Europe"),
            names(optionsOf(vm.uiState.value, CategoryGroup.SUBREGIONS))
        )
    }

    @Test
    fun `the difficulty always starts at Normal, even after a previous instance picked another tier`() {
        val first = viewModel()
        first.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()
        assertEquals(Difficulty.HARD, first.uiState.value.difficulty)

        val second = viewModel()
        assertEquals(Difficulty.NORMAL, second.uiState.value.difficulty)
    }

    @Test
    fun `routeFor builds a border hop route at the selected difficulty`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()
        val option = optionsOf(vm.uiState.value, CategoryGroup.ALL_COUNTRIES).first()

        val route = vm.routeFor(option)

        assertEquals(
            ModeRoutes.start(ModeRoutes.BORDER_HOP_MODE_ID, option.categoryType, option.categoryValue, Difficulty.HARD.id),
            route
        )
    }
}
