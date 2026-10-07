package com.geoquiz.app.domain.usecase

import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.FakeCompletedQuizRepository
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteQuizUseCaseTest {

    private val completedQuizzes = FakeCompletedQuizRepository()
    private val savedQuizRepository = mockk<SavedQuizRepository>(relaxed = true)
    private val achievementRepository = mockk<AchievementRepository>()
    private val quizHistoryRepository = mockk<QuizHistoryRepository>(relaxed = true)
    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true)

    private val useCase = CompleteQuizUseCase(
        calculateScore = CalculateScoreUseCase(),
        completedQuizRepository = completedQuizzes,
        savedQuizRepository = savedQuizRepository,
        achievementRepository = achievementRepository,
        quizHistoryRepository = quizHistoryRepository,
        playGamesService = playGames
    )

    private val category = QuizCategory.StartingWithLetter('A')
    private val state = QuizState(
        quiz = Quiz(category = category, countries = TestQuizData.THREE),
        // Set order differs from quiz order on purpose.
        answeredCountries = setOf("AUT", "FRA"),
        isComplete = true,
        incorrectGuesses = 2,
        incorrectGuessStrings = listOf("Narnia", "Atlantis")
    )

    private fun request(id: String = "result-1", mode: QuizMode = QuizMode.CAPITALS) =
        CompleteQuizUseCase.Request(
            resultId = id,
            state = state,
            timeElapsedSeconds = 75,
            quizMode = mode,
            routeCategoryType = "startletter",
            routeCategoryValue = "A",
            hardMode = true,
            challengeId = "challenge-7"
        )

    init {
        coEvery { achievementRepository.onQuizCompleted(any(), any(), any(), any(), any(), any(), any()) } returns
            listOf(Achievement.entries.first())
        coEvery { quizHistoryRepository.getTotalCorrectAnswersSync() } returns 120L
        coEvery { quizHistoryRepository.getTotalCorrectAnswersForModeSync("capitals") } returns 30L
    }

    @Test
    fun `persists the completed quiz under the request id`() = runTest {
        val outcome = useCase(request())

        val stored = completedQuizzes.get("result-1")
        assertNotNull(stored)
        stored!!
        assertEquals(outcome.completedQuiz, stored)
        assertEquals("result-1", stored.id)
        assertEquals("capitals", stored.quizModeId)
        assertEquals(category.typeKey, stored.categoryType)
        assertEquals(category.valueKey, stored.categoryValue)
        assertEquals(category.displayName, stored.categoryName)
        assertEquals(listOf("FRA", "DEU", "AUT"), stored.countryCodes)
        // Answered codes follow quiz order.
        assertEquals(listOf("FRA", "AUT"), stored.answeredCodes)
        assertEquals(listOf("Narnia", "Atlantis"), stored.incorrectGuessStrings)
        assertEquals(2, stored.correct)
        assertEquals(3, stored.total)
        assertEquals(75, stored.timeSeconds)
        assertEquals(2.0 / 3 * 2, stored.score, 1e-9)
        assertFalse(stored.perfectBonus)
        assertEquals(2, stored.incorrectGuesses)
        assertTrue(stored.hardMode)
        assertEquals("challenge-7", stored.challengeId)
        assertTrue(stored.completedAtMillis > 0)
    }

    @Test
    fun `records achievements, history and leaderboards once and clears the resume save`() = runTest {
        val outcome = useCase(request())

        assertTrue(outcome.newlyRecorded)
        assertEquals(listOf(Achievement.entries.first()), outcome.newAchievements)
        coVerify(exactly = 1) {
            achievementRepository.onQuizCompleted(
                category = category,
                correctAnswers = 2,
                totalCountries = 3,
                timeElapsedSeconds = 75,
                quizMode = QuizMode.CAPITALS,
                incorrectGuesses = 2,
                hardMode = true
            )
        }
        verify(exactly = 1) { playGames.unlockAchievement(Achievement.entries.first()) }
        coVerify(exactly = 1) {
            quizHistoryRepository.recordQuizResult(
                quizMode = "capitals",
                categoryType = "startletter",
                categoryValue = "A",
                correctAnswers = 2,
                totalQuestions = 3,
                incorrectGuesses = 2,
                score = any(),
                timeElapsedSeconds = 75,
                perfectBonus = false
            )
        }
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.OVERALL, 120L) }
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.CAPITALS, 30L) }
        coVerify(exactly = 1) { savedQuizRepository.clearSavedQuiz() }
    }

    @Test
    fun `a second call with the same id returns the stored result and records nothing`() = runTest {
        val first = useCase(request())
        val second = useCase(request())

        assertEquals(first.completedQuiz, second.completedQuiz)
        assertFalse(second.newlyRecorded)
        assertTrue(second.newAchievements.isEmpty())
        assertEquals(1, completedQuizzes.saveCount)
        coVerify(exactly = 1) { achievementRepository.onQuizCompleted(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { quizHistoryRepository.recordQuizResult(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 2) { playGames.submitScore(any(), any()) }
    }

    @Test
    fun `a new id records a new result`() = runTest {
        useCase(request(id = "a"))
        useCase(request(id = "b"))

        assertEquals(2, completedQuizzes.saveCount)
        coVerify(exactly = 2) { quizHistoryRepository.recordQuizResult(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `the result is saved before anything is recorded`() = runTest {
        // If the process dies part-way, the stored result stops a second recording.
        val recorded = mutableListOf<String>()
        coEvery { achievementRepository.onQuizCompleted(any(), any(), any(), any(), any(), any(), any()) } answers {
            recorded += "achievements saw result=" + (completedQuizzes.stored != null)
            emptyList()
        }

        useCase(request())

        assertEquals(listOf("achievements saw result=true"), recorded)
        coVerifyOrder {
            savedQuizRepository.clearSavedQuiz()
            quizHistoryRepository.recordQuizResult(any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `each mode submits to its own leaderboard`() = runTest {
        coEvery { quizHistoryRepository.getTotalCorrectAnswersForModeSync("countries") } returns 55L

        useCase(request(mode = QuizMode.COUNTRIES))

        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.OVERALL, 120L) }
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.COUNTRIES, 55L) }
    }
}
