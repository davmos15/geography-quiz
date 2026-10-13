package com.geoquiz.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.domain.model.Difficulty
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [QuizHistoryRepository.getTotalCorrectAnswersSync] feeds the "Overall" leaderboard total
 * (`PlayGamesSyncManager`, `CompleteQuizUseCase`). New modes (Silhouettes, Tap the map, ...)
 * never submit leaderboard scores (D35), so their history rows must not contribute to it even
 * though they are recorded in history (for Stats and mastery stars).
 */
@RunWith(RobolectricTestRunner::class)
class QuizHistoryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: QuizHistoryDao
    private lateinit var repository: QuizHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.quizHistoryDao()
        repository = QuizHistoryRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `overall total excludes a new mode's history rows`() = runBlocking {
        repository.recordQuizResult(
            quizMode = "countries",
            categoryType = "all",
            categoryValue = "_",
            correctAnswers = 10,
            totalQuestions = 20,
            incorrectGuesses = 0,
            score = 10.0,
            timeElapsedSeconds = 60,
            perfectBonus = false,
            difficulty = Difficulty.NORMAL
        )
        repository.recordQuizResult(
            quizMode = "silhouettes",
            categoryType = "all",
            categoryValue = "_",
            correctAnswers = 50,
            totalQuestions = 100,
            incorrectGuesses = 0,
            score = 50.0,
            timeElapsedSeconds = 90,
            perfectBonus = false,
            difficulty = Difficulty.NORMAL
        )

        // Only the classic Countries row (10) counts; the Silhouettes row (50) is excluded.
        assertEquals(10L, repository.getTotalCorrectAnswersSync())
    }

    @Test
    fun `overall total still sums every classic mode`() = runBlocking {
        repository.recordQuizResult(
            quizMode = "countries",
            categoryType = "all",
            categoryValue = "_",
            correctAnswers = 10,
            totalQuestions = 20,
            incorrectGuesses = 0,
            score = 10.0,
            timeElapsedSeconds = 60,
            perfectBonus = false,
            difficulty = Difficulty.NORMAL
        )
        repository.recordQuizResult(
            quizMode = "capitals",
            categoryType = "all",
            categoryValue = "_",
            correctAnswers = 5,
            totalQuestions = 20,
            incorrectGuesses = 0,
            score = 5.0,
            timeElapsedSeconds = 60,
            perfectBonus = false,
            difficulty = Difficulty.HARD
        )

        assertEquals(15L, repository.getTotalCorrectAnswersSync())
    }

    @Test
    fun `overall total still excludes easy quizzes`() = runBlocking {
        repository.recordQuizResult(
            quizMode = "countries",
            categoryType = "all",
            categoryValue = "_",
            correctAnswers = 10,
            totalQuestions = 20,
            incorrectGuesses = 0,
            score = 10.0,
            timeElapsedSeconds = 60,
            perfectBonus = false,
            difficulty = Difficulty.EASY
        )

        assertEquals(0L, repository.getTotalCorrectAnswersSync())
    }
}
