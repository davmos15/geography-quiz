package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.QuizBestScore
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.data.local.db.QuizHistoryEntity
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.usecase.MasteryAttempt
import com.geoquiz.app.domain.usecase.MasteryStars
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuizHistoryRepository @Inject constructor(
    private val quizHistoryDao: QuizHistoryDao
) {
    suspend fun recordQuizResult(
        quizMode: String,
        categoryType: String,
        categoryValue: String,
        correctAnswers: Int,
        totalQuestions: Int,
        incorrectGuesses: Int,
        score: Double,
        timeElapsedSeconds: Int,
        perfectBonus: Boolean,
        difficulty: Difficulty
    ) {
        quizHistoryDao.insertQuizResult(
            QuizHistoryEntity(
                quizMode = quizMode,
                categoryType = categoryType,
                categoryValue = categoryValue,
                correctAnswers = correctAnswers,
                totalQuestions = totalQuestions,
                incorrectGuesses = incorrectGuesses,
                score = score,
                timeElapsedSeconds = timeElapsedSeconds,
                perfectBonus = perfectBonus,
                completedAtMillis = System.currentTimeMillis(),
                difficulty = difficulty.id
            )
        )
    }

    val totalQuizzesCompleted: Flow<Int> = quizHistoryDao.getTotalQuizzesCompleted()
    val totalCorrectAnswers: Flow<Int> = quizHistoryDao.getTotalCorrectAnswers()
    val totalIncorrectGuesses: Flow<Int> = quizHistoryDao.getTotalIncorrectGuesses()
    val totalPerfectQuizzes: Flow<Int> = quizHistoryDao.getTotalPerfectQuizzes()
    val totalQuestionsAnswered: Flow<Int> = quizHistoryDao.getTotalQuestionsAnswered()
    val totalTimeSpent: Flow<Int> = quizHistoryDao.getTotalTimeSpent()
    val uniqueQuizzesCompleted: Flow<Int> = quizHistoryDao.getUniqueQuizzesCompleted()
    val highestScore: Flow<Double> = quizHistoryDao.getHighestScore()
    val averageAccuracy: Flow<Double> = quizHistoryDao.getAverageAccuracy()

    fun recentQuizzes(limit: Int = 10): Flow<List<QuizHistoryEntity>> =
        quizHistoryDao.getRecentQuizzes(limit)

    fun quizzesCompletedForMode(quizMode: String): Flow<Int> =
        quizHistoryDao.getQuizzesCompletedForMode(quizMode)

    /**
     * The best result (highest score) per category of [quizMode], keyed by [categoryKey].
     * Categories never played are absent. Updates when history changes.
     */
    fun bestScoresForMode(quizMode: String): Flow<Map<String, QuizBestScore>> =
        quizHistoryDao.observeBestScoresForMode(quizMode).map { rows ->
            rows.associateBy { categoryKey(it.categoryType, it.categoryValue) }
        }

    /**
     * Mastery stars (0 to 3) per category of [quizMode], keyed by `"categoryType|categoryValue"`
     * as recorded in history. Categories never played are absent. Updates when history changes.
     */
    fun masteryStarsForMode(quizMode: String): Flow<Map<String, Int>> =
        quizHistoryDao.observeMasteryRowsForMode(quizMode).map { rows ->
            rows.groupBy { categoryKey(it.categoryType, it.categoryValue) }
                .mapValues { (_, categoryRows) ->
                    MasteryStars.calculate(
                        categoryRows.map {
                            MasteryAttempt(
                                difficulty = Difficulty.fromIdOrDefault(it.difficulty),
                                correct = it.correctAnswers,
                                total = it.totalQuestions
                            )
                        }
                    )
                }
        }

    /**
     * The categories of [quizMode] that have a recorded quiz, as [categoryKey]s, most recently
     * finished first (each once). Updates when history changes.
     */
    fun categoryKeysByRecencyForMode(quizMode: String): Flow<List<String>> =
        quizHistoryDao.observeCategoriesByRecencyForMode(quizMode).map { rows ->
            rows.map { categoryKey(it.categoryType, it.categoryValue) }
        }

    /** Leaderboard total over Normal and Hard quizzes; Easy never counts (D16). */
    suspend fun getTotalCorrectAnswersSync(): Long =
        quizHistoryDao.getTotalCorrectAnswersSync()

    /** Leaderboard total for one mode over Normal and Hard quizzes; Easy never counts (D16). */
    suspend fun getTotalCorrectAnswersForModeSync(quizMode: String): Long =
        quizHistoryDao.getTotalCorrectAnswersForModeSync(quizMode)

    suspend fun clearAllHistory() = quizHistoryDao.deleteAllHistory()

    companion object {
        /** Key used by [masteryStarsForMode] and [bestScoresForMode]. */
        fun categoryKey(categoryType: String, categoryValue: String): String = "$categoryType|$categoryValue"
    }
}
