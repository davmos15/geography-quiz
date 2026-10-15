package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState

/**
 * Keeps Tap the map's progress in the screen's [SavedStateHandle] (D37: no Room resume save, so
 * this is the only thing process death or "Don't keep activities" restores from). Modelled on
 * `ui/quiz/QuizSavedState.kt`, with the extra fields [TapMapGameState] needs on top of
 * [QuizState]: the current target when typed/tapped (Normal and Hard), and how many wrong taps
 * this prompt has had.
 *
 * The quiz's countries are not stored: [TapMapGameViewModel] rebuilds them from the route's
 * category on every load (see its doc), and only the player's progress is applied on top. The
 * Easy question's [ChoicePrompt] is never restored faithfully (the screen does not read it; see
 * [TapMapChoiceGenerator]), so a placeholder is used.
 */
class TapMapSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val answeredCodes: List<String>,
        val missedCodes: List<String>,
        val remainingOrder: List<String>,
        val currentTargetCode: String?,
        val choiceTarget: String?,
        val choiceOptionCodes: List<String>,
        val choiceOptionLabels: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val hintsUsed: List<String>,
        val wrongTapsThisPrompt: Int,
        val hintUsedThisPrompt: Boolean,
        val isComplete: Boolean,
        val elapsedMillis: Long
    ) {
        /**
         * Rebuilds the state for [quiz]. Codes no longer in the quiz are dropped. A quiz that is
         * not complete comes back paused (classic's `QuizSavedState` does the same), so the
         * player picks up where they chose to.
         *
         * S2: a target or an Easy [ChoiceQuestion] whose country is already answered or missed
         * is dropped (not restored) rather than shown again, the same way classic's
         * `QuizViewModel.prepareChoices` treats a saved Easy question whose target is already
         * done: [TapMapGameViewModel.loadQuiz] then advances past it (its "no target" fallback
         * calls `nextPrompt`), so a restored screen never offers a second answer on a prompt that
         * was already resolved before the process died. A wrong-tap streak that had already
         * reached the reveal limit, but whose reveal (and the miss it marks) had not run yet when
         * the snapshot was taken, is treated the same way: marked missed up front, dropped, and
         * advanced past.
         */
        fun toGameState(quiz: Quiz): TapMapGameState {
            val quizCodes = quiz.countries.mapTo(HashSet()) { it.code }
            val answered = answeredCodes.filter { it in quizCodes }.toSet()
            val restoredMissed = missedCodes.filter { it in quizCodes }.toSet()

            val restoredTarget = currentTargetCode?.takeIf { it in quizCodes }
            val wrongLimitReached = wrongTapsThisPrompt >= TapMapGameViewModel.NORMAL_REVEAL_LIMIT
            val missed = if (
                restoredTarget != null && wrongLimitReached &&
                restoredTarget !in answered && restoredTarget !in restoredMissed
            ) {
                restoredMissed + restoredTarget
            } else {
                restoredMissed
            }
            val done = answered + missed

            val remaining = remainingOrder.filter { it in quizCodes }
            val choice = restoreChoice()?.takeIf { it.targetCode !in done }
            val target = restoredTarget?.takeIf { !wrongLimitReached && it !in done }

            val qs = QuizState(
                quiz = quiz,
                answeredCountries = answered,
                isComplete = isComplete,
                isPaused = !isComplete,
                incorrectGuesses = incorrectGuesses,
                incorrectGuessStrings = incorrectGuessStrings,
                choice = choice,
                remainingOrder = remaining,
                missedCountries = missed,
                hintsUsed = hintsUsed.mapNotNull { name -> HintType.entries.find { it.name == name } }
            )
            return TapMapGameState(
                quizState = qs,
                currentTargetCode = target,
                wrongTapsThisPrompt = if (target == null) 0 else wrongTapsThisPrompt,
                hintUsedThisPrompt = if (target == null) false else hintUsedThisPrompt
            )
        }

        private fun restoreChoice(): ChoiceQuestion? {
            val target = choiceTarget ?: return null
            if (choiceOptionCodes.size != choiceOptionLabels.size || target !in choiceOptionCodes) return null
            return ChoiceQuestion(
                targetCode = target,
                options = choiceOptionCodes.zip(choiceOptionLabels) { code, label -> ChoiceOption(code, label) },
                // The prompt text is never shown for Tap the map (the map highlights the target
                // instead); a placeholder keeps this restorable without round-tripping it.
                prompt = ChoicePrompt.InSet("")
            )
        }
    }

    /** True once [save] has run for this quiz (also after process death). */
    val hasSnapshot: Boolean
        get() = handle.contains(KEY_ANSWERED)

    fun save(state: TapMapGameState, elapsedMillis: Long) {
        val qs = state.quizState
        handle[KEY_ANSWERED] = ArrayList(qs.answeredCountries)
        handle[KEY_MISSED] = ArrayList(qs.missedCountries)
        handle[KEY_REMAINING] = ArrayList(qs.remainingOrder)
        handle[KEY_TARGET] = state.currentTargetCode
        handle[KEY_CHOICE_TARGET] = qs.choice?.targetCode
        handle[KEY_CHOICE_OPTION_CODES] = qs.choice?.options?.mapTo(ArrayList()) { it.code }
        handle[KEY_CHOICE_OPTION_LABELS] = qs.choice?.options?.mapTo(ArrayList()) { it.label }
        handle[KEY_INCORRECT_COUNT] = qs.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(qs.incorrectGuessStrings)
        handle[KEY_HINTS_USED] = ArrayList(qs.hintsUsed.map { it.name })
        handle[KEY_WRONG_THIS_PROMPT] = state.wrongTapsThisPrompt
        handle[KEY_HINT_USED_THIS_PROMPT] = state.hintUsedThisPrompt
        handle[KEY_COMPLETE] = qs.isComplete
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            answeredCodes = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
            missedCodes = handle.get<ArrayList<String>>(KEY_MISSED).orEmpty(),
            remainingOrder = handle.get<ArrayList<String>>(KEY_REMAINING).orEmpty(),
            currentTargetCode = handle[KEY_TARGET],
            choiceTarget = handle[KEY_CHOICE_TARGET],
            choiceOptionCodes = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_CODES).orEmpty(),
            choiceOptionLabels = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_LABELS).orEmpty(),
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            hintsUsed = handle.get<ArrayList<String>>(KEY_HINTS_USED).orEmpty(),
            wrongTapsThisPrompt = handle[KEY_WRONG_THIS_PROMPT] ?: 0,
            hintUsedThisPrompt = handle[KEY_HINT_USED_THIS_PROMPT] ?: false,
            isComplete = handle[KEY_COMPLETE] ?: false,
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L
        )
    }

    /**
     * The id of this quiz's [com.geoquiz.app.domain.model.CompletedQuiz]. Set before the
     * completion is recorded, so a recreated screen reuses it instead of recording again.
     */
    var resultId: String?
        get() = handle[KEY_RESULT_ID]
        set(value) {
            handle[KEY_RESULT_ID] = value
        }

    /** The tier this round resolved to, stored before any progress (see `QuizSavedState`). */
    var difficulty: Difficulty?
        get() = Difficulty.fromIdOrNull(handle[KEY_DIFFICULTY])
        set(value) {
            handle[KEY_DIFFICULTY] = value?.id
        }

    companion object {
        const val KEY_DIFFICULTY = "tap_map_difficulty"
        const val KEY_ANSWERED = "tap_map_answered_codes"
        const val KEY_MISSED = "tap_map_missed_codes"
        const val KEY_REMAINING = "tap_map_remaining_order"
        const val KEY_TARGET = "tap_map_current_target"
        const val KEY_CHOICE_TARGET = "tap_map_choice_target"
        const val KEY_CHOICE_OPTION_CODES = "tap_map_choice_option_codes"
        const val KEY_CHOICE_OPTION_LABELS = "tap_map_choice_option_labels"
        const val KEY_INCORRECT_COUNT = "tap_map_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "tap_map_incorrect_strings"
        const val KEY_HINTS_USED = "tap_map_hints_used"
        const val KEY_WRONG_THIS_PROMPT = "tap_map_wrong_this_prompt"
        const val KEY_HINT_USED_THIS_PROMPT = "tap_map_hint_used_this_prompt"
        const val KEY_COMPLETE = "tap_map_complete"
        const val KEY_ELAPSED_MILLIS = "tap_map_elapsed_millis"
        const val KEY_RESULT_ID = "tap_map_result_id"
    }
}
