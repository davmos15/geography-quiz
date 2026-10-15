package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.map.BoundsAccumulator
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.PlaneRect

/**
 * The plane rectangle that frames [memberCodes] on [layer] in [projection] (task 6.2): the union
 * of their bounds, padded by [paddingFraction] of the larger side. Modelled on
 * [com.geoquiz.app.domain.map.frame] (`RegionPreset`'s own framing), which this does not call:
 * that extension is tied to [com.geoquiz.app.domain.map.RegionPreset] (D43 review says not to
 * extend it), and admin1 parts are never flagged remote or split at the antimeridian
 * (`data/geo/FORMAT.md`: the remote flag is admin-0 only), so this needs none of its extra
 * handling. Null if no member is in [layer].
 */
fun usCaFrame(
    projection: MapProjection,
    layer: GeoLayer,
    memberCodes: Set<String>,
    paddingFraction: Float = 0.08f
): PlaneRect? {
    val acc = BoundsAccumulator()
    val scratch = DoubleArray(2)
    for (code in memberCodes) {
        val feature = layer.feature(code) ?: continue
        for (part in feature.parts) {
            for (i in 0 until part.size) {
                val lon = part.lon[i].toDouble()
                val lat = part.lat[i].toDouble()
                if (!projection.isVisible(lon, lat)) continue
                acc.add(projection, lon, lat, scratch)
            }
        }
    }
    val rect = acc.rect() ?: return null
    return rect.inflate(maxOf(rect.width, rect.height) * paddingFraction)
}
