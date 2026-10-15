package com.geoquiz.app.data.borderhop

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class AssetBorderGraphRepositoryTest {

    /** Serves the committed asset from disk and counts reads. */
    private class FakeAssetSource : BorderGraphAssetSource {
        val reads = AtomicInteger()
        override fun read(path: String): ByteArray {
            reads.incrementAndGet()
            return File("src/main/assets/$path").readBytes()
        }
    }

    @Test
    fun `loads and parses the bundled borders json, caching the result`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetBorderGraphRepository(source, StandardTestDispatcher(testScheduler))

        val first = repo.graph()
        val second = repo.graph()

        assertSame(first, second)
        assertEquals(1, source.reads.get())
        assertEquals(197, first.countries.size)
        assertEquals(1, first.shortestPathLength("FRA", "ESP"))
    }

    @Test
    fun `a Hard-tier puzzle (6 to 21 hops) can be generated from the real bundled graph`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetBorderGraphRepository(source, StandardTestDispatcher(testScheduler))
        val graph = repo.graph()

        // D42's diameter is 21 hops; task 6.1's Hard tier is "6 hops or more" (border_hop_hard_summary).
        val puzzle = graph.randomPuzzle(graph.countries, 6..21, Random(1))

        assertNotNull("a 6+ hop pair must exist in the real graph for Hard to ever offer a round", puzzle)
        val hops = graph.shortestPathLength(puzzle!!.startCode, puzzle.targetCode)
        assertTrue("puzzle should be within the requested range, was $hops hops", hops in 6..21)
    }

    @Test
    fun `concurrent requests share a single read`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetBorderGraphRepository(source, StandardTestDispatcher(testScheduler))

        val graphs = List(5) { async { repo.graph() } }.awaitAll()

        assertEquals(1, source.reads.get())
        assertTrue(graphs.all { it === graphs[0] })
    }
}
