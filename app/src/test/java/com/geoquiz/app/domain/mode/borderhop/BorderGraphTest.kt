package com.geoquiz.app.domain.mode.borderhop

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * BFS correctness on a small hand-built graph, plus a few checks against the real bundled
 * `assets/border_hop/borders.json` (task 6.1).
 */
class BorderGraphTest {

    /**
     * A, B, C, D in a line (A-B-C-D), plus E joined only to C, and F with no edges at all (an
     * island). Mirrors a tiny slice of the real graph's shape: one connected component with a
     * branch, and one isolated country.
     */
    private fun lineGraph() = BorderGraph(
        mapOf(
            "A" to listOf("B"),
            "B" to listOf("A", "C"),
            "C" to listOf("B", "D", "E"),
            "D" to listOf("C"),
            "E" to listOf("C"),
            "F" to emptyList()
        )
    )

    @Test
    fun `neighboursOf returns the adjacency list, empty for an unknown or island code`() {
        val graph = lineGraph()
        assertEquals(listOf("A", "C"), graph.neighboursOf("B"))
        assertEquals(emptyList<String>(), graph.neighboursOf("F"))
        assertEquals(emptyList<String>(), graph.neighboursOf("ZZZ"))
    }

    @Test
    fun `isIsland is true only for a country with no edges`() {
        val graph = lineGraph()
        assertTrue(graph.isIsland("F"))
        assertEquals(false, graph.isIsland("A"))
    }

    @Test
    fun `shortestPathLength counts hops correctly, zero for the same country`() {
        val graph = lineGraph()
        assertEquals(0, graph.shortestPathLength("A", "A"))
        assertEquals(1, graph.shortestPathLength("A", "B"))
        assertEquals(3, graph.shortestPathLength("A", "D"))
        // A-B-C-E: 3 hops, not via D.
        assertEquals(3, graph.shortestPathLength("A", "E"))
        assertEquals(2, graph.shortestPathLength("D", "E"))
    }

    @Test
    fun `shortestPathLength is null across disconnected components or unknown codes`() {
        val graph = lineGraph()
        assertNull(graph.shortestPathLength("A", "F"))
        assertNull(graph.shortestPathLength("A", "ZZZ"))
        assertNull(graph.shortestPathLength("ZZZ", "A"))
    }

    @Test
    fun `shortestPath returns an inclusive path whose length matches shortestPathLength`() {
        val graph = lineGraph()
        val path = graph.shortestPath("A", "D")
        assertNotNull(path)
        assertEquals(listOf("A", "B", "C", "D"), path)
        assertEquals(1, graph.shortestPath("A", "A")!!.size)
        assertNull(graph.shortestPath("A", "F"))
    }

    @Test
    fun `distancesFrom gives every reachable country its hop count, start at zero`() {
        val graph = lineGraph()
        val distances = graph.distancesFrom("A")
        assertEquals(0, distances["A"])
        assertEquals(1, distances["B"])
        assertEquals(2, distances["C"])
        assertEquals(3, distances["D"])
        assertEquals(3, distances["E"])
        assertEquals(false, distances.containsKey("F"))
    }

    @Test
    fun `distancesFrom an unknown country is empty`() {
        assertTrue(lineGraph().distancesFrom("ZZZ").isEmpty())
    }

    @Test
    fun `randomPuzzle never picks an island as start or target`() {
        val graph = lineGraph()
        // F is the only "other" candidate besides the connected A-E; with F in the pool but
        // unreachable from anything, a puzzle must still come from the connected set.
        repeat(20) { seed ->
            val puzzle = graph.randomPuzzle(listOf("A", "B", "C", "D", "E", "F"), 1..3, Random(seed))
            if (puzzle != null) {
                assertTrue(puzzle.startCode != "F" && puzzle.targetCode != "F")
            }
        }
    }

    @Test
    fun `randomPuzzle returns null when the candidates cannot reach the requested hop range`() {
        val graph = lineGraph()
        // The whole connected component's diameter is 3 (A to D or A to E); nothing is 5+ hops away.
        val puzzle = graph.randomPuzzle(listOf("A", "B", "C", "D", "E"), 5..6, Random(1))
        assertNull(puzzle)
    }

    @Test
    fun `randomPuzzle returns null when fewer than two usable candidates are given`() {
        val graph = lineGraph()
        assertNull(graph.randomPuzzle(listOf("F"), 1..3, Random(1)))
        assertNull(graph.randomPuzzle(listOf("A"), 1..3, Random(1)))
        assertNull(graph.randomPuzzle(emptyList(), 1..3, Random(1)))
    }

    @Test
    fun `randomPuzzle's parHops always equals the real shortest-path length between its pair`() {
        val graph = lineGraph()
        repeat(30) { seed ->
            val puzzle = graph.randomPuzzle(listOf("A", "B", "C", "D", "E"), 1..3, Random(seed)) ?: return@repeat
            assertEquals(
                graph.shortestPathLength(puzzle.startCode, puzzle.targetCode),
                puzzle.parHops
            )
            assertTrue(puzzle.parHops in 1..3)
        }
    }

    // --- Real bundled data (task 6.1, D42) ---

    @Serializable
    private data class Asset(val countries: Map<String, List<String>>)

    private fun realGraph(): BorderGraph {
        val file = File("src/main/assets/border_hop/borders.json")
        val json = Json { ignoreUnknownKeys = true }
        val asset = json.decodeFromString<Asset>(file.readText())
        return BorderGraph(asset.countries)
    }

    @Test
    fun `the real graph has 197 countries and known direct and two-hop borders`() {
        val graph = realGraph()
        assertEquals(197, graph.countries.size)
        // France and Spain share a direct land border.
        assertEquals(1, graph.shortestPathLength("FRA", "ESP"))
        // Portugal's only land neighbour is Spain, so Portugal to France is exactly two hops.
        assertEquals(listOf("ESP"), graph.neighboursOf("PRT"))
        assertEquals(2, graph.shortestPathLength("PRT", "FRA"))
    }

    @Test
    fun `the real graph's islands never yield a puzzle pair`() {
        val graph = realGraph()
        // Australia and New Zealand are both islands (no land border at all, data/borders_report.md).
        assertTrue(graph.isIsland("AUS"))
        assertTrue(graph.isIsland("NZL"))
        assertNull(graph.shortestPathLength("AUS", "NZL"))
        val puzzle = graph.randomPuzzle(listOf("AUS", "NZL"), 2..6, Random(1))
        assertNull(puzzle)
    }

    @Test
    fun `the real graph has 317 edges and 40 islands, matching data_borders_report`() {
        val graph = realGraph()
        val edgeCount = graph.countries.sumOf { graph.neighboursOf(it).size } / 2
        assertEquals(317, edgeCount)
        val islandCount = graph.countries.count { graph.isIsland(it) }
        assertEquals(40, islandCount)
    }
}
