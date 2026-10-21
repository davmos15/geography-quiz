package com.geoquiz.app.ui.usca

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.usca.UsCaRegionsGameMode
import com.geoquiz.app.domain.model.Country
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
 * Tap (task 6.2): a state/province's name is the prompt, the player taps it on the admin1 map.
 * Normal and Hard only (Easy's job is the Multiple choice variant, task 6.2 review). Wrong taps
 * adapt D36: a wrong tap names the tapped region briefly and is a wrong guess; Normal reveals a
 * prompt after 3 wrong taps on it and counts it missed, Hard ends the round after 3 wrong taps in
 * total. No Room resume save (D37), process death via [SavedStateHandle] through
 * [UsCaTapSavedState] only.
 */
@HiltViewModel
class UsCaTapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    clock: MonotonicClock
) : ViewModel() {

    val modeSpec: GameModeSpec get() = UsCaRegionsGameMode.SPEC
    val variantId: String = UsCaRegionsGameMode.VARIANT_TAP

    private val categoryType: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_TYPE] ?: "region"
    private val categoryValueRaw: String =
        savedStateHandle[UsCaRegionsRouteArgs.ARG_CATEGORY_VALUE] ?: ""
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    private val routeDifficulty: Difficulty? =
        Difficulty.fromIdOrNull(savedStateHandle[UsCaRegionsRouteArgs.ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<UsCaTapUiState>(UsCaTapUiState.Loading)
    val uiState: StateFlow<UsCaTapUiState> = _uiState.asStateFlow()

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

    private val _mapState = MutableStateFlow(UsCaRegionsMapState())
    val mapState: StateFlow<UsCaRegionsMapState> = _mapState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = UsCaTapSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)

    private var tickerJob: Job? = null
    private var completionJob: Job? = null
    private var flashJob: Job? = null
    private var revealJob: Job? = null

    private var itemsByCode: Map<String, Country> = emptyMap()

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

    /** Route tier, else the saved value, else [Difficulty.DEFAULT] - Normal/Hard only. */
    private fun resolveDifficulty(): Difficulty {
        val requested = routeDifficulty ?: savedState.difficulty ?: Difficulty.DEFAULT
        return if (requested == Difficulty.HARD) Difficulty.HARD else Difficulty.NORMAL
    }

    private suspend fun loadQuiz() {
        val loaded = loadUsCaRegionsRound(mapRepository, categoryType, categoryValue)
        // No regions to ask about (task 9.2 review fix: the admin1 layer failed to load, or a
        // Practice round's codes no longer resolve to any): never publish a round that would
        // complete itself on the spot and get recorded - Unavailable instead, the same reasoning
        // as `BorderHopUiState.Unavailable`.
        if (loaded.items.isEmpty()) {
            _uiState.value = UsCaTapUiState.Unavailable
            return
        }
        itemsByCode = loaded.items.associateBy { it.code }
        val quiz = Quiz(category = loaded.category, countries = loaded.items)
        _mapState.value = loadUsCaRegionsMapState(mapRepository, itemsByCode.keys, loaded.group)

        val snapshot = savedState.restore()
        var state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            snapshot.toGameState(quiz)
        } else {
            val base = QuizState(quiz = quiz, remainingOrder = loaded.items.map { it.code })
            nextPrompt(UsCaTapGameState(quizState = base))
        }
        state = when {
            hardLimitReached(state.quizState) && !state.quizState.isComplete ->
                state.copy(quizState = state.quizState.copy(isComplete = true, isPaused = false))
            !state.quizState.isComplete && state.currentTargetCode == null -> nextPrompt(state)
            else -> state
        }
        _uiState.value = UsCaTapUiState.Active(state)
        onStateChanged()
        finishQuizIfComplete()
    }

    private fun nextPrompt(state: UsCaTapGameState): UsCaTapGameState {
        flashJob?.cancel()
        revealJob?.cancel()
        val qs = state.quizState
        val cleared = state.copy(wrongTapsThisPrompt = 0, wrongFlashCode = null, wrongFlashName = null, revealedCode = null)
        val nextCode = qs.remainingOrder.firstOrNull()
            ?: return cleared.copy(
                quizState = qs.copy(isComplete = qs.quiz.countries.isNotEmpty(), isPaused = false),
                currentTargetCode = null
            )
        val newRemaining = qs.remainingOrder.drop(1)
        return cleared.copy(quizState = qs.copy(remainingOrder = newRemaining), currentTargetCode = nextCode)
    }

    fun onFeatureTap(hit: MapHit?) {
        val active = activeOrNull() ?: return
        val state = active.state
        val qs = state.quizState
        if (qs.isComplete || qs.isPaused || state.revealedCode != null || hit == null || !hit.isPlayable) return
        val code = hit.featureId
        if (code !in itemsByCode) return
        if (code in qs.answeredCountries || code in qs.missedCountries || code == state.wrongFlashCode) return
        val difficulty = _difficulty.value
        val hardLimit = Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK
        if (difficulty == Difficulty.HARD && qs.incorrectGuesses >= hardLimit) return
        if (difficulty == Difficulty.NORMAL && state.wrongTapsThisPrompt >= NORMAL_REVEAL_LIMIT) return
        val target = state.currentTargetCode ?: return
        if (code == target) correctTap(state, code) else wrongTap(state, code)
    }

    private fun correctTap(state: UsCaTapGameState, code: String) {
        flashJob?.cancel()
        revealJob?.cancel()
        val qs = state.quizState
        val updated = state.copy(quizState = qs.copy(answeredCountries = qs.answeredCountries + code))
        publish(nextPrompt(updated))
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
        finishQuizIfComplete()
    }

    private fun wrongTap(state: UsCaTapGameState, code: String) {
        val qs = state.quizState
        val tappedName = itemsByCode[code]?.name ?: return
        val promptTarget = state.currentTargetCode ?: return
        val wrongThisPrompt = state.wrongTapsThisPrompt + 1
        val totalWrong = qs.incorrectGuesses + 1
        val newQs = qs.copy(
            incorrectGuesses = totalWrong,
            incorrectGuessStrings = qs.incorrectGuessStrings + tappedName
        )
        publish(state.copy(quizState = newQs, wrongTapsThisPrompt = wrongThisPrompt, wrongFlashCode = code, wrongFlashName = tappedName))
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(WRONG_FLASH_MILLIS)
            resolveAfterWrongTap(promptTarget, totalWrong, wrongThisPrompt)
        }
    }

    private fun resolveAfterWrongTap(promptTarget: String, totalWrong: Int, wrongThisPrompt: Int) {
        val active = activeOrNull() ?: return
        val cleared = active.state.copy(wrongFlashCode = null, wrongFlashName = null)
        val difficulty = _difficulty.value
        val hardLimit = Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK
        val samePrompt = cleared.currentTargetCode == promptTarget
        when {
            difficulty == Difficulty.HARD && totalWrong >= hardLimit -> {
                val qs = cleared.quizState
                publish(cleared.copy(quizState = qs.copy(isComplete = true, isPaused = false)))
                finishQuizIfComplete()
            }
            samePrompt && difficulty == Difficulty.NORMAL && wrongThisPrompt >= NORMAL_REVEAL_LIMIT -> {
                publish(cleared.copy(revealedCode = promptTarget))
                revealJob?.cancel()
                revealJob = viewModelScope.launch {
                    delay(REVEAL_MILLIS)
                    finishReveal(promptTarget)
                }
            }
            else -> publish(cleared)
        }
    }

    private fun finishReveal(target: String) {
        val active = activeOrNull() ?: return
        val state = active.state
        if (state.currentTargetCode != target) return
        val qs = state.quizState
        val newQs = qs.copy(missedCountries = qs.missedCountries + target)
        publish(nextPrompt(state.copy(quizState = newQs, revealedCode = null)))
        finishQuizIfComplete()
    }

    fun onGiveUp() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete) return
        flashJob?.cancel()
        revealJob?.cancel()
        publish(active.state.copy(quizState = qs.copy(isComplete = true, isPaused = false)))
        finishQuizIfComplete()
    }

    fun onBackgrounded() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete || qs.isPaused) return
        publish(active.state.copy(quizState = qs.copy(isPaused = true)))
    }

    fun togglePause() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete) return
        publish(active.state.copy(quizState = qs.copy(isPaused = !qs.isPaused)))
    }

    private fun activeOrNull(): UsCaTapUiState.Active? = _uiState.value as? UsCaTapUiState.Active

    private fun hardLimitReached(qs: QuizState): Boolean =
        _difficulty.value == Difficulty.HARD && qs.incorrectGuesses >= (Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK)

    private fun publish(state: UsCaTapGameState) {
        _uiState.value = UsCaTapUiState.Active(state)
        onStateChanged()
    }

    private fun onStateChanged() {
        syncTimerWithState()
        val current = _uiState.value
        if (current is UsCaTapUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is UsCaTapUiState.Active && !current.state.quizState.isComplete && !current.state.quizState.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is UsCaTapUiState.Active || !current.state.quizState.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state.quizState,
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
        const val WRONG_FLASH_MILLIS = 900L
        const val NORMAL_REVEAL_LIMIT = 3
        const val REVEAL_MILLIS = 1_500L
        private const val HARD_STRIKE_LIMIT_FALLBACK = 3
        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

sealed class UsCaTapUiState {
    data object Loading : UsCaTapUiState()

    /** No regions to ask about; nothing is recorded (Phase 9 review). */
    data object Unavailable : UsCaTapUiState()
    data class Active(val state: UsCaTapGameState) : UsCaTapUiState()
}
