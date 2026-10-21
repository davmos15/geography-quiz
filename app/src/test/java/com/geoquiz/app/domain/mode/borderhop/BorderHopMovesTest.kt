package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.domain.model.AnswerAlias
import com.geoquiz.app.domain.usecase.NormalizeInputUseCase
import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BorderHopMovesTest {

    private val normalize = NormalizeInputUseCase()

    /** Diamond: ZAF borders BWA and ZWE, both border ZMB; JPN has no land border. */
    private val graph = BorderGraph(
        mapOf(
            "ZAF" to listOf("BWA", "ZWE"),
            "BWA" to listOf("ZAF", "ZMB"),
            "ZWE" to listOf("ZAF", "ZMB"),
            "ZMB" to listOf("BWA", "ZWE"),
            "JPN" to emptyList()
        )
    )

    private val gbr = country("GBR", "United Kingdom", "London")
    private val bwa = country("BWA", "Botswana", "Gaborone")
    private val ice = country("ISL", "Iceland", "Reykjavik")
    private val irl = country("IRL", "Ireland", "Dublin")
    private val aliases = listOf(
        AnswerAlias("united kingdom", gbr),
        AnswerAlias("uk", gbr),
        AnswerAlias("botswana", bwa),
        AnswerAlias("iceland", ice),
        AnswerAlias("ireland", irl)
    )

    @Test
    fun `resolve finds exact aliases at any tier`() {
        assertEquals(gbr, BorderHopMoves.resolve(normalize("UK"), aliases, allowFuzzy = false))
        assertEquals(bwa, BorderHopMoves.resolve(normalize("Botswana"), aliases, allowFuzzy = false))
    }

    @Test
    fun `resolve forgives one typo only when fuzzy is allowed`() {
        assertEquals(bwa, BorderHopMoves.resolve(normalize("Botswanna"), aliases, allowFuzzy = true))
        assertNull(BorderHopMoves.resolve(normalize("Botswanna"), aliases, allowFuzzy = false))
    }

    @Test
    fun `resolve leaves an ambiguous typo or nonsense unrecognised`() {
        // "iteland" is one edit from both Iceland and Ireland.
        assertNull(BorderHopMoves.resolve(normalize("Iteland"), aliases, allowFuzzy = true))
        assertNull(BorderHopMoves.resolve(normalize("Atlantis"), aliases, allowFuzzy = true))
        assertNull(BorderHopMoves.resolve("", aliases, allowFuzzy = true))
    }

    // ---- Short names (4-6 characters), P10-R2 L2 ------------------------------------------------

    private val brazil = country("BRA", "Brazil", "Brasilia")
    private val cuba = country("CUB", "Cuba", "Havana")
    private val iran = country("IRN", "Iran", "Tehran")
    private val iraq = country("IRQ", "Iraq", "Baghdad")
    private val shortNameAliases = aliases + listOf(
        AnswerAlias(normalize("Brazil"), brazil),
        AnswerAlias(normalize("Cuba"), cuba),
        AnswerAlias(normalize("Iran"), iran),
        AnswerAlias(normalize("Iraq"), iraq)
    )

    @Test
    fun `resolve forgives one typo in a short name at Normal (FuzzyAnswerMatcher only fuzzes over 6 characters)`() {
        // "Brazl" is one insertion from "Brazil" (6 characters, below FuzzyAnswerMatcher's own
        // minimum), so it would otherwise stay unrecognised.
        assertEquals(brazil, BorderHopMoves.resolve(normalize("Brazl"), shortNameAliases, allowFuzzy = true))
        assertNull(BorderHopMoves.resolve(normalize("Brazl"), shortNameAliases, allowFuzzy = false))
    }

    @Test
    fun `an ambiguous short-name typo stays unrecognised`() {
        // "Iram" is one substitution from both "Iran" and "Iraq".
        assertNull(BorderHopMoves.resolve(normalize("Iram"), shortNameAliases, allowFuzzy = true))
    }

    @Test
    fun `an input under four characters is never fuzzy-matched, even one edit from a short name`() {
        // "Cub" is one deletion from "Cuba", but three characters is below the short-name floor.
        assertNull(BorderHopMoves.resolve(normalize("Cub"), shortNameAliases, allowFuzzy = true))
    }

    @Test
    fun `judge classifies a guess against the route`() {
        val path = listOf("ZAF", "BWA")
        assertEquals(BorderHopMove.AlreadyOnRoute("ZAF"), BorderHopMoves.judge(graph, path, "ZMB", "ZAF"))
        assertEquals(BorderHopMove.AlreadyOnRoute("BWA"), BorderHopMoves.judge(graph, path, "ZMB", "BWA"))
        assertEquals(BorderHopMove.NotNeighbour("ZWE", "BWA"), BorderHopMoves.judge(graph, path, "ZMB", "ZWE"))
        assertEquals(BorderHopMove.NotNeighbour("JPN", "BWA"), BorderHopMoves.judge(graph, path, "ZMB", "JPN"))
        assertEquals(BorderHopMove.Hop("ZMB", reachesTarget = true), BorderHopMoves.judge(graph, path, "ZMB", "ZMB"))
        assertEquals(BorderHopMove.Hop("ZMB", reachesTarget = false), BorderHopMoves.judge(graph, listOf("BWA"), "ZAF", "ZMB"))
    }

    @Test
    fun `closerNeighbours lists every neighbour one hop nearer the target`() {
        assertEquals(listOf("BWA", "ZWE"), BorderHopMoves.closerNeighbours(graph, "ZAF", "ZMB"))
        assertEquals(listOf("ZMB"), BorderHopMoves.closerNeighbours(graph, "BWA", "ZMB"))
        assertTrue(BorderHopMoves.closerNeighbours(graph, "ZAF", "JPN").isEmpty())
    }
}
