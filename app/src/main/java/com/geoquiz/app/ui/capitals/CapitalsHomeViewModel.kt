package com.geoquiz.app.ui.capitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.ui.home.CategoryGroupInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CapitalsHomeUiState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val categoryGroups: List<CategoryGroupInfo> = emptyList()
)

@HiltViewModel
class CapitalsHomeViewModel @Inject constructor(
    private val repository: CountryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapitalsHomeUiState())
    val uiState: StateFlow<CapitalsHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val allCountries = repository.getAllCountries().first()
                .filter { it.capital.isNotBlank() }

            val groups = buildCategoryGroups(allCountries)

            _uiState.value = CapitalsHomeUiState(
                isLoading = false,
                totalCount = allCountries.size,
                categoryGroups = groups
            )
        }
    }

    private fun buildCategoryGroups(countries: List<Country>): List<CategoryGroupInfo> {
        val regions = countries.map { it.region }.filter { it.isNotBlank() }.distinct().size
        val subregions = countries.map { it.subregion }.filter { it.isNotBlank() }.distinct().size

        // Letter-based categories use the capital name, not the country name
        val asciiStartLetters = countries
            .map { it.capital.first().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct().size

        val asciiEndLetters = countries
            .map { it.capital.last().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct().size

        val asciiContainLetters = ('A'..'Z').count { letter ->
            countries.any { it.capital.contains(letter, ignoreCase = true) }
        }

        val nameLengthCount = countries
            .map { it.capital.length }
            .distinct().size

        val patternCount = 6
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
        ).filter { it.quizCount > 0 }
    }
}
