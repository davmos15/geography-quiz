package com.geoquiz.app.domain.mode.dailychallenge

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The single source of "today" for the daily challenge (task 8.1, D4): the device's local
 * calendar date as an ISO "yyyy-MM-dd" string. [DailyChallengeGenerator] seeds a day's 10
 * questions from exactly this string, so two devices on the same local date always see the same
 * round; a device in a different time zone (or with the clock changed) sees whatever its own
 * [LocalDate.now] says "today" is, by design (D4: "device local date").
 */
object DailyChallengeDate {
    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** Today, in the device's default time zone. */
    fun today(): String = LocalDate.now().format(FORMAT)
}
