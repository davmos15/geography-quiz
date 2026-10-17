package com.geoquiz.app.ui.silhouettes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.HintCosts
import com.geoquiz.app.domain.mode.HintType
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.silhouettes.SilhouettesGameMode
import com.geoquiz.app.domain.model.AnswerResult
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
import com.geoquiz.app.domain.usecase.GetCountryOutlineUseCase
import com.geoquiz.app.ui.quiz.feedback.AnswerFeedbackEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
 * One Country silhouettes round (task 5.1): its own screen and ViewModel (D37), no Room resume
 * save. [savedStateHandle] carries the route's `categoryType`/`categoryValue`/`difficulty` (see
 * `Screen.Silhouettes`) and, after the first state change, the round's own progress so the
 * screen survives rotation and process death (`SilhouettesSavedState`).
 */
@HiltViewModel
class SilhouettesGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val silhouettesGameMode: SilhouettesGameMode,
    private val countryRepository: CountryRepository,
    private val getCountryOutline: GetCountryOutlineUseCase,
    private val completeQuiz: CompleteQuizUseCase,
    private val settingsRepository: SettingsRepository,
    clock: MonotonicClock,
    quizRandom: QuizRandom
) : ViewModel() {

    private val categoryType: String = savedStateHandle["categoryType"] ?: "all"
    private val categoryValueRaw: String = savedStateHandle["categoryValue"] ?: "_"
    private val categoryValue: String = Uri.decode(categoryValueRaw)
    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val routeDifficulty: Difficulty? = Difficulty.fromIdOrNull(savedStateHandle[ARG_DIFFICULTY])

    private val _uiState = MutableStateFlow<SilhouettesUiState>(SilhouettesUiState.Loading)
    val uiState: StateFlow<SilhouettesUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)

    private val _timerVisible = MutableStateFlow(true)

    /** Whether the timer is on screen: the "Show timer" setting, except Hard always shows it (D15). */
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<SilhouettesCompletion?>(null)
    val completion: StateFlow<SilhouettesCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = SilhouettesSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var advanceJob: Job? = null
    private var completionJob: Job? = null

    /** Every country in the game: distractors for Easy, and the pool a typed answer is judged against. */
    private var allCountries: List<Country> = emptyList()

    /** [allCountries] keyed by code, for the Show neighbours hint's names (D38). */
    private var allCountriesByCode: Map<String, Country> = emptyMap()

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
            val difficulty = requested.takeIf { it in silhouettesGameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty
            _timerVisible.value = _showTimer.value || difficulty.timerAlwaysShown
            allCountries = countryRepository.getAllCountries().first()
            allCountriesByCode = allCountries.associateBy { it.code }
            loadRound(difficulty)
        }
    }

    private suspend fun loadRound(difficulty: Difficulty) {
        val snapshot = savedState.restore()
        val state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            SilhouetteState(
                category = category,
                difficulty = difficulty,
                countries = snapshot.countries(allCountries),
                currentIndex = snapshot.currentIndex,
                answeredCodes = snapshot.answeredCodes.toSet(),
                incorrectGuesses = snapshot.incorrectGuesses,
                incorrectGuessStrings = snapshot.incorrectGuessStrings,
                hintsUsed = snapshot.hintsUsed,
                hintsUsedForCurrent = snapshot.hintsUsedForCurrent,
                wrongGuessCodesForCurrent = snapshot.wrongGuessCodesForCurrent,
                wrongGuessNamesForCurrent = snapshot.wrongGuessNamesForCurrent,
                currentInput = snapshot.currentInput,
                isComplete = snapshot.isComplete,
                showNeighbours = snapshot.showNeighbours,
                pendingAdvance = snapshot.pendingAdvance,
                // A restored round comes back paused, like the classic quiz (task 5.1 review, S3).
                paused = !snapshot.isComplete
            )
        } else {
            val countries = silhouettesGameMode.generator.items(category)
            SilhouetteState(category = category, difficulty = difficulty, countries = countries, isComplete = countries.isEmpty())
        }
        commit(state)
        when {
            state.isComplete -> finishIfComplete()
            // The round died mid-pause (a scheduled advance never ran): catch up immediately
            // instead of re-asking the same silhouette (task 5.1 review, S2).
            state.pendingAdvance -> advanceToNext()
            else -> loadOutlineAndChoiceForCurrent(state)
        }
    }

    private fun loadOutlineAndChoiceForCurrent(state: SilhouetteState) {
        val target = state.current ?: return
        val question = if (state.difficulty == Difficulty.EASY) {
            silhouettesGameMode.choiceGenerator.question(
                target = target,
                quizSet = state.countries,
                allCountries = allCountries,
                category = category,
                random = random
            )
        } else {
            null
        }
        commit(
            state.copy(
                choice = question,
                choiceFeedback = null,
                outline = null,
                neighbourNames = emptyList(),
                isOutlineLoading = true,
                isOutlineUnavailable = false
            )
        )
        viewModelScope.launch {
            val outline = try {
                getCountryOutline(target.code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A read or decode failure shows the fallback message below, never an endless
                // spinner (task 5.1 review nit).
                null
            }
            val current = activeState() ?: return@launch
            if (current.current?.code == target.code) {
                val names = outline?.neighbours
                    ?.mapNotNull { allCountriesByCode[it.cca3]?.name }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
                commit(
                    current.copy(
                        outline = outline,
                        neighbourNames = names,
                        isOutlineLoading = false,
                        isOutlineUnavailable = outline == null
                    )
                )
            }
        }
    }

    private fun activeState(): SilhouetteState? = (_uiState.value as? SilhouettesUiState.Active)?.state

    /**
     * Commits [state]. [announce] bumps [SilhouetteState.feedbackToken] so the feedback banner's
     * live region speaks again: by default only when the feedback itself changed, and forced by
     * answers whose feedback can repeat word for word ("Not recognised" twice). Pauses, hints and
     * keystrokes never re-announce the last answer (task 5.1 verification nit).
     */
    private fun commit(state: SilhouetteState, announce: Boolean = state.feedback != activeState()?.feedback) {
        val stamped = if (announce) state.copy(feedbackToken = state.feedbackToken + 1) else state
        _uiState.value = SilhouettesUiState.Active(stamped)
        syncTimer(stamped)
        savedState.save(stamped, quizTimer.snapshot())
    }

    private fun syncTimer(state: SilhouetteState) {
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
        // Only the input itself needs saving on every keystroke, not the whole snapshot, and the
        // timer ticker must not restart each time (task 5.1 review nit): no call to [commit].
        _uiState.value = SilhouettesUiState.Active(state.copy(currentInput = value))
        savedState.saveInput(value)
    }

    /** Typed answer (Normal/Hard): judged against the whole country list, not just this round. */
    fun onSubmit() {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused) return
        val target = state.current ?: return
        val input = state.currentInput.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            val allowFuzzy = state.difficulty.allowsTypos
            val ephemeral = QuizState(quiz = Quiz(category = category, countries = allCountries))
            val result = silhouettesGameMode.validator.validate(input, ephemeral, allowFuzzy)

            val latest = activeState() ?: return@launch
            if (latest.isComplete || latest.isAdvancing || latest.paused || latest.current?.code != target.code) return@launch

            when (result) {
                is AnswerResult.Correct -> {
                    val matched = allCountries.firstOrNull { it.name == result.countryName }
                    if (matched?.code == target.code) {
                        onCorrectAnswer(latest, target)
                    } else {
                        onWrongCountryGuess(latest, target, matched?.name ?: result.countryName, matched?.code.orEmpty())
                    }
                }
                AnswerResult.NearMiss -> {
                    commit(latest.copy(feedback = SilhouetteFeedback.NearMiss), announce = true)
                    _feedbackEvents.tryEmit(AnswerFeedbackEvent.NEAR_MISS)
                }
                AnswerResult.Incorrect, AnswerResult.AlreadyAnswered -> {
                    commit(latest.copy(feedback = SilhouetteFeedback.NotRecognised), announce = true)
                }
                AnswerResult.None -> Unit
            }
        }
    }

    private fun onCorrectAnswer(state: SilhouetteState, target: Country) {
        commit(
            state.copy(
                answeredCodes = state.answeredCodes + target.code,
                feedback = SilhouetteFeedback.Correct(target.name),
                currentInput = "",
                isAdvancing = true,
                pendingAdvance = true
            )
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
        scheduleAdvance(ADVANCE_DELAY_MILLIS)
    }

    /**
     * [name] (and [code], blank if the alias could not be resolved to one country) is a real
     * country but not [target]: a wrong guess. The same country guessed again this silhouette
     * costs nothing more (no extra life lost, no duplicate chip). Losing the last life reveals
     * [target]'s name alongside the wrong guess, kept on screen for
     * [REVEAL_DELAY_MILLIS] before the round's result is published (task 5.1 review, S4).
     */
    private fun onWrongCountryGuess(state: SilhouetteState, target: Country, name: String, code: String) {
        // An alias that maps to no single country has a blank code: de-duplicate it by name, so
        // repeating the same unresolved guess never costs a second life (verification nit).
        val alreadyGuessed = if (code.isNotEmpty()) {
            code in state.wrongGuessCodesForCurrent
        } else {
            state.wrongGuessNamesForCurrent.any { it.equals(name, ignoreCase = true) }
        }
        val newIncorrectGuesses = if (alreadyGuessed) state.incorrectGuesses else state.incorrectGuesses + 1
        val livesAfter = (SilhouetteState.MAX_LIVES - newIncorrectGuesses).coerceAtLeast(0)
        val updated = if (alreadyGuessed) {
            state.copy(feedback = SilhouetteFeedback.WrongCountry(name, livesAfter))
        } else {
            state.copy(
                incorrectGuesses = newIncorrectGuesses,
                incorrectGuessStrings = state.incorrectGuessStrings + name,
                wrongGuessCodesForCurrent = if (code.isEmpty()) state.wrongGuessCodesForCurrent else state.wrongGuessCodesForCurrent + code,
                wrongGuessNamesForCurrent = state.wrongGuessNamesForCurrent + name,
                feedback = SilhouetteFeedback.WrongCountry(name, livesAfter)
            )
        }
        if (!alreadyGuessed && livesAfter == 0) {
            _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
            endRoundLivesExhausted(updated.copy(feedback = SilhouetteFeedback.LivesExhausted(name, target.name)))
            return
        }
        commit(updated, announce = true)
        if (!alreadyGuessed) _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
    }

    /** Easy: one of the four options was picked. */
    fun onChoiceSelected(code: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused || state.choiceFeedback != null) return
        val choice = state.choice ?: return
        val target = state.current ?: return
        val picked = choice.option(code) ?: return
        val isCorrect = code == choice.targetCode

        // Either way the result is shown for a moment before the next question: no hints during
        // that window (task 5.1 review, S7), and the round is already decided if death intervenes
        // (S2, [SilhouetteState.pendingAdvance]).
        val updated = if (isCorrect) {
            state.copy(
                answeredCodes = state.answeredCodes + target.code,
                choiceFeedback = ChoiceFeedback(code, true),
                isAdvancing = true,
                pendingAdvance = true
            )
        } else {
            state.copy(
                incorrectGuesses = state.incorrectGuesses + 1,
                incorrectGuessStrings = state.incorrectGuessStrings + picked.label,
                choiceFeedback = ChoiceFeedback(code, false),
                isAdvancing = true,
                pendingAdvance = true
            )
        }
        commit(updated)
        _feedbackEvents.tryEmit(if (isCorrect) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT)
        if (!isCorrect && updated.lives == 0) {
            // Easy keeps its own choice feedback (the correct option highlighted) as the reveal.
            endRoundLivesExhausted(updated)
            return
        }
        scheduleAdvance(CHOICE_FEEDBACK_MILLIS)
    }

    /**
     * One hint type, usable once per silhouette; each use is recorded for the scoring cost. A
     * no-op once this round has already used its difficulty tier's hint cap (task 8.4,
     * `HintCosts.maxHintsPerRound`). Show neighbours is also a no-op while the current
     * silhouette's outline is still loading (task 9.2 review fix): on a cold silhouette the
     * outline (and so its neighbour list) isn't known yet, so spending the hint here would charge
     * points, use up the round's hint cap and reveal nothing but a false "No land neighbours".
     * Once the outline settles, [SilhouetteState.availableHints] either allows this (loaded) or
     * drops it from the row entirely ([SilhouetteState.isOutlineUnavailable]), so the `!in
     * availableHints` check above already covers that second case.
     */
    fun onHint(type: HintType) {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused) return
        if (type !in state.availableHints) return
        if (type == HintType.SHOW_NEIGHBOURS && state.isOutlineLoading) return
        if (type in state.hintsUsedForCurrent) return
        if (state.hintsUsed.size >= HintCosts.maxHintsPerRound(state.difficulty)) return
        commit(
            state.copy(
                hintsUsed = state.hintsUsed + type,
                hintsUsedForCurrent = state.hintsUsedForCurrent + type,
                showNeighbours = state.showNeighbours || type == HintType.SHOW_NEIGHBOURS
            )
        )
    }

    /** Reveals the target's name briefly, counts it as missed, costs no life. */
    fun onPass() {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused) return
        val target = state.current ?: return
        commit(
            state.copy(
                feedback = SilhouetteFeedback.Revealed(target.name),
                currentInput = "",
                isAdvancing = true,
                pendingAdvance = true
            )
        )
        scheduleAdvance(REVEAL_DELAY_MILLIS)
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        advanceJob?.cancel()
        commit(state.copy(isComplete = true, isAdvancing = false, pendingAdvance = false))
        finishIfComplete()
    }

    /**
     * Pauses on backgrounding, like the classic quiz (task 5.1 review, S3): [onResume] (the
     * paused overlay's Resume button) is the only way back. [syncTimer] keeps the timer paused
     * through any commit made while backgrounded (e.g. a delayed advance firing off-screen).
     */
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

    private fun endRoundLivesExhausted(state: SilhouetteState) {
        advanceJob?.cancel()
        commit(state.copy(isComplete = true, isAdvancing = false, pendingAdvance = false))
        finishIfComplete(REVEAL_DELAY_MILLIS)
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
        val isComplete = nextIndex >= state.countries.size
        val updated = state.copy(
            currentIndex = nextIndex,
            feedback = SilhouetteFeedback.None,
            isAdvancing = false,
            pendingAdvance = false,
            hintsUsedForCurrent = emptySet(),
            wrongGuessCodesForCurrent = emptyList(),
            wrongGuessNamesForCurrent = emptyList(),
            showNeighbours = false,
            outline = null,
            neighbourNames = emptyList(),
            isOutlineUnavailable = false,
            choice = null,
            choiceFeedback = null,
            isComplete = isComplete
        )
        commit(updated)
        if (isComplete) finishIfComplete() else loadOutlineAndChoiceForCurrent(updated)
    }

    /**
     * Records the round once ([CompleteQuizUseCase]) and publishes [completion]. No Room resume
     * save (D37). [revealDelayMillis] (task 5.1 review, S4) lets the final feedback stay on
     * screen while the result is already recorded: only the navigation is delayed.
     */
    private fun finishIfComplete(revealDelayMillis: Long = 0L) {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = state.countries),
            answeredCountries = state.answeredCodes,
            incorrectGuesses = state.incorrectGuesses,
            incorrectGuessStrings = state.incorrectGuessStrings,
            hintsUsed = state.hintsUsed,
            isComplete = true
        )
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = SilhouettesGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = state.difficulty,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            if (revealDelayMillis > 0) delay(revealDelayMillis)
            _completion.value = SilhouettesCompletion(resultId = resultId, step = SilhouettesCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = SilhouettesCompletion.Step.DONE)
    }

    companion object {
        const val ARG_DIFFICULTY = "difficulty"

        /** How long the "Correct" feedback stays before the next silhouette. */
        const val ADVANCE_DELAY_MILLIS = 900L

        /** How long Pass's reveal, or the lives-exhausted reveal, stays before the next step. */
        const val REVEAL_DELAY_MILLIS = 1_500L

        /** Easy only: how long a pick's result stays on the options before the next question. */
        const val CHOICE_FEEDBACK_MILLIS = 900L

        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}
