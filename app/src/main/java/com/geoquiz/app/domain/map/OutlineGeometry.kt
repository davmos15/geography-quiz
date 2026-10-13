package com.geoquiz.app.domain.map

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One ring (outer or hole) in "box" coordinates: see [OutlineGeometry.build] for how the box is
 * defined. Coordinates are interleaved `[x0, y0, x1, y1, ...]`, not closed (match the last point
 * to the first when drawing, as with [GeoPart]).
 */
class OutlineRing(val points: FloatArray, val isHole: Boolean)

/** One faint neighbouring feature, in the same box space as the silhouette. */
class OutlineNeighbour(val cca3: String, val rings: List<OutlineRing>)

/**
 * A single country's silhouette (task 4.5): [rings] is the country's own admin-0 geometry,
 * north-up and scaled for a consistent visual size next to the app's other 196 countries (the
 * sizing rule is [OutlineGeometry.sizeFor]); [neighbours] is faint nearby land for context, in
 * the same coordinate space.
 *
 * Coordinates in [rings] and [neighbours] are centred on (0, 0) and nominally land within
 * `[-0.5, 0.5]` on both axes (a 1x1 "box"); very elongated countries (see [OutlineGeometry.sizeFor])
 * can extend a little past that on their long axis, which the drawing composable (`CountryOutline`)
 * clips. [frame] is the plane rectangle (pre-scale, in [projection]'s units) that box exactly
 * shows; it is also the search area used to find [neighbours].
 */
class OutlineModel(
    val cca3: String,
    /** Whether far-flung parts (e.g. France's overseas territories) were kept. */
    val includeRemote: Boolean,
    val projection: MapProjection,
    val rings: List<OutlineRing>,
    val neighbours: List<OutlineNeighbour>,
    val frame: PlaneRect,
    /** Plane units to box units (applied after centring on [frame]'s centre). */
    val scale: Float,
)

/**
 * Builds [OutlineModel]s from an `admin0_50m` [GeoLayer] (see `data/geo/FORMAT.md`). Pure and
 * off the main thread's caller's choice: no Android or Compose types.
 *
 * ### Projection
 * Each silhouette gets its own [LambertAzimuthalEqualAreaProjection] centred on the country's
 * own "main-territory centre": the midpoint of the geographic bounding box of its non-remote
 * parts (wrap parts such as Chukotka or the Aleutians are shifted by a multiple of 360 degrees
 * onto the same side as the country's largest part first, per the antimeridian rule in
 * `data/geo/FORMAT.md`, so the box is never a bogus half-world). This keeps every country
 * north-up and undistorted at its own centre, and keeps wrap countries (Russia, Fiji, Kiribati)
 * contiguous without a separate "unwrap" step. The projection's visibility cutoff
 * ([LambertAzimuthalEqualAreaProjection.maxDistanceDeg]) is computed, not left at the map's
 * default: first wide enough that none of the country's own vertices are culled, then widened
 * again if needed so every corner of the final frame (below) is inside the cutoff too, so a
 * neighbour at the edge of the frame is never wrongly culled.
 *
 * ### Sizing (consistent visual size, AC 4.5)
 * The box a silhouette is drawn in is 1x1 (nominally `[-0.5, 0.5]` on both axes). Let `w`, `h`
 * be the plane-space width and height of the country's own bounding box and `longer = max(w, h)`.
 * Two candidate scales:
 * - `fitScale = FIT_FRACTION / longer`: the normal rule, fits the bounding box's longer side to
 *   [FIT_FRACTION] (92%) of the box, leaving an even margin.
 * - `minAreaScale`: the scale at which the bounding box's area is exactly [MIN_AREA_FRACTION]
 *   (16%) of the box's area. For a roughly square country `minAreaScale < fitScale` (fitting the
 *   side already gives a much bigger area than the minimum), so it does nothing. For a very
 *   elongated country (Chile, Norway, Vietnam, the Gambia) `fitScale` alone would fit the long
 *   side but shrink the short side to a sliver with hardly any area; `minAreaScale` overrides it.
 *
 * `scale = max(fitScale, minAreaScale)`. The consequence for elongated countries: the short side
 * fills more of the box (so the shape stays legible instead of vanishing to a hairline) and the
 * long side can run past the nominal box and get clipped at the two ends by the drawing
 * composable. See `OutlineGeometryTest` for the measured spread over all 197 and the list of
 * countries where the clamp changes anything.
 *
 * ### Remote parts and neighbours
 * [includeRemote] (default `false`) matches framing a country for a region preset: far-flung
 * parts (Hawaii for the USA, French Guiana for France) are left out unless every part is remote.
 * Neighbours are geometry-only: any other feature in [layer] (playable or not) whose own
 * bounding box overlaps [OutlineModel.frame], the exact area the silhouette's box shows. This
 * is deliberately not the mledoze political land-border list used by Border hop (Phase 6): that
 * list is reached through [com.geoquiz.app.domain.repository.CountryRepository] /
 * [com.geoquiz.app.domain.model.Country], which has no `borders` field yet and is a shared file
 * outside this task's scope. Neighbour rings are kept in full (not vertex-clipped to the frame);
 * the drawing composable's `clipToBounds` keeps nothing drawn outside the box, which is visually
 * the same result for a lot less geometry work.
 */
object OutlineGeometry {

    /** Fraction of the box the bounding box's longer side normally fills. */
    const val FIT_FRACTION = 0.92f

    /** Minimum fraction of the box's area the bounding box must cover (elongated countries). */
    const val MIN_AREA_FRACTION = 0.16f

    /** Half the side of the box coordinates nominally land in (the box spans `[-0.5, 0.5]`). */
    const val BOX_HALF_EXTENT = 0.5f

    /** Degrees of slack added past the farthest vertex when picking the projection's cutoff. */
    private const val VISIBILITY_MARGIN_DEG = 10.0

    /** Degrees of slack added past the frame's farthest corner. */
    private const val FRAME_VISIBILITY_MARGIN_DEG = 3.0

    /** Cutoffs are capped below 180 degrees: [LambertAzimuthalEqualAreaProjection] has no single image for the antipode. */
    private const val MAX_VISIBILITY_DEG = 175.0

    /**
     * A degenerate (near zero width or height) bounding box is treated as if it were at least
     * this fraction of the longer side, so [MIN_AREA_FRACTION] can't blow the scale up without
     * bound on bad data.
     */
    private const val MIN_ASPECT_FOR_CLAMP = 0.01f

    /**
     * Builds the silhouette for [cca3] from [layer] (normally `admin0_50m`), or null if there is
     * no such feature. See the class doc for [includeRemote] and [includeNeighbours].
     */
    fun build(
        layer: GeoLayer,
        cca3: String,
        includeRemote: Boolean = false,
        includeNeighbours: Boolean = true,
    ): OutlineModel? {
        val feature = layer.feature(cca3) ?: return null

        val allRemote = feature.parts.all { it.isRemote }
        val useAll = includeRemote || allRemote
        val ownParts = if (useAll) feature.parts else feature.parts.filter { !it.isRemote }
        if (ownParts.isEmpty()) return null

        val (centreLon, centreLat) = mainTerritoryCentre(ownParts, feature.labelLon, feature.labelLat)
        val provisionalCutoff = visibilityCutoffDeg(ownParts, centreLon, centreLat, VISIBILITY_MARGIN_DEG)
        val provisionalProjection = LambertAzimuthalEqualAreaProjection(centreLon, centreLat, provisionalCutoff)

        val ownRings = projectParts(ownParts, provisionalProjection)
        val bbox = boundsOf(ownRings) ?: return null
        val (scale, frame) = sizeFor(bbox)

        val requiredCutoff = frameVisibilityCutoffDeg(frame)
        val finalCutoff = min(MAX_VISIBILITY_DEG, max(provisionalCutoff, requiredCutoff))
        val projection = if (finalCutoff == provisionalCutoff) provisionalProjection
        else LambertAzimuthalEqualAreaProjection(centreLon, centreLat, finalCutoff)

        val boxRings = ownRings.map { it.toBox(bbox, scale) }

        val neighbours = if (includeNeighbours) {
            findNeighbours(layer, excludeId = feature.id, projection = projection, frame = frame).map { candidate ->
                val candidateAllRemote = candidate.parts.all { it.isRemote }
                val candidateParts = if (candidateAllRemote) candidate.parts else candidate.parts.filter { !it.isRemote }
                val rings = projectParts(candidateParts, projection).map { it.toBox(bbox, scale) }
                OutlineNeighbour(candidate.id, rings)
            }
        } else {
            emptyList()
        }

        return OutlineModel(cca3, includeRemote, projection, boxRings, neighbours, frame, scale)
    }

    /**
     * The scale from plane units to box units, and the plane-space frame it maps exactly onto
     * the box (see the class doc). [bbox] is the country's own plane-space bounding box.
     */
    internal fun sizeFor(bbox: PlaneRect): Pair<Float, PlaneRect> {
        val w = bbox.width
        val h = bbox.height
        val longer = max(w, h)
        val shorterEffective = max(min(w, h), longer * MIN_ASPECT_FOR_CLAMP)
        val safeLonger = max(longer, longer * MIN_ASPECT_FOR_CLAMP).let { if (it <= 0f) 1e-6f else it }

        val fitScale = FIT_FRACTION / safeLonger
        val area = safeLonger * shorterEffective
        val minAreaScale = sqrt((MIN_AREA_FRACTION / max(area, 1e-12f)).toDouble()).toFloat()
        val scale = max(fitScale, minAreaScale)

        val halfPlane = BOX_HALF_EXTENT / scale
        val frame = PlaneRect(bbox.centreX - halfPlane, bbox.centreY - halfPlane, bbox.centreX + halfPlane, bbox.centreY + halfPlane)
        return scale to frame
    }

    /** [points] mapped from plane units (centred on [bbox]'s centre) to box units. */
    private fun OutlineRing.toBox(bbox: PlaneRect, scale: Float): OutlineRing {
        val out = FloatArray(points.size)
        var i = 0
        while (i < points.size) {
            out[i] = (points[i] - bbox.centreX) * scale
            out[i + 1] = (points[i + 1] - bbox.centreY) * scale
            i += 2
        }
        return OutlineRing(out, isHole)
    }

    /**
     * Projects [parts] to plane coordinates, dropping any whose every point is outside
     * [projection]'s visibility cutoff (a hole follows its outer ring, like [ProjectedLayer.build]).
     */
    private fun projectParts(parts: List<GeoPart>, projection: MapProjection): List<OutlineRing> {
        val scratch = DoubleArray(2)
        var outerKept = false
        val kept = parts.filter { part ->
            val visible = (0 until part.size).any { projection.isVisible(part.lon[it].toDouble(), part.lat[it].toDouble()) }
            if (part.isHole) outerKept else visible.also { outerKept = it }
        }
        return kept.map { part ->
            val xy = FloatArray(part.size * 2)
            for (i in 0 until part.size) {
                projection.project(part.lon[i].toDouble(), part.lat[i].toDouble(), scratch)
                xy[2 * i] = scratch[0].toFloat()
                xy[2 * i + 1] = (-scratch[1]).toFloat()
            }
            OutlineRing(xy, part.isHole)
        }
    }

    private fun boundsOf(rings: List<OutlineRing>): PlaneRect? {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (ring in rings) {
            var i = 0
            while (i < ring.points.size) {
                val x = ring.points[i]
                val y = ring.points[i + 1]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                i += 2
            }
        }
        return if (minX > maxX) null else PlaneRect(minX, minY, maxX, maxY)
    }

    /**
     * The geographic centre (degrees) of [parts]' bounding box, treating a part flagged
     * [GeoPart.isWrap] as continuing whichever part its longitudes are closest to (the reference,
     * the largest non-wrap part), per the antimeridian rule in `data/geo/FORMAT.md`. Falls back
     * to the label point if [parts] is empty.
     */
    internal fun mainTerritoryCentre(parts: List<GeoPart>, fallbackLon: Double, fallbackLat: Double): Pair<Double, Double> {
        if (parts.isEmpty()) return fallbackLon to fallbackLat
        val reference = parts.filterNot { it.isWrap }.ifEmpty { parts }.maxByOrNull { it.size }!!
        var refLon = 0.0
        for (i in 0 until reference.size) refLon += reference.lon[i]
        refLon /= reference.size

        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        for (part in parts) {
            for (i in 0 until part.size) {
                var lon = part.lon[i].toDouble()
                while (lon - refLon > 180.0) lon -= 360.0
                while (lon - refLon < -180.0) lon += 360.0
                if (lon < minLon) minLon = lon
                if (lon > maxLon) maxLon = lon
                val lat = part.lat[i].toDouble()
                if (lat < minLat) minLat = lat
                if (lat > maxLat) maxLat = lat
            }
        }
        return normaliseLon((minLon + maxLon) / 2) to (minLat + maxLat) / 2
    }

    /** Great-circle degrees from ([centreLon], [centreLat]) to the farthest vertex of [parts], plus [marginDeg]. */
    private fun visibilityCutoffDeg(parts: List<GeoPart>, centreLon: Double, centreLat: Double, marginDeg: Double): Double {
        var maxDistance = 0.0
        for (part in parts) {
            for (i in 0 until part.size) {
                val d = angularDistanceDeg(part.lon[i].toDouble(), part.lat[i].toDouble(), centreLon, centreLat)
                if (d > maxDistance) maxDistance = d
            }
        }
        return min(MAX_VISIBILITY_DEG, maxDistance + marginDeg)
    }

    /** Great-circle angular distance (degrees) between two lon/lat points. */
    private fun angularDistanceDeg(lon: Double, lat: Double, centreLon: Double, centreLat: Double): Double {
        val deg = Math.PI / 180.0
        val phi0 = centreLat * deg
        val phi = lat * deg
        val dLambda = (lon - centreLon) * deg
        val cosC = sin(phi0) * sin(phi) + cos(phi0) * cos(phi) * cos(dLambda)
        return acos(cosC.coerceIn(-1.0, 1.0)) / deg
    }

    /**
     * Degrees from the projection's centre to the farthest corner of [frame], plus
     * [FRAME_VISIBILITY_MARGIN_DEG], using the same `rho -> angle` relationship as
     * [LambertAzimuthalEqualAreaProjection.inverse].
     */
    private fun frameVisibilityCutoffDeg(frame: PlaneRect): Double {
        val deg = 180.0 / Math.PI
        var maxDistance = 0.0
        for ((x, y) in listOf(
            frame.minX to frame.minY, frame.minX to frame.maxY,
            frame.maxX to frame.minY, frame.maxX to frame.maxY,
        )) {
            val rho = sqrt(x.toDouble() * x + y.toDouble() * y)
            val c = 2 * asin(min(1.0, rho / 2))
            val distanceDeg = c * deg
            if (distanceDeg > maxDistance) maxDistance = distanceDeg
        }
        return min(MAX_VISIBILITY_DEG, maxDistance + FRAME_VISIBILITY_MARGIN_DEG)
    }

    /**
     * Other features in [layer] whose own plane-space bounding box overlaps [frame] (geometry
     * only; see the class doc). A feature whose geographic bounding box centre is already
     * outside [projection]'s visibility cutoff is skipped before it is projected, both as a
     * cheap pre-filter and to avoid the huge (but finite) bounding boxes an azimuthal projection
     * gives land near its antipode.
     */
    private fun findNeighbours(layer: GeoLayer, excludeId: String, projection: MapProjection, frame: PlaneRect): List<GeoFeature> {
        val result = mutableListOf<GeoFeature>()
        for (candidate in layer.features) {
            if (candidate.id == excludeId) continue
            if (!projection.isVisible(candidate.bbox.centreLon, candidate.bbox.centreLat)) continue
            val bounds = projection.planeBounds(candidate, includeRemote = false)
            if (frame.intersects(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY)) result += candidate
        }
        return result
    }

    private fun normaliseLon(lon: Double): Double {
        var l = lon
        while (l > 180) l -= 360
        while (l < -180) l += 360
        return l
    }
}
