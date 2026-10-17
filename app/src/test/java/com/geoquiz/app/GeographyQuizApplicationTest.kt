package com.geoquiz.app

import android.content.ComponentCallbacks2
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.ui.map.CountryOutlinePathCache
import com.geoquiz.app.ui.map.MapPathCache
import com.geoquiz.app.ui.map.PlateProjection
import com.geoquiz.app.ui.map.squareFeature
import com.geoquiz.app.ui.map.squareLayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `onTrimMemory`/`onLowMemory` wiring to the two process-wide map path caches (task 9.2 review
 * fix 2). Robolectric: the manifest declares `android:name=".GeographyQuizApplication"`, so
 * [ApplicationProvider] already hands back the real application under test, with its own
 * `onCreate` (Play Games init) having already run - no Hilt test rule needed for this, since
 * nothing here touches an injected field.
 */
@RunWith(RobolectricTestRunner::class)
class GeographyQuizApplicationTest {

    private val app: GeographyQuizApplication
        get() = ApplicationProvider.getApplicationContext()

    private fun outlineModel(cca3: String) = OutlineModel(
        cca3 = cca3,
        includeRemote = false,
        projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0),
        rings = emptyList(),
        neighbours = emptyList(),
        frame = PlaneRect(-1f, -1f, 1f, 1f),
        scale = 1f,
    )

    @Test
    fun `onTrimMemory at UI_HIDDEN or above clears the map path cache`() = runBlocking {
        val layer = squareLayer(squareFeature("FRA", 0f, 1f))
        val first = MapPathCache.Shared.paths(layer, PlateProjection)
        first.await()

        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)

        assertNotSame(
            "a cleared cache rebuilds on the next request",
            first,
            MapPathCache.Shared.paths(layer, PlateProjection),
        )
    }

    @Test
    fun `onTrimMemory below UI_HIDDEN leaves the map path cache alone`() = runBlocking {
        val layer = squareLayer(squareFeature("DEU", 0f, 1f))
        val first = MapPathCache.Shared.paths(layer, PlateProjection)
        first.await()

        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)

        assertSame(
            "still visible (just under memory pressure): no need to drop built paths yet",
            first,
            MapPathCache.Shared.paths(layer, PlateProjection),
        )
    }

    @Test
    fun `onTrimMemory at UI_HIDDEN or above clears the country outline cache`() = runBlocking {
        val model = outlineModel("ITA")
        val first = CountryOutlinePathCache.Shared.paths(model, showNeighbours = false)
        first.await()

        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)

        assertNotSame(
            "a cleared cache rebuilds on the next request",
            first,
            CountryOutlinePathCache.Shared.paths(model, showNeighbours = false),
        )
    }

    @Test
    fun `onLowMemory clears both shared path caches`() = runBlocking {
        val layer = squareLayer(squareFeature("ESP", 0f, 1f))
        val firstMap = MapPathCache.Shared.paths(layer, PlateProjection)
        firstMap.await()
        val model = outlineModel("ESP")
        val firstOutline = CountryOutlinePathCache.Shared.paths(model, showNeighbours = false)
        firstOutline.await()

        app.onLowMemory()

        assertNotSame(firstMap, MapPathCache.Shared.paths(layer, PlateProjection))
        assertNotSame(firstOutline, CountryOutlinePathCache.Shared.paths(model, showNeighbours = false))
    }
}
