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
    data object OneWord : QuizCategory()
    data object MultiWord : QuizCategory()
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
            is OneWord -> "One Word"
            is MultiWord -> "Multi-Word"
            is IslandCountries -> "Island Nations"
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
            is OneWord -> "oneword"
            is MultiWord -> "multiword"
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
            is OneWord -> "_"
            is MultiWord -> "_"
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
            "oneword" -> OneWord
            "multiword" -> MultiWord
            "island" -> IslandCountries
            else -> AllCountries
        }
    }
}
