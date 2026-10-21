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
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import com.geoquiz.app.ui.category.CategoryOptionsBuilder
import com.geoquiz.app.ui.navigation.ModeRoutes
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

/** A mode in the classic switch or a page's "More ways to play" tiles. */
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

/** A "Pinned" row ([PinnedCategoriesRepository]); one tap starts this quiz in its mode. */
data class PinnedQuiz(
    val quizModeId: String,
    @StringRes val modeLabel: Int,
    val modeIcon: ModeIcon,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String
)

/**
 * The live "Today's challenge" card (8.6): whether today's round is done and the streak, which
 * also feeds the streak chip in the Play header.
 */
data class TodayChallengeInfo(val streak: StreakStatus) {
    val playedToday: Boolean get() = streak.playedToday
}

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
    /**
     * Non-classic modes to show after a classic mode's category groups, under "More ways to
     * play" (task P10-A.5), keyed by classic mode id. A mode is absent from its page's list while
     * its feature flag is off; an empty list hides the heading.
     */
    val extraModesByPage: Map<String, List<ModeOption>> = emptyMap(),
    /** Group tiles per classic mode id; null while loading. */
    val modeContent: Map<String, ModeContent>? = null,
    val savedQuiz: SavedQuizInfo? = null,
    /** The "Today's challenge" card and header streak; null unless [FeatureFlag.DAILY_CHALLENGE] is on. */
    val todayChallenge: TodayChallengeInfo? = null,
    /** Pinned categories of every available mode, oldest pin first; empty hides the section. */
    val pinned: List<PinnedQuiz> = emptyList(),
    /**
     * [PlayViewModel.surprise]'s own candidate pool per classic mode id; null while loading
     * (code review round, task P10-R1: built asynchronously, separately from [modeContent], so a
     * tap on "Surprise me" before this is ready used to silently do nothing - see
     * [surpriseMeReady], which the button is now disabled on until true).
     */
    val candidatesByMode: Map<String, List<CategoryCandidate>>? = null
) {
    val isLoading: Boolean get() = modeContent == null

    val showTodayChallenge: Boolean get() = todayChallenge != null

    /** Tiles for [selectedModeId]. */
    val selectedContent: ModeContent? get() = modeContent?.get(selectedModeId)

    /** "More ways to play" tiles for [selectedModeId] (task P10-A.5). */
    val selectedExtraModes: List<ModeOption> get() = extraModesByPage[selectedModeId].orEmpty()

    /**
     * Whether [PlayViewModel.surprise] has a real candidate pool to draw from for
     * [selectedModeId] right now (task P10-R1): "Surprise me" is disabled until this is true, so
     * a tap can never silently do nothing while [candidatesByMode] is still loading.
     */
    val surpriseMeReady: Boolean get() = candidatesByMode?.get(selectedModeId)?.isNotEmpty() == true
}

/**
 * The Play tab: the "Today's challenge" placeholder (feature-flagged), the "Continue" card, the
 * "Pinned" categories of every mode, a classic-mode switch, that mode's category groups and
 * (task P10-A.5) its other modes under "More ways to play", all built from the
 * [GameModeRegistry] and the feature flags. The selected mode is kept in the [SavedStateHandle],
 * so it survives rotation and process death.
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
    pinnedCategoriesRepository: PinnedCategoriesRepository,
    observeStreak: ObserveStreakUseCase,
    private val quizRandom: QuizRandom
) : ViewModel() {

    private val classicIds: Set<String> = QuizMode.entries.map { it.id }.toSet()

    /**
     * Fixed order of non-classic modes shown after a classic mode's category groups, under
     * "More ways to play" (task P10-A.5). Capitals has none. A mode only appears on its page
     * while it is available (its feature flag, if any, is on).
     */
    private val modePagePlacements: Map<String, List<String>> = mapOf(
        QuizMode.COUNTRIES.id to listOf(
            ModeRoutes.SILHOUETTES_MODE_ID,
            ModeRoutes.PHYSICAL_FEATURES_MODE_ID,
            ModeRoutes.BORDER_HOP_MODE_ID,
            ModeRoutes.CURRENCIES_MODE_ID,
            ModeRoutes.TAP_MAP_MODE_ID,
            ModeRoutes.US_CA_REGIONS_MODE_ID
        ),
        QuizMode.FLAGS.id to listOf(ModeRoutes.FLAG_SPEED_ROUND_MODE_ID),
        QuizMode.CAPITALS.id to emptyList()
    )

    /** Available classic modes, in sort order. */
    private val classicModes: Flow<List<ModeOption>> =
        featureFlagRepository.states.map { states ->
            val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
            val available = registry.available { it in enabled }
            available.filter { it.id in classicIds }.sortedBy { it.spec.sortOrder }.map { it.toOption() }
        }

    /**
     * [modePagePlacements], filtered live to the modes currently available (task P10-A.5): a mode
     * disappears from its page the moment its flag is turned off, and the daily challenge is
     * never included here (it keeps its own Today's challenge card).
     */
    private val extraModesByPage: Flow<Map<String, List<ModeOption>>> =
        featureFlagRepository.states.map { states ->
            val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
            val available = registry.available { it in enabled }
                .filter { it.id != DailyChallengeGameMode.MODE_ID }
                .associateBy { it.id }
            modePagePlacements.mapValues { (_, ids) ->
                ids.mapNotNull { id -> available[id]?.toOption() }
            }
        }

    // Same rule as the destination's guard (rememberModeIsAvailable): the card shows exactly
    // when the daily challenge mode is available, today behind FeatureFlag.DAILY_CHALLENGE, so it
    // stays reachable if the mode is ever released without a flag.
    private val showTodayChallenge: Flow<Boolean> = featureFlagRepository.states
        .map { states ->
            val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
            registry.available { it in enabled }.any { it.id == DailyChallengeGameMode.MODE_ID }
        }
        .distinctUntilChanged()

    /** Live while the flag is on: streak (rolls over at midnight) and "done today". */
    private val todayChallenge: Flow<TodayChallengeInfo?> = showTodayChallenge.flatMapLatest { on ->
        if (on) observeStreak().map { TodayChallengeInfo(it) } else flowOf(null)
    }.distinctUntilChanged()

    /** [classicModes] and [extraModesByPage] together, so the top-level [uiState] combine stays at 5 flows. */
    private val classicAndExtraModes: Flow<Pair<List<ModeOption>, Map<String, List<ModeOption>>>> =
        combine(classicModes, extraModesByPage, ::Pair)

    private val selectedModeId: Flow<String> = combine(
        savedStateHandle.getStateFlow(KEY_SELECTED_MODE, QuizMode.COUNTRIES.id),
        classicModes
    ) { selected, classic ->
        selected.takeIf { id -> classic.any { it.id == id } }
            ?: classic.firstOrNull()?.id
            ?: QuizMode.COUNTRIES.id
    }.distinctUntilChanged()

    private val modeContent = MutableStateFlow<Map<String, ModeContent>?>(null)

    /** Category options per classic mode id, in Play display order; null while loading. */
    private val candidates = MutableStateFlow<Map<String, List<CategoryCandidate>>?>(null)

    private val savedQuiz = savedQuizRepository.savedQuiz.map { it?.toSavedQuizInfo() }

    /**
     * Pins of every available mode whose category the mode still lists (so a pin for a category
     * that has gone from the data is hidden). Empty until the category options are built.
     */
    private val pinned: Flow<List<PinnedQuiz>> = combine(
        pinnedCategoriesRepository.pinnedCategories,
        candidates,
        classicAndExtraModes
    ) { pins, all, (classic, extraByPage) ->
        if (all == null) return@combine emptyList()
        val availableIds = classic.map { it.id }.toSet() + extraByPage.values.flatten().map { it.id }.toSet()
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
        classicAndExtraModes,
        modeContent,
        savedQuiz,
        combine(todayChallenge, pinned, candidates, ::Triple)
    ) { selected, (classic, extraByPage), content, saved, (today, pins, candidateMap) ->
        PlayUiState(
            selectedModeId = selected,
            classicModes = classic,
            extraModesByPage = extraByPage,
            modeContent = content,
            savedQuiz = saved,
            todayChallenge = today,
            pinned = pins,
            candidatesByMode = candidateMap
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

    /**
     * "Surprise me" (task P10-A.6): a category of [modeId] picked uniformly at random from its
     * candidates (never "All", which is not a candidate). Null while loading or if the mode has
     * no candidates yet.
     */
    fun surprise(modeId: String): CategoryCandidate? {
        val options = candidates.value?.get(modeId)
        if (options.isNullOrEmpty()) return null
        return options[quizRandom.random.nextInt(options.size)]
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
