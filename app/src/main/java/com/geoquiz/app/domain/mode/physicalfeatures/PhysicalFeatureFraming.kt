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

/** Earth's mean radius in km: [MapProjection]'s plane units are radians of great-circle distance
 * on the unit sphere (see its KDoc), so multiplying a plane distance by this radius converts it
 * to kilometres. */
private const val EARTH_RADIUS_KM = 6371.0

/** [physicalFeatureContextBounds]'s floor and cap, in km across (task P10-E, Dav's device
 * feedback "need to be able to zoom out further"): big enough that a tiny feature's tight
 * [physicalFeatureFrame] is never the hard limit on how far the player can zoom out, small enough
 * that the per-question [LambertAzimuthalEqualAreaProjection] (centred on the feature, 100 degree
 * visibility cutoff) doesn't show obviously distorted land near the context rectangle's edge. */
private const val CONTEXT_MIN_SPAN_KM = 2500.0
private const val CONTEXT_MAX_SPAN_KM = 4000.0

/**
 * [CONTEXT_MAX_SPAN_KM]'s replacement once [physicalFeatureFrame] is already wider than it (code
 * review round, task P10-R1: a continent-spanning feature - the Andes, the Transantarctic
 * Mountains, Mekong/Amazon/Mississippi - previously got *zero* zoom-out room at all, since the
 * plain cap sat below the frame itself and [physicalFeatureContextBounds] used to floor the
 * result back down to exactly [frame]'s own size in that case). Still a real cap, not "no cap":
 * [LambertAzimuthalEqualAreaProjection] is centred on the feature itself (see
 * [physicalFeatureProjection]), so land distortion out to this radius is acceptable (Dav), but an
 * unbounded context would eventually wrap into the projection's own antipodal singularity.
 */
private const val CONTEXT_MAX_SPAN_KM_WIDE_FRAME = 8000.0

/** How many times [physicalFeatureFrame]'s own larger side [physicalFeatureContextBounds] is at
 * least, before the km floor/cap apply. */
private const val CONTEXT_INFLATE_FACTOR = 3f

/**
 * The absolute floor on [physicalFeatureContextBounds]'s inflation, as a multiple of [frame]'s
 * own larger side (code review round, task P10-R1): applies even when [CONTEXT_INFLATE_FACTOR]'s
 * preferred 3x would otherwise be capped down below it, so every feature - however big its own
 * frame already is - gets at least some real zoom-out room, never exactly [frame] itself.
 */
private const val CONTEXT_MIN_INFLATE_FACTOR = 1.5f

/**
 * A much larger rectangle, [frame]'s own aspect ratio scaled up, around [feature]'s tight
 * [frame] (defaults to [physicalFeatureFrame]), for the scene's `bounds` (task P10-E).
 * [com.geoquiz.app.ui.map.MapViewState] fits its content bounds at zoom 1 and clamps
 * panning/zooming to it, so using [frame] itself as the scene's bounds (the previous behaviour)
 * made that tight frame the hard limit on how far out the player could ever zoom. This is
 * deliberately much bigger; the screen still *opens* framed tightly on the feature by calling
 * [com.geoquiz.app.ui.map.MapViewState.fitTo] with [frame] once the scene is ready, only when the
 * question changes (never on every recomposition).
 *
 * [frame]'s larger side is scaled to at least [CONTEXT_INFLATE_FACTOR] times itself, floored at
 * [CONTEXT_MIN_SPAN_KM] and capped at [CONTEXT_MAX_SPAN_KM] - or [CONTEXT_MAX_SPAN_KM_WIDE_FRAME]
 * once [frame] is already wider than [CONTEXT_MAX_SPAN_KM] itself (code review round, task
 * P10-R1) - converted to plane units via [EARTH_RADIUS_KM]. Whichever cap applies, the result is
 * never smaller than [CONTEXT_MIN_INFLATE_FACTOR] times [frame]'s own span: a feature whose bbox
 * is already wider than every cap (a continent-spanning mountain range or river) still gets real
 * extra room around it, rather than the previous behaviour of exactly [frame] itself with no
 * inflation at all. The other side scales by the same factor, so the context rectangle keeps
 * [frame]'s own aspect ratio rather than forcing it to a square (which would clip a very
 * non-square frame on its shorter axis).
 */
fun physicalFeatureContextBounds(
    projection: MapProjection,
    feature: PhysicalFeature,
    frame: PlaneRect = physicalFeatureFrame(projection, feature),
): PlaneRect {
    val minSpan = (CONTEXT_MIN_SPAN_KM / EARTH_RADIUS_KM).toFloat()
    val plainMaxSpan = (CONTEXT_MAX_SPAN_KM / EARTH_RADIUS_KM).toFloat()
    val wideFrameMaxSpan = (CONTEXT_MAX_SPAN_KM_WIDE_FRAME / EARTH_RADIUS_KM).toFloat()
    val frameSpan = maxOf(frame.width, frame.height)
    val maxSpan = if (frameSpan > plainMaxSpan) wideFrameMaxSpan else plainMaxSpan
    val preferredSpan = (frameSpan * CONTEXT_INFLATE_FACTOR).coerceAtLeast(minSpan).coerceAtMost(maxSpan)
    val span = preferredSpan.coerceAtLeast(frameSpan * CONTEXT_MIN_INFLATE_FACTOR)
    val scale = if (frameSpan > 0f) span / frameSpan else 1f
    val halfWidth = frame.width * scale / 2f
    val halfHeight = frame.height * scale / 2f
    return PlaneRect(frame.centreX - halfWidth, frame.centreY - halfHeight, frame.centreX + halfWidth, frame.centreY + halfHeight)
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
