package com.geoquiz.app.ui.currencies

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.R
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.QuizOptionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One filter group on the setup screen, with the options [CurrencyGameMode.offers]. */
data class CurrenciesSetupSection(
    @StringRes val titleRes: Int,
    val options: List<QuizOptionInfo>
)

data class CurrenciesSetupUiState(
    val isLoading: Boolean = true,
    val difficulty: Difficulty = Difficulty.DEFAULT,
    val sections: List<CurrenciesSetupSection> = emptyList()
)

/**
 * Builds Currencies' own filter list (task 7.1): the same country groups the Countries category
 * list shows ([CategoryOptionsBuilder], read-only), filtered to what [CurrencyGameMode.offers]
 * (every country, a region or a subregion - no letter, word-pattern or flag groups, which say
 * nothing about currencies) and with empty groups left out. The option counts are country
 * counts, same as `SilhouettesSetupViewModel` (a precise currency count per filter is not worth
 * the extra lookup). `CurrenciesSetupScreen`'s `onStart` turns a pick into a route with [routeFor].
 */
@HiltViewModel
class CurrenciesSetupViewModel @Inject constructor(
    private val countryRepository: CountryRepository,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val settingsRepository: SettingsRepository,
    private val currencyGameMode: CurrencyGameMode
) : ViewModel() {

    private val _uiState = MutableStateFlow(CurrenciesSetupUiState())
    val uiState: StateFlow<CurrenciesSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val difficulty = settingsRepository.difficulty.first()
                .takeIf { it in currencyGameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            val allCountries = optionsBuilder.countriesFor(QuizMode.COUNTRIES, countryRepository.getAllCountries().first())
            val sections = GROUPS.mapNotNull { (group, titleRes) ->
                val options = optionsBuilder.build(QuizMode.COUNTRIES, group.id, allCountries)
                    .filter { option -> currencyGameMode.offers(QuizCategory.fromRoute(option.categoryType, option.categoryValue)) }
                if (options.isEmpty()) null else CurrenciesSetupSection(titleRes, options)
            }
            _uiState.value = CurrenciesSetupUiState(isLoading = false, difficulty = difficulty, sections = sections)
        }
    }

    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    }

    /** The route [onStart][CurrenciesSetupScreen] should navigate to for [option]. See [CurrenciesRoutes]. */
    fun routeFor(option: QuizOptionInfo): String = CurrenciesRoutes.createRoute(
        categoryType = option.categoryType,
        categoryValue = option.categoryValue,
        difficulty = _uiState.value.difficulty.id
    )

    companion object {
        /** Groups in the order the screen shows them, each with its section title. */
        private val GROUPS: List<Pair<CategoryGroup, Int>> = listOf(
            CategoryGroup.ALL_COUNTRIES to R.string.currencies_filter_all_countries,
            CategoryGroup.REGIONS to R.string.currencies_filter_by_region,
            CategoryGroup.SUBREGIONS to R.string.currencies_filter_by_subregion
        )
    }
}
