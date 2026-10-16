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
         * Today's local date now, then again just after each local midnight. A changed time zone
         * is picked up at the next tick; distinct so a wake-up that lands early never re-emits.
         */
        fun localDateTicks(): Flow<LocalDate> = flow {
            while (true) {
                val now = LocalDateTime.now()
                emit(now.toLocalDate())
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_SLACK_MILLIS)
            }
        }.distinctUntilChanged()

        private const val MIDNIGHT_SLACK_MILLIS = 1_000L

        /** Same value as [com.geoquiz.app.domain.model.QuizCategory.DailyChallenge.typeKey]. */
        private const val DAILY_CATEGORY_TYPE = "daily"
    }
}
