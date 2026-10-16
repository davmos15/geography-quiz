package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestion
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.ChoiceFeedback

/**
 * Keeps a daily challenge round's progress in the screen's [SavedStateHandle] (D37-style: no Room
 * resume save, so this is the only thing process death or "Don't keep activities" restores from).
 *
 * The day's 10 [DailyChallengeQuestion]s are not saved directly: [DailyChallengeGenerator][
 * com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGenerator] is a pure function of
 * [Snapshot.date] (and the country list, which is static), so [DailyChallengeGameViewModel]
 * rebuilds the exact same 10 questions, in the exact same order, from [Snapshot.date] alone - the
 * saved [Snapshot.date] is the one taken when the round started, so a round that happens to be
 * mid-play right at midnight keeps playing the date it started on, rather than switching to a
 * fresh "today" partway through.
 *
 * [AnswerResult]'s free-form text (e.g. the correct country's name) is not persisted either: only
 * whether the last typed attempt was correct ([Snapshot.lastWasCorrect]) is kept, and
 * [Snapshot.toState] rebuilds [AnswerResult.Correct] from the just-answered question's own target
 * name (always known, since [DailyChallengeRoundState.askedCodes] and [questions] agree).
 */
class DailyChallengeSavedState(private val handle: SavedStateHandle) {

    data class Snapshot(
        val date: String,
        val askedCodes: List<String>,
        val correctCodes: Set<String>,
        val incorrectGuesses: Int,
        val currentInput: String,
        val pendingAdvance: Boolean,
        /** Null when no typed feedback is pending; true/false for the last typed attempt's result. */
        val lastWasCorrect: Boolean?,
        val choiceFeedbackCode: String?,
        val choiceFeedbackCorrect: Boolean?,
        val isComplete: Boolean,
        val gaveUp: Boolean,
        val elapsedMillis: Long
    ) {
        fun toState(questions: List<DailyChallengeQuestion>): DailyChallengeRoundState {
            val justAnsweredTarget = questions.getOrNull(askedCodes.size - 1)?.target
            val lastAnswerResult = when {
                lastWasCorrect == true && justAnsweredTarget != null -> AnswerResult.Correct(justAnsweredTarget.name)
                lastWasCorrect == false -> AnswerResult.Incorrect
                else -> AnswerResult.None
            }
            val choiceFeedback = choiceFeedbackCode?.let { code ->
                choiceFeedbackCorrect?.let { isCorrect -> ChoiceFeedback(code, isCorrect) }
            }
            return DailyChallengeRoundState(
                questions = questions,
                askedCodes = askedCodes,
                correctCodes = correctCodes,
                incorrectGuesses = incorrectGuesses,
                currentInput = currentInput,
                lastAnswerResult = lastAnswerResult,
                choiceFeedback = choiceFeedback,
                pendingAdvance = pendingAdvance,
                isComplete = isComplete,
                gaveUp = gaveUp,
                // A restored, unfinished round comes back paused, like every other new mode (D37).
                paused = !isComplete
            )
        }
    }

    /** True once [save] has run for this round (also after process death). */
    val hasSnapshot: Boolean get() = handle.contains(KEY_DATE)

    fun save(date: String, state: DailyChallengeRoundState, elapsedMillis: Long) {
        handle[KEY_DATE] = date
        handle[KEY_ASKED] = ArrayList(state.askedCodes)
        handle[KEY_CORRECT] = ArrayList(state.correctCodes)
        handle[KEY_INCORRECT] = state.incorrectGuesses
        handle[KEY_INPUT] = state.currentInput
        handle[KEY_PENDING_ADVANCE] = state.pendingAdvance
        handle[KEY_LAST_CORRECT] = when (state.lastAnswerResult) {
            is AnswerResult.Correct -> true
            is AnswerResult.Incorrect -> false
            else -> null
        }
        handle[KEY_CHOICE_CODE] = state.choiceFeedback?.selectedCode
        handle[KEY_CHOICE_CORRECT] = state.choiceFeedback?.isCorrect
        handle[KEY_COMPLETE] = state.isComplete
        handle[KEY_GAVE_UP] = state.gaveUp
        handle[KEY_ELAPSED_MILLIS] = elapsedMillis
    }

    fun restore(): Snapshot? {
        if (!hasSnapshot) return null
        val date = handle.get<String>(KEY_DATE) ?: return null
        return Snapshot(
            date = date,
            askedCodes = handle.get<ArrayList<String>>(KEY_ASKED).orEmpty(),
            correctCodes = handle.get<ArrayList<String>>(KEY_CORRECT).orEmpty().toSet(),
            incorrectGuesses = handle[KEY_INCORRECT] ?: 0,
            currentInput = handle[KEY_INPUT] ?: "",
            pendingAdvance = handle[KEY_PENDING_ADVANCE] ?: false,
            lastWasCorrect = handle[KEY_LAST_CORRECT],
            choiceFeedbackCode = handle[KEY_CHOICE_CODE],
            choiceFeedbackCorrect = handle[KEY_CHOICE_CORRECT],
            isComplete = handle[KEY_COMPLETE] ?: false,
            gaveUp = handle[KEY_GAVE_UP] ?: false,
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

    companion object {
        const val KEY_DATE = "daily_challenge_date"
        const val KEY_ASKED = "daily_challenge_asked"
        const val KEY_CORRECT = "daily_challenge_correct"
        const val KEY_INCORRECT = "daily_challenge_incorrect"
        const val KEY_INPUT = "daily_challenge_input"
        const val KEY_PENDING_ADVANCE = "daily_challenge_pending_advance"
        const val KEY_LAST_CORRECT = "daily_challenge_last_correct"
        const val KEY_CHOICE_CODE = "daily_challenge_choice_code"
        const val KEY_CHOICE_CORRECT = "daily_challenge_choice_correct"
        const val KEY_COMPLETE = "daily_challenge_complete"
        const val KEY_GAVE_UP = "daily_challenge_gave_up"
        const val KEY_ELAPSED_MILLIS = "daily_challenge_elapsed_millis"
        const val KEY_RESULT_ID = "daily_challenge_result_id"
    }
}
