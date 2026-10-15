package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.mode.AnswerType
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsCaRegionsGameModeTest {

    private val mode = UsCaRegionsGameMode(CalculateScoreUseCase())

    @Test
    fun `spec is behind its own feature flag, every difficulty, map tap answer type`() {
        assertEquals(FeatureFlag.US_CA_REGIONS, mode.spec.featureFlag)
        assertEquals(Difficulty.entries.toSet(), mode.spec.supportedDifficulties)
        assertEquals(AnswerType.MAP_TAP, mode.spec.answerType)
        assertEquals("us_ca_regions", mode.id)
    }

    @Test
    fun `offers nothing - this mode has no classic category filter`() {
        assertFalse(mode.offers(QuizCategory.AllCountries))
        assertFalse(mode.offers(QuizCategory.ByRegion("Americas")))
        assertFalse(mode.offers(QuizCategory.Practice(listOf("US-CA"))))
    }

    @Test
    fun `scoring reuses the classic percentage formula`() {
        val california = Country("US-CA", "California", "California", "", "", 10, "")
        val newYork = Country("US-NY", "New York", "New York", "", "", 8, "")
        val state = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(california, newYork)),
            answeredCountries = setOf("US-CA", "US-NY")
        )
        val result = mode.scoring.score(state)
        assertEquals(2 * 1.2, result.score, 1e-9)
        assertTrue(result.perfectBonus)
    }

    @Test
    fun `a hint used drops the perfect bonus and its cost (D37)`() {
        val california = Country("US-CA", "California", "California", "", "", 10, "")
        val state = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(california)),
            answeredCountries = setOf("US-CA"),
            hintsUsed = listOf(HintType.FIRST_LETTER)
        )
        val result = mode.scoring.score(state)
        assertFalse(result.perfectBonus)
        assertTrue(result.score < 1.2)
    }

    @Test
    fun `difficultiesFor each variant`() {
        assertEquals(listOf(Difficulty.NORMAL, Difficulty.HARD), UsCaRegionsGameMode.difficultiesFor(UsCaRegionsGameMode.VARIANT_TAP))
        assertEquals(listOf(Difficulty.NORMAL, Difficulty.HARD), UsCaRegionsGameMode.difficultiesFor(UsCaRegionsGameMode.VARIANT_NAME_ALL))
        assertEquals(listOf(Difficulty.EASY), UsCaRegionsGameMode.difficultiesFor(UsCaRegionsGameMode.VARIANT_CHOICE))
    }

    @Test(expected = IllegalStateException::class)
    fun `generator is an unused placeholder`() {
        kotlinx.coroutines.runBlocking { mode.validator.validate("x", QuizState(Quiz(QuizCategory.AllCountries, emptyList())), true) }
    }
}
