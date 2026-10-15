package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.QuizResult

/**
 * Placeholder hint costs (D37) until the Phase 8 hints economy replaces them. Every mode reads
 * its costs from here, so Phase 8 changes one place. Hints cost points, never money.
 */
object HintCosts {

    /** Points taken off the final score for one use of [type]. */
    fun cost(type: HintType): Double = when (type) {
        HintType.SHOW_REGION -> 0.25
        HintType.FIRST_LETTER -> 0.5
        HintType.SHOW_NEIGHBOURS -> 0.5
        HintType.FLASH_REGION -> 0.25
    }

    /** Total cost of [hintsUsed] (one entry per use). */
    fun total(hintsUsed: List<HintType>): Double = hintsUsed.sumOf { cost(it) }

    /**
     * [result] with the cost of [hintsUsed] taken off its score (never below zero). A quiz that
     * used any hint keeps its correct count but loses the perfect bonus.
     */
    fun applyTo(result: QuizResult, hintsUsed: List<HintType>): QuizResult {
        if (hintsUsed.isEmpty()) return result
        val unbonused = if (result.perfectBonus) result.score / PERFECT_MULTIPLIER else result.score
        return result.copy(
            score = (unbonused - total(hintsUsed)).coerceAtLeast(0.0),
            perfectBonus = false
        )
    }

    /** Same multiplier as `CalculateScoreUseCase`. */
    private const val PERFECT_MULTIPLIER = 1.2
}
