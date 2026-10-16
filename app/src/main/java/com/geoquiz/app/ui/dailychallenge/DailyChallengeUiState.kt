package com.geoquiz.app.ui.dailychallenge

import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestion
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.ChoiceFeedback

/**
 * One daily challenge round in progress (task 8.1). [questions] is the day's fixed list of 10
 * (built once by [com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGenerator] and never
 * changed for the life of the round); [askedCodes] is every target that has been given its one
 * final attempt so far, in order - since every question gets exactly one attempt (no retries,
 * unlike the classic quiz), [askedCodes] always equals `questions.take(askedCodes.size)`'s
 * targets, so [currentIndex] (the question on screen) is simply its size.
 */
data class DailyChallengeRoundState(
    val questions: List<DailyChallengeQuestion> = emptyList(),
    val askedCodes: List<String> = emptyList(),
    val correctCodes: Set<String> = emptySet(),
    val incorrectGuesses: Int = 0,
    /** Typed kinds only: the field's current text. */
    val currentInput: String = "",
    /** Typed kinds only: the last submit's result, reusing [com.geoquiz.app.ui.components.AnswerInput]'s own feedback. */
    val lastAnswerResult: AnswerResult = AnswerResult.None,
    /** Choice kinds only: the just-picked option, shown briefly before the next question. */
    val choiceFeedback: ChoiceFeedback? = null,
    /** True while a correct/incorrect result is showing and the next question is scheduled to follow it. */
    val pendingAdvance: Boolean = false,
    val isComplete: Boolean = false,
    /** True once the round ended by "End challenge" rather than answering all 10. */
    val gaveUp: Boolean = false,
    /** True while backgrounded, or a restored unfinished round (same as every other new mode, D37). */
    val paused: Boolean = false
) {
    val currentIndex: Int get() = askedCodes.size

    /**
     * The question to show on screen: [currentIndex] itself while [pendingAdvance] is true, since
     * [currentIndex] already counts the just-submitted attempt (see the class doc) - without this,
     * the screen would jump to the next question's flag/prompt while still showing the previous
     * pick's feedback overlaid on it.
     */
    val displayIndex: Int get() = if (pendingAdvance) (currentIndex - 1).coerceAtLeast(0) else currentIndex
    val currentQuestion: DailyChallengeQuestion? get() = questions.getOrNull(displayIndex)
    val totalCount: Int get() = questions.size
}

sealed class DailyChallengeUiState {
    data object Loading : DailyChallengeUiState()

    /** No usable country data (should not happen with the real, bundled data). */
    data object Unavailable : DailyChallengeUiState()
    data class Active(val state: DailyChallengeRoundState) : DailyChallengeUiState()
}

/**
 * A recorded round on its way to the shared Results screen, mirroring
 * `BorderHopCompletion`/`FlagSpeedCompletion`.
 */
data class DailyChallengeCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
