package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Difficulty.EASY
import com.geoquiz.app.domain.model.Difficulty.HARD
import com.geoquiz.app.domain.model.Difficulty.NORMAL
import org.junit.Assert.assertEquals
import org.junit.Test

/** Decision D17: 1 star at 50% (any tier), 2 at 80% (Normal/Hard), 3 at 100% (Hard). */
class MasteryStarsTest {

    private fun attempt(difficulty: Difficulty, correct: Int, total: Int = 100) =
        MasteryAttempt(difficulty, correct, total)

    private fun stars(vararg attempts: MasteryAttempt) = MasteryStars.calculate(attempts.toList())

    @Test
    fun `no history means no stars`() {
        assertEquals(0, MasteryStars.calculate(emptyList()))
    }

    @Test
    fun `single attempt table`() {
        // difficulty, correct of 100, expected stars
        val table = listOf(
            Triple(EASY, 0, 0),
            Triple(EASY, 49, 0),
            Triple(EASY, 50, 1),
            Triple(EASY, 79, 1),
            Triple(EASY, 80, 1),
            Triple(EASY, 100, 1),
            Triple(NORMAL, 49, 0),
            Triple(NORMAL, 50, 1),
            Triple(NORMAL, 79, 1),
            Triple(NORMAL, 80, 2),
            Triple(NORMAL, 99, 2),
            Triple(NORMAL, 100, 2),
            Triple(HARD, 49, 0),
            Triple(HARD, 50, 1),
            Triple(HARD, 79, 1),
            Triple(HARD, 80, 2),
            Triple(HARD, 99, 2),
            Triple(HARD, 100, 3)
        )
        for ((difficulty, correct, expected) in table) {
            assertEquals("$difficulty $correct%", expected, stars(attempt(difficulty, correct)))
        }
    }

    @Test
    fun `boundaries are exact for small quizzes`() {
        assertEquals(1, stars(attempt(NORMAL, correct = 1, total = 2)))   // 50%
        assertEquals(0, stars(attempt(NORMAL, correct = 2, total = 5)))   // 40%
        assertEquals(2, stars(attempt(NORMAL, correct = 4, total = 5)))   // 80%
        assertEquals(1, stars(attempt(NORMAL, correct = 3, total = 4)))   // 75%
        assertEquals(1, stars(attempt(HARD, correct = 157, total = 197))) // 79.7%
        assertEquals(2, stars(attempt(HARD, correct = 158, total = 197))) // 80.2%
        assertEquals(3, stars(attempt(HARD, correct = 197, total = 197)))
    }

    @Test
    fun `the best attempt across tiers counts`() {
        assertEquals(
            2,
            stars(attempt(EASY, 100), attempt(NORMAL, 85), attempt(HARD, 60))
        )
        assertEquals(
            3,
            stars(attempt(NORMAL, 10), attempt(HARD, 100), attempt(EASY, 20))
        )
        assertEquals(
            1,
            stars(attempt(EASY, 100), attempt(NORMAL, 70), attempt(HARD, 79))
        )
    }

    @Test
    fun `a perfect normal quiz is two stars, not three`() {
        assertEquals(2, stars(attempt(NORMAL, 100)))
    }

    @Test
    fun `attempts with no questions are ignored`() {
        assertEquals(0, stars(attempt(HARD, correct = 0, total = 0)))
        assertEquals(1, stars(attempt(HARD, correct = 0, total = 0), attempt(EASY, correct = 5, total = 10)))
    }
}
