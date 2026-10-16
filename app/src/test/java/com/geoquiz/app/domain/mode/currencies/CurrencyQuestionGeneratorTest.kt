package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyQuestionGeneratorTest {

    private val pool = mockk<QuestionGenerator>()
    private val countryRepository = mockk<CountryRepository>()
    private val currencyRepository = mockk<CurrencyRepository>()

    private val france = country("FRA", "France", "Paris", region = "Europe")
    private val germany = country("DEU", "Germany", "Berlin", region = "Europe")
    private val peru = country("PER", "Peru", "Lima", region = "Americas")

    private val eur = CurrencyEntry("EUR", "Euro", "€", listOf("DEU", "FRA"), shared = true, fact = "Introduced in 1999.")
    private val pen = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("PER"), shared = false, fact = null)

    private fun generator(allCountries: List<com.geoquiz.app.domain.model.Country> = listOf(france, germany, peru)) =
        CurrencyQuestionGenerator(pool, countryRepository, currencyRepository).also {
            every { countryRepository.getAllCountries() } returns flowOf(allCountries)
        }

    @Test
    fun `items returns the representative country for every eligible currency, in the pool's order`() = runBlocking {
        coEvery { pool.items(QuizCategory.AllCountries) } returns listOf(peru, germany, france)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().items(QuizCategory.AllCountries)

        // EUR's representative is DEU (alphabetically first of DEU/FRA); order follows the pool:
        // PER (PEN) first, then DEU (EUR).
        assertEquals(listOf("PER", "DEU"), items.map { it.code })
    }

    @Test
    fun `currencyItems re-picks a shared currency's representative from the category's own pool (review item 1)`() = runBlocking {
        // Only France is offered by this category; Germany (the full-game alphabetical
        // representative) is filtered out. Before the review fix this dropped the euro
        // entirely - now France itself becomes the representative instead.
        coEvery { pool.items(QuizCategory.ByRegion("Europe")) } returns listOf(france)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().currencyItems(QuizCategory.ByRegion("Europe"))

        assertEquals(listOf("FRA"), items.map { it.country.code })
        assertEquals(listOf("EUR"), items.map { it.entry.code })
    }

    @Test
    fun `a category offering no member of a shared currency still has no item for it`() = runBlocking {
        // Peru alone: no EUR member is offered at all, so the euro has no representative here.
        coEvery { pool.items(QuizCategory.ByRegion("Americas")) } returns listOf(peru)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().currencyItems(QuizCategory.ByRegion("Americas"))

        assertEquals(listOf("PEN"), items.map { it.entry.code })
    }

    @Test
    fun `itemsForCurrencyCodes rebuilds the exact items for stored currency codes, scoped to their category`() = runBlocking {
        coEvery { pool.items(QuizCategory.AllCountries) } returns listOf(peru, germany, france)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().itemsForCurrencyCodes(QuizCategory.AllCountries, listOf("PEN", "EUR"))

        assertEquals(listOf("PEN", "EUR"), items.map { it.entry.code })
        // EUR's representative within the full {DEU, FRA} pool is DEU (alphabetically first) -
        // the exact same pick `currencyItems` itself would make for this category.
        assertEquals(listOf("PER", "DEU"), items.map { it.country.code })
    }

    @Test
    fun `itemsForCurrencyCodes skips a currency code with no matching item rather than failing`() = runBlocking {
        coEvery { pool.items(QuizCategory.AllCountries) } returns listOf(peru)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(pen),
            primaryCurrency = mapOf("PER" to "PEN")
        )

        val items = generator().itemsForCurrencyCodes(QuizCategory.AllCountries, listOf("PEN", "UNKNOWN"))

        assertEquals(listOf("PER"), items.map { it.country.code })
    }
}
