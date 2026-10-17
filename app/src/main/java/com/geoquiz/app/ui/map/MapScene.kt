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
import kotlinx.coroutines.CancellationException
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
    /**
     * The countries this view is about (a [com.geoquiz.app.domain.map.RegionPreset]'s members).
     * Other land is drawn as context in `mapLandInactive` and gets no tap zone, but can still be
     * tapped. Null: every country is a member.
     */
    val members: Set<String>? = null,
    /** How far past [contentBounds] the camera may pan, as a fraction of its size. */
    val panMarginFraction: Float = 0f,
    /**
     * An extra, independent layer composited on top of [land] (task 7.2, C2 review fix): drawn
     * with the same per-feature fill/border/outline/marker rules as [land] (see [MapCanvas]'s
     * `drawMap`), honouring its own [com.geoquiz.app.domain.map.GeometryKind], but with no
     * member/context concept, no tap zones and no exclusion of its own - every feature in it is
     * always drawn. Meant for a view with no answerable country at all (Rivers, mountains and
     * lakes highlights one feature over a non-playable country basemap this way, see
     * `PhysicalFeaturesGameScreen`), so a single synthetic feature can sit over [coarse]/[detail]
     * without disturbing their own two-level, member/tap-zone machinery (which assumes "the
     * country layer", singular). Null for every other caller - zero behaviour change.
     */
    val overlay: MapLayerPaths? = null,
) {
    init {
        require(coarse != null || detail != null) { "A map scene needs at least one country layer" }
    }

    /** The extent the camera is clamped to. */
    val contentBounds: PlaneRect =
        bounds ?: listOfNotNull(coarse?.bounds, detail?.bounds).reduce { a, b -> a.union(b) }

    private val includeInZones: (String) -> Boolean = { id -> members == null || id in members }
    private val coarseZones: TapZones? by lazy { coarse?.let { TapZones.build(it.projected, tapZones?.projected, includeInZones) } }
    private val detailZones: TapZones? by lazy { detail?.let { TapZones.build(it.projected, tapZones?.projected, includeInZones) } }

    // Task 4.7 review fix: Antarctica (under Asia), Canada/Alaska (across Asia/Oceania's top)
    // and the like keep a visible vertex under the azimuthal projection's 100 degree cutoff
    // ([LambertAzimuthalEqualAreaProjection.isVisible]) and so draw whole, far past that cutoff.
    // With a member set (a region preset), non-member land whose label point is more than
    // [FAR_CONTEXT_MAX_DEG] from the centre is dropped from both drawing and hit testing; members
    // are always kept. Null with no member set (World): nothing is dropped.
    private val coarseExcluded: BooleanArray? by lazy { coarse?.let { buildExcluded(it) } }
    private val detailExcluded: BooleanArray? by lazy { detail?.let { buildExcluded(it) } }

    private fun buildExcluded(layer: MapLayerPaths): BooleanArray? {
        if (members == null) return null
        return BooleanArray(layer.size) { i ->
            isFarContext(isMember(layer.ids[i]), projection.centreAngleDeg(layer.labelX[i], layer.labelY[i]))
        }
    }

    /** Whether [id] is drawn as a member (normal land) rather than context. */
    fun isMember(id: String): Boolean = members == null || id in members

    /** The country layer drawn at [level] (falls back to the other one if missing). */
    fun land(level: MapLevel): MapLayerPaths? = if (level == MapLevel.DETAIL) detail ?: coarse else coarse ?: detail

    /** Tap zones for the country layer drawn at [level]. */
    fun zones(level: MapLevel): TapZones? =
        if (level == MapLevel.DETAIL && detail != null || coarse == null) detailZones else coarseZones

    /**
     * Per-feature "drop this one" flags (see [isFarContext]) for the layer [land] draws at
     * [level], indexed the same way; null when the scene has no member set, so nothing is
     * dropped.
     */
    fun excluded(level: MapLevel): BooleanArray? =
        if (level == MapLevel.DETAIL && detail != null || coarse == null) detailExcluded else coarseExcluded
}

/** Non-member land further than this (angular, from a region preset's centre) is far-side
 * context rather than a neighbour worth seeing: task 4.7. */
const val FAR_CONTEXT_MAX_DEG = 80.0

/**
 * True if non-member land at [angleDeg] from the view's centre should be dropped from drawing
 * and hit testing. Members ([isMember] true) are never dropped, however far their label point
 * sits (a large member can still have a far corner). Pure, so it is unit-testable without a
 * projection or real geometry.
 */
fun isFarContext(isMember: Boolean, angleDeg: Double, maxDeg: Double = FAR_CONTEXT_MAX_DEG): Boolean =
    !isMember && angleDeg > maxDeg

/**
 * Process-wide cache of built paths, keyed by (layer, projection), so paths survive rotation,
 * theme changes and leaving and re-entering a screen. Layers come from [com.geoquiz.app.domain.map.MapRepository],
 * which returns one instance per [com.geoquiz.app.domain.map.GeoLayerId], so the key is in
 * effect (layer id, projection); the layer instance is part of the key so test fixtures with
 * the same id never collide. A build that failed or was cancelled is retried on the next
 * request. The whole world set (two country levels, lakes, rivers) is a few MB of paths.
 *
 * Bounded by [projectionCapacity] *projections* (not individual layer entries): switching
 * region presets builds several layers (coarse, detail, lakes, rivers, tap zones) under one new
 * [MapProjection] each time, so without this every preset ever visited in a session would keep
 * its paths forever. [projectionKeys] is an access-order LRU of the projections themselves;
 * evicting the oldest one drops every layer entry built under it. [clear] (memory pressure,
 * tests) drops everything; `GeographyQuizApplication.onTrimMemory`/`onLowMemory` call it once the
 * UI is hidden or memory is tight (task 9.2 review fix). Safe to call while a build is in flight:
 * [paths] and [clear] share the same monitor ([Synchronized]), and a running build never writes
 * back into [builtPaths] on completion (only [paths] does, when it creates the entry) - so a
 * clear mid-build just drops the map entry; the build itself finishes normally and its result is
 * discarded rather than resurrected into the cleared cache.
 */
class MapPathCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val projectionCapacity: Int = 3,
) {
    private data class Key(val layer: GeoLayer, val projection: MapProjection)

    // Named away from "entries": a Map's own `entries` property would otherwise shadow this
    // inside projectionKeys's removeEldestEntry below (it overrides a LinkedHashMap, whose
    // inherited `entries` is its own entry set, not this field).
    private val builtPaths = HashMap<Key, Deferred<MapLayerPaths>>()

    /** Access-order LRU of projections to the cache keys built under them; see the class doc. */
    private val projectionKeys = object : LinkedHashMap<MapProjection, MutableSet<Key>>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<MapProjection, MutableSet<Key>>): Boolean {
            if (size <= projectionCapacity) return false
            builtPaths.keys.removeAll(eldest.value)
            return true
        }
    }

    /** The paths for [layer] in [projection], building them in the background on first use. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun paths(layer: GeoLayer, projection: MapProjection): Deferred<MapLayerPaths> {
        val key = Key(layer, projection)
        projectionKeys.getOrPut(projection) { mutableSetOf() }.add(key)
        builtPaths[key]?.let { existing ->
            val failed = existing.isCompleted && existing.getCompletionExceptionOrNull() != null
            if (!failed) return existing
        }
        return scope.async { MapLayerPaths.build(layer, projection) }.also { builtPaths[key] = it }
    }

    /** Drops every entry (memory pressure, tests). Running builds finish but aren't kept. */
    @Synchronized
    fun clear() {
        builtPaths.clear()
        projectionKeys.clear()
    }

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
 *
 * A failed build (bad geometry, or an [OutOfMemoryError] projecting/triangulating a large layer)
 * never rethrows into composition (task 9.2 review fix): [value] is simply left null, same as
 * "still building", so a caller that only ever checks for a non-null [MapScene] keeps showing its
 * loading state instead of crashing. [MapPathCache] already treats a failed entry as retryable
 * (see its class doc), so the next request for the same (layer, projection) - a recomposition, a
 * rotation, or a cache [MapPathCache.clear] - tries again. [Throwable], not just [Exception], is
 * caught deliberately: the cache's own "did this fail" check
 * ([kotlinx.coroutines.Deferred.getCompletionExceptionOrNull]) already treats *any* throwable
 * completion the same way, so matching that here keeps the two in step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberMapLayerPaths(layer: GeoLayer?, projection: MapProjection): MapLayerPaths? {
    val cache = LocalMapPathCache.current
    val deferred = remember(cache, layer, projection) { layer?.let { cache.paths(it, projection) } }
    val ready = deferred?.takeIf { it.isCompleted && it.getCompletionExceptionOrNull() == null }?.getCompleted()
    // Remember which request produced the value, so stale paths are never returned.
    val awaited by produceState<Pair<Deferred<MapLayerPaths>, MapLayerPaths>?>(initialValue = null, deferred) {
        value = null
        val current = deferred ?: return@produceState
        value = try {
            current to current.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
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
    members: Set<String>? = null,
    panMarginFraction: Float = 0f,
    /** See [MapScene.overlay]. */
    overlay: GeoLayer? = null,
): MapScene? {
    val coarsePaths = rememberMapLayerPaths(coarse, projection)
    val detailPaths = rememberMapLayerPaths(detail, projection)
    val lakePaths = rememberMapLayerPaths(lakes, projection)
    val riverPaths = rememberMapLayerPaths(rivers, projection)
    val tapZonePaths = rememberMapLayerPaths(tapZones, projection)
    val overlayPaths = rememberMapLayerPaths(overlay, projection)
    return remember(
        projection, coarsePaths, detailPaths, lakePaths, riverPaths, bounds, tapZonePaths, members, panMarginFraction, overlayPaths
    ) {
        if (coarsePaths == null && detailPaths == null) null
        else MapScene(projection, coarsePaths, detailPaths, lakePaths, riverPaths, bounds, tapZonePaths, members, panMarginFraction, overlayPaths)
    }
}
