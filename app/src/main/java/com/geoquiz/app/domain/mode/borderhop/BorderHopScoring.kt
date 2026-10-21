package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState

/**
 * Border hop's scoring (D44: hops used against the shortest route; task P10-C adds wrong
 * guesses). [com.geoquiz.app.ui.borderhop.BorderHopGameViewModel] packs a finished round into a
 * plain [QuizState] so [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] can score and record
 * it like any other mode (D35, D37), reusing its fields with Border hop's own meaning (no other
 * code reads one of this mode's [QuizState]s):
 *
 * - [QuizState.quiz]'s countries are the route actually walked, start to wherever the round
 *   ended (undone hops are not in it).
 * - [QuizState.answeredCountries] always holds every code in that route (never a subset), so
 *   [com.geoquiz.app.domain.model.CompletedQuiz.missedCodes] is always empty and "Practise the
 *   ones you missed" never offers a (meaningless) partial-route practice round.
 * - [QuizState.incorrectGuessStrings] holds one entry (the country's name) per wrong guess: a
 *   named or picked country that does not border the end of the route. Its size is the wrong
 *   guess count.
 * - [QuizState.incorrectGuesses] is the extra hops taken beyond the shortest (par) route plus the
 *   wrong guesses, once the target is reached; when the round ended any other way it is the hops
 *   made so far plus the wrong guesses (shown on Results, not used by scoring).
 * - [QuizState.currentInput] is [FLAG_REACHED], [FLAG_GAVE_UP] or [FLAG_OUT_OF_GUESSES] (Hard's
 *   strike limit): only [FLAG_REACHED] scores.
 *
 * A completed round scores like the classic percentage formula, but on par against moves:
 * `correct = par`, `total = hops used + wrong guesses` (so the percentage is the round's
 * efficiency, 100% only when no hop was wasted and no guess was wrong), with the same 1.2 perfect
 * bonus only when hops used equal par and there were no wrong guesses. Any other ending scores
 * zero.
 */
object BorderHopScoring {
    const val FLAG_REACHED = "reached"
    const val FLAG_GAVE_UP = "gave_up"
    const val FLAG_OUT_OF_GUESSES = "out_of_guesses"

    /** Same multiplier as `CalculateScoreUseCase`. */
    private const val PERFECT_MULTIPLIER = 1.2

    fun score(state: QuizState): QuizResult {
        val hopsUsed = (state.quiz.countries.size - 1).coerceAtLeast(0)
        val wrongGuesses = state.incorrectGuessStrings.size
        val reached = state.currentInput == FLAG_REACHED
        if (!reached || hopsUsed == 0) {
            return QuizResult(
                category = state.quiz.category,
                totalCountries = hopsUsed + wrongGuesses,
                correctAnswers = 0,
                timeElapsedSeconds = state.timeElapsedSeconds,
                score = 0.0,
                perfectBonus = false,
                incorrectGuesses = hopsUsed + wrongGuesses
            )
        }
        val extraHops = (state.incorrectGuesses - wrongGuesses).coerceAtLeast(0)
        val parHops = (hopsUsed - extraHops).coerceAtLeast(1)
        val moves = hopsUsed + wrongGuesses
        val percentage = parHops.toDouble() / moves
        val baseScore = percentage * parHops
        val isPerfect = extraHops == 0 && wrongGuesses == 0
        val finalScore = baseScore * (if (isPerfect) PERFECT_MULTIPLIER else 1.0)
        return QuizResult(
            category = state.quiz.category,
            totalCountries = moves,
            correctAnswers = parHops,
            timeElapsedSeconds = state.timeElapsedSeconds,
            score = finalScore,
            perfectBonus = isPerfect,
            incorrectGuesses = extraHops + wrongGuesses
        )
    }

    /**
     * The [QuizState.incorrectGuesses] value for a round (see the class doc): extra hops plus
     * wrong guesses when [reached], else hops made plus wrong guesses.
     */
    fun incorrectGuessesFor(reached: Boolean, hopsUsed: Int, parHops: Int, wrongGuesses: Int): Int =
        if (reached) (hopsUsed - parHops).coerceAtLeast(0) + wrongGuesses else hopsUsed + wrongGuesses
}
