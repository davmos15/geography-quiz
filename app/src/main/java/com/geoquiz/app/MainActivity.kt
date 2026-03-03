package com.geoquiz.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.geoquiz.app.data.PlayGamesLeaderboardIds
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.ChallengeRepository
import com.geoquiz.app.data.repository.QuizHistoryRepository
import com.geoquiz.app.data.service.BillingRepository
import com.geoquiz.app.data.service.PlayGamesAchievementService
import com.geoquiz.app.domain.model.ChallengeDeepLink
import com.geoquiz.app.domain.model.QuizCategory
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
    @Inject lateinit var challengeRepository: ChallengeRepository
    @Inject lateinit var quizHistoryRepository: QuizHistoryRepository
    @Inject lateinit var billingRepository: BillingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playGamesService.setActivity(this)
        billingRepository.connect()
        handleDeepLink(intent?.data)
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
        val challenge = uri?.let { ChallengeDeepLink.fromUri(it) } ?: return
        lifecycleScope.launch {
            val displayName = QuizCategory.fromRoute(
                challenge.categoryType, challenge.categoryValue
            ).displayName
            challengeRepository.createIncomingChallenge(
                id = challenge.challengeId,
                categoryType = challenge.categoryType,
                categoryValue = challenge.categoryValue,
                categoryDisplayName = displayName,
                quizMode = challenge.quizMode,
                challengerName = challenge.challengerName,
                challengerScore = challenge.challengerScore,
                challengerTotal = challenge.challengerTotal,
                challengerTime = challenge.challengerTime
            )
            deepLinkChallenge.value = challenge
        }
    }
}
