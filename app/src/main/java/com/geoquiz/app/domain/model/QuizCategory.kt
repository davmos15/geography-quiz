package com.geoquiz.app.domain.model

sealed class QuizCategory {
    data object AllCountries : QuizCategory()
    data class StartingWithLetter(val letter: Char) : QuizCategory()
    data class EndingWithLetter(val letter: Char) : QuizCategory()
    data class ContainingLetter(val letter: Char) : QuizCategory()
    data class ByRegion(val region: String) : QuizCategory()
    data class BySubregion(val subregion: String) : QuizCategory()
    data class ByNameLengthRange(val min: Int, val max: Int, val label: String) : QuizCategory()
    data class ByWordCount(val count: Int, val label: String) : QuizCategory()
    data class EndingWithSuffix(val suffix: String) : QuizCategory()
    data class ContainingWord(val word: String) : QuizCategory()
    data object DoubleLetter : QuizCategory()
    data object ConsonantCluster : QuizCategory()
    data object RepeatedLetter3 : QuizCategory()
    data object RepeatedLetter4 : QuizCategory()
    data object StartsEndsSame : QuizCategory()
    data object AllVowelsPresent : QuizCategory()
    data object IslandCountries : QuizCategory()
    data object UniqueLetters : QuizCategory()
    data object CardinalDirection : QuizCategory()
    data object CapitalMatchesCountry : QuizCategory()

    // Flag-specific categories
    data class FlagSingleColor(val color: String) : QuizCategory()
    data class FlagColorCombo(val colors: List<String>) : QuizCategory()
    data class FlagColorCount(val count: Int) : QuizCategory()

    val displayName: String
        get() = when (this) {
            is AllCountries -> "All Countries"
            is StartingWithLetter -> "Starting with '$letter'"
            is EndingWithLetter -> "Ending with '$letter'"
            is ContainingLetter -> "Containing '$letter'"
            is ByRegion -> region
            is BySubregion -> subregion
            is ByNameLengthRange -> label
            is ByWordCount -> label
            is EndingWithSuffix -> "Ends with \"$suffix\""
            is ContainingWord -> "Contains \"$word\""
            is DoubleLetter -> "Double Letter"
            is ConsonantCluster -> "Consonant Cluster"
            is RepeatedLetter3 -> "Same Letter 3 Times"
            is RepeatedLetter4 -> "Same Letter 4+ Times"
            is StartsEndsSame -> "Starts & Ends Same"
            is AllVowelsPresent -> "All 5 Vowels"
            is IslandCountries -> "Island Nations"
            is UniqueLetters -> "All Unique Letters"
            is CardinalDirection -> "Cardinal Direction"
            is CapitalMatchesCountry -> "Same as Country"
            is FlagSingleColor -> color.replaceFirstChar { it.uppercase() }
            is FlagColorCombo -> "Only " + colors.joinToString(" & ") { it.replaceFirstChar { c -> c.uppercase() } }
            is FlagColorCount -> "$count ${if (count == 1) "color" else "colors"}"
        }

    val description: String?
        get() = when (this) {
            is DoubleLetter -> "Countries with consecutive identical letters"
            is ConsonantCluster -> "Countries with 3+ consonants in a row"
            is RepeatedLetter3 -> "Countries where one letter appears exactly 3 times"
            is RepeatedLetter4 -> "Countries where one letter appears 4 or more times"
            is StartsEndsSame -> "Countries that begin and end with the same letter"
            is AllVowelsPresent -> "Countries whose name contains all 5 vowels: A, E, I, O, U"
            is UniqueLetters -> "Countries where no letter in the name repeats"
            is CardinalDirection -> "Countries with North, South, East or West in the name"
            is CapitalMatchesCountry -> "Capitals that share their country's name"
            is IslandCountries -> "Countries with 'Island' in the name"
            is EndingWithSuffix -> "Countries ending with \"${suffix}\""
            is ContainingWord -> "Countries containing \"${word}\""
            else -> null
        }

    val typeKey: String
        get() = when (this) {
            is AllCountries -> "all"
            is StartingWithLetter -> "startletter"
            is EndingWithLetter -> "endletter"
            is ContainingLetter -> "containletter"
            is ByRegion -> "region"
            is BySubregion -> "subregion"
            is ByNameLengthRange -> "lengthrange"
            is ByWordCount -> "wordcount"
            is EndingWithSuffix -> "endsuffix"
            is ContainingWord -> "containword"
            is DoubleLetter -> "doubleletter"
            is ConsonantCluster -> "consonantcluster"
            is RepeatedLetter3 -> "repeatedletter3"
            is RepeatedLetter4 -> "repeatedletter4"
            is StartsEndsSame -> "startsendssame"
            is AllVowelsPresent -> "allvowels"
            is IslandCountries -> "island"
            is UniqueLetters -> "uniqueletters"
            is CardinalDirection -> "cardinal"
            is CapitalMatchesCountry -> "capitalmatches"
            is FlagSingleColor -> "flagcolor"
            is FlagColorCombo -> "flagcombo"
            is FlagColorCount -> "flagcount"
        }

    val valueKey: String
        get() = when (this) {
            is AllCountries -> "_"
            is StartingWithLetter -> letter.toString()
            is EndingWithLetter -> letter.toString()
            is ContainingLetter -> letter.toString()
            is ByRegion -> region
            is BySubregion -> subregion
            is ByNameLengthRange -> "$min-$max"
            is ByWordCount -> count.toString()
            is EndingWithSuffix -> suffix
            is ContainingWord -> word
            is DoubleLetter -> "_"
            is ConsonantCluster -> "_"
            is RepeatedLetter3 -> "_"
            is RepeatedLetter4 -> "_"
            is StartsEndsSame -> "_"
            is AllVowelsPresent -> "_"
            is IslandCountries -> "_"
            is UniqueLetters -> "_"
            is CardinalDirection -> "_"
            is CapitalMatchesCountry -> "_"
            is FlagSingleColor -> color
            is FlagColorCombo -> colors.sorted().joinToString("+")
            is FlagColorCount -> count.toString()
        }

    companion object {
        fun fromRoute(type: String, value: String): QuizCategory = try {
            when (type) {
                "all" -> AllCountries
                "startletter" -> if (value.isNotEmpty()) StartingWithLetter(value.first()) else AllCountries
                "endletter" -> if (value.isNotEmpty()) EndingWithLetter(value.first()) else AllCountries
                "containletter" -> if (value.isNotEmpty()) ContainingLetter(value.first()) else AllCountries
                "region" -> ByRegion(value)
                "subregion" -> BySubregion(value)
                "lengthrange" -> {
                    val parts = value.split("-")
                    if (parts.size == 2) {
                        val min = parts[0].toIntOrNull()
                        val max = parts[1].toIntOrNull()
                        if (min != null && max != null) ByNameLengthRange(min, max, "") else AllCountries
                    } else AllCountries
                }
                "wordcount" -> value.toIntOrNull()?.let { ByWordCount(it, "") } ?: AllCountries
                "endsuffix" -> EndingWithSuffix(value)
                "containword" -> ContainingWord(value)
                "doubleletter" -> DoubleLetter
                "consonantcluster" -> ConsonantCluster
                "repeatedletter3" -> RepeatedLetter3
                "repeatedletter4" -> RepeatedLetter4
                "startsendssame" -> StartsEndsSame
                "allvowels" -> AllVowelsPresent
                "island" -> IslandCountries
                "uniqueletters" -> UniqueLetters
                "cardinal" -> CardinalDirection
                "capitalmatches" -> CapitalMatchesCountry
                "flagcolor" -> FlagSingleColor(value)
                "flagcombo" -> FlagColorCombo(value.split("+").sorted())
                "flagcount" -> value.toIntOrNull()?.let { FlagColorCount(it) } ?: AllCountries
                else -> AllCountries
            }
        } catch (_: Exception) {
            AllCountries
        }
    }
}
