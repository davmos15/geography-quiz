package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.AssetCurrencyRepository
import com.geoquiz.app.data.currencies.CurrencyAssetSource
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData.country
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Data-driven audit (task P10-B, Dav 2026-10-20) over the real bundled
 * `app/src/main/assets/currencies/currencies.json`: generates every possible Currencies
 * item/question from the real data and checks nothing shown before an answer is resolved can
 * leak the target country - the exact bug reported ("Afghan afghani (؋)" names Afghanistan
 * outright).
 *
 * "Before answering" text in this mode is always exactly one of:
 * - [CurrencyEntry.symbol] (the clue card's big symbol and its TalkBack description).
 * - the symbol, interpolated into [R.string.currencies_prompt_which_country]/
 *   [R.string.currencies_choice_prompt_symbol] (the prompt sentence).
 * - each [com.geoquiz.app.domain.mode.ChoiceOption.label] (a plain country name - never the
 *   currency's, by construction: [CurrenciesChoiceGenerator] always labels options with
 *   [com.geoquiz.app.domain.model.Country.name]).
 *
 * So auditing "no name, no code, and the symbol itself does not spell out the country" at the
 * data level - every surviving [CurrencyEntry.symbol] against every country that uses it -
 * covers every surface this mode can show before an answer, without needing a Compose test.
 */
class CurrencySymbolAuditTest {

    private class FakeAssetSource : CurrencyAssetSource {
        override fun read(path: String): ByteArray = File("src/main/assets/$path").readBytes()
    }

    private fun realData() = runBlocking {
        AssetCurrencyRepository(FakeAssetSource(), Dispatchers.Unconfined).currencies()
    }

    private fun synthetic(code: String) = country(code, code, code)

    /** cca2/common name/official name for every real cca3, from the same raw mledoze source
     * [com.geoquiz.app.domain.mode.borderhop.BorderGraphTest.realCountryRegions] uses - the app's
     * own [com.geoquiz.app.domain.model.Country] has no cca2 field, so this is the only source. */
    private data class RawCountry(val cca2: String, val commonName: String, val officialName: String)

    private fun realCountriesByCca3(): Map<String, RawCountry> {
        val file = File("../data/source/countries.json")
        return Json.parseToJsonElement(file.readText()).jsonArray.map { it.jsonObject }
            .associate { o ->
                val name = o.getValue("name").jsonObject
                o.getValue("cca3").jsonPrimitive.content to RawCountry(
                    cca2 = o.getValue("cca2").jsonPrimitive.content,
                    commonName = name.getValue("common").jsonPrimitive.content,
                    officialName = name.getValue("official").jsonPrimitive.content
                )
            }
    }

    private fun lettersOnly(value: String): String = value.filter { it.isLetter() }.lowercase()

    // --- The excluded list itself never appears -------------------------------------------------

    @Test
    fun `every CurrencySymbolRules excluded code is absent from the real bundled data's surviving entries`() {
        val data = realData()
        val codes = data.entries.map { it.code }.toSet()

        CurrencySymbolRules.EXCLUDED_CODES.forEach { excluded ->
            assertFalse("$excluded must never survive into the mode's data", excluded in codes)
        }
    }

    @Test
    fun `no generated item (typed or choice target) is ever an excluded code`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        items.forEach { item ->
            assertFalse(
                "${item.entry.code} must never be asked about (CurrencySymbolRules.EXCLUDED_CODES)",
                item.entry.code in CurrencySymbolRules.EXCLUDED_CODES
            )
        }
    }

    // --- The symbol itself never spells out the country ----------------------------------------

    @Test
    fun `no surviving symbol contains its country's cca2, cca3 or a significant fragment of its name`() {
        val data = realData()
        val rawByCca3 = realCountriesByCca3()
        val leaks = mutableListOf<String>()

        data.entries.forEach { entry ->
            val symbolLetters = lettersOnly(entry.symbol)
            if (symbolLetters.isEmpty()) return@forEach // e.g. Arabic-script symbols: no Latin letters at all.
            (entry.countries + entry.listedInBasketBy).forEach { cca3 ->
                val raw = rawByCca3[cca3] ?: return@forEach
                val cca2 = raw.cca2.lowercase()
                val cca3Lower = cca3.lowercase()
                val nameLetters = lettersOnly(raw.commonName) + lettersOnly(raw.officialName)
                when {
                    cca2 in symbolLetters ->
                        leaks += "${entry.code} ${entry.symbol} contains $cca3's cca2 ($cca2)"
                    cca3Lower in symbolLetters ->
                        leaks += "${entry.code} ${entry.symbol} contains $cca3's cca3"
                    // A short symbol (1-2 letters, e.g. "L", "Fr") coincidentally sits inside a
                    // long country name far too often to be a real leak (e.g. "Ar" in
                    // "Madagascar", "Fr" in "Central African Republic") - length >= 3 keeps this
                    // check meaningful without that noise; every symbol in the real data is
                    // either non-Latin, <= 3 letters with no real hit, or already excluded.
                    symbolLetters.length >= 3 && symbolLetters in nameLetters ->
                        leaks += "${entry.code} ${entry.symbol} sits inside $cca3's name (${raw.commonName})"
                }
            }
        }

        assertTrue("Symbol(s) that leak a country: $leaks", leaks.isEmpty())
    }

    // --- Every choice question has exactly one option that uses the asked symbol ---------------

    @Test
    fun `every real choice-only entry's question has exactly one option whose currency uses the symbol`() {
        val data = realData()
        val allCountriesByCode = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val allCountriesList = allCountriesByCode.values.toList()
        val items = CurrencyItems.buildAll(data, allCountriesByCode)
        val quizSet = items.map { it.country }
        val choiceOnlyItems = items.filter { it.entry.isChoiceOnly }
        // Sanity: real choice-only entries exist (shared, D50, plus ambiguous-symbol single-owner,
        // task P10-B) - this audit is meaningless if the fixture silently had none.
        assertTrue(choiceOnlyItems.isNotEmpty())

        val repo = AssetCurrencyRepository(FakeAssetSource(), Dispatchers.Unconfined)
        runBlocking { repo.currencies() } // warms cachedOrNull()
        val generator = CurrenciesChoiceGenerator(repo)

        choiceOnlyItems.forEach { item ->
            val question = generator.question(
                target = item.country,
                quizSet = quizSet,
                allCountries = allCountriesList,
                category = QuizCategory.AllCountries,
                random = Random(1)
            )
            val usingSymbol = question.options.filter {
                CurrencySymbolRules.countryUsesSymbol(it.code, item.entry.symbol, data.entries)
            }
            assertEquals(
                "${item.entry.code} (${item.entry.symbol}) must have exactly one option using the symbol",
                listOf(item.country.code),
                usingSymbol.map { it.code }
            )
        }
    }

    // --- Every typed entry's symbol is unique, with a single owner -----------------------------

    @Test
    fun `every real typed-eligible entry has a unique symbol and a single owning country`() {
        val data = realData()
        val typedEligible = data.entries.filterNot { it.isChoiceOnly }

        typedEligible.forEach { entry ->
            assertEquals(
                "${entry.code} (${entry.symbol}) must have exactly one owning country to be typed",
                1,
                entry.countries.size
            )
            val sameSymbol = data.entries.count { it.symbol == entry.symbol }
            assertEquals(
                "${entry.code} (${entry.symbol}) must be the only entry with this symbol to be typed",
                1,
                sameSymbol
            )
            val sameNormalizedSymbol = data.entries.count {
                CurrencySymbolRules.normalizedSymbol(it.symbol) == CurrencySymbolRules.normalizedSymbol(entry.symbol)
            }
            assertEquals(
                "${entry.code} (${entry.symbol}) must be the only entry with this normalised symbol to be typed",
                1,
                sameNormalizedSymbol
            )
        }
    }

    // --- Normalised symbol groups in the real bundled data (task P10-R1) -----------------------

    @Test
    fun `CHF's Fr period symbol is grouped with the plain Fr entries in the real data`() {
        val data = realData()
        val chf = data.entries.single { it.code == "CHF" }
        val sameGroup = data.entries
            .filter { CurrencySymbolRules.normalizedSymbol(it.symbol) == CurrencySymbolRules.normalizedSymbol(chf.symbol) }
            .map { it.code }
            .toSet()

        assertTrue(
            "CHF's normalised-symbol group must include every plain \"Fr\" currency: $sameGroup",
            sameGroup.containsAll(setOf("CHF", "BIF", "DJF", "GNF", "KMF", "RWF", "XAF", "XOF"))
        )
        // Switzerland/Liechtenstein must be excludable distractors on a plain "Fr" question.
        assertTrue(CurrencySymbolRules.countryUsesSymbol("CHE", "Fr", data.entries))
        assertTrue(CurrencySymbolRules.countryUsesSymbol("LIE", "Fr", data.entries))
    }

    @Test
    fun `BOB and VES are grouped via the explicit alias in the real data and both become choice-only`() {
        val data = realData()
        val bob = data.entries.single { it.code == "BOB" }
        val ves = data.entries.single { it.code == "VES" }

        assertEquals(
            CurrencySymbolRules.normalizedSymbol(bob.symbol),
            CurrencySymbolRules.normalizedSymbol(ves.symbol)
        )
        assertTrue(bob.isChoiceOnly)
        assertTrue(ves.isChoiceOnly)
    }

    @Test
    fun `every real entry with a non-empty listedInBasketBy is choice-only`() {
        val data = realData()
        val basketEntries = data.entries.filter { it.listedInBasketBy.isNotEmpty() }

        // Sanity: the fixture must have at least one non-shared basket entry for this check to
        // mean anything (BWP "P", Zimbabwe also uses it informally, D48).
        assertTrue(basketEntries.any { !it.shared })
        basketEntries.forEach { entry ->
            assertTrue("${entry.code} has a basket listing and must be choice-only", entry.isChoiceOnly)
        }
    }
}
