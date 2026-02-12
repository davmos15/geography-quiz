package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCountriesForCapitalQuizUseCase @Inject constructor(
    private val repository: CountryRepository
) {

    suspend operator fun invoke(category: QuizCategory): List<Country> {
        repository.ensureSeeded()
        val allCountries = repository.getAllCountries().first()
            .filter { it.capital.isNotBlank() }

        return when (category) {
            is QuizCategory.AllCountries -> allCountries

            is QuizCategory.StartingWithLetter -> allCountries.filter {
                it.capital.first().uppercaseChar() == category.letter.uppercaseChar()
            }

            is QuizCategory.EndingWithLetter -> allCountries.filter {
                it.capital.last().uppercaseChar() == category.letter.uppercaseChar()
            }

            is QuizCategory.ContainingLetter -> allCountries.filter {
                it.capital.contains(category.letter, ignoreCase = true)
            }

            is QuizCategory.ByRegion -> allCountries.filter {
                it.region.equals(category.region, ignoreCase = true)
            }

            is QuizCategory.BySubregion -> allCountries.filter {
                it.subregion.equals(category.subregion, ignoreCase = true)
            }

            is QuizCategory.ByNameLengthRange -> allCountries.filter {
                it.capital.length in category.min..category.max
            }

            is QuizCategory.ByWordCount -> allCountries.filter {
                val count = it.capital.split(" ").size
                if (category.count < 0) count >= -category.count
                else count == category.count
            }

            is QuizCategory.EndingWithSuffix -> allCountries.filter {
                it.capital.endsWith(category.suffix, ignoreCase = true)
            }

            is QuizCategory.ContainingWord -> allCountries.filter {
                it.capital.contains(category.word, ignoreCase = true)
            }

            is QuizCategory.DoubleLetter -> allCountries.filter {
                DOUBLE_LETTER_REGEX.containsMatchIn(it.capital)
            }

            is QuizCategory.ConsonantCluster -> allCountries.filter {
                CONSONANT_CLUSTER_REGEX.containsMatchIn(it.capital)
            }

            is QuizCategory.RepeatedLetter3 -> allCountries.filter {
                hasRepeatedLetter(it.capital, 3) && !hasRepeatedLetter(it.capital, 4)
            }

            is QuizCategory.RepeatedLetter4 -> allCountries.filter {
                hasRepeatedLetter(it.capital, 4)
            }

            is QuizCategory.StartsEndsSame -> allCountries.filter {
                it.capital.first().uppercaseChar() == it.capital.last().uppercaseChar()
            }

            is QuizCategory.AllVowelsPresent -> allCountries.filter {
                val lower = it.capital.lowercase()
                VOWELS.all { v -> v in lower }
            }

            is QuizCategory.IslandCountries -> allCountries.filter {
                it.capital.contains("island", ignoreCase = true)
            }

            else -> emptyList()
        }
    }

    companion object {
        private val DOUBLE_LETTER_REGEX = Regex("(.)\\1", RegexOption.IGNORE_CASE)
        private val CONSONANT_CLUSTER_REGEX = Regex("[bcdfghjklmnpqrstvwxyz]{3,}", RegexOption.IGNORE_CASE)
        private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')

        private fun hasRepeatedLetter(name: String, minCount: Int): Boolean {
            val lower = name.lowercase()
            return lower.groupBy { it }
                .any { (ch, occurrences) -> ch.isLetter() && occurrences.size >= minCount }
        }
    }
}
