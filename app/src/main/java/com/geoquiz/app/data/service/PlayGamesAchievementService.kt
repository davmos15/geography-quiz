package com.geoquiz.app.data.service

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.geoquiz.app.data.PlayGamesAchievementIds
import com.geoquiz.app.domain.model.Achievement
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayGamesAchievementService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PlayGamesAchievements"
    }

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private var currentActivity: Activity? = null

    fun setActivity(activity: Activity) {
        currentActivity = activity
        checkAuthentication(activity)
    }

    fun clearActivity() {
        currentActivity = null
    }

    private fun checkAuthentication(activity: Activity) {
        try {
            val gamesSignInClient = PlayGames.getGamesSignInClient(activity)
            gamesSignInClient.isAuthenticated.addOnCompleteListener { task ->
                val isAuthenticated = task.isSuccessful &&
                    (task.result?.isAuthenticated == true)
                _isSignedIn.value = isAuthenticated
                Log.d(TAG, "Play Games authenticated: $isAuthenticated")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Play Games not available", e)
            _isSignedIn.value = false
        }
    }

    fun unlockAchievement(achievement: Achievement) {
        val activity = currentActivity ?: return
        val playGamesId = PlayGamesAchievementIds.getPlayGamesId(achievement) ?: return
        if (playGamesId.startsWith("PLACEHOLDER")) return

        if (!_isSignedIn.value) return

        try {
            PlayGames.getAchievementsClient(activity).unlock(playGamesId)
            Log.d(TAG, "Unlocked: ${achievement.id}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unlock: ${achievement.id}", e)
        }
    }

    fun syncAllUnlocked(unlockedAchievements: Set<String>) {
        val activity = currentActivity ?: return
        if (!_isSignedIn.value) return

        try {
            val client = PlayGames.getAchievementsClient(activity)
            Achievement.entries.forEach { achievement ->
                if (achievement.id in unlockedAchievements) {
                    val playGamesId = PlayGamesAchievementIds.getPlayGamesId(achievement)
                    if (playGamesId != null && !playGamesId.startsWith("PLACEHOLDER")) {
                        client.unlock(playGamesId)
                    }
                }
            }
            Log.d(TAG, "Synced ${unlockedAchievements.size} achievements")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync achievements", e)
        }
    }

    fun showAchievementsUI() {
        val activity = currentActivity ?: return
        if (!_isSignedIn.value) return

        try {
            PlayGames.getAchievementsClient(activity)
                .achievementsIntent
                .addOnSuccessListener { intent ->
                    activity.startActivityForResult(intent, 9003)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show achievements UI", e)
        }
    }
}
