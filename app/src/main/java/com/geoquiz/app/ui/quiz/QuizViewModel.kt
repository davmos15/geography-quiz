package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.domain.model.Achievement
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.domain.usecase.CalculateScoreUseCase
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import com.geoquiz.app.domain.usecase.ValidateAnswerUseCase
import com.geoquiz.app.ui.results.QuizResultHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

@HiltViewModel
class QuizViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getCountriesForQuiz: GetCountriesForQuizUseCase,
    private val validateAnswer: ValidateAnswerUseCase,
    private val calculateScore: CalculateScoreUseCase,
    private val settingsRepository: SettingsRepository,
    private val achievementRepository: AchievementRepository,
    private val savedQuizRepository: SavedQuizRepository
) : ViewModel() {

    private val categoryType: String = savedStateHandle["categoryType"] ?: "all"
    private val categoryValueRaw: String = savedStateHandle["categoryValue"] ?: "_"
    private val categoryValue: String = URLDecoder.decode(categoryValueRaw, "UTF-8")

    val category: QuizCategory = QuizCategory.fromRoute(categoryType, categoryValue)

    private val _uiState = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private val _showTimer = MutableStateFlow(true)
    val showTimer: StateFlow<Boolean> = _showTimer.asStateFlow()

    private val _newAchievements = MutableStateFlow<List<Achievement>>(emptyList())
    val newAchievements: StateFlow<List<Achievement>> = _newAchievements.asStateFlow()

    private var timerJob: Job? = null
    private var achievementsChecked = false

    init {
        loadQuiz()
        viewModelScope.launch {
            _showTimer.value = settingsRepository.showTimer.first()
        }
    }

    private fun loadQuiz() {
        viewModelScope.launch {
            val countries = getCountriesForQuiz(category)
            val quiz = Quiz(
                category = category,
                countries = countries,
                timerSeconds = null
            )

            // Check for saved quiz state to restore
            val savedQuiz = savedQuizRepository.getSavedQuiz()
            if (savedQuiz != null &&
                savedQuiz.categoryType == categoryType &&
                savedQuiz.categoryValue == categoryValue
            ) {
                val savedCodes = savedQuizRepository.parseAnsweredCodes(savedQuiz.answeredCountryCodes)
                // Filter to only codes that are still valid in this quiz
                val validCodes = savedCodes.filter { code ->
                    countries.any { it.code == code }
                }.toSet()
                val state = QuizState(
                    quiz = quiz,
                    answeredCountries = validCodes,
                    timeElapsedSeconds = savedQuiz.timeElapsedSeconds
                )
                _uiState.value = QuizUiState.Active(state)
                savedQuizRepository.clearSavedQuiz()
            } else {
                val state = QuizState(quiz = quiz)
                _uiState.value = QuizUiState.Active(state)
            }
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _uiState.value
                if (current is QuizUiState.Active && !current.state.isComplete && !current.state.isPaused) {
                    _uiState.update { uiState ->
                        if (uiState is QuizUiState.Active) {
                            QuizUiState.Active(
                                uiState.state.copy(
                                    timeElapsedSeconds = uiState.state.timeElapsedSeconds + 1
                                )
                            )
                        } else uiState
                    }
                } else if (current is QuizUiState.Active && current.state.isComplete) {
                    break
                }
            }
        }
    }

    fun togglePause() {
        _uiState.update { uiState ->
            if (uiState is QuizUiState.Active && !uiState.state.isComplete) {
                QuizUiState.Active(uiState.state.copy(isPaused = !uiState.state.isPaused))
            } else uiState
        }
    }

    fun onBackgrounded() {
        val current = _uiState.value
        if (current is QuizUiState.Active && !current.state.isComplete) {
            // Pause the quiz
            _uiState.update { uiState ->
                if (uiState is QuizUiState.Active && !uiState.state.isComplete) {
                    QuizUiState.Active(uiState.state.copy(isPaused = true))
                } else uiState
            }
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
                    timeElapsed = current.state.timeElapsedSeconds
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
    }

    fun onSubmitAnswer() {
        val current = _uiState.value
        if (current !is QuizUiState.Active) return
        if (current.state.isComplete || current.state.isPaused) return

        val input = current.state.currentInput.trim()
        if (input.isBlank()) return

        viewModelScope.launch {
            val result = validateAnswer(input, current.state)
            _uiState.update { uiState ->
                if (uiState is QuizUiState.Active) {
                    val state = uiState.state
                    val newAnswered = if (result is AnswerResult.Correct) {
                        state.answeredCountries + state.quiz.countries
                            .first { it.name == result.countryName }.code
                    } else {
                        state.answeredCountries
                    }
                    val isComplete = newAnswered.size == state.quiz.countries.size
                    QuizUiState.Active(
                        state.copy(
                            answeredCountries = newAnswered,
                            currentInput = if (result is AnswerResult.Correct) "" else state.currentInput,
                            lastAnswerResult = result,
                            isComplete = isComplete
                        )
                    )
                } else uiState
            }
        }
    }

    fun onGiveUp() {
        _uiState.update { uiState ->
            if (uiState is QuizUiState.Active) {
                timerJob?.cancel()
                QuizUiState.Active(uiState.state.copy(isComplete = true))
            } else uiState
        }
        // Clear any saved state since quiz is done
        viewModelScope.launch {
            savedQuizRepository.clearSavedQuiz()
        }
    }

    fun getResult(): com.geoquiz.app.domain.model.QuizResult? {
        val current = _uiState.value
        if (current !is QuizUiState.Active) return null
        val result = calculateScore(current.state)

        // Store in QuizResultHolder for AnswerReview screen
        QuizResultHolder.countries = current.state.quiz.countries
        QuizResultHolder.answeredCodes = current.state.answeredCountries
        QuizResultHolder.categoryName = category.displayName

        // Check achievements once
        if (!achievementsChecked) {
            achievementsChecked = true
            viewModelScope.launch {
                val newlyUnlocked = achievementRepository.onQuizCompleted(
                    category = category,
                    correctAnswers = result.correctAnswers,
                    totalCountries = result.totalCountries,
                    timeElapsedSeconds = result.timeElapsedSeconds
                )
                if (newlyUnlocked.isNotEmpty()) {
                    _newAchievements.value = newlyUnlocked
                }
            }
        }

        // Clear saved state on completion
        viewModelScope.launch {
            savedQuizRepository.clearSavedQuiz()
        }

        return result
    }

    fun clearNewAchievements() {
        _newAchievements.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        val current = _uiState.value
        if (current is QuizUiState.Active && !current.state.isComplete && current.state.answeredCountries.isNotEmpty()) {
            // Can't use viewModelScope here since it's cancelled, but the coroutine
            // launched from saveQuizState would have already saved if onBackgrounded was called
        }
    }
}

sealed class QuizUiState {
    data object Loading : QuizUiState()
    data class Active(val state: QuizState) : QuizUiState()
}
