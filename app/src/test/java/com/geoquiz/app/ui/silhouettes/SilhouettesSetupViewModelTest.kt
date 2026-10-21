package com.geoquiz.app.ui.silhouettes

import android.net.Uri
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.silhouettes.SilhouetteLegibility
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.CategoryOptionsFixture
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
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SilhouettesSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val silhouettesGameMode = SilhouettesGameMode(
        CountriesQuestionGenerator(mockk(relaxed = true), mockk(relaxed = true)),
        mockk<ValidateAnswerUseCase>(),
        CalculateScoreUseCase(),
        QuizRandom(Random(1))
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

    private fun viewModel(
        countries: List<Country> = CategoryOptionsFixture.COUNTRIES,
        optionsBuilder: CategoryOptionsBuilder = CategoryOptionsFixture.builder()
    ): SilhouettesSetupViewModel {
        val vm = SilhouettesSetupViewModel(
            countryRepository = countryRepository(countries),
            optionsBuilder = optionsBuilder,
            silhouettesGameMode = silhouettesGameMode
        )
        dispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun `builds a section for every filter this mode offers, with the Countries category list's options`() {
        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals(Difficulty.NORMAL, state.difficulty)
        // Every group in the fixture has at least one country, and every one of these groups is
        // offered by SilhouettesGameMode.offers (region, subregion, the three letter positions,
        // islands, all countries); none should be hidden.
        val expectedGroups = listOf(
            CategoryGroup.ALL_COUNTRIES, CategoryGroup.REGIONS, CategoryGroup.SUBREGIONS,
            CategoryGroup.STARTING_LETTER, CategoryGroup.ENDING_LETTER, CategoryGroup.CONTAINING_LETTER,
            CategoryGroup.ISLAND_COUNTRIES
        )
        assertEquals(expectedGroups.size, state.sections.size)

        val allCountriesSection = state.sections.first()
        assertEquals(1, allCountriesSection.options.size)
        assertEquals(CategoryOptionsFixture.COUNTRIES.size, allCountriesSection.options.first().countryCount)

        val islandsSection = state.sections.last()
        assertEquals(1, islandsSection.options.size)
        assertEquals("island", islandsSection.options.first().categoryType)
        assertEquals(1, islandsSection.options.first().countryCount) // only "Solomon Islands"
    }

    @Test
    fun `a group with no options for this mode's filter is left out entirely`() {
        // No country has "island" in its name: the Islands section must not appear at all.
        val noIslands = CategoryOptionsFixture.COUNTRIES.filterNot { it.name.contains("island", ignoreCase = true) }
        val flagColorDao = mockk<FlagColorDao>(relaxed = true)
        val flagElementDao = mockk<FlagElementDao>(relaxed = true)
        coEvery { flagColorDao.getAllMappings() } returns emptyList()
        coEvery { flagElementDao.getAllMappings() } returns emptyList()

        val vm = viewModel(countries = noIslands, optionsBuilder = CategoryOptionsBuilder(flagColorDao, flagElementDao))

        assertTrue(vm.uiState.value.sections.none { section -> section.options.any { it.categoryType == "island" } })
    }

    @Test
    fun `an excluded (unrecognisable) silhouette's country is never counted, and a subregion made up only of it is left out`() {
        // Task P10-E: a filter's shown count should already match what a round of it actually
        // asks about, and a filter with nothing left over (here, a subregion of exactly one
        // excluded country) should disappear rather than offer an option that silently asks
        // about fewer countries than shown (or none at all).
        val excludedCode = SilhouetteLegibility.EXCLUDED_CCA3.first()
        val excludedCountry = Country(
            code = excludedCode,
            name = "Excluded Land",
            officialName = "Excluded Land",
            region = "Oceania",
            subregion = "Excluded Isles",
            nameLength = 13,
            capital = "Capital"
        )
        val countries = CategoryOptionsFixture.COUNTRIES + excludedCountry
        val flagColorDao = mockk<FlagColorDao>(relaxed = true)
        val flagElementDao = mockk<FlagElementDao>(relaxed = true)
        coEvery { flagColorDao.getAllMappings() } returns emptyList()
        coEvery { flagElementDao.getAllMappings() } returns emptyList()

        val vm = viewModel(countries = countries, optionsBuilder = CategoryOptionsBuilder(flagColorDao, flagElementDao))
        val sections = vm.uiState.value.sections

        val allCountriesCount = sections.first().options.first().countryCount
        assertEquals("the excluded country is never counted", CategoryOptionsFixture.COUNTRIES.size, allCountriesCount)
        assertTrue(
            "no option should offer the excluded country's own exclusive subregion",
            sections.none { section -> section.options.any { it.categoryType == "subregion" && it.categoryValue == "Excluded Isles" } }
        )
    }

    @Test
    fun `onDifficultySelected updates state without persisting it`() {
        val vm = viewModel()

        vm.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()

        assertEquals(Difficulty.HARD, vm.uiState.value.difficulty)

        val another = viewModel()
        assertEquals(Difficulty.NORMAL, another.uiState.value.difficulty)
    }

    @Test
    fun `routeFor builds a silhouettes route at the selected difficulty`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        val option = vm.uiState.value.sections.first().options.first()

        val route = vm.routeFor(option)

        assertEquals(
            ModeRoutes.start(ModeRoutes.SILHOUETTES_MODE_ID, option.categoryType, option.categoryValue, Difficulty.HARD.id),
            route
        )
    }
}
