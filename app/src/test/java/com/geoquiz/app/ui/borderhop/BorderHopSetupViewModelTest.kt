package com.geoquiz.app.ui.borderhop

import android.net.Uri
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopQuestionGenerator
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.CategoryOptionsFixture
import com.geoquiz.app.ui.navigation.ModeRoutes
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
import org.junit.Before
import org.junit.Test

/**
 * [BorderHopSetupViewModel]'s filter list: only the three groups
 * [BorderHopGameMode.offers] accepts (All countries, By region, By subregion), mirroring
 * `SilhouettesSetupViewModelTest`'s shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BorderHopSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val borderHopGameMode = BorderHopGameMode(
        mockk<BorderHopQuestionGenerator>(relaxed = true),
        mockk<ValidateAnswerUseCase>()
    )

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
        optionsBuilder: CategoryOptionsBuilder = CategoryOptionsFixture.builder()
    ): BorderHopSetupViewModel {
        val vm = BorderHopSetupViewModel(
            countryRepository = countryRepository(countries),
            optionsBuilder = optionsBuilder,
            settingsRepository = settingsRepository,
            borderHopGameMode = borderHopGameMode
        )
        dispatcher.scheduler.runCurrent()
        return vm
    }

    @Test
    fun `only builds sections for the groups Border hop offers, leaving out letter, word and flag filters`() {
        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertEquals(Difficulty.NORMAL, state.difficulty)
        // BorderHopGameMode.offers accepts AllCountries/ByRegion/BySubregion only: no starting
        // letter, ending letter, containing letter or island-countries sections, unlike
        // Silhouettes which offers all of those.
        val expectedGroups = listOf(CategoryGroup.ALL_COUNTRIES, CategoryGroup.REGIONS, CategoryGroup.SUBREGIONS)
        assertEquals(expectedGroups.size, state.sections.size)
        assertEquals(CategoryOptionsFixture.COUNTRIES.size, state.sections.first().options.first().countryCount)
    }

    @Test
    fun `onDifficultySelected updates state and remembers the default`() {
        val vm = viewModel()

        vm.onDifficultySelected(Difficulty.HARD)
        dispatcher.scheduler.runCurrent()

        assertEquals(Difficulty.HARD, vm.uiState.value.difficulty)
        coVerify { settingsRepository.setDifficulty(Difficulty.HARD) }
    }

    @Test
    fun `routeFor builds a border hop route at the selected difficulty`() {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        val option = vm.uiState.value.sections.first().options.first()

        val route = vm.routeFor(option)

        assertEquals(
            ModeRoutes.start(ModeRoutes.BORDER_HOP_MODE_ID, option.categoryType, option.categoryValue, Difficulty.HARD.id),
            route
        )
    }
}
