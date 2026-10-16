package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.LambertAzimuthalEqualAreaProjection
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.planeBounds

/**
 * Frames one physical feature on its own regional map (task 7.2, D51). Unlike US states and
 * Canadian provinces' [com.geoquiz.app.domain.mode.usca.usCaFrame] (one shared frame for a whole
 * round of countries all on one continent), every question here can be a different feature
 * anywhere on Earth, so the projection itself is rebuilt per question, centred on the stored
 * [PhysicalFeature.bbox]'s centre: the task brief's suggestion to use the data file's own
 * precomputed bbox directly rather than re-walking geometry, which is simpler and, unlike
 * geometry, robust to a feature stored as several [GeoLayer] records
 * ([PhysicalFeature.neIds], e.g. the Aral Sea's North and South halves).
 */
fun physicalFeatureProjection(feature: PhysicalFeature): LambertAzimuthalEqualAreaProjection =
    LambertAzimuthalEqualAreaProjection(centreLon = feature.bbox.centreLon, centreLat = feature.bbox.centreLat)

/**
 * The plane rectangle that frames [feature]'s stored bbox in [projection], padded by
 * [paddingFraction] of the larger side (same convention as
 * [com.geoquiz.app.domain.mode.usca.usCaFrame], a little more generous here since a feature's
 * own bbox, unlike a region's member union, has no built-in margin beyond the feature itself).
 */
fun physicalFeatureFrame(
    projection: MapProjection,
    feature: PhysicalFeature,
    paddingFraction: Float = 0.2f
): PlaneRect {
    val rect = projection.planeBounds(feature.bbox)
    return rect.inflate(maxOf(rect.width, rect.height) * paddingFraction)
}

/**
 * [feature]'s ids in [layer] (task 7.2): a [GeoLayer] feature's own `id` *is* its Natural Earth
 * id as a string for rivers/lakes/regions/geo_points layers (`data/geo/FORMAT.md`; confirmed
 * against `tools/geodata/build_geodata.py`'s `ne_id()`), so resolving [PhysicalFeature.neIds] is
 * a direct lookup. More than one id means the feature highlights as several map parts (e.g. the
 * Aral Sea's two halves); an id with no match in [layer] (a data mismatch) is left out rather
 * than failing the round.
 */
fun physicalFeatureLayerIds(feature: PhysicalFeature, layer: GeoLayer): Set<String> =
    feature.neIds.filterTo(HashSet()) { layer.feature(it) != null }

/**
 * A synthetic, single-feature (or few-feature) copy of [layer] holding only the parts whose id
 * is in [ids] (task 7.2 review fix C2, [ids] from [physicalFeatureLayerIds]): [layer] itself can
 * carry hundreds of unrelated features (e.g. `regions_50m`'s 517 polygons, including
 * whole-continent shapes) that would otherwise draw over the highlighted target later in the
 * same layer's draw order, or - for a line layer - get a fill pass that makes no sense for an
 * open polyline. Filtering to just the target's own ids before handing anything to
 * [com.geoquiz.app.ui.map.MapCanvas] (as the scene's `overlay`) removes that z-order fighting
 * entirely, rather than relying on draw-order tricks. Null if [ids] is empty (a data mismatch,
 * same case [physicalFeatureLayerIds] already tolerates).
 */
fun physicalFeatureOverlayLayer(layer: GeoLayer, ids: Set<String>): GeoLayer? {
    if (ids.isEmpty()) return null
    val parts = layer.features.filter { it.id in ids }
    if (parts.isEmpty()) return null
    return GeoLayer(
        id = layer.id,
        kind = layer.kind,
        bounds = layer.bounds,
        source = layer.source,
        properties = layer.properties,
        features = parts,
    )
}
