package com.geoquiz.app.ui.flagspeed

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty

/**
 * Keeps a flag speed round's progress in the screen's [SavedStateHandle] (D37-style: no Room
 * resume save, so this is the only thing process death or "Don't keep activities" restores from).
 *
 * The current [com.geoquiz.app.domain.mode.ChoiceQuestion] is not saved directly: there is nothing
 * to serialise that matters - [FlagSpeedGameViewModel] rebuilds the same question from
 * [Snapshot.lastDrawSetId]/[Snapshot.lastDrawTargetCode] (same target, same lookalike set,
 * freshly shuffled option order). The shuffle changing on restore is harmless: no answer is
 * recorded until a pick, and [Snapshot.feedbackSelectedCode] still matches one of the rebuilt
 * options by code regardless of their on-screen order.
 */
class FlagSpeedSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val askedCodes: List<String>,
        val correctCodes: Set<String>,
        val incorrectGuesses: Int,
        val incorrectGuessStrings: List<String>,
        val liveCombo: Int,
        val longestCombo: Int,
        val encounteredSetIds: Set<String>,
        val lastDrawSetId: String?,
        val lastDrawTargetCode: String?,
        val isComplete: Boolean,
        val gaveUp: Boolean,
        val pendingAdvance: Boolean,
        val feedbackSelectedCode: String?,
        val feedbackIsCorrect: Boolean?,
        val elapsedMillis: Long
    ) {
        fun toState(difficulty: Difficulty): FlagSpeedRoundState = FlagSpeedRoundState(
            difficulty = difficulty,
            question = null,
            feedback = feedbackSelectedCode?.let { code -> feedbackIsCorrect?.let { ChoiceFeedback(code, it) } },
            askedCodes = askedCodes,
            correctCodes = correctCodes,
            incorrectGuesses = incorrectGuesses,
            incorrectGuessStrings = incorrectGuessStrings,
            liveCombo = liveCombo,
            longestCombo = longestCombo,
            encounteredSetIds = encounteredSetIds,
            lastDrawSetId = lastDrawSetId,
            lastDrawTargetCode = lastDrawTargetCode,
            isComplete = isComplete,
            gaveUp = gaveUp,
            pendingAdvance = pendingAdvance,
            // A restored, unfinished round comes back paused, like every other new mode (D37).
            paused = !isComplete
        )
    }

    /** True once [save] has run for this round (also after process death). */
    val hasSnapshot: Boolean get() = handle.contains(KEY_ASKED)

    fun save(state: FlagSpeedRoundState, elapsedMillis: Long) {
        handle[KEY_ASKED] = ArrayList(state.askedCodes)
        handle[KEY_CORRECT] = ArrayList(state.correctCodes)
        handle[KEY_INCORRECT] = state.incorrectGuesses
        handle[KEY_INCORRECT_STRINGS] = ArrayList(state.incorrectGuessStrings)
        handle[KEY_LIVE_COMBO] = state.liveCombo
        handle[KEY_LONGEST_COMBO] = state.longestCombo
        handle[KEY_ENCOUNTERED] = ArrayList(state.encounteredSetIds)
        handle[KEY_LAST_SET] = state.lastDrawSetId
        handle[KEY_LAST_TARGET] = state.lastDrawTargetCode
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_GAVE_UP] = state.gaveUp
        handle[KEY_PENDING_ADVANCE] = state.pendingAdvance
        handle[KEY_FEEDBACK_CODE] = state.feedback?.selectedCode
        handle[KEY_FEEDBACK_CORRECT] = state.feedback?.isCorrect
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        return Snapshot(
            askedCodes = handle.get<ArrayList<String>>(KEY_ASKED).orEmpty(),
            correctCodes = handle.get<ArrayList<String>>(KEY_CORRECT).orEmpty().toSet(),
            incorrectGuesses = handle[KEY_INCORRECT] ?: 0,
            incorrectGuessStrings = handle.get<ArrayList<String>>(KEY_INCORRECT_STRINGS).orEmpty(),
            liveCombo = handle[KEY_LIVE_COMBO] ?: 0,
            longestCombo = handle[KEY_LONGEST_COMBO] ?: 0,
            encounteredSetIds = handle.get<ArrayList<String>>(KEY_ENCOUNTERED).orEmpty().toSet(),
            lastDrawSetId = handle[KEY_LAST_SET],
            lastDrawTargetCode = handle[KEY_LAST_TARGET],
            isComplete = handle[KEY_COMPLETE] ?: false,
            gaveUp = handle[KEY_GAVE_UP] ?: false,
            pendingAdvance = handle[KEY_PENDING_ADVANCE] ?: false,
            feedbackSelectedCode = handle[KEY_FEEDBACK_CODE],
            feedbackIsCorrect = handle[KEY_FEEDBACK_CORRECT],
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L
        )
    }

    /**
     * The id of this round's [com.geoquiz.app.domain.model.CompletedQuiz]. Set before the
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
        const val KEY_DIFFICULTY = "flag_speed_difficulty"
        const val KEY_ASKED = "flag_speed_asked"
        const val KEY_CORRECT = "flag_speed_correct"
        const val KEY_INCORRECT = "flag_speed_incorrect"
        const val KEY_INCORRECT_STRINGS = "flag_speed_incorrect_strings"
        const val KEY_LIVE_COMBO = "flag_speed_live_combo"
        const val KEY_LONGEST_COMBO = "flag_speed_longest_combo"
        const val KEY_ENCOUNTERED = "flag_speed_encountered"
        const val KEY_LAST_SET = "flag_speed_last_set"
        const val KEY_LAST_TARGET = "flag_speed_last_target"
        const val KEY_COMPLETE = "flag_speed_complete"
        const val KEY_GAVE_UP = "flag_speed_gave_up"
        const val KEY_PENDING_ADVANCE = "flag_speed_pending_advance"
        const val KEY_FEEDBACK_CODE = "flag_speed_feedback_code"
        const val KEY_FEEDBACK_CORRECT = "flag_speed_feedback_correct"
        const val KEY_ELAPSED_MILLIS = "flag_speed_elapsed_millis"
        const val KEY_RESULT_ID = "flag_speed_result_id"
    }
}
