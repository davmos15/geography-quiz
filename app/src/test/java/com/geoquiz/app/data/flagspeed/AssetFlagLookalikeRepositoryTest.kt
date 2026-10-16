package com.geoquiz.app.data.flagspeed

import com.geoquiz.app.domain.mode.flagspeed.FlagLookalikeVisibility
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetFlagLookalikeRepositoryTest {

    /** Serves the committed asset from disk and counts reads. */
    private class FakeAssetSource(private val path: String = "src/main/assets/${AssetFlagLookalikeRepository.ASSET_PATH}") :
        FlagLookalikeAssetSource {
        val reads = AtomicInteger()
        override fun read(path: String): ByteArray {
            reads.incrementAndGet()
            return File(this.path).readBytes()
        }
    }

    private class BytesAssetSource(private val bytes: ByteArray) : FlagLookalikeAssetSource {
        override fun read(path: String): ByteArray = bytes
    }

    @Test
    fun `loads and parses the bundled flag_lookalikes json, caching the result`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetFlagLookalikeRepository(source, StandardTestDispatcher(testScheduler))

        val first = repo.sets()
        val second = repo.sets()

        assertSame(first, second)
        assertEquals(1, source.reads.get())
        assertEquals(24, first.size)
        assertTrue("every id should be unique", first.map { it.id }.toSet().size == first.size)
    }

    @Test
    fun `exactly one set is near_identical in the real bundled data, and it is tier 3`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetFlagLookalikeRepository(source, StandardTestDispatcher(testScheduler))

        val sets = repo.sets()
        val nearIdentical = sets.filter { it.visibility == FlagLookalikeVisibility.NEAR_IDENTICAL }

        assertEquals(1, nearIdentical.size)
        assertEquals("red-over-white", nearIdentical.single().id)
        assertEquals(setOf("IDN", "MCO"), nearIdentical.single().countries.toSet())
        assertEquals(3, nearIdentical.single().tier)
    }

    @Test
    fun `every set has between 2 and 4 members, a tier of 1 to 3, and a non-blank explanation`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetFlagLookalikeRepository(source, StandardTestDispatcher(testScheduler))

        val sets = repo.sets()
        for (set in sets) {
            assertTrue("${set.id} should have 2-4 countries, had ${set.countries.size}", set.countries.size in 2..4)
            assertTrue("${set.id} should have tier 1-3, had ${set.tier}", set.tier in 1..3)
            assertTrue("${set.id} should have an explanation", set.explanation.isNotBlank())
            assertTrue("${set.id}'s explanation should be at most 320 characters", set.explanation.length <= 320)
        }
    }

    @Test
    fun `concurrent requests share a single read`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetFlagLookalikeRepository(source, StandardTestDispatcher(testScheduler))

        val results = List(5) { async { repo.sets() } }.awaitAll()

        assertEquals(1, source.reads.get())
        assertTrue(results.all { it === results[0] })
    }

    @Test
    fun `an unknown in_app value fails safe to near_identical rather than failing to load`() = runTest {
        // Task 7.3 fix round, item 6: an unrecognised value must never become askable by
        // accident, so it reads as near_identical (never drawn), not clear (never excluded).
        val json = """
            {"sets": [{"id": "x", "countries": ["AAA", "BBB"], "tier": 2, "in_app": "mystery", "explanation": "e", "cues": {}}]}
        """.trimIndent()
        val repo = AssetFlagLookalikeRepository(BytesAssetSource(json.toByteArray()), StandardTestDispatcher(testScheduler))

        val sets = repo.sets()

        assertEquals(1, sets.size)
        assertEquals(FlagLookalikeVisibility.NEAR_IDENTICAL, sets.single().visibility)
    }

    @Test(expected = SerializationException::class)
    fun `malformed json fails loudly rather than returning a silently wrong result`() = runTest {
        val repo = AssetFlagLookalikeRepository(BytesAssetSource("not json".toByteArray()), StandardTestDispatcher(testScheduler))
        repo.sets()
    }
}
