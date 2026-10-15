package com.geoquiz.app.ui.tapmap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Tap the map's "Name all" variant (task 5.2b, D40): type every member of the chosen region's
 * name; each correct one fills in on the map. Answered exactly like the classic Countries quiz
 * (same [com.geoquiz.app.domain.usecase.ValidateAnswerUseCase], via [TapMapGameMode.validator]):
 * typos forgiven at Normal, a [AnswerResult.NearMiss] "Close - check the spelling" at Hard with
 * no strike, 3 Hard strikes end the round. Normal and Hard only - Easy's multiple choice is
 * Find's job (D40), so a route that somehow carries `easy` is treated as Normal. No Room resume
 * save (D37, process death via [SavedStateHandle] through [TapMapNameAllSavedState] only) and no
 * hints yet ([QuizState.hintsUsed] stays empty).
 */
@HiltViewModel
class TapMapNameAllViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameModes: GameModeRegistry,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    clock: MonotonicClock,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val gameMode = gameModes.require(TapMapGameMode.MODE_ID)

    /** Labels and icon for this mode (Give up dialog message, items noun, etc.), shared with Find. */
    val modeSpec: GameModeSpec get() = gameMode.spec

    val variantId: String = TapMapGameMode.VARIANT_NAME_ALL

    private val categoryType: String =
        savedStateHandle[TapMapGameViewModel.ARG_CATEGORY_TYPE] ?: QuizCategory.MapRegion(RegionPreset.WORLD.id).typeKey
    private val categoryValueRaw: String =
        savedStateHandle[TapMapGameViewModel.ARG_CATEGORY_VALUE] ?: RegionPreset.WORLD.id
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val routeDifficulty: Difficulty? =
        Difficulty.fromIdOrNull(savedStateHandle[TapMapGameViewModel.ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<TapMapNameAllUiState>(TapMapNameAllUiState.Loading)
    val uiState: StateFlow<TapMapNameAllUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    val showTimer: StateFlow<Boolean> = _showTimer.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.NORMAL)
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<TapMapCompletion?>(null)
    val completion: StateFlow<TapMapCompletion?> = _completion.asStateFlow()

    private val _mapState = MutableStateFlow(TapMapMapState())
    val mapState: StateFlow<TapMapMapState> = _mapState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = TapMapNameAllSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)

    private var tickerJob: Job? = null
    private var completionJob: Job? = null

    /** Guards [onSubmitAnswer] (S10): a submit while one is still validating is ignored, so
     * tapping Submit twice (or hitting enter while a slow validation is in flight) never counts
     * one answer as two guesses. */
    private var submitJob: Job? = null

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            _difficulty.value = resolveDifficulty()
            savedState.difficulty = _difficulty.value
            updateTimerVisible()
            loadQuiz()
        }
    }

    /**
     * Route tier, else the value saved for process death, else the remembered default - clamped
     * to Normal/Hard (D40): Easy is Find's multiple-choice tier, never Name all's, whatever asked
     * for it.
     */
    private suspend fun resolveDifficulty(): Difficulty {
        val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
        return if (requested == Difficulty.HARD) Difficulty.HARD else Difficulty.NORMAL
    }

    private fun updateTimerVisible() {
        _timerVisible.value = _showTimer.value || _difficulty.value.timerAlwaysShown
    }

    private suspend fun loadQuiz() {
        val items = gameMode.generator.items(category)
        val quiz = Quiz(category = category, countries = items)

        // Loaded before Active is published, exactly like Find (TapMapGameViewModel.loadQuiz): a
        // submit should never race the map's own load, and the timer should not run while the
        // player is still looking at a loading spinner.
        val preset = (category as? QuizCategory.MapRegion)?.presetId?.let { RegionPreset.fromId(it) } ?: RegionPreset.WORLD
        _mapState.value = loadTapMapMapState(
            mapRepository = mapRepository,
            defaultDispatcher = defaultDispatcher,
            preset = preset,
            members = items.mapTo(HashSet()) { it.code },
            isPractice = category is QuizCategory.Practice
        )

        val snapshot = savedState.restore()
        var state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            snapshot.toQuizState(quiz)
        } else {
            QuizState(quiz = quiz)
        }
        // Guard: a region or practice set with nothing to name (should not normally happen)
        // completes immediately instead of soft-locking with nothing to type and no way to finish.
        if (items.isEmpty()) state = state.copy(isComplete = true, isPaused = false)
        _uiState.value = TapMapNameAllUiState.Active(state)
        onStateChanged()
        // A quiz restored as complete was finished before the process died: reuse its result.
        finishQuizIfComplete()
    }

    fun onInputChange(input: String) {
        _uiState.update { ui -> if (ui is TapMapNameAllUiState.Active) TapMapNameAllUiState.Active(ui.state.copy(currentInput = input)) else ui }
        saveToHandle()
    }

    /**
     * Validated exactly like the classic Countries quiz, via [TapMapGameMode.validator]. Ignored
     * while a previous submit is still validating ([submitJob], S10): tapping Submit twice before
     * the first finishes must count only one guess. The update is also skipped if, by the time
     * validation finishes, the round is already complete (e.g. Give up ran first - never
     * un-completes it) or the field no longer holds the input this result is for (the player
     * changed it while validation was running).
     */
    fun onSubmitAnswer() {
        val current = _uiState.value
        if (current !is TapMapNameAllUiState.Active) return
        if (current.state.isComplete || current.state.isPaused) return
        if (submitJob?.isActive == true) return
        val input = current.state.currentInput.trim()
        if (input.isBlank()) return

        submitJob = viewModelScope.launch {
            val rules = _difficulty.value
            val result = gameMode.validator.validate(input, current.state, rules.allowsTypos)
            _uiState.update { ui ->
                if (ui !is TapMapNameAllUiState.Active) return@update ui
                val state = ui.state
                if (state.isComplete || state.currentInput.trim() != input) return@update ui
                val correctCode = (result as? AnswerResult.Correct)?.let { correct ->
                    state.quiz.countries.firstOrNull { it.name == correct.countryName }?.code
                }
                val newAnswered = if (correctCode != null) state.answeredCountries + correctCode else state.answeredCountries
                val newRecent = if (correctCode != null) {
                    QuizState.pushRecentCorrect(state.recentCorrect, correctCode)
                } else {
                    state.recentCorrect
                }
                val newIncorrect = if (result is AnswerResult.Incorrect) state.incorrectGuesses + 1 else state.incorrectGuesses
                val newIncorrectStrings = if (result is AnswerResult.Incorrect) {
                    state.incorrectGuessStrings + input
                } else {
                    state.incorrectGuessStrings
                }
                val allAnswered = newAnswered.size == state.quiz.countries.size
                val struckOut = rules.strikeLimit?.let { newIncorrect >= it } ?: false
                TapMapNameAllUiState.Active(
                    state.copy(
                        answeredCountries = newAnswered,
                        currentInput = if (result is AnswerResult.Correct) "" else state.currentInput,
                        lastAnswerResult = result,
                        // Never un-complete (belt and braces: the isComplete check above already
                        // returns before this runs for an already-finished round).
                        isComplete = state.isComplete || allAnswered || struckOut,
                        incorrectGuesses = newIncorrect,
                        incorrectGuessStrings = newIncorrectStrings,
                        recentCorrect = newRecent
                    )
                )
            }
            onStateChanged()
            AnswerFeedbackEvent.from(result)?.let { _feedbackEvents.tryEmit(it) }
            finishQuizIfComplete()
        }
    }

    /** D40: confirmed by a dialog (the screen's job); the rest count as missed once this runs. */
    fun onGiveUp() {
        _uiState.update { ui ->
            if (ui is TapMapNameAllUiState.Active && !ui.state.isComplete) {
                TapMapNameAllUiState.Active(ui.state.copy(isComplete = true, isPaused = false))
            } else {
                ui
            }
        }
        onStateChanged()
        finishQuizIfComplete()
    }

    /**
     * Pauses on backgrounding, exactly like Find and the classic quiz: saves so a killed process
     * comes back paused too (D37: no Room resume save, [TapMapNameAllSavedState] is process
     * death's only record). Rotation does not call this (the screen's `LifecycleEventEffect`
     * skips it while `isChangingConfigurations`).
     */
    fun onBackgrounded() {
        val current = _uiState.value
        if (current !is TapMapNameAllUiState.Active || current.state.isComplete || current.state.isPaused) return
        _uiState.value = TapMapNameAllUiState.Active(current.state.copy(isPaused = true))
        onStateChanged()
    }

    /** The pause overlay's only control (resume); nothing else can reach it while paused. */
    fun togglePause() {
        val current = _uiState.value
        if (current !is TapMapNameAllUiState.Active || current.state.isComplete) return
        _uiState.value = TapMapNameAllUiState.Active(current.state.copy(isPaused = !current.state.isPaused))
        onStateChanged()
    }

    private fun onStateChanged() {
        syncTimerWithState()
        saveToHandle()
    }

    private fun saveToHandle() {
        val current = _uiState.value
        if (current is TapMapNameAllUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is TapMapNameAllUiState.Active && !current.state.isComplete && !current.state.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    /**
     * Records a finished round once (see [CompleteQuizUseCase]) and publishes [completion]. The
     * full item list (every member asked, named or not) is the "quiz" ([QuizState.quiz]), so
     * [com.geoquiz.app.domain.model.CompletedQuiz.missedCodes] and "Practise the ones you missed"
     * work whether the round finished by naming everyone, Hard strikes or Give up.
     */
    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is TapMapNameAllUiState.Active || !current.state.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = TapMapGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = _difficulty.value,
            challengeId = null,
            variantId = variantId
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = TapMapCompletion(resultId = resultId, step = TapMapCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.update { it?.copy(step = TapMapCompletion.Step.DONE) }
    }

    companion object {
        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

sealed class TapMapNameAllUiState {
    data object Loading : TapMapNameAllUiState()
    data class Active(val state: QuizState) : TapMapNameAllUiState()
}
