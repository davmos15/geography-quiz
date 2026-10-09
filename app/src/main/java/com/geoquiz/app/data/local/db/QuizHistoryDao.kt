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

    // Best result per category of a quiz mode (highest score); follows history, so category rows
    // update when the player comes back from a quiz

    @Query("""
        SELECT qh.categoryType, qh.categoryValue, qh.score, qh.correctAnswers, qh.totalQuestions
        FROM quiz_history qh
        INNER JOIN (
            SELECT categoryType, categoryValue, MAX(score) AS maxScore
            FROM quiz_history
            WHERE quizMode = :quizMode
            GROUP BY categoryType, categoryValue
        ) best ON qh.categoryType = best.categoryType
            AND qh.categoryValue = best.categoryValue
            AND qh.score = best.maxScore
        WHERE qh.quizMode = :quizMode
        GROUP BY qh.categoryType, qh.categoryValue
    """)
    fun observeBestScoresForMode(quizMode: String): Flow<List<QuizBestScore>>

    // Mastery stars: every distinct (category, tier, result) for a mode. Duplicate results
    // collapse, so the list stays small; MasteryStars picks the best per category.

    @Query("""
        SELECT DISTINCT categoryType, categoryValue, difficulty, correctAnswers, totalQuestions
        FROM quiz_history
        WHERE quizMode = :quizMode AND totalQuestions > 0
    """)
    fun observeMasteryRowsForMode(quizMode: String): Flow<List<QuizMasteryRow>>

    // Categories of a mode, most recently finished first (one row each), for "Recommended next"

    @Query("""
        SELECT categoryType, categoryValue
        FROM quiz_history
        WHERE quizMode = :quizMode
        GROUP BY categoryType, categoryValue
        ORDER BY MAX(completedAtMillis) DESC, MAX(id) DESC
    """)
    fun observeCategoriesByRecencyForMode(quizMode: String): Flow<List<QuizCategoryRef>>

    // Recent history

    @Query("SELECT * FROM quiz_history ORDER BY completedAtMillis DESC LIMIT :limit")
    fun getRecentQuizzes(limit: Int = 10): Flow<List<QuizHistoryEntity>>

    // Cumulative totals (for leaderboard submission). Easy quizzes never count (D16).

    @Query("SELECT COALESCE(SUM(correctAnswers), 0) FROM quiz_history WHERE difficulty != 'easy'")
    suspend fun getTotalCorrectAnswersSync(): Long

    @Query("SELECT COALESCE(SUM(correctAnswers), 0) FROM quiz_history WHERE quizMode = :quizMode AND difficulty != 'easy'")
    suspend fun getTotalCorrectAnswersForModeSync(quizMode: String): Long

    // Reset

    @Query("DELETE FROM quiz_history")
    suspend fun deleteAllHistory()
}

/** A recorded category of a mode, as returned by [QuizHistoryDao.observeCategoriesByRecencyForMode]. */
data class QuizCategoryRef(
    val categoryType: String,
    val categoryValue: String
)
