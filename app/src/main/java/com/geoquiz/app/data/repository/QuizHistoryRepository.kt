package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.QuizBestScore
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.data.local.db.QuizHistoryEntity
import kotlinx.coroutines.flow.Flow
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
        perfectBonus: Boolean
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
                completedAtMillis = System.currentTimeMillis()
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

    suspend fun getAllBestScoresForMode(quizMode: String): List<QuizBestScore> =
        quizHistoryDao.getAllBestScoresForMode(quizMode)
}
