package com.geoquiz.app.ui.usca

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.navigation.ModeRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class UsCaRegionsSetupUiState(
    val group: UsCaRegionGroup = UsCaRegionGroup.US_STATES,
    val includeDc: Boolean = true,
    val variant: String = UsCaRegionsGameMode.VARIANT_TAP,
    val availableDifficulties: List<Difficulty> = UsCaRegionsGameMode.TAP_DIFFICULTIES,
    val difficulty: Difficulty = Difficulty.NORMAL
)

/**
 * The group (US states / Canadian provinces), DC-optional checkbox, variant and difficulty
 * picker (task 6.2). A per-round choice via [SavedStateHandle] and an in-memory
 * [_difficulty] only (P10-G): nothing here is persisted, so every new instance of this screen
 * starts the tier back at [Difficulty.DEFAULT]. The variant choice sits above difficulty, exactly
 * like Tap the map's setup screen (D40): switching to a variant with fewer tiers (Name all and
 * Tap offer Normal/Hard only, Multiple choice Easy only, task 6.2 review) hides the tiers it does
 * not support; the selected tier is never overwritten by this clamp (display only), so switching
 * variants again still offers every tier that one supports.
 */
@HiltViewModel
class UsCaRegionsSetupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _group = MutableStateFlow(
        UsCaRegionGroup.fromRouteOrNull(savedStateHandle.get<String>(KEY_GROUP)) ?: UsCaRegionGroup.US_STATES
    )
    private val _includeDc = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_INCLUDE_DC) ?: true)
    private val _variant = MutableStateFlow(
        savedStateHandle.get<String>(KEY_VARIANT) ?: UsCaRegionsGameMode.VARIANT_TAP
    )
    private val _difficulty = MutableStateFlow(Difficulty.DEFAULT)

    val uiState: StateFlow<UsCaRegionsSetupUiState> = combine(
        _group, _includeDc, _variant, _difficulty
    ) { group, includeDc, variant, difficulty ->
        val available = UsCaRegionsGameMode.difficultiesFor(variant)
        val effective = difficulty.takeIf { it in available } ?: available.first()
        UsCaRegionsSetupUiState(
            group = group,
            includeDc = includeDc,
            variant = variant,
            availableDifficulties = available,
            difficulty = effective
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsCaRegionsSetupUiState())

    fun onGroupSelected(group: UsCaRegionGroup) {
        _group.value = group
        savedStateHandle[KEY_GROUP] = group.routeValue
    }

    /** Only meaningful for [UsCaRegionGroup.US_STATES]; ignored otherwise. */
    fun onIncludeDcChanged(includeDc: Boolean) {
        _includeDc.value = includeDc
        savedStateHandle[KEY_INCLUDE_DC] = includeDc
    }

    fun onVariantSelected(variant: String) {
        _variant.value = variant
        savedStateHandle[KEY_VARIANT] = variant
    }

    /** Selection only: never persisted (P10-G), so it does not carry over to the next visit. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _difficulty.value = difficulty
    }

    /** The route to start a round with the current selection. */
    fun buildStartRoute(): String {
        val state = uiState.value
        val category = UsCaRegionsCategory.toQuizCategory(state.group, state.includeDc)
        return ModeRoutes.start(
            modeId = UsCaRegionsGameMode.MODE_ID,
            categoryType = category.typeKey,
            categoryValue = category.valueKey,
            difficulty = state.difficulty.id,
            variantId = state.variant
        )
    }

    companion object {
        const val KEY_GROUP = "us_ca_setup_group"
        const val KEY_INCLUDE_DC = "us_ca_setup_include_dc"
        const val KEY_VARIANT = "us_ca_setup_variant"
    }
}
