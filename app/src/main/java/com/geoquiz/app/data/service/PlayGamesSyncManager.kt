package com.geoquiz.app.data.service

import android.util.Log
import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.di.ApplicationScope
import com.geoquiz.app.domain.model.QuizMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes locally stored progress (DataStore achievements, the source of truth, and the Room
 * leaderboard totals) to Play Games.
 *
 * A full sync is requested at start, on every sign-in (false to true), whenever connectivity
 * returns and whenever the set of local unlocks changes. A request waits until the player is
 * signed in, online and an Activity is attached (the Play Games client needs one), so an unlock
 * earned offline or signed out is pushed later without restarting the app. Requests are
 * debounced so a burst causes one sync, and syncs never overlap. Play Games unlocks and score
 * submits are idempotent, so re-sending everything is safe.
 */
@OptIn(FlowPreview::class)
@Singleton
class PlayGamesSyncManager @Inject constructor(
    private val playGames: PlayGamesAchievementService,
    private val achievementRepository: AchievementRepository,
    private val quizHistoryRepository: QuizHistoryRepository,
    private val connectivity: ConnectivityObserver,
    @ApplicationScope private val scope: CoroutineScope
) {
    /** True while a sync is requested but has not run. Starts true: one sync per launch. */
    private val pending = MutableStateFlow(true)
    private val syncMutex = Mutex()
    private var job: Job? = null

    /** Starts watching. Safe to call more than once (e.g. on every Activity creation). */
    @Synchronized
    fun start() {
        if (job != null) return
        // Main: the Play Games service keeps its Activity reference on the main thread.
        job = scope.launch(Dispatchers.Main) { watch() }
    }

    private suspend fun watch(): Unit = coroutineScope {
        val online = connectivity.isOnline
            .distinctUntilChanged()
            .stateIn(this, SharingStarted.Eagerly, false)

        // StateFlows emit only changes, so each `true` is a false-to-true transition
        // (or the initial value).
        launch { playGames.isSignedIn.filter { it }.collect { pending.value = true } }
        launch {
            online.filter { it }.collect {
                pending.value = true
                // The sign-in check at launch may have failed while offline: ask again.
                if (!playGames.isSignedIn.value) playGames.refreshSignInState()
            }
        }
        launch {
            achievementRepository.unlockedAchievements
                .distinctUntilChanged()
                .collect { pending.value = true }
        }

        combine(pending, playGames.isSignedIn, online, playGames.hasActivity) { p, s, o, a ->
            p && s && o && a
        }
            // Debounce before filtering, so the state after a quiet period decides.
            .debounce(DEBOUNCE_MS)
            .filter { it }
            .collect { sync() }
    }

    private suspend fun sync() = syncMutex.withLock {
        // Cleared before reading, so a request made during this sync causes another one.
        pending.value = false
        try {
            val unlocked = achievementRepository.unlockedAchievements.first()
            if (unlocked.isNotEmpty()) playGames.syncAllUnlocked(unlocked)

            val overall = quizHistoryRepository.getTotalCorrectAnswersSync()
            if (overall > 0) {
                playGames.submitScore(PlayGamesLeaderboardIds.OVERALL, overall)
                for (mode in QuizMode.entries) {
                    val id = PlayGamesLeaderboardIds.forMode(mode.id) ?: continue
                    val modeTotal = quizHistoryRepository.getTotalCorrectAnswersForModeSync(mode.id)
                    if (modeTotal > 0) playGames.submitScore(id, modeTotal)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Play Games sync failed; retried on the next trigger", e)
        }
    }

    internal companion object {
        private const val TAG = "PlayGamesSync"
        const val DEBOUNCE_MS = 2_000L
    }
}
