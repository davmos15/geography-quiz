package com.geoquiz.app.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.QuizCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playGamesService: PlayGamesAchievementService,
    private val challengeRepository: ChallengeRepository
) : ViewModel() {
    val playerName = playGamesService.playerName

    private val challengeId: String? = savedStateHandle.get<String>("challengeId")?.takeIf { it != "_" && it.isNotBlank() }
    private val correct: Int = savedStateHandle["correct"] ?: 0
    private val total: Int = savedStateHandle["total"] ?: 0
    private val time: Int = savedStateHandle["time"] ?: 0

    init {
        // Update the incoming challenge record with our quiz result
        if (challengeId != null) {
            viewModelScope.launch {
                challengeRepository.updateMyResult(challengeId, correct, total, time)
            }
        }
    }

    fun saveOutgoingChallenge(
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
}
