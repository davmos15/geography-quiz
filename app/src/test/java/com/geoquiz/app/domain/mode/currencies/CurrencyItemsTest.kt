package com.geoquiz.app.domain.mode.currencies

import com.geoquiz.app.data.currencies.AssetCurrencyRepository
import com.geoquiz.app.data.currencies.CurrencyAssetSource
import com.geoquiz.app.testutil.TestQuizData.country
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CurrencyItems.buildAll]/[build] against both the real bundled `currencies.json` (D48's named
 * edge cases - Panama, Zimbabwe, Palestine, Sierra Leone - plus the three "orphan" codes this
 * class's KDoc extends D48's reasoning to: `CUC`/`KID`/`TVD`) and small synthetic data (the
 * general shape of the representative-assignment algorithm, including the four real
 * Bhutan/Brunei/Bahamas/Lesotho collisions it must resolve).
 */
class CurrencyItemsTest {

    /** Reads the real committed asset from disk, like `AssetBorderGraphRepositoryTest`'s fake. */
    private class FakeAssetSource : CurrencyAssetSource {
        override fun read(path: String): ByteArray = File("src/main/assets/$path").readBytes()
    }

    private fun realData() = runBlocking {
        AssetCurrencyRepository(FakeAssetSource(), Dispatchers.Unconfined).currencies()
    }

    /** A synthetic [Country] keyed only by [code] (name/region are never read by [CurrencyItems]). */
    private fun synthetic(code: String) = country(code, code, code)

    // --- Real bundled data: D48 and this class's extension of it -------------------------------

    @Test
    fun `real data has exactly 124 items, each with a unique representative code`() {
        // 143 before task P10-B's CurrencySymbolRules.EXCLUDED_CODES (19 codes whose symbol
        // itself spells out the country, e.g. AED's Arabic abbreviation) dropped 19 representative
        // items (18 single-owner currencies plus the shared JOD): 143 - 19 = 124.
        val data = realData()
        val allCountries = (data.primaryCurrency.keys).associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        assertEquals(124, items.size)
        assertEquals(items.size, items.map { it.country.code }.distinct().size)
    }

    @Test
    fun `Panama's typed target is the balboa (PAB), not USD (D48)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        val panama = items.find { it.country.code == "PAN" }
        assertEquals("PAB", panama?.entry?.code)
        assertFalse(panama?.entry?.shared ?: true)
    }

    @Test
    fun `Zimbabwe never becomes a representative - it owns no non-shared code and is not alphabetically first for USD (D48)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        assertTrue(items.none { it.country.code == "ZWE" })
        // USD's representative skips every country that already owns its own non-shared currency.
        val usd = items.find { it.entry.code == "USD" }
        assertEquals("ECU", usd?.country?.code)
    }

    @Test
    fun `Palestine (PSE) is never a representative, so never a typed target (D48)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        assertTrue(items.none { it.country.code == "PSE" })
        assertNull(data.primaryCurrency["PSE"])
        // But it is still a true member of the shared currencies it actually uses.
        val egp = data.entries.first { it.code == "EGP" }
        assertTrue("PSE" in egp.countries)
    }

    @Test
    fun `Sierra Leone's currency is SLL, since mledoze has no SLE entry (D48)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        val sierraLeone = items.find { it.country.code == "SLE" }
        assertEquals("SLL", sierraLeone?.entry?.code)
    }

    @Test
    fun `CUC, KID and TVD have no representative - real codes that are nobody's actual primary currency`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        val askedCodes = items.map { it.entry.code }.toSet()
        assertFalse("CUC" in askedCodes)
        assertFalse("KID" in askedCodes)
        assertFalse("TVD" in askedCodes)
        // Cuba, Kiribati and Tuvalu are still asked about, just via their real primary currency.
        assertTrue(items.any { it.country.code == "CUB" && it.entry.code == "CUP" })
    }

    @Test
    fun `a shared currency's representative is always a true member of its own countries list`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        items.filter { it.entry.shared }.forEach { item ->
            assertTrue(
                "${item.entry.code}'s representative ${item.country.code} must be a true member",
                item.country.code in item.entry.countries
            )
        }
    }

    @Test
    fun `Bhutan, Brunei, The Bahamas and Lesotho each keep their own currency, not their shared one`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        assertEquals("BTN", items.find { it.country.code == "BTN" }?.entry?.code)
        assertEquals("BND", items.find { it.country.code == "BRN" }?.entry?.code)
        assertEquals("BSD", items.find { it.country.code == "BHS" }?.entry?.code)
        assertEquals("LSL", items.find { it.country.code == "LSO" }?.entry?.code)
        // The shared currencies they'd otherwise have represented fall through to another member.
        assertEquals("IND", items.find { it.entry.code == "INR" }?.country?.code)
        assertEquals("SGP", items.find { it.entry.code == "SGD" }?.country?.code)
    }

    // --- General algorithm shape, with small synthetic data -------------------------------------

    @Test
    fun `build filters buildAll down to the given candidate codes`() {
        val entryA = CurrencyEntry("AAA", "A dollar", "$", listOf("ONE"), shared = false, fact = null)
        val entryB = CurrencyEntry("BBB", "B dollar", "$", listOf("TWO"), shared = false, fact = null)
        val data = CurrencyData(listOf(entryA, entryB), mapOf("ONE" to "AAA", "TWO" to "BBB"))
        val byCode = mapOf("ONE" to synthetic("ONE"), "TWO" to synthetic("TWO"))

        val filtered = CurrencyItems.build(data, byCode, candidateCodes = setOf("ONE"))

        assertEquals(listOf("ONE"), filtered.map { it.country.code })
    }

    // --- Per-round representative scoping (task 7.1 review item 1) -----------------------------

    @Test
    fun `a shared entry's representative is re-chosen within the candidate set, not fixed globally`() {
        // THREE's own currency owns nothing; ONE and TWO both use the shared code.
        val shared = CurrencyEntry("SSS", "S", "s", listOf("ONE", "TWO"), shared = true, fact = null)
        val data = CurrencyData(listOf(shared), primaryCurrency = emptyMap())
        val byCode = mapOf("ONE" to synthetic("ONE"), "TWO" to synthetic("TWO"))

        // Globally (both candidates in scope), ONE is the alphabetically-first representative.
        assertEquals("ONE", CurrencyItems.build(data, byCode, setOf("ONE", "TWO")).single().country.code)
        // Restricted to only TWO, the representative becomes TWO - not dropped, and not ONE.
        assertEquals("TWO", CurrencyItems.build(data, byCode, setOf("TWO")).single().country.code)
    }

    @Test
    fun `a filter that excludes every shared currency's old global representative is no longer empty (review item 1)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        // Micronesia (review's named worst case): FSM/MHL/PLW use USD (old global rep: ECU, not
        // Micronesian), KIR/NRU use AUD. With the old global-representative scheme this filter
        // produced zero items at all, despite advertising 5 real countries.
        val micronesia = setOf("FSM", "KIR", "MHL", "NRU", "PLW")

        val items = CurrencyItems.build(data, allCountries, micronesia)

        assertTrue("Micronesia must never produce an empty round", items.isNotEmpty())
        val usd = items.find { it.entry.code == "USD" }?.country?.code
        assertTrue("FSM/MHL/PLW use USD", usd != null && usd in setOf("FSM", "MHL", "PLW"))
        val aud = items.find { it.entry.code == "AUD" }?.country?.code
        assertTrue("KIR/NRU use AUD - this is also how Kiribati/Tuvalu stay askable (review item 6)", aud != null && aud in setOf("KIR", "NRU"))
    }

    @Test
    fun `North America gets USD when USA is a candidate, not the globally-chosen Ecuador (review item 1)`() {
        val data = realData()
        val allCountries = data.primaryCurrency.keys.associateWith { synthetic(it) }
        val northAmerica = setOf("USA", "CAN", "MEX")

        val items = CurrencyItems.build(data, allCountries, northAmerica)

        val usd = items.find { it.entry.code == "USD" }
        assertEquals("USA", usd?.country?.code)
    }

    @Test
    fun `a region with real countries but zero askable currencies produces no items, not a crash`() {
        // A country whose primary currency code has no entry in this data at all (an orphan, like
        // the real KID/TVD) and is not a member of any shared entry either.
        val data = CurrencyData(entries = emptyList(), primaryCurrency = mapOf("ONE" to "NOPE"))
        val byCode = mapOf("ONE" to synthetic("ONE"))

        val items = CurrencyItems.build(data, byCode, candidateCodes = setOf("ONE"))

        assertTrue(items.isEmpty())
    }

    @Test
    fun `a shared entry with every in-scope member already taken is dropped, never duplicates a representative (review item 7)`() {
        // Every member of this shared code already owns its own non-shared currency.
        val ownX = CurrencyEntry("XXX", "X", "x", listOf("ONE"), shared = false, fact = null)
        val ownY = CurrencyEntry("YYY", "Y", "y", listOf("TWO"), shared = false, fact = null)
        val shared = CurrencyEntry("ZZZ", "Z", "z", listOf("TWO", "ONE"), shared = true, fact = null)
        val data = CurrencyData(
            listOf(ownX, ownY, shared),
            mapOf("ONE" to "XXX", "TWO" to "YYY")
        )
        val byCode = mapOf("ONE" to synthetic("ONE"), "TWO" to synthetic("TWO"))

        val items = CurrencyItems.buildAll(data, byCode)

        // Tightened from a previous version that reused "ONE" as a duplicate representative
        // (task 7.1 review item 7): a duplicate would make two items collide under one cca3 code,
        // so the shared entry is dropped entirely instead. XXX and YYY still get their own items.
        assertTrue(items.none { it.entry.code == "ZZZ" })
        assertEquals(setOf("XXX", "YYY"), items.map { it.entry.code }.toSet())
        assertEquals(items.size, items.map { it.country.code }.distinct().size)
    }

    @Test
    fun `a non-shared code with no country naming it as primary has no representative at all`() {
        val orphan = CurrencyEntry("OOO", "Orphan", "o", listOf("ONE"), shared = false, fact = null)
        // ONE's primary currency is a different code entirely.
        val data = CurrencyData(listOf(orphan), mapOf("ONE" to "OTHER"))
        val byCode = mapOf("ONE" to synthetic("ONE"))

        val items = CurrencyItems.buildAll(data, byCode)

        assertTrue(items.isEmpty())
    }

    @Test
    fun `a representative missing from allCountriesByCode is skipped rather than crashing`() {
        val entry = CurrencyEntry("AAA", "A", "a", listOf("GONE"), shared = false, fact = null)
        val data = CurrencyData(listOf(entry), mapOf("GONE" to "AAA"))

        val items = CurrencyItems.buildAll(data, emptyMap())

        assertTrue(items.isEmpty())
    }
}
