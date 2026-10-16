package com.geoquiz.app.domain.streak

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8.2: table tests for every rule in [StreakCalculator]'s KDoc. [today] is fixed so every
 * test is independent of the real calendar date.
 */
class StreakCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 16)

    private fun daysBack(vararg daysAgo: Int): List<String> = daysAgo.map { today.minusDays(it.toLong()).toString() }

    /** A run of [count] consecutive days ending [endDaysAgo] days before [today]. */
    private fun consecutiveRun(count: Int, endDaysAgo: Int): List<String> =
        (0 until count).map { today.minusDays((endDaysAgo + it).toLong()).toString() }

    @Test
    fun `empty history has no streak and needs 7 days to the first freeze`() {
        val status = StreakCalculator.calculate(emptyList(), today)

        assertEquals(StreakStatus(current = 0, best = 0, freezesHeld = 0, daysToNextFreeze = 7, playedToday = false), status)
    }

    @Test
    fun `single day played today is a live one-day streak`() {
        val status = StreakCalculator.calculate(daysBack(0), today)

        assertEquals(1, status.current)
        assertEquals(1, status.best)
        assertEquals(0, status.freezesHeld)
        assertEquals(6, status.daysToNextFreeze)
        assertTrue(status.playedToday)
    }

    @Test
    fun `single day played yesterday is still alive, not yet played today`() {
        val status = StreakCalculator.calculate(daysBack(1), today)

        assertEquals(1, status.current)
        assertEquals(1, status.best)
        assertFalse(status.playedToday)
    }

    @Test
    fun `played 2 days ago with no freeze held breaks the streak to zero`() {
        val status = StreakCalculator.calculate(daysBack(2), today)

        assertEquals(0, status.current)
        assertEquals(1, status.best)
        assertEquals(0, status.freezesHeld)
        assertEquals(7, status.daysToNextFreeze)
        assertFalse(status.playedToday)
    }

    @Test
    fun `a freeze is earned on the 7th consecutive day played`() {
        val status = StreakCalculator.calculate(consecutiveRun(count = 7, endDaysAgo = 0), today)

        assertEquals(7, status.current)
        assertEquals(7, status.best)
        assertEquals(1, status.freezesHeld)
        assertEquals(7, status.daysToNextFreeze)
        assertTrue(status.playedToday)
    }

    @Test
    fun `a second freeze is earned on the 14th consecutive day played`() {
        val status = StreakCalculator.calculate(consecutiveRun(count = 14, endDaysAgo = 0), today)

        assertEquals(14, status.current)
        assertEquals(2, status.freezesHeld)
        assertEquals(7, status.daysToNextFreeze)
    }

    @Test
    fun `freezes cap at 2 - the 21st day earns nothing extra`() {
        val status = StreakCalculator.calculate(consecutiveRun(count = 21, endDaysAgo = 0), today)

        assertEquals(21, status.current)
        assertEquals(2, status.freezesHeld)
        assertEquals(7, status.daysToNextFreeze)
    }

    @Test
    fun `a single missed day covered by a held freeze stays alive with current unchanged`() {
        // 7 consecutive days (earns 1 freeze), ending 2 days ago: yesterday was missed, covered
        // by the one freeze earned.
        val status = StreakCalculator.calculate(consecutiveRun(count = 7, endDaysAgo = 2), today)

        assertEquals(7, status.current)
        assertEquals(0, status.freezesHeld, "the one freeze earned on day 7 is spent covering yesterday")
        assertFalse(status.playedToday)
    }

    @Test
    fun `a multi-day gap covered by enough held freezes stays alive`() {
        // 14 consecutive days (earns 2 freezes), then a 2-day gap, then one more day played today.
        val fourteenDayRun = consecutiveRun(count = 14, endDaysAgo = 3)
        val status = StreakCalculator.calculate(fourteenDayRun + daysBack(0), today)

        assertEquals(15, status.current)
        assertEquals(0, status.freezesHeld, "both freezes spent covering the 2-day gap")
        assertTrue(status.playedToday)
    }

    @Test
    fun `a multi-day gap longer than the held freezes breaks the run`() {
        // Same 14-day run (2 freezes), but a 3-day gap this time - one day more than held.
        val fourteenDayRun = consecutiveRun(count = 14, endDaysAgo = 4)
        val status = StreakCalculator.calculate(fourteenDayRun + daysBack(0), today)

        assertEquals(1, status.current, "the gap broke the run; today starts a fresh one")
        assertEquals(14, status.best)
        assertEquals(0, status.freezesHeld)
    }

    @Test
    fun `flying east and skipping a calendar date is an ordinary missed day with no freeze held`() {
        // Played two days ago and today, skipping yesterday entirely (the device's local date
        // jumped forward a whole day), with no freeze available to cover it.
        val status = StreakCalculator.calculate(daysBack(2, 0), today)

        assertEquals(1, status.current, "no freeze available: the run breaks and today starts a new one")
        assertEquals(1, status.best)
        assertTrue(status.playedToday)
    }

    @Test
    fun `a played date in the future from a clock that moved back never breaks the streak`() {
        // The device played "tomorrow" (clock moved back after flying west): the evaluation
        // anchor becomes that later date, so there is no gap to break on, and the actual today
        // (not itself played) correctly reports playedToday = false.
        val tomorrow = today.plusDays(1).toString()
        val status = StreakCalculator.calculate(listOf(tomorrow), today)

        assertEquals(1, status.current)
        assertEquals(1, status.best)
        assertFalse(status.playedToday)
    }

    @Test
    fun `duplicate date strings are not double counted`() {
        val status = StreakCalculator.calculate(daysBack(0, 0, 0), today)

        assertEquals(1, status.current)
    }

    @Test
    fun `malformed date strings are ignored, not treated as an error`() {
        val status = StreakCalculator.calculate(listOf("not-a-date", "", today.toString()), today)

        assertEquals(1, status.current)
        assertTrue(status.playedToday)
    }

    @Test
    fun `best streak survives a later break`() {
        // A 5-day run, then a 10-day gap (far beyond any held freeze), then a 3-day run ending
        // today - best must stay at 5, even though the current run is shorter.
        val fiveDayRun = consecutiveRun(count = 5, endDaysAgo = 15)
        val threeDayRun = consecutiveRun(count = 3, endDaysAgo = 0)
        val status = StreakCalculator.calculate(fiveDayRun + threeDayRun, today)

        assertEquals(3, status.current)
        assertEquals(5, status.best)
    }

    @Test
    fun `daysToNextFreeze counts up from 6 on day one to 7 right after a freeze is earned`() {
        assertEquals(6, StreakCalculator.calculate(consecutiveRun(count = 1, endDaysAgo = 0), today).daysToNextFreeze)
        assertEquals(1, StreakCalculator.calculate(consecutiveRun(count = 6, endDaysAgo = 0), today).daysToNextFreeze)
        assertEquals(7, StreakCalculator.calculate(consecutiveRun(count = 7, endDaysAgo = 0), today).daysToNextFreeze)
    }

    // Review round (8.2): rules not pinned by the cases above.

    @Test
    fun `trailing gap longer than the one held freeze breaks the run`() {
        val status = StreakCalculator.calculate(consecutiveRun(count = 7, endDaysAgo = 3), today)

        assertEquals(StreakStatus(current = 0, best = 7, freezesHeld = 0, daysToNextFreeze = 7, playedToday = false), status)
    }

    @Test
    fun `trailing two-day gap is covered by two held freezes`() {
        val status = StreakCalculator.calculate(consecutiveRun(count = 14, endDaysAgo = 3), today)

        assertEquals(14, status.current)
        assertEquals(0, status.freezesHeld)
        assertEquals(7, status.daysToNextFreeze)
    }

    @Test
    fun `a freeze earned on the 7th day cannot cover the gap just before it`() {
        // Six days, a miss, then today: the 7th play is today, but the gap comes first.
        val status = StreakCalculator.calculate(consecutiveRun(count = 6, endDaysAgo = 2) + daysBack(0), today)

        assertEquals(1, status.current)
        assertEquals(6, status.best)
        assertEquals(0, status.freezesHeld)
    }

    @Test
    fun `frozen days do not count toward the next freeze`() {
        // Days -10..-4 (earns one), -3 missed and frozen, then -2 and -1 played.
        val status = StreakCalculator.calculate(consecutiveRun(count = 7, endDaysAgo = 4) + daysBack(2, 1), today)

        assertEquals(9, status.current)
        assertEquals(0, status.freezesHeld)
        assertEquals(5, status.daysToNextFreeze)
    }

    @Test
    fun `days played before a break do not count toward a freeze in the new run`() {
        val status = StreakCalculator.calculate(
            consecutiveRun(count = 6, endDaysAgo = 15) + consecutiveRun(count = 6, endDaysAgo = 0),
            today
        )

        assertEquals(6, status.current)
        assertEquals(0, status.freezesHeld)
        assertEquals(1, status.daysToNextFreeze)
    }

    @Test
    fun `a future date reached across an unfrozen today starts a new run`() {
        // Yesterday, then tomorrow (clock moved forward then back): today itself was missed.
        val dates = listOf(today.minusDays(1).toString(), today.plusDays(1).toString())

        val status = StreakCalculator.calculate(dates, today)

        assertEquals(1, status.current)
        assertFalse(status.playedToday)
    }

    @Test
    fun `a freeze earned at the cap is lost, so only two missed days are covered`() {
        // 21 days (freezes earned on days 7 and 14; day 21's is lost), two missed days, then today.
        val covered = StreakCalculator.calculate(consecutiveRun(count = 21, endDaysAgo = 3) + daysBack(0), today)
        assertEquals(22, covered.current)
        assertEquals(0, covered.freezesHeld)

        // Three missed days would need a third freeze.
        val broken = StreakCalculator.calculate(consecutiveRun(count = 21, endDaysAgo = 4) + daysBack(0), today)
        assertEquals(1, broken.current)
        assertEquals(21, broken.best)
    }

    @Test
    fun `a corrupt far-future date never adds freezes`() {
        val status = StreakCalculator.calculate(daysBack(1, 0) + "+999999999-12-31", today)

        assertTrue(status.freezesHeld in 0..StreakCalculator.MAX_FREEZES)
    }
}

private fun assertEquals(expected: Int, actual: Int, message: String) {
    org.junit.Assert.assertEquals(message, expected, actual)
}
