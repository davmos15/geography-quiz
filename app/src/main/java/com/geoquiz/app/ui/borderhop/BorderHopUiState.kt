package com.geoquiz.app.ui.borderhop

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory

/**
 * One Border hop round (task 6.1, D44; map version task P10-C): [startCode] and [targetCode] are
 * fixed for the round (picked by
 * [BorderGraph.randomPuzzle][com.geoquiz.app.domain.mode.borderhop.BorderGraph.randomPuzzle] for
 * [category] and [difficulty]'s hop range), [parHops] is the shortest-route length between them
 * when the round began. [path] is the route so far, always starting with [startCode]; the player
 * is standing on its last entry and names a country that borders it.
 *
 * The round ends by reaching [targetCode], by Give up ([gaveUp]) or, at Hard, by
 * [Difficulty.strikeLimit] wrong guesses ([outOfGuesses]).
 */
data class BorderHopState(
    val category: QuizCategory,
    val difficulty: Difficulty,
    val startCode: String,
    val targetCode: String,
    val parHops: Int,
    val path: List<String>,
    /** Every wrong guess (a named or picked country that doesn't border the end of the route), in order. */
    val wrongGuesses: List<String> = emptyList(),
    /**
     * Every distinct (current country, wrongly named country) pair seen so far, as
     * `"$fromCode>$code"`. Naming the same non-neighbour again from the same position repeats the
     * feedback but is not a second strike or a duplicate [wrongGuesses] entry (task P10-R2 M1).
     * Never reset mid-round, including by [canUndo]'s undo.
     */
    val wrongPairs: Set<String> = emptySet(),
    /** Every hint used, one entry per use, for [com.geoquiz.app.domain.mode.borderhop.BorderHopScoring]. */
    val hintsUsed: List<HintType> = emptyList(),
    /** [HintType.SHOW_VALID_HOP]'s reveal for the current position; cleared on every hop or undo. */
    val revealedHopCode: String? = null,
    val isComplete: Boolean = false,
    /** True once the round ended by Give up. */
    val gaveUp: Boolean = false,
    /** True once Hard's strike limit ended the round. */
    val outOfGuesses: Boolean = false,
    /** True while backgrounded, or a restored unfinished round (same as the other new modes). */
    val paused: Boolean = false,
    /** Normal/Hard: the text field. */
    val input: String = "",
    /** Normal only: up to five country codes suggested for [input] (empty at Hard and Easy). */
    val suggestions: List<String> = emptyList(),
    /** The result of the latest guess, for the feedback line; null before the first one. */
    val feedback: BorderHopFeedback? = null,
    /** A wrong guess shown orange + cross on the map for a moment, then cleared. */
    val flashWrongCode: String? = null,
    /** Easy: the four options for the current position (empty at Normal/Hard). */
    val options: List<String> = emptyList(),
    /** Easy: options already picked wrongly at this position (orange + cross, disabled). */
    val wrongPicks: Set<String> = emptySet(),
    /**
     * Easy: a right pick still being shown (blue + tick) before the next options appear. Already
     * on [path]; input is locked until new options are built.
     */
    val correctPick: String? = null,
    /**
     * Whether [HintType.SHOW_VALID_HOP] has anything to reveal from here: false when every
     * neighbour one hop closer to the target is already on [path] (P10-R2 L5). Recomputed by the
     * ViewModel on every [com.geoquiz.app.ui.borderhop.BorderHopGameViewModel] commit (it needs
     * the border graph, which this plain state doesn't have); defaults to true so a state built
     * without going through a commit (tests, screenshots) still shows the hint button enabled.
     */
    val hintAvailable: Boolean = true
) {
    val currentCode: String get() = path.last()
    val hopsUsed: Int get() = path.size - 1
    val reachedTarget: Boolean get() = !gaveUp && !outOfGuesses && currentCode == targetCode
    val canUndo: Boolean get() = path.size > 1 && !isComplete && !paused && correctPick == null
    val acceptsInput: Boolean get() = !isComplete && !paused && correctPick == null

    /** Hard's strikes left, or null when this tier has no strike limit. */
    val strikesLeft: Int?
        get() = difficulty.strikeLimit?.let { (it - wrongGuesses.size).coerceAtLeast(0) }
}

/** The result of the latest guess, shown in a polite live region under the input. */
sealed interface BorderHopFeedback {
    data class NotRecognised(val input: String) : BorderHopFeedback
    data class AlreadyOnRoute(val code: String) : BorderHopFeedback
    data class NotNeighbour(val code: String, val fromCode: String) : BorderHopFeedback
    data class Hopped(val code: String) : BorderHopFeedback
}

/**
 * The bundled map layers for the round's world map (no labels): the same three layers
 * Tap the map loads (`loadTapMapMapState`), always the World preset, so the player can pan
 * anywhere; the camera frames the route with [BorderHopGameViewModel.routeFrame].
 */
data class BorderHopMapState(
    val coarse: GeoLayer? = null,
    val detail: GeoLayer? = null,
    val tapZones: GeoLayer? = null,
    /** True once loading has finished and every country layer came back null. */
    val loadFailed: Boolean = false
)

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

/**
 * The camera target: [rect] frames [codes], which are [routeCodes] (start, target and every hop)
 * plus a wrong guess while it flashes; never smaller than the ViewModel's minimum frame.
 */
data class BorderHopRouteFrame(val routeCodes: Set<String>, val codes: Set<String>, val rect: PlaneRect)
