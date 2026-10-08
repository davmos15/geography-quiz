package com.geoquiz.app.ui.play

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.ModeIcon
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CountryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

/** The "Resume quiz" card: the single quiz saved when the player left one part-way through. */
data class SavedQuizInfo(
    val quizModeId: String,
    @StringRes val modeLabel: Int,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String,
    val answeredCount: Int
)

data class PlayUiState(
    val selectedModeId: String = QuizMode.COUNTRIES.id,
    /** Countries, Capitals and Flags (those available), in sort order. */
    val classicModes: List<ModeOption> = emptyList(),
    /** Available non-classic modes, in sort order; empty hides the "New modes" section. */
    val newModes: List<ModeOption> = emptyList(),
    /** Group tiles per classic mode id; null while loading. */
    val modeContent: Map<String, ModeContent>? = null,
    val savedQuiz: SavedQuizInfo? = null
) {
    val isLoading: Boolean get() = modeContent == null

    /** Tiles for [selectedModeId]. */
    val selectedContent: ModeContent? get() = modeContent?.get(selectedModeId)
}

/**
 * The Play tab: a classic-mode switch, that mode's category groups and the "New modes" grid, all
 * built from the [GameModeRegistry] and the feature flags. The selected mode is kept in the
 * [SavedStateHandle], so it survives rotation and process death.
 */
@HiltViewModel
class PlayViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val registry: GameModeRegistry,
    featureFlagRepository: FeatureFlagRepository,
    private val countryRepository: CountryRepository,
    private val savedQuizRepository: SavedQuizRepository,
    private val categoryGroups: PlayCategoryGroups
) : ViewModel() {

    private val classicIds: Set<String> = QuizMode.entries.map { it.id }.toSet()

    private val availableModes = featureFlagRepository.states.map { states ->
        val enabled = states.filter { it.enabled }.map { it.flag }.toSet()
        registry.available { it in enabled }
    }

    private val modeContent = MutableStateFlow<Map<String, ModeContent>?>(null)

    private val savedQuiz = savedQuizRepository.savedQuiz.map { it?.toSavedQuizInfo() }

    val uiState: StateFlow<PlayUiState> = combine(
        savedStateHandle.getStateFlow(KEY_SELECTED_MODE, QuizMode.COUNTRIES.id),
        availableModes,
        modeContent,
        savedQuiz
    ) { selected, available, content, saved ->
        val classic = available.filter { it.id in classicIds }.sortedBy { it.spec.sortOrder }.map { it.toOption() }
        val newModes = available.filter { it.id !in classicIds }.sortedBy { it.spec.sortOrder }.map { it.toOption() }
        PlayUiState(
            selectedModeId = selected.takeIf { id -> classic.any { it.id == id } }
                ?: classic.firstOrNull()?.id
                ?: QuizMode.COUNTRIES.id,
            classicModes = classic,
            newModes = newModes,
            modeContent = content,
            savedQuiz = saved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayUiState())

    init {
        viewModelScope.launch {
            val countries = countryRepository.getAllCountries().first()
            modeContent.value = categoryGroups.build(countries)
        }
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
        answeredCount = savedQuizRepository.parseAnsweredCodes(answeredCountryCodes).size
    )

    companion object {
        const val KEY_SELECTED_MODE = "play_selected_mode"
    }
}
