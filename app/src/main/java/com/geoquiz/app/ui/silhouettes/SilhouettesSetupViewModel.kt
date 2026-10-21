package com.geoquiz.app.ui.silhouettes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.R
import com.geoquiz.app.domain.mode.silhouettes.SilhouetteLegibility
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
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

/** One filter group on the setup screen, with the options [SilhouettesGameMode.offers]. */
data class SilhouettesSetupSection(
    @StringRes val titleRes: Int,
    val options: List<QuizOptionInfo>
)

data class SilhouettesSetupUiState(
    val isLoading: Boolean = true,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val sections: List<SilhouettesSetupSection> = emptyList()
)

/**
 * Builds Country silhouettes' own filter list (task 5.1): the same groups as the Countries
 * category list ([CategoryOptionsBuilder], read-only), filtered to what
 * [SilhouettesGameMode.offers] and with empty groups left out. [onStart] (the screen) turns a
 * pick into a route with [ModeRoutes.start].
 */
@HiltViewModel
class SilhouettesSetupViewModel @Inject constructor(
    private val countryRepository: CountryRepository,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val silhouettesGameMode: SilhouettesGameMode
) : ViewModel() {

    private val _uiState = MutableStateFlow(SilhouettesSetupUiState())
    val uiState: StateFlow<SilhouettesSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val difficulty = Difficulty.DEFAULT
            // Task P10-E: excluded (unrecognisable, e.g. Tuvalu) codes are left out before the
            // shared CategoryOptionsBuilder counts options, so a filter's shown count already
            // matches what a round of it will actually ask about, and a filter that is only
            // excluded codes (e.g. a region of nothing but scattered atolls) disappears rather
            // than offering an option that silently asks about fewer countries than shown.
            val allCountries = optionsBuilder.countriesFor(QuizMode.COUNTRIES, countryRepository.getAllCountries().first())
                .filterNot { it.code in SilhouetteLegibility.EXCLUDED_CCA3 }
            val sections = GROUPS.mapNotNull { (group, titleRes) ->
                val options = optionsBuilder.build(QuizMode.COUNTRIES, group.id, allCountries)
                    .filter { option -> silhouettesGameMode.offers(QuizCategory.fromRoute(option.categoryType, option.categoryValue)) }
                if (options.isEmpty()) null else SilhouettesSetupSection(titleRes, options)
            }
            _uiState.value = SilhouettesSetupUiState(isLoading = false, difficulty = difficulty, sections = sections)
        }
    }

    /** Selection only: never persisted (P10-G), so it does not carry over to the next visit. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    /** The route [onStart][SilhouettesSetupScreen] should navigate to for [option]. */
    fun routeFor(option: QuizOptionInfo): String = ModeRoutes.start(
        modeId = ModeRoutes.SILHOUETTES_MODE_ID,
        categoryType = option.categoryType,
        categoryValue = option.categoryValue,
        difficulty = _uiState.value.difficulty.id
    )

    companion object {
        /** Groups in the order the screen shows them, each with its section title. */
        private val GROUPS: List<Pair<CategoryGroup, Int>> = listOf(
            CategoryGroup.ALL_COUNTRIES to R.string.silhouettes_filter_all_countries,
            CategoryGroup.REGIONS to R.string.silhouettes_filter_by_region,
            CategoryGroup.SUBREGIONS to R.string.silhouettes_filter_by_subregion,
            CategoryGroup.STARTING_LETTER to R.string.silhouettes_filter_starting_letter,
            CategoryGroup.ENDING_LETTER to R.string.silhouettes_filter_ending_letter,
            CategoryGroup.CONTAINING_LETTER to R.string.silhouettes_filter_containing_letter,
            CategoryGroup.ISLAND_COUNTRIES to R.string.silhouettes_filter_islands
        )
    }
}
