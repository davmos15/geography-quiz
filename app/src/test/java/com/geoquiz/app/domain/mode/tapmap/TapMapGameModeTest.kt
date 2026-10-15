package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 5.2, 5.2b: scoring takes hint costs off the score; [offers] restricts this mode's
 * categories; [validator] is Name all's answer path (D40), delegating straight to the injected
 * [ValidateAnswerUseCase] exactly like the classic Countries quiz.
 */
class TapMapGameModeTest {

    private val validateAnswer = mockk<ValidateAnswerUseCase>(relaxed = true)
    private val mode = TapMapGameMode(
        generator = mockk(relaxed = true),
        validateAnswer = validateAnswer,
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

    @Test
    fun `validator delegates to the injected ValidateAnswerUseCase (Name all, D40)`() = runTest {
        val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = TestQuizData.THREE)
        val state = QuizState(quiz = quiz)
        coEvery { validateAnswer("France", state, true) } returns AnswerResult.Correct("France")

        val result = mode.validator.validate("France", state, allowFuzzy = true)

        assertEquals(AnswerResult.Correct("France"), result)
        coVerify { validateAnswer("France", state, true) }
    }
}
