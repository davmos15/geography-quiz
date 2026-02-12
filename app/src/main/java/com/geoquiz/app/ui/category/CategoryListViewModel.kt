package com.geoquiz.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class CategoryListUiState(
    val isLoading: Boolean = true,
    val groupName: String = "",
    val groupDescription: String = "",
    val quizOptions: List<QuizOptionInfo> = emptyList()
)

data class QuizOptionInfo(
    val name: String,
    val countryCount: Int,
    val categoryType: String,
    val categoryValue: String
)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CountryRepository
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""

    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val allCountries = repository.getAllCountries().first()
            val group = CategoryGroup.fromId(groupId)

            if (group != null) {
                val options = buildQuizOptions(group, allCountries)
                _uiState.value = CategoryListUiState(
                    isLoading = false,
                    groupName = group.displayName,
                    groupDescription = group.description,
                    quizOptions = options
                )
            } else {
                _uiState.value = CategoryListUiState(isLoading = false, groupName = "Unknown")
            }
        }
    }

    private fun buildQuizOptions(
        group: CategoryGroup,
        countries: List<Country>
    ): List<QuizOptionInfo> = when (group) {
        CategoryGroup.ALL_COUNTRIES -> listOf(
            QuizOptionInfo("All Countries", countries.size, "all", "_")
        )

        CategoryGroup.REGIONS -> countries
            .map { it.region }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { region ->
                val count = countries.count { it.region == region }
                QuizOptionInfo(region, count, "region", region)
            }

        CategoryGroup.SUBREGIONS -> countries
            .map { it.subregion }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { subregion ->
                val count = countries.count { it.subregion == subregion }
                QuizOptionInfo(subregion, count, "subregion", subregion)
            }

        CategoryGroup.STARTING_LETTER -> countries
            .map { it.name.first().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.name.first().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "startletter", letter.toString()
                )
            }

        CategoryGroup.ENDING_LETTER -> countries
            .map { it.name.last().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.name.last().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "endletter", letter.toString()
                )
            }

        CategoryGroup.CONTAINING_LETTER -> ('A'..'Z')
            .map { letter ->
                val count = countries.count {
                    it.name.contains(letter, ignoreCase = true)
                }
                QuizOptionInfo(
                    letter.toString(), count, "containletter", letter.toString()
                )
            }
            .filter { it.countryCount > 0 }

        CategoryGroup.NAME_LENGTH -> listOf(
            QuizOptionInfo("Short (3-5 letters)", countries.count { it.name.length in 3..5 }, "lengthrange", "3-5"),
            QuizOptionInfo("Medium (6-8 letters)", countries.count { it.name.length in 6..8 }, "lengthrange", "6-8"),
            QuizOptionInfo("Long (9-12 letters)", countries.count { it.name.length in 9..12 }, "lengthrange", "9-12"),
            QuizOptionInfo("Very Long (13+)", countries.count { it.name.length >= 13 }, "lengthrange", "13-99")
        ).filter { it.countryCount > 0 }

        CategoryGroup.WORD_COUNT -> listOf(
            QuizOptionInfo(
                "One Word",
                countries.count { !it.name.contains(' ') && !it.name.contains('-') },
                "oneword", "_"
            ),
            QuizOptionInfo(
                "Multi-Word",
                countries.count { it.name.contains(' ') || it.name.contains('-') },
                "multiword", "_"
            )
        ).filter { it.countryCount > 0 }

        CategoryGroup.LETTER_PATTERNS -> {
            val doubleLetterRegex = Regex("(.)\\1", RegexOption.IGNORE_CASE)
            val count = countries.count { doubleLetterRegex.containsMatchIn(it.name) }
            listOf(
                QuizOptionInfo("Double Letter", count, "doubleletter", "_")
            ).filter { it.countryCount > 0 }
        }

        CategoryGroup.ISLAND_COUNTRIES -> {
            val count = countries.count { it.name.contains("island", ignoreCase = true) }
            listOf(
                QuizOptionInfo("Island Nations", count, "island", "_")
            ).filter { it.countryCount > 0 }
        }
    }
}
