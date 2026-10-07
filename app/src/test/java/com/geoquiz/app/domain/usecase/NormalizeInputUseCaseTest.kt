package com.geoquiz.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven: each case is (input, expected normalised form). The Python port in
 * tools/data/export_aliases.py must give the same results (StaticDatabaseTest checks
 * every shipped alias against this implementation).
 */
class NormalizeInputUseCaseTest {

    private val normalize = NormalizeInputUseCase()

    private fun check(vararg cases: Pair<String, String>) {
        val failures = cases.mapNotNull { (input, expected) ->
            val actual = normalize(input)
            if (actual == expected) null else "'$input' -> '$actual', expected '$expected'"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `case is folded`() = check(
        "FRANCE" to "france",
        "France" to "france",
        "fRaNcE" to "france"
    )

    @Test
    fun `diacritics are removed`() = check(
        "Côte d'Ivoire" to "cote divoire",
        "Curaçao" to "curacao",
        "Réunion" to "reunion",
        "São Tomé and Príncipe" to "sao tome and principe",
        "Bogotá" to "bogota",
        "Reykjavík" to "reykjavik",
        "Asunción" to "asuncion",
        "Malé" to "male",
        "Lomé" to "lome",
        "Yaoundé" to "yaounde",
        "Nukuʻalofa" to "nukuʻalofa", // ʻokina is a letter, not an apostrophe: kept
        // Already-decomposed input (e + combining acute) gives the same result.
        "Réunion" to "reunion",
        // Letters with no decomposition are kept.
        "Straße" to "straße"
    )

    @Test
    fun `all four apostrophes are removed`() = check(
        "Cote d'Ivoire" to "cote divoire",
        "Cote d’Ivoire" to "cote divoire",
        "Cote d‘Ivoire" to "cote divoire",
        "Cote dʼIvoire" to "cote divoire",
        "St. John's" to "saint johns",
        "N'Djamena" to "ndjamena"
    )

    @Test
    fun `hyphens become spaces`() = check(
        "Timor-Leste" to "timor leste",
        "Guinea-Bissau" to "guinea bissau",
        "Port-au-Prince" to "port au prince",
        "Guinea - Bissau" to "guinea bissau"
    )

    @Test
    fun `St and Saint are the same word`() = check(
        "St Lucia" to "saint lucia",
        "St. Lucia" to "saint lucia",
        "Saint Lucia" to "saint lucia",
        "ST LUCIA" to "saint lucia",
        "St Kitts and Nevis" to "saint kitts and nevis",
        "St. Vincent and the Grenadines" to "saint vincent and the grenadines",
        "St. George's" to "saint georges",
        "St" to "saint",
        // Only the whole word: these are untouched.
        "Stockholm" to "stockholm",
        "Bucharest" to "bucharest",
        "Astana" to "astana"
    )

    @Test
    fun `ampersand means and`() = check(
        "Bosnia & Herzegovina" to "bosnia and herzegovina",
        "Bosnia&Herzegovina" to "bosnia and herzegovina",
        "Trinidad & Tobago" to "trinidad and tobago",
        "Antigua and Barbuda" to "antigua and barbuda",
        "St Kitts & Nevis" to "saint kitts and nevis"
    )

    @Test
    fun `a leading The is dropped`() = check(
        "The Gambia" to "gambia",
        "the bahamas" to "bahamas",
        "THE NETHERLANDS" to "netherlands",
        "  The   Gambia " to "gambia",
        // Only at the start, and only as a whole word with something after it.
        "Republic of the Gambia" to "republic of the gambia",
        "Theodore" to "theodore",
        "The" to "the"
    )

    @Test
    fun `full stops and commas are punctuation`() = check(
        "Washington D.C." to "washington dc",
        "Washington DC" to "washington dc",
        "U.S.A." to "usa",
        "U.K." to "uk",
        "Korea, Republic of" to "korea republic of",
        "Korea,Republic of" to "korea republic of",
        "France." to "france",
        "." to "",
        // Other punctuation is not touched.
        "Republic of China (Taiwan)" to "republic of china (taiwan)"
    )

    @Test
    fun `whitespace is trimmed and collapsed`() = check(
        "  New   Zealand  " to "new zealand",
        "United\t States" to "united states",
        "New\nZealand" to "new zealand"
    )

    @Test
    fun `blank input returns empty`() = check(
        "" to "",
        "   " to "",
        "\t\n" to ""
    )

    @Test
    fun `combined rules`() = check(
        "  CÔTE D'IVOIRE  " to "cote divoire",
        "the St. Kitts & Nevis" to "saint kitts and nevis",
        "São-Tomé & Príncipe" to "sao tome and principe"
    )

    @Test
    fun `different spellings of the same name agree`() {
        val groups = listOf(
            listOf("St Lucia", "St. Lucia", "Saint Lucia", "saint-lucia", "ST. LUCIA"),
            listOf("Bosnia and Herzegovina", "Bosnia & Herzegovina", "bosnia-and-herzegovina"),
            listOf("The Gambia", "Gambia", "the gambia"),
            listOf("Côte d'Ivoire", "Cote d’Ivoire", "COTE DIVOIRE", "Cote dIvoire"),
            listOf("St. John's", "Saint John's", "St Johns", "saint johns")
        )
        for (group in groups) {
            val forms = group.map(normalize::invoke).toSet()
            assertEquals("$group normalise to $forms", 1, forms.size)
        }
    }
}
