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

    // ---- Australian states and territories (P10-D2) ---------------------------------------

    private val newSouthWales = Country("AU-NSW", "New South Wales", "New South Wales", "", "", "New South Wales".length, "")
    private val tasmania = Country("AU-TAS", "Tasmania", "Tasmania", "", "", "Tasmania".length, "")
    private val westernAustralia = Country("AU-WA", "Western Australia", "Western Australia", "", "", "Western Australia".length, "")

    private val auPostalByCode = mapOf("AU-NSW" to "NSW", "AU-TAS" to "TAS", "AU-WA" to "WA")

    private fun auState() = QuizState(
        quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(newSouthWales, tasmania, westernAustralia))
    )

    @Test
    fun `an Australian full name is correct`() = runTest {
        val result = validator.validate("Tasmania", auState(), allowFuzzy = true, auPostalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Tasmania", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `an Australian postal code is accepted, case-insensitively`() = runTest {
        val result = validator.validate("nsw", auState(), allowFuzzy = false, auPostalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("New South Wales", (result as AnswerResult.Correct).countryName)
    }

    /**
     * WA is also Washington's US postal code, but the validator only ever considers the active
     * round's own members (task brief point 6): an Australian round's "WA" can only ever mean
     * Western Australia, never clashing with a US round that is never in scope at the same time.
     */
    @Test
    fun `WA resolves to Western Australia in an Australian round, no clash with the US postal code`() = runTest {
        val result = validator.validate("WA", auState(), allowFuzzy = false, auPostalByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Western Australia", (result as AnswerResult.Correct).countryName)
    }
}
