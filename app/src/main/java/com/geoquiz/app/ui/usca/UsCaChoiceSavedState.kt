package com.geoquiz.app.ui.usca

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState

/**
 * Keeps Multiple choice's progress in the screen's [SavedStateHandle] (D37: no Room resume save).
 * Modelled on `ui/tapmap/TapMapSavedState.kt`'s Easy-only fields (this variant has no map tap or
 * hint state to keep on top). The quiz's items are not stored: [UsCaChoiceViewModel] rebuilds
 * them from the route's category on every load.
 */
class UsCaChoiceSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val answeredCodes: List<String>,
        val missedCodes: List<String>,
        val remainingOrder: List<String>,
        val choiceTarget: String?,
        val choiceOptionCodes: List<String>,
        val choiceOptionLabels: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val isComplete: Boolean,
        val elapsedMillis: Long
    ) {
        fun toQuizState(quiz: Quiz): QuizState {
            val quizCodes = quiz.countries.mapTo(HashSet()) { it.code }
            val answered = answeredCodes.filter { it in quizCodes }.toSet()
            val missed = missedCodes.filter { it in quizCodes }.toSet()
            val done = answered + missed
            val remaining = remainingOrder.filter { it in quizCodes }
            val choice = restoreChoice()?.takeIf { it.targetCode !in done }
            return QuizState(
                quiz = quiz,
                answeredCountries = answered,
                isComplete = isComplete,
                isPaused = !isComplete,
                incorrectGuesses = incorrectGuesses,
                incorrectGuessStrings = incorrectGuessStrings,
                choice = choice,
                remainingOrder = remaining,
                missedCountries = missed
            )
        }

        private fun restoreChoice(): ChoiceQuestion? {
            val target = choiceTarget ?: return null
            if (choiceOptionCodes.size != choiceOptionLabels.size || target !in choiceOptionCodes) return null
            return ChoiceQuestion(
                targetCode = target,
                options = choiceOptionCodes.zip(choiceOptionLabels) { code, label -> ChoiceOption(code, label) },
                prompt = ChoicePrompt.InSet("")
            )
        }
    }

    val hasSnapshot: Boolean
        get() = handle.contains(KEY_ANSWERED)

    fun save(state: QuizState, elapsedMillis: Long) {
        handle[KEY_ANSWERED] = ArrayList(state.answeredCountries)
        handle[KEY_MISSED] = ArrayList(state.missedCountries)
        handle[KEY_REMAINING] = ArrayList(state.remainingOrder)
        handle[KEY_CHOICE_TARGET] = state.choice?.targetCode
        handle[KEY_CHOICE_OPTION_CODES] = state.choice?.options?.mapTo(ArrayList()) { it.code }
        handle[KEY_CHOICE_OPTION_LABELS] = state.choice?.options?.mapTo(ArrayList()) { it.label }
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            answeredCodes = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
            missedCodes = handle.get<ArrayList<String>>(KEY_MISSED).orEmpty(),
            remainingOrder = handle.get<ArrayList<String>>(KEY_REMAINING).orEmpty(),
            choiceTarget = handle[KEY_CHOICE_TARGET],
            choiceOptionCodes = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_CODES).orEmpty(),
            choiceOptionLabels = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_LABELS).orEmpty(),
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            isComplete = handle[KEY_COMPLETE] ?: false,
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L
        )
    }

    var resultId: String?
        get() = handle[KEY_RESULT_ID]
        set(value) {
            handle[KEY_RESULT_ID] = value
        }

    companion object {
        const val KEY_ANSWERED = "us_ca_choice_answered_codes"
        const val KEY_MISSED = "us_ca_choice_missed_codes"
        const val KEY_REMAINING = "us_ca_choice_remaining_order"
        const val KEY_CHOICE_TARGET = "us_ca_choice_target"
        const val KEY_CHOICE_OPTION_CODES = "us_ca_choice_option_codes"
        const val KEY_CHOICE_OPTION_LABELS = "us_ca_choice_option_labels"
        const val KEY_INCORRECT_COUNT = "us_ca_choice_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "us_ca_choice_incorrect_strings"
        const val KEY_COMPLETE = "us_ca_choice_complete"
        const val KEY_ELAPSED_MILLIS = "us_ca_choice_elapsed_millis"
        const val KEY_RESULT_ID = "us_ca_choice_result_id"
    }
}
