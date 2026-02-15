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

        // Batch-fetch all flag color mappings once to avoid N+1 queries
        val allMappings = flagColorDao.getAllMappings()
        val colorsByCountry = allMappings.groupBy({ it.countryCca3 }, { it.color }).mapValues { it.value.toSet() }

        return when (category) {
            is QuizCategory.FlagSingleColor -> {
                allCountries.filter { category.color in (colorsByCountry[it.code] ?: emptySet()) }
            }

            is QuizCategory.FlagColorCombo -> {
                val targetColors = category.colors.toSet()
                allCountries.filter { colorsByCountry[it.code] == targetColors }
            }

            is QuizCategory.FlagColorCount -> {
                allCountries.filter { (colorsByCountry[it.code]?.size ?: 0) == category.count }
            }

            else -> emptyList()
        }
    }
}
