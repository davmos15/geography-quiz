package com.geoquiz.app.domain.time

/**
 * A clock that only ever moves forward, including while the device sleeps.
 *
 * Values are only meaningful relative to each other within one process lifetime:
 * never persist a reading, persist durations instead.
 */
fun interface MonotonicClock {
    fun elapsedRealtimeMillis(): Long
}
