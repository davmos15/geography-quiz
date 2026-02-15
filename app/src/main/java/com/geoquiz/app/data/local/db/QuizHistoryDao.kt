package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizHistoryDao {

    @Insert
    suspend fun insertQuizResult(result: QuizHistoryEntity)

    // Aggregate stats

    @Query("SELECT COUNT(*) FROM quiz_history")
    fun getTotalQuizzesCompleted(): Flow<Int>

    @Query("SELECT COALESCE(SUM(correctAnswers), 0) FROM quiz_history")
    fun getTotalCorrectAnswers(): Flow<Int>

    @Query("SELECT COALESCE(SUM(incorrectGuesses), 0) FROM quiz_history")
    fun getTotalIncorrectGuesses(): Flow<Int>

    @Query("SELECT COUNT(*) FROM quiz_history WHERE correctAnswers = totalQuestions AND totalQuestions > 0")
    fun getTotalPerfectQuizzes(): Flow<Int>

    @Query("SELECT COALESCE(SUM(totalQuestions), 0) FROM quiz_history")
    fun getTotalQuestionsAnswered(): Flow<Int>

    @Query("SELECT COALESCE(SUM(timeElapsedSeconds), 0) FROM quiz_history")
    fun getTotalTimeSpent(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT quizMode || '|' || categoryType || '|' || categoryValue) FROM quiz_history")
    fun getUniqueQuizzesCompleted(): Flow<Int>

    @Query("SELECT COALESCE(MAX(score), 0.0) FROM quiz_history")
    fun getHighestScore(): Flow<Double>

    @Query("SELECT COALESCE(AVG(CAST(correctAnswers AS REAL) / CASE WHEN totalQuestions = 0 THEN 1 ELSE totalQuestions END), 0.0) FROM quiz_history")
    fun getAverageAccuracy(): Flow<Double>

    // Per-mode stats

    @Query("SELECT COUNT(*) FROM quiz_history WHERE quizMode = :quizMode")
    fun getQuizzesCompletedForMode(quizMode: String): Flow<Int>

    // Batch best scores for a quiz mode

    @Query("""
        SELECT categoryType, categoryValue, MAX(score) as score,
               MAX(correctAnswers) as correctAnswers,
               MAX(totalQuestions) as totalQuestions
        FROM quiz_history
        WHERE quizMode = :quizMode
        GROUP BY categoryType, categoryValue
    """)
    suspend fun getAllBestScoresForMode(quizMode: String): List<QuizBestScore>

    // Recent history

    @Query("SELECT * FROM quiz_history ORDER BY completedAtMillis DESC LIMIT :limit")
    fun getRecentQuizzes(limit: Int = 10): Flow<List<QuizHistoryEntity>>
}
