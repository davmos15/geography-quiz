package com.geoquiz.app.domain.map

import kotlin.math.max
import kotlin.math.min

/** What a tap landed on. */
data class MapHit(
    val featureId: String,
    /** False for land that isn't one of the app's countries; callers decide what to do with it. */
    val isPlayable: Boolean,
    /** True if a tap zone (the circle round a small country) caught the tap. */
    val viaTapZone: Boolean,
)

/**
 * Point-in-polygon hit testing over one [ProjectedLayer] in plane coordinates, with a uniform
 * grid of feature bounding boxes so a tap only tests the few features near it. Build it once
 * per layer and projection (it is cached with the layer's paths).
 *
 * A feature contains a point if an even-odd ray cast across all of its rings is odd, so holes
 * work. If several features contain the point (an enclave in a country without a hole for it,
 * such as San Marino and Vatican City inside Italy at 110m), the one with the smallest bounding
 * box wins. Non-playable land is hit like any other feature.
 */
class FeatureHitIndex(val layer: ProjectedLayer, columns: Int = 64, rows: Int = 32) {
    private val features = layer.features
    private val minX = layer.bounds.minX
    private val minY = layer.bounds.minY
    private val cols = max(1, columns)
    private val rowCount = max(1, rows)
    private val cellW = max(layer.bounds.width / cols, 1e-6f)
    private val cellH = max(layer.bounds.height / rowCount, 1e-6f)

    /** Compressed cell lists: features of cell c are cellItems[cellStart[c] until cellStart[c + 1]]. */
    private val cellStart = IntArray(cols * rowCount + 1)
    private val cellItems: IntArray

    /** Bounding-box area per feature, for the smallest-wins rule. */
    private val area = FloatArray(features.size) { features[it].bounds.width * features[it].bounds.height }

    init {
        val counts = IntArray(cols * rowCount)
        forEachCell { _, c -> counts[c]++ }
        for (c in counts.indices) cellStart[c + 1] = cellStart[c] + counts[c]
        cellItems = IntArray(cellStart.last())
        val fill = cellStart.copyOf(counts.size)
        forEachCell { f, c -> cellItems[fill[c]++] = f }
    }

    private inline fun forEachCell(action: (feature: Int, cell: Int) -> Unit) {
        for (f in features.indices) {
            val b = features[f].bounds
            val c0 = col(b.minX)
            val c1 = col(b.maxX)
            val r0 = row(b.minY)
            val r1 = row(b.maxY)
            for (r in r0..r1) for (c in c0..c1) action(f, r * cols + c)
        }
    }

    private fun col(x: Float): Int = ((x - minX) / cellW).toInt().coerceIn(0, cols - 1)
    private fun row(y: Float): Int = ((y - minY) / cellH).toInt().coerceIn(0, rowCount - 1)

    /** Index of the feature at plane ([x], [y]), or -1 for water. */
    fun featureAt(x: Float, y: Float): Int {
        if (!layer.bounds.contains(x, y)) return -1
        val cell = row(y) * cols + col(x)
        var best = -1
        for (k in cellStart[cell] until cellStart[cell + 1]) {
            val f = cellItems[k]
            if (!features[f].bounds.contains(x, y)) continue
            if (best >= 0 && area[f] >= area[best]) continue
            if (contains(f, x, y)) best = f
        }
        return best
    }

    /** True if feature [index] contains plane ([x], [y]) (even-odd over all its rings). */
    fun contains(index: Int, x: Float, y: Float): Boolean {
        var inside = false
        for (ring in features[index].rings) {
            if (ringCrossingsOdd(ring, x, y)) inside = !inside
        }
        return inside
    }

    /** The feature at plane ([x], [y]) as a [MapHit], or null for water. */
    fun hit(x: Float, y: Float): MapHit? {
        val f = featureAt(x, y)
        return if (f < 0) null else MapHit(features[f].id, features[f].isPlayable, viaTapZone = false)
    }

    private companion object {
        /** Ray cast to +x over an open ring of interleaved x, y. */
        fun ringCrossingsOdd(ring: FloatArray, x: Float, y: Float): Boolean {
            val n = ring.size / 2
            if (n < 3) return false
            var odd = false
            var j = n - 1
            for (i in 0 until n) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) odd = !odd
                j = i
            }
            return odd
        }
    }
}

/**
 * Tap zones for one country layer: a circle of fixed on-screen size round each small
 * playable country, so it can be tapped (and its state seen) when its shape is too small.
 *
 * Candidates are every playable feature of [land]. A country listed in the tap-zone layer
 * (`tap_zones.bin`, under 20 000 km²) is centred on its tap point and keeps its zone until its
 * largest piece is [alwaysMaxExtent] across; any other playable country is centred on its
 * label point and gets a zone only while its largest piece is under [otherMaxExtent]. Sizes
 * are passed in plane units for the current zoom (see [visible]).
 *
 * "Largest piece" is the bigger side of the bounding box of the largest outer ring, so a
 * spread-out island nation (Kiribati) is judged by its biggest island, not its whole spread.
 */
class TapZones private constructor(
    val ids: Array<String>,
    /** Zone centres in plane coordinates. */
    val centreX: FloatArray,
    val centreY: FloatArray,
    /** Largest outer ring of the country (plane units). */
    val extent: FloatArray,
    /** True for countries from the tap-zone layer. */
    val listed: BooleanArray,
    /** Index of each zone's feature in the land layer. */
    val featureIndex: IntArray,
    /** Zone index per land feature, or -1. */
    private val zoneOfFeature: IntArray,
) {
    val size: Int get() = ids.size

    /** The zone of land feature [feature], or -1 if it has none. */
    fun zoneOf(feature: Int): Int = if (feature in zoneOfFeature.indices) zoneOfFeature[feature] else -1

    /** Whether zone [i] is shown at the current zoom (thresholds in plane units). */
    fun visible(i: Int, alwaysMaxExtent: Float, otherMaxExtent: Float): Boolean =
        extent[i] < if (listed[i]) alwaysMaxExtent else otherMaxExtent

    fun indexOf(id: String): Int = ids.indexOf(id)

    companion object {
        fun build(land: ProjectedLayer, tapZones: ProjectedLayer?): TapZones {
            val tapById = tapZones?.features?.associateBy { it.id }.orEmpty()
            val picked = land.features.withIndex().filter { it.value.isPlayable }
            return TapZones(
                ids = Array(picked.size) { picked[it].value.id },
                centreX = FloatArray(picked.size) { i -> picked[i].value.let { tapById[it.id]?.labelX ?: it.labelX } },
                centreY = FloatArray(picked.size) { i -> picked[i].value.let { tapById[it.id]?.labelY ?: it.labelY } },
                extent = FloatArray(picked.size) { largestOuterRing(picked[it].value) },
                listed = BooleanArray(picked.size) { picked[it].value.id in tapById },
                featureIndex = IntArray(picked.size) { picked[it].index },
                zoneOfFeature = IntArray(land.features.size) { -1 }.also { map ->
                    picked.forEachIndexed { zone, f -> map[f.index] = zone }
                },
            )
        }

        private fun largestOuterRing(feature: ProjectedFeature): Float {
            var best = 0f
            for (r in feature.rings.indices) {
                if (feature.partFlags[r] and GeoPart.FLAG_HOLE != 0) continue
                val ring = feature.rings[r]
                var x0 = Float.POSITIVE_INFINITY
                var x1 = Float.NEGATIVE_INFINITY
                var y0 = Float.POSITIVE_INFINITY
                var y1 = Float.NEGATIVE_INFINITY
                var p = 0
                while (p < ring.size) {
                    x0 = min(x0, ring[p]); x1 = max(x1, ring[p])
                    y0 = min(y0, ring[p + 1]); y1 = max(y1, ring[p + 1])
                    p += 2
                }
                best = max(best, max(x1 - x0, y1 - y0))
            }
            return best
        }
    }
}

/**
 * Resolves a tap at plane ([x], [y]). All distances are plane units at the current zoom.
 *
 * 1. Tap zones: among visible zones whose circle ([zoneRadius]) contains the point, take the
 *    nearest centre. If other zone centres lie within [tieDistance] of that one (microstates
 *    on top of each other or of a neighbour's label), see [settleTie]: listed microstates
 *    first, then the polygon under the tap, then the nearest centre.
 * 2. The zone wins over water, over the zone's own country, and over a country whose own
 *    zone also covers the tap (zone against zone: nearest centre). Over the land of a country
 *    without a zone there (a larger neighbour, or non-playable land), the zone wins only if
 *    the tap is nearer the zone centre than that country's label point. Without that rule the
 *    circles of small neighbours could cover a whole larger country (the Democratic Republic
 *    of the Congo between Rwanda, Burundi, Uganda and Congo at world zoom); with it, every
 *    country keeps at least the land round its own label point.
 * 3. Otherwise the polygon under the point ([FeatureHitIndex.hit]), or null for water.
 */
fun resolveTap(
    index: FeatureHitIndex,
    zones: TapZones?,
    x: Float,
    y: Float,
    zoneRadius: Float,
    alwaysMaxExtent: Float,
    otherMaxExtent: Float,
    tieDistance: Float,
): MapHit? {
    val polygon = index.featureAt(x, y)
    if (zones != null) {
        val r2 = zoneRadius * zoneRadius
        var best = -1
        var bestD2 = Float.POSITIVE_INFINITY
        for (i in 0 until zones.size) {
            if (!zones.visible(i, alwaysMaxExtent, otherMaxExtent)) continue
            val dx = zones.centreX[i] - x
            val dy = zones.centreY[i] - y
            val d2 = dx * dx + dy * dy
            if (d2 <= r2 && d2 < bestD2) {
                best = i
                bestD2 = d2
            }
        }
        if (best >= 0) {
            val chosen = settleTie(index, zones, best, x, y, zoneRadius, alwaysMaxExtent, otherMaxExtent, tieDistance)
            val zoneFeature = zones.featureIndex[chosen]
            val cx = zones.centreX[chosen] - x
            val cy = zones.centreY[chosen] - y
            val zoneD2 = cx * cx + cy * cy
            val nearerThanLabel = polygon >= 0 && index.layer.features[polygon].let { p ->
                val lx = p.labelX - x
                val ly = p.labelY - y
                zoneD2 < lx * lx + ly * ly
            }
            val polygonZone = if (polygon >= 0) zones.zoneOf(polygon) else -1
            val polygonZoneCovers = polygonZone >= 0 &&
                zones.visible(polygonZone, alwaysMaxExtent, otherMaxExtent) &&
                (zones.centreX[polygonZone] - x).let { dx -> (zones.centreY[polygonZone] - y).let { dy -> dx * dx + dy * dy <= r2 } }
            if (polygon < 0 || polygon == zoneFeature || polygonZoneCovers || nearerThanLabel) {
                val feature = index.layer.features[zoneFeature]
                return MapHit(feature.id, feature.isPlayable, viaTapZone = true)
            }
        }
    }
    if (polygon < 0) return null
    val feature = index.layer.features[polygon]
    return MapHit(feature.id, feature.isPlayable, viaTapZone = false)
}

/**
 * Picks among the zones whose centres lie within [tieDistance] of zone [best] (the nearest)
 * and whose circles contain the tap. If the nearest zone is listed (`tap_zones.bin`, the
 * microstates), only listed zones are considered: Vatican City beats Italy at its own tap
 * point even though the point lies in Italy's polygon. Among the considered zones, one whose
 * polygon contains the tap wins (the smallest if several do), else the nearest centre.
 */
private fun settleTie(
    index: FeatureHitIndex,
    zones: TapZones,
    best: Int,
    x: Float,
    y: Float,
    zoneRadius: Float,
    alwaysMaxExtent: Float,
    otherMaxExtent: Float,
    tieDistance: Float,
): Int {
    val t2 = tieDistance * tieDistance
    val r2 = zoneRadius * zoneRadius
    fun inCluster(i: Int): Boolean {
        if (!zones.visible(i, alwaysMaxExtent, otherMaxExtent)) return false
        val dx = zones.centreX[i] - zones.centreX[best]
        val dy = zones.centreY[i] - zones.centreY[best]
        if (dx * dx + dy * dy > t2) return false
        val px = zones.centreX[i] - x
        val py = zones.centreY[i] - y
        return px * px + py * py <= r2
    }
    // Listed zones take priority only when the nearest centre is itself listed. Wider
    // priorities (the whole tie cluster, or a fixed disc round each microstate) left
    // neighbours untappable at world zoom: 28 countries, then 16, then North Macedonia,
    // whose label is 1.4 dp from Kosovo's tap point.
    val listedFirst = zones.listed[best]

    var containing = -1
    var containingArea = Float.POSITIVE_INFINITY
    var nearest = -1
    var nearestD2 = Float.POSITIVE_INFINITY
    for (i in 0 until zones.size) {
        if (!inCluster(i) || (listedFirst && !zones.listed[i])) continue
        val px = zones.centreX[i] - x
        val py = zones.centreY[i] - y
        val d2 = px * px + py * py
        if (d2 < nearestD2) { nearest = i; nearestD2 = d2 }
        val f = zones.featureIndex[i]
        if (index.contains(f, x, y)) {
            val b = index.layer.features[f].bounds
            val a = b.width * b.height
            if (a < containingArea) { containing = i; containingArea = a }
        }
    }
    return when {
        containing >= 0 -> containing
        nearest >= 0 -> nearest
        else -> best
    }
}
