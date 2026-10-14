package com.geoquiz.app.ui.silhouettes

import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory

/** One answer's result, shown under the silhouette until the next submit (or Pass/hint reveal). */
sealed class SilhouetteFeedback {
    data object None : SilhouetteFeedback()

    /** The target's name, typed or picked correctly. */
    data class Correct(val name: String) : SilhouetteFeedback()

    /**
     * [name] is a real country, just not the one shown: a wrong guess (loses a life once per
     * name). [livesLeft] is how many remain after this guess, for the spoken text (e.g.
     * "Germany is a country, but not this one. 2 lives left").
     */
    data class WrongCountry(val name: String, val livesLeft: Int) : SilhouetteFeedback()

    /** A typo in Hard, or an ambiguous one in Normal; never a life lost. */
    data object NearMiss : SilhouetteFeedback()

    /** Not recognised as any country's name; never a life lost. */
    data object NotRecognised : SilhouetteFeedback()

    /** Pass, or lives ran out: the target's name is shown briefly. Counts as missed. */
    data class Revealed(val name: String) : SilhouetteFeedback()

    /**
     * Lives ran out on this wrong guess (task 5.1 review, S4): [wrongName] is the last wrong
     * country named, [revealedName] the target's real name. Shown together for
     * [com.geoquiz.app.ui.silhouettes.SilhouettesGameViewModel.REVEAL_DELAY_MILLIS] before the
     * round's result is published.
     */
    data class LivesExhausted(val wrongName: String, val revealedName: String) : SilhouetteFeedback()
}

/**
 * One Country silhouettes round (task 5.1, D34): [countries] is fixed for the round (15 random
 * from the category's filter, fewer if smaller, or a practice set's exact codes in order);
 * [currentIndex] points at the silhouette on screen. [answeredCodes] are correctly named;
 * everything else in [countries] not answered counts as missed once the round finishes
 * ([com.geoquiz.app.domain.model.CompletedQuiz.missedCodes]), whether passed, revealed after
 * lives ran out, or never reached.
 */
data class SilhouetteState(
    val category: QuizCategory,
    val difficulty: Difficulty,
    val countries: List<Country>,
    val currentIndex: Int = 0,
    val answeredCodes: Set<String> = emptySet(),
    /** Wrong-country guesses and wrong Easy picks; three end the round early (D34). */
    val incorrectGuesses: Int = 0,
    val incorrectGuessStrings: List<String> = emptyList(),
    /** Every hint used, one entry per use, for this mode's [com.geoquiz.app.domain.mode.ScoringRule]. */
    val hintsUsed: List<HintType> = emptyList(),
    /** Hints used on the current silhouette only; each type is usable once per silhouette. */
    val hintsUsedForCurrent: Set<HintType> = emptySet(),
    /** Codes wrongly guessed on the current silhouette, in guess order (for de-duplication). */
    val wrongGuessCodesForCurrent: List<String> = emptyList(),
    /** Names in the same order as [wrongGuessCodesForCurrent] (the "guessed so far" chips). */
    val wrongGuessNamesForCurrent: List<String> = emptyList(),
    val currentInput: String = "",
    val feedback: SilhouetteFeedback = SilhouetteFeedback.None,
    /** Show neighbours hint, reset for every new silhouette. */
    val showNeighbours: Boolean = false,
    val outline: OutlineModel? = null,
    /** Sorted names of the outline's land neighbours, for the Show neighbours hint (D38). */
    val neighbourNames: List<String> = emptyList(),
    val isOutlineLoading: Boolean = false,
    /** True once the outline failed to load (or there is none): shows a fallback, not a spinner. */
    val isOutlineUnavailable: Boolean = false,
    val isComplete: Boolean = false,
    /** True for the brief pause after a correct answer, a pass or a reveal, before the next one. */
    val isAdvancing: Boolean = false,
    /**
     * True while [isAdvancing] (or an Easy pick's result is on screen): the round has already
     * moved on in spirit, just waiting out its pause. Persisted so a process death mid-pause
     * advances once on restore instead of re-asking the same silhouette (task 5.1 review, S2).
     */
    val pendingAdvance: Boolean = false,
    /** True while the screen is backgrounded or on a paused restored round (task 5.1 review, S3). */
    val paused: Boolean = false,
    /** Bumped on every commit so repeated identical feedback is still a new value to announce. */
    val feedbackToken: Int = 0,
    /** Easy only: the question on screen for the current silhouette. */
    val choice: ChoiceQuestion? = null,
    val choiceFeedback: ChoiceFeedback? = null
) {
    val lives: Int get() = (MAX_LIVES - incorrectGuesses).coerceAtLeast(0)
    val current: Country? get() = countries.getOrNull(currentIndex)
    val progress: Int get() = currentIndex.coerceAtMost(countries.size)

    /**
     * This mode's hint set ([SilhouettesGameMode.SPEC]), minus Show region when [category] is
     * already a region or subregion filter: the player chose it, so the hint would give nothing
     * away (task 5.1 review nit).
     */
    val availableHints: Set<HintType>
        get() {
            val all = SilhouettesGameMode.SPEC.hintTypes
            return if (category is QuizCategory.ByRegion || category is QuizCategory.BySubregion) {
                all - HintType.SHOW_REGION
            } else {
                all
            }
        }

    companion object {
        /** 3 lives in every tier (D34). */
        const val MAX_LIVES = 3
    }
}

sealed class SilhouettesUiState {
    data object Loading : SilhouettesUiState()
    data class Active(val state: SilhouetteState) : SilhouettesUiState()
}

/** A recorded round on its way to Results, mirroring `QuizCompletion`. */
data class SilhouettesCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
