package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState

/**
 * Border hop's scoring (task 6.1, D44: "scoring based on hops used vs the optimal path length").
 * [com.geoquiz.app.ui.borderhop.BorderHopGameViewModel] packs a finished round into a plain
 * [QuizState] so [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] can score and record it like
 * any other mode (D35, D37), reusing its fields with Border hop's own meaning (no other code reads
 * one of this mode's [QuizState]s):
 *
 * - [QuizState.quiz]'s countries are the path actually walked, start to wherever the round ended.
 * - [QuizState.answeredCountries] always holds every code in that path (never a subset), so
 *   [com.geoquiz.app.domain.model.CompletedQuiz.missedCodes] is always empty and "Practise the
 *   ones you missed" never offers a (meaningless) partial-path practice round.
 * - [QuizState.currentInput] is [FLAG_REACHED] or [FLAG_GAVE_UP]: whether the path's last country
 *   is the round's target.
 * - [QuizState.incorrectGuesses] is the extra hops taken beyond the optimal (par) length once the
 *   target is reached; while given up it just holds the hop count so far (not used by scoring).
 *
 * A completed round scores like the classic percentage formula, but on par versus hops used
 * instead of correct versus total: `correct = par`, `total = hops used` (so the percentage is the
 * round's efficiency, 100% only when no hop was wasted), with the same 1.2 perfect bonus when
 * [QuizState.incorrectGuesses] (extra hops) is zero. Giving up scores zero.
 */
object BorderHopScoring {
    const val FLAG_REACHED = "reached"
    const val FLAG_GAVE_UP = "gave_up"

    /** Same multiplier as `CalculateScoreUseCase`. */
    private const val PERFECT_MULTIPLIER = 1.2

    fun score(state: QuizState): QuizResult {
        val hopsUsed = (state.quiz.countries.size - 1).coerceAtLeast(0)
        val reached = state.currentInput == FLAG_REACHED
        if (!reached || hopsUsed == 0) {
            return QuizResult(
                category = state.quiz.category,
                totalCountries = hopsUsed,
                correctAnswers = 0,
                timeElapsedSeconds = state.timeElapsedSeconds,
                score = 0.0,
                perfectBonus = false,
                incorrectGuesses = hopsUsed
            )
        }
        val extraHops = state.incorrectGuesses.coerceAtLeast(0)
        val parHops = (hopsUsed - extraHops).coerceAtLeast(1)
        val percentage = parHops.toDouble() / hopsUsed
        val baseScore = percentage * parHops
        val isPerfect = extraHops == 0
        val finalScore = baseScore * (if (isPerfect) PERFECT_MULTIPLIER else 1.0)
        return QuizResult(
            category = state.quiz.category,
            totalCountries = hopsUsed,
            correctAnswers = parHops,
            timeElapsedSeconds = state.timeElapsedSeconds,
            score = finalScore,
            perfectBonus = isPerfect,
            incorrectGuesses = extraHops
        )
    }
}
