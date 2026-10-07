package com.geoquiz.app.data.service

import android.util.Log
import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayGamesSyncManagerTest {

    private val dispatcher = StandardTestDispatcher()

    private val signedIn = MutableStateFlow(false)
    private val hasActivity = MutableStateFlow(true)
    private val online = MutableStateFlow(true)
    private val unlocked = MutableStateFlow(setOf("first_quiz"))

    /** Every set passed to syncAllUnlocked, i.e. one entry per sync. */
    private val syncedSets = mutableListOf<Set<String>>()

    private val playGames = mockk<PlayGamesAchievementService>(relaxed = true) {
        every { isSignedIn } returns signedIn
        every { hasActivity } returns this@PlayGamesSyncManagerTest.hasActivity
        every { syncAllUnlocked(any()) } answers { syncedSets += firstArg<Set<String>>() }
    }
    private val achievementRepository = mockk<AchievementRepository> {
        every { unlockedAchievements } returns unlocked
    }
    private val quizHistoryRepository = mockk<QuizHistoryRepository> {
        coEvery { getTotalCorrectAnswersSync() } returns 120L
        coEvery { getTotalCorrectAnswersForModeSync("countries") } returns 70L
        coEvery { getTotalCorrectAnswersForModeSync("capitals") } returns 50L
        coEvery { getTotalCorrectAnswersForModeSync("flags") } returns 0L
    }
    private val connectivity = object : ConnectivityObserver {
        override val isOnline = online
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Lets all work finish in virtual time. advanceUntilIdle() would skip the manager's
     * debounce and delays, because they run in backgroundScope.
     */
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    private fun TestScope.startManager(): PlayGamesSyncManager =
        newManager(backgroundScope).also {
            it.start()
            runCurrent()
        }

    private fun newManager(scope: CoroutineScope) = PlayGamesSyncManager(
        playGames, achievementRepository, quizHistoryRepository, connectivity, scope
    )

    /** Starts signed in and online, and lets the launch sync finish. */
    private fun TestScope.startSyncedAtLaunch(): PlayGamesSyncManager {
        signedIn.value = true
        val manager = startManager()
        settle()
        assertEquals(1, syncedSets.size)
        return manager
    }

    @Test
    fun `syncs unlocks and leaderboard totals on first sign-in`() = runTest(dispatcher) {
        startManager()
        settle()
        assertEquals(0, syncedSets.size)

        signedIn.value = true
        settle()

        assertEquals(listOf(setOf("first_quiz")), syncedSets)
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.OVERALL, 120L) }
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.COUNTRIES, 70L) }
        verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.CAPITALS, 50L) }
        verify(exactly = 0) { playGames.submitScore(PlayGamesLeaderboardIds.FLAGS, any()) }
    }

    @Test
    fun `syncs again on a later sign-in after signing out`() = runTest(dispatcher) {
        startSyncedAtLaunch()

        signedIn.value = false
        settle()
        assertEquals(1, syncedSets.size)

        signedIn.value = true
        settle()
        assertEquals(2, syncedSets.size)
    }

    @Test
    fun `syncs when connectivity returns while signed in`() = runTest(dispatcher) {
        startSyncedAtLaunch()

        online.value = false
        settle()
        online.value = true
        settle()

        assertEquals(2, syncedSets.size)
    }

    @Test
    fun `does not sync while signed out or offline`() = runTest(dispatcher) {
        online.value = false
        startManager()
        unlocked.value = setOf("first_quiz", "perfect_score")
        settle()
        assertEquals(0, syncedSets.size)

        // Signed in but still offline
        signedIn.value = true
        settle()
        assertEquals(0, syncedSets.size)

        // Online but signed out
        signedIn.value = false
        online.value = true
        settle()
        assertEquals(0, syncedSets.size)
        verify(exactly = 0) { playGames.submitScore(any(), any()) }
    }

    @Test
    fun `an unlock earned offline is synced when connectivity returns`() = runTest(dispatcher) {
        startSyncedAtLaunch()

        online.value = false // airplane mode
        settle()
        unlocked.value = setOf("first_quiz", "perfect_score")
        settle()
        assertEquals(1, syncedSets.size)

        online.value = true
        settle()

        assertEquals(2, syncedSets.size)
        assertEquals(setOf("first_quiz", "perfect_score"), syncedSets.last())
    }

    @Test
    fun `a new unlock while online and signed in is synced`() = runTest(dispatcher) {
        startSyncedAtLaunch()

        unlocked.value = setOf("first_quiz", "perfect_score")
        settle()

        assertEquals(setOf("first_quiz", "perfect_score"), syncedSets.last())
        assertEquals(2, syncedSets.size)
    }

    @Test
    fun `connectivity returning re-checks sign-in and syncs once signed in`() = runTest(dispatcher) {
        // Launched offline: the sign-in check failed, so the player looks signed out
        online.value = false
        every { playGames.refreshSignInState() } answers { signedIn.value = true }
        startManager()
        settle()
        assertEquals(0, syncedSets.size)

        online.value = true
        settle()

        verify(exactly = 1) { playGames.refreshSignInState() }
        assertEquals(1, syncedSets.size)
    }

    @Test
    fun `a pending sync waits for an attached activity`() = runTest(dispatcher) {
        hasActivity.value = false
        signedIn.value = true
        startManager()
        settle()
        assertEquals(0, syncedSets.size)

        hasActivity.value = true
        settle()
        assertEquals(1, syncedSets.size)

        // Returning to the app with nothing pending does not sync again
        hasActivity.value = false
        settle()
        hasActivity.value = true
        settle()
        assertEquals(1, syncedSets.size)
    }

    @Test
    fun `a burst of triggers causes one sync`() = runTest(dispatcher) {
        startSyncedAtLaunch()

        unlocked.value = setOf("a")
        advanceTimeBy(500)
        online.value = false
        advanceTimeBy(100)
        online.value = true
        advanceTimeBy(500)
        unlocked.value = setOf("a", "b")
        advanceTimeBy(500)
        signedIn.value = false
        signedIn.value = true
        settle()

        assertEquals(2, syncedSets.size)
        assertEquals(setOf("a", "b"), syncedSets.last())
    }

    @Test
    fun `syncs never overlap and a trigger during a sync runs another afterwards`() =
        runTest(dispatcher) {
            var active = 0
            var maxActive = 0
            coEvery { quizHistoryRepository.getTotalCorrectAnswersSync() } coAnswers {
                active++
                maxActive = maxOf(maxActive, active)
                delay(10_000)
                active--
                120L
            }
            signedIn.value = true
            startManager()
            advanceTimeBy(PlayGamesSyncManager.DEBOUNCE_MS + 1_000) // launch sync is running
            assertEquals(1, active)

            unlocked.value = setOf("first_quiz", "perfect_score")
            advanceTimeBy(PlayGamesSyncManager.DEBOUNCE_MS + 1_000)
            assertEquals(1, active)
            assertEquals(1, syncedSets.size)

            settle()
            assertEquals(1, maxActive)
            assertEquals(2, syncedSets.size)
            assertEquals(setOf("first_quiz", "perfect_score"), syncedSets.last())
        }

    @Test
    fun `starting twice watches once`() = runTest(dispatcher) {
        signedIn.value = true
        val manager = startManager()
        manager.start()
        settle()

        assertEquals(1, syncedSets.size)
    }

    @Test
    fun `a failed sync does not stop later syncs`() = runTest(dispatcher) {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>(), any()) } returns 0
        try {
            coEvery { quizHistoryRepository.getTotalCorrectAnswersSync() } throws
                IllegalStateException("database closed") andThen 120L
            startSyncedAtLaunch()

            online.value = false
            settle()
            online.value = true
            settle()

            assertEquals(2, syncedSets.size)
            verify(exactly = 1) { playGames.submitScore(PlayGamesLeaderboardIds.OVERALL, 120L) }
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
