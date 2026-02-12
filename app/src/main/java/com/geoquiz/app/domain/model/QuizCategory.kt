package com.geoquiz.app.domain.model

sealed class QuizCategory {
    data object AllCountries : QuizCategory()
    data class StartingWithLetter(val letter: Char) : QuizCategory()
    data class EndingWithLetter(val letter: Char) : QuizCategory()
    data class ContainingLetter(val letter: Char) : QuizCategory()
    data class ByRegion(val region: String) : QuizCategory()
    data class BySubregion(val subregion: String) : QuizCategory()
    data class ByNameLengthRange(val min: Int, val max: Int, val label: String) : QuizCategory()
    data object DoubleLetter : QuizCategory()
    data object ConsonantCluster : QuizCategory()
    data object RepeatedLetter3 : QuizCategory()
    data object StartsEndsSame : QuizCategory()
    data object PalindromeName : QuizCategory()
    data object AllVowelsPresent : QuizCategory()
    data object IslandCountries : QuizCategory()

    val displayName: String
        get() = when (this) {
            is AllCountries -> "All Countries"
            is StartingWithLetter -> "Starting with '$letter'"
            is EndingWithLetter -> "Ending with '$letter'"
            is ContainingLetter -> "Containing '$letter'"
            is ByRegion -> region
            is BySubregion -> subregion
            is ByNameLengthRange -> label
            is DoubleLetter -> "Double Letter"
            is ConsonantCluster -> "Consonant Cluster"
            is RepeatedLetter3 -> "Repeated Letter (3+)"
            is StartsEndsSame -> "Starts & Ends Same"
            is PalindromeName -> "Palindrome"
            is AllVowelsPresent -> "All 5 Vowels"
            is IslandCountries -> "Island Nations"
        }

    val description: String?
        get() = when (this) {
            is DoubleLetter -> "Countries with consecutive identical letters (e.g., Morocco, Greece)"
            is ConsonantCluster -> "Countries with 3+ consonants in a row (e.g., Kyrgyzstan)"
            is RepeatedLetter3 -> "Countries where one letter appears at least 3 times (e.g., Madagascar)"
            is StartsEndsSame -> "Countries that begin and end with the same letter (e.g., Austria)"
            is PalindromeName -> "Countries containing a palindrome of 3+ letters (e.g., Iran \u2192 'ira')"
            is AllVowelsPresent -> "Countries whose name contains all 5 vowels: A, E, I, O, U (e.g., Mozambique)"
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
            is DoubleLetter -> "doubleletter"
            is ConsonantCluster -> "consonantcluster"
            is RepeatedLetter3 -> "repeatedletter3"
            is StartsEndsSame -> "startsendssame"
            is PalindromeName -> "palindrome"
            is AllVowelsPresent -> "allvowels"
            is IslandCountries -> "island"
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
            is DoubleLetter -> "_"
            is ConsonantCluster -> "_"
            is RepeatedLetter3 -> "_"
            is StartsEndsSame -> "_"
            is PalindromeName -> "_"
            is AllVowelsPresent -> "_"
            is IslandCountries -> "_"
        }

    companion object {
        fun fromRoute(type: String, value: String): QuizCategory = when (type) {
            "all" -> AllCountries
            "startletter" -> StartingWithLetter(value.first())
            "endletter" -> EndingWithLetter(value.first())
            "containletter" -> ContainingLetter(value.first())
            "region" -> ByRegion(value)
            "subregion" -> BySubregion(value)
            "lengthrange" -> {
                val parts = value.split("-")
                ByNameLengthRange(parts[0].toInt(), parts[1].toInt(), "")
            }
            "doubleletter" -> DoubleLetter
            "consonantcluster" -> ConsonantCluster
            "repeatedletter3" -> RepeatedLetter3
            "startsendssame" -> StartsEndsSame
            "palindrome" -> PalindromeName
            "allvowels" -> AllVowelsPresent
            "island" -> IslandCountries
            else -> AllCountries
        }
    }
}
