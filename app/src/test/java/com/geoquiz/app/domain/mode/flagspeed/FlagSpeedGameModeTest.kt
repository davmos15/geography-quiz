package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FlagSpeedGameMode]'s own wiring (task 7.3, D52); scoring correctness lives in [FlagSpeedScoringTest]. */
class FlagSpeedGameModeTest {

    private val lookalikeRepository = mockk<FlagLookalikeRepository>()
    private val countryRepository = mockk<CountryRepository>()
    private val chad = country("TCD", "Chad", "N'Djamena")
    private val romania = country("ROU", "Romania", "Bucharest")

    private fun mode() = FlagSpeedGameMode(FlagSpeedQuestionGenerator(lookalikeRepository, countryRepository))

    @Test
    fun `spec is multiple choice at every tier, with no hints, behind its own feature flag`() {
        val spec = FlagSpeedGameMode.SPEC
        assertEquals(com.geoquiz.app.domain.mode.AnswerType.MULTIPLE_CHOICE, spec.answerType)
        assertEquals(Difficulty.entries.toSet(), spec.supportedDifficulties)
        assertEquals(emptySet<com.geoquiz.app.domain.mode.HintType>(), spec.hintTypes)
        assertEquals(FeatureFlag.FLAG_SPEED_ROUND, spec.featureFlag)
    }

    @Test
    fun `the round length constant is 60 seconds`() {
        assertEquals(60, FlagSpeedGameMode.ROUND_DURATION_SECONDS)
    }

    @Test
    fun `supportsPractice is false - the route only ever takes a difficulty, no category`() {
        // Task 7.3 fix round, item 2: ModeRoutes.start/practise for this mode's id drop the
        // practice category entirely, so "Practise the ones you missed" must not be offered.
        assertFalse(FlagSpeedGameMode.SPEC.supportsPractice)
    }

    @Test
    fun `choiceGenerator is FlagSpeedChoiceGenerator`() {
        assertTrue(mode().choiceGenerator === FlagSpeedChoiceGenerator)
    }

    @Test
    fun `validator never used - always returns None`() = runBlocking {
        val state = QuizState(quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(chad)))
        val result = mode().validator.validate("anything", state, allowFuzzy = true)
        assertEquals(AnswerResult.None, result)
    }

    @Test
    fun `generator returns every country in a non-near-identical lookalike set`() = runBlocking {
        val sets = listOf(
            FlagLookalikeSet("a", listOf("TCD", "ROU"), tier = 3, visibility = FlagLookalikeVisibility.CLEAR, explanation = "e", cues = emptyMap()),
            FlagLookalikeSet("b", listOf("TCD", "XXX"), tier = 3, visibility = FlagLookalikeVisibility.NEAR_IDENTICAL, explanation = "e", cues = emptyMap())
        )
        coEvery { lookalikeRepository.sets() } returns sets
        coEvery { countryRepository.getCountriesByCodes(listOf("TCD", "ROU")) } returns listOf(chad, romania)

        val items = mode().generator.items(QuizCategory.AllCountries)

        assertEquals(listOf(chad, romania), items)
    }

    @Test
    fun `scoring delegates to FlagSpeedScoring`() {
        val state = QuizState(
            quiz = Quiz(category = QuizCategory.AllCountries, countries = listOf(chad, romania)),
            answeredCountries = setOf("TCD", "ROU"),
            incorrectGuesses = 0,
            currentInput = "2",
            isComplete = true
        )
        val expected = FlagSpeedScoring.score(state)
        assertEquals(expected, mode().scoring.score(state))
    }
}
