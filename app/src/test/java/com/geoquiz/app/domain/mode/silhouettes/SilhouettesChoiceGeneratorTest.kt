package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SilhouettesChoiceGeneratorTest {

    private val france = country("FRA", "France", "Paris")
    private val germany = country("DEU", "Germany", "Berlin")
    private val austria = country("AUT", "Austria", "Vienna")
    private val italy = country("ITA", "Italy", "Rome")
    private val japan = country("JPN", "Japan", "Tokyo", region = "Asia")
    private val china = country("CHN", "China", "Beijing", region = "Asia")

    private val all: List<Country> = listOf(france, germany, austria, italy, japan, china)
    private val category = QuizCategory.ByRegion("Europe")

    @Test
    fun `four options, the target once, distinct codes and labels, over many seeds`() {
        for (seed in 0 until 30) {
            val question = SilhouettesChoiceGenerator.question(
                target = france,
                quizSet = listOf(france, germany, austria),
                allCountries = all,
                category = category,
                random = Random(seed)
            )

            assertEquals(france.code, question.targetCode)
            assertEquals(CHOICE_OPTION_COUNT, question.options.size)
            assertEquals(1, question.options.count { it.code == france.code })
            assertEquals(CHOICE_OPTION_COUNT, question.options.map { it.code }.toSet().size)
            assertEquals(CHOICE_OPTION_COUNT, question.options.map { it.label.lowercase() }.toSet().size)
            assertEquals("France", question.correctOption.label)
        }
    }

    @Test
    fun `distractors come from the target's region first, from the whole game (not just the round)`() {
        // Only Germany and Austria share France's region among "all"; Italy is the third European
        // outsider to the round's own set, but distractors are drawn from every country, not the
        // round, so Japan/China (Asia) can appear once the same-region pool runs out.
        val question = SilhouettesChoiceGenerator.question(
            target = france,
            quizSet = listOf(france),
            allCountries = all,
            category = category,
            random = Random(5)
        )
        val distractorCodes = question.options.map { it.code }.filter { it != france.code }.toSet()
        assertTrue("Germany or Austria (same region) is preferred", distractorCodes.any { it in setOf("DEU", "AUT") })
    }

    @Test
    fun `the prompt is built but is in-set text, not a flag (the outline is the real prompt)`() {
        val question = SilhouettesChoiceGenerator.question(france, listOf(france), all, category, Random(1))
        assertEquals(ChoicePrompt.InSet("Europe"), question.prompt)
    }
}
