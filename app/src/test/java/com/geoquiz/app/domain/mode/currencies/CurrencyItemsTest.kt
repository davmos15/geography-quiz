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
    fun `real data has exactly 143 items, each with a unique representative code`() {
        val data = realData()
        val allCountries = (data.primaryCurrency.keys).associateWith { synthetic(it) }
        val items = CurrencyItems.buildAll(data, allCountries)

        assertEquals(143, items.size)
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

    @Test
    fun `a shared entry with no true member not already taken falls back to the alphabetically-first member`() {
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

        val zItem = items.find { it.entry.code == "ZZZ" }
        // Falls back to the alphabetically-first member even though it's already taken, rather
        // than disappearing outright; this never happens in the real data (checked above).
        assertEquals("ONE", zItem?.country?.code)
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
