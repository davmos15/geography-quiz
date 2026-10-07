package com.geoquiz.app.domain.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Quiz stopwatch driven by a [MonotonicClock].
 *
 * Elapsed time is the accumulated time of all finished running periods plus, while running,
 * the time since the current period started. Wall-clock changes and coroutine scheduling
 * delays therefore never skew the result.
 *
 * Not thread-safe: call it from one thread (the main thread in the ViewModel).
 */
class QuizTimer(private val clock: MonotonicClock) {

    private var accumulatedMillis = 0L
    private var runningSince: Long? = null

    val isRunning: Boolean
        get() = runningSince != null

    /** Starts or resumes counting. Does nothing if already running. */
    fun start() {
        if (runningSince == null) runningSince = clock.elapsedRealtimeMillis()
    }

    /** Alias of [start], for readability at call sites that resume after a pause. */
    fun resume() = start()

    /** Stops counting and banks the current period. Does nothing if already paused. */
    fun pause() {
        val since = runningSince ?: return
        accumulatedMillis += (clock.elapsedRealtimeMillis() - since).coerceAtLeast(0L)
        runningSince = null
    }

    fun elapsedMillis(): Long {
        val since = runningSince ?: return accumulatedMillis
        return accumulatedMillis + (clock.elapsedRealtimeMillis() - since).coerceAtLeast(0L)
    }

    /** Whole seconds elapsed (rounded down), as shown to the player and stored in results. */
    fun elapsedSeconds(): Int = (elapsedMillis() / 1000L).toInt()

    /** Milliseconds until [elapsedSeconds] next increases (1..1000). */
    fun millisUntilNextSecond(): Long = 1000L - (elapsedMillis() % 1000L)

    /**
     * Elapsed time to persist. Clock readings are not meaningful across process death or
     * reboot, so only the duration is stored; whether the timer should run again is derived
     * from the quiz state on restore.
     */
    fun snapshot(): Long = elapsedMillis()

    /** Replaces the elapsed time with [accumulatedMillis] and leaves the timer paused. */
    fun restore(accumulatedMillis: Long) {
        this.accumulatedMillis = accumulatedMillis.coerceAtLeast(0L)
        runningSince = null
    }

    /**
     * Emits the whole elapsed seconds now and then each time the value changes, waking at the
     * next second boundary. Completes once the timer is paused, after emitting the final value.
     * Display only: read [elapsedSeconds] directly when saving or scoring.
     */
    fun ticks(): Flow<Int> = flow {
        emit(elapsedSeconds())
        while (isRunning) {
            delay(millisUntilNextSecond())
            emit(elapsedSeconds())
        }
    }.distinctUntilChanged()
}
