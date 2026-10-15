package com.geoquiz.app.domain.mode.tapmap

import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tap the map's Easy tier (task 5.2, D37): 4 names for the highlighted target. */
class TapMapChoiceGeneratorTest {

    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")
    private val austria = country("AUT", "Austria", "Vienna")
    private val italy = country("ITA", "Italy", "Rome")
    private val spain = country("ESP", "Spain", "Madrid")
    private val japan = country("JPN", "Japan", "Tokyo", region = "Asia")
    private val china = country("CHN", "China", "Beijing", region = "Asia")

    private val europe = listOf(france, germany, austria, italy, spain)
    private val all: List<Country> = europe + listOf(japan, china)
    private val category = QuizCategory.MapRegion("europe")
    private val seeds = 0 until 50

    @Test
    fun `question has 4 distinct options, the target exactly once`() {
        for (seed in seeds) {
            val question = TapMapChoiceGenerator.question(france, europe, all, category, Random(seed))

            assertEquals(france.code, question.targetCode)
            assertEquals(CHOICE_OPTION_COUNT, question.options.size)
            assertEquals(1, question.options.count { it.code == france.code })
            assertEquals(CHOICE_OPTION_COUNT, question.options.map { it.code }.toSet().size)
            assertEquals("France", question.correctOption.label)
        }
    }

    @Test
    fun `distractors come from the quiz's own region set, never the target`() {
        val setCodes = europe.map { it.code }.toSet()
        for (seed in seeds) {
            val question = TapMapChoiceGenerator.question(france, europe, all, category, Random(seed))
            val distractorCodes = question.options.map { it.code }.filter { it != france.code }

            assertTrue("distractors are quiz-set members", distractorCodes.all { it in setCodes })
        }
    }

    @Test
    fun `tops up from every country when the quiz set is too small`() {
        val small = listOf(japan)
        for (seed in seeds) {
            val question = TapMapChoiceGenerator.question(japan, small, all, category, Random(seed))

            assertEquals(CHOICE_OPTION_COUNT, question.options.size)
            assertEquals(CHOICE_OPTION_COUNT, question.options.map { it.code }.toSet().size)
            // Only one other country shares Japan's region in `all`.
            assertTrue(question.options.any { it.code == "CHN" })
        }
    }

    @Test
    fun `a one-country quiz set still returns options from elsewhere`() {
        val question = TapMapChoiceGenerator.question(japan, listOf(japan), all, category, Random(1))

        assertEquals(japan.code, question.targetCode)
        assertTrue(question.options.size in 2..CHOICE_OPTION_COUNT)
        assertEquals(1, question.options.count { it.code == japan.code })
    }
}
