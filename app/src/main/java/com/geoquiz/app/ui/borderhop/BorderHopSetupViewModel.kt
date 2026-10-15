package com.geoquiz.app.ui.borderhop

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.QuizOptionInfo
import com.geoquiz.app.ui.navigation.ModeRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One filter group on the setup screen, with the options [BorderHopGameMode.offers]. */
data class BorderHopSetupSection(
    @StringRes val titleRes: Int,
    val options: List<QuizOptionInfo>
)

data class BorderHopSetupUiState(
    val isLoading: Boolean = true,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val sections: List<BorderHopSetupSection> = emptyList()
)

/**
 * Builds Border hop's own filter list (task 6.1): All countries, By region and By subregion only
 * (every other group is left out, [BorderHopGameMode.offers]). [onStart] (the screen) turns a
 * pick into a route with [ModeRoutes.start]; the actual start/target pair is picked fresh every
 * time the game screen loads ([BorderHopGameViewModel]), not here.
 */
@HiltViewModel
class BorderHopSetupViewModel @Inject constructor(
    private val countryRepository: CountryRepository,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val settingsRepository: SettingsRepository,
    private val borderHopGameMode: BorderHopGameMode
) : ViewModel() {

    private val _uiState = MutableStateFlow(BorderHopSetupUiState())
    val uiState: StateFlow<BorderHopSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val difficulty = settingsRepository.difficulty.first()
                .takeIf { it in borderHopGameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            val allCountries = optionsBuilder.countriesFor(QuizMode.COUNTRIES, countryRepository.getAllCountries().first())
            val sections = GROUPS.mapNotNull { (group, titleRes) ->
                val options = optionsBuilder.build(QuizMode.COUNTRIES, group.id, allCountries)
                    .filter { option -> borderHopGameMode.offers(QuizCategory.fromRoute(option.categoryType, option.categoryValue)) }
                if (options.isEmpty()) null else BorderHopSetupSection(titleRes, options)
            }
            _uiState.value = BorderHopSetupUiState(isLoading = false, difficulty = difficulty, sections = sections)
        }
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    }

    /** The route [onStart][BorderHopSetupScreen] should navigate to for [option]. */
    fun routeFor(option: QuizOptionInfo): String = ModeRoutes.start(
        modeId = ModeRoutes.BORDER_HOP_MODE_ID,
        categoryType = option.categoryType,
        categoryValue = option.categoryValue,
        difficulty = _uiState.value.difficulty.id
    )

    companion object {
        /** Groups in the order the screen shows them, each with its section title. */
        private val GROUPS: List<Pair<CategoryGroup, Int>> = listOf(
            CategoryGroup.ALL_COUNTRIES to R.string.border_hop_filter_all_countries,
            CategoryGroup.REGIONS to R.string.border_hop_filter_by_region,
            CategoryGroup.SUBREGIONS to R.string.border_hop_filter_by_subregion
        )
    }
}
