package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BorderHopGameMode]'s own wiring: candidate generator, category offers and scoring (hint costs
 * on top of [BorderHopScoring]). BFS correctness lives in `BorderGraphTest`.
 */
class BorderHopGameModeTest {

    private val getCountriesForQuiz = mockk<GetCountriesForQuizUseCase>()
    private val validateAnswer = mockk<ValidateAnswerUseCase>()
    private val france = country("FRA", "France", "Paris")
    private val spain = country("ESP", "Spain", "Madrid")

    private fun mode() = BorderHopGameMode(BorderHopQuestionGenerator(getCountriesForQuiz), validateAnswer)

    @Test
    fun `generator delegates to GetCountriesForQuizUseCase`() = runBlocking {
        val pool = listOf(france, spain)
        coEvery { getCountriesForQuiz(QuizCategory.AllCountries) } returns pool

        assertEquals(pool, mode().generator.items(QuizCategory.AllCountries))
    }

    @Test
    fun `offers only All countries, region and subregion categories`() {
        val m = mode()
        assertTrue(m.offers(QuizCategory.AllCountries))
        assertTrue(m.offers(QuizCategory.ByRegion("Europe")))
        assertTrue(m.offers(QuizCategory.BySubregion("Southern Europe")))

        assertFalse(m.offers(QuizCategory.StartingWithLetter('A')))
        assertFalse(m.offers(QuizCategory.IslandCountries))
        assertFalse(m.offers(QuizCategory.FlagSingleColor("red")))
        assertFalse(m.offers(QuizCategory.Practice(listOf("FRA", "ESP"))))
    }

    @Test
    fun `spec lists the SHOW_VALID_HOP hint, every difficulty and the Border hop feature flag`() {
        val spec = BorderHopGameMode.SPEC
        assertEquals(setOf(HintType.SHOW_VALID_HOP), spec.hintTypes)
        assertEquals(Difficulty.entries.toSet(), spec.supportedDifficulties)
        assertEquals(FeatureFlag.BORDER_HOP, spec.featureFlag)
    }

    @Test
    fun `hopRangeFor matches D44's tiers`() {
        assertEquals(2..3, BorderHopGameMode.hopRangeFor(Difficulty.EASY))
        assertEquals(4..5, BorderHopGameMode.hopRangeFor(Difficulty.NORMAL))
        assertEquals(6..21, BorderHopGameMode.hopRangeFor(Difficulty.HARD))
    }

    @Test
    fun `scoring applies this round's hint costs on top of BorderHopScoring`() {
        val path = listOf(france, spain)
        val state = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = path),
            answeredCountries = setOf("FRA", "ESP"),
            incorrectGuesses = 0,
            currentInput = BorderHopScoring.FLAG_REACHED,
            hintsUsed = listOf(HintType.SHOW_VALID_HOP),
            isComplete = true
        )

        val result = mode().scoring.score(state)

        // Base: par 1, used 1 -> perfect, 1 * 1.2 = 1.2; the hint drops the bonus and costs 0.5.
        assertEquals(1.0 - 0.5, result.score, 1e-9)
        assertFalse(result.perfectBonus)
    }

    @Test
    fun `choiceGenerator is never called (no multiple-choice tier)`() {
        assertThrows(IllegalStateException::class.java) {
            mode().choiceGenerator.question(
                target = france,
                quizSet = listOf(france),
                allCountries = listOf(france, spain),
                category = QuizCategory.AllCountries,
                random = kotlin.random.Random(1)
            )
        }
    }
}
