package com.geoquiz.app.ui.tapmap

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.RegionPreset
import com.geoquiz.app.ui.map.squareFeature
import com.geoquiz.app.ui.map.squareLayer
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [loadTapMapMapState] and [withMinimumFrameSize] (task 5.2b): shared by Find
 * ([TapMapGameViewModel]) and Name all ([TapMapNameAllViewModel]). World is only ever framed for
 * a practice set; every other preset is always framed, with a floor on its size;
 * [TapMapMapState.loadFailed] is set only once every layer fails to load.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TapMapMapLoaderTest {

    // Roughly 0-1 degrees east, 0-1 south: a real, valid coordinate, far from anywhere in
    // particular, which is all this needs.
    private val franceLayer = squareLayer(squareFeature("FRA", 0f, 1f), id = GeoLayerId.ADMIN0_50M)

    private fun repository(detailLayer: GeoLayer? = franceLayer) = mockk<MapRepository> {
        coEvery { layer(GeoLayerId.ADMIN0_110M) } throws IllegalStateException("not needed in this test")
        if (detailLayer != null) {
            coEvery { layer(GeoLayerId.ADMIN0_50M) } returns detailLayer
        } else {
            coEvery { layer(GeoLayerId.ADMIN0_50M) } throws IllegalStateException("not needed in this test")
        }
        coEvery { layer(GeoLayerId.TAP_ZONES) } throws IllegalStateException("not needed in this test")
    }

    @Test
    fun `a rect smaller than the minimum grows about its own centre`() {
        val rect = PlaneRect(0f, 0f, 0.1f, 0.1f)

        val grown = rect.withMinimumFrameSize(1f, 2f)

        assertEquals(1f, grown.width, 1e-6f)
        assertEquals(2f, grown.height, 1e-6f)
        assertEquals(rect.centreX, grown.centreX, 1e-6f)
        assertEquals(rect.centreY, grown.centreY, 1e-6f)
    }

    @Test
    fun `a rect already bigger than the minimum is left alone`() {
        val rect = PlaneRect(0f, 0f, 10f, 10f)

        assertEquals(rect, rect.withMinimumFrameSize(1f, 2f))
    }

    @Test
    fun `World is not framed unless the round is a practice set`() = runTest {
        val mapRepository = repository()

        val notPractice = loadTapMapMapState(
            mapRepository = mapRepository,
            defaultDispatcher = Dispatchers.Unconfined,
            preset = RegionPreset.WORLD,
            members = setOf("FRA"),
            isPractice = false
        )
        assertNull(notPractice.frame)

        val practice = loadTapMapMapState(
            mapRepository = mapRepository,
            defaultDispatcher = Dispatchers.Unconfined,
            preset = RegionPreset.WORLD,
            members = setOf("FRA"),
            isPractice = true
        )
        assertNotNull(practice.frame)
    }

    @Test
    fun `a continent preset is always framed, practice or not`() = runTest {
        val mapRepository = repository()

        val state = loadTapMapMapState(
            mapRepository = mapRepository,
            defaultDispatcher = Dispatchers.Unconfined,
            preset = RegionPreset.EUROPE,
            members = setOf("FRA"),
            isPractice = false
        )

        assertNotNull(state.frame)
    }

    @Test
    fun `loadFailed is true only once every layer fails`() = runTest {
        val bothFail = repository(detailLayer = null)

        val failedState = loadTapMapMapState(
            mapRepository = bothFail,
            defaultDispatcher = Dispatchers.Unconfined,
            preset = RegionPreset.WORLD,
            members = emptySet(),
            isPractice = false
        )

        assertTrue(failedState.loadFailed)
        assertNull(failedState.coarse)
        assertNull(failedState.detail)
    }

    @Test
    fun `loadFailed is false once any one layer succeeds`() = runTest {
        val oneSucceeds = repository()

        val state = loadTapMapMapState(
            mapRepository = oneSucceeds,
            defaultDispatcher = Dispatchers.Unconfined,
            preset = RegionPreset.WORLD,
            members = setOf("FRA"),
            isPractice = false
        )

        assertFalse(state.loadFailed)
        assertNotNull(state.detail)
    }
}
