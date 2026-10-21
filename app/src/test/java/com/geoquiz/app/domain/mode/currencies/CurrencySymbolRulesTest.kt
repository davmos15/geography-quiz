package com.geoquiz.app.domain.mode.currencies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CurrencySymbolRules]'s own logic (task P10-B), with small synthetic fixtures. */
class CurrencySymbolRulesTest {

    private fun entry(
        code: String,
        symbol: String,
        countries: List<String>,
        shared: Boolean = false,
        basket: List<String> = emptyList()
    ) = CurrencyEntry(code, code, symbol, countries, shared, fact = null, listedInBasketBy = basket)

    @Test
    fun `classify drops every excluded code`() {
        val excludedEntry = entry("AED", "د.إ", listOf("ARE"))
        val keptEntry = entry("PEN", "S/", listOf("PER"))

        val result = CurrencySymbolRules.classify(listOf(excludedEntry, keptEntry))

        assertEquals(listOf("PEN"), result.map { it.code })
    }

    @Test
    fun `classify marks a symbol used by more than one surviving entry as ambiguous`() {
        val lek = entry("ALL", "L", listOf("ALB"))
        val lempira = entry("HNL", "L", listOf("HND"))
        val sol = entry("PEN", "S/", listOf("PER"))

        val result = CurrencySymbolRules.classify(listOf(lek, lempira, sol))

        assertTrue(result.first { it.code == "ALL" }.ambiguousSymbol)
        assertTrue(result.first { it.code == "HNL" }.ambiguousSymbol)
        assertFalse(result.first { it.code == "PEN" }.ambiguousSymbol)
    }

    @Test
    fun `classify ignores an excluded entry's symbol when deciding ambiguity`() {
        // MYR's "RM" is excluded; it must not make some other unrelated "RM"-symbol entry
        // ambiguous (there happens to be none in the real data, but the rule itself should not
        // count a dropped entry).
        val myr = entry("MYR", "RM", listOf("MYS"))
        val onlyOther = entry("XYZ", "RM", listOf("ONE"))

        val result = CurrencySymbolRules.classify(listOf(myr, onlyOther))

        // MYR itself is gone; XYZ is now the *only* surviving "RM" entry, so not ambiguous.
        assertEquals(listOf("XYZ"), result.map { it.code })
        assertFalse(result.single().ambiguousSymbol)
    }

    @Test
    fun `isChoiceOnly is true for a shared currency even with a unique symbol`() {
        val entry = entry("EUR", "€", listOf("FRA", "DEU"), shared = true)
        val classified = CurrencySymbolRules.classify(listOf(entry)).single()

        assertTrue(classified.isChoiceOnly)
    }

    @Test
    fun `isChoiceOnly is true for a single-owner currency with an ambiguous symbol`() {
        val lek = entry("ALL", "L", listOf("ALB"))
        val lempira = entry("HNL", "L", listOf("HND"))

        val classified = CurrencySymbolRules.classify(listOf(lek, lempira)).first { it.code == "ALL" }

        assertFalse(classified.shared)
        assertTrue(classified.isChoiceOnly)
    }

    @Test
    fun `isChoiceOnly is false for a unique-symbol single-owner currency`() {
        val sol = entry("PEN", "S/", listOf("PER"))
        val classified = CurrencySymbolRules.classify(listOf(sol)).single()

        assertFalse(classified.isChoiceOnly)
    }

    @Test
    fun `countryUsesSymbol is true for an official member`() {
        val eur = entry("EUR", "€", listOf("FRA", "DEU"))

        assertTrue(CurrencySymbolRules.countryUsesSymbol("FRA", "€", listOf(eur)))
    }

    @Test
    fun `countryUsesSymbol is true for a basket-only listing (D48)`() {
        val zar = entry("ZAR", "R", listOf("ZAF"), basket = listOf("ZWE"))

        assertTrue(CurrencySymbolRules.countryUsesSymbol("ZWE", "R", listOf(zar)))
    }

    @Test
    fun `countryUsesSymbol is true across different currencies sharing the same symbol`() {
        val usd = entry("USD", "$", listOf("USA"))
        val aud = entry("AUD", "$", listOf("AUS"))

        assertTrue(CurrencySymbolRules.countryUsesSymbol("AUS", "$", listOf(usd, aud)))
    }

    @Test
    fun `countryUsesSymbol is false when the country's currency uses a different symbol`() {
        val usd = entry("USD", "$", listOf("USA"))
        val eur = entry("EUR", "€", listOf("FRA"))

        assertFalse(CurrencySymbolRules.countryUsesSymbol("FRA", "$", listOf(usd, eur)))
    }

    // --- Normalised symbol grouping (task P10-R1) -----------------------------------------------

    @Test
    fun `normalizedSymbol trims, drops a trailing period and folds case`() {
        assertEquals("fr", CurrencySymbolRules.normalizedSymbol("Fr."))
        assertEquals("fr", CurrencySymbolRules.normalizedSymbol(" Fr "))
        assertEquals("fr", CurrencySymbolRules.normalizedSymbol("FR"))
        assertEquals("fr", CurrencySymbolRules.normalizedSymbol("fr"))
    }

    @Test
    fun `normalizedSymbol applies the explicit BOB VES alias`() {
        assertEquals(
            CurrencySymbolRules.normalizedSymbol("Bs."),
            CurrencySymbolRules.normalizedSymbol("Bs.S.")
        )
    }

    @Test
    fun `classify marks CHF's Fr period symbol ambiguous alongside the plain Fr entries`() {
        val chf = entry("CHF", "Fr.", listOf("CHE", "LIE"), shared = true)
        val bif = entry("BIF", "Fr", listOf("BDI"))

        val result = CurrencySymbolRules.classify(listOf(chf, bif))

        assertTrue(result.first { it.code == "BIF" }.ambiguousSymbol)
        // CHF is already isChoiceOnly via `shared`; ambiguousSymbol is also true now that "Fr."
        // normalises the same as "Fr".
        assertTrue(result.first { it.code == "CHF" }.ambiguousSymbol)
    }

    @Test
    fun `classify marks BOB and VES ambiguous via the explicit alias even though their raw symbols differ`() {
        val bob = entry("BOB", "Bs.", listOf("BOL"))
        val ves = entry("VES", "Bs.S.", listOf("VEN"))

        val result = CurrencySymbolRules.classify(listOf(bob, ves))

        assertTrue(result.first { it.code == "BOB" }.ambiguousSymbol)
        assertTrue(result.first { it.code == "VES" }.ambiguousSymbol)
        assertTrue(result.first { it.code == "BOB" }.isChoiceOnly)
        assertTrue(result.first { it.code == "VES" }.isChoiceOnly)
    }

    @Test
    fun `countryUsesSymbol matches CHF's Fr period against a plain Fr entry`() {
        val chf = entry("CHF", "Fr.", listOf("CHE", "LIE"), shared = true)
        val bif = entry("BIF", "Fr", listOf("BDI"))

        assertTrue(CurrencySymbolRules.countryUsesSymbol("CHE", "Fr", listOf(chf, bif)))
        assertTrue(CurrencySymbolRules.countryUsesSymbol("BDI", "Fr.", listOf(chf, bif)))
    }

    @Test
    fun `isChoiceOnly is true for a non-shared entry with a non-empty listedInBasketBy`() {
        val bwp = entry("BWP", "P", listOf("BWA"), basket = listOf("ZWE"))
        val classified = CurrencySymbolRules.classify(listOf(bwp)).single()

        assertFalse(classified.shared)
        assertFalse(classified.ambiguousSymbol)
        assertTrue(classified.isChoiceOnly)
    }
}
