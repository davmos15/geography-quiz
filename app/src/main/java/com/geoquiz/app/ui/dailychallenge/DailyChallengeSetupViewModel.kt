package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** [streak] defaults to "no streak yet" until [ObserveStreakUseCase] emits its first value. */
data class DailyChallengeSetupUiState(
    val isLoading: Boolean = true,
    val alreadyCompletedToday: Boolean = false,
    val streak: StreakStatus = StreakStatus.NONE
)

/**
 * Daily challenge's own setup screen (task 8.1): no region or category filter, just today's date,
 * the streak (8.2) and whether today has already been played.
 *
 * Both come from one live source, [ObserveStreakUseCase]: its played dates are the same
 * `quiz_history` rows (this mode's id, category type "daily", value = the ISO date) that
 * `QuizHistoryRepository.hasCompletedCategory` checks, and [StreakStatus.playedToday] is exactly
 * "today's date is among them". Deriving "already played" from it keeps the Start button and the
 * streak consistent when the player comes back here from Results, and both roll over at midnight.
 */
@HiltViewModel
class DailyChallengeSetupViewModel @Inject constructor(
    private val observeStreak: ObserveStreakUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyChallengeSetupUiState())
    val uiState: StateFlow<DailyChallengeSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeStreak().collect { streak ->
                _uiState.value = DailyChallengeSetupUiState(
                    isLoading = false,
                    alreadyCompletedToday = streak.playedToday,
                    streak = streak
                )
            }
        }
    }
}
