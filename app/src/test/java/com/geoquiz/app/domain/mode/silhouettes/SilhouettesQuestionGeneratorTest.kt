package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SilhouettesQuestionGeneratorTest {

    private val twenty: List<Country> = (1..20).map { country("C$it", "Country $it", "Capital $it") }
    private val five: List<Country> = (1..5).map { country("F$it", "Few $it", "Capital $it") }

    private fun poolOf(countries: List<Country>): QuestionGenerator = QuestionGenerator { countries }

    @Test
    fun `a round is 15 random countries from a larger pool`() = runBlocking {
        val generator = SilhouettesQuestionGenerator(poolOf(twenty), QuizRandom(Random(1)))

        val round = generator.items(QuizCategory.AllCountries)

        assertEquals(15, round.size)
        assertEquals("every item is a distinct member of the pool", 15, round.map { it.code }.toSet().size)
        assertTrue(round.all { it in twenty })
    }

    @Test
    fun `fewer than 15 when the filter has fewer countries`() = runBlocking {
        val generator = SilhouettesQuestionGenerator(poolOf(five), QuizRandom(Random(1)))

        val round = generator.items(QuizCategory.ByRegion("Nowhere"))

        assertEquals(five.toSet(), round.toSet())
    }

    @Test
    fun `different seeds give different rounds (over many seeds, at least one differs)`() = runBlocking {
        val rounds = (0 until 20).map { seed ->
            SilhouettesQuestionGenerator(poolOf(twenty), QuizRandom(Random(seed))).items(QuizCategory.AllCountries)
        }
        assertTrue("at least two of 20 seeded rounds differ in order or members", rounds.toSet().size > 1)
    }

    @Test
    fun `the same seed gives the same round (deterministic)`() = runBlocking {
        val first = SilhouettesQuestionGenerator(poolOf(twenty), QuizRandom(Random(42))).items(QuizCategory.AllCountries)
        val second = SilhouettesQuestionGenerator(poolOf(twenty), QuizRandom(Random(42))).items(QuizCategory.AllCountries)

        assertEquals(first.map { it.code }, second.map { it.code })
    }

    @Test
    fun `a practice set is exactly its codes, in order, never shuffled or capped`() = runBlocking {
        // The pool stands in for GetCountriesForQuizUseCase's practiceItems ordering.
        val practiceOrder = listOf(twenty[4], twenty[0], twenty[9])
        val generator = SilhouettesQuestionGenerator(poolOf(practiceOrder), QuizRandom(Random(7)))

        val round = generator.items(QuizCategory.Practice(practiceOrder.map { it.code }))

        assertEquals(practiceOrder.map { it.code }, round.map { it.code })
    }

    // ---- task P10-E: excluded (unrecognisable) silhouettes are never asked about ----

    @Test
    fun `an excluded code is never yielded, even across many seeds`() = runBlocking {
        val excludedCode = SilhouetteLegibility.EXCLUDED_CCA3.first()
        val pool = twenty + country(excludedCode, "Excluded", "Capital")
        repeat(20) { seed ->
            val generator = SilhouettesQuestionGenerator(poolOf(pool), QuizRandom(Random(seed)))
            val round = generator.items(QuizCategory.AllCountries)
            assertTrue("seed $seed should never include $excludedCode", round.none { it.code == excludedCode })
        }
    }

    @Test
    fun `a practice set made entirely of excluded codes resolves to no countries`() = runBlocking {
        val excludedCode = SilhouetteLegibility.EXCLUDED_CCA3.first()
        val excludedCountry = country(excludedCode, "Excluded", "Capital")
        val generator = SilhouettesQuestionGenerator(poolOf(listOf(excludedCountry)), QuizRandom(Random(1)))

        val round = generator.items(QuizCategory.Practice(listOf(excludedCode)))

        assertTrue(round.isEmpty())
    }

    @Test
    fun `a practice set mixing excluded and valid codes keeps only the valid ones`() = runBlocking {
        val excludedCode = SilhouetteLegibility.EXCLUDED_CCA3.first()
        val excludedCountry = country(excludedCode, "Excluded", "Capital")
        val valid = twenty[0]
        val generator = SilhouettesQuestionGenerator(poolOf(listOf(excludedCountry, valid)), QuizRandom(Random(1)))

        val round = generator.items(QuizCategory.Practice(listOf(excludedCode, valid.code)))

        assertEquals(listOf(valid.code), round.map { it.code })
    }
}
