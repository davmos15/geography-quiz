package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed answers (task 7.2, D51): reuses [com.geoquiz.app.domain.usecase.resolveAnswer]'s shared
 * typo rules, same as [com.geoquiz.app.domain.mode.usca.UsCaRegionsAnswerValidatorTest] mirrors
 * for that mode.
 */
class PhysicalFeatureAnswerValidatorTest {

    private val malawi = com.geoquiz.app.domain.model.Country("lake-malawi", "Lake Malawi", "Lake Malawi", "", "", "Lake Malawi".length, "")
    private val victoria = com.geoquiz.app.domain.model.Country("lake-victoria", "Lake Victoria", "Lake Victoria", "", "", "Lake Victoria".length, "")
    private val tanganyika = com.geoquiz.app.domain.model.Country("lake-tanganyika", "Lake Tanganyika", "Lake Tanganyika", "", "", "Lake Tanganyika".length, "")

    private val aliasesByCode = mapOf("lake-malawi" to listOf("Lake Nyasa"))

    private val validator = PhysicalFeatureAnswerValidator(NormalizeInputUseCase())

    private fun state(answered: Set<String> = emptySet()) = QuizState(
        quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(malawi, victoria, tanganyika)),
        answeredCountries = answered
    )

    @Test
    fun `the exact name is correct`() = runTest {
        val result = validator.validate("Lake Malawi", state(), allowFuzzy = true, aliasesByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Malawi", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `an alias is also accepted as a full answer, case and diacritic insensitively`() = runTest {
        val result = validator.validate("lake nyasa", state(), allowFuzzy = true, aliasesByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Malawi", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `a typo one edit away is correct at Normal (allowFuzzy)`() = runTest {
        val result = validator.validate("Lake Malwi", state(), allowFuzzy = true, aliasesByCode)
        assertTrue(result is AnswerResult.Correct)
        assertTrue((result as AnswerResult.Correct).viaTypo)
    }

    @Test
    fun `the same typo is a near miss at Hard (no fuzzy)`() = runTest {
        val result = validator.validate("Lake Malwi", state(), allowFuzzy = false, aliasesByCode)
        assertEquals(AnswerResult.NearMiss, result)
    }

    @Test
    fun `already answered is reported once correct again`() = runTest {
        val result = validator.validate("Lake Malawi", state(answered = setOf("lake-malawi")), allowFuzzy = true, aliasesByCode)
        assertEquals(AnswerResult.AlreadyAnswered, result)
    }

    @Test
    fun `an unrelated name is incorrect`() = runTest {
        val result = validator.validate("Lake Baikal", state(), allowFuzzy = true, aliasesByCode)
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `blank input is incorrect`() = runTest {
        val result = validator.validate("   ", state(), allowFuzzy = true, aliasesByCode)
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `a different round feature's name is correct for that feature, not this one`() = runTest {
        // The validator judges against the whole round's quiz.countries; callers (the ViewModel)
        // compare the matched code against the current target to decide same-feature vs not.
        val result = validator.validate("Lake Victoria", state(), allowFuzzy = true, aliasesByCode)
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Victoria", (result as AnswerResult.Correct).countryName)
    }
}
