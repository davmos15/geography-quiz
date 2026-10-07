package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.mode.ChoiceOption
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.mode.ChoiceQuestion
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
 *
 * Easy quizzes also keep their multiple-choice progress: the order still to be asked, the missed
 * codes and the question on screen (target, options with their labels, and the prompt), so a
 * recreated screen shows the same question. A pick's feedback is not kept: QuizViewModel treats
 * a question whose target is already answered or missed as done and moves on.
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
        val elapsedMillis: Long,
        /** Easy only: codes still to be asked after [choice], in order. */
        val remainingOrder: List<String> = emptyList(),
        /** Easy only: codes answered wrongly. */
        val missedCodes: List<String> = emptyList(),
        /** Easy only: the question on screen. */
        val choice: ChoiceQuestion? = null
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
                incorrectGuessStrings = incorrectGuessStrings,
                choice = choice,
                remainingOrder = remainingOrder.filter { it in quizCodes },
                missedCountries = missedCodes.filter { it in quizCodes }.toSet()
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
        saveChoice(state)
    }

    /** Writes the Easy progress, or removes it for a Normal or Hard quiz (nothing to keep). */
    private fun saveChoice(state: QuizState) {
        val hasChoiceProgress = state.choice != null || state.remainingOrder.isNotEmpty() ||
            state.missedCountries.isNotEmpty()
        if (!hasChoiceProgress) {
            CHOICE_KEYS.forEach { handle.remove<Any?>(it) }
            return
        }
        handle[KEY_CHOICE_REMAINING] = ArrayList(state.remainingOrder)
        handle[KEY_CHOICE_MISSED] = ArrayList(state.missedCountries)
        val choice = state.choice
        handle[KEY_CHOICE_TARGET] = choice?.targetCode
        handle[KEY_CHOICE_OPTION_CODES] = choice?.options?.mapTo(ArrayList()) { it.code }
        handle[KEY_CHOICE_OPTION_LABELS] = choice?.options?.mapTo(ArrayList()) { it.label }
        handle[KEY_CHOICE_PROMPT_KIND] = when (choice?.prompt) {
            is ChoicePrompt.InSet -> PROMPT_IN_SET
            is ChoicePrompt.CapitalOf -> PROMPT_CAPITAL_OF
            is ChoicePrompt.FlagOf -> PROMPT_FLAG_OF
            null -> null
        }
        handle[KEY_CHOICE_PROMPT_VALUE] = when (val prompt = choice?.prompt) {
            is ChoicePrompt.InSet -> prompt.categoryName
            is ChoicePrompt.CapitalOf -> prompt.countryName
            is ChoicePrompt.FlagOf -> prompt.code
            null -> null
        }
    }

    /** The saved Easy question, or null if there is none or it does not read back whole. */
    private fun restoreChoice(): ChoiceQuestion? {
        val target = handle.get<String>(KEY_CHOICE_TARGET) ?: return null
        val codes = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_CODES) ?: return null
        val labels = handle.get<ArrayList<String>>(KEY_CHOICE_OPTION_LABELS) ?: return null
        val value = handle.get<String>(KEY_CHOICE_PROMPT_VALUE) ?: return null
        val prompt = when (handle.get<String>(KEY_CHOICE_PROMPT_KIND)) {
            PROMPT_IN_SET -> ChoicePrompt.InSet(value)
            PROMPT_CAPITAL_OF -> ChoicePrompt.CapitalOf(value)
            PROMPT_FLAG_OF -> ChoicePrompt.FlagOf(value)
            else -> return null
        }
        if (codes.size != labels.size || target !in codes) return null
        return ChoiceQuestion(
            targetCode = target,
            options = codes.zip(labels) { code, label -> ChoiceOption(code, label) },
            prompt = prompt
        )
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
            elapsedMillis = handle[KEY_ELAPSED_MILLIS] ?: 0L,
            remainingOrder = handle.get<ArrayList<String>>(KEY_CHOICE_REMAINING).orEmpty(),
            missedCodes = handle.get<ArrayList<String>>(KEY_CHOICE_MISSED).orEmpty(),
            choice = restoreChoice()
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
        const val KEY_CHOICE_REMAINING = "quiz_choice_remaining"
        const val KEY_CHOICE_MISSED = "quiz_choice_missed"
        const val KEY_CHOICE_TARGET = "quiz_choice_target"
        const val KEY_CHOICE_OPTION_CODES = "quiz_choice_option_codes"
        const val KEY_CHOICE_OPTION_LABELS = "quiz_choice_option_labels"
        const val KEY_CHOICE_PROMPT_KIND = "quiz_choice_prompt_kind"
        const val KEY_CHOICE_PROMPT_VALUE = "quiz_choice_prompt_value"

        private val CHOICE_KEYS = listOf(
            KEY_CHOICE_REMAINING, KEY_CHOICE_MISSED, KEY_CHOICE_TARGET, KEY_CHOICE_OPTION_CODES,
            KEY_CHOICE_OPTION_LABELS, KEY_CHOICE_PROMPT_KIND, KEY_CHOICE_PROMPT_VALUE
        )
        private const val PROMPT_IN_SET = "in_set"
        private const val PROMPT_CAPITAL_OF = "capital_of"
        private const val PROMPT_FLAG_OF = "flag_of"
    }
}
