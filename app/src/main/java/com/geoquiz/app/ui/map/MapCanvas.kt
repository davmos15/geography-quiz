package com.geoquiz.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.geoquiz.app.domain.map.MapFeatureState
import com.geoquiz.app.ui.components.rememberReducedMotion
import com.geoquiz.app.ui.theme.GeoColors
import com.geoquiz.app.ui.theme.geoColors
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [MapScene] with pan, pinch zoom (about the pinch centre) and double-tap zoom
 * (2x, or back to the whole map when already at maximum zoom; animated unless reduced motion
 * is on). Stateless apart from the camera in [state].
 *
 * Layers, bottom to top: water background, land fills (playable land in `mapLand`, other land
 * in `mapLandInactive`, state fills), lakes, rivers, borders (a hairline that stays the same
 * on-screen width at any zoom), state outlines and patterns, then state markers. Paths are
 * built once (see [rememberMapScene]) and drawn under a canvas transform; features outside the
 * viewport are skipped.
 *
 * State styling, so colour is never the only cue (see [MapFeatureState]):
 * - Found: `mapFound` fill, a medium (1.5 dp) solid outline and a tick marker in `correct`.
 * - Wrong: `mapWrong` fill, a cross-hatch in the land colour, a medium outline and a cross
 *   marker in `wrong`.
 * - Highlighted: `mapHighlight` fill and a thick (3 dp) solid outline, no marker.
 * - Start: `mapStart` fill, a thick solid outline and a filled-dot marker.
 * - End: `mapEnd` fill, a thick dashed outline and a ring marker.
 *
 * Outlines use `onSurface`. Markers sit on the feature's label point at a fixed on-screen size
 * (a `surface` disc with an `onSurface` rim) and are drawn only when the feature is at least
 * [MARKER_MIN_FEATURE_DP] across on screen; tap zones (task 4.3) cover smaller ones.
 *
 * @param featureStates feature id to state; missing ids are [MapFeatureState.Default].
 * @param onTapProjected called on a single tap with the tapped point in projected coordinates
 *   (x east, y north, as returned by [com.geoquiz.app.domain.map.MapProjection.project]), so
 *   `scene.projection.inverse(x, y)` gives the longitude and latitude. A single tap is reported
 *   after the double-tap timeout.
 */
@Composable
fun MapCanvas(
    scene: MapScene,
    state: MapViewState,
    modifier: Modifier = Modifier,
    featureStates: Map<String, MapFeatureState> = emptyMap(),
    levelPreference: MapLevelPreference = MapLevelPreference.AUTO,
    showLakes: Boolean = true,
    showRivers: Boolean = true,
    onTapProjected: ((x: Float, y: Float) -> Unit)? = null,
) {
    val geo = MaterialTheme.geoColors
    val outline = MaterialTheme.colorScheme.onSurface
    val markerBackground = MaterialTheme.colorScheme.surface
    val palette = remember(geo, outline, markerBackground) { MapPalette.from(geo, outline, markerBackground) }
    val reducedMotion = rememberReducedMotion()
    val reducedMotionState = rememberUpdatedState(reducedMotion)
    val tapCallback by rememberUpdatedState(onTapProjected)
    val scope = rememberCoroutineScope()
    val cache = remember { MapDrawCache() }
    val stateCount = remember(featureStates) { featureStates.count { it.value != MapFeatureState.Default } }

    SideEffect { state.updateContentBounds(scene.contentBounds) }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { state.updateViewport(it.width.toFloat(), it.height.toFloat()) }
            .semantics { mapFeatureStateCount = stateCount }
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    state.cancelAnimation()
                    state.applyGesture(centroid.x, centroid.y, pan.x, pan.y, zoom)
                }
            }
            .pointerInput(state) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        state.cancelAnimation()
                        scope.launch { state.doubleTapZoom(at.x, at.y, animate = !reducedMotionState.value) }
                    },
                    onTap = { at ->
                        val callback = tapCallback
                        if (callback != null && state.isReady) {
                            callback(state.screenToPlaneX(at.x), -state.screenToPlaneY(at.y))
                        }
                    },
                )
            }
    ) {
        drawMap(scene, state, featureStates, levelPreference, showLakes, showRivers, palette, cache)
    }
}

/** Semantics for tests (not read by TalkBack; map accessibility is task 4.6). */
val MapFeatureStateCountKey = SemanticsPropertyKey<Int>("MapFeatureStateCount")
var SemanticsPropertyReceiver.mapFeatureStateCount by MapFeatureStateCountKey

/** A feature gets a state marker only when it is at least this wide or tall on screen. */
const val MARKER_MIN_FEATURE_DP = 12f

private const val BORDER_WIDTH_DP = 0.6f
private const val RIVER_WIDTH_DP = 0.8f
internal const val MEDIUM_OUTLINE_DP = 1.5f
internal const val THICK_OUTLINE_DP = 3f
private const val HATCH_SPACING_DP = 6f
private const val HATCH_WIDTH_DP = 1.25f
internal const val DASH_DP = 6f
internal const val MARKER_RADIUS_DP = 7f
private const val MARKER_RIM_DP = 1f
private const val MARKER_GLYPH_DP = 2f

@Immutable
internal class MapPalette(
    val water: Color,
    val land: Color,
    val inactiveLand: Color,
    val border: Color,
    val found: Color,
    val highlighted: Color,
    val wrong: Color,
    val start: Color,
    val end: Color,
    /** Thick outlines and marker rims, dots and rings (`onSurface`). */
    val outline: Color,
    val hatch: Color,
    /** Marker disc (`surface`). */
    val markerBackground: Color,
    /** Tick glyph (`correct`, blue). */
    val tick: Color,
    /** Cross glyph (`wrong`, orange). */
    val cross: Color,
) {
    companion object {
        /** Map colours from the theme tokens; [outline] is `onSurface`, [markerBackground] `surface`. */
        fun from(geo: GeoColors, outline: Color, markerBackground: Color) = MapPalette(
            water = geo.mapWater,
            land = geo.mapLand,
            inactiveLand = geo.mapLandInactive,
            border = geo.mapLandBorder,
            found = geo.mapFound,
            highlighted = geo.mapHighlight,
            wrong = geo.mapWrong,
            start = geo.mapStart,
            end = geo.mapEnd,
            outline = outline,
            hatch = geo.mapLand,
            markerBackground = markerBackground,
            tick = geo.correct,
            cross = geo.wrong,
        )
    }
}

/**
 * Strokes in plane units for the current scale, rebuilt only when the scale or density
 * changes, so a still frame (or a pan) allocates nothing. During a pinch the scale changes
 * every frame, which costs five small Stroke objects and one dash effect per frame.
 */
internal class MapDrawCache {
    private var scale = Float.NaN
    private var density = Float.NaN
    lateinit var border: Stroke
    lateinit var river: Stroke
    lateinit var medium: Stroke
    lateinit var thick: Stroke
    lateinit var dashed: Stroke
    var hatchSpacing = 0f
    var hatchWidth = 0f

    // Screen-space sizes (pixels), independent of zoom.
    var markerRadius = 0f
    var markerRim = 0f
    var markerGlyph = 0f
    var markerMinExtent = 0f
    lateinit var markerRimStroke: Stroke
    lateinit var markerRingStroke: Stroke

    fun update(scale: Float, density: Float) {
        if (scale == this.scale && density == this.density) return
        this.scale = scale
        this.density = density
        val px = 1f / scale
        border = Stroke(width = max(1f, BORDER_WIDTH_DP * density) * px, join = StrokeJoin.Round)
        river = Stroke(width = max(1f, RIVER_WIDTH_DP * density) * px, cap = StrokeCap.Round, join = StrokeJoin.Round)
        medium = Stroke(width = MEDIUM_OUTLINE_DP * density * px, join = StrokeJoin.Round)
        thick = Stroke(width = THICK_OUTLINE_DP * density * px, join = StrokeJoin.Round)
        val dash = DASH_DP * density * px
        dashed = Stroke(
            width = THICK_OUTLINE_DP * density * px,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.75f)),
        )
        hatchSpacing = HATCH_SPACING_DP * density * px
        hatchWidth = HATCH_WIDTH_DP * density * px
        markerRadius = MARKER_RADIUS_DP * density
        markerRim = MARKER_RIM_DP * density
        markerGlyph = MARKER_GLYPH_DP * density
        markerMinExtent = MARKER_MIN_FEATURE_DP * density
        if (!::markerRimStroke.isInitialized || density != markerDensity) {
            markerDensity = density
            markerRimStroke = Stroke(markerRim)
            markerRingStroke = Stroke(markerGlyph)
        }
    }

    private var markerDensity = Float.NaN
}

/** The whole map frame; internal so tests can render it offscreen. */
internal fun DrawScope.drawMap(
    scene: MapScene,
    state: MapViewState,
    featureStates: Map<String, MapFeatureState>,
    levelPreference: MapLevelPreference,
    showLakes: Boolean,
    showRivers: Boolean,
    palette: MapPalette,
    cache: MapDrawCache,
) {
    drawRect(palette.water)
    if (!state.isReady) return
    val scale = state.scale
    val offsetX = state.offsetX
    val offsetY = state.offsetY
    val level = resolveMapLevel(
        levelPreference,
        dpPerUnit = scale / density,
        coarseAvailable = scene.coarse != null,
        detailAvailable = scene.detail != null,
    )
    val land = (if (level == MapLevel.DETAIL) scene.detail else scene.coarse) ?: return
    cache.update(scale, density)

    // Visible plane rectangle, for culling.
    val vMinX = -offsetX / scale
    val vMinY = -offsetY / scale
    val vMaxX = (size.width - offsetX) / scale
    val vMaxY = (size.height - offsetY) / scale

    // Paths are drawn under a scale of up to maxZoom x fit. Device check (API 26/27 HWUI): a
    // large filled path at high zoom can exceed the path texture size ("path too large to be
    // rendered into a texture") and be skipped; culling keeps most off-screen features out,
    // but a big country filling the screen at max zoom is the case to look at on old devices.
    withTransform({
        translate(offsetX, offsetY)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        // Land fills.
        for (i in 0 until land.size) {
            if (!land.visible(i, vMinX, vMinY, vMaxX, vMaxY)) continue
            val featureState = featureStates[land.ids[i]] ?: MapFeatureState.Default
            val fill = when (featureState) {
                MapFeatureState.Default -> if (land.playable[i]) palette.land else palette.inactiveLand
                MapFeatureState.Found -> palette.found
                MapFeatureState.Highlighted -> palette.highlighted
                MapFeatureState.Start -> palette.start
                MapFeatureState.End -> palette.end
                MapFeatureState.Wrong -> palette.wrong
            }
            drawPath(land.paths[i], fill)
        }

        // Water on top of land.
        val lakes = scene.lakes
        if (showLakes && lakes != null) {
            for (i in 0 until lakes.size) {
                if (lakes.visible(i, vMinX, vMinY, vMaxX, vMaxY)) drawPath(lakes.paths[i], palette.water)
            }
        }
        val rivers = scene.rivers
        if (showRivers && rivers != null) {
            for (i in 0 until rivers.size) {
                if (rivers.visible(i, vMinX, vMinY, vMaxX, vMaxY)) drawPath(rivers.paths[i], palette.water, style = cache.river)
            }
        }

        // Borders and coastlines.
        for (i in 0 until land.size) {
            if (land.visible(i, vMinX, vMinY, vMaxX, vMaxY)) drawPath(land.paths[i], palette.border, style = cache.border)
        }

        // Outlines and patterns for states, on top so neighbours' borders don't cover them.
        if (featureStates.isNotEmpty()) {
            for (i in 0 until land.size) {
                if (!land.visible(i, vMinX, vMinY, vMaxX, vMaxY)) continue
                val featureState = featureStates[land.ids[i]] ?: continue
                val path = land.paths[i]
                when (featureState) {
                    MapFeatureState.Default -> Unit
                    MapFeatureState.Found -> drawPath(path, palette.outline, style = cache.medium)
                    MapFeatureState.Wrong -> {
                        clipPath(path) {
                            drawCrossHatch(
                                max(land.minX[i], vMinX), max(land.minY[i], vMinY),
                                min(land.maxX[i], vMaxX), min(land.maxY[i], vMaxY),
                                cache.hatchSpacing, cache.hatchWidth, palette.hatch,
                            )
                        }
                        drawPath(path, palette.outline, style = cache.medium)
                    }
                    MapFeatureState.Highlighted, MapFeatureState.Start -> drawPath(path, palette.outline, style = cache.thick)
                    MapFeatureState.End -> drawPath(path, palette.outline, style = cache.dashed)
                }
            }
        }
    }

    // Markers, in screen space at a fixed size.
    if (featureStates.isEmpty()) return
    val r = cache.markerRadius
    for (i in 0 until land.size) {
        val featureState = featureStates[land.ids[i]] ?: continue
        if (featureState == MapFeatureState.Default || featureState == MapFeatureState.Highlighted) continue
        val extent = max(land.maxX[i] - land.minX[i], land.maxY[i] - land.minY[i]) * scale
        if (extent < cache.markerMinExtent) continue
        val x = land.labelX[i] * scale + offsetX
        val y = land.labelY[i] * scale + offsetY
        if (x < -r || y < -r || x > size.width + r || y > size.height + r) continue
        drawStateMarker(featureState, x, y, palette, cache)
    }
}

/** One marker centred on (x, y) screen pixels. */
private fun DrawScope.drawStateMarker(featureState: MapFeatureState, x: Float, y: Float, palette: MapPalette, cache: MapDrawCache) {
    val r = cache.markerRadius
    val centre = Offset(x, y)
    drawCircle(palette.markerBackground, radius = r, center = centre)
    drawCircle(palette.outline, radius = r - cache.markerRim / 2, center = centre, style = cache.markerRimStroke)
    val g = cache.markerGlyph
    when (featureState) {
        MapFeatureState.Found -> {
            // Tick: short stroke down-right, long stroke up-right.
            val a = Offset(x - 0.45f * r, y + 0.02f * r)
            val b = Offset(x - 0.12f * r, y + 0.35f * r)
            val c = Offset(x + 0.45f * r, y - 0.35f * r)
            drawLine(palette.tick, a, b, strokeWidth = g, cap = StrokeCap.Round)
            drawLine(palette.tick, b, c, strokeWidth = g, cap = StrokeCap.Round)
        }
        MapFeatureState.Wrong -> {
            val d = 0.38f * r
            drawLine(palette.cross, Offset(x - d, y - d), Offset(x + d, y + d), strokeWidth = g, cap = StrokeCap.Round)
            drawLine(palette.cross, Offset(x - d, y + d), Offset(x + d, y - d), strokeWidth = g, cap = StrokeCap.Round)
        }
        // Start: a filled dot.
        MapFeatureState.Start -> drawCircle(palette.outline, radius = 0.45f * r, center = centre)
        // End: a ring.
        MapFeatureState.End -> drawCircle(palette.outline, radius = 0.42f * r, center = centre, style = cache.markerRingStroke)
        MapFeatureState.Default, MapFeatureState.Highlighted -> Unit
    }
}

private fun MapLayerPaths.visible(i: Int, vMinX: Float, vMinY: Float, vMaxX: Float, vMaxY: Float): Boolean =
    minX[i] <= vMaxX && maxX[i] >= vMinX && minY[i] <= vMaxY && maxY[i] >= vMinY

/**
 * Lines at +45° and -45° across the rectangle, [spacing] apart (plane units). The lines sit on
 * a grid anchored to the plane, so the hatch moves with the map when it pans (it is re-spaced
 * when the zoom changes, to keep a fixed on-screen spacing). Clipped to the feature by the
 * caller; a tiled shader would avoid the clip but needs its matrix re-anchored every frame, so
 * lines are kept (at most a few hundred per Wrong feature, viewport-limited).
 */
internal fun DrawScope.drawCrossHatch(
    minX: Float, minY: Float, maxX: Float, maxY: Float,
    spacing: Float, width: Float, color: Color,
) {
    if (maxX <= minX || maxY <= minY || spacing <= 0f) return
    // Lines y = x - c, for c from (minX - maxY) to (maxX - minY), c on the spacing grid.
    var c = hatchGridStart(minX - maxY, spacing)
    while (c <= maxX - minY) {
        drawLine(color, Offset(minX, minX - c), Offset(maxX, maxX - c), strokeWidth = width)
        c += spacing
    }
    // Lines y = c - x, for c from (minX + minY) to (maxX + maxY).
    c = hatchGridStart(minX + minY, spacing)
    while (c <= maxX + maxY) {
        drawLine(color, Offset(minX, c - minX), Offset(maxX, c - maxX), strokeWidth = width)
        c += spacing
    }
}

/** The first multiple of [spacing] at or below [from]: hatch lines stay put while panning. */
internal fun hatchGridStart(from: Float, spacing: Float): Float = floor(from / spacing) * spacing
