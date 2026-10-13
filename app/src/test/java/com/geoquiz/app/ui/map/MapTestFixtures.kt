package com.geoquiz.app.ui.map

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoFeature
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.GeoPart
import com.geoquiz.app.domain.map.GeoProperties
import com.geoquiz.app.domain.map.GeoPropertyDef
import com.geoquiz.app.domain.map.GeoPropertyType
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.map.MapProjection

/** Identity "projection" for pixel tests: x = lon, y = lat, so plane = (lon, -lat). */
data object PlateProjection : MapProjection {
    override fun project(lonDeg: Double, latDeg: Double, out: DoubleArray) {
        out[0] = lonDeg
        out[1] = latDeg
    }

    override fun inverse(x: Double, y: Double, out: DoubleArray): Boolean {
        out[0] = x
        out[1] = y
        return true
    }
}

private val playableSchema = listOf(GeoPropertyDef("playable", GeoPropertyType.UINT8))

/**
 * A square country covering plane x and y from [min] to [max] (lat = -plane y), labelled at
 * its centre.
 */
fun squareFeature(id: String, min: Float, max: Float, playable: Boolean = true): GeoFeature {
    val part = GeoPart(0, floatArrayOf(min, max, max, min), floatArrayOf(-max, -max, -min, -min))
    val centre = (min + max) / 2.0
    return GeoFeature(
        id, GeoProperties(playableSchema, arrayOf(if (playable) 1 else 0)),
        GeoBounds(min.toDouble(), -max.toDouble(), max.toDouble(), -min.toDouble()),
        centre, -centre, listOf(part),
    )
}

fun squareLayer(vararg features: GeoFeature, id: GeoLayerId = GeoLayerId.ADMIN0_110M): GeoLayer =
    GeoLayer(id, GeometryKind.POLYGON, GeoBounds(-180.0, -90.0, 180.0, 90.0), "test", playableSchema, features.toList())
