package com.geoquiz.app.ui.physicalfeatures

import com.geoquiz.app.data.physicalfeatures.PhysicalFeatureRepository
import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeature
import com.geoquiz.app.domain.mode.physicalfeatures.PhysicalFeatureType

/** A tiny fake river feature (task 7.2 tests): just enough geometry for a [GeoLayer]. */
fun fakeRiverLayerFeature(neId: String): GeoFeature = GeoFeature(
    id = neId,
    properties = GeoProperties(emptyList(), emptyArray()),
    bbox = GeoBounds(10.0, 10.0, 20.0, 20.0),
    labelLon = 15.0,
    labelLat = 15.0,
    parts = emptyList()
)

fun fakeRiversLayer(vararg neIds: String): GeoLayer = GeoLayer(
    GeoLayerId.RIVERS_50M,
    GeometryKind.LINE,
    GeoBounds(-180.0, -90.0, 180.0, 90.0),
    "test",
    emptyList(),
    neIds.map { fakeRiverLayerFeature(it) }
)

fun fakeRiver(id: String, name: String, continent: String = "Asia", neId: String = "1$id"): PhysicalFeature = PhysicalFeature(
    id = id,
    name = name,
    aliases = emptyList(),
    type = PhysicalFeatureType.RIVER,
    layerId = GeoLayerId.RIVERS_50M,
    neIds = listOf(neId),
    countries = emptyList(),
    continent = continent,
    bbox = GeoBounds(10.0, 10.0, 20.0, 20.0),
    tier = 1,
    wikidata = ""
)

/**
 * Three fake rivers, so a round never shrinks below [PhysicalFeaturesRound.ROUND_SIZE]'s cap.
 * Names are all over 6 characters (`FuzzyAnswerMatcher.MIN_LENGTH_EXCLUSIVE`) so a typo test
 * never depends on which one the round's shuffle happens to put first.
 */
val FAKE_NILE = fakeRiver("nile", "Nile River", neId = "1nile")
val FAKE_AMAZON = fakeRiver("amazon", "Amazon River", continent = "South America", neId = "1amazon")
val FAKE_YANGTZE = fakeRiver("yangtze", "Yangtze River", neId = "1yangtze")

val FAKE_RIVERS = listOf(FAKE_NILE, FAKE_AMAZON, FAKE_YANGTZE)

val FAKE_RIVERS_LAYER: GeoLayer = fakeRiversLayer("1nile", "1amazon", "1yangtze")

/** Serves [features] without touching the real bundled asset. */
class FakePhysicalFeatureRepository(private val features: List<PhysicalFeature> = FAKE_RIVERS) : PhysicalFeatureRepository {
    override suspend fun features(): List<PhysicalFeature> = features
}
