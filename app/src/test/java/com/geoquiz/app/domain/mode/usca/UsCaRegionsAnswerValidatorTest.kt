package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Name all's answer check (task 6.2, D43): typo-tolerant full names and exact postal codes,
 * never touching [com.geoquiz.app.domain.repository.CountryRepository] or Room.
 */
class UsCaRegionsAnswerValidatorTest {

    private val california = Country("US-CA", "California", "California", "", "", "California".length, "")
    private val newYork = Country("US-NY", "New York", "New York", "", "", "New York".length, "")
    private val texas = Country("US-TX", "Texas", "Texas", "", "", "Texas".length, "")

    private val postalByCode = mapOf("US-CA" to "CA", "US-NY" to "NY", "US-TX" to "TX")

    private val validator = UsCaRegionsAnswerValidator(NormalizeInputUseCase())

    private fun state(answered: Set<String> = emptySet()) = QuizState(
        quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(california, newYork, texas)),
        answeredCountries = answered
    )

    @Test
    fun `the exact full name is correct`() = runTest {
        val result = validator.validate("California", state(), allowFuzzy = true, postalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("California", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `the postal abbreviation is also accepted as a full answer`() = runTest {
        val result = validator.validate("CA", state(), allowFuzzy = true, postalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("California", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `postal abbreviation matches exactly only, case-insensitively via normalisation`() = runTest {
        val result = validator.validate("ca", state(), allowFuzzy = false, postalByCode)
        assertTrue(result is AnswerResult.Correct)
    }

    @Test
    fun `a typo one edit away is correct at Normal (allowFuzzy)`() = runTest {
        val result = validator.validate("Califronia", state(), allowFuzzy = true, postalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertTrue((result as AnswerResult.Correct).viaTypo)
    }

    @Test
    fun `the same typo is a near miss at Hard (no fuzzy)`() = runTest {
        val result = validator.validate("Califronia", state(), allowFuzzy = false, postalByCode)
        assertEquals(AnswerResult.NearMiss, result)
    }

    @Test
    fun `already answered is reported once correct again`() = runTest {
        val result = validator.validate("California", state(answered = setOf("US-CA")), allowFuzzy = true, postalByCode)
        assertEquals(AnswerResult.AlreadyAnswered, result)
    }

    @Test
    fun `an unrelated name is incorrect`() = runTest {
        val result = validator.validate("Florida", state(), allowFuzzy = true, postalByCode)
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `blank input is incorrect`() = runTest {
        val result = validator.validate("   ", state(), allowFuzzy = true, postalByCode)
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `a postal code for a region not in the quiz is incorrect, not mistaken for another`() = runTest {
        val result = validator.validate("WA", state(), allowFuzzy = true, postalByCode)
        assertEquals(AnswerResult.Incorrect, result)
    }
}
