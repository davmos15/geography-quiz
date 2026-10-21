package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.QuestionGenerator
import com.geoquiz.app.domain.mode.QuizRandom
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.repository.CountryRepository
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun generator(
        allCountries: List<com.geoquiz.app.domain.model.Country> = listOf(france, germany, peru),
        quizRandom: QuizRandom = QuizRandom()
    ) =
        CurrencyQuestionGenerator(pool, countryRepository, currencyRepository, quizRandom).also {
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

    // --- One question per symbol per round (task P10-B) -----------------------------------------

    @Test
    fun `currencyItems asks about each symbol at most once per round`() = runBlocking {
        // AUD and USD both use "$" in the real data; a round with both candidates must only ask
        // about the symbol once. Which one is a random pick (task P10-R1).
        val australia = country("AUS", "Australia", "Canberra", region = "Oceania")
        val usa = country("USA", "United States", "Washington", region = "Americas")
        val aud = CurrencyEntry("AUD", "Australian dollar", "$", listOf("AUS"), shared = false, fact = null)
        val usd = CurrencyEntry("USD", "US dollar", "$", listOf("USA"), shared = false, fact = null)
        coEvery { pool.items(QuizCategory.AllCountries) } returns listOf(australia, usa)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(aud, usd),
            primaryCurrency = mapOf("AUS" to "AUD", "USA" to "USD")
        )

        val items = generator(listOf(australia, usa)).currencyItems(QuizCategory.AllCountries)

        assertEquals(1, items.size)
        assertTrue(items.single().entry.code in setOf("AUD", "USD"))
    }

    @Test
    fun `a practice set re-asks every missed currency even when two share a symbol`() = runBlocking {
        val australia = country("AUS", "Australia", "Canberra", region = "Oceania")
        val usa = country("USA", "United States", "Washington", region = "Americas")
        val aud = CurrencyEntry("AUD", "Australian dollar", "$", listOf("AUS"), shared = false, fact = null)
        val usd = CurrencyEntry("USD", "US dollar", "$", listOf("USA"), shared = false, fact = null)
        val practice = QuizCategory.Practice(listOf("AUS", "USA"))
        coEvery { pool.items(practice) } returns listOf(australia, usa)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(aud, usd),
            primaryCurrency = mapOf("AUS" to "AUD", "USA" to "USD")
        )

        val items = generator(listOf(australia, usa)).currencyItems(practice)

        assertEquals(listOf("AUD", "USD"), items.map { it.entry.code })
    }

    @Test
    fun `currencyItems never drops a currency with a unique symbol`() = runBlocking {
        coEvery { pool.items(QuizCategory.AllCountries) } returns listOf(peru, germany, france)
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(eur, pen),
            primaryCurrency = mapOf("DEU" to "EUR", "FRA" to "EUR", "PER" to "PEN")
        )

        val items = generator().currencyItems(QuizCategory.AllCountries)

        assertEquals(setOf("PEN", "EUR"), items.map { it.entry.code }.toSet())
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

    // --- Random (not always-first) symbol dedup pick (task P10-R1) -----------------------------

    @Test
    fun `currencyItems picks different dollar currencies for different seeds, not always the pool's first`() = runBlocking {
        val australia = country("AUS", "Australia", "Canberra", region = "Oceania")
        val usa = country("USA", "United States", "Washington", region = "Americas")
        val canada = country("CAN", "Canada", "Ottawa", region = "Americas")
        val aud = CurrencyEntry("AUD", "Australian dollar", "$", listOf("AUS"), shared = false, fact = null)
        val usd = CurrencyEntry("USD", "US dollar", "$", listOf("USA"), shared = false, fact = null)
        val cad = CurrencyEntry("CAD", "Canadian dollar", "$", listOf("CAN"), shared = false, fact = null)
        val allCountries = listOf(australia, usa, canada)
        coEvery { pool.items(QuizCategory.AllCountries) } returns allCountries
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(aud, usd, cad),
            primaryCurrency = mapOf("AUS" to "AUD", "USA" to "USD", "CAN" to "CAD")
        )

        val picks = (0 until 30).map { seed ->
            generator(allCountries, QuizRandom(Random(seed))).currencyItems(QuizCategory.AllCountries)
                .single().entry.code
        }.toSet()

        assertEquals(
            "across enough seeds, every \"$\" currency must be reachable, not always AUD (the pool's first)",
            setOf("AUD", "USD", "CAD"),
            picks
        )
    }

    @Test
    fun `itemsForCurrencyCodes restores a saved code that is not this round's random symbol pick`() = runBlocking {
        val australia = country("AUS", "Australia", "Canberra", region = "Oceania")
        val usa = country("USA", "United States", "Washington", region = "Americas")
        val aud = CurrencyEntry("AUD", "Australian dollar", "$", listOf("AUS"), shared = false, fact = null)
        val usd = CurrencyEntry("USD", "US dollar", "$", listOf("USA"), shared = false, fact = null)
        val allCountries = listOf(australia, usa)
        coEvery { pool.items(QuizCategory.AllCountries) } returns allCountries
        coEvery { currencyRepository.currencies() } returns CurrencyData(
            entries = listOf(aud, usd),
            primaryCurrency = mapOf("AUS" to "AUD", "USA" to "USD")
        )

        // A fresh generator (e.g. after process death) with different randomness than whatever
        // picked USD for the original round must still find it when restoring by code.
        val restored = generator(allCountries, QuizRandom(Random(12345)))
            .itemsForCurrencyCodes(QuizCategory.AllCountries, listOf("USD"))

        assertEquals(listOf("USD"), restored.map { it.entry.code })
        assertEquals(listOf("USA"), restored.map { it.country.code })
    }
}
