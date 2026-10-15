package com.geoquiz.app.ui.flagspeed

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.flagspeed.FlagLookalikeRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeSet
import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeVisibility
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedChoiceGenerator
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedDraw
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedGameMode
import com.geoquiz.app.domain.mode.flagspeed.FlagSpeedRoundDrawer
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One flag speed round (task 7.3, D52): its own screen and ViewModel (D37), no Room resume save.
 * The quiz category is fixed at [QuizCategory.AllCountries] - this mode's own setup screen offers
 * a difficulty only, no region or category filter. [FlagSpeedRoundDrawer] decides the next
 * (lookalike set, target) pair live, for as long as the 60-second countdown
 * ([FlagSpeedGameMode.ROUND_DURATION_SECONDS]) keeps running; [quizTimer] is the same
 * monotonic-clock stopwatch every other mode uses ([QuizTimer], `elapsedRealtime`-based), read as
 * a countdown by subtracting its elapsed seconds from the round length, so pausing on
 * backgrounding (D37) genuinely stops the clock rather than just hiding it.
 *
 * See [FlagSpeedUiState]/[FlagSpeedRoundState] for how a round's progress is kept, and
 * [com.geoquiz.app.domain.mode.flagspeed.FlagSpeedScoring] for how it is packed into a plain
 * [QuizState] for [CompleteQuizUseCase].
 */
@HiltViewModel
class FlagSpeedGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val countryRepository: CountryRepository,
    private val lookalikeRepository: FlagLookalikeRepository,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom
) : ViewModel() {

    /** No region/category filter makes sense for this mode (task 7.3 brief): always All countries. */
    private val category: QuizCategory = QuizCategory.AllCountries

    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<FlagSpeedUiState>(FlagSpeedUiState.Loading)
    val uiState: StateFlow<FlagSpeedUiState> = _uiState.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    /** Whole seconds left in the round, for the countdown display. Never negative. */
    private val _remainingSeconds = MutableStateFlow(FlagSpeedGameMode.ROUND_DURATION_SECONDS)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _completion = MutableStateFlow<FlagSpeedCompletion?>(null)
    val completion: StateFlow<FlagSpeedCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = FlagSpeedSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var advanceJob: Job? = null
    private var completionJob: Job? = null

    private var allCountriesByCode: Map<String, Country> = emptyMap()
    private var allSets: List<FlagLookalikeSet> = emptyList()
    private var setsById: Map<String, FlagLookalikeSet> = emptyMap()
    private var pool: List<FlagLookalikeSet> = emptyList()

    init {
        viewModelScope.launch {
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
            val difficulty = requested.takeIf { it in FlagSpeedGameMode.SPEC.supportedDifficulties } ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty

            val allCountries = countryRepository.getAllCountries().first()
            allCountriesByCode = allCountries.associateBy { it.code }
            allSets = lookalikeRepository.sets()
            setsById = allSets.associateBy { it.id }
            pool = FlagSpeedRoundDrawer.poolFor(allSets, difficulty)

            loadRound(difficulty)
        }
    }

    private fun loadRound(difficulty: Difficulty) {
        val snapshot = savedState.restore()
        if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            val state = snapshot.toState(difficulty)
            if (pool.isEmpty() && !state.isComplete) {
                _uiState.value = FlagSpeedUiState.Unavailable
                return
            }
            commit(state)
            when {
                state.isComplete -> finishIfComplete()
                state.pendingAdvance -> advanceAfterFeedback()
                else -> drawNextQuestion(state)
            }
            return
        }

        if (pool.isEmpty()) {
            _uiState.value = FlagSpeedUiState.Unavailable
            return
        }
        val state = FlagSpeedRoundState(difficulty = difficulty)
        commit(state)
        drawNextQuestion(state)
    }

    private fun activeState(): FlagSpeedRoundState? = (_uiState.value as? FlagSpeedUiState.Active)?.state

    private fun commit(state: FlagSpeedRoundState) {
        _uiState.value = FlagSpeedUiState.Active(state)
        syncTimer(state)
        savedState.save(state, quizTimer.snapshot())
    }

    /**
     * Keeps [quizTimer] running or paused to match [state], and re-arms the per-second ticker.
     * The round ends the instant the ticker reports the full [FlagSpeedGameMode.ROUND_DURATION_SECONDS]
     * elapsed, even mid-question - a genuinely counting-down clock, not just a display.
     */
    private fun syncTimer(state: FlagSpeedRoundState) {
        if (state.isComplete || state.paused) quizTimer.pause() else quizTimer.resume()
        tickerJob?.cancel()
        _remainingSeconds.value = remainingSecondsNow()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch {
                quizTimer.ticks().collect { elapsed ->
                    _remainingSeconds.value = (FlagSpeedGameMode.ROUND_DURATION_SECONDS - elapsed).coerceAtLeast(0)
                    if (elapsed >= FlagSpeedGameMode.ROUND_DURATION_SECONDS) endRound(gaveUp = false)
                }
            }
        }
    }

    private fun remainingSecondsNow(): Int =
        (FlagSpeedGameMode.ROUND_DURATION_SECONDS - quizTimer.elapsedSeconds()).coerceAtLeast(0)

    private fun drawNextQuestion(state: FlagSpeedRoundState) {
        val previous = state.lastDrawSetId?.let { setId ->
            state.lastDrawTargetCode?.let { target -> FlagSpeedDraw(setId, target) }
        }
        val draw = FlagSpeedRoundDrawer.next(pool, previous, random)
        if (draw == null) {
            // Defensive only: the pool was non-empty when the round started and never changes.
            endRound(gaveUp = false)
            return
        }
        val lookalikeSet = setsById[draw.setId] ?: return
        val target = allCountriesByCode[draw.targetCode] ?: return
        val otherMembers = lookalikeSet.countries
            .filter { it != draw.targetCode }
            .mapNotNull { allCountriesByCode[it] }
        val question = FlagSpeedChoiceGenerator.question(target, otherMembers, emptyList(), category, random)
        commit(
            state.copy(
                question = question,
                feedback = null,
                pendingAdvance = false,
                lastDrawSetId = draw.setId,
                lastDrawTargetCode = draw.targetCode,
                encounteredSetIds = state.encounteredSetIds + draw.setId
            )
        )
    }

    /** One of the question's options was picked. */
    fun onChoiceSelected(code: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.paused || state.pendingAdvance) return
        val question = state.question ?: return
        val targetCode = question.targetCode
        val isCorrect = code == targetCode

        val updated = if (isCorrect) {
            val newCombo = state.liveCombo + 1
            state.copy(
                feedback = ChoiceFeedback(code, true),
                askedCodes = state.askedCodes + targetCode,
                correctCodes = state.correctCodes + targetCode,
                liveCombo = newCombo,
                longestCombo = maxOf(state.longestCombo, newCombo),
                pendingAdvance = true
            )
        } else {
            val pickedLabel: String = question.option(code)?.label ?: code
            state.copy(
                feedback = ChoiceFeedback(code, false),
                askedCodes = state.askedCodes + targetCode,
                incorrectGuesses = state.incorrectGuesses + 1,
                incorrectGuessStrings = state.incorrectGuessStrings + pickedLabel,
                liveCombo = 0,
                pendingAdvance = true
            )
        }
        commit(updated)
        _feedbackEvents.tryEmit(if (isCorrect) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT)
        scheduleAdvance(CHOICE_FEEDBACK_MILLIS)
    }

    private fun scheduleAdvance(delayMillis: Long) {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(delayMillis)
            advanceAfterFeedback()
        }
    }

    /** Clears the just-answered question's feedback and either draws the next one or ends the round. */
    private fun advanceAfterFeedback() {
        val state = activeState() ?: return
        if (state.isComplete) return
        if (remainingSecondsNow() <= 0) {
            endRound(gaveUp = false)
            return
        }
        drawNextQuestion(state.copy(feedback = null, pendingAdvance = false, question = null))
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        endRound(gaveUp = true)
    }

    private fun endRound(gaveUp: Boolean) {
        val state = activeState() ?: return
        if (state.isComplete) return
        advanceJob?.cancel()
        val finished = state.copy(
            isComplete = true,
            gaveUp = gaveUp,
            feedback = null,
            pendingAdvance = false,
            question = null
        )
        commit(finished)
        finishIfComplete()
    }

    /** Pauses on backgrounding, like the classic quiz and the other new modes (D37). */
    fun onBackgrounded() {
        val state = activeState() ?: return
        if (state.isComplete || state.paused) return
        advanceJob?.cancel()
        commit(state.copy(paused = true))
    }

    fun onResume() {
        val state = activeState() ?: return
        if (!state.paused) return
        val resumed = state.copy(paused = false)
        commit(resumed)
        // A pick's feedback was showing when backgrounded: re-arm the delayed advance instead of
        // leaving the round stuck on an already-answered question (same catch-up spirit as
        // Silhouettes' `pendingAdvance`).
        if (resumed.pendingAdvance) scheduleAdvance(CHOICE_FEEDBACK_MILLIS)
    }

    fun countryFor(code: String): Country? = allCountriesByCode[code]

    /** The lookalike sets actually encountered during the round, for the post-round review. */
    fun reviewSets(): List<FlagLookalikeSet> {
        val state = activeState() ?: return emptyList()
        return state.encounteredSetIds.mapNotNull { setsById[it] }.sortedBy { it.tier }
    }

    /**
     * An optional "did you know" near-identical set for the review (task 7.3, D52's nice-to-have):
     * never drawn as a question, so shown here instead if the data file has one.
     */
    fun nearIdenticalDidYouKnow(): FlagLookalikeSet? =
        allSets.firstOrNull { it.visibility == FlagLookalikeVisibility.NEAR_IDENTICAL }

    /**
     * Records the round once ([CompleteQuizUseCase]) and publishes [completion] at
     * [FlagSpeedCompletion.Step.REVIEW] first - see [FlagSpeedUiState].
     */
    private fun finishIfComplete() {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val askedCountries = state.askedCodes.mapNotNull { allCountriesByCode[it] }
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = askedCountries),
            answeredCountries = state.correctCodes,
            incorrectGuesses = state.incorrectGuesses,
            incorrectGuessStrings = state.incorrectGuessStrings,
            // FlagSpeedScoring's documented convention: the round's longest combo, as a string.
            currentInput = state.longestCombo.toString(),
            isComplete = true
        )
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = FlagSpeedGameMode.MODE_ID,
            routeCategoryType = category.typeKey,
            routeCategoryValue = category.valueKey,
            difficulty = state.difficulty,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = FlagSpeedCompletion(resultId = resultId, step = FlagSpeedCompletion.Step.REVIEW)
        }
    }

    /** The review screen's "Continue" button: moves on to the shared Results screen. */
    fun onContinueFromReview() {
        val current = _completion.value ?: return
        if (current.step != FlagSpeedCompletion.Step.REVIEW) return
        _completion.value = current.copy(step = FlagSpeedCompletion.Step.NAVIGATE)
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = FlagSpeedCompletion.Step.DONE)
    }

    companion object {
        const val ARG_DIFFICULTY = "difficulty"

        /** How long a pick's result stays on the options before the next question. */
        const val CHOICE_FEEDBACK_MILLIS = 900L

        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}
