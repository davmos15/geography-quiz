package com.geoquiz.app.ui.map

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.OutlineNeighbour
import com.geoquiz.app.domain.map.OutlineRing
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.geoquiz.app.ui.theme.LightGeoColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Task 4.5's drawing side, offscreen (Robolectric native graphics; `captureToImage` is slow in
 * this setup, see `MapCanvasUiTest`, so this draws [drawCountryOutline] onto a fixed bitmap
 * directly, the same technique as `MapCanvasUiTest.renderFrame`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CountryOutlineRenderTest {

    @get:Rule
    val compose = createComposeRule()

    /** A square ring from ([min], [min]) to ([max], [max]) in box units, counter-clockwise. */
    private fun squareRing(min: Float, max: Float, isHole: Boolean = false) = OutlineRing(
        floatArrayOf(min, min, max, min, max, max, min, max), isHole,
    )

    private fun outline(neighbours: List<OutlineNeighbour> = emptyList()) = OutlineModel(
        cca3 = "TST",
        includeRemote = false,
        projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0),
        rings = listOf(squareRing(-0.4f, 0.4f)),
        neighbours = neighbours,
        frame = PlaneRect(-1f, -1f, 1f, 1f),
        scale = 1f,
    )

    private fun render(
        model: OutlineModel,
        showNeighbours: Boolean = true,
        width: Int = 200,
        height: Int = 200,
        density: Float = 2f,
    ): PixelMap {
        val paths = CountryOutlinePaths.build(model, showNeighbours)
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
            drawCountryOutline(paths, showNeighbours, density, LightGeoColors.mapLand, LightGeoColors.mapLandBorder, LightGeoColors.mapLandInactive)
        }
        return bitmap.toPixelMap()
    }

    private fun close(a: Int, b: Int, tolerance: Int = 4): Boolean =
        listOf(24, 16, 8, 0).all { abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) <= tolerance }

    @Test
    fun `the silhouette fills the middle in the land colour`() {
        val pixels = render(outline())
        val centre = pixels[100, 100].toArgb()
        assertTrue(close(centre, LightGeoColors.mapLand.toArgb()))
    }

    @Test
    fun `nothing is drawn in the padding outside the fitted box`() {
        // Padding is 6% of the shorter side (OUTLINE_PADDING_FRACTION); the very corner of the
        // canvas is well outside the box whatever a silhouette's shape, including the small
        // overshoot OutlineGeometry allows on the long axis of an elongated country.
        val pixels = render(outline())
        val corner = pixels[2, 2].toArgb()
        // Untouched: still the bitmap's initial colour (transparent black), not land, border or
        // neighbour fill.
        assertTrue(!close(corner, LightGeoColors.mapLand.toArgb()))
        assertTrue(!close(corner, LightGeoColors.mapLandBorder.toArgb()))
        assertTrue(!close(corner, LightGeoColors.mapLandInactive.toArgb()))
        assertTrue("corner should stay transparent", (corner ushr 24) and 0xFF < 10)
    }

    @Test
    fun `overshoot past the fitted box is clipped at the box edge, not the whole canvas`() {
        // Ring spans -0.55..0.55 in box units: past the nominal 0.5 box edge (the small overshoot
        // OutlineGeometry allows on the long axis of an elongated country) but, scaled up, still
        // short of the canvas's own edge. 10.5's fix clips at the padded box, not the canvas, so
        // a pixel in the padding strip beyond the box (but inside the overshoot) must stay empty.
        val overshoot = outline().let {
            OutlineModel(it.cca3, it.includeRemote, it.projection, listOf(squareRing(-0.55f, 0.55f)), it.neighbours, it.frame, it.scale)
        }
        val pixels = render(overshoot)
        // Canvas is 200x200, padding is 6% of 200 = 12px, so the box spans pixel 12..188. A ring
        // corner at 0.55 * 176 (box size) = 96.8px from the 100,100 centre lands at pixel ~196.8,
        // well past the box edge (188) but still inside the 200px canvas.
        val pastBoxEdge = pixels[193, 100].toArgb()
        assertTrue("past the box edge should stay transparent, not show the overshoot", (pastBoxEdge ushr 24) and 0xFF < 10)
    }

    @Test
    fun `a border is drawn round the silhouette`() {
        val pixels = render(outline())
        // The box fills the canvas (minus padding); the own ring spans -0.4..0.4 of a nominally
        // 1-unit box, so its left edge sits a little in from the padded box's own left edge.
        // Scan a horizontal line for the border colour.
        val row = (0 until 200).map { pixels[it, 100].toArgb() }
        assertTrue("border colour appears somewhere on the scan line", row.any { close(it, LightGeoColors.mapLandBorder.toArgb()) })
    }

    @Test
    fun `neighbours draw faintly when shown and not at all when hidden`() {
        val neighbour = OutlineNeighbour("NXT", listOf(squareRing(0.42f, 0.48f)))
        val shown = render(outline(listOf(neighbour)), showNeighbours = true)
        val hidden = render(outline(listOf(neighbour)), showNeighbours = false)

        // Box units are scaled by the full box size (176 px here: 200 px canvas minus 6%
        // padding each side), so 0.42..0.48 lands at pixel 173..184, just past the silhouette's
        // own edge (it spans -0.4..0.4, pixel 30..170) and still well inside the 200 px canvas.
        val shownPatch = (165 until 195).flatMap { x -> (165 until 195).map { y -> shown[x, y].toArgb() } }
        val hiddenPatch = (165 until 195).flatMap { x -> (165 until 195).map { y -> hidden[x, y].toArgb() } }
        assertTrue("neighbour fill appears when shown", shownPatch.any { close(it, LightGeoColors.mapLandInactive.toArgb()) })
        assertTrue("no neighbour fill when hidden", hiddenPatch.none { close(it, LightGeoColors.mapLandInactive.toArgb()) })
    }

    @Test
    fun `a custom fill colour overrides the land token`() {
        val paths = CountryOutlinePaths.build(outline(), showNeighbours = false)
        val bitmap = ImageBitmap(200, 200)
        CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(bitmap), Size(200f, 200f)) {
            drawCountryOutline(paths, showNeighbours = false, density = 2f, land = Color.Red, border = LightGeoColors.mapLandBorder, neighbourLand = LightGeoColors.mapLandInactive)
        }
        val pixels = bitmap.toPixelMap()
        assertTrue(close(pixels[100, 100].toArgb(), Color.Red.toArgb()))
    }

    // ---- The composable itself: decorative by default, never leaks the answer unasked ----

    @Test
    fun `with no content description the composable is marked decorative`() {
        compose.setContent {
            GeographyQuizTheme {
                CountryOutline(outline(), modifier = Modifier.size(100.dp).testTag(TAG))
            }
        }
        val node = compose.onNodeWithTag(TAG).fetchSemanticsNode()
        assertFalse(node.config.contains(SemanticsProperties.ContentDescription))
        assertTrue(node.config.contains(SemanticsProperties.InvisibleToUser))
    }

    @Test
    fun `a supplied content description is used and the node is not decorative`() {
        compose.setContent {
            GeographyQuizTheme {
                CountryOutline(outline(), modifier = Modifier.size(100.dp).testTag(TAG), contentDescription = "A country silhouette")
            }
        }
        val node = compose.onNodeWithTag(TAG).fetchSemanticsNode()
        assertEquals(listOf("A country silhouette"), node.config[SemanticsProperties.ContentDescription])
        assertFalse(node.config.contains(SemanticsProperties.InvisibleToUser))
    }

    // ---- CountryOutlinePathCache: keyed on whether the model actually has neighbours (4.6/2) ----

    @Test
    fun `same country drawn first without neighbours then with shows neighbours`() {
        val cache = CountryOutlinePathCache()
        val neighbour = OutlineNeighbour("NXT", listOf(squareRing(0.42f, 0.48f)))

        val withoutNeighbours = outline(neighbours = emptyList())
        val firstPaths = kotlinx.coroutines.runBlocking { cache.paths(withoutNeighbours, showNeighbours = true).await() }
        assertTrue("first draw has no neighbours to show", firstPaths.neighbourPaths.isEmpty())

        val withNeighbours = outline(neighbours = listOf(neighbour))
        val secondPaths = kotlinx.coroutines.runBlocking { cache.paths(withNeighbours, showNeighbours = true).await() }
        assertEquals("second draw's model has a neighbour, so the cache must not reuse the first (neighbour-less) entry", 1, secondPaths.neighbourPaths.size)
    }

    @Test
    fun `clear while a build is in flight neither crashes nor resurrects the discarded entry`() = kotlinx.coroutines.runBlocking {
        val cache = CountryOutlinePathCache()
        val model = outline()
        val inFlight = cache.paths(model, showNeighbours = true)
        // See MapPathCache's equivalent test and CountryOutlinePathCache.clear's doc: paths()
        // and clear() share one monitor, and a build never writes its result back into the map
        // on completion, so clear() can only drop the map entry, never resurrect it afterwards.
        cache.clear()
        inFlight.await()
        assertNotSame(
            "the cleared key is gone: the next request is a fresh build, not the discarded one",
            inFlight,
            cache.paths(model, showNeighbours = true)
        )
    }

    // ---- A failed build never crashes composition (task 9.2 review fix) ----

    /** An odd-length points array: [buildOutlinePath]'s loop reads one past the end and throws. */
    private fun malformedOutline() = outline().let {
        OutlineModel(
            it.cca3, it.includeRemote, it.projection,
            listOf(OutlineRing(floatArrayOf(0f, 0f, 1f, 0f, 1f), isHole = false)),
            it.neighbours, it.frame, it.scale,
        )
    }

    @Test
    fun `a failed path build never crashes composition - CountryOutline just draws nothing`() {
        val cache = CountryOutlinePathCache()
        val model = malformedOutline()
        // Force the failure to materialise before composition, so the test isn't racing the
        // background build; either way, produceState must not rethrow it (task 9.2 review fix).
        kotlinx.coroutines.runBlocking {
            runCatching { cache.paths(model, showNeighbours = true).await() }
        }
        compose.setContent {
            CompositionLocalProvider(LocalCountryOutlinePathCache provides cache) {
                CountryOutline(outline = model, modifier = Modifier.size(100.dp))
            }
        }
        // No exception thrown out of setContent/waitForIdle is the assertion: before the fix,
        // the failed build's exception was rethrown by produceState into composition.
        compose.waitForIdle()
    }

    private companion object {
        const val TAG = "outline"
    }
}
