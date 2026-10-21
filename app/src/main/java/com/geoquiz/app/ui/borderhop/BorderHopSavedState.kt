package com.geoquiz.app.ui.borderhop

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.QuizCategory

/**
 * Keeps Border hop's progress in the screen's [SavedStateHandle] (D37: no Room resume save, so
 * this is the only thing process death or "Don't keep activities" restores from). The puzzle
 * itself (start, target and par) is fixed for the round, so it is saved alongside the route
 * rather than regenerated; regenerating on restore could hand back a different puzzle. Easy's
 * options are saved too, so a restore never reshuffles them (or hands back a wrongly picked one
 * as fresh). Transient feedback (the feedback line, the wrong-guess flash, suggestions) is not
 * kept.
 */
class BorderHopSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val startCode: String,
        val targetCode: String,
        val parHops: Int,
        val path: List<String>,
        val wrongGuesses: List<String>,
        val wrongPairs: List<String>,
        val hintsUsed: List<String>,
        val revealedHopCode: String?,
        val isComplete: Boolean,
        val gaveUp: Boolean,
        val outOfGuesses: Boolean,
        val input: String,
        val options: List<String>,
        val wrongPicks: List<String>,
        val correctPick: String?,
        val elapsedMillis: Long
    ) {
        fun toState(category: QuizCategory, difficulty: Difficulty): BorderHopState = BorderHopState(
            category = category,
            difficulty = difficulty,
            startCode = startCode,
            targetCode = targetCode,
            parHops = parHops,
            path = path,
            wrongGuesses = wrongGuesses,
            wrongPairs = wrongPairs.toSet(),
            hintsUsed = hintsUsed.mapNotNull { name -> HintType.entries.find { it.name == name } },
            revealedHopCode = revealedHopCode,
            isComplete = isComplete,
            gaveUp = gaveUp,
            outOfGuesses = outOfGuesses,
            // A restored, unfinished round comes back paused, like the classic quiz and the other
            // new modes: the player resumes deliberately.
            paused = !isComplete,
            input = input,
            options = options,
            wrongPicks = wrongPicks.toSet(),
            correctPick = correctPick
        )
    }

    /** True once [save] has run for this round (also after process death). */
    val hasSnapshot: Boolean get() = handle.contains(KEY_START)

    fun save(state: BorderHopState, elapsedMillis: Long) {
        handle[KEY_START] = state.startCode
        handle[KEY_TARGET] = state.targetCode
        handle[KEY_PAR] = state.parHops
        handle[KEY_PATH] = ArrayList(state.path)
        handle[KEY_WRONG_GUESSES] = ArrayList(state.wrongGuesses)
        handle[KEY_WRONG_PAIRS] = ArrayList(state.wrongPairs)
        handle[KEY_HINTS_USED] = ArrayList(state.hintsUsed.map { it.name })
        handle[KEY_REVEALED_HOP] = state.revealedHopCode
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_GAVE_UP] = state.gaveUp
        handle[KEY_OUT_OF_GUESSES] = state.outOfGuesses
        handle[KEY_INPUT] = state.input
        handle[KEY_OPTIONS] = ArrayList(state.options)
        handle[KEY_WRONG_PICKS] = ArrayList(state.wrongPicks)
        handle[KEY_CORRECT_PICK] = state.correctPick
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    /** Typing changes only the field, so save just that rather than the whole snapshot. */
    fun saveInput(input: String) {
        handle[KEY_INPUT] = input
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        val start = handle.get<String>(KEY_START) ?: return null
        val target = handle.get<String>(KEY_TARGET) ?: return null
        val path = handle.get<ArrayList<String>>(KEY_PATH)?.takeIf { it.isNotEmpty() } ?: return null
        return Snapshot(
            startCode = start,
            targetCode = target,
            parHops = handle[KEY_PAR] ?: 1,
            path = path,
            wrongGuesses = handle.get<ArrayList<String>>(KEY_WRONG_GUESSES).orEmpty(),
            wrongPairs = handle.get<ArrayList<String>>(KEY_WRONG_PAIRS).orEmpty(),
            hintsUsed = handle.get<ArrayList<String>>(KEY_HINTS_USED).orEmpty(),
            revealedHopCode = handle[KEY_REVEALED_HOP],
            isComplete = handle[KEY_COMPLETE] ?: false,
            gaveUp = handle[KEY_GAVE_UP] ?: false,
            outOfGuesses = handle[KEY_OUT_OF_GUESSES] ?: false,
            input = handle[KEY_INPUT] ?: "",
            options = handle.get<ArrayList<String>>(KEY_OPTIONS).orEmpty(),
            wrongPicks = handle.get<ArrayList<String>>(KEY_WRONG_PICKS).orEmpty(),
            correctPick = handle[KEY_CORRECT_PICK],
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
        const val KEY_DIFFICULTY = "border_hop_difficulty"
        const val KEY_START = "border_hop_start"
        const val KEY_TARGET = "border_hop_target"
        const val KEY_PAR = "border_hop_par"
        const val KEY_PATH = "border_hop_path"
        const val KEY_WRONG_GUESSES = "border_hop_wrong_guesses"
        const val KEY_WRONG_PAIRS = "border_hop_wrong_pairs"
        const val KEY_HINTS_USED = "border_hop_hints_used"
        const val KEY_REVEALED_HOP = "border_hop_revealed_hop"
        const val KEY_COMPLETE = "border_hop_complete"
        const val KEY_GAVE_UP = "border_hop_gave_up"
        const val KEY_OUT_OF_GUESSES = "border_hop_out_of_guesses"
        const val KEY_INPUT = "border_hop_input"
        const val KEY_OPTIONS = "border_hop_options"
        const val KEY_WRONG_PICKS = "border_hop_wrong_picks"
        const val KEY_CORRECT_PICK = "border_hop_correct_pick"
        const val KEY_ELAPSED_MILLIS = "border_hop_elapsed_millis"
        const val KEY_RESULT_ID = "border_hop_result_id"
    }
}
