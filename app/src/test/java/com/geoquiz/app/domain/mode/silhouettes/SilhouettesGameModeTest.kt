package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForFlagQuizUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Task 5.1's replacement behaviour: own generator, scoring with hint costs and its own choice
 * generator. [offers] is covered by `GameModeRegistryTest` (shared file); this is the mode's own
 * scoring and wiring.
 */
class SilhouettesGameModeTest {

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val getCountriesForFlagQuiz = mockk<GetCountriesForFlagQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")

    private fun mode(seed: Int = 1) = SilhouettesGameMode(
        CountriesQuestionGenerator(getCountriesForQuiz, getCountriesForFlagQuiz),
        validateAnswer,
        CalculateScoreUseCase(),
        QuizRandom(Random(seed))
    )

    @Test
    fun `generator asks the pool and trims to a round, excluding flag categories (never offered)`() = runBlocking {
        val twenty = (1..20).map { country("C$it", "Country $it", "Capital $it") }
        coEvery { getCountriesForQuiz(QuizCategory.AllCountries) } returns twenty

        val items = mode().generator.items(QuizCategory.AllCountries)

        assertEquals(SilhouettesQuestionGenerator.ROUND_SIZE, items.size)
        assertTrue(items.all { it in twenty })
    }

    @Test
    fun `scoring applies this round's hint costs on top of the base score`() {
        val quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(france, germany))
        val state = QuizState(
            quiz = quiz,
            answeredCountries = setOf(france.code, germany.code),
            hintsUsed = listOf(HintType.SHOW_REGION)
        )

        val result = mode().scoring.score(state)

        // Base score: perfect 2/2 -> (2/2 * 2) * 1.2 = 2.4, then the region hint's 0.25 cost (D37)
        // and the lost perfect bonus (HintCosts.applyTo): unbonused 2.0, minus 0.25 = 1.75.
        assertEquals(1.75, result.score, 1e-9)
        assertFalse("any hint drops the perfect bonus", result.perfectBonus)
    }

    @Test
    fun `scoring with no hints keeps the base score untouched`() {
        val quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(france, germany))
        val state = QuizState(quiz = quiz, answeredCountries = setOf(france.code, germany.code))

        val result = mode().scoring.score(state)

        assertEquals(2.4, result.score, 1e-9)
        assertTrue(result.perfectBonus)
    }

    @Test
    fun `choiceGenerator is SilhouettesChoiceGenerator, not the classic flag-fallback one`() {
        assertEquals(SilhouettesChoiceGenerator, mode().choiceGenerator)
    }
}
