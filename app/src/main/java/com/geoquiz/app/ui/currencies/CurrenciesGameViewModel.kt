package com.geoquiz.app.ui.currencies

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.mode.ChoiceQuestion
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.mode.currencies.CurrencyGameMode
import com.geoquiz.app.domain.mode.currencies.CurrencyItem
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
import kotlinx.coroutines.launch

/**
 * One Currencies round (task 7.1): its own screen and ViewModel (D37), no Room resume save.
 * [savedStateHandle] carries the route's `categoryType`/`categoryValue`/`difficulty` and, after
 * the first state change, the round's own progress ([CurrenciesSavedState]) so the screen
 * survives rotation and process death.
 *
 * One item is on screen at a time, like `SilhouettesGameViewModel`, but with no lives or hints:
 * [CurrencyRoundState.isCurrentChoice] decides per item whether the player types an answer
 * ([onSubmit]) or picks one of four options ([onChoiceSelected]) - D50's shared-currency rule,
 * not the tier alone. A wrong typed answer never locks the item - the field stays open to try
 * again, exactly like every classic typed mode - but any answer that *does* move on (a correct
 * one, or a choice question either way) shows its feedback, and the item's fact when it has one,
 * until the player taps [onNext]: unlike every other mode there is no timed auto-advance, so a
 * fact can always be read at the player's own pace.
 */
@HiltViewModel
class CurrenciesGameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val currencyGameMode: CurrencyGameMode,
    private val countryRepository: CountryRepository,
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

    private val _uiState = MutableStateFlow<CurrenciesUiState>(CurrenciesUiState.Loading)
    val uiState: StateFlow<CurrenciesUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    private val _timerVisible = MutableStateFlow(true)

    /** Whether the timer is on screen: the "Show timer" setting, except Hard always shows it (D15). */
    val timerVisible: StateFlow<Boolean> = _timerVisible.asStateFlow()

    private val _vibration = MutableStateFlow(true)
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _completion = MutableStateFlow<CurrenciesCompletion?>(null)
    val completion: StateFlow<CurrenciesCompletion?> = _completion.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<AnswerFeedbackEvent>(extraBufferCapacity = FEEDBACK_EVENT_BUFFER)
    val feedbackEvents: SharedFlow<AnswerFeedbackEvent> = _feedbackEvents.asSharedFlow()

    private val savedState = CurrenciesSavedState(savedStateHandle)
    private val quizTimer = QuizTimer(clock)
    private val random = quizRandom.random
    private var tickerJob: Job? = null
    private var completionJob: Job? = null

    /** Every country in the game: distractors for a choice question. */
    private var allCountries: List<Country> = emptyList()

    init {
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
            _vibration.value = settingsRepository.vibration.first()
            val requested = routeDifficulty ?: savedState.difficulty ?: settingsRepository.difficulty.first()
            val difficulty = requested.takeIf { it in currencyGameMode.spec.supportedDifficulties } ?: Difficulty.DEFAULT
            savedState.difficulty = difficulty
            _timerVisible.value = _showTimer.value || difficulty.timerAlwaysShown
            allCountries = countryRepository.getAllCountries().first()
            loadRound(difficulty)
        }
    }

    private suspend fun loadRound(difficulty: Difficulty) {
        val snapshot = savedState.restore()
        val state = if (snapshot != null) {
            quizTimer.restore(snapshot.elapsedMillis)
            val items = currencyGameMode.generator.itemsForCurrencyCodes(category, snapshot.currencyCodes)
            CurrencyRoundState(
                category = category,
                difficulty = difficulty,
                items = items,
                currentIndex = snapshot.currentIndex,
                answeredCodes = snapshot.answeredCodes.toSet(),
                incorrectGuesses = snapshot.incorrectGuesses,
                incorrectGuessStrings = snapshot.incorrectGuessStrings,
                currentInput = snapshot.currentInput,
                isComplete = snapshot.isComplete,
                // A restored round comes back paused, like the classic quiz and Silhouettes.
                paused = !snapshot.isComplete
            )
        } else {
            val items = currencyGameMode.generator.currencyItems(category)
            CurrencyRoundState(category = category, difficulty = difficulty, items = items, isComplete = items.isEmpty())
        }
        // No currencies left to ask about for this filter (task 9.2 review fix, e.g. a Practice
        // filter whose codes have all been resolved away): never commit or record this as a
        // played round - Unavailable instead, the same reasoning as Border hop's own `randomPuzzle
        // == null` case.
        if (state.items.isEmpty()) {
            _uiState.value = CurrenciesUiState.Unavailable
            return
        }
        commit(state)
        when {
            state.isComplete -> finishIfComplete()
            // The round died while waiting for the player to tap Next (item already answered,
            // index not yet moved on): catch up immediately instead of re-asking the same item,
            // same reasoning as `SilhouettesGameViewModel`'s `pendingAdvance` (task 5.1 review, S2).
            snapshot?.pendingAdvance == true -> performAdvance()
            // Restoring the identical choice question, not a freshly drawn one (task 7.1 review).
            else -> loadQuestionForCurrent(state, restoredChoice = snapshot?.restoreChoice())
        }
    }

    private fun loadQuestionForCurrent(state: CurrencyRoundState, restoredChoice: ChoiceQuestion? = null) {
        val item = state.current ?: return
        val question = if (!state.isCurrentChoice) {
            null
        } else {
            restoredChoice?.takeIf { it.targetCode == item.country.code } ?: currencyGameMode.choiceGenerator.question(
                target = item.country,
                quizSet = state.items.map { it.country },
                allCountries = allCountries,
                category = category,
                random = random
            )
        }
        commit(state.copy(choice = question, choiceFeedback = null))
    }

    private fun activeState(): CurrencyRoundState? = (_uiState.value as? CurrenciesUiState.Active)?.state

    private fun commit(state: CurrencyRoundState, announce: Boolean = state.feedback != activeState()?.feedback) {
        val stamped = if (announce) state.copy(feedbackToken = state.feedbackToken + 1) else state
        _uiState.value = CurrenciesUiState.Active(stamped)
        syncTimer(stamped)
        savedState.save(stamped, quizTimer.snapshot())
    }

    private fun syncTimer(state: CurrencyRoundState) {
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
        _uiState.value = CurrenciesUiState.Active(state.copy(currentInput = value))
        savedState.saveInput(value)
    }

    /** Typed answer (Normal/Hard, non-shared currencies only): judged against this item's target country. */
    fun onSubmit() {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused || state.isCurrentChoice) return
        val item = state.current ?: return
        val input = state.currentInput.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            val allowFuzzy = state.difficulty.allowsTypos
            val ephemeral = QuizState(quiz = Quiz(category = category, countries = listOf(item.country)))
            val result = currencyGameMode.validator.validate(input, ephemeral, allowFuzzy)

            val latest = activeState() ?: return@launch
            if (latest.isComplete || latest.isAdvancing || latest.paused || latest.current?.country?.code != item.country.code) return@launch

            when (result) {
                is AnswerResult.Correct -> onCorrectAnswer(latest, item)
                AnswerResult.NearMiss -> {
                    commit(latest.copy(feedback = CurrencyFeedback.NearMiss), announce = true)
                    _feedbackEvents.tryEmit(AnswerFeedbackEvent.NEAR_MISS)
                }
                AnswerResult.Incorrect -> onIncorrectTyped(latest, item, input)
                // The ephemeral QuizState above always has an empty answeredCountries, so the
                // validator can never actually return this; kept only so the `when` is exhaustive.
                AnswerResult.AlreadyAnswered -> Unit
                AnswerResult.None -> Unit
            }
        }
    }

    private fun onCorrectAnswer(state: CurrencyRoundState, item: CurrencyItem) {
        commit(
            state.copy(
                answeredCodes = state.answeredCodes + item.country.code,
                feedback = CurrencyFeedback.Correct(item.country.name, item.entry.fact),
                currentInput = "",
                isAdvancing = true
            ),
            announce = true
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.CORRECT)
    }

    /**
     * A wrong typed answer never locks the item (D50: "validated like any country answer", the
     * same as every classic typed mode): the field stays open and the player can try again, no
     * "Next" needed. Only Hard's strike limit can end the round from here, and - matching the
     * classic quiz's own strike-out exactly (`QuizViewModel.onSubmitAnswer`) - it does so straight
     * away, with no reveal pause; Results and Answer review show what was missed.
     */
    private fun onIncorrectTyped(state: CurrencyRoundState, item: CurrencyItem, input: String) {
        val newIncorrect = state.incorrectGuesses + 1
        val struckOut = isStruckOut(state.difficulty, newIncorrect)
        commit(
            state.copy(
                incorrectGuesses = newIncorrect,
                incorrectGuessStrings = state.incorrectGuessStrings + input,
                feedback = CurrencyFeedback.Incorrect,
                currentInput = "",
                isComplete = struckOut
            ),
            announce = true
        )
        _feedbackEvents.tryEmit(AnswerFeedbackEvent.INCORRECT)
        if (struckOut) finishIfComplete()
    }

    /**
     * Easy, or any shared currency: one of the four options was picked. Unlike a wrong typed
     * answer, a wrong pick always reveals the correct option (standard `MultipleChoicePanel`
     * behaviour) and waits for [onNext] - including on a Hard strike-out, so the reveal stays on
     * screen until the player taps through, rather than jumping straight to Results the way a
     * typed strike-out does.
     */
    fun onChoiceSelected(code: String) {
        val state = activeState() ?: return
        if (state.isComplete || state.isAdvancing || state.paused || state.choiceFeedback != null) return
        val choice = state.choice ?: return
        val item = state.current ?: return
        val picked = choice.option(code) ?: return
        val isCorrect = code == choice.targetCode

        val newIncorrect = if (isCorrect) state.incorrectGuesses else state.incorrectGuesses + 1
        val updated = if (isCorrect) {
            state.copy(
                answeredCodes = state.answeredCodes + item.country.code,
                choiceFeedback = ChoiceFeedback(code, true),
                feedback = CurrencyFeedback.Correct(item.country.name, item.entry.fact),
                isAdvancing = true
            )
        } else {
            state.copy(
                incorrectGuesses = newIncorrect,
                incorrectGuessStrings = state.incorrectGuessStrings + picked.label,
                choiceFeedback = ChoiceFeedback(code, false),
                feedback = CurrencyFeedback.Revealed(item.country.name, item.entry.fact),
                isAdvancing = true
            )
        }
        commit(updated, announce = true)
        _feedbackEvents.tryEmit(if (isCorrect) AnswerFeedbackEvent.CORRECT else AnswerFeedbackEvent.INCORRECT)
    }

    /** The player is ready for the next item (or to finish, if this was the last one or a strike-out). */
    fun onNext() {
        val state = activeState() ?: return
        if (!state.isAdvancing || state.paused) return
        performAdvance()
    }

    /** Moves past the current (already-answered) item. Shared by [onNext] and restore's catch-up. */
    private fun performAdvance() {
        val state = activeState() ?: return
        val nextIndex = state.currentIndex + 1
        val struckOut = isStruckOut(state.difficulty, state.incorrectGuesses)
        val isComplete = struckOut || nextIndex >= state.items.size
        val updated = state.copy(
            currentIndex = nextIndex,
            feedback = CurrencyFeedback.None,
            choice = null,
            choiceFeedback = null,
            currentInput = "",
            isAdvancing = false,
            isComplete = isComplete
        )
        commit(updated)
        if (isComplete) finishIfComplete() else loadQuestionForCurrent(updated)
    }

    fun onGiveUp() {
        val state = activeState() ?: return
        if (state.isComplete) return
        commit(state.copy(isComplete = true, isAdvancing = false, choice = null))
        finishIfComplete()
    }

    /** Pauses on backgrounding, like the classic quiz and every Phase 5-7 mode. Resume is the only way back. */
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

    /** Hard's 3-strike limit (D44/classic convention); Easy and Normal never strike out. */
    private fun isStruckOut(difficulty: Difficulty, incorrectGuesses: Int): Boolean =
        difficulty.strikeLimit?.let { incorrectGuesses >= it } ?: false

    /** Records the round once ([CompleteQuizUseCase]) and publishes [completion]. No Room resume save (D37). */
    private fun finishIfComplete() {
        val state = activeState() ?: return
        if (!state.isComplete || completionJob != null) return

        val resultId = savedState.resultId ?: UUID.randomUUID().toString().also { savedState.resultId = it }
        val quizState = QuizState(
            quiz = Quiz(category = category, countries = state.items.map { it.country }),
            answeredCountries = state.answeredCodes,
            incorrectGuesses = state.incorrectGuesses,
            incorrectGuessStrings = state.incorrectGuessStrings,
            isComplete = true
        )
        val request = CompleteQuizUseCase.Request(
            resultId = resultId,
            state = quizState,
            timeElapsedSeconds = quizTimer.elapsedSeconds(),
            modeId = CurrencyGameMode.MODE_ID,
            routeCategoryType = categoryType,
            routeCategoryValue = categoryValue,
            difficulty = state.difficulty,
            challengeId = null
        )
        completionJob = viewModelScope.launch {
            completeQuiz(request)
            _completion.value = CurrenciesCompletion(resultId = resultId, step = CurrenciesCompletion.Step.NAVIGATE)
        }
    }

    fun onNavigatedToResults() {
        _completion.value = _completion.value?.copy(step = CurrenciesCompletion.Step.DONE)
    }

    companion object {
        const val ARG_DIFFICULTY = "difficulty"
        private const val FEEDBACK_EVENT_BUFFER = 8
    }
}
