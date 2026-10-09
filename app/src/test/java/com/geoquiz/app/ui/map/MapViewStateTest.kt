package com.geoquiz.app.ui.map

import androidx.compose.runtime.saveable.SaverScope
import com.geoquiz.app.domain.map.PlaneRect
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Camera maths: fit, clamping, focus-preserving zoom, fit-to-bounds and saving. */
class MapViewStateTest {

    /** A 4 x 2 plane-unit world in a 400 x 400 px viewport: fit scale 100, height 200 px. */
    private val world = PlaneRect(-2f, -1f, 2f, 1f)

    private fun ready(maxZoom: Float = 10f) = MapViewState(maxZoom = maxZoom).apply {
        updateViewport(400f, 400f)
        updateContentBounds(world)
    }

    @Test
    fun `not ready until viewport and content are known`() {
        val state = MapViewState()
        assertFalse(state.isReady)
        assertNull(state.visibleRect())
        state.updateViewport(400f, 400f)
        assertFalse(state.isReady)
        state.updateContentBounds(world)
        assertTrue(state.isReady)
    }

    @Test
    fun `zoom 1 fits the content and centres it`() {
        val state = ready()
        assertEquals(100f, state.fitScale, 1e-4f)
        assertEquals(1f, state.zoom)
        assertEquals(0f, state.centreX, 1e-6f)
        assertEquals(0f, state.centreY, 1e-6f)
        assertEquals(0f, state.planeToScreenX(-2f), 1e-3f)
        assertEquals(400f, state.planeToScreenX(2f), 1e-3f)
        // Content shorter than the viewport is centred vertically.
        assertEquals(100f, state.planeToScreenY(-1f), 1e-3f)
    }

    @Test
    fun `zoom is clamped between fit and max`() {
        val state = ready(maxZoom = 8f)
        state.applyGesture(200f, 200f, 0f, 0f, 0.25f)
        assertEquals(1f, state.zoom)
        state.applyGesture(200f, 200f, 0f, 0f, 100f)
        assertEquals(8f, state.zoom)
        state.snapTo(50f, 0f, 0f)
        assertEquals(8f, state.zoom)
    }

    @Test
    fun `pinch zoom keeps the plane point under the fingers fixed`() {
        val state = ready()
        val focusX = 300f
        val focusY = 220f
        val planeX = state.screenToPlaneX(focusX)
        val planeY = state.screenToPlaneY(focusY)
        state.applyGesture(focusX, focusY, 0f, 0f, 3f)
        assertEquals(3f, state.zoom, 1e-5f)
        assertEquals(focusX, state.planeToScreenX(planeX), 1e-2f)
        assertEquals(focusY, state.planeToScreenY(planeY), 1e-2f)
    }

    @Test
    fun `pan moves the map and stops at the content edge`() {
        val state = ready()
        state.applyGesture(200f, 200f, 0f, 0f, 4f) // scale 400 px/unit, view 1 x 1 units
        state.panBy(-100f, 0f) // drag left: view moves right by 0.25 units
        assertEquals(0.25f, state.centreX, 1e-4f)
        state.panBy(-10_000f, -10_000f)
        // Half the view is 0.5 units, so the centre stops 0.5 inside each edge.
        assertEquals(1.5f, state.centreX, 1e-4f)
        assertEquals(0.5f, state.centreY, 1e-4f)
        val visible = state.visibleRect()!!
        assertEquals(2f, visible.maxX, 1e-3f)
        assertEquals(1f, visible.maxY, 1e-3f)
    }

    @Test
    fun `at zoom 1 panning does nothing`() {
        val state = ready()
        state.panBy(150f, 150f)
        assertEquals(0f, state.centreX, 1e-6f)
        assertEquals(0f, state.centreY, 1e-6f)
    }

    @Test
    fun `fit to bounds frames the box with padding`() = runTest {
        val state = ready(maxZoom = 50f)
        val box = PlaneRect(1f, 0f, 1.5f, 0.25f) // 0.5 x 0.25 units
        state.fitTo(box, paddingPx = 20f, animate = false)
        // (400 - 40) / 0.5 = 720 px per unit, i.e. zoom 7.2.
        assertEquals(7.2f, state.zoom, 1e-3f)
        assertEquals(1.25f, state.centreX, 1e-4f)
        assertEquals(0.125f, state.centreY, 1e-4f)
        assertTrue(state.planeToScreenX(box.minX) >= 19.9f)
        assertTrue(state.planeToScreenX(box.maxX) <= 380.1f)
    }

    @Test
    fun `fit to a box near the edge is clamped inside the content`() = runTest {
        val state = ready()
        state.fitTo(PlaneRect(1.9f, -1f, 2f, -0.9f), animate = false)
        assertEquals(10f, state.zoom) // max zoom
        val visible = state.visibleRect()!!
        assertEquals(2f, visible.maxX, 1e-3f)
        assertEquals(-1f, visible.minY, 1e-3f)
    }

    @Test
    fun `zoom by without animation zooms about the point`() = runTest {
        val state = ready()
        val planeX = state.screenToPlaneX(100f)
        state.zoomBy(2f, 100f, 200f, animate = false)
        assertEquals(2f, state.zoom, 1e-5f)
        assertEquals(100f, state.planeToScreenX(planeX), 1e-2f)
    }

    @Test
    fun `camera survives a viewport change`() {
        val state = ready()
        state.applyGesture(300f, 200f, 0f, 0f, 4f)
        val cx = state.centreX
        state.updateViewport(800f, 400f) // rotate to landscape
        assertEquals(4f, state.zoom)
        assertEquals(cx, state.centreX, 1e-4f)
        assertEquals(200f, state.fitScale, 1e-4f)
    }

    @Test
    fun `saver round trips the camera`() {
        val state = ready()
        state.applyGesture(320f, 140f, 0f, 0f, 5f)
        val saver = MapViewState.saver()
        val saved = with(saver) { SaverScope { true }.save(state) }!!
        val restored = saver.restore(saved)!!
        restored.updateViewport(400f, 400f)
        restored.updateContentBounds(world)
        assertEquals(state.maxZoom, restored.maxZoom)
        assertEquals(state.zoom, restored.zoom)
        assertEquals(state.centreX, restored.centreX)
        assertEquals(state.centreY, restored.centreY)
    }

    @Test
    fun `reset returns to the whole content`() {
        val state = ready()
        state.applyGesture(320f, 140f, 0f, 0f, 5f)
        state.reset()
        assertEquals(1f, state.zoom)
        assertEquals(0f, state.centreX, 1e-6f)
    }

    // ---- Animations (need a frame clock, as in a composition scope) ----

    /** A 60 Hz frame clock on the test scheduler's virtual time. */
    private class TestFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private val box = PlaneRect(1f, 0f, 1.5f, 0.25f) // frames at zoom 7.2 with 20 px padding

    @Test
    fun `animated fit passes through intermediate cameras and lands on the target`() = runTest {
        val state = ready(maxZoom = 50f)
        val job = launch(TestFrameClock(testScheduler)) { state.fitTo(box, paddingPx = 20f, animate = true) }
        advanceTimeBy(MapViewState.ANIMATION_MILLIS / 2L)
        runCurrent()
        assertTrue("mid-animation zoom ${state.zoom}", state.zoom > 1.05f && state.zoom < 7.1f)
        advanceUntilIdle()
        assertEquals(7.2f, state.zoom, 1e-3f)
        assertEquals(1.25f, state.centreX, 1e-4f)
        assertTrue(job.isCompleted && !job.isCancelled)
    }

    @Test
    fun `a gesture interrupts an animation and the caller returns normally`() = runTest {
        val state = ready(maxZoom = 50f)
        var returned = false
        val job = launch(TestFrameClock(testScheduler)) {
            state.fitTo(box, paddingPx = 20f, animate = true)
            returned = true
        }
        advanceTimeBy(100)
        runCurrent()
        // What MapCanvas does when a finger lands.
        state.cancelAnimation()
        state.applyGesture(200f, 200f, 0f, 0f, 1.1f)
        val afterGesture = state.zoom to state.centreX
        advanceUntilIdle()

        assertTrue(returned)
        assertTrue(job.isCompleted && !job.isCancelled)
        assertEquals(afterGesture, state.zoom to state.centreX)
    }

    @Test
    fun `a newer animation replaces the running one`() = runTest {
        val state = ready(maxZoom = 50f)
        val clock = TestFrameClock(testScheduler)
        val first = launch(clock) { state.fitTo(box, paddingPx = 20f, animate = true) }
        advanceTimeBy(100)
        runCurrent()
        val second = launch(clock) { state.zoomBy(2f, 200f, 200f, animate = true) }
        advanceUntilIdle()
        assertTrue(first.isCompleted && !first.isCancelled)
        assertTrue(second.isCompleted && !second.isCancelled)
        assertTrue("second animation ran from where the first stopped", state.zoom < 7.2f)
    }

    @Test
    fun `cancelling the caller still cancels it`() = runTest {
        val state = ready(maxZoom = 50f)
        val job = launch(TestFrameClock(testScheduler)) { state.fitTo(box, paddingPx = 20f, animate = true) }
        advanceTimeBy(100)
        runCurrent()
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `double tap zooms in, and at max zoom goes back to fit`() = runTest {
        val state = ready(maxZoom = 8f)
        state.doubleTapZoom(300f, 200f, animate = false)
        assertEquals(2f, state.zoom, 1e-5f)
        state.snapTo(8f, 1f, 0.5f)
        state.doubleTapZoom(300f, 200f, animate = false)
        assertEquals(1f, state.zoom, 1e-5f)
        assertEquals(0f, state.centreX, 1e-6f)
        // Animated reset too.
        state.snapTo(8f, 1f, 0.5f)
        launch(TestFrameClock(testScheduler)) { state.doubleTapZoom(300f, 200f, animate = true) }
        advanceUntilIdle()
        assertEquals(1f, state.zoom, 1e-4f)
    }

    @Test
    fun `clamp axis centres narrow content`() {
        assertEquals(5f, MapViewState.clampAxis(100f, 0f, 10f, 6f), 0f)
        assertEquals(3f, MapViewState.clampAxis(0f, 0f, 10f, 3f), 0f)
        assertEquals(7f, MapViewState.clampAxis(9f, 0f, 10f, 3f), 0f)
        assertEquals(0f, MapViewState.fitScale(null, 100f, 100f), 0f)
        assertEquals(0f, MapViewState.fitScale(world, 0f, 100f), 0f)
    }
}
