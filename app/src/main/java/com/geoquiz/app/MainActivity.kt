package com.geoquiz.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.BillingRepository
import com.geoquiz.app.data.service.ConsentManager
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.ui.challenges.IncomingChallengeHandler
import com.geoquiz.app.ui.challenges.IncomingChallengeOutcome
import com.geoquiz.app.ui.navigation.AppNavigation
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val deepLinkChallenge = mutableStateOf<ChallengeDeepLink?>(null)

    @Inject lateinit var playGamesService: PlayGamesAchievementService
    @Inject lateinit var achievementRepository: AchievementRepository
    @Inject lateinit var incomingChallengeHandler: IncomingChallengeHandler
    @Inject lateinit var quizHistoryRepository: QuizHistoryRepository
    @Inject lateinit var billingRepository: BillingRepository
    @Inject lateinit var consentManager: ConsentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playGamesService.setActivity(this)
        billingRepository.connect()
        // Ads stay off until UMP consent allows them (D8 tagging is applied at initialisation)
        consentManager.gatherConsent(this)
        // Only for a fresh launch: after rotation or process death the challenge is already saved
        // and the back stack restored, so re-handling would navigate (or warn) a second time.
        if (savedInstanceState == null) handleDeepLink(intent?.data)
        setContent {
            GeographyQuizTheme {
                AppNavigation(challengeDeepLink = deepLinkChallenge.value)
            }
        }

        // Sync locally unlocked achievements and leaderboard scores on startup
        lifecycleScope.launch {
            playGamesService.isSignedIn.filter { it }.first()
            val unlocked = achievementRepository.unlockedAchievements.first()
            if (unlocked.isNotEmpty()) {
                playGamesService.syncAllUnlocked(unlocked)
            }
            // Sync leaderboard scores
            val overall = quizHistoryRepository.getTotalCorrectAnswersSync()
            if (overall > 0) {
                playGamesService.submitScore(PlayGamesLeaderboardIds.OVERALL, overall)
                for (mode in listOf("countries", "capitals", "flags")) {
                    val modeTotal = quizHistoryRepository.getTotalCorrectAnswersForModeSync(mode)
                    if (modeTotal > 0) {
                        val id = PlayGamesLeaderboardIds.forMode(mode) ?: continue
                        playGamesService.submitScore(id, modeTotal)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playGamesService.setActivity(this)
        // Picks up pending purchases that completed while the app was in the background
        lifecycleScope.launch { billingRepository.restorePurchases() }
    }

    override fun onStop() {
        super.onStop()
        playGamesService.clearActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        if (uri == null) return
        val parsed = ChallengeDeepLink.parse(uri)
        lifecycleScope.launch {
            when (val outcome = incomingChallengeHandler.handle(parsed)) {
                is IncomingChallengeOutcome.Accepted -> deepLinkChallenge.value = outcome.link
                is IncomingChallengeOutcome.Rejected -> {
                    Log.w(TAG, "Challenge link rejected: ${outcome.reason}")
                    Toast.makeText(
                        this@MainActivity, R.string.challenge_link_invalid, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
