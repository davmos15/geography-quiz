package com.geoquiz.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.SavedQuizEntity
import com.geoquiz.app.data.repository.SavedQuizRepository
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val categoryGroups: List<CategoryGroupInfo> = emptyList(),
    val savedQuiz: SavedQuizInfo? = null
)

data class CategoryGroupInfo(
    val group: CategoryGroup,
    val quizCount: Int
)

data class SavedQuizInfo(
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String,
    val answeredCount: Int,
    val timeElapsed: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CountryRepository,
    private val savedQuizRepository: SavedQuizRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val allCountries = repository.getAllCountries().first()

            val groups = buildCategoryGroups(allCountries)

            _uiState.value = HomeUiState(
                isLoading = false,
                totalCount = allCountries.size,
                categoryGroups = groups
            )
        }

        // Watch for saved quiz changes
        viewModelScope.launch {
            savedQuizRepository.savedQuiz.collect { entity ->
                _uiState.value = _uiState.value.copy(
                    savedQuiz = entity?.toSavedQuizInfo()
                )
            }
        }
    }

    private fun SavedQuizEntity.toSavedQuizInfo(): SavedQuizInfo {
        val category = QuizCategory.fromRoute(categoryType, categoryValue)
        val answeredCount = savedQuizRepository.parseAnsweredCodes(answeredCountryCodes).size
        return SavedQuizInfo(
            categoryType = categoryType,
            categoryValue = categoryValue,
            categoryDisplayName = category.displayName,
            answeredCount = answeredCount,
            timeElapsed = timeElapsedSeconds
        )
    }

    fun dismissSavedQuiz() {
        viewModelScope.launch {
            savedQuizRepository.clearSavedQuiz()
        }
    }

    private fun buildCategoryGroups(countries: List<Country>): List<CategoryGroupInfo> {
        val regions = countries.map { it.region }.filter { it.isNotBlank() }.distinct().size
        val subregions = countries.map { it.subregion }.filter { it.isNotBlank() }.distinct().size

        val asciiStartLetters = countries
            .map { it.name.first().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct().size

        val asciiEndLetters = countries
            .map { it.name.last().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct().size

        val asciiContainLetters = ('A'..'Z').count { letter ->
            countries.any { it.name.contains(letter, ignoreCase = true) }
        }

        // Count distinct name lengths that have countries
        val nameLengthCount = countries
            .map { it.name.length }
            .distinct().size

        val islandCount = countries.count { it.name.contains("island", ignoreCase = true) }

        // Letter patterns: count how many quiz options we'll have (6 pattern types)
        val patternCount = 6

        // Word patterns: 7 quiz options
        val wordPatternCount = 7

        return listOf(
            CategoryGroupInfo(CategoryGroup.ALL_COUNTRIES, 1),
            CategoryGroupInfo(CategoryGroup.REGIONS, regions),
            CategoryGroupInfo(CategoryGroup.SUBREGIONS, subregions),
            CategoryGroupInfo(CategoryGroup.STARTING_LETTER, asciiStartLetters),
            CategoryGroupInfo(CategoryGroup.ENDING_LETTER, asciiEndLetters),
            CategoryGroupInfo(CategoryGroup.CONTAINING_LETTER, asciiContainLetters),
            CategoryGroupInfo(CategoryGroup.NAME_LENGTH, nameLengthCount),
            CategoryGroupInfo(CategoryGroup.LETTER_PATTERNS, patternCount),
            CategoryGroupInfo(CategoryGroup.WORD_PATTERNS, wordPatternCount),
            CategoryGroupInfo(CategoryGroup.ISLAND_COUNTRIES, if (islandCount > 0) 1 else 0)
        ).filter { it.quizCount > 0 }
    }
}
