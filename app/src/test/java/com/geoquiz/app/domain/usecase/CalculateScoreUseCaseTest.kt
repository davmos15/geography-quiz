package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalculateScoreUseCaseTest {

    private lateinit var calculateScore: CalculateScoreUseCase

    private val countries = listOf(
        Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6),
        Country("DEU", "Germany", "Federal Republic of Germany", "Europe", "Western Europe", 7),
        Country("JPN", "Japan", "Japan", "Asia", "Eastern Asia", 5),
        Country("BRA", "Brazil", "Federative Republic of Brazil", "Americas", "South America", 6),
        Country("AUS", "Australia", "Commonwealth of Australia", "Oceania", "Australia and New Zealand", 9)
    )

    private val quiz = Quiz(
        category = QuizCategory.AllCountries,
        countries = countries
    )

    @Before
    fun setUp() {
        calculateScore = CalculateScoreUseCase()
    }

    @Test
    fun `perfect score with bonus`() {
        val state = QuizState(
            quiz = quiz,
            answeredCountries = setOf("FRA", "DEU", "JPN", "BRA", "AUS"),
            timeElapsedSeconds = 120
        )

        val result = calculateScore(state)

        assertEquals(5, result.correctAnswers)
        assertEquals(5, result.totalCountries)
        assertTrue(result.perfectBonus)
        // 100% × 5 = 5.0, × 1.2 bonus = 6.0
        assertEquals(6.0, result.score, 0.001)
        assertEquals(120, result.timeElapsedSeconds)
    }

    @Test
    fun `partial score without bonus`() {
        val state = QuizState(
            quiz = quiz,
            answeredCountries = setOf("FRA", "DEU"),
            timeElapsedSeconds = 60
        )

        val result = calculateScore(state)

        assertEquals(2, result.correctAnswers)
        assertEquals(5, result.totalCountries)
        assertFalse(result.perfectBonus)
        // 40% × 2 = 0.8
        assertEquals(0.8, result.score, 0.001)
    }

    @Test
    fun `zero answers edge case`() {
        val state = QuizState(
            quiz = quiz,
            answeredCountries = emptySet(),
            timeElapsedSeconds = 30
        )

        val result = calculateScore(state)

        assertEquals(0, result.correctAnswers)
        assertEquals(5, result.totalCountries)
        assertFalse(result.perfectBonus)
        assertEquals(0.0, result.score, 0.001)
    }

    @Test
    fun `empty quiz edge case`() {
        val emptyQuiz = Quiz(
            category = QuizCategory.AllCountries,
            countries = emptyList()
        )
        val state = QuizState(quiz = emptyQuiz)

        val result = calculateScore(state)

        assertEquals(0, result.correctAnswers)
        assertEquals(0, result.totalCountries)
        assertFalse(result.perfectBonus)
        assertEquals(0.0, result.score, 0.001)
    }
}
