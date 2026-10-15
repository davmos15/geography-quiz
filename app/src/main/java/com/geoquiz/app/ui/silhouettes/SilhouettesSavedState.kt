package com.geoquiz.app.ui.silhouettes

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty

/**
 * Keeps a running Country silhouettes round in the screen's [SavedStateHandle] (task 5.1, D37):
 * survives process death and activity recreation; rotation keeps the same ViewModel.
 *
 * Unlike the classic quiz, the round's countries are themselves randomly chosen
 * ([com.geoquiz.app.domain.mode.silhouettes.SilhouettesQuestionGenerator]), so their codes (in
 * round order) are stored here too, not rebuilt from the category. The current silhouette's Easy
 * question (if any) is not restored byte-for-byte: a recreated screen asks about the same target
 * but may show a freshly drawn set of options.
 */
class SilhouettesSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val countryCodes: List<String>,
        val currentIndex: Int,
        val answeredCodes: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val hintsUsed: List<HintType>,
        val hintsUsedForCurrent: Set<HintType>,
        val wrongGuessCodesForCurrent: List<String>,
        val wrongGuessNamesForCurrent: List<String>,
        val currentInput: String,
        val isComplete: Boolean,
        val showNeighbours: Boolean,
        /** True if the round died mid-pause (task 5.1 review, S2): restore advances immediately. */
        val pendingAdvance: Boolean,
        val elapsedMillis: Long
    ) {
        /** Rebuilds the round's countries from [allCountries], in the saved order. Unknown codes are skipped. */
        fun countries(allCountries: List<Country>): List<Country> {
            val byCode = allCountries.associateBy { it.code }
            return countryCodes.mapNotNull { byCode[it] }
        }
    }

    val hasSnapshot: Boolean get() = handle.contains(KEY_COUNTRY_CODES)

    fun save(state: SilhouetteState, elapsedMillis: Long) {
        handle[KEY_COUNTRY_CODES] = ArrayList(state.countries.map { it.code })
        handle[KEY_CURRENT_INDEX] = state.currentIndex
        handle[KEY_ANSWERED] = ArrayList(state.answeredCodes)
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_HINTS_USED] = ArrayList(state.hintsUsed.map { it.name })
        handle[KEY_HINTS_USED_CURRENT] = ArrayList(state.hintsUsedForCurrent.map { it.name })
        handle[KEY_WRONG_CODES_CURRENT] = ArrayList(state.wrongGuessCodesForCurrent)
        handle[KEY_WRONG_NAMES_CURRENT] = ArrayList(state.wrongGuessNamesForCurrent)
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_SHOW_NEIGHBOURS] = state.showNeighbours
        handle[KEY_PENDING_ADVANCE] = state.pendingAdvance
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    /**
     * A keystroke only changes the input (task 5.1 review nit): writing every key on every
     * keystroke, or restarting the timer ticker via the full [save]/commit path, is wasteful.
     */
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
            hintsUsed = handle.get<ArrayList<String>>(KEY_HINTS_USED).orEmpty().mapNotNull(::hintTypeOrNull),
            hintsUsedForCurrent = handle.get<ArrayList<String>>(KEY_HINTS_USED_CURRENT).orEmpty()
                .mapNotNull(::hintTypeOrNull).toSet(),
            wrongGuessCodesForCurrent = handle.get<ArrayList<String>>(KEY_WRONG_CODES_CURRENT).orEmpty(),
            wrongGuessNamesForCurrent = handle.get<ArrayList<String>>(KEY_WRONG_NAMES_CURRENT).orEmpty(),
            currentInput = handle[KEY_INPUT] ?: "",
            isComplete = handle[KEY_COMPLETE] ?: false,
            showNeighbours = handle[KEY_SHOW_NEIGHBOURS] ?: false,
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

    private fun hintTypeOrNull(name: String): HintType? = HintType.entries.find { it.name == name }

    companion object {
        const val KEY_DIFFICULTY = "silhouettes_difficulty"
        const val KEY_COUNTRY_CODES = "silhouettes_country_codes"
        const val KEY_CURRENT_INDEX = "silhouettes_current_index"
        const val KEY_ANSWERED = "silhouettes_answered_codes"
        const val KEY_INCORRECT_COUNT = "silhouettes_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "silhouettes_incorrect_strings"
        const val KEY_HINTS_USED = "silhouettes_hints_used"
        const val KEY_HINTS_USED_CURRENT = "silhouettes_hints_used_current"
        const val KEY_WRONG_CODES_CURRENT = "silhouettes_wrong_codes_current"
        const val KEY_WRONG_NAMES_CURRENT = "silhouettes_wrong_names_current"
        const val KEY_INPUT = "silhouettes_current_input"
        const val KEY_COMPLETE = "silhouettes_complete"
        const val KEY_SHOW_NEIGHBOURS = "silhouettes_show_neighbours"
        const val KEY_PENDING_ADVANCE = "silhouettes_pending_advance"
        const val KEY_ELAPSED_MILLIS = "silhouettes_elapsed_millis"
        const val KEY_RESULT_ID = "silhouettes_result_id"
    }
}
