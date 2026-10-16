package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 8.1: worked examples, perfect-round bonus, zero-question edge case. */
class DailyChallengeScoringTest {

    private fun country(code: String) = Country(
        code = code,
        name = code,
        officialName = code,
        region = "Europe",
        subregion = "Europe",
        nameLength = code.length,
        capital = "$code City"
    )

    private fun state(askedCount: Int, correctCount: Int, incorrectGuesses: Int): QuizState {
        val asked = (1..askedCount).map { country("C$it") }
        val correct = asked.take(correctCount).map { it.code }.toSet()
        return QuizState(
            quiz = Quiz(category = QuizCategory.DailyChallenge("2026-10-16"), countries = asked),
            answeredCountries = correct,
            incorrectGuesses = incorrectGuesses,
            timeElapsedSeconds = 123,
            isComplete = true
        )
    }

    @Test
    fun `worked example - 8 correct out of 10, 2 incorrect, not perfect`() {
        val result = DailyChallengeScoring.score(state(askedCount = 10, correctCount = 8, incorrectGuesses = 2))

        assertEquals(10, result.totalCountries)
        assertEquals(8, result.correctAnswers)
        assertEquals(2, result.incorrectGuesses)
        assertFalse(result.perfectBonus)
        assertEquals(6.4, result.score, 1e-9)
    }

    @Test
    fun `a perfect round scores with the 1point2 bonus`() {
        val result = DailyChallengeScoring.score(state(askedCount = 10, correctCount = 10, incorrectGuesses = 0))

        assertTrue(result.perfectBonus)
        assertEquals(12.0, result.score, 1e-9)
    }

    @Test
    fun `giving up with nothing asked scores zero and is not perfect`() {
        val result = DailyChallengeScoring.score(state(askedCount = 0, correctCount = 0, incorrectGuesses = 0))

        assertEquals(0, result.totalCountries)
        assertEquals(0, result.correctAnswers)
        assertEquals(0.0, result.score, 1e-9)
        assertFalse(result.perfectBonus)
    }

    @Test
    fun `time elapsed and category pass through unchanged`() {
        val result = DailyChallengeScoring.score(state(askedCount = 4, correctCount = 2, incorrectGuesses = 2))

        assertEquals(123, result.timeElapsedSeconds)
        assertEquals(QuizCategory.DailyChallenge("2026-10-16"), result.category)
    }

    @Test
    fun `incorrectGuesses is coerced into range so a malformed state cannot score negative or over-count`() {
        val malformed = state(askedCount = 5, correctCount = 5, incorrectGuesses = -1).copy(incorrectGuesses = -1)
        val result = DailyChallengeScoring.score(malformed)

        assertEquals(0, result.incorrectGuesses)
        assertTrue(result.perfectBonus)
    }
}
