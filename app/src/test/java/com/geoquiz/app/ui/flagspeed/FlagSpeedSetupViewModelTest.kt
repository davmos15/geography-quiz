package com.geoquiz.app.ui.flagspeed

import com.geoquiz.app.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Flag speed's own setup screen (task 7.3): a difficulty tier only, never persisted (P10-G).
 */
class FlagSpeedSetupViewModelTest {

    @Test
    fun `starts at Normal, not loading`() {
        val vm = FlagSpeedSetupViewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(Difficulty.NORMAL, vm.uiState.value.difficulty)
    }

    @Test
    fun `onDifficultySelected updates the state but is never persisted`() {
        val vm = FlagSpeedSetupViewModel()

        vm.onDifficultySelected(Difficulty.HARD)
        assertEquals(Difficulty.HARD, vm.uiState.value.difficulty)

        // A fresh instance of this screen starts back at Normal: nothing was persisted.
        val another = FlagSpeedSetupViewModel()
        assertEquals(Difficulty.NORMAL, another.uiState.value.difficulty)
    }
}
