package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
        UsCaRegionsSetupViewModel(handle)

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

    /** P10-D2: three groups are offered (United States, Canada, Australia); switching to
     * Australia is reflected in the state exactly like Canada. */
    @Test
    fun `switching group to Australia is reflected in the state`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onGroupSelected(UsCaRegionGroup.AU_STATES)
            val state = awaitItem()
            assertEquals(UsCaRegionGroup.AU_STATES, state.group)
        }
    }

    @Test
    fun `every group is offered as a route value`() {
        assertEquals(setOf("us", "ca", "au"), UsCaRegionGroup.entries.map { it.routeValue }.toSet())
    }

    @Test
    fun `selecting Australia builds a start route carrying the Australian label`() = runTest {
        val vm = viewModel()
        vm.uiState.test { awaitItem() }
        vm.onGroupSelected(UsCaRegionGroup.AU_STATES)
        val route = vm.buildStartRoute()
        assertTrue(route.contains(UsCaRegionsCategory.LABEL_AU))
    }

    @Test
    fun `selecting a difficulty updates the state but is never persisted`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onDifficultySelected(Difficulty.HARD)
            assertEquals(Difficulty.HARD, awaitItem().difficulty)
        }

        // A fresh instance of this screen starts back at Normal (P10-G): nothing was persisted.
        viewModel().uiState.test {
            assertEquals(Difficulty.NORMAL, awaitItem().difficulty)
        }
    }
}
