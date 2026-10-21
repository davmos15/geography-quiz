package com.geoquiz.app.ui.borderhop

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.di.DefaultDispatcher
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.domain.map.frame
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopChoiceBuilder
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopMove
import com.geoquiz.app.domain.mode.borderhop.BorderHopMoves
import com.geoquiz.app.domain.mode.borderhop.BorderHopScoring
import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.usecase.CountryNameSuggester
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import com.geoquiz.app.ui.tapmap.withMinimumFrameSize
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One Border hop round (task 6.1, D44; map version task P10-C): its own screen and ViewModel
 * (D37), no Room resume save. The player extends the route by naming a country that borders the
 * end of it; every guess is judged by [BorderHopMoves.judge]:
 *
 * - Normal/Hard ([onSubmit], [onSuggestionSelected]): typed text is resolved against every
 *   country's aliases ([BorderHopMoves.resolve]; typos forgiven only at Normal, which also gets
 *   [CountryNameSuggester] suggestions). Not recognised or already on the route costs nothing; a
 *   recognised non-neighbour is a wrong guess, flashed on the map, and Hard ends the round at its
 *   strike limit.
 * - Easy ([onOptionSelected]): four options from [BorderHopChoiceBuilder], exactly one valid; a
 *   wrong pick is a wrong guess and disables that option; a right one shows for
 *   [CORRECT_PICK_MILLIS] before the next options.
 *
 * [onUndo] is a free, core mechanic, not a hint. [onHint] is [HintType.SHOW_VALID_HOP] (capped
 * per round, hidden at Easy, D55/D62). [routeFrame] is the camera target for the map: start,
 * target and route (plus a flashed wrong guess while it shows).
 */
@HiltViewModel
class BorderHopGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameMode: BorderHopGameMode,
    private val borderGraphRepository: BorderGraphRepository,
    private val countryRepository: CountryRepository,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val mapRepository: MapRepository,
    private val normalizeInput: NormalizeInputUseCase,
    private val suggester: CountryNameSuggester,
    clock: MonotonicClock,
    quizRandom: QuizRandom,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val categoryType: String = savedStateHandle[ARG_CATEGORY_TYPE] ?: "all"
    private val categoryValueRaw: String = savedStateHandle[ARG_CATEGORY_VALUE] ?: "_"
    private val categoryValue: String = Uri.decode(categoryValueRaw)
    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<BorderHopUiState>(BorderHopUiState.Loading)
    val uiState: StateFlow<BorderHopUiState> = _uiState.asStateFlow()

    private val _mapState = MutableStateFlow(BorderHopMapState())
    val mapState: StateFlow<BorderHopMapState> = _mapState.asStateFlow()

    private val _routeFrame = MutableStateFlow<BorderHopRouteFrame?>(null)
    val routeFrame: StateFlow<BorderHopRouteFrame?> = _routeFrame.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<BorderHopCompletion?>(null)
    val completion: StateFlow<BorderHopCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = BorderHopSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var completionJob: Job? = null
    private var flashJob: Job? = null
    private var advanceJob: Job? = null
    private var frameJob: Job? = null

    private lateinit var borderGraph: BorderGraph
    private var allCountries: List<Country> = emptyList()
    private var allCountriesByCode: Map<String, Country> = emptyMap()
    private var aliases: List<AnswerAlias> = emptyList()

    init {
        viewModelScope.launch {
            val showTimer = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: Difficulty.DEFAULT
            val difficulty = requested.takeIf { it in gameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty
            _timerVisible.value = showTimer || difficulty.timerAlwaysShown

            allCountries = countryRepository.getAllCountries().first()
            allCountriesByCode = allCountries.associateBy { it.code }
            borderGraph = borderGraphRepository.graph()
            if (difficulty != Difficulty.EASY) aliases = countryRepository.getCountryAnswerAliases()
            // Loaded before Active is published (like Tap the map's Name all): the timer should
            // not run while the player is still looking at a loading spinner.
            _mapState.value = loadMapState()

            loadRound(difficulty)
        }
    }

    private suspend fun loadMapState(): BorderHopMapState {
        val coarse = runCatching { mapRepository.layer(GeoLayerId.ADMIN0_110M) }.getOrNull()
        val detail = runCatching { mapRepository.layer(GeoLayerId.ADMIN0_50M) }.getOrNull()
        val tapZones = runCatching { mapRepository.layer(GeoLayerId.TAP_ZONES) }.getOrNull()
        return BorderHopMapState(coarse = coarse, detail = detail, tapZones = tapZones, loadFailed = coarse == null && detail == null)
    }

    private suspend fun loadRound(difficulty: Difficulty) {
        val snapshot = savedState.restore()
        if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            var restored = snapshot.toState(category, difficulty)
            // A right Easy pick was still showing: move straight on to the next options.
            if (restored.correctPick != null && !restored.isComplete) restored = withNewOptions(restored)
            restored = restored.copy(suggestions = suggestionsFor(restored, restored.input))
            commit(restored)
            finishIfComplete()
            return
        }

        val candidates = gameMode.generator.items(category).map { it.code }
        val puzzle = borderGraph.randomPuzzle(candidates, BorderHopGameMode.hopRangeFor(difficulty), random)
        if (puzzle == null) {
            _uiState.value = BorderHopUiState.Unavailable
            return
        }
        var state = BorderHopState(
            category = category,
            difficulty = difficulty,
            startCode = puzzle.startCode,
            targetCode = puzzle.targetCode,
            parHops = puzzle.parHops,
            path = listOf(puzzle.startCode)
        )
        if (difficulty == Difficulty.EASY) state = withNewOptions(state)
        commit(state)
    }

    private fun activeState(): BorderHopState? = (_uiState.value as? BorderHopUiState.Active)?.state

    private fun commit(state: BorderHopState) {
        val resolved = state.copy(hintAvailable = hintAvailable(state))
        _uiState.value = BorderHopUiState.Active(resolved)
        syncTimer(resolved)
        savedState.save(resolved, quizTimer.snapshot())
        updateRouteFrame(resolved)
    }

    /** Whether [HintType.SHOW_VALID_HOP] has anything left to reveal from [state]'s position
     * (P10-R2 L5): false once every neighbour one hop closer to the target is already on the
     * route (e.g. an earlier detour visited them all already). */
    private fun hintAvailable(state: BorderHopState): Boolean =
        BorderHopMoves.closerNeighbours(borderGraph, state.currentCode, state.targetCode).any { it !in state.path }

    private fun syncTimer(state: BorderHopState) {
        if (state.isComplete || state.paused) quizTimer.pause() else quizTimer.resume()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    /**
     * Re-frames the camera target when the set of countries to show changes: start, target,
     * every hop and, while it flashes, a wrong guess (so the player sees where it is). Never
     * smaller than [MIN_FRAME_WIDTH] by [MIN_FRAME_HEIGHT] plane units, so two small neighbours
     * don't zoom in absurdly far.
     *
     * If the new codes already match the published frame, any still in-flight [frameJob] is
     * cancelled rather than left to publish later (P10-R2 L1): otherwise a hop followed by a
     * quick Undo back to the same codes could let the hop's stale frame land after the undo,
     * since the undo's call would see the still-current (pre-hop) published frame as a match and
     * return without touching the hop's pending job.
     */
    private fun updateRouteFrame(state: BorderHopState) {
        val routeCodes = (state.path + state.targetCode).toSet()
        val codes = state.flashWrongCode?.let { routeCodes + it } ?: routeCodes
        if (_routeFrame.value?.codes == codes) {
            frameJob?.cancel()
            return
        }
        val layer = _mapState.value.detail ?: _mapState.value.coarse ?: return
        frameJob?.cancel()
        frameJob = viewModelScope.launch {
            val rect = withContext(defaultDispatcher) {
                RegionPreset.WORLD.frame(layer, codes)?.withMinimumFrameSize(MIN_FRAME_WIDTH, MIN_FRAME_HEIGHT)
            } ?: return@launch
            _routeFrame.value = BorderHopRouteFrame(routeCodes, codes, rect)
        }
    }

    fun countryFor(code: String): Country? = allCountriesByCode[code]

    // ---- Normal / Hard: typed guesses ---------------------------------------------------------

    fun onInputChange(input: String) {
        val state = activeState() ?: return
        if (!state.acceptsInput) return
        _uiState.value = BorderHopUiState.Active(state.copy(input = input, suggestions = suggestionsFor(state, input)))
        savedState.saveInput(input)
    }

    private fun suggestionsFor(state: BorderHopState, input: String): List<String> =
        if (state.difficulty != Difficulty.NORMAL || input.isBlank()) {
            emptyList()
        } else {
            suggester.suggest(input, aliases).map { it.code }
        }

    /** Submits the field: Normal forgives one typo, Hard needs an exact name (D12's spirit). */
    fun onSubmit() {
        val state = activeState() ?: return
        if (!state.acceptsInput || state.difficulty == Difficulty.EASY) return
        val input = state.input.trim()
        if (input.isEmpty()) return
        val country = BorderHopMoves.resolve(normalizeInput(input), aliases, allowFuzzy = state.difficulty.allowsTypos)
        if (country == null) {
            commit(state.copy(feedback = BorderHopFeedback.NotRecognised(input), suggestions = emptyList()))
            _feedbackEvents.tryEmit(AnswerFeedbackEvent.NEAR_MISS)
            return
        }
        applyGuess(state, country.code)
    }

    /** A tapped suggestion submits that country directly (Normal only). */
    fun onSuggestionSelected(code: String) {
        val state = activeState() ?: return
        if (!state.acceptsInput || state.difficulty != Difficulty.NORMAL) return
        if (code !in allCountriesByCode) return
        applyGuess(state, code)
    }

    private fun applyGuess(state: BorderHopState, code: String) {
        when (val move = BorderHopMoves.judge(borderGraph, state.path, state.targetCode, code)) {
            is BorderHopMove.AlreadyOnRoute -> {
                commit(state.copy(input = "", suggestions = emptyList(), feedback = BorderHopFeedback.AlreadyOnRoute(code)))
                _feedbackEvents.tryEmit(AnswerFeedbackEvent.ALREADY_ANSWERED)
            }
            is BorderHopMove.NotNeighbour -> {
                commitWrongGuess(
                    state.copy(input = "", suggestions = emptyList(), feedback = BorderHopFeedback.NotNeighbour(code, move.fromCode)),
                    code
                )
            }
            is BorderHopMove.Hop -> {
                commit(
                    state.copy(
                        path = state.path + code,
                        input = "",
                        suggestions = emptyList(),
                        feedback = BorderHopFeedback.Hopped(code),
                        revealedHopCode = null,
                        flashWrongCode = null,
                        isComplete = move.reachesTarget
                    )
                )
                _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
                finishIfComplete()
            }
            BorderHopMove.NotRecognised -> Unit
        }
    }

    /**
     * Counts a wrong guess, flashes it on the map and ends a Hard round at its strike limit. The
     * feedback (already set on [state] by the caller) always shows again, but naming the same
     * non-neighbour again from the same current country - the same `fromCode>code` pair - is not
     * a second strike or a duplicate [BorderHopState.wrongGuesses] entry (P10-R2 M1).
     */
    private fun commitWrongGuess(state: BorderHopState, code: String) {
        val pairKey = wrongPairKey(state.currentCode, code)
        val repeat = pairKey in state.wrongPairs
        val wrongGuesses = if (repeat) state.wrongGuesses else state.wrongGuesses + code
        val wrongPairs = if (repeat) state.wrongPairs else state.wrongPairs + pairKey
        val struckOut = if (repeat) false else state.difficulty.strikeLimit?.let { wrongGuesses.size >= it } ?: false
        commit(
            state.copy(
                wrongGuesses = wrongGuesses,
                wrongPairs = wrongPairs,
                flashWrongCode = code,
                isComplete = state.isComplete || struckOut,
                outOfGuesses = state.outOfGuesses || struckOut
            )
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(WRONG_FLASH_MILLIS)
            val current = activeState() ?: return@launch
            if (current.flashWrongCode == code) commit(current.copy(flashWrongCode = null))
        }
        if (!repeat) finishIfComplete()
    }

    // ---- Easy: four options -------------------------------------------------------------------

    /** A pick from [BorderHopState.options]; a wrongly picked or stale option is ignored. */
    fun onOptionSelected(code: String) {
        val state = activeState() ?: return
        if (!state.acceptsInput || state.difficulty != Difficulty.EASY) return
        if (code !in state.options || code in state.wrongPicks) return
        when (val move = BorderHopMoves.judge(borderGraph, state.path, state.targetCode, code)) {
            is BorderHopMove.Hop -> {
                commit(
                    state.copy(
                        path = state.path + code,
                        correctPick = code,
                        feedback = BorderHopFeedback.Hopped(code),
                        flashWrongCode = null,
                        isComplete = move.reachesTarget
                    )
                )
                _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
                if (move.reachesTarget) {
                    finishIfComplete()
                } else {
                    advanceJob?.cancel()
                    advanceJob = viewModelScope.launch {
                        delay(CORRECT_PICK_MILLIS)
                        val current = activeState() ?: return@launch
                        if (current.correctPick == code && !current.isComplete) commit(withNewOptions(current))
                    }
                }
            }
            is BorderHopMove.NotNeighbour, is BorderHopMove.AlreadyOnRoute -> {
                commitWrongGuess(
                    state.copy(
                        wrongPicks = state.wrongPicks + code,
                        feedback = BorderHopFeedback.NotNeighbour(code, state.currentCode)
                    ),
                    code
                )
            }
            BorderHopMove.NotRecognised -> Unit
        }
    }

    private fun withNewOptions(state: BorderHopState): BorderHopState {
        val choice = BorderHopChoiceBuilder.build(borderGraph, state.path, state.targetCode, allCountries, random)
        return state.copy(options = choice?.options.orEmpty(), wrongPicks = emptySet(), correctPick = null)
    }

    // ---- Shared ---------------------------------------------------------------------------------

    /** Free, core mechanic (not a hint): drops the last hop and returns to the previous country. */
    fun onUndo() {
        val state = activeState() ?: return
        if (!state.canUndo) return
        var updated = state.copy(
            path = state.path.dropLast(1),
            revealedHopCode = null,
            feedback = null,
            flashWrongCode = null
        )
        if (state.difficulty == Difficulty.EASY) updated = withNewOptions(updated)
        commit(updated)
    }

    /**
     * [HintType.SHOW_VALID_HOP]: reveals one neighbour of the current country that is still on a
     * shortest route to the target. Usable again once the player moves (a hop or an undo clears
     * the reveal); tapping it again at the same position costs nothing more. A no-op once this
     * round has used its tier's hint cap (task 8.4, `HintCosts.maxHintsPerRound`; 0 at Easy), or
     * when [BorderHopState.hintAvailable] is false (every closer neighbour is already on the
     * route; the screen disables the button in that state, P10-R2 L5).
     */
    fun onHint() {
        val state = activeState() ?: return
        if (!state.acceptsInput) return
        if (state.revealedHopCode != null) return
        if (state.hintsUsed.size >= HintCosts.maxHintsPerRound(state.difficulty)) return
        if (!state.hintAvailable) return
        val validHop = BorderHopMoves.closerNeighbours(borderGraph, state.currentCode, state.targetCode)
            .firstOrNull { it !in state.path }
            ?: return
        commit(state.copy(hintsUsed = state.hintsUsed + HintType.SHOW_VALID_HOP, revealedHopCode = validHop))
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        commit(state.copy(isComplete = true, gaveUp = true, paused = false))
        finishIfComplete()
    }

    /** Pauses on backgrounding, like the classic quiz and the other new modes (D37). */
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

    /**
     * Records the round once ([CompleteQuizUseCase]) and publishes [completion]. See
     * [BorderHopScoring] for how the round is packed into a [QuizState].
     */
    private fun finishIfComplete() {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val pathCountries = state.path.mapNotNull { allCountriesByCode[it] }
        val reached = state.reachedTarget
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = pathCountries),
            answeredCountries = pathCountries.map { it.code }.toSet(),
            incorrectGuesses = BorderHopScoring.incorrectGuessesFor(
                reached = reached,
                hopsUsed = state.hopsUsed,
                parHops = state.parHops,
                wrongGuesses = state.wrongGuesses.size
            ),
            incorrectGuessStrings = state.wrongGuesses.map { allCountriesByCode[it]?.name ?: it },
            currentInput = when {
                reached -> BorderHopScoring.FLAG_REACHED
                state.outOfGuesses -> BorderHopScoring.FLAG_OUT_OF_GUESSES
                else -> BorderHopScoring.FLAG_GAVE_UP
            },
            hintsUsed = state.hintsUsed,
            isComplete = true
        )
        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = BorderHopGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = state.difficulty,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = BorderHopCompletion(resultId = resultId, step = BorderHopCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = BorderHopCompletion.Step.DONE)
    }

    companion object {
        const val ARG_CATEGORY_TYPE = "categoryType"
        const val ARG_CATEGORY_VALUE = "categoryValue"
        const val ARG_DIFFICULTY = "difficulty"

        /** How long a wrong guess stays orange + cross on the map. */
        const val WRONG_FLASH_MILLIS = 1_500L

        /** How long a right Easy pick shows (blue + tick) before the next options. */
        const val CORRECT_PICK_MILLIS = 900L

        /** Minimum route frame, in Equal Earth plane units (the world is about 5.4 by 2.6). */
        const val MIN_FRAME_WIDTH = 0.8f
        const val MIN_FRAME_HEIGHT = 0.4f

        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}

/**
 * How the map shows a round (task P10-C): the start [MapFeatureState.Start], the target
 * [MapFeatureState.End], every hop [MapFeatureState.Found] (blue + tick; the target too once
 * reached), the hinted hop [MapFeatureState.Highlighted] and a wrong guess, while it flashes,
 * [MapFeatureState.Wrong] (orange + cross). Every state differs by more than colour (`MapCanvas`).
 */
/** The [BorderHopState.wrongPairs] key for naming [code] as a neighbour of [fromCode]. */
internal fun wrongPairKey(fromCode: String, code: String): String = "$fromCode>$code"

internal fun borderHopFeatureStates(state: BorderHopState): Map<String, MapFeatureState> = buildMap {
    state.revealedHopCode?.let { put(it, MapFeatureState.Highlighted) }
    state.flashWrongCode?.let { put(it, MapFeatureState.Wrong) }
    put(state.targetCode, MapFeatureState.End)
    state.path.drop(1).forEach { put(it, MapFeatureState.Found) }
    put(state.startCode, MapFeatureState.Start)
}
