package com.geoquiz.app.ui.play

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.PinnedCategoriesRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.RecommendNextCategoryUseCase
import com.geoquiz.app.domain.usecase.RecommendationCandidate
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A mode in the classic switch or the "New modes" grid. */
data class ModeOption(
    val id: String,
    @StringRes val label: Int,
    val icon: ModeIcon
)

/** The "Continue" card: the single quiz saved when the player left one part-way through. */
data class SavedQuizInfo(
    val quizModeId: String,
    @StringRes val modeLabel: Int,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String,
    val answeredCount: Int,
    /** Time played so far. */
    val timeElapsedSeconds: Int = 0
)

/** The "Recommended next" card ([RecommendNextCategoryUseCase]); one tap starts this quiz. */
data class RecommendedQuiz(
    val quizModeId: String,
    @StringRes val modeLabel: Int,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String,
    /** Mastery stars, 0 to 3. */
    val stars: Int
)

/** A "Pinned" row ([PinnedCategoriesRepository]); one tap starts this quiz in its mode. */
data class PinnedQuiz(
    val quizModeId: String,
    @StringRes val modeLabel: Int,
    val modeIcon: ModeIcon,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String
)

/** A concrete category option of a classic mode, in Play display order. */
data class CategoryCandidate(
    val groupId: String,
    val categoryType: String,
    val categoryValue: String
) {
    val key: String get() = QuizHistoryRepository.categoryKey(categoryType, categoryValue)
}

data class PlayUiState(
    val selectedModeId: String = QuizMode.COUNTRIES.id,
    /** Countries, Capitals and Flags (those available), in sort order. */
    val classicModes: List<ModeOption> = emptyList(),
    /** Available non-classic modes, in sort order; empty hides the "New modes" section. */
    val newModes: List<ModeOption> = emptyList(),
    /** Group tiles per classic mode id; null while loading. */
    val modeContent: Map<String, ModeContent>? = null,
    val savedQuiz: SavedQuizInfo? = null,
    /** The "Today's challenge" placeholder card; only while [FeatureFlag.DAILY_CHALLENGE] is on (D24). */
    val showTodayChallenge: Boolean = false,
    /** "Recommended next" for [selectedModeId]; null hides the card. */
    val recommended: RecommendedQuiz? = null,
    /** Pinned categories of every available mode, oldest pin first; empty hides the section. */
    val pinned: List<PinnedQuiz> = emptyList()
) {
    val isLoading: Boolean get() = modeContent == null

    /** Tiles for [selectedModeId]. */
    val selectedContent: ModeContent? get() = modeContent?.get(selectedModeId)
}

/**
 * The Play tab: the "Today's challenge" placeholder (feature-flagged), "Continue" and
 * "Recommended next" cards, the "Pinned" categories of every mode, a classic-mode switch, that mode's category groups and the
 * "New modes" grid, all built from the [GameModeRegistry] and the feature flags. The selected
 * mode is kept in the [SavedStateHandle], so it survives rotation and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val registry: GameModeRegistry,
    featureFlagRepository: FeatureFlagRepository,
    private val countryRepository: CountryRepository,
    private val savedQuizRepository: SavedQuizRepository,
    private val categoryGroups: PlayCategoryGroups,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val quizHistoryRepository: QuizHistoryRepository,
    private val recommendNext: RecommendNextCategoryUseCase,
    pinnedCategoriesRepository: PinnedCategoriesRepository
) : ViewModel() {

    private val classicIds: Set<String> = QuizMode.entries.map { it.id }.toSet()

    /** Available classic modes and new modes, each in sort order. */
    private val modeOptions: Flow<Pair<List<ModeOption>, List<ModeOption>>> =
        featureFlagRepository.states.map { states ->
            val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
            val available = registry.available { it in enabled }
            val classic = available.filter { it.id in classicIds }.sortedBy { it.spec.sortOrder }.map { it.toOption() }
            val newModes = available.filter { it.id !in classicIds }.sortedBy { it.spec.sortOrder }.map { it.toOption() }
            classic to newModes
        }

    private val showTodayChallenge: Flow<Boolean> = featureFlagRepository.states
        .map { states -> states.any { it.flag == FeatureFlag.DAILY_CHALLENGE && it.enabled } }
        .distinctUntilChanged()

    private val selectedModeId: Flow<String> = combine(
        savedStateHandle.getStateFlow(KEY_SELECTED_MODE, QuizMode.COUNTRIES.id),
        modeOptions
    ) { selected, (classic, _) ->
        selected.takeIf { id -> classic.any { it.id == id } }
            ?: classic.firstOrNull()?.id
            ?: QuizMode.COUNTRIES.id
    }.distinctUntilChanged()

    private val modeContent = MutableStateFlow<Map<String, ModeContent>?>(null)

    /** Category options per classic mode id, in Play display order; null while loading. */
    private val candidates = MutableStateFlow<Map<String, List<CategoryCandidate>>?>(null)

    private val savedQuiz = savedQuizRepository.savedQuiz.map { it?.toSavedQuizInfo() }

    /**
     * "Recommended next" for the selected mode. Follows history (stars and recency) live and
     * never names the category in the Continue card.
     */
    private val recommended: Flow<RecommendedQuiz?> = combine(selectedModeId, savedQuiz) { mode, saved ->
        val excluded = saved?.takeIf { it.quizModeId == mode }
            ?.let { QuizHistoryRepository.categoryKey(it.categoryType, it.categoryValue) }
        mode to excluded
    }.distinctUntilChanged().flatMapLatest { (mode, excludedKey) ->
        candidates.flatMapLatest { all ->
            val modeCandidates = all?.get(mode)
            if (modeCandidates == null) {
                flowOf(null)
            } else {
                combine(
                    quizHistoryRepository.masteryStarsForMode(mode),
                    quizHistoryRepository.categoryKeysByRecencyForMode(mode)
                ) { stars, recent ->
                    recommend(mode, modeCandidates, stars, recent, excludedKey)
                }
            }
        }
    }.distinctUntilChanged()

    /**
     * Pins of every available mode whose category the mode still lists (so a pin for a category
     * that has gone from the data is hidden). Empty until the category options are built.
     */
    private val pinned: Flow<List<PinnedQuiz>> = combine(
        pinnedCategoriesRepository.pinnedCategories,
        candidates,
        modeOptions
    ) { pins, all, (classic, newModes) ->
        if (all == null) return@combine emptyList()
        val availableIds = (classic + newModes).map { it.id }.toSet()
        pins.filter { pin ->
            pin.modeId in availableIds &&
                all[pin.modeId].orEmpty().any {
                    it.categoryType == pin.categoryType && it.categoryValue == pin.categoryValue
                }
        }.map { pin ->
            val spec = registry.findOrDefault(pin.modeId).spec
            PinnedQuiz(
                quizModeId = pin.modeId,
                modeLabel = spec.labels.name,
                modeIcon = spec.icon,
                categoryType = pin.categoryType,
                categoryValue = pin.categoryValue,
                categoryDisplayName = QuizCategory.fromRoute(pin.categoryType, pin.categoryValue).displayName
            )
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<PlayUiState> = combine(
        selectedModeId,
        modeOptions,
        modeContent,
        savedQuiz,
        combine(showTodayChallenge, recommended, pinned, ::Triple)
    ) { selected, (classic, newModes), content, saved, (today, recommendation, pins) ->
        PlayUiState(
            selectedModeId = selected,
            classicModes = classic,
            newModes = newModes,
            modeContent = content,
            savedQuiz = saved,
            showTodayChallenge = today,
            recommended = recommendation,
            pinned = pins
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayUiState())

    init {
        viewModelScope.launch {
            val countries = countryRepository.getAllCountries().first()
            val content = categoryGroups.build(countries)
            modeContent.value = content
            candidates.value = content.mapValues { (modeId, modeContent) ->
                buildCandidates(modeId, modeContent, countries)
            }
        }
    }

    /**
     * Every option of [content]'s groups: groups in Play order, options in category-list order
     * (D25). The "All" tile is not a candidate.
     */
    private suspend fun buildCandidates(
        modeId: String,
        content: ModeContent,
        allCountries: List<Country>
    ): List<CategoryCandidate> {
        val mode = QuizMode.fromId(modeId)
        val countries = optionsBuilder.countriesFor(mode, allCountries)
        return content.groups.flatMap { group ->
            optionsBuilder.build(mode, group.id, countries).map {
                CategoryCandidate(group.id, it.categoryType, it.categoryValue)
            }
        }
    }

    private fun recommend(
        modeId: String,
        modeCandidates: List<CategoryCandidate>,
        stars: Map<String, Int>,
        recentKeys: List<String>,
        excludedKey: String?
    ): RecommendedQuiz? {
        val pick = recommendNext(
            candidates = modeCandidates.map { RecommendationCandidate(it.groupId, it.key) },
            stars = stars,
            recentKeys = recentKeys,
            excludedKey = excludedKey
        ) ?: return null
        val option = modeCandidates.first { it.key == pick.key }
        return RecommendedQuiz(
            quizModeId = modeId,
            modeLabel = registry.findOrDefault(modeId).spec.labels.name,
            categoryType = option.categoryType,
            categoryValue = option.categoryValue,
            categoryDisplayName = QuizCategory.fromRoute(option.categoryType, option.categoryValue).displayName,
            stars = stars[pick.key] ?: 0
        )
    }

    /** Shows [modeId]'s groups. Ignores ids that are not classic modes. */
    fun selectMode(modeId: String) {
        if (modeId in classicIds) savedStateHandle[KEY_SELECTED_MODE] = modeId
    }

    fun dismissSavedQuiz() {
        viewModelScope.launch { savedQuizRepository.clearSavedQuiz() }
    }

    private fun GameMode.toOption() = ModeOption(id = id, label = spec.labels.name, icon = spec.icon)

    private fun SavedQuizEntity.toSavedQuizInfo() = SavedQuizInfo(
        quizModeId = quizMode,
        modeLabel = registry.findOrDefault(quizMode).spec.labels.name,
        categoryType = categoryType,
        categoryValue = categoryValue,
        categoryDisplayName = QuizCategory.fromRoute(categoryType, categoryValue).displayName,
        answeredCount = savedQuizRepository.parseAnsweredCodes(answeredCountryCodes).size,
        timeElapsedSeconds = timeElapsedSeconds
    )

    companion object {
        const val KEY_SELECTED_MODE = "play_selected_mode"
    }
}
