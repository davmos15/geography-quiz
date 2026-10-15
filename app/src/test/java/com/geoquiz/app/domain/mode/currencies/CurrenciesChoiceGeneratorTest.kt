package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrenciesChoiceGeneratorTest {

    private val currencyRepository = mockk<CurrencyRepository>()

    private val france = country("FRA", "France", "Paris", region = "Europe")
    private val germany = country("DEU", "Germany", "Berlin", region = "Europe")
    private val andorra = country("AND", "Andorra", "Andorra la Vella", region = "Europe")
    private val peru = country("PER", "Peru", "Lima", region = "Americas")
    private val brazil = country("BRA", "Brazil", "Brasília", region = "Americas")
    private val japan = country("JPN", "Japan", "Tokyo", region = "Asia")
    private val allCountries = listOf(france, germany, andorra, peru, brazil, japan)

    private val eur = CurrencyEntry(
        "EUR", "Euro", "€", listOf("AND", "DEU", "FRA"), shared = true, fact = "Introduced in 1999."
    )
    private val pen = CurrencyEntry("PEN", "Peruvian sol", "S/", listOf("PER"), shared = false, fact = null)

    private fun generator(data: CurrencyData?) = CurrenciesChoiceGenerator(currencyRepository).also {
        every { currencyRepository.cachedOrNull() } returns data
    }

    @Test
    fun `a shared currency's question has exactly one correct option, a true member of its countries`() {
        val data = CurrencyData(entries = listOf(eur, pen), primaryCurrency = mapOf("PER" to "PEN"))

        val question = generator(data).question(
            target = andorra, // EUR's representative in this test's fixtures
            quizSet = allCountries,
            allCountries = allCountries,
            category = QuizCategory.AllCountries,
            random = Random(1)
        )

        assertEquals("AND", question.targetCode)
        assertEquals(CHOICE_OPTION_COUNT, question.options.size)
        val memberCodes = eur.countries.toSet()
        val correctOptions = question.options.filter { it.code in memberCodes }
        assertEquals(listOf("AND"), correctOptions.map { it.code })
        // Every other option is verifiably not a true member of the euro's countries.
        question.options.filter { it.code != "AND" }.forEach {
            assertFalse("${it.code} must not be a true EUR member", it.code in memberCodes)
        }
    }

    @Test
    fun `a shared currency's prompt uses the currency's name, symbol and code`() {
        val data = CurrencyData(entries = listOf(eur, pen), primaryCurrency = mapOf("PER" to "PEN"))

        val question = generator(data).question(
            target = andorra,
            quizSet = allCountries,
            allCountries = allCountries,
            category = QuizCategory.AllCountries,
            random = Random(1)
        )

        assertEquals(ChoicePrompt.CurrencyOf("the Euro (€, EUR)"), question.prompt)
    }

    @Test
    fun `a non-shared currency's question is a plain four-name question`() {
        val data = CurrencyData(entries = listOf(eur, pen), primaryCurrency = mapOf("PER" to "PEN"))

        val question = generator(data).question(
            target = peru,
            quizSet = allCountries,
            allCountries = allCountries,
            category = QuizCategory.AllCountries,
            random = Random(1)
        )

        assertEquals("PER", question.targetCode)
        assertEquals(ChoicePrompt.CurrencyOf("the Peruvian sol (S/, PEN)"), question.prompt)
        assertTrue(question.options.any { it.code == "PER" })
    }

    @Test
    fun `falls back to the target's own name when the cache is cold, rather than crashing`() {
        val question = generator(null).question(
            target = peru,
            quizSet = allCountries,
            allCountries = allCountries,
            category = QuizCategory.AllCountries,
            random = Random(1)
        )

        assertEquals(ChoicePrompt.CurrencyOf("Peru"), question.prompt)
        assertEquals("PER", question.targetCode)
    }

    @Test
    fun `currencyLabel formats name, symbol and code together`() {
        assertEquals("the Euro (€, EUR)", currencyLabel(eur))
    }
}
