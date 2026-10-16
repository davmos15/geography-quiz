package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FlagSpeedScoring]'s percentage + combo-bonus formula (task 7.3, D52), with the worked examples
 * from its own KDoc, plus give-up and empty-round edge cases.
 */
class FlagSpeedScoringTest {

    private val chad = country("TCD", "Chad", "N'Djamena")
    private val romania = country("ROU", "Romania", "Bucharest")

    /** [askedCount] repeats of a target country, standing in for a round's repeated draws. */
    private fun askedCountries(askedCount: Int) = List(askedCount) { if (it % 2 == 0) chad else romania }

    private fun stateFor(asked: Int, incorrect: Int, longestStreak: Int, timeElapsedSeconds: Int = 60) = QuizState(
        quiz = Quiz(category = QuizCategory.AllCountries, countries = askedCountries(asked)),
        answeredCountries = setOf("TCD", "ROU"),
        incorrectGuesses = incorrect,
        currentInput = longestStreak.toString(),
        timeElapsedSeconds = timeElapsedSeconds,
        isComplete = true
    )

    @Test
    fun `worked example from the KDoc - 20 asked, 15 correct, longest streak 6`() {
        val result = FlagSpeedScoring.score(stateFor(asked = 20, incorrect = 5, longestStreak = 6))

        assertEquals(20, result.totalCountries)
        assertEquals(15, result.correctAnswers)
        assertEquals(5, result.incorrectGuesses)
        assertFalse(result.perfectBonus)
        // base = (15/20) * 15 = 11.25, comboBonus = (6 - 1) * 0.5 = 2.5, score = 13.75.
        assertEquals(13.75, result.score, 1e-9)
    }

    @Test
    fun `worked example from the KDoc - a perfect round of 10 with longest streak 10`() {
        val result = FlagSpeedScoring.score(stateFor(asked = 10, incorrect = 0, longestStreak = 10))

        assertEquals(10, result.totalCountries)
        assertEquals(10, result.correctAnswers)
        assertTrue(result.perfectBonus)
        // base = 10, comboBonus = (10 - 1) * 0.5 = 4.5, (10 + 4.5) * 1.2 = 17.4.
        assertEquals(17.4, result.score, 1e-9)
    }

    @Test
    fun `an isolated correct answer - streak of 1 - adds no combo bonus`() {
        val result = FlagSpeedScoring.score(stateFor(asked = 4, incorrect = 2, longestStreak = 1))
        // base = (2/4) * 2 = 1.0, comboBonus = max(1 - 1, 0) * 0.5 = 0.
        assertEquals(1.0, result.score, 1e-9)
    }

    @Test
    fun `giving up before answering anything scores zero without crashing`() {
        val result = FlagSpeedScoring.score(stateFor(asked = 0, incorrect = 0, longestStreak = 0))

        assertEquals(0, result.totalCountries)
        assertEquals(0, result.correctAnswers)
        assertEquals(0.0, result.score, 1e-9)
        assertFalse(result.perfectBonus)
        assertEquals(0, result.incorrectGuesses)
    }

    @Test
    fun `giving up mid-round scores only what was actually asked and answered`() {
        // Gave up after 7 questions: 5 correct, 2 wrong, best streak of 3.
        val result = FlagSpeedScoring.score(stateFor(asked = 7, incorrect = 2, longestStreak = 3, timeElapsedSeconds = 37))

        assertEquals(7, result.totalCountries)
        assertEquals(5, result.correctAnswers)
        assertEquals(37, result.timeElapsedSeconds)
        // base = (5/7) * 5 = 25/7, comboBonus = (3 - 1) * 0.5 = 1.0.
        assertEquals(25.0 / 7.0 + 1.0, result.score, 1e-9)
    }

    @Test
    fun `a malformed combo value in currentInput reads as zero rather than crashing`() {
        val state = stateFor(asked = 4, incorrect = 0, longestStreak = 0).copy(currentInput = "not-a-number")
        val result = FlagSpeedScoring.score(state)
        // base = (4/4) * 4 = 4, comboBonus = 0, perfect bonus applies: 4 * 1.2 = 4.8.
        assertEquals(4.8, result.score, 1e-9)
    }

    @Test
    fun `a combo larger than the round's own correct count is clamped, never inflating the bonus`() {
        // 4 asked, 2 incorrect -> 2 correct, but currentInput claims an impossible streak of 100.
        val state = stateFor(asked = 4, incorrect = 2, longestStreak = 100)
        val result = FlagSpeedScoring.score(state)
        // base = (2/4) * 2 = 1.0, comboBonus clamped to (2 - 1) * 0.5 = 0.5, not perfect.
        assertEquals(1.5, result.score, 1e-9)
    }

    @Test
    fun `the category is carried through from the quiz state`() {
        val state = stateFor(asked = 2, incorrect = 0, longestStreak = 2)
            .let { it.copy(quiz = it.quiz.copy(category = QuizCategory.ByRegion("Europe"))) }
        val result = FlagSpeedScoring.score(state)
        assertEquals(QuizCategory.ByRegion("Europe"), result.category)
    }
}
