package com.geoquiz.app.domain.feedback

/**
 * Tracks a "combo" of fast, consecutive correct answers in a classic quiz (task 8.5), purely as
 * on-screen feel alongside the existing haptic/pulse-shake feedback (3.3): it never changes
 * scoring. [com.geoquiz.app.domain.usecase.CalculateScoreUseCase] is untouched and knows nothing
 * about combos; there is no score bonus for one anywhere.
 *
 * Pure and platform-free (no Android imports, no [com.geoquiz.app.domain.model.QuizState]
 * coupling) so it is trivially unit-testable with fake timestamps: callers pass the elapsed time
 * of each answer from whatever clock they already use (`QuizViewModel` uses the same
 * [com.geoquiz.app.domain.time.MonotonicClock] as its quiz timer).
 *
 * A correct answer within [FAST_ANSWER_WINDOW_MILLIS] of the previous correct answer extends the
 * combo by one; a correct answer slower than that starts a fresh combo of 1 (it is still a
 * correct answer, just not a "fast" one); any wrong answer resets the combo to 0. Near misses and
 * already-answered results cost nothing elsewhere in the app (3.3) and do not call either method
 * here, so they neither extend nor reset a combo.
 */
class ComboTracker {

    /** Consecutive fast correct answers so far. 0 before the first answer or after a reset. */
    var count: Int = 0
        private set

    private var lastCorrectAtMillis: Long? = null

    /** Call on a correct answer at [nowMillis] (the same clock for every call). Returns [count]. */
    fun onCorrect(nowMillis: Long): Int {
        val previous = lastCorrectAtMillis
        count = if (previous != null && nowMillis - previous <= FAST_ANSWER_WINDOW_MILLIS) {
            count + 1
        } else {
            1
        }
        lastCorrectAtMillis = nowMillis
        return count
    }

    /** Call on a wrong answer: ends the combo. */
    fun onIncorrect() {
        count = 0
        lastCorrectAtMillis = null
    }

    companion object {
        /**
         * A correct answer this soon (in milliseconds) after the previous one keeps the combo
         * going. 5 seconds is long enough for a confident player's normal typing pace on an easy
         * item, but short enough that pausing to think (or looking something up) breaks the
         * streak, so the counter reads as "fast answers in a row", not just "answers in a row".
         */
        const val FAST_ANSWER_WINDOW_MILLIS = 5_000L

        /**
         * [count] is only meant to be shown in the UI from this many fast, consecutive correct
         * answers: a single correct answer is not a "streak" worth calling out.
         */
        const val VISIBLE_THRESHOLD = 2
    }
}
