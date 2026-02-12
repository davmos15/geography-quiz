package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCountriesForQuizUseCase @Inject constructor(
    private val repository: CountryRepository
) {

    suspend operator fun invoke(category: QuizCategory): List<Country> {
        repository.ensureSeeded()
        val allCountries = repository.getAllCountries().first()

        return when (category) {
            is QuizCategory.AllCountries -> allCountries

            is QuizCategory.StartingWithLetter -> allCountries.filter {
                it.name.first().uppercaseChar() == category.letter.uppercaseChar()
            }

            is QuizCategory.EndingWithLetter -> allCountries.filter {
                it.name.last().uppercaseChar() == category.letter.uppercaseChar()
            }

            is QuizCategory.ContainingLetter -> allCountries.filter {
                it.name.contains(category.letter, ignoreCase = true)
            }

            is QuizCategory.ByRegion -> allCountries.filter {
                it.region.equals(category.region, ignoreCase = true)
            }

            is QuizCategory.BySubregion -> allCountries.filter {
                it.subregion.equals(category.subregion, ignoreCase = true)
            }

            is QuizCategory.ByNameLengthRange -> allCountries.filter {
                it.name.length in category.min..category.max
            }

            is QuizCategory.DoubleLetter -> allCountries.filter { country ->
                DOUBLE_LETTER_REGEX.containsMatchIn(country.name)
            }

            is QuizCategory.OneWord -> allCountries.filter { country ->
                !country.name.contains(' ') && !country.name.contains('-')
            }

            is QuizCategory.MultiWord -> allCountries.filter { country ->
                country.name.contains(' ') || country.name.contains('-')
            }

            is QuizCategory.IslandCountries -> allCountries.filter { country ->
                country.name.contains("island", ignoreCase = true)
            }
        }
    }

    companion object {
        private val DOUBLE_LETTER_REGEX = Regex("(.)\\1", RegexOption.IGNORE_CASE)
    }
}
