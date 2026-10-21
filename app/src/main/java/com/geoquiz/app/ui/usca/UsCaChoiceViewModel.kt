package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.usca.UsCaRegionsChoiceGenerator
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Multiple choice (task 6.2): the target highlighted on the map, four name options
 * ([UsCaRegionsChoiceGenerator]). Easy only (D39's pattern: this is this mode's Easy-equivalent
 * variant, task 6.2 review). No Room resume save (D37), process death via [SavedStateHandle]
 * through [UsCaChoiceSavedState] only.
 */
@HiltViewModel
class UsCaChoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom
) : ViewModel() {

    val modeSpec: GameModeSpec get() = UsCaRegionsGameMode.SPEC
    val variantId: String = UsCaRegionsGameMode.VARIANT_CHOICE

    private val categoryType: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_TYPE] ?: "region"
    private val categoryValueRaw: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_VALUE] ?: ""
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    private val _uiState = MutableStateFlow<UsCaChoiceUiState>(UsCaChoiceUiState.Loading)
    val uiState: StateFlow<UsCaChoiceUiState> = _uiState.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _mapState = MutableStateFlow(UsCaRegionsMapState())
    val mapState: StateFlow<UsCaRegionsMapState> = _mapState.asStateFlow()

    private val _completion = MutableStateFlow<UsCaRegionsCompletion?>(null)
    val completion: StateFlow<UsCaRegionsCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = UsCaChoiceSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random

    private var flashJob: Job? = null
    private var completionJob: Job? = null

    private var itemsByCode: Map<String, com.geoquiz.app.domain.model.Country> = emptyMap()

    init {
        viewModelScope.launch {
            _vibration.value = settingsRepository.vibration.first()
            loadQuiz()
        }
    }

    private suspend fun loadQuiz() {
        val loaded = loadUsCaRegionsRound(mapRepository, categoryType, categoryValue)
        // No regions to ask about (the admin-1 layer failed to load, or a practice set no longer
        // resolves): show Unavailable rather than a round that completes and records itself (Phase 9 review).
        if (loaded.items.isEmpty()) {
            _uiState.value = UsCaChoiceUiState.Unavailable
            return
        }
        itemsByCode = loaded.items.associateBy { it.code }
        val quiz = Quiz(category = loaded.category, countries = loaded.items)
        _mapState.value =
            loadUsCaRegionsMapState(mapRepository, loaded.items.mapTo(HashSet()) { it.code }, loaded.group)

        val snapshot = savedState.restore()
        var state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            snapshot.toQuizState(quiz)
        } else {
            val base = QuizState(quiz = quiz, remainingOrder = loaded.items.map { it.code })
            nextPrompt(base)
        }
        _uiState.value = UsCaChoiceUiState.Active(state)
        onStateChanged()
        finishQuizIfComplete()
    }

    private fun nextPrompt(state: QuizState): QuizState {
        flashJob?.cancel()
        val nextCode = state.remainingOrder.firstOrNull()
            ?: return state.copy(choice = null, choiceFeedback = null, isComplete = state.quiz.countries.isNotEmpty(), isPaused = false)
        val newRemaining = state.remainingOrder.drop(1)
        val target = itemsByCode[nextCode] ?: return state.copy(remainingOrder = newRemaining)
        val choice = UsCaRegionsChoiceGenerator.question(target, state.quiz.countries, state.quiz.countries, state.quiz.category, random)
        return state.copy(choice = choice, choiceFeedback = null, remainingOrder = newRemaining)
    }

    fun onChoiceSelected(code: String) {
        val active = activeOrNull() ?: return
        if (active.isComplete || active.isPaused || active.choiceFeedback != null) return
        val choice = active.choice ?: return
        val picked = choice.option(code) ?: return
        val target = choice.targetCode
        val updated = if (code == target) {
            active.copy(
                answeredCountries = active.answeredCountries + target,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = true)
            )
        } else {
            active.copy(
                missedCountries = active.missedCountries + target,
                incorrectGuesses = active.incorrectGuesses + 1,
                incorrectGuessStrings = active.incorrectGuessStrings + picked.label,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = false)
            )
        }
        publish(updated)
        _feedbackEvents.tryEmit(if (code == target) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT)

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(CHOICE_FEEDBACK_MILLIS)
            advanceAfterFeedback()
        }
    }

    private fun advanceAfterFeedback() {
        val active = activeOrNull() ?: return
        if (active.choiceFeedback == null || active.isComplete) return
        publish(nextPrompt(active.copy(choiceFeedback = null)))
        finishQuizIfComplete()
    }

    fun onGiveUp() {
        val active = activeOrNull() ?: return
        if (active.isComplete) return
        flashJob?.cancel()
        publish(active.copy(isComplete = true, isPaused = false))
        finishQuizIfComplete()
    }

    fun onBackgrounded() {
        val active = activeOrNull() ?: return
        if (active.isComplete || active.isPaused) return
        publish(active.copy(isPaused = true))
    }

    fun togglePause() {
        val active = activeOrNull() ?: return
        if (active.isComplete) return
        publish(active.copy(isPaused = !active.isPaused))
    }

    private fun activeOrNull(): QuizState? = (_uiState.value as? UsCaChoiceUiState.Active)?.state

    private fun publish(state: QuizState) {
        _uiState.value = UsCaChoiceUiState.Active(state)
        onStateChanged()
    }

    private fun onStateChanged() {
        syncTimerWithState()
        val current = _uiState.value
        if (current is UsCaChoiceUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is UsCaChoiceUiState.Active && !current.state.isComplete && !current.state.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()
    }

    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is UsCaChoiceUiState.Active || !current.state.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = UsCaRegionsGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = Difficulty.EASY,
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
        const val CHOICE_FEEDBACK_MILLIS = 900L
        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

sealed class UsCaChoiceUiState {
    data object Loading : UsCaChoiceUiState()

    /** No regions to ask about; nothing is recorded (Phase 9 review). */
    data object Unavailable : UsCaChoiceUiState()
    data class Active(val state: QuizState) : UsCaChoiceUiState()
}
