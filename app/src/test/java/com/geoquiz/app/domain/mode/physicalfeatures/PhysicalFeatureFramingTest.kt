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
