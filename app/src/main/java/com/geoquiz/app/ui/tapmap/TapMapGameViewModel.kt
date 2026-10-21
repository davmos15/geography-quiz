package com.geoquiz.app.ui.tapmap

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.tapmap.TapMapGameMode
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import java.util.UUID
import javax.inject.Inject

/**
 * Tap the map's own screen logic (D37: no [com.geoquiz.app.ui.quiz.QuizViewModel] reuse). Every
 * member of the chosen region (or a practice set) is asked once, in the order
 * [com.geoquiz.app.domain.mode.tapmap.TapMapQuestionGenerator] shuffles them in; Normal and Hard
 * ask by map tap ([onFeatureTap]), Easy by picking a name ([onChoiceSelected]) for the
 * highlighted target. [TapMapGameState] wraps a plain [QuizState] (so it reuses
 * [QuizState.choice]/[remainingOrder][QuizState.remainingOrder]/
 * [missedCountries][QuizState.missedCountries] exactly as Easy quizzes already do, and so
 * [CompleteQuizUseCase] can score it like any other mode) plus the extra bits a tap-driven round
 * needs: which code is the current target at Normal/Hard, and the transient tap feedback.
 */
@HiltViewModel
class TapMapGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameModes: GameModeRegistry,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val countryRepository: CountryRepository,
    private val mapRepository: MapRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val gameMode = gameModes.require(TapMapGameMode.MODE_ID)

    /** Labels and icon for this mode (Give up dialog message, items noun, etc.). */
    val modeSpec: GameModeSpec get() = gameMode.spec

    /**
     * "find" or "name_all" (task 5.2b); carried into the completed quiz. Normalised: only
     * [TapMapGameMode.VARIANT_NAME_ALL] is ever kept as is - anything else (missing, or a
     * value from some future or malformed route) falls back to "find", matching
     * [TapMapGameScreen]'s own `when (variant) { VARIANT_NAME_ALL -> ...; else -> Find }`.
     */
    val variantId: String =
        if (savedStateHandle.get<String>(ARG_VARIANT) == TapMapGameMode.VARIANT_NAME_ALL) {
            TapMapGameMode.VARIANT_NAME_ALL
        } else {
            TapMapGameMode.VARIANT_FIND
        }

    private val categoryType: String =
        savedStateHandle[ARG_CATEGORY_TYPE] ?: QuizCategory.MapRegion(RegionPreset.WORLD.id).typeKey
    private val categoryValueRaw: String = savedStateHandle[ARG_CATEGORY_VALUE] ?: RegionPreset.WORLD.id
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<TapMapUiState>(TapMapUiState.Loading)
    val uiState: StateFlow<TapMapUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    val showTimer: StateFlow<Boolean> = _showTimer.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.DEFAULT)
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<TapMapCompletion?>(null)
    val completion: StateFlow<TapMapCompletion?> = _completion.asStateFlow()

    private val _mapState = MutableStateFlow(TapMapMapState())

    /** The map's own layers and frame, loaded once and separate from [uiState] (D37: a tap or a
     * hint should never reload or re-fit the map). */
    val mapState: StateFlow<TapMapMapState> = _mapState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = TapMapSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random

    private var tickerJob: Job? = null
    private var completionJob: Job? = null
    private var flashJob: Job? = null
    private var revealJob: Job? = null
    private var hintJob: Job? = null

    /** Every country in the game, for the hint's subregion match and Easy's distractor top-up. */
    private var allCountries: List<Country> = emptyList()

    /** This round's items (the chosen region's members, or a practice set), keyed by code. */
    private var itemsByCode: Map<String, Country> = emptyMap()

    private val isEasy: Boolean get() = _difficulty.value == Difficulty.EASY

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            allCountries = countryRepository.getAllCountries().first()
            _difficulty.value = resolveDifficulty()
            savedState.difficulty = _difficulty.value
            updateTimerVisible()
            loadQuiz()
        }
    }

    /** Route tier, else the value saved for process death, else [Difficulty.DEFAULT]. */
    private fun resolveDifficulty(): Difficulty {
        val requested = routeDifficulty ?: savedState.difficulty ?: Difficulty.DEFAULT
        return requested.takeIf { it in gameMode.spec.supportedDifficulties } ?: Difficulty.NORMAL
    }

    private fun updateTimerVisible() {
        _timerVisible.value = _showTimer.value || _difficulty.value.timerAlwaysShown
    }

    private suspend fun loadQuiz() {
        val items = gameMode.generator.items(category)
        itemsByCode = items.associateBy { it.code }
        val quiz = Quiz(category = category, countries = items)

        // Loaded before Active is published (and so before syncTimerWithState starts the timer):
        // a tap or a hint should never race the map's own load, and the timer should not run
        // while the player is still looking at a loading spinner.
        val preset = (category as? QuizCategory.MapRegion)?.presetId?.let { RegionPreset.fromId(it) } ?: RegionPreset.WORLD
        loadMapLayers(preset, itemsByCode.keys)

        val snapshot = savedState.restore()
        var state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            snapshot.toGameState(quiz)
        } else {
            val base = QuizState(quiz = quiz, remainingOrder = items.map { it.code })
            TapMapGameState(quizState = base)
        }
        state = when {
            // Guard: a region or practice set with nothing to ask about (should not normally
            // happen) completes immediately instead of soft-locking with no target and no way
            // to finish.
            items.isEmpty() -> state.copy(quizState = state.quizState.copy(isComplete = true, isPaused = false))
            // Restored in the window between a third Hard strike and its game over (D36): the
            // round is over, whatever the pending flash would have done.
            hardLimitReached(state.quizState) && !state.quizState.isComplete ->
                state.copy(quizState = state.quizState.copy(isComplete = true, isPaused = false))
            // A fresh round, or a snapshot saved mid-transition: put a prompt on screen.
            !state.quizState.isComplete && state.targetCode == null -> nextPrompt(state)
            else -> state
        }
        _uiState.value = TapMapUiState.Active(state)
        onStateChanged()
        finishQuizIfComplete()
    }

    /**
     * Loads the country layers once (not re-run by any later state change) and frames [preset]
     * round [members]. A [QuizCategory.Practice] set is always on [RegionPreset.WORLD] (it can
     * span several continents), but it is still framed to its own members rather than shown at
     * the full-world fit, with a floor on the frame's size so a round of one tiny country is not
     * zoomed in absurdly far; picking "World" as the region itself keeps the full-world view, as
     * no single frame could fit "everywhere" sensibly. The frame itself is CPU-bound geometry
     * work (see [com.geoquiz.app.domain.usecase.GetCountryOutlineUseCase]), so it runs on
     * [defaultDispatcher], off the caller's dispatcher. [mapRepository] decodes and caches each
     * layer once for the life of the process, so this is cheap on every call after the first.
     * Shared with [TapMapNameAllViewModel] ([loadTapMapMapState], task 5.2b).
     */
    private suspend fun loadMapLayers(preset: RegionPreset, members: Set<String>) {
        _mapState.value = loadTapMapMapState(
            mapRepository = mapRepository,
            defaultDispatcher = defaultDispatcher,
            preset = preset,
            members = members,
            isPractice = category is QuizCategory.Practice
        )
    }

    /**
     * Pops the next code off [QuizState.remainingOrder] and puts it on screen: a [QuizState.choice]
     * at Easy, [TapMapGameState.currentTargetCode] otherwise. Clears every prompt's transient
     * fields. With nothing left, the round is complete.
     */
    private fun nextPrompt(state: TapMapGameState): TapMapGameState {
        // B2: whatever prompt a pending flash or reveal was about is gone now; its own captured
        // target no longer matches once a new one is on screen (see resolveAfterWrongTap), but
        // cancelling here too means a stale job never even runs.
        flashJob?.cancel()
        revealJob?.cancel()
        val qs = state.quizState
        val cleared = state.copy(
            wrongTapsThisPrompt = 0,
            hintUsedThisPrompt = false,
            wrongFlashCode = null,
            wrongFlashName = null,
            revealedCode = null,
            hintFlashCodes = emptySet()
        )
        val nextCode = qs.remainingOrder.firstOrNull()
            ?: return cleared.copy(
                quizState = qs.copy(
                    choice = null,
                    choiceFeedback = null,
                    isComplete = qs.quiz.countries.isNotEmpty(),
                    isPaused = false
                ),
                currentTargetCode = null
            )
        val newRemaining = qs.remainingOrder.drop(1)
        return if (isEasy) {
            val target = itemsByCode[nextCode] ?: return cleared
            val choice = gameMode.choiceGenerator.question(target, qs.quiz.countries, allCountries, category, random)
            cleared.copy(
                quizState = qs.copy(choice = choice, choiceFeedback = null, remainingOrder = newRemaining),
                currentTargetCode = null
            )
        } else {
            cleared.copy(
                quizState = qs.copy(choiceFeedback = null, remainingOrder = newRemaining),
                currentTargetCode = nextCode
            )
        }
    }

    /**
     * A tap resolved against the map (Normal and Hard only; Easy answers through
     * [onChoiceSelected]). Sea, non-playable land, a country outside this round's region, an
     * already-found country, an already-missed country and the country currently flashing wrong
     * are all ignored (D36, S1), as is any tap while paused or while a reveal is showing (B1), or
     * once this prompt's (Normal) or the round's (Hard) wrong-tap limit has been reached but the
     * flash or reveal that will act on it has not finished yet (B2, S1: so a strike count never
     * shows past its limit). The target is correct; any other member of the round is a wrong tap.
     */
    fun onFeatureTap(hit: MapHit?) {
        if (isEasy) return
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

    private fun correctTap(state: TapMapGameState, code: String) {
        flashJob?.cancel()
        revealJob?.cancel()
        val qs = state.quizState
        val updated = state.copy(quizState = qs.copy(answeredCountries = qs.answeredCountries + code))
        publish(nextPrompt(updated))
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
        finishQuizIfComplete()
    }

    private fun wrongTap(state: TapMapGameState, code: String) {
        val qs = state.quizState
        val tappedName = itemsByCode[code]?.name ?: return
        // Captured now, so the flash this schedules only acts on the Normal per-prompt reveal if
        // this is still the prompt on screen when it runs (B2).
        val promptTarget = state.currentTargetCode ?: return
        val wrongThisPrompt = state.wrongTapsThisPrompt + 1
        val totalWrong = qs.incorrectGuesses + 1
        val newQs = qs.copy(
            incorrectGuesses = totalWrong,
            incorrectGuessStrings = qs.incorrectGuessStrings + tappedName
        )
        publish(
            state.copy(
                quizState = newQs,
                wrongTapsThisPrompt = wrongThisPrompt,
                wrongFlashCode = code,
                wrongFlashName = tappedName
            )
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(WRONG_FLASH_MILLIS)
            resolveAfterWrongTap(promptTarget, totalWrong, wrongThisPrompt)
        }
    }

    /**
     * After the "That's X" flash: Hard ends at 3 total wrong (whatever prompt that lands on, so
     * it always counts); Normal reveals at 3 wrong on one prompt, but only if [promptTarget] is
     * still the prompt on screen (B2: a flash from a prompt the player has since moved past, by
     * finding or skipping it before this ran, must not reveal or miss the new one).
     */
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

    /** B1: only advances if [target] is still the prompt being revealed. */
    private fun finishReveal(target: String) {
        val active = activeOrNull() ?: return
        val state = active.state
        if (state.currentTargetCode != target) return
        val qs = state.quizState
        val newQs = qs.copy(missedCountries = qs.missedCountries + target)
        publish(nextPrompt(state.copy(quizState = newQs, revealedCode = null)))
        finishQuizIfComplete()
    }

    /** Easy: the player picked an option's country for the highlighted target. */
    fun onChoiceSelected(code: String) {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete || qs.isPaused || qs.choiceFeedback != null) return
        val choice = qs.choice ?: return
        val picked = choice.option(code) ?: return
        val target = choice.targetCode
        val updatedQs = if (code == target) {
            qs.copy(
                answeredCountries = qs.answeredCountries + target,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = true)
            )
        } else {
            qs.copy(
                missedCountries = qs.missedCountries + target,
                incorrectGuesses = qs.incorrectGuesses + 1,
                incorrectGuessStrings = qs.incorrectGuessStrings + picked.label,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = false)
            )
        }
        publish(active.state.copy(quizState = updatedQs))
        _feedbackEvents.tryEmit(if (code == target) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT)

        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(CHOICE_FEEDBACK_MILLIS)
            advanceAfterChoiceFeedback()
        }
    }

    private fun advanceAfterChoiceFeedback() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        // Advances even while paused (backgrounded mid-feedback): the pick is already recorded,
        // and skipping this would leave the options disabled after Resume with no way on (Easy
        // has no Skip, D39).
        if (qs.choiceFeedback == null || qs.isComplete) return
        publish(nextPrompt(active.state.copy(quizState = qs.copy(choiceFeedback = null))))
        finishQuizIfComplete()
    }

    /**
     * Marks the current prompt missed (no penalty beyond not finding it) and moves on. Hidden at
     * Easy (D39); still guarded here (B3) in case it is ever called while a reveal or an Easy
     * pick's feedback is showing.
     */
    fun onSkip() {
        val active = activeOrNull() ?: return
        val state = active.state
        val qs = state.quizState
        if (qs.isComplete || qs.isPaused || state.revealedCode != null || qs.choiceFeedback != null) return
        // Hard's third strike is already counted: end the round now rather than letting Skip
        // cancel the pending game over (D36).
        if (hardLimitReached(qs)) return endRound(state)
        val target = state.targetCode ?: return
        flashJob?.cancel()
        revealJob?.cancel()
        publish(nextPrompt(state.copy(quizState = qs.copy(missedCountries = qs.missedCountries + target))))
        finishQuizIfComplete()
    }

    /**
     * FLASH_REGION (D37): briefly highlights the target's UN subregion mates, and the target
     * itself (S9: otherwise the highlighted target would leave a revealing hole in the flash).
     * Once per prompt; hidden at Easy (D39), guarded here (B3) all the same. A no-op once this
     * round has already used its difficulty tier's hint cap (task 8.4, `HintCosts.maxHintsPerRound`).
     */
    fun onHint() {
        val active = activeOrNull() ?: return
        val state = active.state
        val qs = state.quizState
        if (qs.isComplete || qs.isPaused || state.hintUsedThisPrompt) return
        if (state.revealedCode != null || qs.choiceFeedback != null) return
        if (hardLimitReached(qs)) return
        if (qs.hintsUsed.size >= HintCosts.maxHintsPerRound(_difficulty.value)) return
        val targetCode = state.targetCode ?: return
        val target = itemsByCode[targetCode] ?: allCountries.find { it.code == targetCode } ?: return
        val mates = allCountries.filter { it.subregion == target.subregion }
            .mapTo(mutableSetOf()) { it.code }
        publish(
            state.copy(
                quizState = qs.copy(hintsUsed = qs.hintsUsed + HintType.FLASH_REGION),
                hintUsedThisPrompt = true,
                hintFlashCodes = mates
            )
        )
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_FLASH_MILLIS)
            val current = activeOrNull() ?: return@launch
            publish(current.state.copy(hintFlashCodes = emptySet()))
        }
    }

    fun onGiveUp() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete) return
        flashJob?.cancel()
        revealJob?.cancel()
        hintJob?.cancel()
        publish(active.state.copy(quizState = qs.copy(isComplete = true, isPaused = false)))
        finishQuizIfComplete()
    }

    /**
     * Pauses on backgrounding, exactly like the classic quiz
     * ([com.geoquiz.app.ui.quiz.QuizViewModel.onBackgrounded]): saves so a killed process comes
     * back paused too (D37: no Room resume save, [TapMapSavedState] is process death's only
     * record). Rotation does not call this (`TapMapGameScreen`'s `LifecycleEventEffect` skips it
     * while `isChangingConfigurations`).
     */
    fun onBackgrounded() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete || qs.isPaused) return
        publish(active.state.copy(quizState = qs.copy(isPaused = true)))
    }

    /** The pause overlay's only control (resume); nothing else can reach it while paused. */
    fun togglePause() {
        val active = activeOrNull() ?: return
        val qs = active.state.quizState
        if (qs.isComplete) return
        publish(active.state.copy(quizState = qs.copy(isPaused = !qs.isPaused)))
    }

    private fun activeOrNull(): TapMapUiState.Active? = _uiState.value as? TapMapUiState.Active

    /** Hard only: the strike limit has been reached (the round is over, or about to be). */
    private fun hardLimitReached(qs: QuizState): Boolean =
        _difficulty.value == Difficulty.HARD &&
            qs.incorrectGuesses >= (Difficulty.HARD.strikeLimit ?: HARD_STRIKE_LIMIT_FALLBACK)

    private fun endRound(state: TapMapGameState) {
        flashJob?.cancel()
        revealJob?.cancel()
        hintJob?.cancel()
        publish(
            state.copy(
                wrongFlashCode = null,
                wrongFlashName = null,
                quizState = state.quizState.copy(isComplete = true, isPaused = false)
            )
        )
        finishQuizIfComplete()
    }

    private fun publish(state: TapMapGameState) {
        _uiState.value = TapMapUiState.Active(state)
        onStateChanged()
    }

    private fun onStateChanged() {
        syncTimerWithState()
        val current = _uiState.value
        if (current is TapMapUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is TapMapUiState.Active &&
            !current.state.quizState.isComplete &&
            !current.state.quizState.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    /**
     * Records a finished round once (see [CompleteQuizUseCase]) and publishes [completion]. The
     * full item list (every member asked, found or not) is the "quiz" ([QuizState.quiz]), so
     * [com.geoquiz.app.domain.model.CompletedQuiz.missedCodes] and "Practise the ones you missed"
     * work whether the round finished by asking everyone, Hard strikes or Give up.
     */
    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is TapMapUiState.Active || !current.state.quizState.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state.quizState,
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
        const val ARG_VARIANT = "variant"
        const val ARG_CATEGORY_TYPE = "categoryType"
        const val ARG_CATEGORY_VALUE = "categoryValue"
        const val ARG_DIFFICULTY = "difficulty"

        /** How long a wrong tap's "That's X" flash stays up. */
        const val WRONG_FLASH_MILLIS = 900L

        /** Normal: how many wrong taps on one prompt before it is revealed. */
        const val NORMAL_REVEAL_LIMIT = 3

        /** How long a revealed target, or a FLASH_REGION hint, stays highlighted. */
        const val REVEAL_MILLIS = 1_500L
        const val HINT_FLASH_MILLIS = 1_500L

        /** Easy: how long a pick's result stays on the options before the next question. */
        const val CHOICE_FEEDBACK_MILLIS = 900L

        /** Used only if [Difficulty.HARD] ever had no strike limit; it always does. */
        private const val HARD_STRIKE_LIMIT_FALLBACK = 3

        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

/**
 * One Tap the map round. [quizState] is a plain [QuizState] (its [QuizState.quiz] holds every
 * item this round asks about), reused so [CompleteQuizUseCase] scores a round exactly like any
 * other mode. The rest is this screen's own: which code is the current target at Normal/Hard
 * (Easy keeps its target on [QuizState.choice] instead) and the transient tap feedback.
 */
data class TapMapGameState(
    val quizState: QuizState,
    /** Normal/Hard only; null at Easy (see [QuizState.choice]) or once the round is complete. */
    val currentTargetCode: String? = null,
    /** Wrong taps on the current prompt (Normal: reveals it at [TapMapGameViewModel.NORMAL_REVEAL_LIMIT]). */
    val wrongTapsThisPrompt: Int = 0,
    val hintUsedThisPrompt: Boolean = false,
    /** The country just wrongly tapped, briefly shown orange with its name. */
    val wrongFlashCode: String? = null,
    val wrongFlashName: String? = null,
    /** Normal: the target being revealed (highlighted) before it is marked missed. */
    val revealedCode: String? = null,
    /** FLASH_REGION hint: the target's UN subregion mates, briefly highlighted. */
    val hintFlashCodes: Set<String> = emptySet()
) {
    /** The country being asked about, at any difficulty. */
    val targetCode: String? get() = quizState.choice?.targetCode ?: currentTargetCode
}

sealed class TapMapUiState {
    data object Loading : TapMapUiState()
    data class Active(val state: TapMapGameState) : TapMapUiState()
}

/** The map's own, slower-changing state: layers, the preset in play and its frame. */
data class TapMapMapState(
    val preset: RegionPreset = RegionPreset.WORLD,
    val members: Set<String> = emptySet(),
    val coarse: GeoLayer? = null,
    val detail: GeoLayer? = null,
    val tapZones: GeoLayer? = null,
    val frame: PlaneRect? = null,
    /** True once loading has finished and every layer came back null (nit: show an error). */
    val loadFailed: Boolean = false
) {
    val isLoaded: Boolean get() = coarse != null || detail != null
}

/** A recorded round on its way to Results (see `QuizCompletion`). */
data class TapMapCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}
