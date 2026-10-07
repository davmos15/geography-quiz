package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.net.Uri
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    gameModes: GameModeRegistry,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    private val savedQuizRepository: SavedQuizRepository,
    private val adManager: AdManager,
    clock: MonotonicClock
) : ViewModel() {

    private val quizModeId: String = savedStateHandle["quizMode"] ?: "countries"
    val quizMode: QuizMode = QuizMode.fromId(quizModeId)

    /** Generator, validator and scoring for this quiz; unknown ids fall back to Countries. */
    private val gameMode: GameMode = gameModes.findOrDefault(quizModeId)

    /** Labels and icon of this quiz's mode. */
    val modeSpec: GameModeSpec get() = gameMode.spec

    val challengeId: String? = savedStateHandle.get<String>("challengeId")?.takeIf { it.isNotBlank() }

    private val categoryType: String = savedStateHandle["categoryType"] ?: "all"
    private val categoryValueRaw: String = savedStateHandle["categoryValue"] ?: "_"
    private val categoryValue: String = Uri.decode(categoryValueRaw)

    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    /** Tier asked for by the route (`?difficulty=`), or null for old callers, challenges and resume. */
    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    val showTimer: StateFlow<Boolean> = _showTimer.asStateFlow()

    private val _showFlags = MutableStateFlow(false)
    val showFlags: StateFlow<Boolean> = _showFlags.asStateFlow()

    private val _showCountryHint = MutableStateFlow(false)
    val showCountryHint: StateFlow<Boolean> = _showCountryHint.asStateFlow()

    private val _difficulty = MutableStateFlow(Difficulty.DEFAULT)

    /**
     * The tier this quiz is played at. Resolved once before the quiz loads (route, then the
     * value saved for process death, then the resume save, then the remembered default) and
     * never changes during the quiz. Read it once [uiState] is [QuizUiState.Active].
     */
    val difficulty: StateFlow<Difficulty> = _difficulty.asStateFlow()

    private val _timerVisible = MutableStateFlow(true)

    /** Whether the timer is on screen: the "Show timer" setting, except Hard always shows it (D15). */
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _newAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val newAchievements: StateFlow<List<Achievement>> = _newAchievements.asStateFlow()

    /** Set once the finished quiz has been recorded; the screen then moves on to Results. */
    private val _completion = MutableStateFlow<QuizCompletion?>(null)
    val completion: StateFlow<QuizCompletion?> = _completion.asStateFlow()

    private val savedState = QuizSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private var tickerJob: Job? = null
    private var completionJob: Job? = null

    init {
        adManager.preloadInterstitial()
        viewModelScope.launch {
            // Settings first: the difficulty decides typo tolerance and strikes and is recorded
            // with the result.
            _showTimer.value = settingsRepository.showTimer.first()
            _showFlags.value = settingsRepository.showFlags.first()
            _showCountryHint.value = settingsRepository.showCountryHint.first()
            val savedQuiz = savedQuizRepository.getSavedQuiz()
            _difficulty.value = resolveDifficulty(savedQuiz)
            savedState.difficulty = _difficulty.value
            updateTimerVisible()
            loadQuiz(savedQuiz)
        }
    }

    /**
     * Route > value saved for process death > the matching resume save's tier > the remembered
     * default. Challenges never run at Easy ([Difficulty.forChallenge]), and a tier the mode
     * does not offer falls back to Normal.
     */
    private suspend fun resolveDifficulty(savedQuiz: SavedQuizEntity?): Difficulty {
        val requested = routeDifficulty
            ?: savedState.difficulty
            ?: savedQuiz?.takeIf { it.isSameQuiz() }?.let { Difficulty.fromIdOrDefault(it.difficulty) }
        val resolved = if (challengeId != null) {
            Difficulty.forChallenge(requested, settingsRepository.difficulty.first())
        } else {
            requested ?: settingsRepository.difficulty.first()
        }
        return resolved.takeIf { it in gameMode.spec.supportedDifficulties } ?: Difficulty.NORMAL
    }

    /** Same mode and category as this quiz (the tier is checked separately). */
    private fun SavedQuizEntity.isSameQuiz(): Boolean =
        categoryType == this@QuizViewModel.categoryType &&
            categoryValue == this@QuizViewModel.categoryValue &&
            quizMode == quizModeId

    private fun updateTimerVisible() {
        _timerVisible.value = _showTimer.value || _difficulty.value.timerAlwaysShown
    }

    private suspend fun loadQuiz(savedQuiz: SavedQuizEntity?) {
        // TODO(3.2b): Easy is multiple choice (D14). Branch here on
        //  `_difficulty.value == Difficulty.EASY` to build the 4-option questions and their
        //  QuizState; until then Easy runs as a typed quiz with the Normal rules.
        val countries = gameMode.generator.items(category)
        val quiz = Quiz(
            category = category,
            countries = countries,
            timerSeconds = null
        )

        // 1. Progress in the SavedStateHandle: this screen was recreated after process death
        //    or activity destruction. It wins over the "Resume quiz" save in Room.
        // 2. The Room save ("Resume quiz" on the home screen) for the same quiz.
        //    It must also match the tier: a Hard save is not resumed as a Normal quiz.
        // 3. A fresh quiz.
        val snapshot = savedState.restore()
        val roomSaveMatches = savedQuiz != null && savedQuiz.isSameQuiz() &&
            Difficulty.fromIdOrDefault(savedQuiz.difficulty) == _difficulty.value
        if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            _uiState.value = QuizUiState.Active(snapshot.toQuizState(quiz))
            if (roomSaveMatches) savedQuizRepository.clearSavedQuiz()
        } else if (savedQuiz != null && roomSaveMatches) {
            val savedCodes = savedQuizRepository.parseAnsweredCodes(savedQuiz.answeredCountryCodes)
            val validCodes = savedCodes.filter { code ->
                countries.any { it.code == code }
            }.toSet()
            val state = QuizState(
                quiz = quiz,
                answeredCountries = validCodes
            )
            quizTimer.restore(savedQuiz.timeElapsedSeconds * 1000L)
            _uiState.value = QuizUiState.Active(state)
            savedQuizRepository.clearSavedQuiz()
        } else {
            val state = QuizState(quiz = quiz)
            _uiState.value = QuizUiState.Active(state)
        }
        onStateChanged()
        // A quiz restored as complete was finished before the process died: reuse its result.
        finishQuizIfComplete()
    }

    /** Persists the state for process death and starts or stops the timer to match it. */
    private fun onStateChanged() {
        syncTimerWithState()
        saveToHandle()
    }

    private fun saveToHandle() {
        val current = _uiState.value
        if (current is QuizUiState.Active) savedState.save(current.state, quizTimer.snapshot())
    }

    /**
     * Runs the timer only while the quiz is active, unpaused and not complete. Call after every
     * state change that can affect this. The ticker only refreshes [timerSeconds] for display;
     * saving and scoring read the timer directly.
     */
    private fun syncTimerWithState() {
        val current = _uiState.value
        val shouldRun = current is QuizUiState.Active &&
            !current.state.isComplete && !current.state.isPaused
        if (shouldRun) quizTimer.resume() else quizTimer.pause()

        tickerJob?.cancel()
        _timerSeconds.value = quizTimer.elapsedSeconds()
        if (quizTimer.isRunning) {
            tickerJob = viewModelScope.launch {
                quizTimer.ticks().collect { _timerSeconds.value = it }
            }
        }
    }

    fun togglePause() {
        _uiState.update { uiState ->
            if (uiState is QuizUiState.Active && !uiState.state.isComplete) {
                QuizUiState.Active(uiState.state.copy(isPaused = !uiState.state.isPaused))
            } else uiState
        }
        onStateChanged()
    }

    fun onBackgrounded() {
        val current = _uiState.value
        if (current is QuizUiState.Active && !current.state.isComplete) {
            _uiState.update { uiState ->
                if (uiState is QuizUiState.Active && !uiState.state.isComplete) {
                    QuizUiState.Active(uiState.state.copy(isPaused = true))
                } else uiState
            }
            onStateChanged()
            saveQuizState()
        }
    }

    private fun saveQuizState() {
        val current = _uiState.value
        if (current is QuizUiState.Active && !current.state.isComplete && current.state.answeredCountries.isNotEmpty()) {
            viewModelScope.launch {
                savedQuizRepository.saveQuizState(
                    categoryType = categoryType,
                    categoryValue = categoryValue,
                    answeredCodes = current.state.answeredCountries,
                    timeElapsed = quizTimer.elapsedSeconds(),
                    quizMode = quizModeId,
                    difficulty = _difficulty.value
                )
            }
        }
    }

    fun onInputChange(input: String) {
        _uiState.update { uiState ->
            if (uiState is QuizUiState.Active) {
                QuizUiState.Active(uiState.state.copy(currentInput = input))
            } else uiState
        }
        saveToHandle()
    }

    fun onSubmitAnswer() {
        val current = _uiState.value
        if (current !is QuizUiState.Active) return
        if (current.state.isComplete || current.state.isPaused) return

        val input = current.state.currentInput.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            // Typos are forgiven except at Hard, where they get a NearMiss (no strike).
            val rules = _difficulty.value
            val allowFuzzy = rules.allowsTypos
            val result = gameMode.validator.validate(input, current.state, allowFuzzy)
            _uiState.update { uiState ->
                if (uiState is QuizUiState.Active) {
                    val state = uiState.state
                    val newAnswered = if (result is AnswerResult.Correct) {
                        state.answeredCountries + state.quiz.countries
                            .first { it.name == result.countryName }.code
                    } else {
                        state.answeredCountries
                    }
                    val newIncorrect = if (result is AnswerResult.Incorrect) {
                        state.incorrectGuesses + 1
                    } else {
                        state.incorrectGuesses
                    }
                    val newIncorrectStrings = if (result is AnswerResult.Incorrect) {
                        state.incorrectGuessStrings + input
                    } else {
                        state.incorrectGuessStrings
                    }
                    val allAnswered = newAnswered.size == state.quiz.countries.size
                    val struckOut = rules.strikeLimit?.let { newIncorrect >= it } ?: false
                    val isComplete = allAnswered || struckOut
                    QuizUiState.Active(
                        state.copy(
                            answeredCountries = newAnswered,
                            currentInput = if (result is AnswerResult.Correct) "" else state.currentInput,
                            lastAnswerResult = result,
                            isComplete = isComplete,
                            incorrectGuesses = newIncorrect,
                            incorrectGuessStrings = newIncorrectStrings
                        )
                    )
                } else uiState
            }
            onStateChanged()
            finishQuizIfComplete()
        }
    }

    fun onGiveUp() {
        _uiState.update { uiState ->
            if (uiState is QuizUiState.Active) {
                QuizUiState.Active(uiState.state.copy(isComplete = true))
            } else uiState
        }
        onStateChanged()
        finishQuizIfComplete()
    }

    /**
     * Records a finished quiz once (see [CompleteQuizUseCase]) and then publishes [completion].
     *
     * The result id is stored in the SavedStateHandle before recording, so a screen recreated
     * after process death reuses it: the use case then returns the stored result without
     * recording again. Any interstitial is decided and shown on Results, after it has rendered.
     */
    private fun finishQuizIfComplete() {
        val current = _uiState.value
        if (current !is QuizUiState.Active || !current.state.isComplete) return
        if (completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = current.state,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            quizMode = quizMode,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = _difficulty.value,
            challengeId = challengeId
        )
        completionJob = viewModelScope.launch {
            val outcome = completeQuiz(request)
            if (outcome.newAchievements.isNotEmpty()) {
                _newAchievements.value = outcome.newAchievements
            }
            _completion.value = QuizCompletion(resultId = resultId, step = QuizCompletion.Step.NAVIGATE)
        }
    }

    /** The screen has navigated to Results; nothing more to do. */
    fun onNavigatedToResults() {
        _completion.update { it?.copy(step = QuizCompletion.Step.DONE) }
    }

    fun clearNewAchievements() {
        _newAchievements.value = emptyList()
    }

    fun toggleShowTimer() {
        val newValue = !_showTimer.value
        _showTimer.value = newValue
        updateTimerVisible()
        viewModelScope.launch { settingsRepository.setShowTimer(newValue) }
    }

    fun toggleShowFlags() {
        val newValue = !_showFlags.value
        _showFlags.value = newValue
        viewModelScope.launch { settingsRepository.setShowFlags(newValue) }
    }

    fun toggleShowCountryHint() {
        val newValue = !_showCountryHint.value
        _showCountryHint.value = newValue
        viewModelScope.launch { settingsRepository.setShowCountryHint(newValue) }
    }

    companion object {
        /** Optional route argument with a [Difficulty.id] (see `Screen.Quiz`). */
        const val ARG_DIFFICULTY = "difficulty"
    }
}

/**
 * A recorded quiz on its way to Results. Kept in the ViewModel (not the composition) so that
 * navigation happens exactly once, even if the activity is recreated in between.
 */
data class QuizCompletion(val resultId: String, val step: Step) {
    enum class Step { NAVIGATE, DONE }
}

sealed class QuizUiState {
    data object Loading : QuizUiState()
    data class Active(val state: QuizState) : QuizUiState()
}
