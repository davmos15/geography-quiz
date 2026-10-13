package com.geoquiz.app.domain.map

import com.geoquiz.app.data.geo.GeoLayerReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-JVM tests for task 4.5 on the real `admin0_50m` asset: every one of the 197 playable
 * countries produces a finite, consistently sized silhouette, wrap countries stay contiguous,
 * remote parts are left out by default and neighbours come from bounding-box overlap.
 */
class OutlineGeometryTest {

    private val layer: GeoLayer by lazy { GeoLayerReader.read(File("src/main/assets/geo", GeoLayerId.ADMIN0_50M.fileName).readBytes()) }
    private val playableCodes: List<String> by lazy { layer.features.filter { it.isPlayable }.map { it.id } }

    // ---- sizeFor: the sizing rule in isolation ----

    @Test
    fun `a square country fits its longer side without the area clamp`() {
        val (scale, frame) = OutlineGeometry.sizeFor(PlaneRect(-1f, -1f, 1f, 1f))
        // Longer side (2) times scale should land on FIT_FRACTION of the 1x1 box.
        assertEquals(OutlineGeometry.FIT_FRACTION, 2f * scale, 1e-4f)
        // Frame is centred on the bbox and exactly 1 / scale wide and tall (BOX_HALF_EXTENT is
        // 0.5, so the frame spans +-0.5 / scale on each axis).
        assertEquals(0f, frame.centreX, 1e-4f)
        assertEquals(0f, frame.centreY, 1e-4f)
        assertEquals(1f / scale, frame.width, 1e-4f)
        assertEquals(1f / scale, frame.height, 1e-4f)
    }

    @Test
    fun `an elongated country is widened by the area clamp`() {
        // 20 long, 0.4 wide: fitting the long side alone would give an area far under the minimum.
        val bbox = PlaneRect(-0.2f, -10f, 0.2f, 10f)
        val (scale, _) = OutlineGeometry.sizeFor(bbox)
        val fitScale = OutlineGeometry.FIT_FRACTION / 20f
        assertTrue("clamp should scale up more than the plain fit", scale > fitScale)
        val areaInBox = (bbox.width * scale) * (bbox.height * scale)
        assertEquals(OutlineGeometry.MIN_AREA_FRACTION, areaInBox, 1e-3f)
    }

    @Test
    fun `sizeFor never divides by zero on a degenerate bbox`() {
        val (scale, frame) = OutlineGeometry.sizeFor(PlaneRect(0f, -1f, 0f, 1f))
        assertTrue(scale.isFinite() && scale > 0f)
        assertTrue(frame.width.isFinite() && frame.height.isFinite())
    }

    // ---- mainTerritoryCentre: the antimeridian-aware centre ----

    @Test
    fun `main territory centre ignores a wrap part on the far side`() {
        // A Russia-like shape: a big main part near 100E and a small wrap part just past 180
        // (stored as -175E, continuing the same landmass across the antimeridian).
        val main = GeoPart(0, floatArrayOf(60f, 140f, 140f, 60f), floatArrayOf(40f, 40f, 70f, 70f))
        val wrap = GeoPart(GeoPart.FLAG_WRAP, floatArrayOf(-179f, -175f, -175f, -179f), floatArrayOf(60f, 60f, 70f, 70f))
        val (lon, lat) = OutlineGeometry.mainTerritoryCentre(listOf(main, wrap), 0.0, 0.0)
        // Unwrapped, the wrap part sits at about 181..185 degrees, so the combined bbox is
        // 60..185, centred near 122.5, not the bogus -50-ish centre a naive min/max would give.
        assertEquals(122.5, lon, 2.0)
        assertTrue(lat in 40.0..70.0)
    }

    @Test
    fun `main territory centre falls back to the label point with no parts`() {
        val (lon, lat) = OutlineGeometry.mainTerritoryCentre(emptyList(), 12.5, -3.0)
        assertEquals(12.5, lon, 1e-9)
        assertEquals(-3.0, lat, 1e-9)
    }

    // ---- build(): on the real data, all 197 ----

    @Test
    fun `every playable country builds a non-empty outline with finite coordinates`() {
        assertEquals(197, playableCodes.size)
        val problems = mutableListOf<String>()
        for (code in playableCodes) {
            val outline = OutlineGeometry.build(layer, code)
            if (outline == null) {
                problems += "$code: no outline"
                continue
            }
            if (outline.rings.isEmpty()) problems += "$code: no rings"
            for (ring in outline.rings) {
                if (ring.points.any { !it.isFinite() }) problems += "$code: non-finite point"
            }
            if (!outline.frame.width.isFinite() || !outline.frame.height.isFinite()) problems += "$code: non-finite frame"
            if (outline.scale <= 0f || !outline.scale.isFinite()) problems += "$code: bad scale ${outline.scale}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Longer side of [outline]'s own bounding box, in box units (fraction of the 1x1 box). */
    private fun longerSideFraction(outline: OutlineModel): Float {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (ring in outline.rings) {
            var i = 0
            while (i < ring.points.size) {
                val x = ring.points[i]; val y = ring.points[i + 1]
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                i += 2
            }
        }
        return max(maxX - minX, maxY - minY)
    }

    /** Area of [outline]'s own bounding box, in box units (fraction of the 1x1 box's area). */
    private fun areaFraction(outline: OutlineModel): Float {
        var minX = Float.POSITIVE_INFINITY; var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        for (ring in outline.rings) {
            var i = 0
            while (i < ring.points.size) {
                val x = ring.points[i]; val y = ring.points[i + 1]
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                i += 2
            }
        }
        return (maxX - minX) * (maxY - minY)
    }

    /**
     * The famously elongated countries the area clamp ([OutlineGeometry.MIN_AREA_FRACTION]) is
     * there for, allowed outside the normal 85-100% band if the clamp ever needs to push their
     * longer side past the box (see [OutlineGeometry]'s class doc). Measured on the real
     * (50m, already Natural-Earth-simplified) geometry: every one of the other 192 playable
     * countries lands at 0.920-0.921 (right at [OutlineGeometry.FIT_FRACTION]). Of this list,
     * only the Maldives (MDV, a long north-south chain of atolls) is elongated enough at bbox
     * level to actually trip the clamp, landing at 1.046 with its area at exactly
     * [OutlineGeometry.MIN_AREA_FRACTION]. Chile, Norway, Vietnam and the Gambia turn out not to
     * need it: their bounding boxes (as opposed to their coastlines) aren't elongated enough for
     * the plain fit to drop under the minimum area (0.920 longer side, 0.22-0.49 area, well
     * above the 0.16 floor) - the next test checks that invariant directly for all five.
     */
    private val elongatedExceptions = setOf("CHL", "NOR", "VNM", "GMB", "MDV")

    @Test
    fun `most countries keep their longer side within a tight band of the box`() {
        val report = StringBuilder()
        val problems = mutableListOf<String>()
        var minFraction = Float.POSITIVE_INFINITY
        var maxFraction = Float.NEGATIVE_INFINITY
        var sum = 0f
        for (code in playableCodes) {
            val outline = OutlineGeometry.build(layer, code, includeNeighbours = false) ?: continue
            val fraction = longerSideFraction(outline)
            minFraction = min(minFraction, fraction)
            maxFraction = max(maxFraction, fraction)
            sum += fraction
            if (code !in elongatedExceptions && fraction !in 0.85f..1.0f) {
                problems += "$code: longer side $fraction outside 0.85..1.0"
            }
        }
        report.append("longer-side fraction over 197: min=%.3f max=%.3f mean=%.3f\n".format(minFraction, maxFraction, sum / playableCodes.size))
        println(report)
        assertTrue(report.toString() + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the area clamp keeps elongated countries from shrinking to a sliver`() {
        val report = StringBuilder()
        for (code in elongatedExceptions) {
            val outline = requireNotNull(OutlineGeometry.build(layer, code, includeNeighbours = false)) { code }
            val area = areaFraction(outline)
            val longer = longerSideFraction(outline)
            report.append("$code: area=%.3f longerSide=%.3f\n".format(area, longer))
            assertTrue("$code area $area should be at least the minimum", area >= OutlineGeometry.MIN_AREA_FRACTION - 0.01f)
        }
        println(report)
    }

    @Test
    fun `wrap countries stay contiguous, not split across the antimeridian`() {
        fun longestSegment(outline: OutlineModel): Float {
            var longest = 0f
            for (ring in outline.rings) {
                val n = ring.points.size / 2
                for (i in 0 until n) {
                    val j = (i + 1) % n
                    val dx = ring.points[2 * j] - ring.points[2 * i]
                    val dy = ring.points[2 * j + 1] - ring.points[2 * i + 1]
                    longest = max(longest, kotlin.math.sqrt(dx * dx + dy * dy))
                }
            }
            return longest
        }
        for (code in listOf("RUS", "FJI", "KIR", "USA")) {
            val outline = requireNotNull(OutlineGeometry.build(layer, code, includeNeighbours = false)) { code }
            val longest = longestSegment(outline)
            // A box is nominally 1 unit across; a streak from an unhandled antimeridian jump
            // would span most of the box, far longer than any real coastline segment.
            assertTrue("$code longest segment $longest should be a coastline, not a jump across the map", longest < 0.5f)
        }
    }

    @Test
    fun `remote parts are left out by default`() {
        // Mainland France only: no ring vertex is anywhere near French Guiana (over 4000 degrees
        // of latitude away is impossible, this is simply absent); Paris-ish coordinates, by
        // contrast, resolve to a point that is actually part of the drawn shape.
        val fra = requireNotNull(OutlineGeometry.build(layer, "FRA", includeNeighbours = false))
        assertTrue(hasRingPointNear(fra, lon = 2.35, lat = 48.85, toleranceDeg = 8.0))
        assertTrue(!hasRingPointNear(fra, lon = -53.1, lat = 3.9, toleranceDeg = 5.0))

        // Asking for remote parts brings French Guiana's geometry back.
        val fraWithRemote = requireNotNull(OutlineGeometry.build(layer, "FRA", includeRemote = true, includeNeighbours = false))
        assertTrue(hasRingPointNear(fraWithRemote, lon = -53.1, lat = 3.9, toleranceDeg = 5.0))

        // The USA keeps Alaska (not flagged remote) but drops Hawaii (flagged remote). The
        // overall bounding box is already continent-sized (it has to reach Alaska), so this
        // checks the actual ring geometry rather than the (deliberately generous, square) frame.
        val usa = requireNotNull(OutlineGeometry.build(layer, "USA", includeNeighbours = false))
        assertTrue("Anchorage-ish coordinates should be part of the shape", hasRingPointNear(usa, lon = -149.9, lat = 61.2, toleranceDeg = 8.0))
        assertTrue("Hawaii should be absent from the shape", !hasRingPointNear(usa, lon = -157.86, lat = 21.3, toleranceDeg = 5.0))

        val usaWithRemote = requireNotNull(OutlineGeometry.build(layer, "USA", includeRemote = true, includeNeighbours = false))
        assertTrue(hasRingPointNear(usaWithRemote, lon = -157.86, lat = 21.3, toleranceDeg = 5.0))
    }

    /** True if some vertex of [outline]'s own rings, converted back to lon/lat, is within [toleranceDeg] of ([lon], [lat]). */
    private fun hasRingPointNear(outline: OutlineModel, lon: Double, lat: Double, toleranceDeg: Double): Boolean {
        for (ring in outline.rings) {
            var i = 0
            while (i < ring.points.size) {
                val planeX = (ring.points[i] / outline.scale + outline.frame.centreX).toDouble()
                val planeY = (ring.points[i + 1] / outline.scale + outline.frame.centreY).toDouble()
                val ll = outline.projection.inverse(planeX, -planeY)
                if (ll != null && abs(ll.x - lon) < toleranceDeg && abs(ll.y - lat) < toleranceDeg) return true
                i += 2
            }
        }
        return false
    }

    @Test
    fun `neighbours of France include Spain, Germany and Belgium`() {
        val fra = requireNotNull(OutlineGeometry.build(layer, "FRA"))
        val ids = fra.neighbours.map { it.cca3 }.toSet()
        assertTrue(ids.toString(), "ESP" in ids)
        assertTrue(ids.toString(), "DEU" in ids)
        assertTrue(ids.toString(), "BEL" in ids)
        // Every neighbour has at least one ring, and none is the country itself.
        assertTrue(fra.neighbours.none { it.cca3 == "FRA" })
        assertTrue(fra.neighbours.all { it.rings.isNotEmpty() })
    }

    @Test
    fun `includeNeighbours false returns no neighbours`() {
        val fra = requireNotNull(OutlineGeometry.build(layer, "FRA", includeNeighbours = false))
        assertTrue(fra.neighbours.isEmpty())
    }

    @Test
    fun `an unknown code returns null`() {
        assertNull(OutlineGeometry.build(layer, "ZZZ"))
    }

    @Test
    fun `a small island nation still gets a well-formed outline`() {
        // Tiny, remote countries are exactly the ones a bad projection cutoff or division would
        // break first.
        for (code in listOf("TUV", "NRU", "VAT", "SMR")) {
            val outline = assertNotNull2(OutlineGeometry.build(layer, code), code)
            assertTrue(code, outline.rings.isNotEmpty())
            assertTrue(code, outline.scale.isFinite() && outline.scale > 0f)
        }
    }

    private fun <T> assertNotNull2(value: T?, message: String): T {
        assertNotNull(message, value)
        return value!!
    }
}
