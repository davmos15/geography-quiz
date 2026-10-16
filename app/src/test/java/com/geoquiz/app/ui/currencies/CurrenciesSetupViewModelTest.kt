package com.geoquiz.app.ui.currencies

import android.net.Uri
import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.mode.currencies.CurrencyData
import com.geoquiz.app.domain.mode.currencies.CurrencyEntry
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.GetRegionMembersUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
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

/**
 * Task 7.1 code review item 1/5: the setup screen previously showed a raw country count per
 * filter, which could overstate (a shared currency counts once for several countries) or be
 * wrong entirely (a filter with real countries but zero askable currencies still looked normal).
 * There was also no test for this ViewModel at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrenciesSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    /**
     * [CategoryOptionsFixture.COUNTRIES] (17 countries), with currencies assigned so the real
     * currency count can differ from the raw country count, and one deliberate dead end:
     * - Western Europe (FRA, DEU) shares one currency (EUR): 2 countries, 1 currency.
     * - Eastern Asia (MAC only) has an orphan primary currency with no matching entry at all
     *   (like the real KID/TVD): 1 country, 0 currencies - must be hidden entirely.
     * - Every other country has its own distinct non-shared currency.
     */
    private val eur = CurrencyEntry("EUR", "Euro", "€", listOf("DEU", "FRA"), shared = true, fact = null)
    private val currencyData = CurrencyData(
        entries = listOf(eur) + listOf(
            "GBR" to "GBP", "ISL" to "ISK", "KAZ" to "KZT", "PHL" to "PHP", "PNG" to "PGK",
            "SLB" to "SBD", "AUS" to "AUD", "NZL" to "NZD", "ZAF" to "ZAR", "MOZ" to "MZN",
            "TCD" to "XAF", "DJI" to "DJF", "MEX" to "MXN", "ARG" to "ARS"
        ).map { (country, code) -> CurrencyEntry(code, code, code, listOf(country), shared = false, fact = null) },
        primaryCurrency = mapOf(
            "FRA" to "EUR", "DEU" to "EUR", "GBR" to "GBP", "ISL" to "ISK", "KAZ" to "KZT",
            "PHL" to "PHP", "MAC" to "MOP", // MOP: deliberately no matching entry (orphan).
            "PNG" to "PGK", "SLB" to "SBD", "AUS" to "AUD", "NZL" to "NZD", "ZAF" to "ZAR",
            "MOZ" to "MZN", "TCD" to "XAF", "DJI" to "DJF", "MEX" to "MXN", "ARG" to "ARS"
        )
    )
    private val currencyRepository = mockk<CurrencyRepository> {
        coEvery { currencies() } returns currencyData
        every { cachedOrNull() } returns currencyData
    }

    private fun currencyMode(countries: List<Country>): CurrencyGameMode {
        val countryRepository = countryRepository(countries)
        val getCountriesForQuiz = GetCountriesForQuizUseCase(countryRepository, mockk<GetRegionMembersUseCase>(relaxed = true))
        val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
        return CurrencyGameMode(
            CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
            countryRepository,
            currencyRepository,
            mockk<ValidateAnswerUseCase>(),
            CalculateScoreUseCase()
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { firstArg() }
        every { settingsRepository.difficulty } returns flowOf(Difficulty.NORMAL)
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
        optionsBuilder: com.geoquiz.app.ui.category.CategoryOptionsBuilder = CategoryOptionsFixture.builder()
    ): CurrenciesSetupViewModel {
        val mode = currencyMode(countries)
        val vm = CurrenciesSetupViewModel(
            countryRepository = countryRepository(countries),
            optionsBuilder = optionsBuilder,
            settingsRepository = settingsRepository,
            currencyGameMode = mode
        )
        dispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun `All countries shows the real currency count, not the raw country count (review item 1)`() {
        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        val allCountriesSection = state.sections.first { section ->
            section.options.any { it.option.categoryType == "all" }
        }
        val allCountriesOption = allCountriesSection.options.single()
        // 17 countries, but Western Europe's EUR collapses FRA+DEU into one item and MAC's MOP
        // has no entry at all: 17 - 1 (DEU folded into EUR) - 1 (MAC) = 15 real currency items.
        assertEquals(17, allCountriesOption.option.countryCount)
        assertEquals(15, allCountriesOption.currencyCount)
    }

    @Test
    fun `a region's currency count differs from its country count when it shares a currency (review item 1)`() {
        val vm = viewModel()
        val state = vm.uiState.value

        val regionSection = state.sections.first { section ->
            section.options.any { it.option.categoryType == "region" && it.option.name == "Europe" }
        }
        val europe = regionSection.options.first { it.option.name == "Europe" }
        // FRA, DEU, GBR, ISL: 4 countries, but FRA/DEU collapse into one EUR item: 3 currencies.
        assertEquals(4, europe.option.countryCount)
        assertEquals(3, europe.currencyCount)
    }

    @Test
    fun `a subregion with countries but zero askable currencies is hidden entirely (review item 1)`() {
        val vm = viewModel()
        val state = vm.uiState.value

        val subregionSection = state.sections.first { section ->
            section.options.any { it.option.categoryType == "subregion" }
        }
        assertTrue(
            "Eastern Asia (Macau only, orphan currency) must never be offered - it would start a 0/0 round",
            subregionSection.options.none { it.option.name == "Eastern Asia" }
        )
        // Sibling subregions with real currencies are unaffected.
        assertTrue(subregionSection.options.any { it.option.name == "Western Europe" })
    }

    @Test
    fun `a subregion that shares a currency across its only two countries still shows exactly one`() {
        val vm = viewModel()
        val state = vm.uiState.value

        val subregionSection = state.sections.first { section ->
            section.options.any { it.option.categoryType == "subregion" }
        }
        val westernEurope = subregionSection.options.first { it.option.name == "Western Europe" }
        assertEquals(2, westernEurope.option.countryCount)
        assertEquals(1, westernEurope.currencyCount)
    }

    @Test
    fun `onDifficultySelected updates state and remembers the default`() {
        val vm = viewModel()

        vm.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()

        assertEquals(Difficulty.HARD, vm.uiState.value.difficulty)
    }

    @Test
    fun `routeFor builds a currencies route at the selected difficulty from the inner option`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        val filterOption = vm.uiState.value.sections.first().options.first()

        val route = vm.routeFor(filterOption.option)

        assertEquals(
            CurrenciesRoutes.createRoute(filterOption.option.categoryType, filterOption.option.categoryValue, Difficulty.HARD.id),
            route
        )
    }
}
