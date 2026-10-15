package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UsCaRegionsChoiceGeneratorTest {

    private fun region(code: String, name: String) = Country(code, name, name, "", "", name.length, "")

    private val quizSet = listOf(
        region("US-CA", "California"),
        region("US-NY", "New York"),
        region("US-TX", "Texas"),
        region("US-FL", "Florida"),
        region("US-WA", "Washington")
    )

    @Test
    fun `builds 4 options including the target, no duplicates`() {
        val target = quizSet[0]
        val question = UsCaRegionsChoiceGenerator.question(target, quizSet, quizSet, QuizCategory.AllCountries, Random(1))
        assertEquals(4, question.options.size)
        assertEquals(question.options.map { it.code }.distinct().size, question.options.size)
        assertTrue(question.options.any { it.code == target.code })
        assertEquals(target.code, question.targetCode)
    }

    @Test
    fun `distractors never include the target twice`() {
        val target = quizSet[1]
        repeat(20) { seed ->
            val question = UsCaRegionsChoiceGenerator.question(target, quizSet, quizSet, QuizCategory.AllCountries, Random(seed.toLong()))
            assertEquals(1, question.options.count { it.code == target.code })
        }
    }

    @Test
    fun `labels are the region names`() {
        val target = quizSet[2]
        val question = UsCaRegionsChoiceGenerator.question(target, quizSet, quizSet, QuizCategory.AllCountries, Random(7))
        val correctOption = question.correctOption
        assertEquals("Texas", correctOption.label)
    }
}
