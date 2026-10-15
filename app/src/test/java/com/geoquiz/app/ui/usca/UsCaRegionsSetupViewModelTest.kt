package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UsCaRegionsSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val difficultyFlow = MutableStateFlow(Difficulty.NORMAL)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { difficulty } returns difficultyFlow
    }

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        UsCaRegionsSetupViewModel(handle, settingsRepository)

    @Test
    fun `defaults to US states with DC, the Tap variant and Normal`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(UsCaRegionGroup.US_STATES, state.group)
            assertTrue(state.includeDc)
            assertEquals(UsCaRegionsGameMode.VARIANT_TAP, state.variant)
            assertEquals(Difficulty.NORMAL, state.difficulty)
        }
    }

    @Test
    fun `switching to Multiple choice offers only Easy`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onVariantSelected(UsCaRegionsGameMode.VARIANT_CHOICE)
            val state = awaitItem()
            assertEquals(listOf(Difficulty.EASY), state.availableDifficulties)
            assertEquals(Difficulty.EASY, state.difficulty)
        }
    }

    @Test
    fun `switching to Name all offers Normal and Hard only`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onVariantSelected(UsCaRegionsGameMode.VARIANT_NAME_ALL)
            val state = awaitItem()
            assertEquals(listOf(Difficulty.NORMAL, Difficulty.HARD), state.availableDifficulties)
        }
    }

    @Test
    fun `the DC checkbox feeds the start route's category value`() = runTest {
        val vm = viewModel()
        vm.uiState.test { awaitItem() }
        vm.onIncludeDcChanged(false)
        val route = vm.buildStartRoute()
        assertTrue(route.contains(UsCaRegionsCategory.LABEL_US_NO_DC))
    }

    @Test
    fun `switching group to Canada is reflected in the state`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onGroupSelected(UsCaRegionGroup.CANADIAN_PROVINCES)
            val state = awaitItem()
            assertEquals(UsCaRegionGroup.CANADIAN_PROVINCES, state.group)
        }
    }

    @Test
    fun `selecting a difficulty is remembered via SettingsRepository`() = runTest {
        val vm = viewModel()
        vm.onDifficultySelected(Difficulty.HARD)
        coVerify { settingsRepository.setDifficulty(Difficulty.HARD) }
    }
}
