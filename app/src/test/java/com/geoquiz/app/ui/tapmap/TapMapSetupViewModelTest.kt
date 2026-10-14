package com.geoquiz.app.ui.tapmap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
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

/** The region and difficulty picker (task 5.2): only the "find" variant exists yet. */
class TapMapSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val difficultyFlow = MutableStateFlow(Difficulty.NORMAL)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { difficulty } returns difficultyFlow
    }

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
        TapMapSetupViewModel(handle, settingsRepository)

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
    fun `picking a difficulty remembers it as the default`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onDifficultySelected(Difficulty.HARD)
            difficultyFlow.value = Difficulty.HARD
            awaitItem()
        }
        coVerify { settingsRepository.setDifficulty(Difficulty.HARD) }
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
