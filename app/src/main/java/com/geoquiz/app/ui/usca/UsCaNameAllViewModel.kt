package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.usca.UsCaRegionsAnswerValidator
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.AnswerResult
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Name all (task 6.2, D40's precedent): type every state/province's name, each correct one
 * filling in; Normal and Hard only (clamped in [resolveDifficulty], same fallback as Tap the
 * map's Name all). Validated by [UsCaRegionsAnswerValidator] (D43: typos allowed at Normal, exact
 * at Hard, the full name or the postal abbreviation both accepted). No Room resume save (D37),
 * process death via [SavedStateHandle] through [UsCaNameAllSavedState] only.
 */
@HiltViewModel
class UsCaNameAllViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val validateAnswer: UsCaRegionsAnswerValidator,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    clock: MonotonicClock
) : ViewModel() {

    val modeSpec: GameModeSpec get() = UsCaRegionsGameMode.SPEC
    val variantId: String = UsCaRegionsGameMode.VARIANT_NAME_ALL

    private val categoryType: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_TYPE] ?: "region"
    private val categoryValueRaw: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_VALUE] ?: ""
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    private val routeDifficulty: Difficulty? =
        Difficulty.fromIdOrNull(savedStateHandle[UsCaRegionsRouteArgs.ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<UsCaNameAllUiState>(UsCaNameAllUiState.Loading)
    val uiState: StateFlow<UsCaNameAllUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.NORMAL)
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<UsCaRegionsCompletion?>(null)
    val completion: StateFlow<UsCaRegionsCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = UsCaNameAllSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)

    private var tickerJob: Job? = null
    private var completionJob: Job? = null
    private var submitJob: Job? = null

    private var round: UsCaRegionsRound? = null

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            _difficulty.value = resolveDifficulty()
            savedState.difficulty = _difficulty.value
            _timerVisible.value = _showTimer.value || _difficulty.value.timerAlwaysShown
            loadQuiz()
        }
    }

    /** Route tier, else the saved value, else the remembered default - Normal/Hard only (D40). */
    private suspend fun resolveDifficulty(): Difficulty {
        val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
        return if (requested == Difficulty.HARD) Difficulty.HARD else Difficulty.NORMAL
    }

    private suspend fun loadQuiz() {
        val loaded = loadUsCaRegionsRound(mapRepository, categoryType, categoryValue)
        round = loaded
        val quiz = Quiz(category = loaded.category, countries = loaded.items)

        val snapshot = savedState.restore()
        var state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            snapshot.toQuizState(quiz)
        } else {
            QuizState(quiz = quiz)
        }
        if (loaded.items.isEmpty()) state = state.copy(isComplete = true, isPaused = false)
        _uiState.value = UsCaNameAllUiState.Active(state)
        onStateChanged()
        finishQuizIfComplete()
    }

    fun onInputChange(input: String) {
        _uiState.update { ui -> if (ui is UsCaNameAllUiState.Active) UsCaNameAllUiState.Active(ui.state.copy(currentInput = input)) else ui }
        saveToHandle()
    }

    fun onSubmitAnswer() {
        val current = _uiState.value
        if (current !is UsCaNameAllUiState.Active) return
        if (current.state.isComplete || current.state.isPaused) return
        if (submitJob?.isActive == true) return
        val input = current.state.currentInput.trim()
        if (input.isBlank()) return
        val postalByCode = round?.postalByCode.orEmpty()

        submitJob = viewModelScope.launch {
            val rules = _difficulty.value
            val result = validateAnswer.validate(input, current.state, rules.allowsTypos, postalByCode)
            _uiState.update { ui ->
                if (ui !is UsCaNameAllUiState.Active) return@update ui
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
                UsCaNameAllUiState.Active(
                    state.copy(
                        answeredCountries = newAnswered,
                        currentInput = if (result is AnswerResult.Correct) "" else state.currentInput,
                        lastAnswerResult = result,
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

    fun onGiveUp() {
        _uiState.update { ui ->
            if (ui is UsCaNameAllUiState.Active && !ui.state.isComplete) {
                UsCaNameAllUiState.Active(ui.state.copy(isComplete = true, isPaused = false))
            } else {
                ui
            }
        }
        onStateChanged()
        finishQuizIfComplete()
    }

    fun onBackgrounded() {
        val current = _uiState.value
        if (current !is UsCaNameAllUiState.Active || current.state.isComplete || current.state.isPaused) return
        _uiState.value = UsCaNameAllUiState.Active(current.state.copy(isPaused = true))
        onStateChanged()
    }

    fun togglePause() {
        val current = _uiState.value
        if (current !is UsCaNameAllUiState.Active || current.state.isComplete) return
        _uiState.value = UsCaNameAllUiState.Active(current.state.copy(isPaused = !current.state.isPaused))
        onStateChanged()
    }

    private fun onStateChanged() {
        syncTimerWithState()
        saveToHandle()
    }

    private fun saveToHandle() {
        val current = _uiState.value
        if (current is UsCaNameAllUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is UsCaNameAllUiState.Active && !current.state.isComplete && !current.state.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is UsCaNameAllUiState.Active || !current.state.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = UsCaRegionsGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = _difficulty.value,
            challengeId = null,
            variantId = variantId
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = UsCaRegionsCompletion(resultId = resultId, step = UsCaRegionsCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.update { it?.copy(step = UsCaRegionsCompletion.Step.DONE) }
    }

    companion object {
        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

sealed class UsCaNameAllUiState {
    data object Loading : UsCaNameAllUiState()
    data class Active(val state: QuizState) : UsCaNameAllUiState()
}

/** A recorded round on its way to Results, shared by all three variants. */
data class UsCaRegionsCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
