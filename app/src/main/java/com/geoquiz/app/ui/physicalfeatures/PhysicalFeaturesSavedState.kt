package com.geoquiz.app.ui.physicalfeatures

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.model.Difficulty

/**
 * Keeps a running round in the screen's [SavedStateHandle] (task 7.2, D37): survives process
 * death and activity recreation; rotation keeps the same ViewModel. No Room resume save, the
 * same pattern every new mode uses.
 *
 * Like Country silhouettes, the round's features are themselves randomly chosen
 * ([com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesRound]), so their ids (in round
 * order) are stored here too, not rebuilt from the category filter alone. The current feature's
 * Easy question (if any) is not restored byte-for-byte: a recreated screen asks about the same
 * target but may show a freshly drawn set of options.
 */
class PhysicalFeaturesSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val featureIds: List<String>,
        val currentIndex: Int,
        val answeredIds: List<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val currentInput: String,
        val isComplete: Boolean,
        val pendingAdvance: Boolean,
        val elapsedMillis: Long
    ) {
        /** Rebuilds the round's features from [allFeatures], in the saved order. Unknown ids are
         * skipped (a data update removed one, or a decode error). */
        fun features(allFeatures: List<PhysicalFeature>): List<PhysicalFeature> {
            val byId = allFeatures.associateBy { it.id }
            return featureIds.mapNotNull { byId[it] }
        }
    }

    val hasSnapshot: Boolean get() = handle.contains(KEY_FEATURE_IDS)

    fun save(state: PhysicalFeaturesRoundState, elapsedMillis: Long) {
        handle[KEY_FEATURE_IDS] = ArrayList(state.features.map { it.id })
        handle[KEY_CURRENT_INDEX] = state.currentIndex
        handle[KEY_ANSWERED] = ArrayList(state.answeredIds)
        handle[KEY_INCORRECT_COUNT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_PENDING_ADVANCE] = state.pendingAdvance
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    /** A keystroke only changes the input (same nit Country silhouettes avoids, task 5.1
     * review): no full [save]/commit path, so the timer ticker never restarts on every key. */
    fun saveInput(value: String) {
        handle[KEY_INPUT] = value
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            featureIds = handle.get<ArrayList<String>>(KEY_FEATURE_IDS).orEmpty(),
            currentIndex = handle[KEY_CURRENT_INDEX] ?: 0,
            answeredIds = handle.get<ArrayList<String>>(KEY_ANSWERED).orEmpty(),
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
        const val KEY_DIFFICULTY = "physical_features_difficulty"
        const val KEY_FEATURE_IDS = "physical_features_ids"
        const val KEY_CURRENT_INDEX = "physical_features_current_index"
        const val KEY_ANSWERED = "physical_features_answered_ids"
        const val KEY_INCORRECT_COUNT = "physical_features_incorrect_count"
        const val KEY_INCORRECT_STRINGS = "physical_features_incorrect_strings"
        const val KEY_INPUT = "physical_features_current_input"
        const val KEY_COMPLETE = "physical_features_complete"
        const val KEY_PENDING_ADVANCE = "physical_features_pending_advance"
        const val KEY_ELAPSED_MILLIS = "physical_features_elapsed_millis"
        const val KEY_RESULT_ID = "physical_features_result_id"
    }
}
