package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ValidateAnswerUseCaseTest {

    private lateinit var validateAnswer: ValidateAnswerUseCase
    private lateinit var repository: CountryRepository
    private lateinit var normalizeInput: NormalizeInputUseCase

    private val france = Country("FRA", "France", "French Republic", "Europe", "Western Europe", 6)
    private val germany = Country("DEU", "Germany", "Federal Republic of Germany", "Europe", "Western Europe", 7)
    private val japan = Country("JPN", "Japan", "Japan", "Asia", "Eastern Asia", 5)

    private val quiz = Quiz(
        category = QuizCategory.AllCountries,
        countries = listOf(france, germany, japan)
    )

    @Before
    fun setUp() {
        repository = mockk()
        normalizeInput = NormalizeInputUseCase()
        validateAnswer = ValidateAnswerUseCase(repository, normalizeInput)
    }

    @Test
    fun `correct answer returns Correct`() = runTest {
        coEvery { repository.findCountryByAnswer("France") } returns france
        val state = QuizState(quiz = quiz)

        val result = validateAnswer("France", state)

        assertTrue(result is AnswerResult.Correct)
        assertEquals("France", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `already answered returns AlreadyAnswered`() = runTest {
        coEvery { repository.findCountryByAnswer("France") } returns france
        val state = QuizState(quiz = quiz, answeredCountries = setOf("FRA"))

        val result = validateAnswer("France", state)

        assertTrue(result is AnswerResult.AlreadyAnswered)
    }

    @Test
    fun `unrecognized answer returns Incorrect`() = runTest {
        coEvery { repository.findCountryByAnswer("Atlantis") } returns null
        val state = QuizState(quiz = quiz)

        val result = validateAnswer("Atlantis", state)

        assertTrue(result is AnswerResult.Incorrect)
    }

    @Test
    fun `blank input returns Incorrect`() = runTest {
        val state = QuizState(quiz = quiz)

        val result = validateAnswer("   ", state)

        assertTrue(result is AnswerResult.Incorrect)
    }

    @Test
    fun `country not in quiz set returns Incorrect`() = runTest {
        val brazil = Country("BRA", "Brazil", "Federative Republic of Brazil", "Americas", "South America", 6)
        coEvery { repository.findCountryByAnswer("Brazil") } returns brazil
        val state = QuizState(quiz = quiz)

        val result = validateAnswer("Brazil", state)

        assertTrue(result is AnswerResult.Incorrect)
    }
}
