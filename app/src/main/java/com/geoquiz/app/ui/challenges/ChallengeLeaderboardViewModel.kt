package com.geoquiz.app.ui.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.ChallengeEntity
import com.geoquiz.app.data.repository.ChallengeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChallengeLeaderboardUiState(
    val isLoading: Boolean = true,
    val challenges: List<ChallengeEntity> = emptyList(),
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
)

@HiltViewModel
class ChallengeLeaderboardViewModel @Inject constructor(
    private val challengeRepository: ChallengeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeLeaderboardUiState())
    val uiState: StateFlow<ChallengeLeaderboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            challengeRepository.allChallenges.collect { challenges ->
                val completed = challenges.filter { it.status == "completed" && it.challengerScore != null && it.myScore != null }
                val wins = completed.count { (it.myScore ?: 0) > (it.challengerScore ?: 0) }
                val losses = completed.count { (it.myScore ?: 0) < (it.challengerScore ?: 0) }
                val ties = completed.count { (it.myScore ?: 0) == (it.challengerScore ?: 0) }
                _uiState.value = ChallengeLeaderboardUiState(
                    isLoading = false,
                    challenges = challenges,
                    wins = wins,
                    losses = losses,
                    ties = ties
                )
            }
        }
    }
}
