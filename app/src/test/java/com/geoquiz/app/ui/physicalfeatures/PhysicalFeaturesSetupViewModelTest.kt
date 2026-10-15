package com.geoquiz.app.ui.physicalfeatures

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureTab
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PhysicalFeaturesSetupViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val difficultyFlow = MutableStateFlow(Difficulty.NORMAL)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true) {
        every { difficulty } returns difficultyFlow
    }
    private val repository = FakePhysicalFeatureRepository(FAKE_RIVERS)

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
        PhysicalFeaturesSetupViewModel(handle, settingsRepository, repository)

    @Test
    fun `defaults to Rivers with no continent filter`() = runTest {
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(PhysicalFeatureTab.RIVERS, state.tab)
            assertNull(state.continent)
            assertEquals(listOf("Asia", "South America"), state.availableContinents)
            assertEquals(Difficulty.NORMAL, state.difficulty)
        }
    }

    @Test
    fun `selecting a continent not in the new tab is cleared`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onContinentSelected("South America")
            assertEquals("South America", awaitItem().continent)

            // Mountains has no features in this fixture, so the filter clears rather than
            // sticking to a continent the new tab doesn't have.
            vm.onTabSelected(PhysicalFeatureTab.MOUNTAINS)
            val state = awaitItem()
            assertNull(state.continent)
            assertTrue(state.availableContinents.isEmpty())
        }
    }

    @Test
    fun `buildStartRoute carries the mode id, tab-continent label and difficulty`() = runTest {
        // This mode is registered in `ui/navigation/ModeRoutes.kt` (the lead's integration pass,
        // Screen.PhysicalFeatures), so `ModeRoutes.start()` builds this mode's own route shape.
        val vm = viewModel()
        vm.uiState.test {
            awaitItem()
            vm.onContinentSelected("Asia")
            awaitItem()
        }
        val route = vm.buildStartRoute()
        assertTrue(route.startsWith("physical_features/fixed_region/"))
        assertTrue(route.contains("Rivers - Asia"))
        assertTrue(route.contains("difficulty=normal"))
    }
}
