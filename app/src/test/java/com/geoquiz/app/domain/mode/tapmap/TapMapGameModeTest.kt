package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 5.2: scoring takes hint costs off the score; [offers] restricts this mode's categories. */
class TapMapGameModeTest {

    private val mode = TapMapGameMode(
        generator = mockk(relaxed = true),
        validateAnswer = mockk<ValidateAnswerUseCase>(relaxed = true),
        calculateScore = CalculateScoreUseCase()
    )

    private fun stateFor(answered: Set<String>, hintsUsed: List<HintType>): QuizState {
        val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = TestQuizData.THREE)
        return QuizState(quiz = quiz, answeredCountries = answered, hintsUsed = hintsUsed)
    }

    @Test
    fun `a perfect round with no hints keeps the perfect bonus`() {
        val state = stateFor(TestQuizData.THREE.map { it.code }.toSet(), emptyList())
        val result = mode.scoring.score(state)

        assertTrue(result.perfectBonus)
        assertEquals(3.6, result.score, 1e-9) // (3/3) * 3 * 1.2
    }

    @Test
    fun `a hint removes the perfect bonus and takes its cost off the score`() {
        val state = stateFor(TestQuizData.THREE.map { it.code }.toSet(), listOf(HintType.FLASH_REGION))
        val result = mode.scoring.score(state)

        assertFalse(result.perfectBonus)
        assertEquals(3.0 - 0.25, result.score, 1e-9) // unbonused (3) minus FLASH_REGION's cost
    }

    @Test
    fun `offers a map region category for a known preset`() {
        assertTrue(mode.offers(QuizCategory.MapRegion("world")))
        assertTrue(mode.offers(QuizCategory.MapRegion("europe")))
    }

    @Test
    fun `never offers an unknown preset`() {
        assertFalse(mode.offers(QuizCategory.MapRegion("atlantis")))
    }

    @Test
    fun `offers practice sets`() {
        assertTrue(mode.offers(QuizCategory.Practice(listOf("FRA"))))
    }

    @Test
    fun `never offers a classic category`() {
        assertFalse(mode.offers(QuizCategory.AllCountries))
        assertFalse(mode.offers(QuizCategory.ByRegion("Europe")))
    }
}
