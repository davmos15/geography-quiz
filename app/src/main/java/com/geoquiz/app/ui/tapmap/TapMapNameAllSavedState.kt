package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState

/**
 * Keeps "Name all"'s progress in the screen's [SavedStateHandle] (D37/D40: no Room resume save,
 * so this is the only thing process death or "Don't keep activities" restores from). Modelled on
 * `ui/quiz/QuizSavedState.kt`'s typed-tier fields, minus the Easy-only multiple-choice ones this
 * variant never uses (D40: Normal and Hard only).
 *
 * The quiz's countries are not stored: [TapMapNameAllViewModel] rebuilds them from the route's
 * category on every load, and only the player's progress is applied on top.
 */
class TapMapNameAllSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val answeredCodes: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val currentInput: String,
        val recentCorrect: List<String>,
        val isComplete: Boolean,
        val elapsedMillis: Long
    ) {
        /**
         * Rebuilds the state for [quiz]. Codes no longer in the quiz are dropped. A quiz that is
         * not complete comes back paused (classic's `QuizSavedState` does the same), so the
         * player picks up where they chose to.
         */
        fun toQuizState(quiz: Quiz): QuizState {
            val quizCodes = quiz.countries.mapTo(HashSet()) { it.code }
            val answered = answeredCodes.filter { it in quizCodes }.toSet()
            return QuizState(
                quiz = quiz,
                answeredCountries = answered,
                currentInput = currentInput,
                isComplete = isComplete,
                isPaused = !isComplete,
                incorrectGuesses = incorrectGuesses,
                incorrectGuessStrings = incorrectGuessStrings,
                recentCorrect = recentCorrect.filter { it in answered }.distinct().take(QuizState.RECENT_CORRECT_MAX)
            )
        }
    }

    /** True once [save] has run for this quiz (also after process death). */
    val hasSnapshot: Boolean
        get() = handle.contains(KEY_ANSWERED)

    fun save(state: QuizState, elapsedMillis: Long) {
        handle[KEY_ANSWERED] = ArrayList(state.answeredCountries)
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_RECENT_CORRECT] = ArrayList(state.recentCorrect)
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            answeredCodes = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            currentInput = handle[KEY_INPUT] ?: "",
            recentCorrect = handle.get<ArrayList<String>>(KEY_RECENT_CORRECT).orEmpty(),
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
        const val KEY_DIFFICULTY = "tap_map_name_all_difficulty"
        const val KEY_ANSWERED = "tap_map_name_all_answered_codes"
        const val KEY_INCORRECT_COUNT = "tap_map_name_all_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "tap_map_name_all_incorrect_strings"
        const val KEY_INPUT = "tap_map_name_all_current_input"
        const val KEY_RECENT_CORRECT = "tap_map_name_all_recent_correct"
        const val KEY_COMPLETE = "tap_map_name_all_complete"
        const val KEY_ELAPSED_MILLIS = "tap_map_name_all_elapsed_millis"
        const val KEY_RESULT_ID = "tap_map_name_all_result_id"
    }
}
