package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.data.service.AdManager
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.ChoiceFeedback
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.mode.GameMode
import com.geoquiz.app.domain.mode.GameModeRegistry
import com.geoquiz.app.domain.mode.GameModeSpec
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.domain.usecase.CompleteQuizUseCase
import com.geoquiz.app.domain.time.MonotonicClock
import com.geoquiz.app.domain.time.QuizTimer
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    clock: MonotonicClock,
    private val countryRepository: CountryRepository,
    quizRandom: QuizRandom
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

    /** In-app vibration switch; the screen only plays haptics while this is on. */
    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

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

    /** Set once the finished quiz has been recorded; the screen then moves on to Results. */
    private val _completion = MutableStateFlow<QuizCompletion?>(null)
    val completion: StateFlow<QuizCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)

    /**
     * One event per typed answer or Easy pick (3.3), for the screen's haptic and animation.
     * Not replayed: an event sent while nothing collects (e.g. the screen is gone) is dropped,
     * so a recreated screen never buzzes for an old answer.
     */
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = QuizSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private var tickerJob: Job? = null
    private var completionJob: Job? = null

    /** Easy: order, distractors and option order. */
    private val random = quizRandom.random

    /** Easy: every country in the game, for distractors. Loaded only for Easy quizzes. */
    private var allCountries: List<Country> = emptyList()

    /** Easy: shows a pick's feedback for [CHOICE_FEEDBACK_MILLIS], then moves on. */
    private var feedbackJob: Job? = null

    init {
        adManager.preloadInterstitial()
        viewModelScope.launch {
            // Settings first: the difficulty decides typo tolerance and strikes and is recorded
            // with the result.
            _showTimer.value = settingsRepository.showTimer.first()
            _showFlags.value = settingsRepository.showFlags.first()
            _vibration.value = settingsRepository.vibration.first()
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
        // Easy is multiple choice (D14): the same item set, asked one question at a time.
        val isEasy = _difficulty.value == Difficulty.EASY
        val countries = gameMode.generator.items(category)
        if (isEasy) allCountries = countryRepository.getAllCountries().first()
        val withChoices: (QuizState) -> QuizState = { if (isEasy) prepareChoices(it) else it }
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
            _uiState.value = QuizUiState.Active(withChoices(snapshot.toQuizState(quiz)))
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
            // Easy: the resume save holds answered codes only, so missed items are asked again.
            _uiState.value = QuizUiState.Active(withChoices(state))
            savedQuizRepository.clearSavedQuiz()
        } else {
            val state = QuizState(quiz = quiz)
            _uiState.value = QuizUiState.Active(withChoices(state))
        }
        onStateChanged()
        // A quiz restored as complete was finished before the process died: reuse its result.
        finishQuizIfComplete()
    }

    /**
     * Easy: makes the multiple-choice progress agree with the answers and puts a question on
     * screen. Every item not yet answered or missed is asked once: the saved order first, then
     * any item it does not list (e.g. after a Room resume, which keeps answered codes only, so
     * missed items are asked again) in random order. A saved question whose target is already
     * answered or missed was picked just before the screen was recreated: the next one is shown.
     */
    private fun prepareChoices(state: QuizState): QuizState {
        if (state.isComplete) return state
        val codes = state.quiz.countries.map { it.code }
        val done = state.answeredCountries + state.missedCountries
        val current = state.choice?.takeIf { it.targetCode in codes && it.targetCode !in done }
        val pending = { code: String -> code !in done && code != current?.targetCode }
        val listed = state.remainingOrder.filter(pending).distinct()
        val unlisted = codes.filter { pending(it) && it !in listed }.shuffled(random)
        val prepared = state.copy(choice = current, choiceFeedback = null, remainingOrder = listed + unlisted)
        return if (current == null) nextChoice(prepared) else prepared
    }

    /** Easy: the next question, or the finished quiz once every item has been asked. */
    private fun nextChoice(state: QuizState): QuizState {
        val nextCode = state.remainingOrder.firstOrNull()
            ?: return state.copy(
                choice = null,
                choiceFeedback = null,
                isComplete = state.quiz.countries.isNotEmpty(),
                isPaused = false
            )
        val target = state.quiz.countries.first { it.code == nextCode }
        val question = gameMode.choiceGenerator.question(
            target = target,
            quizSet = state.quiz.countries,
            allCountries = allCountries,
            category = category,
            random = random
        )
        return state.copy(choice = question, choiceFeedback = null, remainingOrder = state.remainingOrder.drop(1))
    }

    /**
     * Easy: the player picked the option for [code]. A correct pick answers the item; a wrong
     * pick misses it (an incorrect guess, recorded with the option's label for Answer review)
     * and it is not asked again. The options show the result for [CHOICE_FEEDBACK_MILLIS], then
     * the next question appears. Ignored while paused, during feedback and for unknown codes.
     */
    fun onChoiceSelected(code: String) {
        val current = _uiState.value as? QuizUiState.Active ?: return
        val state = current.state
        if (state.isComplete || state.isPaused || state.choiceFeedback != null) return
        val choice = state.choice ?: return
        val picked = choice.option(code) ?: return
        val target = choice.targetCode
        val updated = if (code == target) {
            state.copy(
                answeredCountries = state.answeredCountries + target,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = true)
            )
        } else {
            state.copy(
                missedCountries = state.missedCountries + target,
                incorrectGuesses = state.incorrectGuesses + 1,
                incorrectGuessStrings = state.incorrectGuessStrings + picked.label,
                choiceFeedback = ChoiceFeedback(selectedCode = code, isCorrect = false)
            )
        }
        _uiState.value = QuizUiState.Active(updated)
        onStateChanged()
        _feedbackEvents.tryEmit(
            if (code == target) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT
        )
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(CHOICE_FEEDBACK_MILLIS)
            advanceAfterFeedback()
        }
    }

    /**
     * Easy: replaces a shown pick with the next question. While paused it waits: [togglePause]
     * calls it again on resume.
     */
    private fun advanceAfterFeedback() {
        val current = _uiState.value as? QuizUiState.Active ?: return
        val state = current.state
        if (state.choiceFeedback == null || state.isComplete || state.isPaused) return
        _uiState.value = QuizUiState.Active(nextChoice(state))
        onStateChanged()
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
        // Easy: a pick whose feedback ran out while paused moves on once play resumes.
        if (feedbackJob?.isActive != true) advanceAfterFeedback()
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

    /**
     * Saves the "Resume quiz" record in Room: answered codes, time and tier only. At Easy the
     * missed items and question order are not kept, so a resumed Easy quiz asks the missed items
     * again (see [prepareChoices]).
     *
     * A practice quiz is never saved here (D21), so "Resume quiz" never shows one; the
     * SavedStateHandle still keeps its progress across process death.
     */
    private fun saveQuizState() {
        if (!category.isRecorded) return
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
                    val correctCode = (result as? AnswerResult.Correct)?.let { correct ->
                        state.quiz.countries.first { it.name == correct.countryName }.code
                    }
                    val newAnswered = if (correctCode != null) {
                        state.answeredCountries + correctCode
                    } else {
                        state.answeredCountries
                    }
                    val newRecent = if (correctCode != null) {
                        QuizState.pushRecentCorrect(state.recentCorrect, correctCode)
                    } else {
                        state.recentCorrect
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
                            incorrectGuessStrings = newIncorrectStrings,
                            recentCorrect = newRecent
                        )
                    )
                } else uiState
            }
            onStateChanged()
            AnswerFeedbackEvent.from(result)?.let { _feedbackEvents.tryEmit(it) }
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
            completeQuiz(request)
            _completion.value = QuizCompletion(resultId = resultId, step = QuizCompletion.Step.NAVIGATE)
        }
    }

    /** The screen has navigated to Results; nothing more to do. */
    fun onNavigatedToResults() {
        _completion.update { it?.copy(step = QuizCompletion.Step.DONE) }
    }

    fun toggleShowTimer() {
        val newValue = !_showTimer.value
        _showTimer.value = newValue
        updateTimerVisible()
        viewModelScope.launch { settingsRepository.setShowTimer(newValue) }
    }

    fun toggleVibration() {
        val newValue = !_vibration.value
        _vibration.value = newValue
        viewModelScope.launch { settingsRepository.setVibration(newValue) }
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

        /** Easy: how long a pick's result stays on the options before the next question. */
        const val CHOICE_FEEDBACK_MILLIS = 900L

        /** Room for answer events while the screen's collector catches up. */
        private const val FEEDBACK_EVENT_BUFFER = 8
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
