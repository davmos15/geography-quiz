package com.geoquiz.app.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.domain.model.Achievement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AchievementsUiState(
    val achievements: List<AchievementDisplayInfo> = emptyList(),
    val quizzesCompleted: Int = 0
)

data class AchievementDisplayInfo(
    val achievement: Achievement,
    val unlocked: Boolean
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    achievementRepository: AchievementRepository
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        achievementRepository.unlockedAchievements,
        achievementRepository.quizzesCompleted
    ) { unlockedIds, quizCount ->
        val displayList = Achievement.entries.map { achievement ->
            AchievementDisplayInfo(
                achievement = achievement,
                unlocked = achievement.id in unlockedIds
            )
        }
        AchievementsUiState(
            achievements = displayList,
            quizzesCompleted = quizCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AchievementsUiState())
}
