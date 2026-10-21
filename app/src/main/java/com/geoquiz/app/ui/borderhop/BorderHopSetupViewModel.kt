package com.geoquiz.app.ui.borderhop

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.R
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.category.QuizOptionInfo
import com.geoquiz.app.ui.navigation.ModeRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * An option passing [BorderHopGameMode.offers] is not enough on its own (task 9.2 review): a BFS
 * over the real border graph shows several of them can never actually produce a puzzle at a given
 * difficulty (e.g. Oceania has no land borders at all; many subregions are too small for Hard's
 * 6+ hop range). [sectionsFor] additionally hides any option with no [BorderGraph.hasPuzzle] at
 * the current [Difficulty] - the same "leave it out" treatment the classic category list already
 * gives an empty option - rather than showing it and letting the round fail with
 * [R.string.border_hop_unavailable]. The BFS itself is cheap (197 countries), but is still kept
 * off the main thread ([defaultDispatcher]) and cached per (option, difficulty)
 * ([feasibilityCache]) so repeat difficulty taps are instant.
 */
@HiltViewModel
class BorderHopSetupViewModel @Inject constructor(
    private val countryRepository: CountryRepository,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val borderHopGameMode: BorderHopGameMode,
    private val borderGraphRepository: BorderGraphRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(BorderHopSetupUiState())
    val uiState: StateFlow<BorderHopSetupUiState> = _uiState.asStateFlow()

    /** One option Border hop offers, with its candidate codes resolved once (difficulty-independent). */
    private data class CandidateOption(val info: QuizOptionInfo, val codes: List<String>)

    private var optionsByGroup: Map<CategoryGroup, List<CandidateOption>> = emptyMap()
    private var borderGraph: BorderGraph? = null

    /** [BorderGraph.hasPuzzle] results already computed for a difficulty, keyed by (categoryType, categoryValue). */
    private val feasibilityCache = mutableMapOf<Difficulty, Map<Pair<String, String>, Boolean>>()
    private var sectionsJob: Job? = null

    init {
        viewModelScope.launch {
            val difficulty = Difficulty.DEFAULT
            val allCountries = optionsBuilder.countriesFor(QuizMode.COUNTRIES, countryRepository.getAllCountries().first())
            optionsByGroup = buildOptionsByGroup(allCountries)
            borderGraph = borderGraphRepository.graph()

            _uiState.value = BorderHopSetupUiState(
                isLoading = false,
                difficulty = difficulty,
                sections = sectionsFor(difficulty)
            )
        }
    }

    private suspend fun buildOptionsByGroup(allCountries: List<Country>): Map<CategoryGroup, List<CandidateOption>> =
        GROUPS.associate { (group, _) ->
            val options = optionsBuilder.build(QuizMode.COUNTRIES, group.id, allCountries)
                .filter { option -> borderHopGameMode.offers(QuizCategory.fromRoute(option.categoryType, option.categoryValue)) }
                .map { option ->
                    val category = QuizCategory.fromRoute(option.categoryType, option.categoryValue)
                    CandidateOption(option, borderHopGameMode.generator.items(category).map { it.code })
                }
            group to options
        }

    /** This [difficulty]'s sections: [BorderHopGameMode.offers]'s options, minus any with no [BorderGraph.hasPuzzle]. */
    private suspend fun sectionsFor(difficulty: Difficulty): List<BorderHopSetupSection> {
        val feasible = feasibilityFor(difficulty)
        return GROUPS.mapNotNull { (group, titleRes) ->
            val options = optionsByGroup[group].orEmpty()
                .filter { feasible(it.info.categoryType to it.info.categoryValue) }
                .map { it.info }
            if (options.isEmpty()) null else BorderHopSetupSection(titleRes, options)
        }
    }

    private suspend fun feasibilityFor(difficulty: Difficulty): (Pair<String, String>) -> Boolean {
        feasibilityCache[difficulty]?.let { cached -> return { key -> cached[key] == true } }
        val graph = borderGraph ?: return { false }
        val hopRange = BorderHopGameMode.hopRangeFor(difficulty)
        val computed = withContext(defaultDispatcher) {
            optionsByGroup.values.flatten().associate { option ->
                (option.info.categoryType to option.info.categoryValue) to graph.hasPuzzle(option.codes, hopRange)
            }
        }
        feasibilityCache[difficulty] = computed
        return { key -> computed[key] == true }
    }

    /** Selection only: never persisted (P10-G), so it does not carry over to the next visit. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
        sectionsJob?.cancel()
        sectionsJob = viewModelScope.launch {
            val sections = sectionsFor(difficulty)
            _uiState.value = _uiState.value.copy(sections = sections)
        }
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
