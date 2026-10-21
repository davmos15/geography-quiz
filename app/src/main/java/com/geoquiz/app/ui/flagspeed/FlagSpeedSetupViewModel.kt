package com.geoquiz.app.ui.flagspeed

import androidx.lifecycle.ViewModel
import com.geoquiz.app.domain.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FlagSpeedSetupUiState(
    val isLoading: Boolean = false,
    val difficulty: Difficulty = Difficulty.DEFAULT
)

/**
 * Flag speed's own setup screen (task 7.3, D52): a difficulty tier only. No region or category
 * filter makes sense for a timed flag-lookalike round, unlike every other mode's own setup
 * screen. Starting the round itself is left to the caller ([FlagSpeedSetupScreen]'s `onStart`,
 * wired in `AppNavigation.kt` to `Screen.FlagSpeed.createRoute`): this ViewModel only ever hands
 * off the chosen difficulty, never builds a route itself. The selected tier starts at
 * [Difficulty.DEFAULT] and lives only in [_uiState] (P10-G): it is never persisted, so it does
 * not carry over to the next visit.
 */
@HiltViewModel
class FlagSpeedSetupViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(FlagSpeedSetupUiState())
    val uiState: StateFlow<FlagSpeedSetupUiState> = _uiState.asStateFlow()

    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }
}
