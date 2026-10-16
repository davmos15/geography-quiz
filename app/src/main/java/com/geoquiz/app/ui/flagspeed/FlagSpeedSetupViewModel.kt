package com.geoquiz.app.ui.flagspeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedGameMode
import com.geoquiz.app.domain.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FlagSpeedSetupUiState(
    val isLoading: Boolean = true,
    val difficulty: Difficulty = Difficulty.DEFAULT
)

/**
 * Flag speed's own setup screen (task 7.3, D52): a difficulty tier only. No region or category
 * filter makes sense for a timed flag-lookalike round, unlike every other mode's own setup
 * screen. Starting the round itself is left to the caller ([FlagSpeedSetupScreen]'s `onStart`,
 * wired in `AppNavigation.kt` to `Screen.FlagSpeed.createRoute`): this ViewModel only ever hands
 * off the chosen difficulty, never builds a route itself.
 */
@HiltViewModel
class FlagSpeedSetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FlagSpeedSetupUiState())
    val uiState: StateFlow<FlagSpeedSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val difficulty = settingsRepository.difficulty.first()
                .takeIf { it in FlagSpeedGameMode.SPEC.supportedDifficulties } ?: Difficulty.DEFAULT
            _uiState.value = FlagSpeedSetupUiState(isLoading = false, difficulty = difficulty)
        }
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    }
}
