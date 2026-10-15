package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reads the route's `variant` argument before [TapMapGameScreen] decides which real screen (and
 * view model) to show (task 5.2b): "name_all" only for [TapMapGameMode.VARIANT_NAME_ALL]; every
 * other value, including a missing one, is Find's job (the screen's own `when` treats anything
 * that is not "name_all" as Find).
 */
class TapMapVariantViewModelTest {

    private fun viewModel(handle: SavedStateHandle) = TapMapVariantViewModel(handle)

    @Test
    fun `name_all is read from the route`() {
        val vm = viewModel(SavedStateHandle(mapOf(TapMapGameViewModel.ARG_VARIANT to TapMapGameMode.VARIANT_NAME_ALL)))

        assertEquals(TapMapGameMode.VARIANT_NAME_ALL, vm.variant)
    }

    @Test
    fun `find is read from the route`() {
        val vm = viewModel(SavedStateHandle(mapOf(TapMapGameViewModel.ARG_VARIANT to TapMapGameMode.VARIANT_FIND)))

        assertEquals(TapMapGameMode.VARIANT_FIND, vm.variant)
    }

    @Test
    fun `no route argument defaults to find, not name_all`() {
        val vm = viewModel(SavedStateHandle())

        assertEquals(TapMapGameMode.VARIANT_FIND, vm.variant)
    }

    @Test
    fun `anything else on the route is never read as name_all`() {
        val vm = viewModel(SavedStateHandle(mapOf(TapMapGameViewModel.ARG_VARIANT to "something_else")))

        assertEquals("something_else", vm.variant)
        assertEquals(false, vm.variant == TapMapGameMode.VARIANT_NAME_ALL)
    }
}
