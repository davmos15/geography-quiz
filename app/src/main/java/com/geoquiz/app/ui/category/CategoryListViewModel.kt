package com.geoquiz.app.ui.category

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.PinnedCategoriesRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.challenge.ChallengeLinkSigner
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.repository.CountryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CategoryListUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val groupDescription: String = "",
    val quizOptions: List<QuizOptionInfo> = emptyList(),
    val hideCompleted: Boolean = false
)

data class QuizOptionInfo(
    val name: String,
    val countryCount: Int,
    val categoryType: String,
    val categoryValue: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val bestScore: Double? = null,
    val bestCorrect: Int? = null,
    val bestTotal: Int? = null,
    /** Mastery stars, 0 to 3 ([com.geoquiz.app.domain.usecase.MasteryStars]). */
    val masteryStars: Int = 0,
    /** Pinned in this mode (shown on the Play tab). */
    val isPinned: Boolean = false
)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CountryRepository,
    private val optionsBuilder: CategoryOptionsBuilder,
    private val quizHistoryRepository: QuizHistoryRepository,
    private val challengeRepository: ChallengeRepository,
    private val playGamesService: PlayGamesAchievementService,
    private val settingsRepository: SettingsRepository,
    private val pinnedCategoriesRepository: PinnedCategoriesRepository,
    private val challengeLinkSigner: ChallengeLinkSigner,
    gameModes: GameModeRegistry
) : ViewModel() {

    val playerName = playGamesService.playerName

    private val groupId: String = savedStateHandle["groupId"] ?: ""
    private val quizModeId: String = savedStateHandle["quizMode"] ?: "countries"
    private val quizMode: QuizMode = QuizMode.fromId(quizModeId)

    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    /** Tiers this mode offers, in Easy, Normal, Hard order. */
    val difficulties: List<Difficulty> = gameModes.findOrDefault(quizModeId).spec.supportedDifficulties
        .sortedBy { it.ordinal }

    /**
     * The tier a tapped category starts at: the remembered default. Changing it here changes
     * the default too, so a quiz stays two taps from home.
     */
    val difficulty: StateFlow<Difficulty> = settingsRepository.difficulty
        .stateIn(viewModelScope, SharingStarted.Eagerly, Difficulty.DEFAULT)

    fun onDifficultySelected(difficulty: Difficulty) {
        viewModelScope.launch { settingsRepository.setDifficulty(difficulty) }
    }

    /** This group's options before history and pins are applied; null while loading. */
    private val baseOptions = MutableStateFlow<List<QuizOptionInfo>?>(null)

    init {
        viewModelScope.launch {
            val allCountries = optionsBuilder.countriesFor(quizMode, repository.getAllCountries().first())

            // Try regular CategoryGroup first, then FlagCategoryGroup
            val group = CategoryGroup.fromId(groupId)
            val flagGroup = FlagCategoryGroup.fromId(groupId)

            val options = optionsBuilder.build(quizMode, groupId, allCountries)

            _uiState.update {
                it.copy(
                    groupName = group?.displayName ?: flagGroup?.displayName ?: "Unknown",
                    groupDescription = group?.description ?: flagGroup?.description ?: ""
                )
            }
            baseOptions.value = options
        }

        // Best scores, stars and pins follow their stores, so the rows are up to date when the
        // player comes back from a quiz or pins a category.
        viewModelScope.launch {
            combine(
                baseOptions.filterNotNull(),
                quizHistoryRepository.bestScoresForMode(quizModeId),
                quizHistoryRepository.masteryStarsForMode(quizModeId),
                pinnedCategoriesRepository.pinnedCategories.map { pins ->
                    pins.filter { it.modeId == quizModeId }
                        .map { QuizHistoryRepository.categoryKey(it.categoryType, it.categoryValue) }
                        .toSet()
                }
            ) { options, bestScores, starsByCategory, pinnedKeys ->
                options.map { option ->
                    val key = QuizHistoryRepository.categoryKey(option.categoryType, option.categoryValue)
                    val best = bestScores[key]
                    option.copy(
                        isCompleted = best != null,
                        bestScore = best?.score,
                        bestCorrect = best?.correctAnswers,
                        bestTotal = best?.totalQuestions,
                        masteryStars = starsByCategory[key] ?: 0,
                        isPinned = key in pinnedKeys
                    )
                }
            }.collect { options ->
                _uiState.update { it.copy(isLoading = false, quizOptions = options) }
            }
        }
    }

    /** Pins [option] (shown on the Play tab, newest last) or unpins it, for this mode. */
    fun onTogglePin(option: QuizOptionInfo) {
        viewModelScope.launch {
            pinnedCategoriesRepository.setPinned(
                modeId = quizModeId,
                categoryType = option.categoryType,
                categoryValue = option.categoryValue,
                pinned = !option.isPinned
            )
        }
    }

    /** Records a new outgoing challenge (no score yet) and returns its signed share link. */
    fun createChallengeShareUrl(categoryType: String, categoryValue: String): Uri {
        val deepLink = ChallengeDeepLink(
            challengeId = UUID.randomUUID().toString(),
            categoryType = categoryType,
            categoryValue = categoryValue,
            challengerName = playGamesService.playerName.value,
            challengerScore = null,
            challengerTotal = null,
            challengerTime = null,
            quizMode = quizModeId
        )
        saveOutgoingChallenge(deepLink.challengeId, categoryType, categoryValue)
        return deepLink.toShareUrl(challengeLinkSigner)
    }

    private fun saveOutgoingChallenge(challengeId: String, categoryType: String, categoryValue: String) {
        viewModelScope.launch {
            val name = playGamesService.playerName.value
            val displayName = QuizCategory.fromRoute(categoryType, categoryValue).displayName
            challengeRepository.createOutgoingChallenge(
                id = challengeId,
                categoryType = categoryType,
                categoryValue = categoryValue,
                categoryDisplayName = displayName,
                quizMode = quizModeId,
                challengerName = name,
                score = null,
                total = null,
                time = null
            )
        }
    }

    fun toggleHideCompleted() {
        _uiState.value = _uiState.value.copy(
            hideCompleted = !_uiState.value.hideCompleted
        )
    }
}
