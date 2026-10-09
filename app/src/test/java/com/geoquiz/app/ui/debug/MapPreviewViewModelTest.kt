package com.geoquiz.app.ui.debug

import app.cash.turbine.test
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoFormatException
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.ui.map.MapLevelPreference
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapPreviewViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun fakeLayer(id: GeoLayerId, vararg features: GeoFeature) = GeoLayer(
        id, id.kind, GeoBounds(-180.0, -90.0, 180.0, 90.0), "test", emptyList(), features.toList()
    )

    /** Mainland square 110-150E, 40-10S, plus a remote island far to the east. */
    private val australia = GeoFeature(
        id = "AUS",
        properties = GeoProperties(emptyList(), emptyArray()),
        bbox = GeoBounds(110.0, -40.0, 170.0, -10.0),
        labelLon = 134.0, labelLat = -25.0,
        parts = listOf(
            GeoPart(0, floatArrayOf(110f, 150f, 150f, 110f), floatArrayOf(-40f, -40f, -10f, -10f)),
            GeoPart(GeoPart.FLAG_REMOTE, floatArrayOf(168f, 170f, 169f), floatArrayOf(-30f, -30f, -29f)),
        ),
    )

    private fun repository(failing: GeoLayerId? = null): MapRepository = mockk {
        GeoLayerId.entries.forEach { id ->
            if (id == failing) {
                coEvery { layer(id) } throws GeoFormatException(GeoFormatException.Reason.TRUNCATED, "short")
            } else {
                coEvery { layer(id) } returns fakeLayer(id, australia)
            }
        }
    }

    @Test
    fun `loads the four preview layers`() = runTest {
        val vm = MapPreviewViewModel(repository())
        val state = vm.uiState.value
        assertEquals(GeoLayerId.ADMIN0_110M, state.coarse?.id)
        assertEquals(GeoLayerId.ADMIN0_50M, state.detail?.id)
        assertEquals(GeoLayerId.LAKES_50M, state.lakes?.id)
        assertEquals(GeoLayerId.RIVERS_50M, state.rivers?.id)
        assertEquals(MapPreviewViewModel.PREVIEW_LAYERS.toSet(), state.loadMillis.keys)
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a failing layer is reported and the others still load`() = runTest {
        val vm = MapPreviewViewModel(repository(failing = GeoLayerId.LAKES_50M))
        val state = vm.uiState.value
        assertNull(state.lakes)
        assertNotNull(state.rivers)
        assertTrue(state.error!!.contains("lakes_50m.bin"))
    }

    @Test
    fun `toggles update the state`() = runTest {
        val vm = MapPreviewViewModel(repository())
        vm.uiState.test {
            val initial = awaitItem()
            assertEquals(MapLevelPreference.AUTO, initial.level)
            assertEquals(MapPreviewViewModel.DEMO_STATES, initial.featureStates)

            vm.onLevelChange(MapLevelPreference.DETAIL)
            assertEquals(MapLevelPreference.DETAIL, awaitItem().level)
            vm.onShowLakesChange(false)
            assertFalse(awaitItem().showLakes)
            vm.onShowRiversChange(false)
            assertFalse(awaitItem().showRivers)
            vm.onShowDemoStatesChange(false)
            assertTrue(awaitItem().featureStates.isEmpty())
            vm.onShowFrameRateChange(true)
            assertTrue(awaitItem().showFrameRate)
        }
    }

    @Test
    fun `demo states cover every non-default state`() {
        val used = MapPreviewViewModel.DEMO_STATES.values.toSet()
        assertEquals(MapFeatureState.entries.toSet() - MapFeatureState.Default, used)
    }

    @Test
    fun `tap records the longitude and latitude, or off the map`() = runTest {
        val vm = MapPreviewViewModel(repository())
        val p = EqualEarthProjection.project(151.2, -33.9)
        vm.onTap(p.x.toFloat(), p.y.toFloat())
        val state = vm.uiState.value
        assertTrue(state.hasTapped)
        assertEquals(151.2, state.lastTapLon!!, 1e-3)
        assertEquals(-33.9, state.lastTapLat!!, 1e-3)

        vm.onTap(2.7f, 1.3f)
        assertTrue(vm.uiState.value.hasTapped)
        assertNull(vm.uiState.value.lastTapLon)
    }

    @Test
    fun `tapping a country cycles it through the states`() = runTest {
        val vm = MapPreviewViewModel(repository())
        val hit = MapHit("AUS", isPlayable = true, viaTapZone = false)
        vm.onShowDemoStatesChange(false)
        val seen = (1..MapPreviewViewModel.STATE_CYCLE.size).map {
            vm.onFeatureTap(hit)
            vm.uiState.value.featureStates["AUS"]
        }
        assertEquals(MapPreviewViewModel.STATE_CYCLE.drop(1) + MapFeatureState.Default, seen)
        assertEquals(hit, vm.uiState.value.lastHit)
        // Tap states win over demo states.
        vm.onShowDemoStatesChange(true)
        vm.onFeatureTap(hit)
        assertEquals(MapFeatureState.Found, vm.uiState.value.featureStates["AUS"])
    }

    @Test
    fun `tapping water records a miss`() = runTest {
        val vm = MapPreviewViewModel(repository())
        vm.onFeatureTap(null)
        assertTrue(vm.uiState.value.hasHit)
        assertNull(vm.uiState.value.lastHit)
    }

    @Test
    fun `australia bounds leave out remote parts`() = runTest {
        val vm = MapPreviewViewModel(repository())
        val bounds = vm.australiaBounds()!!
        assertEquals(EqualEarthProjection.project(150.0, -10.0).x.toFloat(), bounds.maxX, 1e-5f)
        assertEquals(-EqualEarthProjection.project(110.0, -10.0).y.toFloat(), bounds.minY, 1e-5f)
    }
}
