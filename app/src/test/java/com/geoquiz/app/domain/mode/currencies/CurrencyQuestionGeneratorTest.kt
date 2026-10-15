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
    fun `currencyItems restricts representatives to the category's filtered pool`() = runBlocking {
        // Only France is offered by this category; Germany (EUR's alphabetical representative)
        // is filtered out, so the euro is not asked about at all here.
        coEvery { pool.items(QuizCategory.ByRegion("Europe")) } returns listOf(france)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().currencyItems(QuizCategory.ByRegion("Europe"))

        assertEquals(emptyList<CurrencyItem>(), items)
    }

    @Test
    fun `itemsForCodes rebuilds the exact items for stored codes, regardless of category`() = runBlocking {
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().itemsForCodes(listOf("PER", "DEU"))

        assertEquals(listOf("PEN", "EUR"), items.map { it.entry.code })
        assertEquals(listOf("PER", "DEU"), items.map { it.country.code })
    }

    @Test
    fun `itemsForCodes skips a code with no matching item rather than failing`() = runBlocking {
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(pen),
            primaryCurrency = mapOf("PER" to "PEN")
        )

        val items = generator().itemsForCodes(listOf("PER", "UNKNOWN"))

        assertEquals(listOf("PER"), items.map { it.country.code })
    }
}
