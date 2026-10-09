package com.geoquiz.app.domain.model

import com.geoquiz.app.domain.mode.ChoiceQuestion

/**
 * A running quiz.
 *
 * The `choice*`, [remainingOrder] and [missedCountries] fields are used only at
 * [Difficulty.EASY] (multiple choice); they stay empty at Normal and Hard.
 */
data class QuizState(
    val quiz: Quiz,
    val answeredCountries: Set<String> = emptySet(),
    val currentInput: String = "",
    val timeElapsedSeconds: Int = 0,
    val isComplete: Boolean = false,
    val isPaused: Boolean = false,
    val lastAnswerResult: AnswerResult = AnswerResult.None,
    val incorrectGuesses: Int = 0,
    val incorrectGuessStrings: List<String> = emptyList(),
    /** Easy: the question on screen, or null between questions and once all have been asked. */
    val choice: ChoiceQuestion? = null,
    /** Easy: the player's pick on [choice], shown briefly before the next question. */
    val choiceFeedback: ChoiceFeedback? = null,
    /** Easy: codes still to be asked after [choice], in the order they will be asked. */
    val remainingOrder: List<String> = emptyList(),
    /** Easy: codes asked and answered wrongly. They are not asked again. */
    val missedCountries: Set<String> = emptySet(),
    /**
     * Normal and Hard (typed): codes of the latest correct answers, newest first, at most
     * [RECENT_CORRECT_MAX]. Shown above the answer field; stays empty at Easy.
     */
    val recentCorrect: List<String> = emptyList()
) {
    val progress: Float
        get() = if (quiz.countries.isEmpty()) 0f
        else answeredCountries.size.toFloat() / quiz.countries.size

    val remainingCount: Int
        get() = quiz.countries.size - answeredCountries.size

    val timerRemaining: Int?
        get() = quiz.timerSeconds?.let { (it - timeElapsedSeconds).coerceAtLeast(0) }

    companion object {
        /** How many recent correct answers [recentCorrect] keeps. */
        const val RECENT_CORRECT_MAX = 3

        /** [recent] with [code] added as the newest, without duplicates, capped at [RECENT_CORRECT_MAX]. */
        fun pushRecentCorrect(recent: List<String>, code: String): List<String> =
            (listOf(code) + recent.filter { it != code }).take(RECENT_CORRECT_MAX)
    }
}

/** Easy: the option the player picked on the current question. */
data class ChoiceFeedback(val selectedCode: String, val isCorrect: Boolean)

sealed class AnswerResult {
    data object None : AnswerResult()

    /** Accepted. [viaTypo] is true when the answer was one small typo away from an alias. */
    data class Correct(val countryName: String, val viaTypo: Boolean = false) : AnswerResult()
    data object AlreadyAnswered : AnswerResult()
    data object Incorrect : AnswerResult()

    /**
     * Close to a valid answer but not accepted: a typo in hard mode, or a typo that is
     * equally close to more than one country. No strike; the input is kept for editing.
     */
    data object NearMiss : AnswerResult()
}
