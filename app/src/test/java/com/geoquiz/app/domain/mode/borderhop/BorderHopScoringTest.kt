package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderHopScoringTest {

    private val france = country("FRA", "France", "Paris")
    private val spain = country("ESP", "Spain", "Madrid")
    private val andorra = country("AND", "Andorra", "Andorra la Vella")
    private val portugal = country("PRT", "Portugal", "Lisbon")

    private fun stateFor(path: List<com.geoquiz.app.domain.model.Country>, extraHops: Int, reached: Boolean) = QuizState(
        quiz = Quiz(category = QuizCategory.AllCountries, countries = path),
        answeredCountries = path.map { it.code }.toSet(),
        incorrectGuesses = extraHops,
        currentInput = if (reached) BorderHopScoring.FLAG_REACHED else BorderHopScoring.FLAG_GAVE_UP,
        isComplete = true
    )

    @Test
    fun `an optimal completed path scores full marks with the perfect bonus`() {
        // Portugal -> Spain -> France, par = 2, used = 2 (no extra hops).
        val path = listOf(portugal, spain, france)
        val result = BorderHopScoring.score(stateFor(path, extraHops = 0, reached = true))

        assertEquals(2, result.totalCountries)
        assertEquals(2, result.correctAnswers)
        assertTrue(result.perfectBonus)
        assertEquals(0, result.incorrectGuesses)
        // percentage 1.0 * par(2) * 1.2 bonus = 2.4
        assertEquals(2.4, result.score, 1e-9)
    }

    @Test
    fun `a completed path with extra hops scores less and loses the perfect bonus`() {
        // Portugal -> Spain -> Andorra -> France: par is 2 (via Spain-France direct), used 3.
        val path = listOf(portugal, spain, andorra, france)
        val result = BorderHopScoring.score(stateFor(path, extraHops = 1, reached = true))

        assertEquals(3, result.totalCountries)
        assertEquals(2, result.correctAnswers)
        assertFalse(result.perfectBonus)
        assertEquals(1, result.incorrectGuesses)
        // percentage = 2/3, baseScore = 2/3 * 2 = 1.333..., no bonus.
        assertEquals(2.0 / 3.0 * 2.0, result.score, 1e-9)
    }

    @Test
    fun `giving up scores zero and is never a perfect bonus`() {
        val path = listOf(portugal, spain)
        val result = BorderHopScoring.score(stateFor(path, extraHops = 0, reached = false))

        assertEquals(0, result.correctAnswers)
        assertEquals(0.0, result.score, 1e-9)
        assertFalse(result.perfectBonus)
        // The hop count so far is kept, for Results' "Incorrect Guesses" row.
        assertEquals(1, result.incorrectGuesses)
    }

    @Test
    fun `giving up before any hop scores zero without crashing on an empty path`() {
        val path = listOf(portugal)
        val result = BorderHopScoring.score(stateFor(path, extraHops = 0, reached = false))

        assertEquals(0, result.totalCountries)
        assertEquals(0, result.correctAnswers)
        assertEquals(0.0, result.score, 1e-9)
    }

    @Test
    fun `the category is carried through from the quiz state`() {
        val state = stateFor(listOf(portugal, spain), extraHops = 0, reached = true)
            .let { it.copy(quiz = it.quiz.copy(category = QuizCategory.ByRegion("Europe"))) }
        val result = BorderHopScoring.score(state)
        assertEquals(QuizCategory.ByRegion("Europe"), result.category)
    }
}
