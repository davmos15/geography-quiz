package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.streak.ObserveStreakUseCase
import com.geoquiz.app.domain.streak.StreakStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
 *
 * Review fix: [uiState] is built with [kotlinx.coroutines.flow.stateIn]/
 * [SharingStarted.WhileSubscribed] (same pattern as `PlayViewModel.uiState`), not a `collect` in
 * `init` that runs once for the ViewModel's whole lifetime. A `collect` in `init` keeps observing
 * the *first* [ObserveStreakUseCase] flow forever, including its one-shot "today" read inside
 * [com.geoquiz.app.domain.streak.ObserveStreakUseCase.localDateTicks]; if the screen is left open
 * (or the ViewModel otherwise outlives a long sleep) it could keep showing a stale "today" even
 * after [ObserveStreakUseCase] itself starts re-checking the wall clock more often. `WhileSubscribed`
 * restarts the upstream flow - re-reading today - every time the Setup screen's composable (re)
 * subscribes, e.g. navigating back to it after a sleep that crossed midnight.
 */
@HiltViewModel
class DailyChallengeSetupViewModel @Inject constructor(
    observeStreak: ObserveStreakUseCase
) : ViewModel() {

    val uiState: StateFlow<DailyChallengeSetupUiState> = observeStreak()
        .map { streak ->
            DailyChallengeSetupUiState(
                isLoading = false,
                alreadyCompletedToday = streak.playedToday,
                streak = streak
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DailyChallengeSetupUiState())
}
