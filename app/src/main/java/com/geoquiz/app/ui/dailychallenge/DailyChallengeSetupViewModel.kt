package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeDate
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.model.QuizCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DailyChallengeSetupUiState(
    val isLoading: Boolean = true,
    val alreadyCompletedToday: Boolean = false
)

/**
 * Daily challenge's own setup screen (task 8.1): no region or category filter, just today's date
 * and whether it has already been played. [com.geoquiz.app.data.repository.QuizHistoryRepository.hasCompletedCategory]
 * is the same history table every other mode records to, keyed by this mode's id and today's
 * "yyyy-MM-dd" category value - the acceptance test for "one attempt per day" and Stats agreeing.
 */
@HiltViewModel
class DailyChallengeSetupViewModel @Inject constructor(
    private val quizHistoryRepository: QuizHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyChallengeSetupUiState())
    val uiState: StateFlow<DailyChallengeSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val today = DailyChallengeDate.today()
            val completed = quizHistoryRepository.hasCompletedCategory(
                quizMode = DailyChallengeGameMode.MODE_ID,
                categoryType = QuizCategory.DailyChallenge(today).typeKey,
                categoryValue = today
            )
            _uiState.value = DailyChallengeSetupUiState(isLoading = false, alreadyCompletedToday = completed)
        }
    }
}
