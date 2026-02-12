package com.geoquiz.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
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
    val categoryGroups: List<CategoryGroupInfo> = emptyList()
)

data class CategoryGroupInfo(
    val group: CategoryGroup,
    val quizCount: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CountryRepository
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

        val doubleLetterCount = countries.count { country ->
            Regex("(.)\\1", RegexOption.IGNORE_CASE).containsMatchIn(country.name)
        }

        val islandCount = countries.count { it.name.contains("island", ignoreCase = true) }

        return listOf(
            CategoryGroupInfo(CategoryGroup.ALL_COUNTRIES, 1),
            CategoryGroupInfo(CategoryGroup.REGIONS, regions),
            CategoryGroupInfo(CategoryGroup.SUBREGIONS, subregions),
            CategoryGroupInfo(CategoryGroup.STARTING_LETTER, asciiStartLetters),
            CategoryGroupInfo(CategoryGroup.ENDING_LETTER, asciiEndLetters),
            CategoryGroupInfo(CategoryGroup.CONTAINING_LETTER, asciiContainLetters),
            CategoryGroupInfo(CategoryGroup.NAME_LENGTH, 4),
            CategoryGroupInfo(CategoryGroup.WORD_COUNT, 2),
            CategoryGroupInfo(CategoryGroup.LETTER_PATTERNS, if (doubleLetterCount > 0) 1 else 0),
            CategoryGroupInfo(CategoryGroup.ISLAND_COUNTRIES, if (islandCount > 0) 1 else 0)
        ).filter { it.quizCount > 0 }
    }
}
