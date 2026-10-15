package com.geoquiz.app.ui.flagspeed

import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty

/**
 * One flag speed round in progress (task 7.3, D52).
 *
 * [askedCodes] is every target asked *and answered* so far, in order, repeats allowed - there is
 * no fixed quiz set in this mode. The question on screen when time runs out, or when the player
 * gives up, is never added here (it was shown but never answered). [correctCodes] is the set of
 * codes answered correctly at least once; [FlagSpeedScoring][com.geoquiz.app.domain.mode.flagspeed.FlagSpeedScoring]'s
 * KDoc explains why a set, not a per-question record, is the right shape here.
 *
 * [liveCombo] is the player-visible streak counter: it increases by one on every correct answer
 * and resets to zero on a wrong one. [longestCombo] never resets - it is the round's best streak
 * so far, and what scoring reads at the end.
 */
data class FlagSpeedRoundState(
    val difficulty: Difficulty,
    val question: ChoiceQuestion? = null,
    val feedback: ChoiceFeedback? = null,
    val askedCodes: List<String> = emptyList(),
    val correctCodes: Set<String> = emptySet(),
    val incorrectGuesses: Int = 0,
    val incorrectGuessStrings: List<String> = emptyList(),
    val liveCombo: Int = 0,
    val longestCombo: Int = 0,
    /** Every lookalike set id drawn during the round, for the post-round review. */
    val encounteredSetIds: Set<String> = emptySet(),
    /** The current (or, mid-feedback, the just-answered) question's draw, to avoid an immediate repeat. */
    val lastDrawSetId: String? = null,
    val lastDrawTargetCode: String? = null,
    val isComplete: Boolean = false,
    /** True once the round ended by Give up rather than the clock running out. */
    val gaveUp: Boolean = false,
    /** True while a pick's feedback is showing and the next question is scheduled to follow it. */
    val pendingAdvance: Boolean = false,
    /** True while backgrounded, or a restored unfinished round (same as the other new modes). */
    val paused: Boolean = false
) {
    val askedCount: Int get() = askedCodes.size
    val correctCount: Int get() = askedCodes.size - incorrectGuesses
}

sealed class FlagSpeedUiState {
    data object Loading : FlagSpeedUiState()

    /** No usable draw pool for this difficulty (should not happen with the real data file). */
    data object Unavailable : FlagSpeedUiState()
    data class Active(val state: FlagSpeedRoundState) : FlagSpeedUiState()
}

/**
 * A recorded round on its way to Results, mirroring `SilhouettesCompletion`/`BorderHopCompletion`
 * with one extra step: [Step.REVIEW] shows the lookalike explanations for sets encountered during
 * the round (task 7.3's "explanation after each answer in review") before [Step.NAVIGATE] leaves
 * for the shared Results screen.
 */
data class FlagSpeedCompletion(val resultId: String, val step: Step) {
    enum class Step { REVIEW, NAVIGATE, DONE }
}
