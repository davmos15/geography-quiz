package com.geoquiz.app.domain.mode.flagspeed

import com.geoquiz.app.domain.model.QuizResult
import com.geoquiz.app.domain.model.QuizState

/**
 * Flag speed round's scoring (task 7.3, D52). Like Border hop ([com.geoquiz.app.domain.mode.borderhop.BorderHopScoring],
 * D37's precedent), this mode's own ViewModel packs a finished round into a plain [QuizState] so
 * [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] can score and record it like any other
 * mode, reusing its fields with this mode's own meaning (no other code reads one of this mode's
 * [QuizState]s):
 *
 * - [QuizState.quiz]'s countries are every target **asked and answered** during the round, in
 *   the order asked, **repeats allowed**: 60 seconds asks far more questions than the data
 *   file's 24 lookalike sets can give without repeats, so unlike every other mode this list is
 *   deliberately not deduplicated. A question still open when time runs out (or Give up is
 *   pressed) is never added here: it was shown but never answered, so it is neither a correct
 *   nor an incorrect pick.
 * - [QuizState.answeredCountries] is the *set* of codes answered correctly **at least once**
 *   during the round, not "every occurrence answered correctly" (a [Set] cannot distinguish one
 *   occurrence of a repeated code from another). This is a deliberate choice, not an oversight:
 *   [com.geoquiz.app.domain.model.CompletedQuiz.missedCodes] (used by "Practise the ones you
 *   missed") already does `countryCodes.filter { it !in answered }.distinct()`, so a code the
 *   player got right even once is correctly treated as "not missed" - which is the sensible
 *   reading of "missed" for a speed round anyway. [CompletedQuiz.answeredCodes][com.geoquiz.app.domain.model.CompletedQuiz.answeredCodes]
 *   (used by Answer review) is filtered the same way, so every occurrence of a code that was
 *   ever correct shows as answered there too; Answer review itself only ever shows one row per
 *   country ([com.geoquiz.app.domain.repository.CountryRepository.getCountriesByCodes]
 *   de-duplicates), so this never produces a visibly duplicated or contradictory row. The actual
 *   correct-answer *count* used for scoring below does not rely on this set at all, so it is
 *   never undercounted by a repeat.
 * - [QuizState.incorrectGuesses] is the total number of wrong picks in the round (a plain count,
 *   not a set: every wrong pick counts, including a repeat of a code already picked wrongly).
 * - [QuizState.currentInput] holds the round's longest run of consecutive correct answers
 *   (the "combo"), as a plain base-10 string. None of [QuizState]'s numeric fields are free for
 *   this mode's own use ([timeElapsedSeconds] is set by `CompleteQuizUseCase` itself, and
 *   [incorrectGuesses] already holds the wrong-pick count above), so - exactly like Border hop
 *   repurposing this same field for its reached/gave-up flag - the combo is stashed in
 *   [currentInput] with a documented, parseable convention instead. A missing or malformed value
 *   reads as zero.
 *
 * Every question asked and answered is either correct or incorrect (there is no near miss or
 * "already answered" in a multiple-choice round), so the correct count is always
 * `asked - incorrectGuesses`; no separate tally is needed.
 *
 * Score formula: `base = (correct / asked) * correct` (same shape as `CalculateScoreUseCase`'s
 * classic formula), plus a combo bonus of `0.5` points for every consecutive correct answer
 * beyond the first in the round's longest streak (`max(longestStreak - 1, 0) * 0.5`), so a streak
 * of 1 (an isolated correct answer, no combo) adds nothing. The total is then multiplied by 1.2,
 * the same perfect-round bonus every other mode uses, if the round had zero incorrect guesses.
 *
 * Worked example: 20 questions asked, 15 correct, 5 incorrect, a longest streak of 6:
 * `base = (15/20) * 15 = 11.25`, `comboBonus = (6 - 1) * 0.5 = 2.5`, not perfect (incorrect > 0),
 * so `score = 11.25 + 2.5 = 13.75`.
 *
 * Worked example, a perfect round: 10 asked, 10 correct, 0 incorrect, longest streak 10:
 * `base = (10/10) * 10 = 10`, `comboBonus = (10 - 1) * 0.5 = 4.5`, perfect, so
 * `score = (10 + 4.5) * 1.2 = 17.4`.
 *
 * Giving up before answering anything (`asked == 0`) scores zero.
 */
object FlagSpeedScoring {

    /** Points added per consecutive correct answer beyond the first in the longest streak. */
    private const val COMBO_BONUS_PER_EXTRA_STREAK = 0.5

    /** Same multiplier as `CalculateScoreUseCase` and `BorderHopScoring`. */
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
        val longestStreak = longestStreakOf(state)
        val comboBonus = (longestStreak - 1).coerceAtLeast(0) * COMBO_BONUS_PER_EXTRA_STREAK
        val isPerfect = incorrect == 0
        val finalScore = (baseScore + comboBonus) * (if (isPerfect) PERFECT_MULTIPLIER else 1.0)
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

    /** [QuizState.currentInput] parsed back to the longest streak it holds; malformed reads as 0. */
    private fun longestStreakOf(state: QuizState): Int = state.currentInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
}
