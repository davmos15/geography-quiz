package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyAnswerValidatorTest {

    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val countryRepository = mockk<CountryRepository>()
    private val currencyRepository = mockk<CurrencyRepository>()

    private val peru = country("PER", "Peru", "Lima", region = "Americas")
    private val andorra = country("AND", "Andorra", "Andorra la Vella", region = "Europe")
    private val allCountries = listOf(peru, andorra)

    private val eur = CurrencyEntry("EUR", "Euro", "€", listOf("AND"), shared = true, fact = null)
    private val pen = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("PER"), shared = false, fact = null)

    private fun validator(): CurrencyAnswerValidator {
        every { countryRepository.getAllCountries() } returns flowOf(allCountries)
        return CurrencyAnswerValidator(validateAnswer, countryRepository, currencyRepository)
    }

    @Test
    fun `rejects any typed answer for a shared currency's representative (D50 defence in depth)`() = runBlocking {
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("PER" to "PEN")
        )
        val state = QuizState(quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(andorra)))

        val result = validator().validate("Andorra", state, allowFuzzy = true)

        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `delegates to ValidateAnswerUseCase for a non-shared currency's target`() = runBlocking {
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("PER" to "PEN")
        )
        val state = QuizState(quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(peru)))
        coEvery { validateAnswer("Peru", state, true) } returns AnswerResult.Correct("Peru")

        val result = validator().validate("Peru", state, allowFuzzy = true)

        assertEquals(AnswerResult.Correct("Peru"), result)
    }

    @Test
    fun `an empty quiz (no single target) is Incorrect without consulting the repository`() = runBlocking {
        val state = QuizState(quiz = Quiz(category = QuizCategory.AllCountries, countries = emptyList()))

        val result = validator().validate("Peru", state, allowFuzzy = true)

        assertEquals(AnswerResult.Incorrect, result)
    }
}
