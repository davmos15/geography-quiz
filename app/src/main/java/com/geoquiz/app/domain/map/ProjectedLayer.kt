package com.geoquiz.app.domain.map

/**
 * A [GeoLayer] projected once into plane coordinates (see [MapProjection]), ready to turn
 * into draw paths and to hit-test against. Build it off the main thread: the 50m world layer
 * has about 70 000 points.
 */
class ProjectedLayer(
    val layerId: GeoLayerId,
    val kind: GeometryKind,
    val projection: MapProjection,
    /** Same order as the source layer (draw order). */
    val features: List<ProjectedFeature>,
    /** Union of all feature bounds. */
    val bounds: PlaneRect,
) {
    companion object {
        fun build(layer: GeoLayer, projection: MapProjection): ProjectedLayer {
            val scratch = DoubleArray(2)
            val layerAcc = BoundsAccumulator()
            val features = layer.features.map { feature ->
                val acc = BoundsAccumulator()
                val rings = Array(feature.parts.size) { p ->
                    val part = feature.parts[p]
                    val xy = FloatArray(part.size * 2)
                    for (i in 0 until part.size) {
                        projection.project(part.lon[i].toDouble(), part.lat[i].toDouble(), scratch)
                        val x = scratch[0].toFloat()
                        val y = (-scratch[1]).toFloat()
                        xy[2 * i] = x
                        xy[2 * i + 1] = y
                        acc.add(x, y)
                    }
                    xy
                }
                projection.project(feature.labelLon, feature.labelLat, scratch)
                val labelX = scratch[0].toFloat()
                val labelY = (-scratch[1]).toFloat()
                if (acc.isEmpty) acc.add(labelX, labelY)
                val bounds = acc.rect()!!
                layerAcc.add(bounds)
                ProjectedFeature(
                    id = feature.id,
                    isPlayable = feature.isPlayable,
                    rings = rings,
                    partFlags = IntArray(feature.parts.size) { feature.parts[it].flags },
                    bounds = bounds,
                    labelX = labelX,
                    labelY = labelY,
                )
            }
            return ProjectedLayer(
                layerId = layer.id,
                kind = layer.kind,
                projection = projection,
                features = features,
                bounds = layerAcc.rect() ?: PlaneRect(0f, 0f, 0f, 0f),
            )
        }
    }
}

/**
 * One feature in plane coordinates.
 *
 * @property rings one interleaved `[x0, y0, x1, y1, ...]` array per part (rings are open:
 *   close them when drawing); empty for point features.
 * @property partFlags [GeoPart] flags per ring.
 */
class ProjectedFeature(
    val id: String,
    val isPlayable: Boolean,
    val rings: Array<FloatArray>,
    val partFlags: IntArray,
    val bounds: PlaneRect,
    val labelX: Float,
    val labelY: Float,
) {
    val pointCount: Int get() = rings.sumOf { it.size / 2 }
}
