package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.model.Country
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import kotlinx.coroutines.flow.first

/**
 * Currencies' item pool (task 7.1): wraps [pool] (every country matching the category filter -
 * [com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator] in production, unchanged
 * constructor shape for test fixtures, same pattern as
 * `domain/mode/silhouettes/SilhouettesQuestionGenerator.kt`) and [currencyRepository]'s bundled
 * data, turned into one [CurrencyItem] per eligible currency code via [CurrencyItems].
 *
 * Every item the filter allows is asked, in [pool]'s order - unlike Silhouettes' 15-item random
 * round, this mode behaves like the classic quizzes, just over currencies instead of countries
 * directly. Not an `@Inject`-constructed class itself (same reasoning as
 * `SilhouettesQuestionGenerator`): [CurrencyGameMode] builds it with the concrete
 * [com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator] Hilt gives it.
 */
class CurrencyQuestionGenerator(
    private val pool: QuestionGenerator,
    private val countryRepository: CountryRepository,
    private val currencyRepository: CurrencyRepository
) : QuestionGenerator {

    override suspend fun items(category: QuizCategory): List<Country> = currencyItems(category).map { it.country }

    /** The full items (country + currency entry) this category asks about, in [pool]'s order. */
    suspend fun currencyItems(category: QuizCategory): List<CurrencyItem> {
        val candidates = pool.items(category)
        val candidateCodes = candidates.mapTo(mutableSetOf()) { it.code }
        val allCountriesByCode = countryRepository.getAllCountries().first().associateBy { it.code }
        val data = currencyRepository.currencies()
        val items = CurrencyItems.build(data, allCountriesByCode, candidateCodes)
        val order = candidates.withIndex().associate { (index, country) -> country.code to index }
        return items.sortedBy { order[it.country.code] ?: Int.MAX_VALUE }
    }

    /**
     * Rebuilds the exact items for [category], then picks out [currencyCodes] (each a
     * [CurrencyEntry.code], in that order) - used to restore a saved round
     * (`ui/currencies/CurrenciesSavedState.kt` stores currency codes, never a representative
     * country, since [CurrencyItems]'s representative assignment is scoped to [category]: looking
     * codes up against [currencyItems] for the *same* category reproduces the exact same
     * representatives the original round had, rather than a different global assignment that
     * might not even include one of them (the task 7.1 review's main finding).
     */
    suspend fun itemsForCurrencyCodes(category: QuizCategory, currencyCodes: List<String>): List<CurrencyItem> {
        val byCurrencyCode = currencyItems(category).associateBy { it.entry.code }
        return currencyCodes.mapNotNull { byCurrencyCode[it] }
    }
}
