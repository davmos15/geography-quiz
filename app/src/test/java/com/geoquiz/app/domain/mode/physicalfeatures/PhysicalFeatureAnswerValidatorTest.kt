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
        val result = validator.validate("Lake Malawi", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Malawi", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `an alias is also accepted as a full answer, case and diacritic insensitively`() = runTest {
        val result = validator.validate("lake nyasa", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Malawi", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `a typo one edit away is correct at Normal (allowFuzzy)`() = runTest {
        val result = validator.validate("Lake Malwi", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertTrue(result is AnswerResult.Correct)
        assertTrue((result as AnswerResult.Correct).viaTypo)
    }

    @Test
    fun `the same typo is a near miss at Hard (no fuzzy)`() = runTest {
        val result = validator.validate("Lake Malwi", state(), allowFuzzy = false, aliasesByCode, targetId = "lake-malawi")
        assertEquals(AnswerResult.NearMiss, result)
    }

    @Test
    fun `already answered is reported once correct again`() = runTest {
        val result = validator.validate(
            "Lake Malawi", state(answered = setOf("lake-malawi")), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi"
        )
        assertEquals(AnswerResult.AlreadyAnswered, result)
    }

    @Test
    fun `an unrelated name is incorrect`() = runTest {
        val result = validator.validate("Lake Baikal", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `blank input is incorrect`() = runTest {
        val result = validator.validate("   ", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `a different round feature's exact name is still correct for that feature`() = runTest {
        // Exact matches check every round feature regardless of targetId (only typo tolerance is
        // scoped to the target, M1); callers (the ViewModel) compare the matched code against the
        // current target to decide same-feature vs not.
        val result = validator.validate("Lake Victoria", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertTrue(result is AnswerResult.Correct)
        assertEquals("Lake Victoria", (result as AnswerResult.Correct).countryName)
    }

    @Test
    fun `M1 a typo of a different round feature is not a near miss when it is not the target`() = runTest {
        // "Lake Victoira" is one transposition away from "Lake Victoria", but the target is Lake
        // Malawi: typo tolerance must not reach across to a feature the player wasn't asked about.
        val result = validator.validate("Lake Victoira", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-malawi")
        assertEquals(AnswerResult.Incorrect, result)
    }

    @Test
    fun `M1 the same typo is a near miss once it is actually the target`() = runTest {
        val result = validator.validate("Lake Victoira", state(), allowFuzzy = true, aliasesByCode, targetId = "lake-victoria")
        assertTrue(result is AnswerResult.Correct)
        assertTrue((result as AnswerResult.Correct).viaTypo)
    }

    @Test
    fun `H1 a generic word difference is still an exact match`() = runTest {
        val withGenericWord = mapOf(
            "lake-malawi" to com.geoquiz.app.domain.model.Country(
                "lake-malawi", "Nile River", "Nile River", "", "", "Nile River".length, ""
            )
        )
        val riverState = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(withGenericWord["lake-malawi"]!!)),
            answeredCountries = emptySet()
        )
        val result = validator.validate("Nile", riverState, allowFuzzy = true, emptyMap(), targetId = "lake-malawi")
        assertTrue(result is AnswerResult.Correct)
    }
}
