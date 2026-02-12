package com.geoquiz.app.domain.usecase

import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetCountriesForFlagQuizUseCase @Inject constructor(
    private val repository: CountryRepository,
    private val flagColorDao: FlagColorDao
) {

    suspend operator fun invoke(category: QuizCategory): List<Country> {
        repository.ensureSeeded()
        val allCountries = repository.getAllCountries().first()
        val countryByCode = allCountries.associateBy { it.code }

        return when (category) {
            is QuizCategory.FlagSingleColor -> {
                val codes = flagColorDao.getCountryCodesForColor(category.color).toSet()
                allCountries.filter { it.code in codes }
            }

            is QuizCategory.FlagColorCombo -> {
                // Countries that have ALL the specified colors
                val codeSets = category.colors.map { color ->
                    flagColorDao.getCountryCodesForColor(color).toSet()
                }
                val intersection = codeSets.reduceOrNull { acc, set -> acc.intersect(set) } ?: emptySet()
                allCountries.filter { it.code in intersection }
            }

            is QuizCategory.FlagColorCount -> {
                allCountries.filter { country ->
                    val count = flagColorDao.getColorCountForCountry(country.code)
                    count == category.count
                }
            }

            else -> emptyList()
        }
    }
}
