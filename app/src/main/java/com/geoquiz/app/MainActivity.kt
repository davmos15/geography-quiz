package com.geoquiz.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.geoquiz.app.data.local.preferences.AchievementRepository
import com.geoquiz.app.data.repository.ChallengeRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playGamesService.setActivity(this)
        handleDeepLink(intent?.data)
        setContent {
            GeographyQuizTheme {
                AppNavigation(challengeDeepLink = deepLinkChallenge.value)
            }
        }

        // Sync locally unlocked achievements to Play Games on startup
        lifecycleScope.launch {
            playGamesService.isSignedIn.filter { it }.first()
            val unlocked = achievementRepository.unlockedAchievements.first()
            if (unlocked.isNotEmpty()) {
                playGamesService.syncAllUnlocked(unlocked)
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
        val challenge = uri?.let { ChallengeDeepLink.fromUri(it) }
        deepLinkChallenge.value = challenge
        if (challenge != null) {
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
            }
        }
    }
}
