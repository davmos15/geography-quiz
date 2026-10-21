package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountryNameSuggesterTest {

    private val normalize = NormalizeInputUseCase()
    private val suggester = CountryNameSuggester(normalize)

    private val countries = listOf(
        country("GBR", "United Kingdom", ""),
        country("USA", "United States", ""),
        country("ARE", "United Arab Emirates", ""),
        country("CIV", "Côte d'Ivoire", ""),
        country("DEU", "Germany", ""),
        country("GEO", "Georgia", ""),
        country("GHA", "Ghana", ""),
        country("GRC", "Greece", ""),
        country("GRD", "Grenada", ""),
        country("GTM", "Guatemala", ""),
        country("BRA", "Brazil", ""),
        country("PNG", "Papua New Guinea", ""),
        country("GIN", "Guinea", "")
    )
    private val byCode = countries.associateBy { it.code }

    private val aliases: List<AnswerAlias> = countries.map { AnswerAlias(normalize(it.name), it) } + listOf(
        AnswerAlias("uk", byCode.getValue("GBR")),
        AnswerAlias("great britain", byCode.getValue("GBR")),
        AnswerAlias("ivory coast", byCode.getValue("CIV")),
        AnswerAlias("deutschland", byCode.getValue("DEU"))
    )

    private fun codes(input: String, limit: Int = CountryNameSuggester.DEFAULT_LIMIT) =
        suggester.suggest(input, aliases, limit).map { it.code }

    @Test
    fun `blank input suggests nothing`() {
        assertTrue(codes("").isEmpty())
        assertTrue(codes("   ").isEmpty())
    }

    @Test
    fun `prefix matches come back alphabetically`() {
        assertEquals(listOf("ARE", "GBR", "USA"), codes("united"))
    }

    @Test
    fun `an exact alias such as UK ranks first`() {
        assertEquals("GBR", codes("UK").first())
    }

    @Test
    fun `aliases find their country, once`() {
        assertEquals(listOf("GBR"), codes("great"))
        assertEquals(listOf("DEU"), codes("deutsch"))
    }

    @Test
    fun `diacritics, case and apostrophes do not matter`() {
        assertEquals(listOf("CIV"), codes("COTE D"))
        assertEquals(listOf("CIV"), codes("côte d'iv"))
        assertEquals(listOf("CIV"), codes("ivory"))
    }

    @Test
    fun `a later word's prefix matches after whole-name prefixes`() {
        // "guinea" is Guinea's own name (exact) and a later word of Papua New Guinea.
        assertEquals(listOf("GIN", "PNG"), codes("guinea"))
        assertEquals(listOf("GBR"), codes("kingdom"))
    }

    @Test
    fun `a typo still finds the country from four characters on`() {
        assertEquals(listOf("DEU"), codes("germny"))
        assertEquals(listOf("BRA"), codes("brazl"))
        assertTrue("no fuzzy below four characters", codes("brz").isEmpty())
    }

    @Test
    fun `fuzzy matches rank after prefix matches`() {
        // "gree" is a prefix of Greece and one edit from the start of Grenada ("gre") and of the
        // alias "great britain"; a country's own name wins the tie.
        assertEquals(listOf("GRC", "GRD", "GBR"), codes("gree"))
    }

    @Test
    fun `at most five suggestions, or the given limit`() {
        assertEquals(5, codes("g").size)
        assertEquals(listOf("GEO", "DEU"), codes("ge", limit = 2))
        assertTrue(codes("g", limit = 0).isEmpty())
    }

    @Test
    fun `own-name ranking stays correct across repeated calls and a changed alias list (P10-R2 L3)`() {
        // Each own-country-name tie-break is cached per alias list by reference; repeated calls
        // with the same list (every keystroke of a round) must keep ranking correctly.
        repeat(3) { assertEquals(listOf("GRC", "GRD", "GBR"), codes("gree")) }

        // A different list (e.g. a fresh round) must not reuse a stale cached own-name map.
        val onlyGreece = listOf(AnswerAlias(normalize("Greece"), byCode.getValue("GRC")))
        assertEquals(listOf("GRC"), suggester.suggest("gree", onlyGreece).map { it.code })

        // Back on the original list, ranking is still correct.
        assertEquals(listOf("GRC", "GRD", "GBR"), codes("gree"))
    }
}
