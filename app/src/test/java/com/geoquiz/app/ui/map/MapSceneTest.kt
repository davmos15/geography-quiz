package com.geoquiz.app.ui.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.PlaneRect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
