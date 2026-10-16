package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FlagSpeedChoiceGenerator]: distractors are always exactly the target's lookalike set-mates,
 * never generic same-region countries (task 7.3, D52).
 */
class FlagSpeedChoiceGeneratorTest {

    private val chad = country("TCD", "Chad", "N'Djamena", region = "Africa")
    private val romania = country("ROU", "Romania", "Bucharest", region = "Europe")
    private val andorra = country("AND", "Andorra", "Andorra la Vella", region = "Europe")
    private val moldova = country("MDA", "Moldova", "Chisinau", region = "Europe")
    private val unrelated = country("JPN", "Japan", "Tokyo", region = "Asia")

    private val category = QuizCategory.AllCountries
    private val seeds = 0 until 50

    @Test
    fun `options are exactly the target plus its lookalike set's other members`() {
        val setMates = listOf(romania, andorra, moldova)
        for (seed in seeds) {
            val question = FlagSpeedChoiceGenerator.question(chad, setMates, listOf(unrelated), category, Random(seed))

            assertEquals("TCD", question.targetCode)
            assertEquals(4, question.options.size)
            assertEquals(setOf("TCD", "ROU", "AND", "MDA"), question.options.map { it.code }.toSet())
            assertTrue("unrelated countries never appear as options", question.options.none { it.code == "JPN" })
        }
    }

    @Test
    fun `the prompt shows the target's flag and never names it`() {
        val question = FlagSpeedChoiceGenerator.question(chad, listOf(romania), emptyList(), category, Random(0))
        assertEquals(ChoicePrompt.FlagOf("TCD"), question.prompt)
    }

    @Test
    fun `a two-member set gives a two-option question, never padded with unrelated countries`() {
        // Indonesia/Poland-style pair: only one set-mate exists.
        val question = FlagSpeedChoiceGenerator.question(chad, listOf(romania), listOf(unrelated, andorra, moldova), category, Random(5))

        assertEquals(2, question.options.size)
        assertEquals(setOf("TCD", "ROU"), question.options.map { it.code }.toSet())
    }

    @Test
    fun `the target option is present exactly once and labelled by name`() {
        for (seed in seeds) {
            val question = FlagSpeedChoiceGenerator.question(chad, listOf(romania, andorra), emptyList(), category, Random(seed))
            assertEquals(1, question.options.count { it.code == "TCD" })
            assertEquals("Chad", question.correctOption.label)
        }
    }

    @Test
    fun `the target is never duplicated even if it appears in the passed set-mates list`() {
        // Defensive: callers should never pass the target in quizSet, but the generator must not
        // show it twice if they accidentally do.
        val question = FlagSpeedChoiceGenerator.question(chad, listOf(romania, chad), emptyList(), category, Random(1))
        assertEquals(1, question.options.count { it.code == "TCD" })
        assertEquals(2, question.options.size)
    }
}
