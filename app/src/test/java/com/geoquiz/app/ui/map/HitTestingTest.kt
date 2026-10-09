package com.geoquiz.app.ui.map

import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.EqualEarthProjection
import com.geoquiz.app.domain.map.FeatureHitIndex
import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.map.MapHit
import com.geoquiz.app.domain.map.ProjectedLayer
import com.geoquiz.app.domain.map.TapZones
import com.geoquiz.app.domain.map.resolveTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Hit testing (task 4.3) on the real layers, pure JVM: the same steps as `MapScene.hitTest`
 * (tap zones, then polygons) with the camera from [MapViewState].
 */
class HitTestingTest {

    private fun read(id: GeoLayerId): GeoLayer = GeoLayerReader.read(File("src/main/assets/geo", id.fileName).readBytes())

    private val coarse by lazy { ProjectedLayer.build(read(GeoLayerId.ADMIN0_110M), EqualEarthProjection) }
    private val detail by lazy { ProjectedLayer.build(read(GeoLayerId.ADMIN0_50M), EqualEarthProjection) }
    private val tapLayer by lazy { ProjectedLayer.build(read(GeoLayerId.TAP_ZONES), EqualEarthProjection) }

    private val appCountries: Set<String> by lazy {
        File("src/main/assets/flags").listFiles()!!.filter { it.name.endsWith(".svg") }
            .map { it.name.removeSuffix(".svg").uppercase() }.toSet()
    }

    /** A phone: 360 x 640 dp at density 2.75, showing the whole world. */
    private class Phone(layer: ProjectedLayer, val density: Float = 2.75f) {
        val state = MapViewState().apply {
            updateViewport(360 * density, 640 * density)
            updateContentBounds(layer.bounds)
        }
        val m get() = ZoneMetrics(state.scale, density)
    }

    private fun hit(index: FeatureHitIndex, zones: TapZones?, phone: Phone, x: Float, y: Float): MapHit? {
        val m = phone.m
        return resolveTap(index, zones, x, y, m.radius, m.listedMax, m.otherMax, m.tie)
    }

    @Test
    fun `every one of the 197 countries is tappable at world fit on a phone`() {
        val index = FeatureHitIndex(coarse)
        val zones = TapZones.build(coarse, tapLayer)
        val phone = Phone(coarse)
        assertEquals("world fit stays at 110m", MapLevel.COARSE,
            resolveMapLevel(MapLevelPreference.AUTO, phone.state.scale / phone.density, true, true))

        val viaZone = mutableListOf<String>()
        val viaLabel = mutableListOf<String>()
        val viaInterior = mutableListOf<String>()
        val untappable = mutableListOf<String>()
        for (id in appCountries.sorted()) {
            val f = coarse.features.first { it.id == id }
            val z = zones.indexOf(id)
            val zoneShown = z >= 0 && zones.visible(z, phone.m.listedMax, phone.m.otherMax)
            when {
                zoneShown && hit(index, zones, phone, zones.centreX[z], zones.centreY[z])?.featureId == id -> viaZone += id
                hit(index, zones, phone, f.labelX, f.labelY)?.featureId == id -> viaLabel += id
                interiorPointHitting(index, zones, phone, id) -> viaInterior += id
                else -> untappable += id
            }
        }
        println(
            "World fit on 360x640 dp: ${viaZone.size} by zone centre, ${viaLabel.size} at the label point, " +
                "${viaInterior.size} at another interior point $viaInterior"
        )
        assertTrue("Not tappable: $untappable", untappable.isEmpty())
        assertEquals(197, viaZone.size + viaLabel.size + viaInterior.size)
        // Every microstate from tap_zones.bin is hit at its own zone centre (VAT included).
        val listed = appCountries.filter { zones.indexOf(it).let { z -> z >= 0 && zones.listed[z] } }
        assertEquals(46, listed.size)
        assertTrue("Listed but not hit at the zone centre: ${listed - viaZone.toSet()}", viaZone.containsAll(listed))
    }

    /** Scans a grid over the feature's box for a point inside it that resolves to it. */
    private fun interiorPointHitting(index: FeatureHitIndex, zones: TapZones, phone: Phone, id: String): Boolean {
        val f = index.layer.features.indexOfFirst { it.id == id }
        val b = index.layer.features[f].bounds
        for (i in 1 until 40) for (j in 1 until 40) {
            val x = b.minX + b.width * i / 40
            val y = b.minY + b.height * j / 40
            if (index.contains(f, x, y) && hit(index, zones, phone, x, y)?.featureId == id) return true
        }
        return false
    }

    @Test
    fun `every country is tappable on the 50m layer zoomed to 3x`() {
        val index = FeatureHitIndex(detail)
        val zones = TapZones.build(detail, tapLayer)
        val phone = Phone(detail)
        phone.state.snapTo(3f, phone.state.centreX, phone.state.centreY)
        val missing = appCountries.filter { id ->
            val f = detail.features.first { it.id == id }
            val z = zones.indexOf(id)
            val atZone = z >= 0 && zones.visible(z, phone.m.listedMax, phone.m.otherMax) &&
                hit(index, zones, phone, zones.centreX[z], zones.centreY[z])?.featureId == id
            !atZone && hit(index, zones, phone, f.labelX, f.labelY)?.featureId != id &&
                !interiorPointHitting(index, zones, phone, id)
        }
        assertTrue("Not tappable at 50m: $missing", missing.isEmpty())
    }

    @Test
    fun `enclaves win over the country round them`() {
        for (layer in listOf(coarse, detail)) {
            val index = FeatureHitIndex(layer)
            fun at(id: String): MapHit? {
                val f = layer.features.first { it.id == id }
                // Vertex average: inside the small convex shapes (VAT is a triangle whose label
                // point falls just outside it).
                val ring = f.rings[0]
                var sx = 0f
                var sy = 0f
                for (p in ring.indices step 2) { sx += ring[p]; sy += ring[p + 1] }
                val n = ring.size / 2
                return index.hit(sx / n, sy / n)
            }
            assertEquals("${layer.layerId} LSO", "LSO", at("LSO")?.featureId)
            assertEquals("${layer.layerId} SMR", "SMR", at("SMR")?.featureId)
            assertEquals("${layer.layerId} VAT", "VAT", at("VAT")?.featureId)
            val zaf = layer.features.first { it.id == "ZAF" }
            assertEquals("ZAF", index.hit(zaf.labelX, zaf.labelY)?.featureId)
            val ita = layer.features.first { it.id == "ITA" }
            assertEquals("ITA", index.hit(ita.labelX, ita.labelY)?.featureId)
        }
    }

    @Test
    fun `water returns null and non-playable land is reported as such`() {
        val index = FeatureHitIndex(coarse)
        val zones = TapZones.build(coarse, tapLayer)
        val phone = Phone(coarse)
        fun planeOf(lon: Double, lat: Double): Pair<Float, Float> {
            val p = EqualEarthProjection.project(lon, lat)
            return p.x.toFloat() to (-p.y).toFloat()
        }
        val (wx, wy) = planeOf(-30.0, -45.0) // South Atlantic
        assertNull(hit(index, zones, phone, wx, wy))
        val (ox, oy) = planeOf(5.0, 89.0) // off the top of the map data
        assertNull(index.hit(ox, oy))
        val (gx, gy) = planeOf(-40.0, 75.0) // inland Greenland
        val greenland = hit(index, zones, phone, gx, gy)
        assertEquals("GRL", greenland?.featureId)
        assertFalse(greenland!!.isPlayable)
        val (ax, ay) = planeOf(30.0, -85.0)
        assertEquals("ATA", index.hit(ax, ay)?.featureId)
    }

    @Test
    fun `zones resolve before polygons and hide once the country is large`() {
        val index = FeatureHitIndex(coarse)
        val zones = TapZones.build(coarse, tapLayer)
        val phone = Phone(coarse)
        val mus = zones.indexOf("MUS")
        assertTrue(zones.listed[mus])
        // Mauritius's zone covers the sea round it at world fit: a tap 15 dp east still hits it.
        val dx = 15 * phone.density / phone.state.scale
        val nearMauritius = hit(index, zones, phone, zones.centreX[mus] + dx, zones.centreY[mus])
        assertEquals("MUS", nearMauritius?.featureId)
        assertEquals(true, nearMauritius?.viaTapZone)
        // Just outside the 22 dp radius it is open sea again.
        val outside = 23 * phone.density / phone.state.scale
        assertNull(hit(index, zones, phone, zones.centreX[mus] + outside, zones.centreY[mus]))

        // Zoomed far in, Australia is huge: its zone (if any) is gone and taps use the shape.
        val aus = zones.indexOf("AUS")
        assertFalse(zones.visible(aus, phone.m.listedMax, phone.m.otherMax))
        // A small unlisted country has a zone at world fit but not at max zoom.
        val bel = zones.indexOf("BEL")
        assertFalse(zones.listed[bel])
        assertTrue(zones.visible(bel, phone.m.listedMax, phone.m.otherMax))
        phone.state.snapTo(50f, zones.centreX[bel], zones.centreY[bel])
        assertFalse(zones.visible(bel, phone.m.listedMax, phone.m.otherMax))
        // At that zoom a tap just off Belgium's label lands on the shape, not a zone.
        val b = coarse.features.first { it.id == "BEL" }
        val shape = hit(index, zones, phone, b.labelX, b.labelY)
        assertEquals("BEL", shape?.featureId)
        assertEquals(false, shape?.viaTapZone)
    }

    // ---- Synthetic zones for the tie-break ----

    /** Square A (0..4) and square B (4..5) with zones at their centres (2, 2) and (4.5, 4.5). */
    private fun twoSquares(): Pair<FeatureHitIndex, TapZones> {
        val layer = ProjectedLayer.build(squareLayer(squareFeature("A", 0f, 4f), squareFeature("B", 4f, 5f)), PlateProjection)
        return FeatureHitIndex(layer) to TapZones.build(layer, null)
    }

    @Test
    fun `overlapping zones pick the nearest centre`() {
        val (index, zones) = twoSquares()
        // Both zones (radius 10) cover (4.2, 4.2); B's centre is nearer.
        assertEquals("B", resolveTap(index, zones, 4.2f, 4.2f, 10f, 100f, 100f, tieDistance = 1f)?.featureId)
        assertEquals("A", resolveTap(index, zones, 1f, 1f, 10f, 100f, 100f, tieDistance = 1f)?.featureId)
        // Outside every zone and every shape: water.
        assertNull(resolveTap(index, zones, 30f, 30f, 10f, 100f, 100f, tieDistance = 1f))
    }

    @Test
    fun `near-identical zone centres are settled by the polygon under the tap`() {
        val (index, zones) = twoSquares()
        // (3.9, 3.9) is in A but nearer B's centre. With centres within the tie distance, the
        // polygon decides; without, the nearest centre wins.
        assertEquals("A", resolveTap(index, zones, 3.9f, 3.9f, 10f, 100f, 100f, tieDistance = 5f)?.featureId)
        assertEquals("B", resolveTap(index, zones, 3.9f, 3.9f, 10f, 100f, 100f, tieDistance = 1f)?.featureId)
        // In the tie, if no polygon holds the tap, the nearest centre still wins.
        assertEquals("B", resolveTap(index, zones, 6f, 6f, 10f, 100f, 100f, tieDistance = 5f)?.featureId)
    }

    @Test
    fun `in a tie a listed microstate beats a non-listed neighbour holding the tap`() {
        val layer = ProjectedLayer.build(squareLayer(squareFeature("A", 0f, 4f), squareFeature("B", 4f, 5f)), PlateProjection)
        // B is listed (a tap-zone country), A is not.
        val tap = ProjectedLayer.build(
            GeoLayer(GeoLayerId.TAP_ZONES, GeometryKind.POINT, GeoBounds(-180.0, -90.0, 180.0, 90.0), "t", emptyList(),
                listOf(GeoFeature("B", GeoProperties(emptyList(), emptyArray()), GeoBounds(4.5, -4.5, 4.5, -4.5), 4.5, -4.5, emptyList()))),
            PlateProjection,
        )
        val index = FeatureHitIndex(layer)
        val zones = TapZones.build(layer, tap)
        // (3.9, 3.9) lies in A; with the centres in a tie, listed B still wins.
        assertEquals("B", resolveTap(index, zones, 3.9f, 3.9f, 10f, 100f, 100f, tieDistance = 5f)?.featureId)
        // Outside the tie (centres further apart than the tie distance) the usual rules apply:
        // B is nearest, but over A's land A's label is nearer, so A keeps it.
        assertEquals("A", resolveTap(index, zones, 2.2f, 2.2f, 10f, 100f, 100f, tieDistance = 1f)?.featureId)
    }

    @Test
    fun `zone visibility follows the size thresholds`() {
        val layer = ProjectedLayer.build(squareLayer(squareFeature("A", 0f, 4f), squareFeature("B", 4f, 5f)), PlateProjection)
        val tap = ProjectedLayer.build(
            GeoLayer(GeoLayerId.TAP_ZONES, GeometryKind.POINT, GeoBounds(-180.0, -90.0, 180.0, 90.0), "t", emptyList(),
                listOf(GeoFeature("A", GeoProperties(emptyList(), emptyArray()), GeoBounds(1.0, -1.0, 1.0, -1.0), 1.0, -1.0, emptyList()))),
            PlateProjection,
        )
        val zones = TapZones.build(layer, tap)
        val a = zones.indexOf("A")
        val b = zones.indexOf("B")
        assertTrue(zones.listed[a])
        assertFalse(zones.listed[b])
        // A is centred on its tap point, not its label.
        assertEquals(1f, zones.centreX[a])
        assertEquals(4f, zones.extent[a])
        // Listed: shown until 4 units; others until the smaller threshold.
        assertTrue(zones.visible(a, alwaysMaxExtent = 5f, otherMaxExtent = 0.5f))
        assertFalse(zones.visible(a, alwaysMaxExtent = 4f, otherMaxExtent = 100f))
        assertTrue(zones.visible(b, alwaysMaxExtent = 0.1f, otherMaxExtent = 2f))
        assertFalse(zones.visible(b, alwaysMaxExtent = 100f, otherMaxExtent = 1f))
        // Hidden zones don't catch taps: (6, 6) is outside both shapes.
        assertNull(resolveTap(FeatureHitIndex(layer), zones, 6f, 6f, 10f, alwaysMaxExtent = 1f, otherMaxExtent = 0.5f, tieDistance = 1f))
    }

    @Test
    fun `non-playable land gets no zone`() {
        val layer = ProjectedLayer.build(squareLayer(squareFeature("A", 0f, 1f), squareFeature("X", 2f, 3f, playable = false)), PlateProjection)
        val zones = TapZones.build(layer, null)
        assertEquals(listOf("A"), zones.ids.toList())
        assertEquals(MapHit("X", isPlayable = false, viaTapZone = false), FeatureHitIndex(layer).hit(2.5f, 2.5f))
    }

    @Test
    fun `hit test timing on the 50m layer`() {
        val index = FeatureHitIndex(detail)
        val zones = TapZones.build(detail, tapLayer)
        val phone = Phone(detail)
        val b = detail.bounds
        val random = Random(42)
        val points = List(20_000) { b.minX + random.nextFloat() * b.width to b.minY + random.nextFloat() * b.height }
        repeat(2) { points.forEach { (x, y) -> hit(index, zones, phone, x, y) } } // warm up
        val start = System.nanoTime()
        var hits = 0
        points.forEach { (x, y) -> if (hit(index, zones, phone, x, y) != null) hits++ }
        val perHitMicros = (System.nanoTime() - start) / 1e3 / points.size
        println("Hit test on admin0_50m with zones: %.1f µs per tap (%d of %d on land or a zone)".format(perHitMicros, hits, points.size))
        if (System.getProperty("geoquiz.perf") == "true") assertTrue("$perHitMicros µs", perHitMicros < 1000)
    }
}
