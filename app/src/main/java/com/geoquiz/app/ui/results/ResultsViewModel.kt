package com.geoquiz.app.ui.results

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.data.service.InterstitialPolicy
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CompletedQuizRepository
import com.geoquiz.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsUiState {
    data object Loading : ResultsUiState

    /** The result is gone (e.g. "Reset all data" ran, or a newer quiz replaced it). */
    data object Missing : ResultsUiState

    data class Loaded(val result: CompletedQuiz) : ResultsUiState
}

/**
 * Loads the finished quiz by the id in the route (`results/{resultId}`), so Results survives
 * process death.
 *
 * Results is only reached from a freshly finished quiz, so the first time this screen loads a
 * result it counts that quiz with [InterstitialPolicy] and decides whether to show an
 * interstitial. The decision is made once per Results entry: a flag in the SavedStateHandle
 * stops a Results screen restored after process death from counting the quiz again.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val playGamesService: PlayGamesAchievementService,
    private val challengeRepository: ChallengeRepository,
    private val completedQuizRepository: CompletedQuizRepository,
    private val adManager: AdManager,
    private val interstitialPolicy: InterstitialPolicy
) : ViewModel() {
    val playerName = playGamesService.playerName

    private val resultId: String? = savedStateHandle[Screen.Results.ARG_RESULT_ID]

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val _challengeResult = MutableStateFlow<ChallengeEntity?>(null)
    val challengeResult: StateFlow<ChallengeEntity?> = _challengeResult.asStateFlow()

    /** True while an interstitial is due; the screen shows it once Results has rendered. */
    private val _interstitialDue = MutableStateFlow(false)
    val interstitialDue: StateFlow<Boolean> = _interstitialDue.asStateFlow()

    init {
        viewModelScope.launch {
            val result = resultId?.let { completedQuizRepository.get(it) }
            if (result == null) {
                _uiState.value = ResultsUiState.Missing
                return@launch
            }
            decideInterstitial(result)
            _uiState.value = ResultsUiState.Loaded(result)

            // Update the incoming challenge with our result. This sets the same values every
            // time, so running it again after process death is harmless.
            val challengeId = result.challengeId?.takeIf { it.isNotBlank() }
            if (challengeId != null) {
                challengeRepository.updateMyResult(challengeId, result.correct, result.total, result.timeSeconds)
                _challengeResult.value = challengeRepository.getChallengeById(challengeId)
            }
        }
    }

    private fun decideInterstitial(result: CompletedQuiz) {
        if (savedStateHandle.get<Boolean>(KEY_AD_DECIDED) == true) return
        savedStateHandle[KEY_AD_DECIDED] = true
        _interstitialDue.value = interstitialPolicy.onQuizCompleted(result.timeSeconds)
    }

    /**
     * Called by the screen after the loaded Results content has been drawn. Shows the due
     * interstitial at most once; the player stays on Results when it closes. Only an ad that
     * actually appeared resets the frequency cap.
     */
    fun showDueInterstitial(activity: Activity) {
        if (!_interstitialDue.value) return
        _interstitialDue.value = false
        adManager.showInterstitial(activity, onShown = interstitialPolicy::onInterstitialShown)
    }

    fun saveOutgoingChallenge(
        challengeId: String,
        categoryType: String,
        categoryValue: String,
        quizMode: String,
        score: Int?,
        total: Int?,
        time: Int?
    ) {
        viewModelScope.launch {
            val name = playGamesService.playerName.value
            val displayName = QuizCategory.fromRoute(categoryType, categoryValue).displayName
            challengeRepository.createOutgoingChallenge(
                id = challengeId,
                categoryType = categoryType,
                categoryValue = categoryValue,
                categoryDisplayName = displayName,
                quizMode = quizMode,
                challengerName = name,
                score = score,
                total = total,
                time = time
            )
        }
    }

    companion object {
        /** Set once the interstitial decision for this Results entry has been made. */
        internal const val KEY_AD_DECIDED = "results_ad_decided"
    }
}
