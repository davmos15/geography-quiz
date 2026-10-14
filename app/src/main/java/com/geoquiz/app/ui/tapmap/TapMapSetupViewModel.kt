package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.ui.navigation.ModeRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tap the map's region and difficulty picker (task 5.2). Only the "find" variant exists yet
 * (5.2b adds "name all"): there is nothing to pick, so the screen shows no variant control
 * (D37, spec: "no disabled control").
 */
@HiltViewModel
class TapMapSetupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Survives process death and activity recreation (nit), like every other setup screen. */
    private val _selectedPreset = MutableStateFlow(
        RegionPreset.fromId(savedStateHandle.get<String>(KEY_PRESET) ?: RegionPreset.WORLD.id) ?: RegionPreset.WORLD
    )

    val uiState: StateFlow<TapMapSetupUiState> = combine(
        _selectedPreset,
        settingsRepository.difficulty
    ) { preset, difficulty -> TapMapSetupUiState(selectedPreset = preset, difficulty = difficulty) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TapMapSetupUiState())

    fun onPresetSelected(preset: RegionPreset) {
        _selectedPreset.value = preset
        savedStateHandle[KEY_PRESET] = preset.id
    }

    /** Also remembered as the player's default tier (same as the classic category list). */
    fun onDifficultySelected(difficulty: Difficulty) {
        viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    }

    /** The route to start a round with the current selection. */
    fun buildStartRoute(): String {
        val state = uiState.value
        val category = QuizCategory.MapRegion(state.selectedPreset.id)
        return ModeRoutes.start(
            modeId = TapMapGameMode.MODE_ID,
            categoryType = category.typeKey,
            categoryValue = category.valueKey,
            difficulty = state.difficulty.id,
            variantId = TapMapGameMode.VARIANT_FIND
        )
    }

    companion object {
        const val KEY_PRESET = "tap_map_setup_preset"
    }
}

data class TapMapSetupUiState(
    val presets: List<RegionPreset> = RegionPreset.entries,
    val selectedPreset: RegionPreset = RegionPreset.WORLD,
    val difficulty: Difficulty = Difficulty.DEFAULT
)
