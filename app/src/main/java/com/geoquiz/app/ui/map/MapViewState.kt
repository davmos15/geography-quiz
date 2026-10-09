package com.geoquiz.app.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.geoquiz.app.domain.map.PlaneRect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Camera of a [MapCanvas]: which part of the plane (projected coordinates, y down) is on
 * screen. Described by [zoom] (1 = the whole content fits the viewport, up to [maxZoom]) and
 * the plane point at the viewport centre ([centreX], [centreY]), so it survives a viewport
 * size change (rotation) and saves as three floats ([Saver]).
 *
 * `screen = plane * scale + offset`, with [scale] in pixels per plane unit.
 *
 * Gestures and animations clamp the camera: zoom to `1..maxZoom`, and the centre so the view
 * never leaves the content (content smaller than the viewport is centred).
 */
@Stable
class MapViewState(
    val maxZoom: Float = DEFAULT_MAX_ZOOM,
    initialZoom: Float = 1f,
    initialCentreX: Float = Float.NaN,
    initialCentreY: Float = Float.NaN,
) {
    init {
        require(maxZoom >= 1f) { "maxZoom must be at least 1" }
    }

    var zoom by mutableFloatStateOf(initialZoom.coerceIn(1f, maxZoom))
        private set
    var centreX by mutableFloatStateOf(initialCentreX)
        private set
    var centreY by mutableFloatStateOf(initialCentreY)
        private set
    var viewportWidth by mutableFloatStateOf(0f)
        private set
    var viewportHeight by mutableFloatStateOf(0f)
        private set
    var contentBounds by mutableStateOf<PlaneRect?>(null)
        private set

    /** Pixels per plane unit at zoom 1; 0 until the viewport and content are known. */
    val fitScale: Float
        get() = fitScale(contentBounds, viewportWidth, viewportHeight)

    /** Pixels per plane unit. */
    val scale: Float get() = fitScale * zoom

    val offsetX: Float get() = viewportWidth / 2 - centreX * scale
    val offsetY: Float get() = viewportHeight / 2 - centreY * scale

    /** True once there is a viewport, content and a camera, so the map can be drawn. */
    val isReady: Boolean get() = fitScale > 0f && centreX.isFinite() && centreY.isFinite()

    fun screenToPlaneX(screenX: Float): Float = (screenX - offsetX) / scale
    fun screenToPlaneY(screenY: Float): Float = (screenY - offsetY) / scale
    fun planeToScreenX(planeX: Float): Float = planeX * scale + offsetX
    fun planeToScreenY(planeY: Float): Float = planeY * scale + offsetY

    /** The visible part of the plane, or null if not [isReady]. */
    fun visibleRect(): PlaneRect? = if (!isReady) null else PlaneRect(
        screenToPlaneX(0f), screenToPlaneY(0f), screenToPlaneX(viewportWidth), screenToPlaneY(viewportHeight)
    )

    fun updateViewport(width: Float, height: Float) {
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        clampCamera()
    }

    fun updateContentBounds(bounds: PlaneRect) {
        if (bounds == contentBounds) return
        contentBounds = bounds
        if (!centreX.isFinite() || !centreY.isFinite()) {
            centreX = bounds.centreX
            centreY = bounds.centreY
        }
        clampCamera()
    }

    /**
     * One step of a pan/pinch gesture: zoom by [zoomFactor] keeping the plane point under
     * ([focusX], [focusY]) (screen pixels) fixed, then pan by ([panX], [panY]) pixels.
     */
    fun applyGesture(focusX: Float, focusY: Float, panX: Float, panY: Float, zoomFactor: Float) {
        if (!isReady) return
        val oldScale = scale
        val focusPlaneX = centreX + (focusX - viewportWidth / 2) / oldScale
        val focusPlaneY = centreY + (focusY - viewportHeight / 2) / oldScale
        zoom = (zoom * zoomFactor).coerceIn(1f, maxZoom)
        val newScale = scale
        centreX = focusPlaneX - (focusX - viewportWidth / 2) / newScale - panX / newScale
        centreY = focusPlaneY - (focusY - viewportHeight / 2) / newScale - panY / newScale
        clampCamera()
    }

    fun panBy(dx: Float, dy: Float) = applyGesture(viewportWidth / 2, viewportHeight / 2, dx, dy, 1f)

    /** Jumps to a camera (clamped). */
    fun snapTo(zoom: Float, centreX: Float, centreY: Float) {
        this.zoom = zoom.coerceIn(1f, maxZoom)
        this.centreX = centreX
        this.centreY = centreY
        clampCamera()
    }

    /** Back to zoom 1 on the content centre. */
    fun reset() {
        val content = contentBounds ?: return
        snapTo(1f, content.centreX, content.centreY)
    }

    /**
     * The camera that frames [bounds] (plane coordinates) with [paddingPx] on each side,
     * clamped; null if not [isReady].
     */
    fun cameraFor(bounds: PlaneRect, paddingPx: Float): Camera? {
        if (!isReady) return null
        val availW = max(1f, viewportWidth - 2 * paddingPx)
        val availH = max(1f, viewportHeight - 2 * paddingPx)
        val w = max(bounds.width, 1e-6f)
        val h = max(bounds.height, 1e-6f)
        val targetScale = min(availW / w, availH / h)
        val z = (targetScale / fitScale).coerceIn(1f, maxZoom)
        val (cx, cy) = clampedCentre(bounds.centreX, bounds.centreY, fitScale * z)
        return Camera(z, cx, cy)
    }

    /**
     * Frames [bounds]; animated unless [animate] is false (reduced motion).
     *
     * Animated calls need a [androidx.compose.runtime.MonotonicFrameClock], so call them from
     * a composition scope (`rememberCoroutineScope()`). If a gesture, [cancelAnimation] or
     * another animation interrupts this one, it returns normally where the camera is; it only
     * throws if the caller's own coroutine is cancelled.
     */
    suspend fun fitTo(bounds: PlaneRect, paddingPx: Float = 0f, animate: Boolean = true) {
        val target = cameraFor(bounds, paddingPx) ?: return
        if (animate) animateTo(target) else runInterruptible { snapTo(target.zoom, target.centreX, target.centreY) }
    }

    /**
     * Zooms by [factor] about the screen point ([focusX], [focusY]), animated unless [animate]
     * is false. Same calling rules as [fitTo].
     */
    suspend fun zoomBy(factor: Float, focusX: Float, focusY: Float, animate: Boolean = true) {
        if (!isReady) return
        if (!animate) {
            runInterruptible { applyGesture(focusX, focusY, 0f, 0f, factor) }
            return
        }
        runInterruptible {
            val startZoom = zoom
            val endZoom = (zoom * factor).coerceIn(1f, maxZoom)
            if (endZoom == startZoom) return@runInterruptible
            val fit = fitScale
            val focusPlaneX = centreX + (focusX - viewportWidth / 2) / scale
            val focusPlaneY = centreY + (focusY - viewportHeight / 2) / scale
            animate(0f, 1f, animationSpec = tween(ANIMATION_MILLIS, easing = FastOutSlowInEasing)) { t, _ ->
                val z = startZoom * (endZoom / startZoom).pow(t)
                val s = fit * z
                snapTo(z, focusPlaneX - (focusX - viewportWidth / 2) / s, focusPlaneY - (focusY - viewportHeight / 2) / s)
            }
        }
    }

    /**
     * A double tap at ([focusX], [focusY]): zoom in [DOUBLE_TAP_ZOOM] times about that point,
     * or, when already at [maxZoom], back out to the whole content. Same calling rules as [fitTo].
     */
    suspend fun doubleTapZoom(focusX: Float, focusY: Float, animate: Boolean = true) {
        if (!isReady) return
        if (zoom >= maxZoom * (1f - 1e-4f)) {
            val content = contentBounds ?: return
            val target = Camera(1f, content.centreX, content.centreY)
            if (animate) animateTo(target) else runInterruptible { snapTo(1f, target.centreX, target.centreY) }
        } else {
            zoomBy(DOUBLE_TAP_ZOOM, focusX, focusY, animate)
        }
    }

    /** Animates to [target]: zoom interpolates geometrically, the centre linearly. Same calling rules as [fitTo]. */
    suspend fun animateTo(target: Camera) {
        runInterruptible {
            val startZoom = zoom
            val startX = centreX
            val startY = centreY
            animate(0f, 1f, animationSpec = tween(ANIMATION_MILLIS, easing = FastOutSlowInEasing)) { t, _ ->
                snapTo(
                    startZoom * (target.zoom / startZoom).pow(t),
                    startX + (target.centreX - startX) * t,
                    startY + (target.centreY - startY) * t,
                )
            }
        }
    }

    /** Stops a running [fitTo]/[zoomBy] animation (the player touched the map). */
    fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    /**
     * Runs [block] as the one camera animation: a newer one, or [cancelAnimation], cancels it.
     * That cancellation is swallowed while the caller is still active, so interrupted callers
     * return normally.
     */
    private suspend fun runInterruptible(block: suspend () -> Unit) {
        try {
            mutex.mutate {
                val job = currentCoroutineContext()[Job]
                animationJob = job
                try {
                    block()
                } finally {
                    if (animationJob === job) animationJob = null
                }
            }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }

    private val mutex = MutatorMutex()
    private var animationJob: Job? = null

    private fun clampCamera() {
        val fit = fitScale
        if (fit <= 0f || !centreX.isFinite() || !centreY.isFinite()) return
        zoom = zoom.coerceIn(1f, maxZoom)
        val (cx, cy) = clampedCentre(centreX, centreY, fit * zoom)
        centreX = cx
        centreY = cy
    }

    private fun clampedCentre(cx: Float, cy: Float, scale: Float): Pair<Float, Float> {
        val content = contentBounds ?: return cx to cy
        return clampAxis(cx, content.minX, content.maxX, viewportWidth / (2 * scale)) to
            clampAxis(cy, content.minY, content.maxY, viewportHeight / (2 * scale))
    }

    /** A camera position: zoom and the plane point at the viewport centre. */
    data class Camera(val zoom: Float, val centreX: Float, val centreY: Float)

    companion object {
        const val DEFAULT_MAX_ZOOM = 50f
        const val ANIMATION_MILLIS = 300
        const val DOUBLE_TAP_ZOOM = 2f

        /** Pixels per plane unit that fit [content] in the viewport (0 if unknown). */
        fun fitScale(content: PlaneRect?, viewportWidth: Float, viewportHeight: Float): Float {
            if (content == null || viewportWidth <= 0f || viewportHeight <= 0f) return 0f
            if (content.width <= 0f && content.height <= 0f) return 0f
            val sx = if (content.width > 0f) viewportWidth / content.width else Float.POSITIVE_INFINITY
            val sy = if (content.height > 0f) viewportHeight / content.height else Float.POSITIVE_INFINITY
            return min(sx, sy)
        }

        /**
         * Clamps a centre coordinate so a view [halfView] plane units either side stays inside
         * `[min, max]`; content narrower than the view is centred.
         */
        fun clampAxis(centre: Float, min: Float, max: Float, halfView: Float): Float =
            if (max - min <= 2 * halfView) (min + max) / 2 else centre.coerceIn(min + halfView, max - halfView)

        fun saver(): Saver<MapViewState, Any> = Saver(
            save = { floatArrayOf(it.maxZoom, it.zoom, it.centreX, it.centreY) },
            restore = {
                val a = it as FloatArray
                MapViewState(maxZoom = a[0], initialZoom = a[1], initialCentreX = a[2], initialCentreY = a[3])
            }
        )
    }
}

/** A [MapViewState] that survives configuration changes and process death. */
@Composable
fun rememberMapViewState(maxZoom: Float = MapViewState.DEFAULT_MAX_ZOOM): MapViewState =
    rememberSaveable(saver = MapViewState.saver()) { MapViewState(maxZoom = maxZoom) }
