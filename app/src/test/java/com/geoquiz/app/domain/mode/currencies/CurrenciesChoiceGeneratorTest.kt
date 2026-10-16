package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.AssetCurrencyRepository
import com.geoquiz.app.data.currencies.CurrencyAssetSource
import com.geoquiz.app.data.currencies.CurrencyRepository
import com.geoquiz.app.domain.mode.CHOICE_OPTION_COUNT
import com.geoquiz.app.domain.mode.ChoicePrompt
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

    // --- Zimbabwe's basket (task 7.1 review item 3) --------------------------------------------

    @Test
    fun `Zimbabwe's basket currency never appears as a wrong option for ZAR`() {
        val southAfrica = country("ZAF", "South Africa", "Pretoria", region = "Africa")
        val namibia = country("NAM", "Namibia", "Windhoek", region = "Africa")
        val lesotho = country("LSO", "Lesotho", "Maseru", region = "Africa")
        val eswatini = country("SWZ", "Eswatini", "Mbabane", region = "Africa")
        val zimbabwe = country("ZWE", "Zimbabwe", "Harare", region = "Africa")
        val other = country("KEN", "Kenya", "Nairobi", region = "Africa")
        val all = listOf(southAfrica, namibia, lesotho, eswatini, zimbabwe, other)
        val byCode = all.associateBy { it.code }
        // ZAR, true members per data/currencies.json; Zimbabwe only listed in its unofficial basket.
        val zar = CurrencyEntry(
            "ZAR", "South African rand", "R", listOf("LSO", "NAM", "SWZ", "ZAF"),
            shared = true, fact = null, listedInBasketBy = listOf("ZWE")
        )
        val data = CurrencyData(entries = listOf(zar), primaryCurrency = emptyMap())
        // Build the round's own item set, exactly one ZAR representative, like a real round.
        val roundItems = CurrencyItems.build(data, byCode, candidateCodes = byCode.keys)
        val zarItem = roundItems.single { it.entry.code == "ZAR" }
        val quizSet = roundItems.map { it.country }

        val generator = CurrenciesChoiceGenerator(currencyRepository)
        every { currencyRepository.cachedOrNull() } returns data

        repeat(20) { seed ->
            val question = generator.question(
                target = zarItem.country,
                quizSet = quizSet,
                allCountries = all,
                category = QuizCategory.AllCountries,
                random = Random(seed)
            )
            assertFalse(
                "seed $seed: Zimbabwe must never appear as an option for ZAR (it genuinely also circulates it)",
                question.options.any { it.code == "ZWE" }
            )
        }
    }

    // --- Every real shared currency, many seeds (task 7.1 review item 5) -----------------------

    /** Reads the real committed asset from disk, like `CurrencyItemsTest`'s fake. */
    private class FakeAssetSource : CurrencyAssetSource {
        override fun read(path: String): ByteArray = File("src/main/assets/$path").readBytes()
    }

    private fun realData() = runBlocking {
        AssetCurrencyRepository(FakeAssetSource(), Dispatchers.Unconfined).currencies()
    }

    private fun synthetic(code: String) = country(code, code, code)

    @Test
    fun `every real shared currency's question has exactly one correct option and no basket country, across many seeds`() {
        val data = realData()
        val allCountriesByCode = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val allCountriesList = allCountriesByCode.values.toList()
        val items = CurrencyItems.buildAll(data, allCountriesByCode)
        val quizSet = items.map { it.country }
        val sharedItems = items.filter { it.entry.shared }
        // Sanity: every one of the 13 real shared codes still gets a representative at full scope.
        assertEquals(13, sharedItems.size)

        val repo = mockk<CurrencyRepository>()
        every { repo.cachedOrNull() } returns data
        val generator = CurrenciesChoiceGenerator(repo)

        for (seed in listOf(1, 2, 3, 42, 100)) {
            sharedItems.forEach { item ->
                val question = generator.question(
                    target = item.country,
                    quizSet = quizSet,
                    allCountries = allCountriesList,
                    category = QuizCategory.AllCountries,
                    random = Random(seed)
                )
                val memberCodes = item.entry.countries.toSet()
                val basketCodes = item.entry.listedInBasketBy.toSet()
                val correctOptions = question.options.filter { it.code in memberCodes }
                assertEquals(
                    "${item.entry.code}, seed $seed must have exactly one correct option",
                    listOf(item.country.code),
                    correctOptions.map { it.code }
                )
                question.options.forEach { option ->
                    assertFalse(
                        "${item.entry.code}, seed $seed: basket country ${option.code} must never be offered",
                        option.code in basketCodes
                    )
                }
            }
        }
    }
}
