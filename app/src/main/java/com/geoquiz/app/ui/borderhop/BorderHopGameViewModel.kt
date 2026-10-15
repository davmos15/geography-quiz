package com.geoquiz.app.ui.borderhop

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.borderhop.BorderGraphRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.borderhop.BorderGraph
import com.geoquiz.app.domain.mode.borderhop.BorderHopGameMode
import com.geoquiz.app.domain.mode.borderhop.BorderHopScoring
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One Border hop round (task 6.1, D44): its own screen and ViewModel (D37), no Room resume save.
 * There is no "wrong tap": [onHop] only accepts one of [BorderGraph.neighboursOf] the current
 * country, so the round only ever ends by reaching the target ([BorderHopState.reachedTarget]) or
 * [onGiveUp]. [onUndo] is a free, core mechanic, not a hint: it drops the last hop and returns to
 * the previous country. [onHint] is [HintType.SHOW_VALID_HOP] (placeholder cost, D37/D44).
 */
@HiltViewModel
class BorderHopGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val gameMode: BorderHopGameMode,
    private val borderGraphRepository: BorderGraphRepository,
    private val countryRepository: CountryRepository,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom
) : ViewModel() {

    private val categoryType: String = savedStateHandle[ARG_CATEGORY_TYPE] ?: "all"
    private val categoryValueRaw: String = savedStateHandle[ARG_CATEGORY_VALUE] ?: "_"
    private val categoryValue: String = Uri.decode(categoryValueRaw)
    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<BorderHopUiState>(BorderHopUiState.Loading)
    val uiState: StateFlow<BorderHopUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    private val _timerVisible = MutableStateFlow(true)
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<BorderHopCompletion?>(null)
    val completion: StateFlow<BorderHopCompletion?> = _completion.asStateFlow()

    private val savedState = BorderHopSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var completionJob: Job? = null

    private lateinit var borderGraph: BorderGraph
    private var allCountriesByCode: Map<String, Country> = emptyMap()

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
            val difficulty = requested.takeIf { it in gameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty
            _timerVisible.value = _showTimer.value || difficulty.timerAlwaysShown

            val allCountries = countryRepository.getAllCountries().first()
            allCountriesByCode = allCountries.associateBy { it.code }
            borderGraph = borderGraphRepository.graph()

            loadRound(difficulty)
        }
    }

    private suspend fun loadRound(difficulty: Difficulty) {
        val snapshot = savedState.restore()
        if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            commit(snapshot.toState(category, difficulty))
            finishIfComplete()
            return
        }

        val candidates = gameMode.generator.items(category).map { it.code }
        val puzzle = borderGraph.randomPuzzle(candidates, BorderHopGameMode.hopRangeFor(difficulty), random)
        if (puzzle == null) {
            _uiState.value = BorderHopUiState.Unavailable
            return
        }
        val state = BorderHopState(
            category = category,
            difficulty = difficulty,
            startCode = puzzle.startCode,
            targetCode = puzzle.targetCode,
            parHops = puzzle.parHops,
            path = listOf(puzzle.startCode)
        )
        commit(state)
    }

    private fun activeState(): BorderHopState? = (_uiState.value as? BorderHopUiState.Active)?.state

    private fun commit(state: BorderHopState) {
        _uiState.value = BorderHopUiState.Active(state)
        syncTimer(state)
        savedState.save(state, quizTimer.snapshot())
    }

    private fun syncTimer(state: BorderHopState) {
        if (state.isComplete || state.paused) quizTimer.pause() else quizTimer.resume()
        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch { quizTimer.ticks().collect { _timerSeconds.value = it } }
        }
    }

    /** Every land-border neighbour of the current country, as full [Country]s, name-sorted. */
    fun neighbourOptions(): List<Country> {
        val state = activeState() ?: return emptyList()
        if (state.isComplete || state.paused) return emptyList()
        return borderGraph.neighboursOf(state.currentCode)
            .mapNotNull { allCountriesByCode[it] }
            .sortedBy { it.name }
    }

    fun countryFor(code: String): Country? = allCountriesByCode[code]

    /** A tap on one of [neighbourOptions]; anything else (including a stale option) is ignored. */
    fun onHop(code: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.paused) return
        if (code !in borderGraph.neighboursOf(state.currentCode)) return
        val newPath = state.path + code
        val updated = state.copy(
            path = newPath,
            revealedHopCode = null,
            isComplete = code == state.targetCode
        )
        commit(updated)
        finishIfComplete()
    }

    /** Free, core mechanic (not a hint): drops the last hop and returns to the previous country. */
    fun onUndo() {
        val state = activeState() ?: return
        if (!state.canUndo) return
        commit(state.copy(path = state.path.dropLast(1), revealedHopCode = null))
    }

    /**
     * [HintType.SHOW_VALID_HOP]: reveals one neighbour of the current country that is still on a
     * shortest path to the target (its distance to the target is exactly one less than the
     * current country's). Usable again once the player moves (a hop or an undo clears the
     * reveal); tapping it again at the same position costs nothing more.
     */
    fun onHint() {
        val state = activeState() ?: return
        if (state.isComplete || state.paused) return
        if (state.revealedHopCode != null) return
        val distances = borderGraph.distancesFrom(state.targetCode)
        val currentDistance = distances[state.currentCode] ?: return
        val validHop = borderGraph.neighboursOf(state.currentCode)
            .firstOrNull { (distances[it] ?: Int.MAX_VALUE) == currentDistance - 1 }
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
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = pathCountries),
            answeredCountries = pathCountries.map { it.code }.toSet(),
            incorrectGuesses = if (state.reachedTarget) {
                (state.hopsUsed - state.parHops).coerceAtLeast(0)
            } else {
                state.hopsUsed
            },
            currentInput = if (state.reachedTarget) BorderHopScoring.FLAG_REACHED else BorderHopScoring.FLAG_GAVE_UP,
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
    }
}
