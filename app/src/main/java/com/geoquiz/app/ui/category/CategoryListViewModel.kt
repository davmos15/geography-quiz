package com.geoquiz.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.data.repository.QuizHistoryRepository
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
    val quizOptions: List<QuizOptionInfo> = emptyList(),
    val hideCompleted: Boolean = false
)

data class QuizOptionInfo(
    val name: String,
    val countryCount: Int,
    val categoryType: String,
    val categoryValue: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val bestScore: Double? = null,
    val bestCorrect: Int? = null,
    val bestTotal: Int? = null
)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CountryRepository,
    private val flagColorDao: FlagColorDao,
    private val quizHistoryRepository: QuizHistoryRepository
) : ViewModel() {

    private val groupId: String = savedStateHandle["groupId"] ?: ""
    private val quizMode: String = savedStateHandle["quizMode"] ?: "countries"

    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val allCountries = if (quizMode == "capitals") {
                repository.getAllCountries().first().filter { it.capital.isNotBlank() }
            } else {
                repository.getAllCountries().first()
            }

            // Try regular CategoryGroup first, then FlagCategoryGroup
            val group = CategoryGroup.fromId(groupId)
            val flagGroup = FlagCategoryGroup.fromId(groupId)

            val options = if (group != null) {
                buildQuizOptions(group, allCountries)
            } else if (flagGroup != null) {
                buildFlagQuizOptions(flagGroup, allCountries)
            } else {
                emptyList()
            }

            val groupName = group?.displayName ?: flagGroup?.displayName ?: "Unknown"
            val groupDescription = group?.description ?: flagGroup?.description ?: ""

            // Enrich with best scores from history
            val bestScores = quizHistoryRepository.getAllBestScoresForMode(quizMode)
            val scoreMap = bestScores.associateBy { "${it.categoryType}|${it.categoryValue}" }
            val enrichedOptions = options.map { option ->
                val key = "${option.categoryType}|${option.categoryValue}"
                val best = scoreMap[key]
                if (best != null) {
                    option.copy(
                        isCompleted = true,
                        bestScore = best.score,
                        bestCorrect = best.correctAnswers,
                        bestTotal = best.totalQuestions
                    )
                } else {
                    option
                }
            }

            _uiState.value = CategoryListUiState(
                isLoading = false,
                groupName = groupName,
                groupDescription = groupDescription,
                quizOptions = enrichedOptions
            )
        }
    }

    fun toggleHideCompleted() {
        _uiState.value = _uiState.value.copy(
            hideCompleted = !_uiState.value.hideCompleted
        )
    }

    /** Returns the relevant name for the current quiz mode — capital name for capitals, country name otherwise. */
    private fun Country.quizName(): String = if (quizMode == "capitals") capital else name

    private fun buildQuizOptions(
        group: CategoryGroup,
        countries: List<Country>
    ): List<QuizOptionInfo> = when (group) {
        CategoryGroup.ALL_COUNTRIES -> listOf(
            QuizOptionInfo(
                if (quizMode == "capitals") "All Capitals" else "All Countries",
                countries.size, "all", "_"
            )
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
                val displayName = SUBREGION_DISPLAY_NAMES[subregion] ?: subregion
                QuizOptionInfo(displayName, count, "subregion", subregion)
            }

        CategoryGroup.STARTING_LETTER -> countries
            .map { it.quizName().first().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.quizName().first().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "startletter", letter.toString()
                )
            }

        CategoryGroup.ENDING_LETTER -> countries
            .map { it.quizName().last().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.quizName().last().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "endletter", letter.toString()
                )
            }

        CategoryGroup.CONTAINING_LETTER -> ('A'..'Z')
            .map { letter ->
                val count = countries.count {
                    it.quizName().contains(letter, ignoreCase = true)
                }
                QuizOptionInfo(
                    letter.toString(), count, "containletter", letter.toString()
                )
            }
            .filter { it.countryCount > 0 }

        CategoryGroup.NAME_LENGTH -> {
            val lengthCounts = countries
                .groupBy { it.quizName().length }
                .mapValues { it.value.size }
                .toSortedMap()

            lengthCounts.map { (length, count) ->
                QuizOptionInfo(
                    "$length letters",
                    count,
                    "lengthrange",
                    "$length-$length"
                )
            }
        }

        CategoryGroup.LETTER_PATTERNS -> {
            val doubleLetterRegex = Regex("(.)\\1", RegexOption.IGNORE_CASE)
            val consonantClusterRegex = Regex("[bcdfghjklmnpqrstvwxyz]{3,}", RegexOption.IGNORE_CASE)
            val vowels = setOf('a', 'e', 'i', 'o', 'u')

            listOf(
                QuizOptionInfo(
                    "Double Letter",
                    countries.count { doubleLetterRegex.containsMatchIn(it.quizName()) },
                    "doubleletter", "_",
                    description = QuizCategory.DoubleLetter.description
                ),
                QuizOptionInfo(
                    "Consonant Cluster (3+)",
                    countries.count { consonantClusterRegex.containsMatchIn(it.quizName()) },
                    "consonantcluster", "_",
                    description = QuizCategory.ConsonantCluster.description
                ),
                QuizOptionInfo(
                    "Same Letter 3 Times",
                    countries.count { country ->
                        val counts = country.quizName().lowercase().groupBy { it }
                        counts.any { (ch, occ) -> ch.isLetter() && occ.size >= 3 } &&
                                counts.none { (ch, occ) -> ch.isLetter() && occ.size >= 4 }
                    },
                    "repeatedletter3", "_",
                    description = QuizCategory.RepeatedLetter3.description
                ),
                QuizOptionInfo(
                    "Same Letter 4+ Times",
                    countries.count { country ->
                        country.quizName().lowercase().groupBy { it }
                            .any { (ch, occ) -> ch.isLetter() && occ.size >= 4 }
                    },
                    "repeatedletter4", "_",
                    description = QuizCategory.RepeatedLetter4.description
                ),
                QuizOptionInfo(
                    "Starts & Ends Same",
                    countries.count { it.quizName().first().uppercaseChar() == it.quizName().last().uppercaseChar() },
                    "startsendssame", "_",
                    description = QuizCategory.StartsEndsSame.description
                ),
                QuizOptionInfo(
                    "Contains All 5 Vowels",
                    countries.count { country ->
                        val lower = country.quizName().lowercase()
                        vowels.all { it in lower }
                    },
                    "allvowels", "_",
                    description = QuizCategory.AllVowelsPresent.description
                )
            ).filter { it.countryCount > 0 }
        }

        CategoryGroup.WORD_PATTERNS -> {
            val name = { c: Country -> c.quizName() }
            val oneWord = countries.count { name(it).split(" ").size == 1 }
            val multiWord = countries.count { name(it).split(" ").size >= 2 }
            val twoWord = countries.count { name(it).split(" ").size == 2 }
            val endsStan = countries.count { name(it).endsWith("stan", ignoreCase = true) }
            val endsLand = countries.count { name(it).endsWith("land", ignoreCase = true) }
            val containsUnited = countries.count { name(it).contains("United", ignoreCase = true) }
            val containsGuinea = countries.count { name(it).contains("Guinea", ignoreCase = true) }

            listOf(
                QuizOptionInfo("One-Word Names", oneWord, "wordcount", "1"),
                QuizOptionInfo("Multi-Word Names", multiWord, "wordcount", "-2"),
                QuizOptionInfo("Two-Word Names", twoWord, "wordcount", "2"),
                QuizOptionInfo("Ends with \"stan\"", endsStan, "endsuffix", "stan"),
                QuizOptionInfo("Ends with \"land\"", endsLand, "endsuffix", "land"),
                QuizOptionInfo("Contains \"United\"", containsUnited, "containword", "United"),
                QuizOptionInfo("Contains \"Guinea\"", containsGuinea, "containword", "Guinea")
            ).filter { it.countryCount > 0 }
        }

        CategoryGroup.ISLAND_COUNTRIES -> {
            val count = countries.count { it.quizName().contains("island", ignoreCase = true) }
            listOf(
                QuizOptionInfo("Island Nations", count, "island", "_")
            ).filter { it.countryCount > 0 }
        }
    }

    private suspend fun buildFlagQuizOptions(
        group: FlagCategoryGroup,
        countries: List<Country>
    ): List<QuizOptionInfo> {
        val allColors = flagColorDao.getAllColors()
        val countryByCode = countries.associateBy { it.code }

        return when (group) {
            FlagCategoryGroup.FLAG_SINGLE_COLOR -> {
                allColors.map { color ->
                    val codes = flagColorDao.getCountryCodesForColor(color).filter { it in countryByCode }
                    QuizOptionInfo(
                        color.replaceFirstChar { it.uppercase() },
                        codes.size,
                        "flagcolor",
                        color
                    )
                }.filter { it.countryCount > 0 }.sortedByDescending { it.countryCount }
            }

            FlagCategoryGroup.FLAG_TWO_COLOR_COMBO -> {
                val combos = mutableListOf<QuizOptionInfo>()
                for (i in allColors.indices) {
                    for (j in i + 1 until allColors.size) {
                        val c1 = allColors[i]
                        val c2 = allColors[j]
                        val targetColors = setOf(c1, c2)
                        val codes1 = flagColorDao.getCountryCodesForColor(c1).toSet()
                        val codes2 = flagColorDao.getCountryCodesForColor(c2).toSet()
                        val candidates = codes1.intersect(codes2).filter { it in countryByCode }
                        val exactMatches = candidates.filter { code ->
                            flagColorDao.getColorsForCountry(code).toSet() == targetColors
                        }
                        if (exactMatches.size >= 2) {
                            val sorted = listOf(c1, c2).sorted()
                            combos.add(
                                QuizOptionInfo(
                                    "Only " + sorted.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } },
                                    exactMatches.size,
                                    "flagcombo",
                                    sorted.joinToString("+")
                                )
                            )
                        }
                    }
                }
                combos.sortedByDescending { it.countryCount }
            }

            FlagCategoryGroup.FLAG_THREE_COLOR_COMBO -> {
                val combos = mutableListOf<QuizOptionInfo>()
                for (i in allColors.indices) {
                    for (j in i + 1 until allColors.size) {
                        for (k in j + 1 until allColors.size) {
                            val c1 = allColors[i]
                            val c2 = allColors[j]
                            val c3 = allColors[k]
                            val targetColors = setOf(c1, c2, c3)
                            val codes1 = flagColorDao.getCountryCodesForColor(c1).toSet()
                            val codes2 = flagColorDao.getCountryCodesForColor(c2).toSet()
                            val codes3 = flagColorDao.getCountryCodesForColor(c3).toSet()
                            val candidates = codes1.intersect(codes2).intersect(codes3).filter { it in countryByCode }
                            val exactMatches = candidates.filter { code ->
                                flagColorDao.getColorsForCountry(code).toSet() == targetColors
                            }
                            if (exactMatches.size >= 2) {
                                val sorted = listOf(c1, c2, c3).sorted()
                                combos.add(
                                    QuizOptionInfo(
                                        "Only " + sorted.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } },
                                        exactMatches.size,
                                        "flagcombo",
                                        sorted.joinToString("+")
                                    )
                                )
                            }
                        }
                    }
                }
                combos.sortedByDescending { it.countryCount }
            }

            FlagCategoryGroup.FLAG_COLOR_COUNT -> {
                val countMap = mutableMapOf<Int, Int>()
                for (country in countries) {
                    val colorCount = flagColorDao.getColorCountForCountry(country.code)
                    if (colorCount > 0) {
                        countMap[colorCount] = (countMap[colorCount] ?: 0) + 1
                    }
                }
                countMap.entries.sortedBy { it.key }.map { (count, numCountries) ->
                    QuizOptionInfo(
                        "$count ${if (count == 1) "color" else "colors"}",
                        numCountries,
                        "flagcount",
                        count.toString()
                    )
                }.filter { it.countryCount > 0 }
            }
        }
    }

    companion object {
        private val SUBREGION_DISPLAY_NAMES = mapOf(
            "Australia and New Zealand" to "Australasia"
        )

    }
}
