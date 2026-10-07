package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Difficulty

/** One finished quiz of a category, as far as mastery stars care. */
data class MasteryAttempt(
    val difficulty: Difficulty,
    val correct: Int,
    val total: Int
)

/**
 * Mastery stars per (mode, category), from the player's history (decision D17):
 *
 * - 1 star: any finished quiz (any tier, give-ups included) with at least 50% correct.
 * - 2 stars: at least 80% correct at Normal or Hard.
 * - 3 stars: 100% correct at Hard.
 *
 * Rows recorded before tiers existed count as Normal. Percentages are compared in whole
 * numbers, so 4 of 5 is exactly 80%.
 */
object MasteryStars {

    const val MAX = 3

    /** Stars (0 to [MAX]) earned by the best of [attempts]. Attempts with no questions are ignored. */
    fun calculate(attempts: Iterable<MasteryAttempt>): Int {
        var stars = 0
        for (attempt in attempts) {
            if (attempt.total <= 0) continue
            val earned = when {
                attempt.difficulty == Difficulty.HARD && attempt.correct >= attempt.total -> 3
                attempt.difficulty != Difficulty.EASY && atLeastPercent(attempt, 80) -> 2
                atLeastPercent(attempt, 50) -> 1
                else -> 0
            }
            if (earned > stars) stars = earned
            if (stars == MAX) break
        }
        return stars
    }

    private fun atLeastPercent(attempt: MasteryAttempt, percent: Int): Boolean =
        attempt.correct.toLong() * 100 >= attempt.total.toLong() * percent
}
