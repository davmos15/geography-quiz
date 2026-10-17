package com.geoquiz.app.domain.streak

import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Derives the daily challenge's streak (task 8.2) from the player's recorded completion dates -
 * no new storage (D59): every distinct `categoryValue` ever recorded for
 * [DailyChallengeGameMode.MODE_ID]/`"daily"` is exactly the set of ISO dates played
 * ([QuizHistoryRepository.distinctCategoryValues]), which [StreakCalculator] turns into a
 * [StreakStatus]. Kept general (no daily-challenge-specific UI state) so the Play screen can
 * reuse it once task 8.6 wires a streak chip there too.
 *
 * [today] is a plain parameter on an overload, not a new Hilt-bound clock: there is no existing
 * `java.time.LocalDate` clock abstraction in `di/ClockModule.kt` (it only provides
 * [com.geoquiz.app.domain.time.MonotonicClock], an elapsed-time clock unrelated to calendar
 * dates), and adding one is outside this task's file list. A `() -> LocalDate` or default-valued
 * constructor parameter was tried first, but Dagger/Hilt rejects it (`[Dagger/MissingBinding]
 * kotlin.jvm.functions.Function0<java.time.LocalDate> cannot be provided`): a Kotlin default
 * value does not exempt an `@Inject constructor` parameter from needing a binding. The no-arg
 * [invoke] (used everywhere in production) always passes the real [LocalDate.now]; tests call
 * the single-argument overload with a fixed date instead.
 *
 * The no-arg [invoke] re-reads the date at every local midnight ([localDateTicks]), so a
 * long-lived screen (Setup, and the Play header from 8.6) rolls over to the new day on its own
 * instead of describing yesterday until history next changes.
 *
 * Review fix: [localDateTicks] used to compute the exact delay to the next midnight and sleep for
 * it in one [delay] call. [delay] is uptime-based (`SystemClock.uptimeMillis`, not wall-clock
 * time): it stops advancing while the device is in deep sleep (Doze), so a single long delay
 * calculated before sleeping could wake up well after the date had already changed, or - if the
 * device's clock or time zone changed while asleep or backgrounded - at a time that no longer
 * matches the delay's assumptions at all. [localDateTicks] now polls at a short, bounded interval
 * ([POLL_INTERVAL]) instead, re-reading the wall clock each time and only emitting when the date
 * actually changes ([kotlinx.coroutines.flow.distinctUntilChanged]): a sleep that crosses
 * midnight, or a time zone change, is caught within [POLL_INTERVAL] of the device waking (any
 * `delay` that elapsed only partially before sleep still "catches up" the next time the flow's
 * collector resumes, since the loop immediately re-reads `LocalDateTime.now()` rather than trusting
 * elapsed time).
 */
class ObserveStreakUseCase @Inject constructor(
    private val quizHistoryRepository: QuizHistoryRepository
) {
    operator fun invoke(): Flow<StreakStatus> = observe(localDateTicks())

    operator fun invoke(today: LocalDate): Flow<StreakStatus> = observe(flowOf(today))

    /** [today] emits the current local date whenever it changes; internal for tests. */
    internal fun observe(today: Flow<LocalDate>): Flow<StreakStatus> =
        combine(
            quizHistoryRepository.distinctCategoryValues(DailyChallengeGameMode.MODE_ID, DAILY_CATEGORY_TYPE),
            today
        ) { playedDates, date -> StreakCalculator.calculate(playedDates, date) }

    companion object {
        /**
         * Today's local date now, then re-checked at least every [POLL_INTERVAL] (sooner, right up
         * to local midnight, once less than that remains) - never one long [delay] slept all the
         * way to the next midnight, since `delay` is uptime-based and would not advance through
         * deep sleep (see the class doc's review-fix note). [kotlinx.coroutines.flow.distinctUntilChanged]
         * means only an actual date change (crossing midnight, or a device time zone/clock change)
         * is emitted; polling itself is invisible to collectors.
         *
         * [now] defaults to the real wall clock everywhere in production (the no-arg call site in
         * [invoke] never passes it); it exists only so a test can supply a `now` that jumps forward
         * (simulating a device clock change or a sleep crossing midnight) independently of how much
         * virtual test time is advanced - the same spirit as [invoke]'s own `today` test overload,
         * since Hilt cannot bind a `() -> LocalDateTime` for an `@Inject constructor` parameter
         * either (see the class doc).
         */
        fun localDateTicks(now: () -> LocalDateTime = LocalDateTime::now): Flow<LocalDate> = flow {
            while (true) {
                val current = now()
                emit(current.toLocalDate())
                val nextMidnight = current.toLocalDate().plusDays(1).atStartOfDay()
                val untilMidnight = Duration.between(current, nextMidnight).toMillis() + MIDNIGHT_SLACK_MILLIS
                delay(untilMidnight.coerceAtMost(POLL_INTERVAL_MILLIS))
            }
        }.distinctUntilChanged()

        private const val MIDNIGHT_SLACK_MILLIS = 1_000L

        /** The longest [localDateTicks] ever waits before re-reading the wall clock; internal so tests can drive it exactly. */
        internal const val POLL_INTERVAL_MILLIS = 60_000L

        /** Same value as [com.geoquiz.app.domain.model.QuizCategory.DailyChallenge.typeKey]. */
        private const val DAILY_CATEGORY_TYPE = "daily"
    }
}
