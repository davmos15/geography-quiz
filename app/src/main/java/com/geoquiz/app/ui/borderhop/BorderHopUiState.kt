package com.geoquiz.app.ui.borderhop

import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory

/**
 * One Border hop round (task 6.1, D44): [startCode] and [targetCode] are fixed for the round
 * (picked by [BorderGraph.randomPuzzle][com.geoquiz.app.domain.mode.borderhop.BorderGraph.randomPuzzle]
 * for [category] and [difficulty]'s hop range), [parHops] is the shortest-path length between
 * them when the round began. [path] is the chain of countries hopped so far, always starting with
 * [startCode]; the player is standing on [path]'s last entry. There is no "wrong tap": only a
 * current country's land-border neighbours are ever offered, so the round only ever ends by
 * reaching [targetCode] or by giving up.
 */
data class BorderHopState(
    val category: QuizCategory,
    val difficulty: Difficulty,
    val startCode: String,
    val targetCode: String,
    val parHops: Int,
    val path: List<String>,
    /** Every hint used, one entry per use, for [com.geoquiz.app.domain.mode.borderhop.BorderHopScoring]. */
    val hintsUsed: List<HintType> = emptyList(),
    /** [HintType.SHOW_VALID_HOP]'s reveal for the current position; cleared on every hop or undo. */
    val revealedHopCode: String? = null,
    val isComplete: Boolean = false,
    /** True once the round ended by Give up rather than reaching [targetCode]. */
    val gaveUp: Boolean = false,
    /** True while backgrounded, or a restored unfinished round (same as the other new modes). */
    val paused: Boolean = false
) {
    val currentCode: String get() = path.last()
    val hopsUsed: Int get() = path.size - 1
    val reachedTarget: Boolean get() = !gaveUp && currentCode == targetCode
    val canUndo: Boolean get() = path.size > 1 && !isComplete && !paused
}

sealed class BorderHopUiState {
    data object Loading : BorderHopUiState()

    /** No valid puzzle could be generated for this category/difficulty (task 6.1 review). */
    data object Unavailable : BorderHopUiState()
    data class Active(val state: BorderHopState) : BorderHopUiState()
}

/** A recorded round on its way to Results, mirroring `SilhouettesCompletion`/`TapMapCompletion`. */
data class BorderHopCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
