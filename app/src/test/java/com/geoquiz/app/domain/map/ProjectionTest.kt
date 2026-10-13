package com.geoquiz.app.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

class ProjectionTest {

    private val ee = EqualEarthProjection

    // ---- Equal Earth (Šavrič, Patterson and Jenny 2018, unit sphere) ----

    @Test
    fun `equal earth origin and extents`() {
        assertPoint(0.0, 0.0, ee.project(0.0, 0.0))
        // At the equator theta = 0, so x = lambda / (M * A1) with M = sqrt(3)/2, A1 = 1.340264.
        val m = sqrt(3.0) / 2
        assertEquals(PI / (m * 1.340264), ee.project(180.0, 0.0).x, 1e-12)
        assertEquals(2.706629, ee.project(180.0, 0.0).x, 1e-6)
        // At the pole theta = asin(M) = pi/3.
        val t = PI / 3
        val yPole = t * (1.340264 - 0.081106 * t * t + Math.pow(t, 6.0) * (0.000893 + 0.003796 * t * t))
        assertEquals(yPole, ee.project(0.0, 90.0).y, 1e-12)
        assertEquals(1.317363, ee.project(0.0, 90.0).y, 1e-6)
        // The poles are lines (pseudocylindrical), so x still depends on longitude there.
        assertTrue(ee.project(180.0, 90.0).x in 0.5..ee.maxX)
        assertEquals(ee.maxX, ee.project(180.0, 0.0).x, 1e-12)
        assertEquals(ee.maxY, yPole, 1e-12)
        // The published aspect ratio of the map outline is about 2.05:1.
        assertEquals(2.05, ee.maxX / ee.maxY, 0.01)
    }

    @Test
    fun `equal earth is symmetric about the equator and the central meridian`() {
        for (lon in listOf(-170.0, -45.0, 10.0, 151.2)) for (lat in listOf(-80.0, -33.9, 5.0, 60.0)) {
            val p = ee.project(lon, lat)
            assertPoint(-p.x, p.y, ee.project(-lon, lat))
            assertPoint(p.x, -p.y, ee.project(lon, -lat))
        }
    }

    @Test
    fun `equal earth preserves area`() {
        // On the unit sphere the area element is cos(phi) dlambda dphi, so the Jacobian
        // determinant of (lambda, phi) -> (x, y) must equal cos(phi) everywhere.
        for (lon in listOf(-150.0, -20.0, 0.0, 75.0, 179.0)) for (lat in listOf(-85.0, -40.0, 0.0, 30.0, 70.0)) {
            assertEquals("($lon, $lat)", cos(Math.toRadians(lat)), jacobian(ee, lon, lat), 1e-6)
        }
    }

    @Test
    fun `equal earth inverse round trips`() {
        for (lon in listOf(-180.0, -100.0, 0.0, 33.3, 180.0)) for (lat in listOf(-90.0, -60.0, 0.0, 45.5, 89.0)) {
            val p = ee.project(lon, lat)
            val back = ee.inverse(p.x, p.y)!!
            assertEquals(lat, back.y, 1e-9)
            if (abs(lat) < 90) assertEquals(lon, back.x, 1e-9)
        }
    }

    @Test
    fun `equal earth inverse rejects points off the map`() {
        assertNull(ee.inverse(2.8, 0.0))
        assertNull(ee.inverse(0.0, 1.4))
        // Inside the bounding box but outside the curved edge, near a corner.
        assertNull(ee.inverse(2.6, 1.2))
        assertNotNull(ee.inverse(0.0, 0.0))
    }

    // ---- Lambert azimuthal equal-area ----

    @Test
    fun `laea maps its centre to the origin`() {
        for ((lon, lat) in listOf(0.0 to 0.0, 134.0 to -25.0, 15.0 to 52.0, -100.0 to 45.0, 0.0 to 90.0)) {
            val p = LambertAzimuthalEqualAreaProjection(lon, lat)
            assertPoint(0.0, 0.0, p.project(lon, lat))
            val back = p.inverse(0.0, 0.0)!!
            assertEquals(lon, back.x, 1e-12)
            assertEquals(lat, back.y, 1e-12)
        }
    }

    @Test
    fun `laea known values`() {
        val p = LambertAzimuthalEqualAreaProjection(0.0, 0.0)
        // Equatorial aspect: (90E, 0) is a quarter turn away, rho = 2 sin(c/2) = sqrt(2).
        assertPoint(sqrt(2.0), 0.0, p.project(90.0, 0.0))
        assertPoint(0.0, sqrt(2.0), p.project(0.0, 90.0))
        // Polar aspect: rho = 2 sin((90 - lat)/2), measured along the meridian.
        val polar = LambertAzimuthalEqualAreaProjection(0.0, 90.0)
        val q = polar.project(0.0, 0.0)
        assertEquals(0.0, q.x, 1e-12)
        assertEquals(-2 * Math.sin(Math.toRadians(45.0)), q.y, 1e-12)
    }

    @Test
    fun `laea is symmetric either side of the central meridian`() {
        val p = LambertAzimuthalEqualAreaProjection(134.0, -25.0)
        for (d in listOf(5.0, 20.0, 40.0)) for (lat in listOf(-45.0, -25.0, -10.0)) {
            val east = p.project(134.0 + d, lat)
            val west = p.project(134.0 - d, lat)
            assertEquals(-east.x, west.x, 1e-12)
            assertEquals(east.y, west.y, 1e-12)
        }
    }

    @Test
    fun `laea preserves area and round trips`() {
        val p = LambertAzimuthalEqualAreaProjection(15.0, 52.0)
        for (lon in listOf(-20.0, 0.0, 15.0, 40.0)) for (lat in listOf(35.0, 52.0, 70.0)) {
            assertEquals("($lon, $lat)", cos(Math.toRadians(lat)), jacobian(p, lon, lat), 1e-6)
            val q = p.project(lon, lat)
            val back = p.inverse(q.x, q.y)!!
            assertEquals(lon, back.x, 1e-9)
            assertEquals(lat, back.y, 1e-9)
        }
        assertNull(p.inverse(2.1, 0.0))
    }

    @Test
    fun `laea wraps across the antimeridian`() {
        val p = LambertAzimuthalEqualAreaProjection(180.0, -40.0)
        assertPoint(p.project(-175.0, -40.0).x, p.project(-175.0, -40.0).y, p.project(185.0, -40.0))
        assertTrue(p.project(-175.0, -40.0).x > 0)
        assertTrue(p.project(175.0, -40.0).x < 0)
        assertEquals(-175.0, p.inverse(p.project(-175.0, -40.0).x, p.project(-175.0, -40.0).y)!!.x, 1e-9)
    }

    // ---- Bounds helpers ----

    @Test
    fun `plane bounds sample the bulging meridians and flip y`() {
        val world = ee.planeBounds(GeoBounds(-180.0, -90.0, 180.0, 90.0))
        assertEquals(-ee.maxX, world.minX.toDouble(), 1e-5)
        assertEquals(ee.maxX, world.maxX.toDouble(), 1e-5)
        // Plane y points down: the North Pole is at minY.
        assertEquals(-ee.maxY, world.minY.toDouble(), 1e-5)
        assertEquals(ee.maxY, world.maxY.toDouble(), 1e-5)
    }

    @Test
    fun `feature plane bounds can leave out remote parts`() {
        val main = GeoPart(0, floatArrayOf(0f, 10f, 10f), floatArrayOf(0f, 0f, 10f))
        val remote = GeoPart(GeoPart.FLAG_REMOTE, floatArrayOf(100f, 101f, 101f), floatArrayOf(0f, 0f, 1f))
        val feature = GeoFeature("X", GeoProperties(emptyList(), emptyArray()), GeoBounds(0.0, 0.0, 101.0, 10.0), 5.0, 5.0, listOf(main, remote))

        val all = ee.planeBounds(feature, includeRemote = true)
        val home = ee.planeBounds(feature, includeRemote = false)

        assertEquals(ee.project(10.0, 0.0).x.toFloat(), home.maxX, 1e-6f)
        assertTrue(all.maxX > home.maxX)
        assertEquals(-ee.project(0.0, 10.0).y.toFloat(), home.minY, 1e-6f)
        assertFalse(home.contains(ee.project(100.0, 0.0).x.toFloat(), 0f))
    }

    @Test
    fun `projected layer keeps order, bounds and flags`() {
        val part = GeoPart(GeoPart.FLAG_WRAP, floatArrayOf(-10f, 10f, 0f), floatArrayOf(0f, 0f, 20f))
        val props = listOf(GeoPropertyDef("playable", GeoPropertyType.UINT8))
        val layer = GeoLayer(
            GeoLayerId.ADMIN0_110M, GeometryKind.POLYGON, GeoBounds(-10.0, 0.0, 10.0, 20.0), "test", props,
            listOf(
                GeoFeature("A", GeoProperties(props, arrayOf(1)), GeoBounds(-10.0, 0.0, 10.0, 20.0), 0.0, 5.0, listOf(part)),
                GeoFeature("B", GeoProperties(props, arrayOf(0)), GeoBounds(-10.0, 0.0, 10.0, 20.0), 0.0, 5.0, listOf(part)),
            ),
        )
        val projected = ProjectedLayer.build(layer, ee)
        assertEquals(listOf("A", "B"), projected.features.map { it.id })
        assertEquals(listOf(true, false), projected.features.map { it.isPlayable })
        val a = projected.features[0]
        assertEquals(6, a.rings[0].size)
        assertEquals(GeoPart.FLAG_WRAP, a.partFlags[0])
        assertEquals(ee.project(-10.0, 0.0).x.toFloat(), a.rings[0][0], 1e-6f)
        assertEquals(-ee.project(0.0, 20.0).y.toFloat(), a.rings[0][5], 1e-6f)
        assertEquals(a.bounds, projected.bounds)
        assertTrue(a.bounds.minY < 0f && a.bounds.maxY == 0f)
    }

    private fun jacobian(p: MapProjection, lonDeg: Double, latDeg: Double): Double {
        val h = 1e-5 // radians
        val hDeg = Math.toDegrees(h)
        val xl = p.project(lonDeg + hDeg, latDeg)
        val xl0 = p.project(lonDeg - hDeg, latDeg)
        val xp = p.project(lonDeg, latDeg + hDeg)
        val xp0 = p.project(lonDeg, latDeg - hDeg)
        val dxdl = (xl.x - xl0.x) / (2 * h)
        val dydl = (xl.y - xl0.y) / (2 * h)
        val dxdp = (xp.x - xp0.x) / (2 * h)
        val dydp = (xp.y - xp0.y) / (2 * h)
        return abs(dxdl * dydp - dxdp * dydl)
    }

    private fun assertPoint(x: Double, y: Double, actual: ProjectedPoint) {
        assertEquals("x", x, actual.x, 1e-9)
        assertEquals("y", y, actual.y, 1e-9)
    }
}
