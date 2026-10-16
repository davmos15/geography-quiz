package com.geoquiz.app.ui.currencies

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.model.Difficulty

/**
 * Keeps a running Currencies round in the screen's [SavedStateHandle] (task 7.1, D37): survives
 * process death and activity recreation; rotation keeps the same ViewModel.
 *
 * Each item's *currency* code is stored (in round order), not a representative country code
 * (task 7.1 review fix): [com.geoquiz.app.domain.mode.currencies.CurrencyItems]'s representative
 * assignment is scoped to a category filter, not fixed globally, so a representative country
 * code alone cannot be rebuilt reliably outside the round's own category -
 * `CurrencyQuestionGenerator.itemsForCurrencyCodes` rebuilds the category's items afresh and
 * picks these currency codes back out, which always reproduces the exact same representatives.
 *
 * The current item's choice question, if any, *is* restored byte for byte (also a review fix):
 * target, every option's code and label, and the prompt's currency label are all saved, so a
 * recreated screen shows the identical four options, not a freshly drawn set.
 */
class CurrenciesSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val currencyCodes: List<String>,
        val currentIndex: Int,
        val answeredCodes: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val currentInput: String,
        val isComplete: Boolean,
        /**
         * True if the round died while waiting for the player to tap Next: restore catches up
         * past the already-answered item immediately, rather than re-showing it (matches
         * `SilhouettesSavedState`'s `pendingAdvance`; this field's name is a leftover from an
         * earlier draft and is kept only for the saved key, not for its literal meaning here).
         */
        val pendingAdvance: Boolean,
        val elapsedMillis: Long,
        private val choiceTargetCode: String?,
        private val choiceOptionCodes: List<String>,
        private val choiceOptionLabels: List<String>,
        private val choicePromptLabel: String?
    ) {
        /** The exact [ChoiceQuestion] this round had before death, or null if there was none. */
        fun restoreChoice(): ChoiceQuestion? {
            val target = choiceTargetCode ?: return null
            val label = choicePromptLabel ?: return null
            if (choiceOptionCodes.size != choiceOptionLabels.size || target !in choiceOptionCodes) return null
            return ChoiceQuestion(
                targetCode = target,
                options = choiceOptionCodes.zip(choiceOptionLabels) { code, lbl -> ChoiceOption(code, lbl) },
                prompt = ChoicePrompt.CurrencyOf(label)
            )
        }
    }

    val hasSnapshot: Boolean get() = handle.contains(KEY_CURRENCY_CODES)

    fun save(state: CurrencyRoundState, elapsedMillis: Long) {
        handle[KEY_CURRENCY_CODES] = ArrayList(state.items.map { it.entry.code })
        handle[KEY_CURRENT_INDEX] = state.currentIndex
        handle[KEY_ANSWERED] = ArrayList(state.answeredCodes)
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_PENDING_ADVANCE] = state.isAdvancing
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
        handle[KEY_CHOICE_TARGET] = state.choice?.targetCode
        handle[KEY_CHOICE_OPTION_CODES] = state.choice?.options?.mapTo(ArrayList()) { it.code }
        handle[KEY_CHOICE_OPTION_LABELS] = state.choice?.options?.mapTo(ArrayList()) { it.label }
        handle[KEY_CHOICE_PROMPT_LABEL] = (state.choice?.prompt as? ChoicePrompt.CurrencyOf)?.currencyLabel
    }

    /** A keystroke only changes the input (same nit Silhouettes fixed): no full [save] on every key. */
    fun saveInput(value: String) {
        handle[KEY_INPUT] = value
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            currencyCodes = handle.get<ArrayList<String>>(KEY_CURRENCY_CODES).orEmpty(),
            currentIndex = handle[KEY_CURRENT_INDEX] ?: 0,
            answeredCodes = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            currentInput = handle[KEY_INPUT] ?: "",
            isComplete = handle[KEY_COMPLETE] ?: false,
            pendingAdvance = handle[KEY_PENDING_ADVANCE] ?: false,
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L,
            choiceTargetCode = handle[KEY_CHOICE_TARGET],
            choiceOptionCodes = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_CODES).orEmpty(),
            choiceOptionLabels = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_LABELS).orEmpty(),
            choicePromptLabel = handle[KEY_CHOICE_PROMPT_LABEL]
        )
    }

    /** The id of this round's [com.geoquiz.app.domain.model.CompletedQuiz] (set before recording). */
    var resultId: String?
        get() = handle[KEY_RESULT_ID]
        set(value) { handle[KEY_RESULT_ID] = value }

    /** The tier this round resolved to (route or the remembered default), fixed for its life. */
    var difficulty: Difficulty?
        get() = Difficulty.fromIdOrNull(handle[KEY_DIFFICULTY])
        set(value) { handle[KEY_DIFFICULTY] = value?.id }

    companion object {
        const val KEY_DIFFICULTY = "currencies_difficulty"
        const val KEY_CURRENCY_CODES = "currencies_currency_codes"
        const val KEY_CURRENT_INDEX = "currencies_current_index"
        const val KEY_ANSWERED = "currencies_answered_codes"
        const val KEY_INCORRECT_COUNT = "currencies_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "currencies_incorrect_strings"
        const val KEY_INPUT = "currencies_current_input"
        const val KEY_COMPLETE = "currencies_complete"
        const val KEY_PENDING_ADVANCE = "currencies_pending_advance"
        const val KEY_ELAPSED_MILLIS = "currencies_elapsed_millis"
        const val KEY_RESULT_ID = "currencies_result_id"
        const val KEY_CHOICE_TARGET = "currencies_choice_target"
        const val KEY_CHOICE_OPTION_CODES = "currencies_choice_option_codes"
        const val KEY_CHOICE_OPTION_LABELS = "currencies_choice_option_labels"
        const val KEY_CHOICE_PROMPT_LABEL = "currencies_choice_prompt_label"
    }
}
