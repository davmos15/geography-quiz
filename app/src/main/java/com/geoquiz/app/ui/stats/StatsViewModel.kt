package com.geoquiz.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.model.Achievement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatsUiState(
    val totalQuizzesCompleted: Int = 0,
    val totalCorrectAnswers: Int = 0,
    val totalIncorrectGuesses: Int = 0,
    val totalPerfectQuizzes: Int = 0,
    val totalQuestionsAnswered: Int = 0,
    val totalTimeSpentSeconds: Int = 0,
    val uniqueQuizzesCompleted: Int = 0,
    val highestScore: Double = 0.0,
    val averageAccuracy: Double = 0.0,
    val unlockedAchievementCount: Int = 0,
    val totalAchievementCount: Int = Achievement.entries.size,
    val countriesQuizCount: Int = 0,
    val capitalsQuizCount: Int = 0,
    val flagsQuizCount: Int = 0
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    quizHistoryRepository: QuizHistoryRepository,
    achievementRepository: AchievementRepository
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
            quizHistoryRepository.highestScore,
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
    }.combine(
        combine(
            quizHistoryRepository.quizzesCompletedForMode("countries"),
            quizHistoryRepository.quizzesCompletedForMode("capitals"),
            quizHistoryRepository.quizzesCompletedForMode("flags")
        ) { countries, capitals, flags ->
            Triple(countries, capitals, flags)
        }
    ) { stats, modeCounts ->
        stats.copy(
            countriesQuizCount = modeCounts.first,
            capitalsQuizCount = modeCounts.second,
            flagsQuizCount = modeCounts.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())
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
