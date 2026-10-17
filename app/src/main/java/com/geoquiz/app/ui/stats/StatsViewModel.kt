package com.geoquiz.app.ui.stats

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.QuizMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val totalQuizzesCompleted: Int = 0,
    val totalCorrectAnswers: Int = 0,
    val totalIncorrectGuesses: Int = 0,
    val totalPerfectQuizzes: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val totalTimeSpentSeconds: Int = 0,
    val uniqueQuizzesCompleted: Int = 0,
    /** Highest score over the classic modes only (task 9.2: new modes score on other scales). */
    val highestScore: Double = 0.0,
    val averageAccuracy: Double = 0.0,
    val unlockedAchievementCount: Int = 0,
    val totalAchievementCount: Int = Achievement.entries.size,
    /**
     * "By Mode" rows: the three classic modes always (as before), then every other registered
     * mode with any history, in registry order (task 9.2).
     */
    val modeCounts: List<ModeQuizCount> = emptyList()
)

/** One "By Mode" row: a mode's name (string resource) and how many of its quizzes were finished. */
data class ModeQuizCount(
    val modeId: String,
    @StringRes val nameRes: Int,
    val count: Int
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val quizHistoryRepository: QuizHistoryRepository,
    achievementRepository: AchievementRepository,
    private val playGamesService: PlayGamesAchievementService,
    private val gameModeRegistry: GameModeRegistry
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        quizHistoryRepository.totalQuizzesCompleted,
        quizHistoryRepository.totalCorrectAnswers,
        quizHistoryRepository.totalIncorrectGuesses,
        quizHistoryRepository.totalPerfectQuizzes,
        quizHistoryRepository.totalQuestionsAnswered
    ) { quizzes, correct, incorrect, perfect, totalQuestions ->
        StatsPartial1(quizzes, correct, incorrect, perfect, totalQuestions)
    }.combine(
        combine(
            quizHistoryRepository.totalTimeSpent,
            quizHistoryRepository.uniqueQuizzesCompleted,
            quizHistoryRepository.highestClassicScore,
            quizHistoryRepository.averageAccuracy,
            achievementRepository.unlockedAchievements
        ) { time, unique, highest, accuracy, unlocked ->
            StatsPartial2(time, unique, highest, accuracy, unlocked.size)
        }
    ) { p1, p2 ->
        StatsUiState(
            totalQuizzesCompleted = p1.quizzes,
            totalCorrectAnswers = p1.correct,
            totalIncorrectGuesses = p1.incorrect,
            totalPerfectQuizzes = p1.perfect,
            totalQuestionsAnswered = p1.totalQuestions,
            totalTimeSpentSeconds = p2.time,
            uniqueQuizzesCompleted = p2.unique,
            highestScore = p2.highest,
            averageAccuracy = p2.accuracy,
            unlockedAchievementCount = p2.unlockedCount
        )
    }.combine(quizHistoryRepository.quizCountsByMode) { stats, countsByMode ->
        stats.copy(modeCounts = modeRows(countsByMode))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    /**
     * Classic modes first (always shown, in [QuizMode] order), then every other registered mode
     * with history in the registry's sort order. A mode id in history that is no longer
     * registered is left out: there is no label for it (its rows still count in the totals).
     */
    private fun modeRows(countsByMode: Map<String, Int>): List<ModeQuizCount> {
        val classicIds = QuizMode.entries.map { it.id }.toSet()
        val classic = gameModeRegistry.classic
        val others = gameModeRegistry.all.filter { it.id !in classicIds && (countsByMode[it.id] ?: 0) > 0 }
        return (classic + others).map { mode ->
            ModeQuizCount(mode.id, mode.spec.labels.name, countsByMode[mode.id] ?: 0)
        }
    }

    fun showLeaderboards() {
        playGamesService.showAllLeaderboardsUI()
    }

    fun resetStatistics() {
        viewModelScope.launch {
            // Keeps the daily challenge's rows: they are its streak and its one attempt per
            // day (task 9.2). Settings' Reset all data still clears everything.
            quizHistoryRepository.clearHistoryExceptMode(DailyChallengeGameMode.MODE_ID)
        }
    }
}

private data class StatsPartial1(
    val quizzes: Int,
    val correct: Int,
    val incorrect: Int,
    val perfect: Int,
    val totalQuestions: Int
)

private data class StatsPartial2(
    val time: Int,
    val unique: Int,
    val highest: Double,
    val accuracy: Double,
    val unlockedCount: Int
)
