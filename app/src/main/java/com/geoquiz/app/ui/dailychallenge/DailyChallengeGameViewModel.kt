package com.geoquiz.app.ui.dailychallenge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeDate
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGenerator
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeQuestionKind
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.domain.usecase.ValidateCapitalAnswerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One daily challenge round (task 8.1): its own screen and ViewModel (D37), no Room resume save.
 * The day's 10 questions come from [DailyChallengeGenerator], seeded from [DailyChallengeDate]'s
 * local date (or the date a restored round actually started on, see [DailyChallengeSavedState]) -
 * never persisted themselves, since regenerating them from the date is both simpler and exactly
 * as correct (the generator is a pure function). The round is always [Difficulty.NORMAL] (task
 * 8.1 brief: "the daily challenge always runs at one fixed tier").
 *
 * Every question gets exactly one attempt: a typed kind ([onSubmitTyped]) is judged with the
 * existing [ValidateAnswerUseCase]/[ValidateCapitalAnswerUseCase] against a synthetic one-item
 * [QuizState] (so "correct" only ever means "matches *this* question's target", never any other
 * item asked today); a near miss ([AnswerResult.NearMiss]) is the one exception that does not
 * consume the attempt, since its whole point is "not accepted yet - try again". A choice kind
 * ([onChoiceSelected]) is judged by comparing the picked code to the question's own pre-built
 * [com.geoquiz.app.domain.mode.ChoiceQuestion.targetCode] directly. Either way, a brief feedback
 * pause ([FEEDBACK_MILLIS], the same pattern as Flag speed round) precedes moving on to the next
 * question, or finishing the round on the tenth.
 *
 * See [DailyChallengeScoring][com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeScoring]
 * for how a finished round packs into a plain [QuizState] for [CompleteQuizUseCase].
 *
 * Task 8.2's hard guard (D54, replacing the Setup screen's previous soft UI-only gate): a fresh
 * start (no saved-state snapshot) checks [QuizHistoryRepository.hasCompletedCategory] for this
 * round's date first and publishes [DailyChallengeUiState.AlreadyPlayed] instead of building a
 * round if it is already recorded. Restoring an in-progress round is never blocked this way, even
 * for a date not yet recorded (see `init` below).
 */
@HiltViewModel
class DailyChallengeGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val countryRepository: CountryRepository,
    private val validateAnswer: ValidateAnswerUseCase,
    private val validateCapitalAnswer: ValidateCapitalAnswerUseCase,
    private val completeQuiz: CompleteQuizUseCase,
    private val quizHistoryRepository: QuizHistoryRepository,
    clock: MonotonicClock
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyChallengeUiState>(DailyChallengeUiState.Loading)
    val uiState: StateFlow<DailyChallengeUiState> = _uiState.asStateFlow()

    private val _completion = MutableStateFlow<DailyChallengeCompletion?>(null)
    val completion: StateFlow<DailyChallengeCompletion?> = _completion.asStateFlow()

    private val savedState = DailyChallengeSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private var advanceJob: Job? = null
    private var completionJob: Job? = null

    /** The date this round was generated for; set once, before the first [commit]. */
    private lateinit var date: String
    private val category: QuizCategory get() = QuizCategory.DailyChallenge(date)

    /** See [com.geoquiz.app.ui.flagspeed.FlagSpeedGameViewModel]'s own field of this name. */
    private var backgroundedBeforeLoad = false

    init {
        viewModelScope.launch {
            val snapshot = savedState.restore()
            date = snapshot?.date ?: DailyChallengeDate.today()
            // The hard one-attempt-per-day guard (task 8.2, D54) only applies to a *fresh* start:
            // restoring an in-progress saved round (snapshot != null) must always continue, even
            // for a date not yet recorded (it is only recorded once the round finishes).
            if (snapshot == null && quizHistoryRepository.hasCompletedCategory(
                    quizMode = DailyChallengeGameMode.MODE_ID,
                    categoryType = category.typeKey,
                    categoryValue = date
                )
            ) {
                _uiState.value = DailyChallengeUiState.AlreadyPlayed
                return@launch
            }
            val allCountries = countryRepository.getAllCountries().first()
            val questions = DailyChallengeGenerator.generate(date, allCountries)
            if (questions.isEmpty()) {
                _uiState.value = DailyChallengeUiState.Unavailable
                return@launch
            }
            if (snapshot != null) {
                quizTimer.restore(snapshot.elapsedMillis)
                commit(snapshot.toState(questions))
                finishIfComplete()
            } else {
                commit(DailyChallengeRoundState(questions = questions, paused = backgroundedBeforeLoad))
            }
        }
    }

    private fun activeState(): DailyChallengeRoundState? = (_uiState.value as? DailyChallengeUiState.Active)?.state

    private fun commit(state: DailyChallengeRoundState) {
        _uiState.value = DailyChallengeUiState.Active(state)
        if (state.isComplete || state.paused) quizTimer.pause() else quizTimer.resume()
        savedState.save(date, state, quizTimer.snapshot())
    }

    private fun blocked(state: DailyChallengeRoundState) = state.isComplete || state.paused || state.pendingAdvance

    fun onInputChange(value: String) {
        val state = activeState() ?: return
        if (blocked(state)) return
        commit(state.copy(currentInput = value, lastAnswerResult = AnswerResult.None))
    }

    /** The current question's one attempt, for [DailyChallengeQuestionKind.TYPED_COUNTRY]/[DailyChallengeQuestionKind.TYPED_CAPITAL]. */
    fun onSubmitTyped() {
        val state = activeState() ?: return
        if (blocked(state)) return
        val question = state.currentQuestion ?: return
        val kind = question.kind
        if (kind != DailyChallengeQuestionKind.TYPED_COUNTRY && kind != DailyChallengeQuestionKind.TYPED_CAPITAL) return
        val input = state.currentInput

        viewModelScope.launch {
            // A synthetic quiz of exactly this one target: "correct" can only ever mean this
            // question's own target, never a different item asked earlier or later today.
            val singleItemState = QuizState(quiz = Quiz(category = category, countries = listOf(question.target)))
            val result = if (kind == DailyChallengeQuestionKind.TYPED_COUNTRY) {
                validateAnswer(input, singleItemState, allowFuzzy = Difficulty.NORMAL.allowsTypos)
            } else {
                validateCapitalAnswer(input, singleItemState, allowFuzzy = Difficulty.NORMAL.allowsTypos)
            }
            val latest = activeState() ?: return@launch
            when (result) {
                // Not accepted yet: keep the input so the player can correct it (same meaning as
                // everywhere else NearMiss is used). Does not consume the attempt.
                is AnswerResult.NearMiss -> commit(latest.copy(lastAnswerResult = result))
                is AnswerResult.Correct -> recordAttempt(latest, question.target.code, isCorrect = true, typedResult = result)
                // AlreadyAnswered should not happen (the synthetic quiz never marks anything
                // answered); treated the same as Incorrect so the round can never get stuck.
                AnswerResult.Incorrect, AnswerResult.AlreadyAnswered ->
                    recordAttempt(latest, question.target.code, isCorrect = false, typedResult = AnswerResult.Incorrect)
                AnswerResult.None -> Unit
            }
        }
    }

    /** The current question's one attempt, for [DailyChallengeQuestionKind.CHOICE_FLAG]/[DailyChallengeQuestionKind.CHOICE_CAPITAL]. */
    fun onChoiceSelected(code: String) {
        val state = activeState() ?: return
        if (blocked(state)) return
        val question = state.currentQuestion ?: return
        val choice = question.choice ?: return
        val isCorrect = code == choice.targetCode
        recordAttempt(state, question.target.code, isCorrect, choiceFeedback = ChoiceFeedback(code, isCorrect))
    }

    private fun recordAttempt(
        state: DailyChallengeRoundState,
        targetCode: String,
        isCorrect: Boolean,
        typedResult: AnswerResult = AnswerResult.None,
        choiceFeedback: ChoiceFeedback? = null
    ) {
        val updated = state.copy(
            askedCodes = state.askedCodes + targetCode,
            correctCodes = if (isCorrect) state.correctCodes + targetCode else state.correctCodes,
            incorrectGuesses = if (isCorrect) state.incorrectGuesses else state.incorrectGuesses + 1,
            currentInput = "",
            lastAnswerResult = typedResult,
            choiceFeedback = choiceFeedback,
            pendingAdvance = true
        )
        commit(updated)
        scheduleAdvance()
    }

    private fun scheduleAdvance() {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(FEEDBACK_MILLIS)
            advanceAfterFeedback()
        }
    }

    private fun advanceAfterFeedback() {
        val state = activeState() ?: return
        if (state.isComplete) return
        val nowComplete = state.askedCodes.size >= state.questions.size
        commit(
            state.copy(
                lastAnswerResult = AnswerResult.None,
                choiceFeedback = null,
                pendingAdvance = false,
                isComplete = nowComplete
            )
        )
        finishIfComplete()
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        advanceJob?.cancel()
        commit(state.copy(isComplete = true, gaveUp = true, pendingAdvance = false, choiceFeedback = null))
        finishIfComplete()
    }

    /** Pauses on backgrounding, like the classic quiz and every other new mode (D37). */
    fun onBackgrounded() {
        val state = activeState()
        if (state == null) {
            backgroundedBeforeLoad = true
            return
        }
        if (state.isComplete || state.paused) return
        advanceJob?.cancel()
        commit(state.copy(paused = true))
    }

    fun onResume() {
        backgroundedBeforeLoad = false
        val state = activeState() ?: return
        if (!state.paused) return
        val resumed = state.copy(paused = false)
        commit(resumed)
        // Feedback was showing when backgrounded: re-arm the delayed advance (same catch-up
        // spirit as Flag speed round's onResume).
        if (resumed.pendingAdvance) scheduleAdvance()
    }

    /**
     * Records the round once ([CompleteQuizUseCase]) and publishes [completion]. See
     * [com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeScoring] for how the round is
     * packed into a [QuizState].
     */
    private fun finishIfComplete() {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val askedCountries = state.questions.take(state.askedCodes.size).map { it.target }
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = askedCountries),
            answeredCountries = state.correctCodes,
            incorrectGuesses = state.incorrectGuesses,
            isComplete = true
        )
        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = DailyChallengeGameMode.MODE_ID,
            routeCategoryType = category.typeKey,
            routeCategoryValue = category.valueKey,
            difficulty = Difficulty.NORMAL,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = DailyChallengeCompletion(resultId, DailyChallengeCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = DailyChallengeCompletion.Step.DONE)
    }

    companion object {
        /** How long a typed or picked result stays visible before the next question. */
        const val FEEDBACK_MILLIS = 900L
    }
}
