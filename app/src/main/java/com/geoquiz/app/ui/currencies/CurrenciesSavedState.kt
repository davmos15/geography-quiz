package com.geoquiz.app.ui.currencies

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Difficulty

/**
 * Keeps a running Currencies round in the screen's [SavedStateHandle] (task 7.1, D37): survives
 * process death and activity recreation; rotation keeps the same ViewModel.
 *
 * Only each item's representative country code is stored (in round order), never which currency
 * entry backed it: [com.geoquiz.app.domain.mode.currencies.CurrencyItems.buildAll]'s
 * representative assignment is global and fixed, so
 * `CurrencyQuestionGenerator.itemsForCodes` can always rebuild the exact same items from the
 * codes alone. The current item's choice question (if any) is not restored byte for byte: a
 * recreated screen asks about the same target but may show a freshly drawn set of options.
 */
class CurrenciesSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val countryCodes: List<String>,
        val currentIndex: Int,
        val answeredCodes: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val currentInput: String,
        val isComplete: Boolean,
        /** True if the round died while waiting for the player to tap Next: restore re-shows it. */
        val pendingAdvance: Boolean,
        val elapsedMillis: Long
    )

    val hasSnapshot: Boolean get() = handle.contains(KEY_COUNTRY_CODES)

    fun save(state: CurrencyRoundState, elapsedMillis: Long) {
        handle[KEY_COUNTRY_CODES] = ArrayList(state.items.map { it.country.code })
        handle[KEY_CURRENT_INDEX] = state.currentIndex
        handle[KEY_ANSWERED] = ArrayList(state.answeredCodes)
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_PENDING_ADVANCE] = state.isAdvancing
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    /** A keystroke only changes the input (same nit Silhouettes fixed): no full [save] on every key. */
    fun saveInput(value: String) {
        handle[KEY_INPUT] = value
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            countryCodes = handle.get<ArrayList<String>>(KEY_COUNTRY_CODES).orEmpty(),
            currentIndex = handle[KEY_CURRENT_INDEX] ?: 0,
            answeredCodes = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
            incorrectGuesses = handle[KEY_INCORRECT_COUNT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            currentInput = handle[KEY_INPUT] ?: "",
            isComplete = handle[KEY_COMPLETE] ?: false,
            pendingAdvance = handle[KEY_PENDING_ADVANCE] ?: false,
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L
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
        const val KEY_COUNTRY_CODES = "currencies_country_codes"
        const val KEY_CURRENT_INDEX = "currencies_current_index"
        const val KEY_ANSWERED = "currencies_answered_codes"
        const val KEY_INCORRECT_COUNT = "currencies_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "currencies_incorrect_strings"
        const val KEY_INPUT = "currencies_current_input"
        const val KEY_COMPLETE = "currencies_complete"
        const val KEY_PENDING_ADVANCE = "currencies_pending_advance"
        const val KEY_ELAPSED_MILLIS = "currencies_elapsed_millis"
        const val KEY_RESULT_ID = "currencies_result_id"
    }
}
