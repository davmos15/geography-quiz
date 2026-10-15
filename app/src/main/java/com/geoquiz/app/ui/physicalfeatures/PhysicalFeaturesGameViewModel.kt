package com.geoquiz.app.ui.physicalfeatures

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureAnswerValidator
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureChoiceBuilder
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesCategory
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesGameMode
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeaturesRound
import com.geoquiz.app.domain.mode.physicalfeatures.inTab
import com.geoquiz.app.domain.mode.physicalfeatures.matching
import com.geoquiz.app.domain.mode.physicalfeatures.toQuizCountry
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizState
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
 * One Rivers, mountains and lakes round (task 7.2, D51): its own screen and ViewModel (D37), no
 * Room resume save. [savedStateHandle] carries the route's `categoryType`/`categoryValue`/
 * `difficulty` ([PhysicalFeaturesRouteArgs]) and, after the first state change, the round's own
 * progress so the screen survives rotation and process death ([PhysicalFeaturesSavedState]).
 */
@HiltViewModel
class PhysicalFeaturesGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val physicalFeatureRepository: PhysicalFeatureRepository,
    private val mapRepository: MapRepository,
    private val answerValidator: PhysicalFeatureAnswerValidator,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom
) : ViewModel() {

    private val categoryType: String =
        savedStateHandle[PhysicalFeaturesRouteArgs.ARG_CATEGORY_TYPE] ?: "fixed_region"
    private val categoryValueRaw: String =
        savedStateHandle[PhysicalFeaturesRouteArgs.ARG_CATEGORY_VALUE] ?: ""
    private val categoryValue: String = Uri.decode(categoryValueRaw)
    private val filter = PhysicalFeaturesCategory.filterFromLabel(categoryValue)
    val category = PhysicalFeaturesCategory.toQuizCategory(filter)

    private val routeDifficulty: Difficulty? =
        Difficulty.fromIdOrNull(savedStateHandle[PhysicalFeaturesRouteArgs.ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<PhysicalFeaturesUiState>(PhysicalFeaturesUiState.Loading)
    val uiState: StateFlow<PhysicalFeaturesUiState> = _uiState.asStateFlow()

    private val _mapState = MutableStateFlow(PhysicalFeaturesMapState())
    val mapState: StateFlow<PhysicalFeaturesMapState> = _mapState.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _completion = MutableStateFlow<PhysicalFeaturesCompletion?>(null)
    val completion: StateFlow<PhysicalFeaturesCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = PhysicalFeaturesSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var advanceJob: Job? = null
    private var completionJob: Job? = null

    /** Every feature in this round's tab (any continent): Easy's distractor pool (D51: "4
     * feature names from the SAME type tab"). */
    private var choicePool: List<PhysicalFeature> = emptyList()

    /** This round's items' aliases, keyed by id, for [answerValidator]. */
    private var aliasesByCode: Map<String, List<String>> = emptyMap()

    init {
        viewModelScope.launch {
            _timerVisible.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
            val difficulty = requested.takeIf { it in PhysicalFeaturesGameMode.SPEC.supportedDifficulties }
                ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty
            _timerVisible.value = _timerVisible.value || difficulty.timerAlwaysShown
            loadRound(difficulty)
        }
    }

    private suspend fun loadRound(difficulty: Difficulty) {
        val allFeatures = physicalFeatureRepository.features()
        choicePool = allFeatures.inTab(filter.tab)
        val candidates = allFeatures.matching(filter)

        val snapshot = savedState.restore()
        val state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            PhysicalFeaturesRoundState(
                filter = filter,
                difficulty = difficulty,
                // Looked up against the whole bundled list, not just this filter's candidates:
                // the filter is derived from the route every time, but the saved ids are already
                // this exact round (task 7.2 review nit, same reasoning as Country silhouettes).
                features = snapshot.features(allFeatures),
                currentIndex = snapshot.currentIndex,
                answeredIds = snapshot.answeredIds.toSet(),
                incorrectGuesses = snapshot.incorrectGuesses,
                incorrectGuessStrings = snapshot.incorrectGuessStrings,
                currentInput = snapshot.currentInput,
                isComplete = snapshot.isComplete,
                pendingAdvance = snapshot.pendingAdvance,
                paused = !snapshot.isComplete
            )
        } else {
            val features = PhysicalFeaturesRound.items(candidates, random)
            PhysicalFeaturesRoundState(
                filter = filter,
                difficulty = difficulty,
                features = features,
                isComplete = features.isEmpty()
            )
        }
        aliasesByCode = state.features.associate { it.id to it.aliases }
        commit(state)
        loadMapState(state.features)
        when {
            state.isComplete -> finishIfComplete()
            state.pendingAdvance -> advanceToNext()
            else -> loadChoiceForCurrent(state)
        }
    }

    private suspend fun loadMapState(features: List<PhysicalFeature>) {
        _mapState.value = loadPhysicalFeaturesMapState(mapRepository, features)
    }

    private fun loadChoiceForCurrent(state: PhysicalFeaturesRoundState) {
        val target = state.current ?: return
        val question = if (state.difficulty == Difficulty.EASY) {
            PhysicalFeatureChoiceBuilder.question(target, choicePool, random)
        } else {
            null
        }
        commit(state.copy(choice = question, choiceFeedback = null))
    }

    private fun activeState(): PhysicalFeaturesRoundState? = (_uiState.value as? PhysicalFeaturesUiState.Active)?.state

    private fun commit(state: PhysicalFeaturesRoundState, announce: Boolean = state.feedback != activeState()?.feedback) {
        val stamped = if (announce) state.copy(feedbackToken = state.feedbackToken + 1) else state
        _uiState.value = PhysicalFeaturesUiState.Active(stamped)
        syncTimer(stamped)
        savedState.save(stamped, quizTimer.snapshot())
    }

    private fun syncTimer(state: PhysicalFeaturesRoundState) {
        if (state.isComplete || state.paused) quizTimer.pause() else quizTimer.resume()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    fun onInputChange(value: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused) return
        _uiState.value = PhysicalFeaturesUiState.Active(state.copy(currentInput = value))
        savedState.saveInput(value)
    }

    /** Typed answer (Normal/Hard): judged against this round's own items only (D51 review: "keep
     * it simple" - a feature not asked in this round is treated the same as nonsense input). */
    fun onSubmit() {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused) return
        val target = state.current ?: return
        val input = state.currentInput.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            val allowFuzzy = state.difficulty.allowsTypos
            val quizState = QuizState(
                quiz = Quiz(category = category, countries = state.features.map { it.toQuizCountry() }),
                answeredCountries = state.answeredIds
            )
            val result = answerValidator.validate(input, quizState, allowFuzzy, aliasesByCode)

            val latest = activeState() ?: return@launch
            if (latest.isComplete || latest.isAdvancing || latest.paused || latest.current?.id != target.id) return@launch

            when (result) {
                is AnswerResult.Correct -> {
                    val matched = latest.features.firstOrNull { it.name == result.countryName }
                    if (matched?.id == target.id) onCorrectAnswer(latest, target) else onIncorrectGuess(latest, matched?.name ?: input)
                }
                AnswerResult.NearMiss -> {
                    commit(latest.copy(feedback = PhysicalFeatureFeedback.NearMiss), announce = true)
                    _feedbackEvents.tryEmit(AnswerFeedbackEvent.NEAR_MISS)
                }
                AnswerResult.AlreadyAnswered -> {
                    commit(latest.copy(feedback = PhysicalFeatureFeedback.AlreadyAnswered, currentInput = ""), announce = true)
                }
                AnswerResult.Incorrect -> onIncorrectGuess(latest, input)
                AnswerResult.None -> Unit
            }
        }
    }

    private fun onCorrectAnswer(state: PhysicalFeaturesRoundState, target: PhysicalFeature) {
        commit(
            state.copy(
                answeredIds = state.answeredIds + target.id,
                feedback = PhysicalFeatureFeedback.Correct(target.name),
                currentInput = "",
                isAdvancing = true,
                pendingAdvance = true
            )
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
        scheduleAdvance(ADVANCE_DELAY_MILLIS)
    }

    /**
     * A wrong typed guess, or a typed name that matches a different (not-current) round feature
     * (D51 review's simplification, see [PhysicalFeatureFeedback.Incorrect]): a strike (Hard ends
     * the round at [com.geoquiz.app.domain.model.Difficulty.strikeLimit], same as every classic
     * mode), but the current feature stays current - no "reveal and skip" - so the player keeps
     * trying or taps Give up.
     */
    private fun onIncorrectGuess(state: PhysicalFeaturesRoundState, guessText: String) {
        val newIncorrect = state.incorrectGuesses + 1
        val updated = state.copy(
            incorrectGuesses = newIncorrect,
            incorrectGuessStrings = state.incorrectGuessStrings + guessText,
            feedback = PhysicalFeatureFeedback.Incorrect,
            currentInput = ""
        )
        val limit = state.difficulty.strikeLimit
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
        if (limit != null && newIncorrect >= limit) {
            endRoundStrikesExhausted(updated)
        } else {
            commit(updated, announce = true)
        }
    }

    /** Easy: one of the four options was picked. */
    fun onChoiceSelected(code: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused || state.choiceFeedback != null) return
        val choice = state.choice ?: return
        val target = state.current ?: return
        val picked = choice.option(code) ?: return
        val isCorrect = code == choice.targetCode

        if (isCorrect) {
            commit(
                state.copy(
                    answeredIds = state.answeredIds + target.id,
                    choiceFeedback = ChoiceFeedback(code, true),
                    isAdvancing = true,
                    pendingAdvance = true
                )
            )
            _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
            scheduleAdvance(CHOICE_FEEDBACK_MILLIS)
            return
        }

        val newIncorrect = state.incorrectGuesses + 1
        val updated = state.copy(
            incorrectGuesses = newIncorrect,
            incorrectGuessStrings = state.incorrectGuessStrings + picked.label,
            choiceFeedback = ChoiceFeedback(code, false)
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
        // Difficulty.EASY.strikeLimit is always null, so this never actually ends the round at
        // Easy; kept for symmetry with onIncorrectGuess and in case that ever changes.
        val limit = state.difficulty.strikeLimit
        if (limit != null && newIncorrect >= limit) {
            endRoundStrikesExhausted(updated)
        } else {
            commit(updated.copy(isAdvancing = true, pendingAdvance = true))
            scheduleAdvance(CHOICE_FEEDBACK_MILLIS)
        }
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        advanceJob?.cancel()
        commit(state.copy(isComplete = true, isAdvancing = false, pendingAdvance = false))
        finishIfComplete()
    }

    /** Pauses on backgrounding, like every other new mode (D37); Resume on the paused overlay is
     * the only way back. */
    fun onBackgrounded() {
        val state = activeState() ?: return
        if (state.isComplete || state.paused) return
        commit(state.copy(paused = true))
    }

    fun onResume() {
        val state = activeState() ?: return
        if (!state.paused) return
        commit(state.copy(paused = false))
    }

    private fun endRoundStrikesExhausted(state: PhysicalFeaturesRoundState) {
        advanceJob?.cancel()
        commit(state.copy(isComplete = true, isAdvancing = false, pendingAdvance = false), announce = true)
        finishIfComplete()
    }

    private fun scheduleAdvance(delayMillis: Long) {
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(delayMillis)
            advanceToNext()
        }
    }

    private fun advanceToNext() {
        val state = activeState() ?: return
        val nextIndex = state.currentIndex + 1
        val isComplete = nextIndex >= state.features.size
        val updated = state.copy(
            currentIndex = nextIndex,
            feedback = PhysicalFeatureFeedback.None,
            isAdvancing = false,
            pendingAdvance = false,
            choice = null,
            choiceFeedback = null,
            isComplete = isComplete
        )
        commit(updated)
        if (isComplete) finishIfComplete() else loadChoiceForCurrent(updated)
    }

    /** Records the round once ([CompleteQuizUseCase]) and publishes [completion]. No Room resume
     * save (D37). */
    private fun finishIfComplete() {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = state.features.map { it.toQuizCountry() }),
            answeredCountries = state.answeredIds,
            incorrectGuesses = state.incorrectGuesses,
            incorrectGuessStrings = state.incorrectGuessStrings,
            isComplete = true
        )
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = PhysicalFeaturesGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = state.difficulty,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = PhysicalFeaturesCompletion(resultId = resultId, step = PhysicalFeaturesCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = PhysicalFeaturesCompletion.Step.DONE)
    }

    companion object {
        /** How long the "Correct" feedback stays before the next feature. */
        const val ADVANCE_DELAY_MILLIS = 900L

        /** Easy only: how long a pick's result stays on the options before the next question. */
        const val CHOICE_FEEDBACK_MILLIS = 900L

        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}
