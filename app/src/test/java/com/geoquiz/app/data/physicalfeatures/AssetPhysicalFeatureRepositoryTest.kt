package com.geoquiz.app.data.physicalfeatures

import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureType
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssetPhysicalFeatureRepositoryTest {

    /** Serves the committed asset from disk and counts reads. */
    private class FakeAssetSource : PhysicalFeatureAssetSource {
        val reads = AtomicInteger()
        override fun read(path: String): ByteArray {
            reads.incrementAndGet()
            return File("src/main/assets/$path").readBytes()
        }
    }

    @Test
    fun `loads and parses every one of the 101 bundled features, caching the result`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetPhysicalFeatureRepository(source, StandardTestDispatcher(testScheduler))

        val first = repo.features()
        val second = repo.features()

        assertSame(first, second)
        assertEquals(1, source.reads.get())
        assertEquals(101, first.size)
        assertEquals(41, first.count { it.type == PhysicalFeatureType.RIVER })
        assertEquals(30, first.count { it.type == PhysicalFeatureType.LAKE })
        assertEquals(22, first.count { it.type == PhysicalFeatureType.RANGE })
        assertEquals(8, first.count { it.type == PhysicalFeatureType.PEAK })
        assertEquals(first.size, first.distinctBy { it.id }.size)
    }

    @Test
    fun `every feature resolves to a real layer id matching its type`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetPhysicalFeatureRepository(source, StandardTestDispatcher(testScheduler))
        val features = repo.features()

        features.filter { it.type == PhysicalFeatureType.RIVER }.forEach {
            assertEquals(GeoLayerId.RIVERS_50M, it.layerId)
        }
        features.filter { it.type == PhysicalFeatureType.LAKE }.forEach {
            assertEquals(GeoLayerId.LAKES_50M, it.layerId)
        }
        features.filter { it.type == PhysicalFeatureType.RANGE || it.type == PhysicalFeatureType.PEAK }.forEach {
            assertTrue(it.layerId == GeoLayerId.REGIONS_50M || it.layerId == GeoLayerId.GEO_POINTS_50M)
        }
        // Every feature has at least one Natural Earth id to resolve against its layer.
        assertTrue(features.all { it.neIds.isNotEmpty() })
    }

    @Test
    fun `concurrent requests share a single read`() = runTest {
        val source = FakeAssetSource()
        val repo = AssetPhysicalFeatureRepository(source, StandardTestDispatcher(testScheduler))

        val results = List(5) { async { repo.features() } }.awaitAll()

        assertEquals(1, source.reads.get())
        assertTrue(results.all { it === results[0] })
    }

    @Test
    fun `layerIdOf maps the data file's layer field names`() {
        assertEquals(GeoLayerId.RIVERS_50M, AssetPhysicalFeatureRepository.layerIdOf("rivers_50m"))
        assertEquals(GeoLayerId.LAKES_50M, AssetPhysicalFeatureRepository.layerIdOf("lakes_50m"))
        assertEquals(GeoLayerId.REGIONS_50M, AssetPhysicalFeatureRepository.layerIdOf("regions_50m"))
        assertEquals(GeoLayerId.GEO_POINTS_50M, AssetPhysicalFeatureRepository.layerIdOf("geo_points_50m"))
        assertEquals(null, AssetPhysicalFeatureRepository.layerIdOf("admin0_50m"))
    }
}
