package com.geoquiz.app.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.data.geo.AndroidGeoAssetSource
import com.geoquiz.app.data.geo.AssetMapRepository
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.ui.theme.DarkGeoColors
import com.geoquiz.app.ui.theme.GeoColors
import com.geoquiz.app.ui.theme.LightGeoColors
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.geoquiz.app.ui.theme.geoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * MapCanvas with the real world layer: it renders, applies feature states (checked on the
 * pixels of an offscreen frame), reports taps in projected coordinates and zooms on gestures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCanvasUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = AssetMapRepository(AndroidGeoAssetSource(context.assets), Dispatchers.IO)
    private val coarse: GeoLayer by lazy { runBlocking { repository.layer(GeoLayerId.ADMIN0_110M) } }

    private fun showMap(
        mapState: MapViewState,
        featureStates: Map<String, MapFeatureState> = emptyMap(),
        darkTheme: Boolean = false,
        onTap: ((Float, Float) -> Unit)? = null,
        onFeatureTap: ((MapHit?) -> Unit)? = null,
        doubleTapToZoom: Boolean = true,
    ): GeoColors {
        val layer = coarse
        var colours: GeoColors? = null
        compose.setContent {
            GeographyQuizTheme(darkTheme = darkTheme) {
                colours = MaterialTheme.geoColors
                val scene = rememberMapScene(EqualEarthProjection, layer)
                Box(Modifier.size(360.dp, 200.dp)) {
                    if (scene != null) {
                        MapCanvas(
                            scene = scene,
                            state = mapState,
                            modifier = Modifier.fillMaxSize().testTag(TAG),
                            featureStates = featureStates,
                            onTapProjected = onTap,
                            onFeatureTap = onFeatureTap,
                            doubleTapToZoom = doubleTapToZoom,
                        )
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) { compose.onAllNodesWithTag(TAG).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue(mapState.isReady)
        return colours!!
    }

    /** Screen position of a feature's label point. */
    private fun labelOnScreen(state: MapViewState, id: String): Offset {
        val f = coarse.feature(id)!!
        val p = EqualEarthProjection.project(f.labelLon, f.labelLat)
        return Offset(state.planeToScreenX(p.x.toFloat()), state.planeToScreenY((-p.y).toFloat()))
    }

    private fun PixelMap.at(p: Offset): Color = this[p.x.toInt(), p.y.toInt()]

    private fun assertColour(expected: Color, actual: Color) {
        val e = expected.toArgb()
        val a = actual.toArgb()
        assertTrue("expected #${Integer.toHexString(e)} got #${Integer.toHexString(a)}", close(e, a))
    }

    @Test
    fun `renders the world layer and counts non-default states`() {
        val state = MapViewState()
        showMap(
            state,
            mapOf("AUS" to MapFeatureState.Found, "BRA" to MapFeatureState.Wrong, "FRA" to MapFeatureState.Default),
        )
        compose.onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(MapFeatureStateCountKey, 2))
        assertEquals(1f, state.zoom)
        // The world fits the 360 dp width.
        assertEquals(360f, state.contentBounds!!.width * state.scale, 1f)
    }

    /**
     * Draws one frame offscreen with the canvas's own draw function (Robolectric native
     * graphics); `captureToImage` times out in this setup. Outlines are black and marker discs
     * white so the tests can tell them from the fills.
     */
    private fun renderFrame(
        scene: MapScene,
        featureStates: Map<String, MapFeatureState>,
        geo: GeoColors,
        width: Int = 360,
        height: Int = 200,
        density: Float = 1f,
        showTapZones: Boolean = false,
    ): Pair<PixelMap, MapViewState> {
        val state = MapViewState().apply {
            updateViewport(width.toFloat(), height.toFloat())
            updateContentBounds(scene.contentBounds)
        }
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawMap(
                scene, state, featureStates, MapLevelPreference.AUTO, showLakes = true, showRivers = true,
                palette = MapPalette.from(geo, OUTLINE, MARKER_BACKGROUND), cache = MapDrawCache(),
                showTapZones = showTapZones,
            )
        }
        return bitmap.toPixelMap() to state
    }

    private val tapLayer: GeoLayer by lazy { runBlocking { repository.layer(GeoLayerId.TAP_ZONES) } }

    private fun worldScene(withZones: Boolean = false) = MapScene(
        EqualEarthProjection, MapLayerPaths.build(coarse, EqualEarthProjection), detail = null,
        tapZones = if (withZones) MapLayerPaths.build(tapLayer, EqualEarthProjection) else null,
    )

    @Test
    fun `fills follow the state in the light theme`() {
        val (pixels, state) = renderFrame(
            worldScene(),
            mapOf("AUS" to MapFeatureState.Found, "BRA" to MapFeatureState.Highlighted, "IND" to MapFeatureState.Start),
            LightGeoColors,
        )
        // Away from the label point, where Found and Start draw their markers.
        assertColour(LightGeoColors.mapFound, pixels.at(screenOf(state, 122.0, -26.0)))
        assertColour(LightGeoColors.mapHighlight, pixels.at(labelOnScreen(state, "BRA")))
        assertColour(LightGeoColors.mapStart, pixels.at(screenOf(state, 77.5, 15.0)))
        assertColour(LightGeoColors.mapLand, pixels.at(labelOnScreen(state, "KAZ")))
        // Greenland is land but not one of the app's countries.
        assertColour(LightGeoColors.mapLandInactive, pixels.at(labelOnScreen(state, "GRL")))
        // Mid South Pacific is water.
        assertColour(LightGeoColors.mapWater, pixels.at(screenOf(state, -130.0, -30.0)))
    }

    @Test
    fun `fills follow the state in the dark theme`() {
        val (pixels, state) = renderFrame(worldScene(), mapOf("AUS" to MapFeatureState.Found, "CHN" to MapFeatureState.End), DarkGeoColors)
        assertColour(DarkGeoColors.mapFound, pixels.at(screenOf(state, 122.0, -26.0)))
        assertColour(DarkGeoColors.mapEnd, pixels.at(screenOf(state, 90.0, 33.0)))
        assertColour(DarkGeoColors.mapLand, pixels.at(labelOnScreen(state, "KAZ")))
        assertColour(DarkGeoColors.mapLandInactive, pixels.at(labelOnScreen(state, "GRL")))
        assertColour(DarkGeoColors.mapWater, pixels.at(screenOf(state, -130.0, -30.0)))
    }

    @Test
    fun `wrong is cross-hatched, not just a different colour`() {
        val (pixels, state) = renderFrame(worldScene(), mapOf("RUS" to MapFeatureState.Wrong), LightGeoColors)
        // West Siberia, clear of the marker on the label point.
        val at = screenOf(state, 70.0, 62.0)
        val colours = patch(pixels, at, 6)
        assertTrue(colours.any { close(it, LightGeoColors.mapWrong.toArgb()) })
        assertTrue(colours.any { close(it, LightGeoColors.mapLand.toArgb()) })
    }

    // ---- Outlines and markers on a synthetic square (plane 25..75 at 2 px per unit, density 2) ----

    /**
     * 200 x 200 px at density 2 (so 1 dp = 2 px) over plane 0..100: the square "BIG" spans
     * screen 50..150 with its label at (100, 100); "TINY" is 8 px (4 dp) across at (174, 174).
     */
    private fun squareFrame(
        big: MapFeatureState,
        tiny: MapFeatureState = MapFeatureState.Default,
        showTapZones: Boolean = false,
    ): PixelMap {
        val layer = squareLayer(squareFeature("BIG", 25f, 75f), squareFeature("TINY", 85f, 89f))
        val scene = MapScene(PlateProjection, MapLayerPaths.build(layer, PlateProjection), detail = null,
            bounds = com.geoquiz.app.domain.map.PlaneRect(0f, 0f, 100f, 100f))
        val (pixels, state) = renderFrame(scene, mapOf("BIG" to big, "TINY" to tiny), LightGeoColors, 200, 200, density = 2f, showTapZones = showTapZones)
        assertEquals(2f, state.scale, 1e-4f)
        return pixels
    }

    private val black = OUTLINE.toArgb()
    private val white = MARKER_BACKGROUND.toArgb()

    /** Pixel colours in a square of half-size [r] round [at]. */
    private fun patch(pixels: PixelMap, at: Offset, r: Int): Set<Int> = buildSet {
        for (dx in -r until r) for (dy in -r until r) add(pixels[at.x.toInt() + dx, at.y.toInt() + dy].toArgb())
    }

    @Test
    fun `found has a medium outline and a blue tick marker`() {
        val px = squareFrame(MapFeatureState.Found)
        // The 3 px (1.5 dp) outline on the left edge (x = 50) covers 48.5..51.5 only.
        assertTrue(close(px[50, 80].toArgb(), black))
        assertColour(LightGeoColors.mapFound, px[53, 80])
        val marker = patch(px, Offset(100f, 100f), 14)
        assertTrue("disc", marker.any { close(it, white) })
        assertTrue("rim", marker.any { close(it, black) })
        assertTrue("tick in the correct colour", marker.any { close(it, LightGeoColors.correct.toArgb()) })
        assertTrue("no cross colour", marker.none { close(it, LightGeoColors.wrong.toArgb()) })
    }

    @Test
    fun `wrong has a cross marker in the wrong colour`() {
        val px = squareFrame(MapFeatureState.Wrong)
        val marker = patch(px, Offset(100f, 100f), 14)
        assertTrue(marker.any { close(it, LightGeoColors.wrong.toArgb()) })
        assertTrue(marker.none { close(it, LightGeoColors.correct.toArgb()) })
        assertTrue(close(px[50, 80].toArgb(), black))
    }

    @Test
    fun `highlighted has a thick outline and no marker`() {
        val px = squareFrame(MapFeatureState.Highlighted)
        // The 6 px (3 dp) outline covers 47..53, so pixel 52 is outline too.
        assertTrue(close(px[52, 80].toArgb(), black))
        assertColour(LightGeoColors.mapHighlight, px[100, 100])
        assertTrue(patch(px, Offset(100f, 100f), 14).none { close(it, white) })
        // Solid: every pixel down the edge is outline.
        assertTrue((55..145).all { close(px[50, it].toArgb(), black) })
    }

    @Test
    fun `start has a thick outline and a filled dot marker`() {
        val px = squareFrame(MapFeatureState.Start)
        assertTrue(close(px[52, 80].toArgb(), black))
        assertTrue("dot at the centre", close(px[100, 100].toArgb(), black))
        assertTrue("disc round the dot", close(px[100 + 10, 100].toArgb(), white))
    }

    @Test
    fun `end has a dashed outline and a ring marker`() {
        val px = squareFrame(MapFeatureState.End)
        assertTrue("hollow centre", close(px[100, 100].toArgb(), white))
        assertTrue("ring", close(px[100 + 6, 100].toArgb(), black))
        // Dashed: down the edge some pixels are outline and some are not.
        val edge = (55..145).map { close(px[51, it].toArgb(), black) }
        assertTrue(edge.any { it })
        assertTrue(edge.any { !it })
    }

    @Test
    fun `features under 12 dp on screen get no label marker, but their zone has one`() {
        val px = squareFrame(MapFeatureState.Default, tiny = MapFeatureState.Found)
        assertColour(LightGeoColors.mapFound, px[174, 174])
        assertTrue(patch(px, Offset(174f, 174f), 3).none { close(it, white) })
        // With tap zones the 4 dp square gets a zone, and the tick sits on the zone centre.
        val zoned = squareFrame(MapFeatureState.Default, tiny = MapFeatureState.Found, showTapZones = true)
        val marker = patch(zoned, Offset(174f, 174f), 14)
        assertTrue(marker.any { close(it, white) })
        assertTrue(marker.any { close(it, LightGeoColors.correct.toArgb()) })
        // The big square (50 dp) has no zone: its centre is still plain fill.
        assertColour(LightGeoColors.mapLand, zoned[100, 120])
    }

    @Test
    fun `hatch lines are anchored to the plane grid`() {
        assertEquals(1.0f, hatchGridStart(1.3f, 0.5f), 1e-6f)
        assertEquals(-1.5f, hatchGridStart(-1.3f, 0.5f), 1e-6f)
        assertEquals(2.0f, hatchGridStart(2.0f, 0.5f), 1e-6f)
        // Panning moves the visible window, not the grid: two windows share their lines.
        val spacing = 0.25f
        val a = hatchGridStart(0.1f, spacing)
        val b = hatchGridStart(0.6f, spacing)
        assertEquals(0f, ((b - a) / spacing) % 1f, 1e-5f)
    }

    private fun close(a: Int, b: Int, tolerance: Int = 3): Boolean =
        listOf(16, 8, 0).all { abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) <= tolerance }

    private fun screenOf(state: MapViewState, lon: Double, lat: Double): Offset {
        val p = EqualEarthProjection.project(lon, lat)
        return Offset(state.planeToScreenX(p.x.toFloat()), state.planeToScreenY((-p.y).toFloat()))
    }

    @Test
    fun `without double-tap zoom a tap reports the country at once`() {
        val state = MapViewState()
        val hits = mutableListOf<MapHit?>()
        showMap(state, onFeatureTap = { hits += it }, doubleTapToZoom = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(TAG).performTouchInput { click(labelOnScreen(state, "AUS")) }
        // No double-tap timeout to wait for: one frame is enough.
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals("AUS", hits.single()?.featureId)
        compose.onNodeWithTag(TAG).performTouchInput { click(Offset(state.planeToScreenX(-2.0f), state.planeToScreenY(0.9f))) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(2, hits.size)
        assertEquals("South Pacific is water", null, hits[1])
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `tap zones are drawn as dashed circles and take the country state`() {
        val scene = worldScene(withZones = true)
        val (pixels, state) = renderFrame(
            scene, mapOf("MUS" to MapFeatureState.Found), LightGeoColors, width = 720, height = 400, density = 2f,
            showTapZones = true,
        )
        val zones = scene.zones(MapLevel.COARSE)!!
        fun centreOf(id: String): Offset {
            val z = zones.indexOf(id)
            assertTrue("$id has a zone at world fit", zones.visibleAt(z, state.scale, 2f))
            return Offset(state.planeToScreenX(zones.centreX[z]), state.planeToScreenY(zones.centreY[z]))
        }
        // Seychelles (Default): the dashed rim (radius 22 dp = 44 px) uses mapTapZoneOutline, and
        // only part of the circle is drawn.
        val syc = centreOf("SYC")
        val rim = (0 until 72).map { k ->
            val a = k * Math.PI * 2 / 72
            pixels[(syc.x + 44 * Math.cos(a)).toInt(), (syc.y + 44 * Math.sin(a)).toInt()].toArgb()
        }
        assertTrue("dashes", rim.any { close(it, LightGeoColors.mapTapZoneOutline.toArgb(), 60) })
        assertTrue("gaps", rim.any { close(it, LightGeoColors.mapWater.toArgb()) })
        // Mauritius (Found): tick marker at the centre and a found tint inside the circle.
        val mus = centreOf("MUS")
        val marker = patch(pixels, mus, 14)
        assertTrue(marker.any { close(it, LightGeoColors.correct.toArgb()) })
        assertTrue(marker.any { close(it, MARKER_BACKGROUND.toArgb()) })
        val tinted = pixels[(mus.x + 30).toInt(), mus.y.toInt()].toArgb()
        assertTrue("tinted, not plain water", !close(tinted, LightGeoColors.mapWater.toArgb()))
    }

    @Test
    fun `a tap reports the projected point under the finger`() {
        val state = MapViewState()
        var tapped: Pair<Float, Float>? = null
        showMap(state, onTap = { x, y -> tapped = x to y })
        val at = labelOnScreen(state, "AUS")
        compose.onNodeWithTag(TAG).performTouchInput { click(at) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        val (x, y) = tapped ?: error("No tap reported")
        val lonLat = EqualEarthProjection.inverse(x.toDouble(), y.toDouble())!!
        val aus = coarse.feature("AUS")!!
        assertEquals(aus.labelLon, lonLat.x, 1.0)
        assertEquals(aus.labelLat, lonLat.y, 1.0)
    }

    @Test
    fun `double tap zooms in by two`() {
        val state = MapViewState()
        showMap(state)
        compose.onNodeWithTag(TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(2f, state.zoom, 1e-3f)
    }

    @Test
    fun `pinch out zooms in`() {
        val state = MapViewState()
        showMap(state)
        compose.onNodeWithTag(TAG).performTouchInput {
            pinch(
                start0 = center - Offset(10f, 0f), end0 = center - Offset(120f, 0f),
                start1 = center + Offset(10f, 0f), end1 = center + Offset(120f, 0f),
            )
        }
        compose.waitForIdle()
        assertTrue("zoom ${state.zoom}", state.zoom > 2f)
    }

    @Test
    fun `path build timing for the world layers`() {
        // Robolectric's native Path; a device check of pan/zoom frame rate is still needed.
        val detail = runBlocking { repository.layer(GeoLayerId.ADMIN0_50M) }
        repeat(2) { MapLayerPaths.build(coarse, EqualEarthProjection) } // warm up
        val coarseMs = MapLayerPaths.build(coarse, EqualEarthProjection).buildMillis
        val detailMs = MapLayerPaths.build(detail, EqualEarthProjection).buildMillis
        println("MapLayerPaths.build (Robolectric native): admin0_110m $coarseMs ms, admin0_50m $detailMs ms")
        if (System.getProperty("geoquiz.perf") == "true") assertTrue(coarseMs < 5_000 && detailMs < 10_000)
    }

    private companion object {
        const val TAG = "map"
        val OUTLINE = Color.Black
        val MARKER_BACKGROUND = Color.White
    }
}
