package com.geoquiz.app.ui.quiz.feedback

import com.geoquiz.app.domain.model.AnswerResult

/**
 * A one-off reaction to an answer (3.3), sent by `QuizViewModel.feedbackEvents` once per submit
 * or Easy pick. The quiz screen turns it into a haptic and a short animation. The lasting state
 * (the feedback line's text) stays in `QuizState`; these events are not replayed or restored.
 */
enum class AnswerFeedbackEvent {
    /** A correct typed answer or Easy pick. */
    CORRECT,

    /** A wrong typed answer (a strike at Hard) or a wrong Easy pick. */
    INCORRECT,

    /** Close to an answer but not accepted; never a strike. */
    NEAR_MISS,

    /** The answer was already given; costs nothing. */
    ALREADY_ANSWERED;

    companion object {
        /** The event for a typed answer's result, or null for [AnswerResult.None]. */
        fun from(result: AnswerResult): AnswerFeedbackEvent? = when (result) {
            is AnswerResult.Correct -> CORRECT
            AnswerResult.Incorrect -> INCORRECT
            AnswerResult.NearMiss -> NEAR_MISS
            AnswerResult.AlreadyAnswered -> ALREADY_ANSWERED
            AnswerResult.None -> null
        }
    }
}

/**
 * The latest [AnswerFeedbackEvent] as composition state. [sequence] grows with every event, so
 * two equal events in a row (e.g. two wrong answers) still start a new animation.
 */
data class FeedbackSignal(val event: AnswerFeedbackEvent? = null, val sequence: Int = 0) {
    fun next(event: AnswerFeedbackEvent): FeedbackSignal = FeedbackSignal(event, sequence + 1)
}
