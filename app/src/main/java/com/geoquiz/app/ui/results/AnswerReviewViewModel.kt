package com.geoquiz.app.ui.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.repository.CompletedQuizRepository
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AnswerReviewUiState {
    data object Loading : AnswerReviewUiState

    /** The result is gone (e.g. "Reset all data" ran, or a newer quiz replaced it). */
    data object Missing : AnswerReviewUiState

    data class Loaded(
        val categoryName: String,
        val quizMode: QuizMode,
        val category: QuizCategory,
        /** Quiz countries sorted for display (by capital in Capitals mode, else by name). */
        val countries: List<Country>,
        val answeredCodes: Set<String>,
        val incorrectGuesses: List<IncorrectGuess>
    ) : AnswerReviewUiState {
        val answeredCount: Int get() = countries.count { it.code in answeredCodes }
    }
}

/** A wrong guess and, if it names a real country or capital, that country (for a hint). */
data class IncorrectGuess(val guess: String, val matchedCountry: Country?)

/** Loads the finished quiz by the id in the route (`answer_review/{resultId}`). */
@HiltViewModel
class AnswerReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val completedQuizRepository: CompletedQuizRepository,
    private val countryRepository: CountryRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val resultId: String? = savedStateHandle[Screen.AnswerReview.ARG_RESULT_ID]

    private val _uiState = MutableStateFlow<AnswerReviewUiState>(AnswerReviewUiState.Loading)
    val uiState: StateFlow<AnswerReviewUiState> = _uiState.asStateFlow()

    val showFlags: StateFlow<Boolean> = settingsRepository.showFlags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            val result = resultId?.let { completedQuizRepository.get(it) }
            if (result == null) {
                _uiState.value = AnswerReviewUiState.Missing
                return@launch
            }
            val quizMode = result.quizMode
            val countries = countryRepository.getCountriesByCodes(result.countryCodes)
            val sorted = if (quizMode == QuizMode.CAPITALS) {
                countries.sortedBy { it.capital }
            } else {
                countries.sortedBy { it.name }
            }
            val allCountries = if (result.incorrectGuessStrings.isEmpty()) {
                emptyList()
            } else {
                countryRepository.getAllCountries().first()
            }
            _uiState.value = AnswerReviewUiState.Loaded(
                categoryName = result.categoryName,
                quizMode = quizMode,
                category = result.category,
                countries = sorted,
                answeredCodes = result.answeredCodes.toSet(),
                incorrectGuesses = result.incorrectGuessStrings.map { guess ->
                    IncorrectGuess(guess, findMatchingCountry(guess, allCountries))
                }
            )
        }
    }

    companion object {
        /** The country whose name or capital is [guess], ignoring case and outer spaces. */
        internal fun findMatchingCountry(guess: String, allCountries: List<Country>): Country? {
            val normalised = guess.trim().lowercase()
            return allCountries.find {
                it.name.lowercase() == normalised || it.capital.lowercase() == normalised
            }
        }
    }
}
