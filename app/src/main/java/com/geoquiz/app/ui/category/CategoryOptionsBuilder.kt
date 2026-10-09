package com.geoquiz.app.ui.category

import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.FlagElementDao
import com.geoquiz.app.domain.model.CategoryGroup
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.FlagCategoryGroup
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import com.geoquiz.app.domain.usecase.GetCountriesForQuizUseCase
import javax.inject.Inject

/**
 * Builds the concrete quiz options of a category group from the country and flag data, in the
 * order the category list shows them. Options with no countries are left out.
 *
 * Shared by the category list ([CategoryListViewModel]) and the Play tab's "Recommended next"
 * card, so both see exactly the same options. Extracted from [CategoryListViewModel] (3.4b)
 * without changing behaviour.
 */
class CategoryOptionsBuilder @Inject constructor(
    private val flagColorDao: FlagColorDao,
    private val flagElementDao: FlagElementDao
) {

    /**
     * The countries a [mode]'s category list works over: those with a capital in Capitals mode,
     * all of them otherwise.
     */
    fun countriesFor(mode: QuizMode, allCountries: List<Country>): List<Country> =
        if (mode == QuizMode.CAPITALS) allCountries.filter { it.capital.isNotBlank() } else allCountries

    /**
     * Options of the group [groupId] (a [CategoryGroup] or [FlagCategoryGroup] id) in [mode], over
     * [countries] as given by [countriesFor]. Unknown ids give no options.
     */
    suspend fun build(mode: QuizMode, groupId: String, countries: List<Country>): List<QuizOptionInfo> {
        val group = CategoryGroup.fromId(groupId)
        val flagGroup = FlagCategoryGroup.fromId(groupId)
        return if (group != null) {
            buildQuizOptions(mode, group, countries)
        } else if (flagGroup != null) {
            buildFlagQuizOptions(flagGroup, countries)
        } else {
            emptyList()
        }
    }

    /** The name asked in [mode]: the capital in Capitals mode, the country name otherwise. */
    private fun Country.quizName(mode: QuizMode): String = if (mode == QuizMode.CAPITALS) capital else name

    private fun buildQuizOptions(
        mode: QuizMode,
        group: CategoryGroup,
        countries: List<Country>
    ): List<QuizOptionInfo> = when (group) {
        CategoryGroup.ALL_COUNTRIES -> {
            val options = mutableListOf(
                QuizOptionInfo(
                    if (mode == QuizMode.CAPITALS) "All Capitals" else "All Countries",
                    countries.size, "all", "_"
                )
            )
            if (mode == QuizMode.CAPITALS) {
                val matchCount = countries.count {
                    GetCountriesForQuizUseCase.capitalMatchesCountryName(it)
                }
                if (matchCount > 0) {
                    options.add(
                        QuizOptionInfo(
                            "Same as Country", matchCount, "capitalmatches", "_",
                            description = QuizCategory.CapitalMatchesCountry.description
                        )
                    )
                }
            }
            options
        }

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
            .map { it.quizName(mode).first().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.quizName(mode).first().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "startletter", letter.toString()
                )
            }

        CategoryGroup.ENDING_LETTER -> countries
            .map { it.quizName(mode).last().uppercaseChar() }
            .filter { it in 'A'..'Z' }
            .distinct()
            .sorted()
            .map { letter ->
                val count = countries.count {
                    it.quizName(mode).last().uppercaseChar() == letter
                }
                QuizOptionInfo(
                    letter.toString(), count, "endletter", letter.toString()
                )
            }

        CategoryGroup.CONTAINING_LETTER -> ('A'..'Z')
            .map { letter ->
                val count = countries.count {
                    it.quizName(mode).contains(letter, ignoreCase = true)
                }
                QuizOptionInfo(
                    letter.toString(), count, "containletter", letter.toString()
                )
            }
            .filter { it.countryCount > 0 }

        CategoryGroup.NAME_LENGTH -> {
            val lengthCounts = countries
                .groupBy { it.quizName(mode).length }
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
                    countries.count { doubleLetterRegex.containsMatchIn(it.quizName(mode)) },
                    "doubleletter", "_",
                    description = QuizCategory.DoubleLetter.description
                ),
                QuizOptionInfo(
                    "Consonant Cluster (3+)",
                    countries.count { consonantClusterRegex.containsMatchIn(it.quizName(mode)) },
                    "consonantcluster", "_",
                    description = QuizCategory.ConsonantCluster.description
                ),
                QuizOptionInfo(
                    "Same Letter 3 Times",
                    countries.count { country ->
                        val counts = country.quizName(mode).lowercase().groupBy { it }
                        counts.any { (ch, occ) -> ch.isLetter() && occ.size >= 3 } &&
                                counts.none { (ch, occ) -> ch.isLetter() && occ.size >= 4 }
                    },
                    "repeatedletter3", "_",
                    description = QuizCategory.RepeatedLetter3.description
                ),
                QuizOptionInfo(
                    "Same Letter 4+ Times",
                    countries.count { country ->
                        country.quizName(mode).lowercase().groupBy { it }
                            .any { (ch, occ) -> ch.isLetter() && occ.size >= 4 }
                    },
                    "repeatedletter4", "_",
                    description = QuizCategory.RepeatedLetter4.description
                ),
                QuizOptionInfo(
                    "Starts & Ends Same",
                    countries.count { it.quizName(mode).first().uppercaseChar() == it.quizName(mode).last().uppercaseChar() },
                    "startsendssame", "_",
                    description = QuizCategory.StartsEndsSame.description
                ),
                QuizOptionInfo(
                    "Contains All 5 Vowels",
                    countries.count { country ->
                        val lower = country.quizName(mode).lowercase()
                        vowels.all { it in lower }
                    },
                    "allvowels", "_",
                    description = QuizCategory.AllVowelsPresent.description
                ),
                QuizOptionInfo(
                    "All Unique Letters",
                    countries.count { country ->
                        val letters = country.quizName(mode).lowercase().filter { it in 'a'..'z' }
                        letters.length == letters.toSet().size
                    },
                    "uniqueletters", "_",
                    description = QuizCategory.UniqueLetters.description
                ),
                QuizOptionInfo(
                    "Ending in a Vowel",
                    countries.count { it.quizName(mode).last().lowercaseChar() in vowels },
                    "endvowel", "_",
                    description = QuizCategory.EndingInVowel.description
                ),
                QuizOptionInfo(
                    "Single Vowel Type",
                    countries.count { country ->
                        val dv = country.quizName(mode).lowercase().filter { it in vowels }.toSet()
                        dv.size == 1
                    },
                    "singlevowel", "_",
                    description = QuizCategory.SingleVowelType.description
                )
            ).filter { it.countryCount > 0 }
        }

        CategoryGroup.WORD_PATTERNS -> {
            val name = { c: Country -> c.quizName(mode) }
            val cardinalRegex = Regex("\\b(North|South|East|West)\\b", RegexOption.IGNORE_CASE)
            val oneWord = countries.count { name(it).split(" ").size == 1 }
            val multiWord = countries.count { name(it).split(" ").size >= 2 }
            val twoWord = countries.count { name(it).split(" ").size == 2 }
            val endsStan = countries.count { name(it).endsWith("stan", ignoreCase = true) }
            val endsLand = countries.count { name(it).endsWith("land", ignoreCase = true) }
            val containsUnited = countries.count { name(it).contains("United", ignoreCase = true) }
            val containsGuinea = countries.count { name(it).contains("Guinea", ignoreCase = true) }
            val cardinalCount = countries.count { cardinalRegex.containsMatchIn(name(it)) }

            listOf(
                QuizOptionInfo("One-Word Names", oneWord, "wordcount", "1"),
                QuizOptionInfo("Multi-Word Names", multiWord, "wordcount", "-2"),
                QuizOptionInfo("Two-Word Names", twoWord, "wordcount", "2"),
                QuizOptionInfo("Ends with \"stan\"", endsStan, "endsuffix", "stan",
                    description = QuizCategory.EndingWithSuffix("stan").description),
                QuizOptionInfo("Ends with \"land\"", endsLand, "endsuffix", "land",
                    description = QuizCategory.EndingWithSuffix("land").description),
                QuizOptionInfo("Contains \"United\"", containsUnited, "containword", "United",
                    description = QuizCategory.ContainingWord("United").description),
                QuizOptionInfo("Contains \"Guinea\"", containsGuinea, "containword", "Guinea",
                    description = QuizCategory.ContainingWord("Guinea").description),
                QuizOptionInfo(
                    "Cardinal Direction", cardinalCount, "cardinal", "_",
                    description = QuizCategory.CardinalDirection.description
                )
            ).filter { it.countryCount > 0 }
        }

        CategoryGroup.ISLAND_COUNTRIES -> {
            val count = countries.count { it.quizName(mode).contains("island", ignoreCase = true) }
            listOf(
                QuizOptionInfo("Island Nations", count, "island", "_",
                    description = QuizCategory.IslandCountries.description)
            ).filter { it.countryCount > 0 }
        }
    }

    private suspend fun buildFlagQuizOptions(
        group: FlagCategoryGroup,
        countries: List<Country>
    ): List<QuizOptionInfo> {
        // Batch-fetch all flag color mappings once to avoid N+1 queries
        val allMappings = flagColorDao.getAllMappings()
        val validCodes = countries.map { it.code }.toSet()
        val colorsByCountry = allMappings
            .filter { it.countryCca3 in validCodes }
            .groupBy({ it.countryCca3 }, { it.color })
            .mapValues { it.value.toSet() }
        val allColors = allMappings.map { it.color }.distinct().sorted()
        val countriesByColor = allMappings
            .filter { it.countryCca3 in validCodes }
            .groupBy({ it.color }, { it.countryCca3 })
            .mapValues { it.value.toSet() }

        return when (group) {
            FlagCategoryGroup.FLAG_SINGLE_COLOR -> {
                allColors.map { color ->
                    val count = countriesByColor[color]?.size ?: 0
                    QuizOptionInfo(
                        color.replaceFirstChar { it.uppercase() },
                        count,
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
                        val codes1 = countriesByColor[c1] ?: emptySet()
                        val codes2 = countriesByColor[c2] ?: emptySet()
                        val candidates = codes1.intersect(codes2)
                        val exactMatches = candidates.count { code ->
                            colorsByCountry[code] == targetColors
                        }
                        if (exactMatches >= 2) {
                            val sorted = listOf(c1, c2).sorted()
                            combos.add(
                                QuizOptionInfo(
                                    "Only " + sorted.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } },
                                    exactMatches,
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
                            val codes1 = countriesByColor[c1] ?: emptySet()
                            val codes2 = countriesByColor[c2] ?: emptySet()
                            val codes3 = countriesByColor[c3] ?: emptySet()
                            val candidates = codes1.intersect(codes2).intersect(codes3)
                            val exactMatches = candidates.count { code ->
                                colorsByCountry[code] == targetColors
                            }
                            if (exactMatches >= 2) {
                                val sorted = listOf(c1, c2, c3).sorted()
                                combos.add(
                                    QuizOptionInfo(
                                        "Only " + sorted.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } },
                                        exactMatches,
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
                for ((_, colors) in colorsByCountry) {
                    val colorCount = colors.size
                    countMap[colorCount] = (countMap[colorCount] ?: 0) + 1
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

            FlagCategoryGroup.FLAG_ELEMENTS -> {
                val allElements = flagElementDao.getAllMappings()
                val validElements = allElements.filter { it.countryCca3 in validCodes }
                validElements.groupBy { it.element }
                    .map { (element, mappings) ->
                        QuizOptionInfo(
                            QuizCategory.ELEMENT_DISPLAY_NAMES[element] ?: element.replaceFirstChar { it.uppercase() },
                            mappings.size,
                            "flagelement",
                            element
                        )
                    }
                    .filter { it.countryCount > 0 }
                    .sortedByDescending { it.countryCount }
            }
        }
    }

    companion object {
        private val SUBREGION_DISPLAY_NAMES = mapOf(
            "Australia and New Zealand" to "Australasia"
        )
    }
}
