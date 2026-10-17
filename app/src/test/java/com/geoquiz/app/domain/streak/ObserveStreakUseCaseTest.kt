package com.geoquiz.app.domain.streak

import app.cash.turbine.test
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 8.2: [ObserveStreakUseCase] against a fake repository flow, with a fixed [today]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveStreakUseCaseTest {

    private val today = LocalDate.of(2026, 10, 16)
    private val playedDates = MutableStateFlow<List<String>>(emptyList())

    private val quizHistoryRepository = mockk<QuizHistoryRepository> {
        every { distinctCategoryValues(DailyChallengeGameMode.MODE_ID, "daily") } returns playedDates
    }

    private val useCase = ObserveStreakUseCase(quizHistoryRepository)

    @Test
    fun `queries history for the daily challenge mode and category type`() = runTest {
        useCase(today).test {
            assertEquals(StreakCalculator.calculate(emptyList(), today), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits a streak matching StreakCalculator for the repository's played dates`() = runTest {
        useCase(today).test {
            assertEquals(0, awaitItem().current)

            playedDates.value = listOf(today.toString())

            val status = awaitItem()
            assertEquals(StreakCalculator.calculate(listOf(today.toString()), today), status)
            assertEquals(1, status.current)
        }
    }

    @Test
    fun `re-emits a new streak as the repository's history flow updates`() = runTest {
        useCase(today).test {
            awaitItem()

            playedDates.value = listOf(today.minusDays(1).toString())
            val afterYesterday = awaitItem()
            assertEquals(1, afterYesterday.current)
            assertFalse(afterYesterday.playedToday)

            playedDates.value = listOf(today.minusDays(1).toString(), today.toString())
            val afterToday = awaitItem()
            assertEquals(2, afterToday.current)
            assertTrue(afterToday.playedToday)
        }
    }

    @Test
    fun `rolls over to the new day without a history change`() = runTest {
        val date = MutableStateFlow(today)
        playedDates.value = listOf(today.toString())

        useCase.observe(date).test {
            assertTrue(awaitItem().playedToday)

            // Midnight passes: yesterday was played, today not yet, so the streak stays alive.
            date.value = today.plusDays(1)
            val nextDay = awaitItem()
            assertFalse(nextDay.playedToday)
            assertEquals(1, nextDay.current)

            // Another day with no play breaks it.
            date.value = today.plusDays(2)
            assertEquals(0, awaitItem().current)
        }
    }

    @Test
    fun `date ticks start with today`() = runTest {
        ObserveStreakUseCase.localDateTicks().test {
            val first = awaitItem()
            assertTrue(first == LocalDate.now() || first == LocalDate.now().minusDays(1))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Review fix: a device sleeping through midnight (or a clock/time zone change) must be caught
     * within one [ObserveStreakUseCase.POLL_INTERVAL_MILLIS], not only after the one long delay to
     * the next midnight that was originally calculated - `delay` is uptime-based and would not have
     * advanced through a real deep sleep, so that original delay is never trusted to fire on time.
     * This drives [ObserveStreakUseCase.localDateTicks]'s injectable `now` far past midnight while
     * only advancing the *virtual* test clock by one poll interval (not the ~many hours a real
     * sleep would represent), proving the new date is picked up without waiting for it.
     */
    @Test
    fun `polling re-reads the wall clock within one poll interval, catching a now that jumps past midnight`() = runTest {
        var now = LocalDateTime.of(2026, 10, 16, 12, 0, 0)
        ObserveStreakUseCase.localDateTicks(now = { now }).test {
            assertEquals(LocalDate.of(2026, 10, 16), awaitItem())

            // A 12-hour deep sleep crossing midnight: the wall clock jumps straight to the next
            // day, but only one poll interval of *virtual* time elapses.
            now = LocalDateTime.of(2026, 10, 17, 0, 0, 5)
            advanceTimeBy(ObserveStreakUseCase.POLL_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(LocalDate.of(2026, 10, 17), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
