package com.geoquiz.app.ui.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeoPropertyDef
import com.geoquiz.app.domain.map.GeoPropertyType
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.PlaneRect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Path cache, scene bounds and re-keying (Robolectric: paths are android.graphics.Path). */
@RunWith(RobolectricTestRunner::class)
class MapSceneTest {

    @get:Rule
    val compose = createComposeRule()

    private val coarse = squareLayer(squareFeature("A", 0f, 10f), squareFeature("B", 10f, 20f))
    private val detail = squareLayer(squareFeature("A", 5f, 30f), id = com.geoquiz.app.domain.map.GeoLayerId.ADMIN0_50M)

    @Test
    fun `a second request reuses the same paths`() = runBlocking {
        val cache = MapPathCache()
        val first = cache.paths(coarse, PlateProjection)
        val second = cache.paths(coarse, PlateProjection)
        assertSame(first, second)
        assertSame(first.await(), second.await())
        assertNotSame(first, cache.paths(coarse, EqualEarthProjection))
        assertNotSame(first, cache.paths(detail, PlateProjection))
        // Equal projections (data classes) share an entry.
        assertSame(
            cache.paths(coarse, LambertAzimuthalEqualAreaProjection(15.0, 52.0)),
            cache.paths(coarse, LambertAzimuthalEqualAreaProjection(15.0, 52.0)),
        )
        cache.clear()
        assertNotSame(first, cache.paths(coarse, PlateProjection))
    }

    // ---- clear() vs. an in-flight build (task 9.2 review fix) ----

    @Test
    fun `clear while a build is in flight neither crashes nor resurrects the discarded entry`() = runBlocking {
        val cache = MapPathCache()
        val inFlight = cache.paths(coarse, PlateProjection)
        // clear() and the build itself (on Dispatchers.Default) race; either order must be safe
        // (see MapPathCache.clear's doc: paths() and clear() share one monitor, and a build never
        // writes its result back into the map on completion, so clearing can only drop the map
        // entry, never resurrect it once the build finishes).
        cache.clear()
        val result = inFlight.await()
        assertTrue("the build in flight when clear() ran still finishes normally", result.size > 0)
        assertNotSame(
            "the cleared key is gone: the next request is a fresh build, not the discarded one",
            inFlight,
            cache.paths(coarse, PlateProjection)
        )
    }

    @Test
    fun `path cache evicts the least recently used projection beyond its capacity`() = runBlocking {
        // 4.6/7: bounded by distinct *projections*, not individual layer entries - each
        // projection below only ever has one (coarse) entry, to keep the LRU bookkeeping
        // visible directly through object identity of the cached Deferred.
        val cache = MapPathCache(projectionCapacity = 2)
        val a = LambertAzimuthalEqualAreaProjection(0.0, 0.0)
        val b = LambertAzimuthalEqualAreaProjection(10.0, 0.0)
        val c = LambertAzimuthalEqualAreaProjection(20.0, 0.0)

        val firstA = cache.paths(coarse, a)
        val firstB = cache.paths(coarse, b)
        // A third distinct projection pushes A (the least recently touched) out.
        val firstC = cache.paths(coarse, c)

        // Check the still-cached projections first: re-requesting the evicted A touches the LRU
        // again and would otherwise evict whichever of these is checked after it.
        assertSame("B is still cached", firstB, cache.paths(coarse, b))
        assertSame("C is still cached", firstC, cache.paths(coarse, c))
        assertNotSame("A should have been evicted once a third projection was cached", firstA, cache.paths(coarse, a))
    }

    @Test
    fun `touching a cached projection again keeps it from being the next eviction`() = runBlocking {
        val cache = MapPathCache(projectionCapacity = 2)
        val a = LambertAzimuthalEqualAreaProjection(0.0, 0.0)
        val b = LambertAzimuthalEqualAreaProjection(10.0, 0.0)
        val c = LambertAzimuthalEqualAreaProjection(20.0, 0.0)

        val firstA = cache.paths(coarse, a)
        val firstB = cache.paths(coarse, b)
        // Re-touching A makes B the least recently used instead, so the next new projection
        // evicts B, not A.
        cache.paths(coarse, a)
        cache.paths(coarse, c)

        assertSame("A was touched again, so it should survive", firstA, cache.paths(coarse, a))
        assertNotSame("B, now the least recently used, should have been evicted", firstB, cache.paths(coarse, b))
    }

    // ---- MapHit.isMember (4.6/4): computed by MapScene.hitTest from its own member set ----

    @Test
    fun `isMember reflects the scene's own member set, true for a member and false for context land`() = runBlocking {
        val layer = squareLayer(squareFeature("MAR", 0f, 1f), squareFeature("FRA", 2f, 3f))
        val cache = MapPathCache()
        val land = cache.paths(layer, PlateProjection).await()
        // A stand-in "Europe" preset: FRA is a member, MAR (just outside it geographically) is not.
        val scene = MapScene(PlateProjection, land, null, members = setOf("FRA"))
        val state = MapViewState().apply {
            updateViewport(400f, 400f)
            updateContentBounds(scene.contentBounds)
        }

        val morocco = scene.hitTest(state, state.planeToScreenX(0.5f), state.planeToScreenY(0.5f), density = 2f, useTapZones = false)
        assertEquals("MAR", morocco?.featureId)
        assertEquals(false, morocco?.isMember)

        val france = scene.hitTest(state, state.planeToScreenX(2.5f), state.planeToScreenY(2.5f), density = 2f, useTapZones = false)
        assertEquals("FRA", france?.featureId)
        assertEquals(true, france?.isMember)
    }

    @Test
    fun `isMember is always true with no member set`() = runBlocking {
        val layer = squareLayer(squareFeature("MAR", 0f, 1f))
        val cache = MapPathCache()
        val land = cache.paths(layer, PlateProjection).await()
        val scene = MapScene(PlateProjection, land, null, members = null)
        val state = MapViewState().apply {
            updateViewport(400f, 400f)
            updateContentBounds(scene.contentBounds)
        }
        val hit = scene.hitTest(state, state.planeToScreenX(0.5f), state.planeToScreenY(0.5f), density = 2f, useTapZones = false)
        assertEquals("MAR", hit?.featureId)
        assertEquals(true, hit?.isMember)
    }

    // ---- Far-side context land (task 4.7 review fix): excluded() and hitTest ----

    /** A tiny square centred on ([lon], [lat]) degrees (for Lambert azimuthal distance tests). */
    private fun geoFeature(id: String, lon: Double, lat: Double): GeoFeature {
        val d = 0.5
        val lons = floatArrayOf((lon - d).toFloat(), (lon + d).toFloat(), (lon + d).toFloat(), (lon - d).toFloat())
        val lats = floatArrayOf((lat - d).toFloat(), (lat - d).toFloat(), (lat + d).toFloat(), (lat + d).toFloat())
        val schema = listOf(GeoPropertyDef("playable", GeoPropertyType.UINT8))
        return GeoFeature(
            id, GeoProperties(schema, arrayOf(1)),
            GeoBounds(lon - d, lat - d, lon + d, lat + d), lon, lat, listOf(GeoPart(0, lons, lats)),
        )
    }

    @Test
    fun `far-side context land is excluded from drawing and hit testing, members are always kept`() = runBlocking {
        // Centred on (0, 0): on the equator, angular distance from the centre equals longitude.
        val member = geoFeature("MBR", lon = 130.0, lat = 0.0) // a member, past 80 degrees too
        val close = geoFeature("CLOSE", lon = 50.0, lat = 0.0) // context, within 80 degrees
        val far = geoFeature("FAR", lon = 100.0, lat = 0.0) // context, past 80 degrees
        val layer = squareLayer(member, close, far)
        // A wide maxDistanceDeg so isVisible keeps every vertex (this is about the exclusion
        // this task adds, not about the projection's own 100 degree cull).
        val projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0, maxDistanceDeg = 175.0)

        val cache = MapPathCache()
        val land = cache.paths(layer, projection).await()
        val scene = MapScene(projection, land, null, members = setOf("MBR"))

        val excluded = scene.excluded(MapLevel.COARSE)!!
        assertFalse("member kept past 80 degrees", excluded[land.indexOf("MBR")])
        assertFalse("context within 80 degrees kept", excluded[land.indexOf("CLOSE")])
        assertTrue("context past 80 degrees dropped", excluded[land.indexOf("FAR")])

        val state = MapViewState().apply {
            updateViewport(400f, 400f)
            updateContentBounds(scene.contentBounds)
        }
        fun tap(id: String): MapHit? {
            val f = land.projected.features.first { it.id == id }
            return scene.hitTest(state, state.planeToScreenX(f.labelX), state.planeToScreenY(f.labelY), density = 2f)
        }
        assertEquals("MBR", tap("MBR")?.featureId)
        assertEquals(true, tap("MBR")?.isMember)
        assertEquals("CLOSE", tap("CLOSE")?.featureId)
        assertEquals(false, tap("CLOSE")?.isMember)
        assertNull("far-side context land isn't drawn, so there is nothing there to tap", tap("FAR"))
    }

    @Test
    fun `with no member set nothing is excluded, however far`() = runBlocking {
        val far = geoFeature("FAR", lon = 170.0, lat = 0.0)
        val layer = squareLayer(far)
        val projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0, maxDistanceDeg = 175.0)
        val cache = MapPathCache()
        val land = cache.paths(layer, projection).await()
        val scene = MapScene(projection, land, null, members = null)
        assertEquals(null, scene.excluded(MapLevel.COARSE))
    }

    @Test
    fun `content bounds are the union of both levels unless given`() = runBlocking {
        val cache = MapPathCache()
        val c = cache.paths(coarse, PlateProjection).await()
        val d = cache.paths(detail, PlateProjection).await()
        assertEquals(PlaneRect(0f, 0f, 20f, 20f), MapScene(PlateProjection, c, null).contentBounds)
        assertEquals(PlaneRect(5f, 5f, 30f, 30f), MapScene(PlateProjection, null, d).contentBounds)
        assertEquals(PlaneRect(0f, 0f, 30f, 30f), MapScene(PlateProjection, c, d).contentBounds)
        val given = PlaneRect(-1f, -1f, 1f, 1f)
        assertEquals(given, MapScene(PlateProjection, c, d, bounds = given).contentBounds)
    }

    @Test
    fun `already built paths are there on the first composition`() {
        val cache = MapPathCache()
        val built = runBlocking { cache.paths(coarse, PlateProjection).await() }
        val firstComposition = mutableListOf<MapLayerPaths?>()
        compose.setContent {
            CompositionLocalProvider(LocalMapPathCache provides cache) {
                firstComposition += rememberMapLayerPaths(coarse, PlateProjection)
            }
        }
        compose.waitForIdle()
        // Like a rotation: the screen comes back and finds its paths at once.
        assertSame(built, firstComposition.first())
    }

    /** A projection whose `project` always throws, to force [MapLayerPaths.build] to fail. */
    private object ThrowingProjection : MapProjection {
        override fun project(lonDeg: Double, latDeg: Double, out: DoubleArray): Unit =
            throw RuntimeException("boom")

        override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean = true
    }

    @Test
    fun `a failed build never crashes composition - the scene just stays null`() {
        val cache = MapPathCache()
        // Force the failure to materialise before composition, so the test isn't racing the
        // background build; either way, produceState must not rethrow it (task 9.2 review fix).
        runBlocking { runCatching { cache.paths(coarse, ThrowingProjection).await() } }
        var scene: MapScene? = null
        compose.setContent {
            CompositionLocalProvider(LocalMapPathCache provides cache) {
                scene = rememberMapScene(ThrowingProjection, coarse)
            }
        }
        // No exception thrown out of setContent/waitForIdle is itself the assertion: before the
        // fix, the failed build's exception was rethrown by produceState into composition.
        compose.waitForIdle()
        assertNull("a failed build leaves the scene null, like it's still loading", scene)
    }

    @Test
    fun `scene re-keys when the projection changes and never mixes projections`() {
        val cache = MapPathCache()
        var projection: MapProjection by mutableStateOf(PlateProjection)
        var scene: MapScene? = null
        val mixed = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalMapPathCache provides cache) {
                val s = rememberMapScene(projection, coarse, detail)
                scene = s
                if (s != null) {
                    if (s.projection != projection) mixed += "scene ${s.projection} for $projection"
                    listOfNotNull(s.coarse, s.detail).forEach {
                        if (it.projected.projection != s.projection) mixed += "paths ${it.projected.projection} in ${s.projection}"
                    }
                }
            }
        }
        compose.waitUntil(5_000) { scene?.projection == PlateProjection && scene?.detail != null }
        val plate = scene!!
        assertEquals(PlaneRect(0f, 0f, 30f, 30f), plate.contentBounds)

        compose.runOnIdle { projection = EqualEarthProjection }
        compose.waitForIdle()
        compose.waitUntil(5_000) { scene?.projection == EqualEarthProjection && scene?.detail != null }
        val ee = scene!!
        assertNotSame(plate.coarse, ee.coarse)
        assertTrue(ee.contentBounds.width < 1f) // 30 degrees is about half a radian

        assertTrue(mixed.toString(), mixed.isEmpty())
    }
}
