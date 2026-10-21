package com.geoquiz.app.domain.mode.borderhop

import com.geoquiz.app.testutil.TestQuizData.country
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BorderHopChoiceBuilderTest {

    /**
     * A small piece of southern Africa:
     * ZAF borders NAM, BWA, ZWE, MOZ, LSO; BWA-ZMB, ZWE-ZMB, NAM-BWA, NAM-ZMB, MOZ-ZWE, MOZ-ZMB,
     * ZMB-TZA, TZA-KEN. MDG and JPN have no land border.
     */
    private val edges = listOf(
        "ZAF" to "NAM", "ZAF" to "BWA", "ZAF" to "ZWE", "ZAF" to "MOZ", "ZAF" to "LSO",
        "BWA" to "ZMB", "ZWE" to "ZMB", "NAM" to "BWA", "NAM" to "ZMB", "MOZ" to "ZWE", "MOZ" to "ZMB",
        "ZMB" to "TZA", "TZA" to "KEN"
    )
    private val graph = BorderGraph(
        buildMap<String, MutableList<String>> {
            listOf("MDG", "JPN").forEach { put(it, mutableListOf()) }
            edges.forEach { (a, b) ->
                getOrPut(a) { mutableListOf() } += b
                getOrPut(b) { mutableListOf() } += a
            }
        }
    )
    private val countries = listOf("ZAF", "NAM", "BWA", "ZWE", "MOZ", "LSO", "ZMB", "TZA", "KEN", "MDG").map {
        country(it, "Country $it", "", region = "Africa")
    } + country("JPN", "Japan", "Tokyo", region = "Asia")

    private fun build(path: List<String>, target: String, seed: Int) =
        BorderHopChoiceBuilder.build(graph, path, target, countries, Random(seed))

    @Test
    fun `exactly one option is a neighbour, and it lies on a shortest route`() {
        repeat(30) { seed ->
            val choice = build(listOf("ZAF"), "TZA", seed)!!
            assertEquals(BorderHopChoiceBuilder.OPTION_COUNT, choice.options.size)
            assertEquals(choice.options.size, choice.options.toSet().size)
            val neighbours = choice.options.filter { it in graph.neighboursOf("ZAF") }
            assertEquals(listOf(choice.validCode), neighbours)
            assertTrue(choice.validCode in setOf("NAM", "BWA", "ZWE", "MOZ"))
            assertEquals(2, graph.shortestPathLength(choice.validCode, "TZA"))
        }
    }

    @Test
    fun `distractors are never on the route or the target, and prefer countries two hops away`() {
        repeat(30) { seed ->
            val path = listOf("LSO", "ZAF")
            val choice = build(path, "ZMB", seed)!!
            val distractors = choice.options - choice.validCode
            assertTrue(distractors.none { it in path || it == "ZMB" })
            // From ZAF every country two hops away is excluded here, so the same region fills in.
            assertEquals(setOf("TZA", "KEN", "MDG"), distractors.toSet())
            assertTrue("JPN is the last resort and Africa has enough", "JPN" !in distractors)
        }
    }

    @Test
    fun `a country two hops away is the first distractor drawn`() {
        repeat(20) { seed ->
            // ZAF to TZA: ZMB is two hops from ZAF and not a neighbour, so it is always offered.
            assertTrue("ZMB" in build(listOf("ZAF"), "TZA", seed)!!.options)
        }
    }

    @Test
    fun `the valid hop is never a country already on the route`() {
        // From BWA towards ZWE: closer neighbours are ZAF and ZMB; ZAF is on the route.
        repeat(20) { seed ->
            assertEquals("ZMB", build(listOf("ZAF", "BWA"), "ZWE", seed)!!.validCode)
        }
    }

    @Test
    fun `no options when the target cannot be reached`() {
        assertNull(build(listOf("ZAF"), "MDG", 1))
        assertNull(build(emptyList(), "ZAF", 1))
    }

    @Test
    fun `the same seed gives the same options`() {
        assertEquals(build(listOf("ZAF"), "KEN", 7), build(listOf("ZAF"), "KEN", 7))
    }
}
