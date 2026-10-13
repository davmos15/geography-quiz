package com.geoquiz.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import com.geoquiz.app.domain.map.OutlineModel
import com.geoquiz.app.domain.map.OutlineRing
import com.geoquiz.app.ui.theme.geoColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlin.math.max
import kotlin.math.min

/**
 * Draws one country's silhouette from [outline] (see [com.geoquiz.app.domain.map.OutlineGeometry]):
 * the country fills [fill] (default `mapLand`) with a `mapLandBorder` outline, aspect-fit inside
 * [modifier]'s bounds with a small padding; optional faint neighbouring land ([showNeighbours])
 * fills `mapLandInactive` with a thinner border. Nothing outside the fitted box is drawn
 * (`clipToBounds`), which also catches the small overshoot [OutlineGeometry] allows on the long
 * axis of very elongated countries. Stateless and gesture-free: this is just the shape.
 *
 * Paths are built off the main thread from [outline]'s plain geometry and kept in a small LRU
 * (see [CountryOutlinePathCache]), keyed by (cca3, includeRemote, showNeighbours), so re-showing
 * a country (stepping back and forth in a quiz) doesn't rebuild its path.
 *
 * Accessibility: [contentDescription] is the caller's job to supply (or leave null). The
 * silhouette must not leak the answer on its own, so the default is `null`, which marks this
 * composable decorative ([androidx.compose.ui.semantics.invisibleToUser]) rather than an
 * unlabelled image a screen reader would stop on. A mode that needs this to be announced (for
 * example once the country is guessed) should pass a description that doesn't give away an
 * unguessed answer.
 */
@Composable
fun CountryOutline(
    outline: OutlineModel,
    modifier: Modifier = Modifier,
    showNeighbours: Boolean = true,
    fill: Color? = null,
    contentDescription: String? = null,
) {
    val geo = MaterialTheme.geoColors
    val land = fill ?: geo.mapLand
    val border = geo.mapLandBorder
    val neighbourLand = geo.mapLandInactive
    val density = LocalDensity.current.density
    val cache = LocalCountryOutlinePathCache.current
    val built = rememberCountryOutlinePaths(outline, showNeighbours, cache)
    val description = contentDescription

    Canvas(
        modifier = modifier
            .clipToBounds()
            .semantics {
                if (description != null) this.contentDescription = description else invisibleToUser()
            }
    ) {
        if (built != null) drawCountryOutline(built, showNeighbours, density, land, border, neighbourLand)
    }
}

/**
 * The silhouette's whole frame: aspect-fit [paths] into the current [DrawScope.size] with
 * [OUTLINE_PADDING_FRACTION] padding on the shorter side, neighbours first (if [showNeighbours])
 * so the country's own outline draws over their borders. Internal so tests can render it
 * offscreen without the full Compose pipeline (see `CountryOutlineRenderTest`).
 */
internal fun DrawScope.drawCountryOutline(
    paths: CountryOutlinePaths,
    showNeighbours: Boolean,
    density: Float,
    land: Color,
    border: Color,
    neighbourLand: Color,
) {
    val shortSide = min(size.width, size.height)
    val padding = OUTLINE_PADDING_FRACTION * shortSide
    val boxPx = (shortSide - 2 * padding).coerceAtLeast(0f)
    if (boxPx <= 0f) return

    val ownStroke = Stroke(width = max(1f, OUTLINE_BORDER_DP * density) / boxPx)
    val neighbourStroke = Stroke(width = max(1f, OUTLINE_NEIGHBOUR_BORDER_DP * density) / boxPx)

    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(boxPx, boxPx, pivot = Offset.Zero)
    }) {
        if (showNeighbours) {
            for (path in paths.neighbourPaths) {
                drawPath(path, neighbourLand)
                drawPath(path, border, style = neighbourStroke)
            }
        }
        drawPath(paths.ownPath, land)
        drawPath(paths.ownPath, border, style = ownStroke)
    }
}

/** Padding round the fitted box, as a fraction of the drawing area's shorter side. */
internal const val OUTLINE_PADDING_FRACTION = 0.06f
internal const val OUTLINE_BORDER_DP = 1.5f
internal const val OUTLINE_NEIGHBOUR_BORDER_DP = 1f

/** One country's rings built into a single even-odd [Path] (outer rings and holes together). */
internal fun buildOutlinePath(rings: List<OutlineRing>): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    for (ring in rings) {
        val points = ring.points
        if (points.size < 4) continue
        moveTo(points[0], points[1])
        var p = 2
        while (p < points.size) {
            lineTo(points[p], points[p + 1])
            p += 2
        }
        close()
    }
}

/** Compose [Path]s for one [OutlineModel]: the country itself and, if asked for, its neighbours. */
@Immutable
internal class CountryOutlinePaths(
    val ownPath: Path,
    val neighbourPaths: List<Path>,
) {
    companion object {
        fun build(outline: OutlineModel, showNeighbours: Boolean): CountryOutlinePaths {
            val own = buildOutlinePath(outline.rings)
            val neighbours = if (showNeighbours) outline.neighbours.map { buildOutlinePath(it.rings) } else emptyList()
            return CountryOutlinePaths(own, neighbours)
        }
    }
}

/**
 * A small process-wide cache of [CountryOutlinePaths], keyed by (cca3, includeRemote,
 * showNeighbours), built in the background on first use. An LRU of [capacity] entries: a quiz
 * only has a handful of silhouettes on screen or just off it at once.
 */
internal class CountryOutlinePathCache(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val capacity: Int = 12,
) {
    private data class Key(val cca3: String, val includeRemote: Boolean, val showNeighbours: Boolean)

    private val entries = object : LinkedHashMap<Key, Deferred<CountryOutlinePaths>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Deferred<CountryOutlinePaths>>): Boolean =
            size > capacity
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun paths(outline: OutlineModel, showNeighbours: Boolean): Deferred<CountryOutlinePaths> {
        val key = Key(outline.cca3, outline.includeRemote, showNeighbours)
        entries[key]?.let { existing ->
            val failed = existing.isCompleted && existing.getCompletionExceptionOrNull() != null
            if (!failed) return existing
        }
        return scope.async { CountryOutlinePaths.build(outline, showNeighbours) }.also { entries[key] = it }
    }

    /** Drops every entry (memory pressure, tests). Running builds finish but aren't kept. */
    @Synchronized
    fun clear() = entries.clear()

    companion object {
        /** The app's cache; override with [LocalCountryOutlinePathCache] in tests. */
        val Shared = CountryOutlinePathCache()
    }
}

internal val LocalCountryOutlinePathCache = staticCompositionLocalOf { CountryOutlinePathCache.Shared }

/**
 * [outline]'s paths from [cache], or null while they build. Paths that are already built are
 * returned on the first composition, so a rotation or theme change never shows a blank frame.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun rememberCountryOutlinePaths(
    outline: OutlineModel,
    showNeighbours: Boolean,
    cache: CountryOutlinePathCache,
): CountryOutlinePaths? {
    val deferred = remember(cache, outline, showNeighbours) { cache.paths(outline, showNeighbours) }
    val ready = deferred.takeIf { it.isCompleted && it.getCompletionExceptionOrNull() == null }?.getCompleted()
    val awaited by produceState<Pair<Deferred<CountryOutlinePaths>, CountryOutlinePaths>?>(initialValue = null, deferred) {
        value = deferred.let { it to it.await() }
    }
    return ready ?: awaited?.takeIf { it.first === deferred }?.second
}
