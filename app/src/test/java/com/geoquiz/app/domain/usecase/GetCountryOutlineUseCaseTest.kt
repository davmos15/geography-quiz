package com.geoquiz.app.domain.usecase

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeoPropertyDef
import com.geoquiz.app.domain.map.GeoPropertyType
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.map.MapRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/** [GetCountryOutlineUseCase] delegates to [OutlineGeometry][com.geoquiz.app.domain.map.OutlineGeometry] on the right layer. */
class GetCountryOutlineUseCaseTest {

    private val schema = listOf(GeoPropertyDef("playable", GeoPropertyType.UINT8))

    private fun rectangle(id: String, minLon: Float, maxLon: Float, minLat: Float, maxLat: Float) = GeoFeature(
        id,
        GeoProperties(schema, arrayOf(1)),
        GeoBounds(minLon.toDouble(), minLat.toDouble(), maxLon.toDouble(), maxLat.toDouble()),
        ((minLon + maxLon) / 2).toDouble(), ((minLat + maxLat) / 2).toDouble(),
        listOf(GeoPart(0, floatArrayOf(minLon, maxLon, maxLon, minLon), floatArrayOf(minLat, minLat, maxLat, maxLat))),
    )

    private val layer = GeoLayer(
        GeoLayerId.ADMIN0_50M, GeometryKind.POLYGON, GeoBounds(-180.0, -90.0, 180.0, 90.0), "test", schema,
        // ESP shares FRA's western border (lon -5) and overlaps it on the lat axis too.
        listOf(rectangle("FRA", -5f, 5f, -5f, 5f), rectangle("ESP", -15f, -4f, -5f, 5f)),
    )

    private val repository = mockk<MapRepository> {
        coEvery { layer(GeoLayerId.ADMIN0_50M) } returns layer
    }

    @Test
    fun `builds the outline from the admin0_50m layer`() = runTest {
        val outline = GetCountryOutlineUseCase(repository)("FRA")
        assertEquals("FRA", outline?.cca3)
        assertTrue(outline!!.rings.isNotEmpty())
        coVerify(exactly = 1) { repository.layer(GeoLayerId.ADMIN0_50M) }
    }

    @Test
    fun `an unknown code returns null`() = runTest {
        assertNull(GetCountryOutlineUseCase(repository)("ZZZ"))
    }

    @Test
    fun `passes includeRemote and includeNeighbours through`() = runTest {
        val withoutNeighbours = GetCountryOutlineUseCase(repository)("FRA", includeNeighbours = false)
        assertTrue(withoutNeighbours!!.neighbours.isEmpty())

        val withNeighbours = GetCountryOutlineUseCase(repository)("FRA", includeNeighbours = true)
        assertTrue(withNeighbours!!.neighbours.any { it.cca3 == "ESP" })

        val withRemote = GetCountryOutlineUseCase(repository)("FRA", includeRemote = true)
        assertEquals(true, withRemote?.includeRemote)
        assertEquals(false, withNeighbours.includeRemote)
    }

    @Test
    fun `builds the outline on the injected dispatcher, off the caller's`() = runTest {
        val dispatchCount = AtomicInteger(0)
        val tracking = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                dispatchCount.incrementAndGet()
                block.run()
            }
        }
        val outline = GetCountryOutlineUseCase(repository, tracking)("FRA")
        assertEquals("FRA", outline?.cca3)
        assertTrue("OutlineGeometry.build should run via withContext(defaultDispatcher)", dispatchCount.get() > 0)
    }
}
