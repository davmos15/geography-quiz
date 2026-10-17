package com.geoquiz.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.domain.model.Difficulty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Task 9.2: Stats' Reset Statistics, By Mode rows and Highest Score.

    private suspend fun record(
        quizMode: String,
        categoryType: String = "all",
        categoryValue: String = "_",
        score: Double = 1.0
    ) = repository.recordQuizResult(
        quizMode = quizMode,
        categoryType = categoryType,
        categoryValue = categoryValue,
        correctAnswers = 1,
        totalQuestions = 2,
        incorrectGuesses = 0,
        score = score,
        timeElapsedSeconds = 10,
        perfectBonus = false,
        difficulty = Difficulty.NORMAL
    )

    @Test
    fun `clearing history except the daily challenge keeps its rows and dates`() = runBlocking {
        record("countries")
        record("silhouettes")
        record("daily_challenge", categoryType = "daily", categoryValue = "2026-10-16")
        record("daily_challenge", categoryType = "daily", categoryValue = "2026-10-17")

        repository.clearHistoryExceptMode("daily_challenge")

        assertEquals(2, repository.totalQuizzesCompleted.first())
        assertEquals(mapOf("daily_challenge" to 2), repository.quizCountsByMode.first())
        assertEquals(
            setOf("2026-10-16", "2026-10-17"),
            repository.distinctCategoryValues("daily_challenge", "daily").first().toSet()
        )
        assertTrue(repository.hasCompletedCategory("daily_challenge", "daily", "2026-10-17"))
    }

    @Test
    fun `clearing all history still removes the daily challenge too`() = runBlocking {
        record("countries")
        record("daily_challenge", categoryType = "daily", categoryValue = "2026-10-17")

        repository.clearAllHistory()

        assertEquals(0, repository.totalQuizzesCompleted.first())
    }

    @Test
    fun `quiz counts by mode cover every mode with history`() = runBlocking {
        record("countries")
        record("countries")
        record("flags")
        record("border_hop")

        assertEquals(
            mapOf("countries" to 2, "flags" to 1, "border_hop" to 1),
            repository.quizCountsByMode.first()
        )
    }

    @Test
    fun `highest classic score ignores new modes' own score scales`() = runBlocking {
        assertEquals(0.0, repository.highestClassicScore.first(), 0.0)
        record("capitals", score = 12.5)
        record("silhouettes", score = 99.0)

        assertEquals(12.5, repository.highestClassicScore.first(), 0.0)
    }
}
