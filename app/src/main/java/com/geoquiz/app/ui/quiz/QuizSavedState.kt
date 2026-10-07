package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState

/**
 * Keeps the restorable part of a running quiz in the screen's [SavedStateHandle], so it survives
 * process death and activity recreation ("Don't keep activities"). Rotation keeps the same
 * ViewModel and does not need this.
 *
 * The quiz's countries are not stored: they are rebuilt from the route's category, and only the
 * player's progress is applied on top. The last answer feedback is not restored.
 */
class QuizSavedState(private val handle: SavedStateHandle) {

    /** Progress read back from the handle. */
    data class Snapshot(
        val answeredCodes: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val currentInput: String,
        val isPaused: Boolean,
        val isComplete: Boolean,
        val elapsedMillis: Long
    ) {
        /**
         * Rebuilds the state for [quiz]. Codes that are not in the quiz are dropped. A quiz that
         * is not complete comes back paused, so the player picks up where they chose to.
         */
        fun toQuizState(quiz: Quiz): QuizState {
            val quizCodes = quiz.countries.mapTo(HashSet()) { it.code }
            return QuizState(
                quiz = quiz,
                answeredCountries = answeredCodes.filter { it in quizCodes }.toSet(),
                currentInput = currentInput,
                isComplete = isComplete,
                isPaused = !isComplete,
                incorrectGuesses = incorrectGuesses,
                incorrectGuessStrings = incorrectGuessStrings
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
        handle[KEY_PAUSED] = state.isPaused
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
            isPaused = handle[KEY_PAUSED] ?: false,
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

    /**
     * The tier this quiz resolved to (route, resume save or remembered default). Stored on its
     * own, before any progress, so a recreated screen never re-reads a default that has changed.
     */
    var difficulty: Difficulty?
        get() = Difficulty.fromIdOrNull(handle[KEY_DIFFICULTY])
        set(value) {
            handle[KEY_DIFFICULTY] = value?.id
        }

    companion object {
        const val KEY_DIFFICULTY = "quiz_difficulty"
        const val KEY_ANSWERED = "quiz_answered_codes"
        const val KEY_INCORRECT_COUNT = "quiz_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "quiz_incorrect_strings"
        const val KEY_INPUT = "quiz_current_input"
        const val KEY_PAUSED = "quiz_paused"
        const val KEY_COMPLETE = "quiz_complete"
        const val KEY_ELAPSED_MILLIS = "quiz_elapsed_millis"
        const val KEY_RESULT_ID = "quiz_result_id"
    }
}
