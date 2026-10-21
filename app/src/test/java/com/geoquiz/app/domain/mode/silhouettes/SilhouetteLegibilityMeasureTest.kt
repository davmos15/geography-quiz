package com.geoquiz.app.domain.mode.silhouettes

import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.OutlineRing
import com.geoquiz.app.domain.map.PlaneRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure (no real asset) tests for [SilhouetteLegibility.measure] and
 * [SilhouetteLegibility.isRecognisable] against small, exactly computable synthetic shapes
 * (task P10-E).
 */
class SilhouetteLegibilityMeasureTest {

    /** A square ring, centred on ([cx], [cy]), side [side]: deliberately only 4 vertices, used
     * only where a test is specifically about [SilhouetteLegibility.MIN_VERTEX_COUNT] - every
     * other test below uses [pentagon] so coverage/dominant-part assertions aren't accidentally
     * tripped by the separate vertex-detail rule. */
    private fun square(cx: Float, cy: Float, side: Float, isHole: Boolean = false): OutlineRing {
        val h = side / 2f
        return OutlineRing(
            floatArrayOf(cx - h, cy - h, cx + h, cy - h, cx + h, cy + h, cx - h, cy + h),
            isHole
        )
    }

    /** A regular pentagon's area is `(5/4) * side^2 / tan(36deg)`; [sideLength] is chosen directly
     * rather than computed from a target area, so the exact area is asserted inline per test. */
    private fun pentagon(cx: Float, cy: Float, radius: Float, isHole: Boolean = false): OutlineRing {
        val points = FloatArray(10)
        for (i in 0 until 5) {
            val angle = (Math.PI / 2 + i * 2 * Math.PI / 5)
            points[2 * i] = cx + radius * kotlin.math.cos(angle).toFloat()
            points[2 * i + 1] = cy + radius * kotlin.math.sin(angle).toFloat()
        }
        return OutlineRing(points, isHole)
    }

    private fun model(rings: List<OutlineRing>) = OutlineModel(
        cca3 = "XXX",
        includeRemote = false,
        projection = LambertAzimuthalEqualAreaProjection(0.0, 0.0),
        rings = rings,
        neighbours = emptyList(),
        frame = PlaneRect(-0.5f, -0.5f, 0.5f, 0.5f),
        scale = 1f,
    )

    @Test
    fun `a single square covers exactly its own area, one part, fully dominant`() {
        val metrics = SilhouetteLegibility.measure(model(listOf(square(0f, 0f, 0.8f))))

        assertEquals(0.64f, metrics.landCoverage, 1e-4f)
        assertEquals(1, metrics.partCount)
        assertEquals(1f, metrics.largestPartShare, 1e-4f)
        assertEquals(4, metrics.largestPartVertexCount)
    }

    @Test
    fun `a hole is subtracted from its enclosing part's area`() {
        val rings = listOf(square(0f, 0f, 0.8f), square(0f, 0f, 0.2f, isHole = true))
        val metrics = SilhouetteLegibility.measure(model(rings))

        // 0.8^2 - 0.2^2 = 0.64 - 0.04 = 0.60
        assertEquals(0.60f, metrics.landCoverage, 1e-4f)
        assertEquals(1, metrics.partCount)
    }

    @Test
    fun `two equal, far-apart specks have low coverage and no dominant part`() {
        val tiny = 0.02f
        val rings = listOf(
            pentagon(-0.4f, -0.4f, tiny),
            pentagon(0.4f, 0.4f, tiny),
        )
        val metrics = SilhouetteLegibility.measure(model(rings))

        assertEquals(2, metrics.partCount)
        assertEquals(0.5f, metrics.largestPartShare, 1e-4f)
        assertTrue("land coverage should be tiny", metrics.landCoverage < SilhouetteLegibility.MIN_LAND_COVERAGE)
        assertFalse("low coverage and no dominant part: not recognisable", SilhouetteLegibility.isRecognisable(metrics))
    }

    @Test
    fun `one dominant part is not enough on its own if total coverage is still below the floor`() {
        // No escape hatch for a dominant-but-tiny part (task P10-E review): this is exactly
        // Palau's real, measured shape (one island holding 96.6% of a total land area that is
        // still only 0.1% of the box) - the contact sheet showed that dominance alone doesn't
        // make a silhouette this small actually visible, so land coverage alone decides.
        val rings = listOf(
            pentagon(0f, 0f, 0.05f),
            pentagon(0.49f, 0.49f, 0.001f),
        )
        val metrics = SilhouetteLegibility.measure(model(rings))

        assertTrue("total coverage should be below the floor", metrics.landCoverage < SilhouetteLegibility.MIN_LAND_COVERAGE)
        assertTrue("the main part is still dominant", metrics.largestPartShare > 0.99f)
        assertFalse("dominance alone no longer saves it", SilhouetteLegibility.isRecognisable(metrics))
    }

    @Test
    fun `no rings at all measures as zero coverage, not a crash`() {
        val metrics = SilhouetteLegibility.measure(model(emptyList()))

        assertEquals(0f, metrics.landCoverage, 1e-6f)
        assertEquals(0, metrics.partCount)
        assertEquals(0f, metrics.largestPartShare, 1e-6f)
        assertFalse(SilhouetteLegibility.isRecognisable(metrics))
    }

    @Test
    fun `a leading hole with no outer ring yet is ignored, never crashing`() {
        val rings = listOf(square(0f, 0f, 0.1f, isHole = true), square(0f, 0f, 0.8f))
        val metrics = SilhouetteLegibility.measure(model(rings))

        assertEquals(1, metrics.partCount)
        assertEquals(0.64f, metrics.landCoverage, 1e-4f)
    }

    // ---- MIN_VERTEX_COUNT: a crude, near-featureless blob is excluded regardless of coverage ----

    @Test
    fun `a single large square has plenty of coverage but too few vertices to be recognisable`() {
        // Coverage is high (0.8^2 = 0.64) and it is fully dominant (one part), but a 4-vertex
        // quadrilateral is the kind of crude blob MIN_VERTEX_COUNT excludes (Tuvalu's real
        // measured silhouette is a 3-vertex triangle at 9% coverage - comfortably over the
        // coverage floor, but still unrecognisable).
        val metrics = SilhouetteLegibility.measure(model(listOf(square(0f, 0f, 0.8f))))

        assertEquals(4, metrics.largestPartVertexCount)
        assertTrue(metrics.largestPartVertexCount <= SilhouetteLegibility.MIN_VERTEX_COUNT)
        assertFalse("plenty of coverage, but too crude a shape to be recognisable", SilhouetteLegibility.isRecognisable(metrics))
    }

    @Test
    fun `a pentagon with plenty of coverage and one more vertex is recognisable`() {
        // Same shape of test, one more vertex than MIN_VERTEX_COUNT allows: over the line, so
        // coverage/dominance alone decide it, same as every real country's rule.
        val metrics = SilhouetteLegibility.measure(model(listOf(pentagon(0f, 0f, 0.4f))))

        assertTrue(metrics.largestPartVertexCount > SilhouetteLegibility.MIN_VERTEX_COUNT)
        assertTrue(SilhouetteLegibility.isRecognisable(metrics))
    }
}
