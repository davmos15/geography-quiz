package com.geoquiz.app.ui.tapmap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
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

/** The region and difficulty picker (task 5.2): only the "find" variant exists yet. */
class TapMapSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Before
    fun setUp() {
        // Screen.TapMap.createRoute Uri.encode()s the category value; plain JUnit (no
        // Robolectric here) needs it stubbed, as in TapMapGameViewModelTest.
        mockkStatic(Uri::class)
        every { Uri.encode(any()) } answers { firstArg() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        TapMapSetupViewModel(handle)

    @Test
    fun `starts on World with every preset offered`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(RegionPreset.WORLD, state.selectedPreset)
            assertEquals(RegionPreset.entries, state.presets)
            assertEquals(Difficulty.NORMAL, state.difficulty)
        }
    }

    @Test
    fun `selecting a preset updates the start route`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onPresetSelected(RegionPreset.EUROPE)
            val state = awaitItem()
            assertEquals(RegionPreset.EUROPE, state.selectedPreset)
        }
        val route = vm.buildStartRoute()
        assertTrue(route.startsWith("tap_map/find/map_region/europe"))
    }

    @Test
    fun `starting builds a route for the tap map mode, find variant`() = runTest {
        val vm = viewModel()
        vm.uiState.test { awaitItem() }

        val route = vm.buildStartRoute()
        assertTrue(route.contains("tap_map"))
        assertTrue(route.startsWith("tap_map/${TapMapGameMode.VARIANT_FIND}/map_region/world"))
    }

    @Test
    fun `picking a difficulty updates the state but is never persisted`() = runTest {
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

    @Test
    fun `starts on the find variant with every difficulty offered`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(TapMapGameMode.VARIANT_FIND, state.variant)
            assertEquals(Difficulty.entries, state.availableDifficulties)
        }
    }

    @Test
    fun `selecting name all updates the start route's variant segment`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onVariantSelected(TapMapGameMode.VARIANT_NAME_ALL)
            val state = awaitItem()
            assertEquals(TapMapGameMode.VARIANT_NAME_ALL, state.variant)
        }
        val route = vm.buildStartRoute()
        assertTrue(route.startsWith("tap_map/${TapMapGameMode.VARIANT_NAME_ALL}/map_region/world"))
    }

    @Test
    fun `name all hides easy from the available difficulties`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onVariantSelected(TapMapGameMode.VARIANT_NAME_ALL)
            val state = awaitItem()
            assertEquals(listOf(Difficulty.NORMAL, Difficulty.HARD), state.availableDifficulties)
            assertTrue(Difficulty.EASY !in state.availableDifficulties)
        }
    }

    @Test
    fun `an easy selection is shown as normal while name all is selected, display only`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onDifficultySelected(Difficulty.EASY)
            awaitItem()

            vm.onVariantSelected(TapMapGameMode.VARIANT_NAME_ALL)
            val nameAllState = awaitItem()
            assertEquals(Difficulty.NORMAL, nameAllState.difficulty)

            // Switching the same view model back to find offers Easy again: the selection itself
            // was never overwritten.
            vm.onVariantSelected(TapMapGameMode.VARIANT_FIND)
            val findState = awaitItem()
            assertEquals(Difficulty.EASY, findState.difficulty)
        }
    }

    @Test
    fun `the selected variant survives process death via the saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.uiState.test {
            awaitItem()
            vm.onVariantSelected(TapMapGameMode.VARIANT_NAME_ALL)
            awaitItem()
        }

        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = viewModel(restoredHandle)
        restored.uiState.test {
            assertEquals(TapMapGameMode.VARIANT_NAME_ALL, awaitItem().variant)
        }
    }

    @Test
    fun `the selected preset survives process death via the saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.uiState.test {
            awaitItem()
            vm.onPresetSelected(RegionPreset.OCEANIA)
            awaitItem()
        }

        // A fresh handle built from the same entries, as `afterProcessDeath` does elsewhere.
        val restoredHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
        val restored = viewModel(restoredHandle)
        restored.uiState.test {
            assertEquals(RegionPreset.OCEANIA, awaitItem().selectedPreset)
        }
    }
}
