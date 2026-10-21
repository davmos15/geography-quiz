package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
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
 * directly - except that each *symbol* is capped to one question per round (task P10-B, Dav
 * 2026-10-20): since the clue now shows only the symbol, two different currency codes with the
 * same symbol (e.g. the Australian dollar and the US dollar, both "$") would otherwise show the
 * identical clue twice in one round. [dedupBySymbol] keeps one random item per symbol - using
 * [quizRandom], never [pool]'s order (code review round, task P10-R1: "$" alone covers 29
 * currencies in the real data, and always keeping the alphabetically-first of them meant 28 of
 * the 29 could never be asked) - and drops the rest; a currency with a unique symbol is never
 * affected. Not an `@Inject`-constructed class itself (same reasoning as
 * `SilhouettesQuestionGenerator`): [CurrencyGameMode] builds it with the concrete
 * [com.geoquiz.app.domain.mode.classic.CountriesQuestionGenerator] Hilt gives it.
 */
class CurrencyQuestionGenerator(
    private val pool: QuestionGenerator,
    private val countryRepository: CountryRepository,
    private val currencyRepository: CurrencyRepository,
    private val quizRandom: QuizRandom = QuizRandom()
) : QuestionGenerator {

    override suspend fun items(category: QuizCategory): List<Country> = currencyItems(category).map { it.country }

    /**
     * The full items (country + currency entry) this category asks about, in [pool]'s order,
     * with [dedupBySymbol]'s one-question-per-symbol cap already applied.
     */
    suspend fun currencyItems(category: QuizCategory): List<CurrencyItem> {
        val sorted = sortedItems(category)
        // A practice set re-asks exactly what was missed, even if two misses share a symbol.
        return if (category is QuizCategory.Practice) sorted else dedupBySymbol(sorted)
    }

    /**
     * Every eligible item for [category], in [pool]'s order, with no symbol dedup applied -
     * shared by [currencyItems] (before its dedup step) and [itemsForCurrencyCodes] (which must
     * be able to find a saved code even if a *different* item would now win that symbol's random
     * pick, task P10-R1).
     */
    private suspend fun sortedItems(category: QuizCategory): List<CurrencyItem> {
        val candidates = pool.items(category)
        val candidateCodes = candidates.mapTo(mutableSetOf()) { it.code }
        val allCountriesByCode = countryRepository.getAllCountries().first().associateBy { it.code }
        val data = currencyRepository.currencies()
        val items = CurrencyItems.build(data, allCountriesByCode, candidateCodes)
        val order = candidates.withIndex().associate { (index, country) -> country.code to index }
        return items.sortedBy { order[it.country.code] ?: Int.MAX_VALUE }
    }

    /**
     * Keeps one random item per [CurrencyEntry.symbol] in [items] (task P10-R1), chosen with
     * [quizRandom] rather than always the pool's first - otherwise the alphabetically/pool-first
     * currency of a widely-shared symbol like "$" would be the only one ever asked. The chosen
     * items are returned in [items]'s original relative order (not grouped), so the round still
     * reads like the rest of the pool.
     */
    private fun dedupBySymbol(items: List<CurrencyItem>): List<CurrencyItem> {
        val chosenPerSymbol = items.groupBy { it.entry.symbol }
            .mapValues { (_, group) -> group[quizRandom.random.nextInt(group.size)] }
        val chosen = chosenPerSymbol.values.toSet()
        return items.filter { it in chosen }
    }

    /**
     * Rebuilds the exact items for [category] from the *un-deduped* pool ([sortedItems], task
     * P10-R1), then picks out [currencyCodes] (each a [CurrencyEntry.code], in that order) - used
     * to restore a saved round (`ui/currencies/CurrenciesSavedState.kt` stores currency codes,
     * never a representative country, since [CurrencyItems]'s representative assignment is scoped
     * to [category]: looking codes up against the category's own items reproduces the exact same
     * representatives the original round had, rather than a different global assignment that
     * might not even include one of them (the task 7.1 review's main finding). Going through
     * [sortedItems] rather than [currencyItems] matters since [dedupBySymbol] now picks randomly:
     * a saved code that is not this restore's random pick for its symbol must still be found, or
     * the saved round would lose an item it never actually dropped.
     */
    suspend fun itemsForCurrencyCodes(category: QuizCategory, currencyCodes: List<String>): List<CurrencyItem> {
        val byCurrencyCode = sortedItems(category).associateBy { it.entry.code }
        return currencyCodes.mapNotNull { byCurrencyCode[it] }
    }
}
