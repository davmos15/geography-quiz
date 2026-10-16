package com.geoquiz.app.domain.mode

import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizResult

/**
 * The hints economy (task 8.4): per-hint point costs plus a per-difficulty cap on how many hints
 * one round may use, read by every mode that has hints (Country silhouettes, Tap the map Find,
 * Border hop). Hints cost points, never money. Replaces the D37 placeholder; the costs below are
 * unchanged from that placeholder (D37/D44), only [maxHintsPerRound] is new.
 */
object HintCosts {

    /** Points taken off the final score for one use of [type]. */
    fun cost(type: HintType): Double = when (type) {
        HintType.SHOW_REGION -> 0.25
        HintType.FIRST_LETTER -> 0.5
        HintType.SHOW_NEIGHBOURS -> 0.5
        HintType.FLASH_REGION -> 0.25
        // Hands the player a working move, same tier as SHOW_NEIGHBOURS/FIRST_LETTER (D44).
        HintType.SHOW_VALID_HOP -> 0.5
    }

    /**
     * How many hints one round may use at [difficulty], whatever the mode (task 8.4): Easy 0
     * (Country silhouettes and Tap the map already show no hints at Easy, D39/D41 - their four
     * multiple-choice names are the help instead - so this makes that fact enforceable at the one
     * mode, Border hop, whose Easy tier has no multiple-choice variant and so previously had no
     * cap on Easy hints at all), Normal 2, Hard 3.
     */
    fun maxHintsPerRound(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 0
        Difficulty.NORMAL -> 2
        Difficulty.HARD -> 3
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
