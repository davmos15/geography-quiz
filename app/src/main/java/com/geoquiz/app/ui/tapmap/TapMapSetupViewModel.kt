package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

/**
 * Tap the map's region, variant and difficulty picker (task 5.2, 5.2b). The variant choice
 * (Find / Name all) sits above the difficulty selector: Name all offers Normal and Hard only
 * (D40, Easy is Find's multiple-choice tier), so switching to it hides Easy from the difficulty
 * options. The selected tier starts at [Difficulty.DEFAULT] and is never persisted (P10-G): if it
 * shows Normal instead while Name all is picked, that is display only, so switching back to Find
 * still offers Easy.
 */
@HiltViewModel
class TapMapSetupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Survives process death and activity recreation (nit), like every other setup screen. */
    private val _selectedPreset = MutableStateFlow(
        RegionPreset.fromId(savedStateHandle.get<String>(KEY_PRESET) ?: RegionPreset.WORLD.id) ?: RegionPreset.WORLD
    )

    private val _selectedVariant = MutableStateFlow(
        savedStateHandle.get<String>(KEY_VARIANT).takeIf { it == TapMapGameMode.VARIANT_NAME_ALL }
            ?: TapMapGameMode.VARIANT_FIND
    )

    private val _difficulty = MutableStateFlow(Difficulty.DEFAULT)

    val uiState: StateFlow<TapMapSetupUiState> = combine(
        _selectedPreset,
        _selectedVariant,
        _difficulty
    ) { preset, variant, difficulty ->
        val availableDifficulties = if (variant == TapMapGameMode.VARIANT_NAME_ALL) {
            NAME_ALL_DIFFICULTIES
        } else {
            Difficulty.entries
        }
        val effectiveDifficulty = difficulty.takeIf { it in availableDifficulties } ?: Difficulty.NORMAL
        TapMapSetupUiState(
            selectedPreset = preset,
            variant = variant,
            availableDifficulties = availableDifficulties,
            difficulty = effectiveDifficulty
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TapMapSetupUiState())

    fun onPresetSelected(preset: RegionPreset) {
        _selectedPreset.value = preset
        savedStateHandle[KEY_PRESET] = preset.id
    }

    fun onVariantSelected(variant: String) {
        _selectedVariant.value = variant
        savedStateHandle[KEY_VARIANT] = variant
    }

    /** Selection only: never persisted (P10-G), so it does not carry over to the next visit. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _difficulty.value = difficulty
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
            variantId = state.variant
        )
    }

    companion object {
        const val KEY_PRESET = "tap_map_setup_preset"
        const val KEY_VARIANT = "tap_map_setup_variant"

        /** D40: Name all offers Normal and Hard only. */
        val NAME_ALL_DIFFICULTIES = listOf(Difficulty.NORMAL, Difficulty.HARD)
    }
}

data class TapMapSetupUiState(
    val presets: List<RegionPreset> = RegionPreset.entries,
    val selectedPreset: RegionPreset = RegionPreset.WORLD,
    val variant: String = TapMapGameMode.VARIANT_FIND,
    val availableDifficulties: List<Difficulty> = Difficulty.entries,
    val difficulty: Difficulty = Difficulty.DEFAULT
)
