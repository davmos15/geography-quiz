package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.map.planeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalFeatureFramingTest {

    private fun emptyLayer(id: GeoLayerId, kind: GeometryKind, vararg features: GeoFeature): GeoLayer = GeoLayer(
        id, kind, GeoBounds(-180.0, -90.0, 180.0, 90.0), "test", emptyList(), features.toList()
    )

    private fun lineFeature(id: String): GeoFeature = GeoFeature(
        id = id,
        properties = com.geoquiz.app.domain.map.GeoProperties(emptyList(), emptyArray()),
        bbox = GeoBounds(10.0, 10.0, 20.0, 20.0),
        labelLon = 15.0,
        labelLat = 15.0,
        parts = emptyList()
    )

    private val feature = PhysicalFeature(
        id = "aral-sea",
        name = "Aral Sea",
        aliases = emptyList(),
        type = PhysicalFeatureType.LAKE,
        layerId = GeoLayerId.LAKES_50M,
        neIds = listOf("north", "south", "missing"),
        countries = listOf("KAZ", "UZB"),
        continent = "Asia",
        bbox = GeoBounds(minLon = 57.0, minLat = 43.0, maxLon = 62.0, maxLat = 47.0),
        tier = 2,
        wikidata = "Q35883"
    )

    @Test
    fun `the projection is centred on the bbox centre`() {
        val projection = physicalFeatureProjection(feature)
        assertEquals(feature.bbox.centreLon, projection.centreLon, 1e-9)
        assertEquals(feature.bbox.centreLat, projection.centreLat, 1e-9)
    }

    @Test
    fun `the frame contains the projected bbox, padded`() {
        val projection = physicalFeatureProjection(feature)
        val unpadded = projection.planeBounds(feature.bbox)
        val frame = physicalFeatureFrame(projection, feature)

        assertTrue(frame.minX <= unpadded.minX)
        assertTrue(frame.minY <= unpadded.minY)
        assertTrue(frame.maxX >= unpadded.maxX)
        assertTrue(frame.maxY >= unpadded.maxY)
        // Padded, not just equal (the bbox alone has no built-in margin).
        assertTrue(frame.width > unpadded.width)
    }

    @Test
    fun `the context bounds contain the frame`() {
        val projection = physicalFeatureProjection(feature)
        val frame = physicalFeatureFrame(projection, feature)
        val context = physicalFeatureContextBounds(projection, feature, frame)

        assertTrue(context.minX <= frame.minX)
        assertTrue(context.minY <= frame.minY)
        assertTrue(context.maxX >= frame.maxX)
        assertTrue(context.maxY >= frame.maxY)
    }

    @Test
    fun `the context bounds are at least the minimum span for a tiny feature`() {
        // A peak-sized bbox: almost zero extent, so the frame itself is tiny (task P10-E).
        val tiny = feature.copy(
            type = PhysicalFeatureType.PEAK,
            layerId = GeoLayerId.GEO_POINTS_50M,
            bbox = GeoBounds(minLon = 86.9, minLat = 27.9, maxLon = 86.91, maxLat = 27.91)
        )
        val projection = physicalFeatureProjection(tiny)
        val frame = physicalFeatureFrame(projection, tiny)
        val context = physicalFeatureContextBounds(projection, tiny, frame)

        // ~2500 km floor is a large multiple of a near-zero frame; well short of a whole
        // hemisphere (rho = 2 at the antipode), so this is a meaningful, non-trivial lower bound.
        assertTrue(context.width > frame.width * 10f)
        assertTrue(context.height > frame.height * 10f)
        assertTrue(context.width < 1f)
        assertTrue(context.height < 1f)
    }

    @Test
    fun `a feature wider than the cap still gets extra zoom-out room, at least 1_5x its frame`() {
        // A continent-spanning range (e.g. the Andes): the frame itself is already far beyond
        // the ~4000 km cap (task P10-R1 review fix: this used to leave exactly the frame itself,
        // with no inflation and so no zoom-out room at all).
        val huge = feature.copy(
            type = PhysicalFeatureType.RANGE,
            layerId = GeoLayerId.REGIONS_50M,
            bbox = GeoBounds(minLon = -40.0, minLat = -40.0, maxLon = 40.0, maxLat = 40.0)
        )
        val projection = physicalFeatureProjection(huge)
        val frame = physicalFeatureFrame(projection, huge)
        val context = physicalFeatureContextBounds(projection, huge, frame)

        assertTrue(context.width >= frame.width * 1.5f - 1e-6f)
        assertTrue(context.height >= frame.height * 1.5f - 1e-6f)
        assertTrue(context.width > frame.width)
        assertTrue(context.height > frame.height)
    }

    @Test
    fun `a frame just over the plain cap is inflated by the raised cap, beyond the 1_5x floor alone`() {
        // Wide enough that the frame exceeds the plain ~4000 km cap (triggering the raised ~8000
        // km cap), but not so wide that the 1.5x floor alone would already reach that raised cap
        // - here the raised cap itself is the binding constraint, distinct from the 1.5x-floor
        // case covered above (task P10-R1).
        val wide = feature.copy(
            type = PhysicalFeatureType.RANGE,
            layerId = GeoLayerId.REGIONS_50M,
            bbox = GeoBounds(minLon = -14.5, minLat = -1.0, maxLon = 14.5, maxLat = 1.0)
        )
        val projection = physicalFeatureProjection(wide)
        val frame = physicalFeatureFrame(projection, wide)
        val context = physicalFeatureContextBounds(projection, wide, frame)

        // More room than the 1.5x floor alone, but still capped well short of the nominal 3x.
        assertTrue(context.width > frame.width * 1.5f)
        assertTrue(context.width < frame.width * 3f)
    }

    @Test
    fun `a mid-sized feature is inflated by the context factor, capped at the maximum span`() {
        // Wide enough that 3x inflation would exceed the ~4000 km cap, so the cap applies.
        val wide = feature.copy(bbox = GeoBounds(minLon = 40.0, minLat = 35.0, maxLon = 80.0, maxLat = 55.0))
        val projection = physicalFeatureProjection(wide)
        val frame = physicalFeatureFrame(projection, wide)
        val context = physicalFeatureContextBounds(projection, wide, frame)

        assertTrue(context.width < maxOf(frame.width, frame.height) * 3f)
        assertTrue(context.width >= frame.width)
        assertTrue(context.height >= frame.height)
    }

    @Test
    fun `layer ids resolve only the neIds actually present in the layer`() {
        val layer = emptyLayer(GeoLayerId.LAKES_50M, GeometryKind.POLYGON, lineFeature("north"), lineFeature("south"))
        val ids = physicalFeatureLayerIds(feature, layer)
        assertEquals(setOf("north", "south"), ids)
    }

    @Test
    fun `an id with no match in the layer is left out, never failing`() {
        val layer = emptyLayer(GeoLayerId.LAKES_50M, GeometryKind.POLYGON, lineFeature("north"))
        val ids = physicalFeatureLayerIds(feature, layer)
        assertEquals(setOf("north"), ids)
    }

    @Test
    fun `no match at all returns an empty set, not an error`() {
        val layer = emptyLayer(GeoLayerId.LAKES_50M, GeometryKind.POLYGON)
        val ids = physicalFeatureLayerIds(feature, layer)
        assertEquals(emptySet<String>(), ids)
    }
}
