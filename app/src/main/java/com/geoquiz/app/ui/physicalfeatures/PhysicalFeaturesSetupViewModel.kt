package com.geoquiz.app.ui.physicalfeatures

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureFilter
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureTab
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesCategory
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode
import com.geoquiz.app.domain.mode.physicalfeatures.continentsFor
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.ui.navigation.ModeRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhysicalFeaturesSetupUiState(
    val tab: PhysicalFeatureTab = PhysicalFeatureTab.RIVERS,
    val continent: String? = null,
    val availableContinents: List<String> = emptyList(),
    val difficulty: Difficulty = Difficulty.NORMAL
)

/**
 * The tab (Rivers/Mountains/Lakes), an optional continent filter and the difficulty picker (task
 * 7.2). A per-round choice via [SavedStateHandle] and an in-memory [_difficulty] only (P10-G):
 * nothing here is persisted, so every new instance of this screen starts the tier back at
 * [Difficulty.DEFAULT]. Switching tabs clears a continent that the new tab has no features in.
 */
@HiltViewModel
class PhysicalFeaturesSetupViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val physicalFeatureRepository: PhysicalFeatureRepository
) : ViewModel() {

    private val _tab = MutableStateFlow(
        PhysicalFeatureTab.entries.find { it.name == savedStateHandle.get<String>(KEY_TAB) } ?: PhysicalFeatureTab.RIVERS
    )
    private val _continent = MutableStateFlow(savedStateHandle.get<String>(KEY_CONTINENT))
    private val _allFeatures = MutableStateFlow<List<PhysicalFeature>>(emptyList())
    private val _difficulty = MutableStateFlow(Difficulty.DEFAULT)

    init {
        viewModelScope.launch { _allFeatures.value = physicalFeatureRepository.features() }
    }

    val uiState: StateFlow<PhysicalFeaturesSetupUiState> = combine(
        _tab, _continent, _allFeatures, _difficulty
    ) { tab, continent, allFeatures, difficulty ->
        val continents = allFeatures.continentsFor(tab)
        val effectiveContinent = continent?.takeIf { it in continents }
        PhysicalFeaturesSetupUiState(
            tab = tab,
            continent = effectiveContinent,
            availableContinents = continents,
            difficulty = difficulty
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhysicalFeaturesSetupUiState())

    fun onTabSelected(tab: PhysicalFeatureTab) {
        _tab.value = tab
        savedStateHandle[KEY_TAB] = tab.name
    }

    /** Null clears the filter ("All continents"). */
    fun onContinentSelected(continent: String?) {
        _continent.value = continent
        savedStateHandle[KEY_CONTINENT] = continent
    }

    /** Selection only: never persisted (P10-G), so it does not carry over to the next visit. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _difficulty.value = difficulty
    }

    /** The route to start a round with the current selection. */
    fun buildStartRoute(): String {
        val state = uiState.value
        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(state.tab, state.continent))
        return ModeRoutes.start(
            modeId = PhysicalFeaturesGameMode.MODE_ID,
            categoryType = category.typeKey,
            categoryValue = category.valueKey,
            difficulty = state.difficulty.id
        )
    }

    companion object {
        const val KEY_TAB = "physical_features_setup_tab"
        const val KEY_CONTINENT = "physical_features_setup_continent"
    }
}
