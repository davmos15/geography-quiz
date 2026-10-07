package com.geoquiz.app.domain.time

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeClock(var now: Long = 1_000_000L) : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = now
    fun advance(millis: Long) { now += millis }
}

@OptIn(ExperimentalCoroutinesApi::class)
class QuizTimerTest {

    private val clock = FakeClock()
    private val timer = QuizTimer(clock)

    @Test
    fun `new timer is stopped at zero and does not count`() {
        assertFalse(timer.isRunning)
        clock.advance(5_000)
        assertEquals(0L, timer.elapsedMillis())
        assertEquals(0, timer.elapsedSeconds())
    }

    @Test
    fun `running timer counts clock time`() {
        timer.start()
        assertTrue(timer.isRunning)
        clock.advance(2_500)
        assertEquals(2_500L, timer.elapsedMillis())
        assertEquals(2, timer.elapsedSeconds())
    }

    @Test
    fun `pause freezes elapsed time`() {
        timer.start()
        clock.advance(3_000)
        timer.pause()
        assertFalse(timer.isRunning)
        clock.advance(10_000)
        assertEquals(3_000L, timer.elapsedMillis())
    }

    @Test
    fun `resume continues from the paused value`() {
        timer.start()
        clock.advance(3_000)
        timer.pause()
        clock.advance(10_000)
        timer.resume()
        clock.advance(1_200)
        assertEquals(4_200L, timer.elapsedMillis())
        assertEquals(4, timer.elapsedSeconds())
    }

    @Test
    fun `multiple pauses accumulate only running periods, including partial seconds`() {
        repeat(4) {
            timer.resume()
            clock.advance(600)
            timer.pause()
            clock.advance(5_000)
        }
        // 4 x 600 ms = 2.4 s; the old delay(1000) loop would have dropped every partial second.
        assertEquals(2_400L, timer.elapsedMillis())
        assertEquals(2, timer.elapsedSeconds())
    }

    @Test
    fun `start and resume are idempotent`() {
        timer.start()
        clock.advance(1_000)
        timer.start()
        clock.advance(1_000)
        timer.resume()
        clock.advance(1_000)
        assertEquals(3_000L, timer.elapsedMillis())
    }

    @Test
    fun `pause is idempotent`() {
        timer.start()
        clock.advance(1_500)
        timer.pause()
        clock.advance(1_000)
        timer.pause()
        assertEquals(1_500L, timer.elapsedMillis())
    }

    @Test
    fun `pause before start does nothing`() {
        timer.pause()
        clock.advance(1_000)
        assertEquals(0L, timer.elapsedMillis())
        assertFalse(timer.isRunning)
    }

    @Test
    fun `long background gap while paused does not count`() {
        timer.start()
        clock.advance(42_000)
        timer.pause() // app backgrounded
        clock.advance(6L * 60 * 60 * 1000) // six hours in the background, device asleep
        timer.resume() // player resumes
        clock.advance(1_000)
        assertEquals(43, timer.elapsedSeconds())
    }

    @Test
    fun `gap while running counts even if no ticks happened`() {
        timer.start()
        // e.g. the main thread was blocked or the device slept with the quiz on screen
        clock.advance(90_000)
        assertEquals(90, timer.elapsedSeconds())
    }

    @Test
    fun `snapshot captures running time and restore brings it back paused`() {
        timer.start()
        clock.advance(12_345)
        val snapshot = timer.snapshot()
        assertEquals(12_345L, snapshot)

        // New process: the clock base is unrelated to the old one.
        val newClock = FakeClock(now = 7L)
        val restored = QuizTimer(newClock)
        restored.restore(snapshot)
        assertFalse(restored.isRunning)
        newClock.advance(60_000)
        assertEquals(12_345L, restored.elapsedMillis())

        restored.resume()
        newClock.advance(655)
        assertEquals(13_000L, restored.elapsedMillis())
        assertEquals(13, restored.elapsedSeconds())
    }

    @Test
    fun `restore from saved whole seconds`() {
        timer.restore(75 * 1000L)
        assertEquals(75, timer.elapsedSeconds())
    }

    @Test
    fun `restore on a running timer stops it and replaces the value`() {
        timer.start()
        clock.advance(5_000)
        timer.restore(1_000)
        assertFalse(timer.isRunning)
        clock.advance(5_000)
        assertEquals(1_000L, timer.elapsedMillis())
    }

    @Test
    fun `restore clamps negative values to zero`() {
        timer.restore(-500)
        assertEquals(0L, timer.elapsedMillis())
    }

    @Test
    fun `clock going backwards never reduces elapsed time below accumulated`() {
        timer.restore(2_000)
        timer.resume()
        clock.advance(-1_000)
        assertEquals(2_000L, timer.elapsedMillis())
        timer.pause()
        assertEquals(2_000L, timer.elapsedMillis())
    }

    @Test
    fun `millisUntilNextSecond points at the next whole second`() {
        timer.start()
        assertEquals(1_000L, timer.millisUntilNextSecond())
        clock.advance(250)
        assertEquals(750L, timer.millisUntilNextSecond())
        clock.advance(750)
        assertEquals(1_000L, timer.millisUntilNextSecond())
    }

    // --- ticks(): driven by virtual time so the clock and delay() agree ---

    private fun TestScope.virtualClockTimer(): QuizTimer =
        QuizTimer { testScheduler.currentTime }

    @Test
    fun `ticks emits once per whole second while running`() = runTest {
        val t = virtualClockTimer()
        t.start()
        t.ticks().test {
            assertEquals(0, awaitItem())
            advanceTimeBy(999)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, awaitItem())
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(2, awaitItem())
            t.pause()
            advanceTimeBy(1_000)
            runCurrent()
            // Paused: the final value repeats, so nothing new, and the flow completes.
            awaitComplete()
        }
    }

    @Test
    fun `ticks aligns to second boundaries after a resume mid-second`() = runTest {
        val t = virtualClockTimer()
        t.restore(1_700) // e.g. paused at 1.7 s
        t.resume()
        t.ticks().test {
            assertEquals(1, awaitItem())
            advanceTimeBy(299)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(2, awaitItem())
            t.pause()
            advanceTimeBy(1_000)
            runCurrent()
            awaitComplete()
        }
    }

    @Test
    fun `ticks on a paused timer emits the current value and completes`() = runTest {
        val t = virtualClockTimer()
        t.restore(5_000)
        t.ticks().test {
            assertEquals(5, awaitItem())
            awaitComplete()
        }
    }
}
