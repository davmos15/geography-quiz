package com.geoquiz.app.data.geo

import com.geoquiz.app.domain.map.GeoFormatException
import com.geoquiz.app.domain.map.GeoLayerId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class AssetMapRepositoryTest {

    /** Serves the committed assets from disk and counts reads; can be told to fail. */
    private class FakeAssetSource : GeoAssetSource {
        val reads = AtomicInteger()
        val paths = mutableListOf<String>()
        var failNext: IOException? = null
        var override: ByteArray? = null

        override fun read(path: String): ByteArray {
            reads.incrementAndGet()
            synchronized(paths) { paths += path }
            failNext?.let { failNext = null; throw it }
            override?.let { return it }
            return File("src/main/${path.let { "assets/$it" }}").readBytes()
        }
    }

    @Test
    fun `loads a layer from its asset path and caches it`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        assertNull(repo.cachedLayer(GeoLayerId.ADMIN0_110M))
        val first = repo.layer(GeoLayerId.ADMIN0_110M)
        val second = repo.layer(GeoLayerId.ADMIN0_110M)

        assertSame(first, second)
        assertSame(first, repo.cachedLayer(GeoLayerId.ADMIN0_110M))
        assertEquals(1, source.reads.get())
        assertEquals(listOf("geo/admin0_110m.bin"), source.paths)
        assertEquals(204, first.features.size)
    }

    @Test
    fun `concurrent requests for one layer share a single read`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        val layers = List(5) { async { repo.layer(GeoLayerId.TAP_ZONES) } }.awaitAll()

        assertEquals(1, source.reads.get())
        assertTrue(layers.all { it === layers[0] })
    }

    @Test
    fun `different layers are cached separately`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        val lakes = repo.layer(GeoLayerId.LAKES_50M)
        val rivers = repo.layer(GeoLayerId.RIVERS_50M)

        assertEquals(GeoLayerId.LAKES_50M, lakes.id)
        assertEquals(GeoLayerId.RIVERS_50M, rivers.id)
        assertEquals(2, source.reads.get())
    }

    @Test
    fun `a failed read is not cached`() = runTest {
        val source = FakeAssetSource().apply { failNext = IOException("disk") }
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        try {
            repo.layer(GeoLayerId.ADMIN1_50M)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("disk", e.message)
        }
        assertNull(repo.cachedLayer(GeoLayerId.ADMIN1_50M))

        assertEquals(64, repo.layer(GeoLayerId.ADMIN1_50M).features.size)
        assertEquals(2, source.reads.get())
    }

    @Test
    fun `corrupt asset throws a format error`() = runTest {
        val source = FakeAssetSource().apply { override = byteArrayOf(1, 2, 3, 4, 5) }
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        try {
            repo.layer(GeoLayerId.ADMIN0_50M)
            fail("Expected GeoFormatException")
        } catch (e: GeoFormatException) {
            assertEquals(GeoFormatException.Reason.BAD_MAGIC, e.reason)
        }
    }

    @Test
    fun `a file holding another layer is rejected`() = runTest {
        // The fixture is layer 2 (admin0_50m) but is served for admin1.
        val source = FakeAssetSource().apply { override = GeoFixtureWriter.sample().bytes() }
        val repo = AssetMapRepository(source, StandardTestDispatcher(testScheduler))

        try {
            repo.layer(GeoLayerId.ADMIN1_50M)
            fail("Expected GeoFormatException")
        } catch (e: GeoFormatException) {
            assertEquals(GeoFormatException.Reason.WRONG_LAYER, e.reason)
        }
        assertEquals(3, repo.layer(GeoLayerId.ADMIN0_50M).features.size)
    }
}
