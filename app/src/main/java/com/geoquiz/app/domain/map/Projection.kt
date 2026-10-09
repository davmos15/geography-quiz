package com.geoquiz.app.domain.map

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A map projection on the unit sphere (R = 1), so projected units are about one radian of
 * great-circle distance near the centre. Output is the usual map convention: x east, y north.
 *
 * The renderer works in *plane* coordinates, which are the projected coordinates with y
 * flipped to point down like the screen: `planeX = x`, `planeY = -y` (see [toPlane]).
 *
 * Implementations are value-like (an object or a data class), so they can key caches.
 */
interface MapProjection {

    /** Projects degrees to (x, y), written to `out[0]`, `out[1]`. No allocation. */
    fun project(lonDeg: Double, latDeg: Double, out: DoubleArray)

    /**
     * Inverse of [project]: (x, y) to (lon, lat) in degrees, written to `out[0]`, `out[1]`.
     * Returns false (and leaves [out] unspecified) if the point is outside the projection's
     * domain, for example off the edge of the world map.
     */
    fun inverse(x: Double, y: Double, out: DoubleArray): Boolean

    fun project(lonDeg: Double, latDeg: Double): ProjectedPoint {
        val out = DoubleArray(2)
        project(lonDeg, latDeg, out)
        return ProjectedPoint(out[0], out[1])
    }

    /** (lon, lat) in degrees, or null outside the domain. */
    fun inverse(x: Double, y: Double): ProjectedPoint? {
        val out = DoubleArray(2)
        return if (inverse(x, y, out)) ProjectedPoint(out[0], out[1]) else null
    }
}

/** A projected (x, y), or a (lon, lat) from [MapProjection.inverse]. */
data class ProjectedPoint(val x: Double, val y: Double)

/**
 * Equal Earth (Šavrič, Patterson and Jenny 2018), central meridian 0°: an equal-area
 * pseudocylindrical projection for world maps. x spans ±2.7066 and y ±1.3174.
 */
data object EqualEarthProjection : MapProjection {
    private const val A1 = 1.340264
    private const val A2 = -0.081106
    private const val A3 = 0.000893
    private const val A4 = 0.003796
    private val M = sqrt(3.0) / 2.0
    private const val DEG = Math.PI / 180.0

    /** x of the ±180° meridian at the equator. */
    val maxX: Double = Math.PI / (M * A1)

    /** y of the poles. */
    val maxY: Double = run {
        val t = asin(M)
        val t2 = t * t
        val t6 = t2 * t2 * t2
        t * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    }

    override fun project(lonDeg: Double, latDeg: Double, out: DoubleArray) {
        val lambda = lonDeg * DEG
        val theta = asin(M * sin(latDeg * DEG))
        val t2 = theta * theta
        val t6 = t2 * t2 * t2
        out[0] = lambda * cos(theta) / (M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)))
        out[1] = theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2))
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        if (abs(y) > maxY + 1e-9) return false
        // Newton's method for theta from y (converges in a few steps; the polynomial is monotonic).
        var theta = y / A1
        for (step in 0 until 12) {
            val t2 = theta * theta
            val t6 = t2 * t2 * t2
            val f = theta * (A1 + A2 * t2 + t6 * (A3 + A4 * t2)) - y
            val fPrime = A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)
            val delta = f / fPrime
            theta -= delta
            if (abs(delta) < 1e-12) break
        }
        val t2 = theta * theta
        val t6 = t2 * t2 * t2
        val lambda = M * x * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)) / cos(theta)
        if (abs(lambda) > Math.PI + 1e-9) return false
        out[0] = lambda / DEG
        out[1] = asin((sin(theta) / M).coerceIn(-1.0, 1.0)) / DEG
        return true
    }
}

/**
 * Lambert azimuthal equal-area (spherical) centred on ([centreLon], [centreLat]) degrees, for
 * continent and region views. The centre maps to (0, 0); the whole sphere fits in a disc of
 * radius 2 (the antipode is the rim). Longitudes wrap naturally, so features that cross the
 * antimeridian stay contiguous when the centre is near them.
 */
data class LambertAzimuthalEqualAreaProjection(val centreLon: Double, val centreLat: Double) : MapProjection {
    private val lambda0 = centreLon * DEG
    private val sinPhi0 = sin(centreLat * DEG)
    private val cosPhi0 = cos(centreLat * DEG)

    override fun project(lonDeg: Double, latDeg: Double, out: DoubleArray) {
        val phi = latDeg * DEG
        val dLambda = lonDeg * DEG - lambda0
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val cosDl = cos(dLambda)
        val denom = 1 + sinPhi0 * sinPhi + cosPhi0 * cosPhi * cosDl
        // The antipode has no single image; put it on the rim rather than at infinity.
        val k = if (denom <= 1e-12) 0.0 else sqrt(2 / denom)
        out[0] = if (denom <= 1e-12) 2.0 else k * cosPhi * sin(dLambda)
        out[1] = k * (cosPhi0 * sinPhi - sinPhi0 * cosPhi * cosDl)
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        val rho = sqrt(x * x + y * y)
        if (rho > 2 + 1e-9) return false
        if (rho < 1e-12) {
            out[0] = centreLon
            out[1] = centreLat
            return true
        }
        val c = 2 * asin(min(1.0, rho / 2))
        val sinC = sin(c)
        val cosC = cos(c)
        val phi = asin((cosC * sinPhi0 + y * sinC * cosPhi0 / rho).coerceIn(-1.0, 1.0))
        val lambda = lambda0 + atan2(x * sinC, rho * cosPhi0 * cosC - y * sinPhi0 * sinC)
        out[0] = normaliseLon(lambda / DEG)
        out[1] = phi / DEG
        return true
    }

    private companion object {
        const val DEG = Math.PI / 180.0

        fun normaliseLon(lon: Double): Double {
            var l = lon
            while (l > 180) l -= 360
            while (l < -180) l += 360
            return l
        }
    }
}

/**
 * An axis-aligned rectangle in plane coordinates (projected x, and y pointing down; see
 * [MapProjection]).
 */
data class PlaneRect(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centreX: Float get() = (minX + maxX) / 2
    val centreY: Float get() = (minY + maxY) / 2

    fun intersects(minX: Float, minY: Float, maxX: Float, maxY: Float): Boolean =
        this.minX <= maxX && this.maxX >= minX && this.minY <= maxY && this.maxY >= minY

    fun contains(x: Float, y: Float): Boolean = x in minX..maxX && y in minY..maxY

    fun union(other: PlaneRect): PlaneRect = PlaneRect(
        min(minX, other.minX), min(minY, other.minY), max(maxX, other.maxX), max(maxY, other.maxY)
    )

    /** Grown by [amount] on every side. */
    fun inflate(amount: Float): PlaneRect = PlaneRect(minX - amount, minY - amount, maxX + amount, maxY + amount)
}

/** Projects degrees to plane coordinates (`out[0]` = x, `out[1]` = -y). [scratch] holds 2 doubles. */
fun MapProjection.toPlane(lonDeg: Double, latDeg: Double, scratch: DoubleArray, out: FloatArray) {
    project(lonDeg, latDeg, scratch)
    out[0] = scratch[0].toFloat()
    out[1] = (-scratch[1]).toFloat()
}

/**
 * Plane bounds of a lon/lat rectangle, sampling each edge (a projected rectangle's extreme
 * points are not always its corners: Equal Earth meridians bulge at the equator).
 */
fun MapProjection.planeBounds(bounds: GeoBounds, samplesPerEdge: Int = 16): PlaneRect {
    val acc = BoundsAccumulator()
    val scratch = DoubleArray(2)
    for (i in 0..samplesPerEdge) {
        val t = i.toDouble() / samplesPerEdge
        val lon = bounds.minLon + (bounds.maxLon - bounds.minLon) * t
        val lat = bounds.minLat + (bounds.maxLat - bounds.minLat) * t
        acc.add(this, lon, bounds.minLat, scratch)
        acc.add(this, lon, bounds.maxLat, scratch)
        acc.add(this, bounds.minLon, lat, scratch)
        acc.add(this, bounds.maxLon, lat, scratch)
    }
    return acc.rect()!!
}

/**
 * Plane bounds of a feature's geometry (or its label point if it has none). With
 * [includeRemote] false, parts flagged remote (far-flung islands) are left out, which is what
 * framing a country wants; if every part is remote, all parts are used.
 */
fun MapProjection.planeBounds(feature: GeoFeature, includeRemote: Boolean = true): PlaneRect {
    val acc = BoundsAccumulator()
    val scratch = DoubleArray(2)
    val useAll = includeRemote || feature.parts.all { it.isRemote }
    for (part in feature.parts) {
        if (!useAll && part.isRemote) continue
        for (i in 0 until part.size) acc.add(this, part.lon[i].toDouble(), part.lat[i].toDouble(), scratch)
    }
    if (acc.isEmpty) acc.add(this, feature.labelLon, feature.labelLat, scratch)
    return acc.rect()!!
}

/** Running min/max in plane coordinates. */
internal class BoundsAccumulator {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY

    val isEmpty: Boolean get() = minX > maxX

    fun add(x: Float, y: Float) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }

    fun add(projection: MapProjection, lon: Double, lat: Double, scratch: DoubleArray) {
        projection.project(lon, lat, scratch)
        add(scratch[0].toFloat(), (-scratch[1]).toFloat())
    }

    fun add(rect: PlaneRect) {
        add(rect.minX, rect.minY)
        add(rect.maxX, rect.maxY)
    }

    fun rect(): PlaneRect? = if (isEmpty) null else PlaneRect(minX, minY, maxX, maxY)
}
