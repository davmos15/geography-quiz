package com.geoquiz.app.domain.mode.dailychallenge

import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState

/**
 * Daily challenge scoring (task 8.1). Like Border hop
 * ([com.geoquiz.app.domain.mode.borderhop.BorderHopScoring]) and Flag speed round
 * ([com.geoquiz.app.domain.mode.flagspeed.FlagSpeedScoring], D37's precedent), this mode's own
 * [com.geoquiz.app.ui.dailychallenge.DailyChallengeGameViewModel] packs a finished round into a
 * plain [QuizState] so [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] can score and record
 * it like any other mode, reusing its fields with this mode's own, simpler meaning (there is no
 * repeat-question or combo wrinkle here - every item gets exactly one attempt):
 *
 * - [QuizState.quiz]'s countries are the targets actually asked (i.e. given a final attempt)
 *   during the round, in the order asked. A question still on screen when the player gives up is
 *   never added here: it was shown but never answered.
 * - [QuizState.answeredCountries] is the set of targets answered correctly on that one attempt.
 * - [QuizState.incorrectGuesses] is the count of targets answered incorrectly on that one attempt.
 *   Since every asked target is either correct or incorrect (no retries, no "already answered"),
 *   `correct == asked - incorrectGuesses` always holds, exactly like
 *   [com.geoquiz.app.domain.mode.flagspeed.FlagSpeedScoring].
 *
 * Score formula: the same shape as the classic formula
 * ([com.geoquiz.app.domain.usecase.CalculateScoreUseCase]) and as [QuizState]'s two new-mode
 * precedents above - `base = (correct / asked) * correct`, times 1.2 if the round was perfect
 * (zero incorrect guesses).
 *
 * Worked example: 10 asked, 8 correct, 2 incorrect: `base = (8/10) * 8 = 6.4`, not perfect, so
 * `score = 6.4`.
 *
 * Worked example, a perfect round: 10 asked, 10 correct, 0 incorrect:
 * `base = (10/10) * 10 = 10`, perfect, so `score = 10 * 1.2 = 12.0`.
 *
 * Giving up before answering anything, or any other way the round ends with zero questions asked,
 * scores zero.
 */
object DailyChallengeScoring {

    /** Same multiplier as `CalculateScoreUseCase`, `BorderHopScoring` and `FlagSpeedScoring`. */
    private const val PERFECT_MULTIPLIER = 1.2

    fun score(state: QuizState): QuizResult {
        val asked = state.quiz.countries.size
        if (asked == 0) {
            return QuizResult(
                category = state.quiz.category,
                totalCountries = 0,
                correctAnswers = 0,
                timeElapsedSeconds = state.timeElapsedSeconds,
                score = 0.0,
                perfectBonus = false,
                incorrectGuesses = 0
            )
        }
        val incorrect = state.incorrectGuesses.coerceIn(0, asked)
        val correct = asked - incorrect
        val percentage = correct.toDouble() / asked
        val baseScore = percentage * correct
        val isPerfect = incorrect == 0
        val finalScore = baseScore * (if (isPerfect) PERFECT_MULTIPLIER else 1.0)
        return QuizResult(
            category = state.quiz.category,
            totalCountries = asked,
            correctAnswers = correct,
            timeElapsedSeconds = state.timeElapsedSeconds,
            score = finalScore,
            perfectBonus = isPerfect,
            incorrectGuesses = incorrect
        )
    }
}
