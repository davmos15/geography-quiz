package com.geoquiz.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.geoquiz.app.domain.map.FeatureHitIndex
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.TapZones
import com.geoquiz.app.domain.map.GeometryKind
import com.geoquiz.app.domain.map.MapProjection
import com.geoquiz.app.domain.map.PlaneRect
import com.geoquiz.app.domain.map.ProjectedLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Draw-ready paths for one layer: one [Path] per feature in plane coordinates, built once and
 * drawn every frame under a canvas transform. Arrays rather than lists so the draw loop
 * doesn't allocate iterators; feature bounds are unpacked into float arrays for culling.
 */
class MapLayerPaths(
    val projected: ProjectedLayer,
    val paths: Array<Path>,
    /** Wall-clock time to project the layer and build its paths (debug readout). */
    val buildMillis: Long,
    /** Point-in-polygon index (polygon layers only), built with the paths. */
    val hitIndex: FeatureHitIndex? = null,
) {
    val size: Int = paths.size
    val ids: Array<String> = Array(size) { projected.features[it].id }
    val playable: BooleanArray = BooleanArray(size) { projected.features[it].isPlayable }
    val minX = FloatArray(size) { projected.features[it].bounds.minX }
    val minY = FloatArray(size) { projected.features[it].bounds.minY }
    val maxX = FloatArray(size) { projected.features[it].bounds.maxX }
    val maxY = FloatArray(size) { projected.features[it].bounds.maxY }
    val labelX = FloatArray(size) { projected.features[it].labelX }
    val labelY = FloatArray(size) { projected.features[it].labelY }
    val bounds: PlaneRect get() = projected.bounds

    fun indexOf(id: String): Int = ids.indexOf(id)

    companion object {
        /** Projects [layer] and builds its paths. Pure CPU work: call it off the main thread. */
        fun build(layer: GeoLayer, projection: MapProjection): MapLayerPaths {
            val start = System.nanoTime()
            val projected = ProjectedLayer.build(layer, projection)
            val close = layer.kind == GeometryKind.POLYGON
            val paths = Array(projected.features.size) { i ->
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    for (ring in projected.features[i].rings) {
                        if (ring.size < 4) continue
                        moveTo(ring[0], ring[1])
                        var p = 2
                        while (p < ring.size) {
                            lineTo(ring[p], ring[p + 1])
                            p += 2
                        }
                        if (close) close()
                    }
                }
            }
            val hitIndex = if (close) FeatureHitIndex(projected) else null
            return MapLayerPaths(projected, paths, (System.nanoTime() - start) / 1_000_000, hitIndex)
        }
    }
}

/**
 * Everything [MapCanvas] draws: country polygons at two levels plus optional water layers,
 * all in one [projection].
 */
@Immutable
class MapScene(
    val projection: MapProjection,
    /** `admin0_110m` (or any polygon layer for the zoomed-out view). */
    val coarse: MapLayerPaths?,
    /** `admin0_50m`, used above [DETAIL_LEVEL_DP_PER_UNIT]. */
    val detail: MapLayerPaths?,
    val lakes: MapLayerPaths? = null,
    val rivers: MapLayerPaths? = null,
    /** Extent to clamp the camera to; defaults to the union of the country layers. */
    bounds: PlaneRect? = null,
    /** `tap_zones` (point layer): tap centres for the small countries that always get a zone. */
    val tapZones: MapLayerPaths? = null,
) {
    init {
        require(coarse != null || detail != null) { "A map scene needs at least one country layer" }
    }

    /** The extent the camera is clamped to. */
    val contentBounds: PlaneRect =
        bounds ?: listOfNotNull(coarse?.bounds, detail?.bounds).reduce { a, b -> a.union(b) }

    private val coarseZones: TapZones? by lazy { coarse?.let { TapZones.build(it.projected, tapZones?.projected) } }
    private val detailZones: TapZones? by lazy { detail?.let { TapZones.build(it.projected, tapZones?.projected) } }

    /** The country layer drawn at [level] (falls back to the other one if missing). */
    fun land(level: MapLevel): MapLayerPaths? = if (level == MapLevel.DETAIL) detail ?: coarse else coarse ?: detail

    /** Tap zones for the country layer drawn at [level]. */
    fun zones(level: MapLevel): TapZones? =
        if (level == MapLevel.DETAIL && detail != null || coarse == null) detailZones else coarseZones
}

/**
 * Process-wide cache of built paths, keyed by (layer, projection), so paths survive rotation,
 * theme changes and leaving and re-entering a screen. Layers come from [com.geoquiz.app.domain.map.MapRepository],
 * which returns one instance per [com.geoquiz.app.domain.map.GeoLayerId], so the key is in
 * effect (layer id, projection); the layer instance is part of the key so test fixtures with
 * the same id never collide. A build that failed or was cancelled is retried on the next
 * request. The whole world set (two country levels, lakes, rivers) is a few MB of paths.
 */
class MapPathCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private data class Key(val layer: GeoLayer, val projection: MapProjection)

    private val entries = HashMap<Key, Deferred<MapLayerPaths>>()

    /** The paths for [layer] in [projection], building them in the background on first use. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun paths(layer: GeoLayer, projection: MapProjection): Deferred<MapLayerPaths> {
        val key = Key(layer, projection)
        entries[key]?.let { existing ->
            val failed = existing.isCompleted && existing.getCompletionExceptionOrNull() != null
            if (!failed) return existing
        }
        return scope.async { MapLayerPaths.build(layer, projection) }.also { entries[key] = it }
    }

    /** Drops every entry (memory pressure, tests). Running builds finish but aren't kept. */
    @Synchronized
    fun clear() = entries.clear()

    companion object {
        /** The app's cache; also provided by Hilt (`MapModule`) so ViewModels can pre-warm it. */
        val Shared = MapPathCache()
    }
}

/** The [MapPathCache] that map composables use; override in tests. */
val LocalMapPathCache = staticCompositionLocalOf { MapPathCache.Shared }

/**
 * A layer's paths from [LocalMapPathCache], or null while they build (or when [layer] is null).
 * Paths that are already built are returned on the first composition, so a rotation or theme
 * change never shows an empty map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberMapLayerPaths(layer: GeoLayer?, projection: MapProjection): MapLayerPaths? {
    val cache = LocalMapPathCache.current
    val deferred = remember(cache, layer, projection) { layer?.let { cache.paths(it, projection) } }
    val ready = deferred?.takeIf { it.isCompleted && it.getCompletionExceptionOrNull() == null }?.getCompleted()
    // Remember which request produced the value, so stale paths are never returned.
    val awaited by produceState<Pair<Deferred<MapLayerPaths>, MapLayerPaths>?>(initialValue = null, deferred) {
        value = deferred?.let { it to it.await() }
    }
    return ready ?: awaited?.takeIf { it.first === deferred }?.second
}

/**
 * A [MapScene] from decoded layers, or null until at least one country layer is ready. Each
 * layer is projected once in the background and kept in [LocalMapPathCache].
 */
@Composable
fun rememberMapScene(
    projection: MapProjection,
    coarse: GeoLayer?,
    detail: GeoLayer? = null,
    lakes: GeoLayer? = null,
    rivers: GeoLayer? = null,
    bounds: PlaneRect? = null,
    tapZones: GeoLayer? = null,
): MapScene? {
    val coarsePaths = rememberMapLayerPaths(coarse, projection)
    val detailPaths = rememberMapLayerPaths(detail, projection)
    val lakePaths = rememberMapLayerPaths(lakes, projection)
    val riverPaths = rememberMapLayerPaths(rivers, projection)
    val tapZonePaths = rememberMapLayerPaths(tapZones, projection)
    return remember(projection, coarsePaths, detailPaths, lakePaths, riverPaths, bounds, tapZonePaths) {
        if (coarsePaths == null && detailPaths == null) null
        else MapScene(projection, coarsePaths, detailPaths, lakePaths, riverPaths, bounds, tapZonePaths)
    }
}
