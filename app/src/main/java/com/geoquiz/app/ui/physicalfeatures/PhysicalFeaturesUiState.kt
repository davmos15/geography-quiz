package com.geoquiz.app.ui.physicalfeatures

import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureFilter

/** One answer's result, shown until the next submit/pick or the brief pause before advancing. */
sealed class PhysicalFeatureFeedback {
    data object None : PhysicalFeatureFeedback()

    /** Typed or picked correctly. */
    data class Correct(val name: String) : PhysicalFeatureFeedback()

    /** A typo in Hard, or an ambiguous one in Normal (D12); never a strike. */
    data object NearMiss : PhysicalFeatureFeedback()

    /** Not recognised as this round's current feature (whether nonsense or a different, valid
     * one, task 7.2 review: kept as one simple case rather than Silhouettes' "wrong country"
     * distinction, since there is no cross-target bleed to call out here, D51 brief: "keep it
     * simple"). Costs a strike. */
    data object Incorrect : PhysicalFeatureFeedback()

    /** The name of an already-answered feature, typed again: harmless, no strike. */
    data object AlreadyAnswered : PhysicalFeatureFeedback()

    /** Passed (task 7.2 review fix H3): reveals the current feature's name, counts as missed
     * (never added to [PhysicalFeaturesRoundState.answeredIds]), no strike. */
    data class Revealed(val name: String) : PhysicalFeatureFeedback()
}

/**
 * One round (task 7.2, D51): [features] is fixed for the round
 * ([com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesRound], 15 random from the
 * filtered pool, fewer if smaller); [currentIndex] points at the one highlighted on the map.
 * [answeredIds] are correctly named/picked; everything else in [features] not answered counts as
 * missed once the round finishes, whether never reached or still current when Give up or a Hard
 * strike-out ends it early.
 *
 * Wrong typed/picked answers never move the round on (task 7.2 review: unlike Country
 * silhouettes' Pass, there is no "reveal and skip one" here - just keep trying, or Give up ends
 * the whole round): only a correct answer advances, after a brief pause
 * ([PhysicalFeaturesGameViewModel.ADVANCE_DELAY_MILLIS]), or the strike limit at Hard
 * ([com.geoquiz.app.domain.model.Difficulty.strikeLimit]) ends it early, same as every classic
 * mode's Hard tier.
 */
data class PhysicalFeaturesRoundState(
    val filter: PhysicalFeatureFilter,
    val difficulty: Difficulty,
    val features: List<PhysicalFeature>,
    val currentIndex: Int = 0,
    val answeredIds: Set<String> = emptySet(),
    val incorrectGuesses: Int = 0,
    val incorrectGuessStrings: List<String> = emptyList(),
    val currentInput: String = "",
    val feedback: PhysicalFeatureFeedback = PhysicalFeatureFeedback.None,
    val isComplete: Boolean = false,
    /** True for the brief pause after a correct answer, before the next feature. */
    val isAdvancing: Boolean = false,
    /** True while [isAdvancing]: persisted so a process death mid-pause advances once on restore
     * instead of re-asking the same feature (same pattern as Country silhouettes, task 5.1
     * review S2). */
    val pendingAdvance: Boolean = false,
    val paused: Boolean = false,
    /** Bumped on every commit so repeated identical feedback is still a new value to announce. */
    val feedbackToken: Int = 0,
    /** Easy only: the question on screen for the current feature. */
    val choice: ChoiceQuestion? = null,
    val choiceFeedback: ChoiceFeedback? = null
) {
    val current: PhysicalFeature? get() = features.getOrNull(currentIndex)
    val progress: Int get() = currentIndex.coerceAtMost(features.size)
}

sealed class PhysicalFeaturesUiState {
    data object Loading : PhysicalFeaturesUiState()
    data class Active(val state: PhysicalFeaturesRoundState) : PhysicalFeaturesUiState()
}

/** A recorded round on its way to Results, mirroring `SilhouettesCompletion`/`QuizCompletion`. */
data class PhysicalFeaturesCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
