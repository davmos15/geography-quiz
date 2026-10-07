package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.model.Country
import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyAnswerMatcherTest {

    private fun country(code: String, name: String) = Country(code, name, name, "", "", name.length, "")

    private val iceland = country("ISL", "Iceland")
    private val ireland = country("IRL", "Ireland")
    private val australia = country("AUS", "Australia")
    private val austria = country("AUT", "Austria")
    private val iran = country("IRN", "Iran")
    private val iraq = country("IRQ", "Iraq")
    private val kingstonJam = country("JAM", "Jamaica")
    private val kingstownVct = country("VCT", "Saint Vincent and the Grenadines")

    private val aliases = listOf(
        AnswerAlias("iceland", iceland),
        AnswerAlias("republic of iceland", iceland),
        AnswerAlias("ireland", ireland),
        AnswerAlias("republic of ireland", ireland),
        AnswerAlias("australia", australia),
        AnswerAlias("austria", austria),
        AnswerAlias("iran", iran),
        AnswerAlias("iraq", iraq)
    )

    @Test
    fun `distance table`() {
        val cases = listOf(
            Triple("", "", 0),
            Triple("abc", "", 3),
            Triple("", "abc", 3),
            Triple("france", "france", 0),
            Triple("france", "frence", 1),      // substitution
            Triple("france", "frane", 1),       // deletion
            Triple("france", "frannce", 1),     // insertion
            Triple("france", "farnce", 1),      // adjacent swap counts as one edit
            Triple("ca", "abc", 3),             // OSA, not unrestricted Damerau (which gives 2)
            Triple("austria", "australia", 2),
            Triple("iceland", "ireland", 1),
            Triple("kitten", "sitting", 3)
        )
        for ((a, b, expected) in cases) {
            assertEquals("distance('$a', '$b')", expected, FuzzyAnswerMatcher.distance(a, b))
            assertEquals("distance('$b', '$a')", expected, FuzzyAnswerMatcher.distance(b, a))
        }
    }

    @Test
    fun `distance stops early above the limit`() {
        assertEquals(2, FuzzyAnswerMatcher.distance("kitten", "sitting", max = 1))
        assertEquals(2, FuzzyAnswerMatcher.distance("a", "abcdef", max = 1))
        assertEquals(1, FuzzyAnswerMatcher.distance("france", "farnce", max = 1))
        assertEquals(0, FuzzyAnswerMatcher.distance("france", "france", max = 1))
    }

    @Test
    fun `one typo on a long name is a unique match`() {
        assertEquals(FuzzyAnswerMatcher.Match.Unique(australia), FuzzyAnswerMatcher.match("austrailia", aliases))
        assertEquals(FuzzyAnswerMatcher.Match.Unique(austria), FuzzyAnswerMatcher.match("austira", aliases))
        assertEquals(FuzzyAnswerMatcher.Match.Unique(iceland), FuzzyAnswerMatcher.match("icealnd", aliases))
        assertEquals(FuzzyAnswerMatcher.Match.Unique(ireland), FuzzyAnswerMatcher.match("republic of irelnd", aliases))
    }

    @Test
    fun `two typos is no match`() {
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("austrlai", aliases))
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("atlantis", aliases))
    }

    @Test
    fun `names of six characters or fewer are never matched loosely`() {
        // Input too short.
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("irland", aliases))
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("irqa", aliases))
        // Candidate too short: "irann" (5) is one edit from "iran" (4) but neither is long enough,
        // and a 7-letter input is never compared with a 6-letter alias.
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("irann", aliases))
        val sixLetter = listOf(AnswerAlias("france", country("FRA", "France")))
        assertEquals(FuzzyAnswerMatcher.Match.None, FuzzyAnswerMatcher.match("frannce", sixLetter))
    }

    @Test
    fun `input one edit from two countries is ambiguous`() {
        // "icelandd" is one edit from "iceland" and two from "ireland"; "ixeland" is one
        // substitution from both.
        assertEquals(FuzzyAnswerMatcher.Match.Unique(iceland), FuzzyAnswerMatcher.match("icelandd", aliases))
        assertEquals(
            FuzzyAnswerMatcher.Match.Ambiguous(setOf(iceland, ireland)),
            FuzzyAnswerMatcher.match("ixeland", aliases)
        )
        assertEquals(
            FuzzyAnswerMatcher.Match.Ambiguous(setOf(iceland, ireland)),
            FuzzyAnswerMatcher.match("republic of ixeland", aliases)
        )
    }

    @Test
    fun `several aliases of the same country count once`() {
        val dupes = listOf(
            AnswerAlias("kingstown", kingstownVct),
            AnswerAlias("kingstown", kingstownVct),
            AnswerAlias("kingston", kingstonJam)
        )
        assertEquals(FuzzyAnswerMatcher.Match.Unique(kingstownVct), FuzzyAnswerMatcher.match("kingstowm", dupes))
        // "kingstonw" is one swap from "kingstown" and one insertion from "kingston".
        assertEquals(
            FuzzyAnswerMatcher.Match.Ambiguous(setOf(kingstownVct, kingstonJam)),
            FuzzyAnswerMatcher.match("kingstonw", dupes)
        )
    }
}
