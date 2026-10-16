package com.geoquiz.app.domain.streak

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * A consecutive-day streak, as derived by [StreakCalculator]. All fields are read-only snapshots
 * for the moment [StreakCalculator.calculate] was called.
 */
data class StreakStatus(
    /** Days played in the run that is still alive right now (0 if the run just broke). */
    val current: Int,
    /** The longest [current] ever reached across the whole history, by the same rules. */
    val best: Int,
    /** Streak freezes currently held, 0..2 (task 8.2, D59: never purchasable). */
    val freezesHeld: Int,
    /** Days still to play in the current run to earn the next freeze, 1..7. */
    val daysToNextFreeze: Int,
    /** Whether the device's actual local "today" is one of the played dates. */
    val playedToday: Boolean
) {
    companion object {
        /** No history at all: what [StreakCalculator.calculate] returns for an empty set. */
        val NONE = StreakStatus(current = 0, best = 0, freezesHeld = 0, daysToNextFreeze = 7, playedToday = false)
    }
}

/**
 * Derives a consecutive-day streak purely from the dates a player has completed a daily
 * challenge round - no new storage (task 8.2, D59): the dates already live in `quiz_history`
 * ([com.geoquiz.app.data.repository.QuizHistoryRepository.distinctCategoryValues]).
 *
 * Rules:
 * - A streak is the run of consecutive local calendar dates played. Not having played *yet*
 *   today never breaks it, as long as yesterday was played (or its gap to yesterday is covered by
 *   a held freeze) - the player still has the rest of today to play.
 * - A streak freeze is earned automatically on every 7th day played within the current run (the
 *   7th, 14th, 21st, ...); never purchasable (there is no "buy a freeze" API). At most 2 are held
 *   at once - an earned freeze beyond 2 is lost.
 * - Each missed calendar day - one with no play, strictly between two played dates, or between
 *   the last played date and yesterday inclusive - auto-consumes one held freeze. A frozen day
 *   keeps the run alive (it does not break) but does not itself add to [StreakStatus.current].
 *   If a gap needs more freezes than are held, the run breaks: [StreakStatus.current] resets to 0
 *   (the gap runs up to yesterday with nothing played since) or a later played date starts a
 *   fresh run of 1; freezes held reset to 0 and the count of days played toward the next freeze
 *   starts over.
 * - Time zones and clock changes: evaluation anchors at `max(today, the latest played date)`, so
 *   a played date that is technically "in the future" (the device flew west and its clock moved
 *   back) never breaks the streak and still counts as played. Flying east and skipping a calendar
 *   date entirely is just an ordinary missed day, handled the same as any other gap.
 * - [StreakStatus.best] is the longest [StreakStatus.current] ever reached across the whole
 *   history, by the same rules, even across a later break.
 * - [StreakStatus.daysToNextFreeze] is how many more days still need to be played in the current
 *   run to earn the next freeze (1..7); while already holding the maximum of 2, this still counts
 *   down to the next 7th day (the earned freeze is simply lost there, see above).
 * - [StreakStatus.playedToday] reports the device's actual "today" argument (not the evaluation
 *   anchor above), so a device that flew west still correctly reports "haven't played today yet".
 */
object StreakCalculator {

    private const val FREEZE_INTERVAL = 7
    const val MAX_FREEZES = 2

    /**
     * @param playedDates ISO "yyyy-MM-dd" date strings (may contain duplicates or malformed
     * values; malformed ones are ignored, not treated as an error).
     * @param today the device's local "today"; defaults to the real current date.
     */
    fun calculate(playedDates: Collection<String>, today: LocalDate = LocalDate.now()): StreakStatus {
        val dates = playedDates.mapNotNull(::parseOrNull).toSortedSet().toList()
        if (dates.isEmpty()) return StreakStatus.NONE

        val playedToday = today in dates

        var run = 1
        var freezesHeld = 0
        var daysTowardFreeze = 1
        var best = 1

        fun earnFreezeIfDue() {
            if (daysTowardFreeze == FREEZE_INTERVAL) {
                freezesHeld = (freezesHeld + 1).coerceAtMost(MAX_FREEZES)
                daysTowardFreeze = 0
            }
        }

        for (i in 1 until dates.size) {
            val gap = daysBetween(dates[i - 1], dates[i]) - 1
            if (gap <= freezesHeld) {
                freezesHeld -= gap
                run += 1
                daysTowardFreeze += 1
                earnFreezeIfDue()
            } else {
                // The gap breaks the run: a later played date starts a fresh run of 1.
                run = 1
                freezesHeld = 0
                daysTowardFreeze = 1
            }
            best = maxOf(best, run)
        }

        // The stretch from the last played date to the evaluation anchor: not having played yet
        // today never breaks the run (see the class doc), so only days strictly before the anchor
        // count as "missed" here.
        val anchor = maxOf(today, dates.last())
        // Clamped at 0: the last played date can equal the anchor itself (today, or a later
        // "future" play from a clock that moved back), which must never consume a freeze.
        val gapToAnchor = (daysBetween(dates.last(), anchor) - 1).coerceAtLeast(0)
        val current: Int
        if (gapToAnchor <= freezesHeld) {
            freezesHeld -= gapToAnchor
            current = run
        } else {
            current = 0
            freezesHeld = 0
        }

        val daysToNextFreeze = if (current == 0) FREEZE_INTERVAL else FREEZE_INTERVAL - daysTowardFreeze

        return StreakStatus(
            current = current,
            best = best,
            freezesHeld = freezesHeld,
            daysToNextFreeze = daysToNextFreeze,
            playedToday = playedToday
        )
    }

    // Clamped rather than truncated: a corrupt far-future date must never wrap to a negative gap
    // (which would add freezes). Any real gap is far below Int.MAX_VALUE.
    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        ChronoUnit.DAYS.between(from, to).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun parseOrNull(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
}
