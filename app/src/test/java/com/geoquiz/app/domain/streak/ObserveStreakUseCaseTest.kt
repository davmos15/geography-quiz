package com.geoquiz.app.domain.streak

import app.cash.turbine.test
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.domain.mode.dailychallenge.DailyChallengeGameMode
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
}
