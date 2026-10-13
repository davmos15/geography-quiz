package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class HintCostsTest {

    private fun result(score: Double, perfect: Boolean) = QuizResult(
        category = QuizCategory.AllCountries,
        totalCountries = 10,
        correctAnswers = if (perfect) 10 else 8,
        timeElapsedSeconds = 60,
        score = score,
        perfectBonus = perfect,
        incorrectGuesses = 0
    )

    @Test
    fun `no hints leaves the result unchanged`() {
        val r = result(6.4, perfect = false)
        assertSame(r, HintCosts.applyTo(r, emptyList()))
    }

    @Test
    fun `hint costs come off the score`() {
        val r = HintCosts.applyTo(result(6.4, perfect = false), listOf(HintType.SHOW_REGION, HintType.FIRST_LETTER))
        assertEquals(5.65, r.score, 1e-9)
    }

    @Test
    fun `any hint removes the perfect bonus`() {
        val r = HintCosts.applyTo(result(12.0, perfect = true), listOf(HintType.FLASH_REGION))
        assertFalse(r.perfectBonus)
        assertEquals(9.75, r.score, 1e-9)
    }

    @Test
    fun `score never goes below zero`() {
        val r = HintCosts.applyTo(result(0.1, perfect = false), listOf(HintType.SHOW_NEIGHBOURS))
        assertEquals(0.0, r.score, 0.0)
    }
}
