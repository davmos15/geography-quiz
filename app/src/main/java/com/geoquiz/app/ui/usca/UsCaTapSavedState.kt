package com.geoquiz.app.ui.usca

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState

/**
 * Keeps Tap's progress in the screen's [SavedStateHandle] (D37: no Room resume save). Modelled
 * on `ui/tapmap/TapMapSavedState.kt`'s Normal/Hard fields (this variant has no Easy tier and no
 * hint, D36 adapted).
 */
class UsCaTapSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val answeredCodes: List<String>,
        val missedCodes: List<String>,
        val remainingOrder: List<String>,
        val currentTargetCode: String?,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val wrongTapsThisPrompt: Int,
        val isComplete: Boolean,
        val elapsedMillis: Long
    ) {
        fun toGameState(quiz: Quiz): UsCaTapGameState {
            val quizCodes = quiz.countries.mapTo(HashSet()) { it.code }
            val answered = answeredCodes.filter { it in quizCodes }.toSet()
            val restoredMissed = missedCodes.filter { it in quizCodes }.toSet()
            val restoredTarget = currentTargetCode?.takeIf { it in quizCodes }
            val wrongLimitReached = wrongTapsThisPrompt >= UsCaTapViewModel.NORMAL_REVEAL_LIMIT
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
            val target = restoredTarget?.takeIf { !wrongLimitReached && it !in done }

            val qs = QuizState(
                quiz = quiz,
                answeredCountries = answered,
                isComplete = isComplete,
                isPaused = !isComplete,
                incorrectGuesses = incorrectGuesses,
                incorrectGuessStrings = incorrectGuessStrings,
                remainingOrder = remaining,
                missedCountries = missed
            )
            return UsCaTapGameState(
                quizState = qs,
                currentTargetCode = target,
                wrongTapsThisPrompt = if (target == null) 0 else wrongTapsThisPrompt
            )
        }
    }

    val hasSnapshot: Boolean
        get() = handle.contains(KEY_ANSWERED)

    fun save(state: UsCaTapGameState, elapsedMillis: Long) {
        val qs = state.quizState
        handle[KEY_ANSWERED] = ArrayList(qs.answeredCountries)
        handle[KEY_MISSED] = ArrayList(qs.missedCountries)
        handle[KEY_REMAINING] = ArrayList(qs.remainingOrder)
        handle[KEY_TARGET] = state.currentTargetCode
        handle[KEY_INCORRECT_COUNT] = qs.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(qs.incorrectGuessStrings)
        handle[KEY_WRONG_THIS_PROMPT] = state.wrongTapsThisPrompt
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
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            wrongTapsThisPrompt = handle[KEY_WRONG_THIS_PROMPT] ?: 0,
            isComplete = handle[KEY_COMPLETE] ?: false,
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L
        )
    }

    var resultId: String?
        get() = handle[KEY_RESULT_ID]
        set(value) {
            handle[KEY_RESULT_ID] = value
        }

    var difficulty: Difficulty?
        get() = Difficulty.fromIdOrNull(handle[KEY_DIFFICULTY])
        set(value) {
            handle[KEY_DIFFICULTY] = value?.id
        }

    companion object {
        const val KEY_DIFFICULTY = "us_ca_tap_difficulty"
        const val KEY_ANSWERED = "us_ca_tap_answered_codes"
        const val KEY_MISSED = "us_ca_tap_missed_codes"
        const val KEY_REMAINING = "us_ca_tap_remaining_order"
        const val KEY_TARGET = "us_ca_tap_current_target"
        const val KEY_INCORRECT_COUNT = "us_ca_tap_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "us_ca_tap_incorrect_strings"
        const val KEY_WRONG_THIS_PROMPT = "us_ca_tap_wrong_this_prompt"
        const val KEY_COMPLETE = "us_ca_tap_complete"
        const val KEY_ELAPSED_MILLIS = "us_ca_tap_elapsed_millis"
        const val KEY_RESULT_ID = "us_ca_tap_result_id"
    }
}

/**
 * One Tap round. [quizState] is a plain [QuizState] (D37, reused so
 * [com.geoquiz.app.domain.usecase.CompleteQuizUseCase] scores it like any other mode); the rest
 * is this screen's own: the current target and the transient wrong-tap feedback.
 */
data class UsCaTapGameState(
    val quizState: QuizState,
    val currentTargetCode: String? = null,
    val wrongTapsThisPrompt: Int = 0,
    val wrongFlashCode: String? = null,
    val wrongFlashName: String? = null,
    val revealedCode: String? = null
)
