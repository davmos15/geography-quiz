package com.geoquiz.app.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.data.local.db.SavedQuizDao
import com.geoquiz.app.data.local.db.TransactionRunner
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.di.AchievementStore
import com.geoquiz.app.di.SettingsStore
import javax.inject.Inject

/**
 * "Settings → Reset all data" (L8): deletes everything the player has created on this device.
 *
 * Cleared: quiz history, challenges and the saved quiz (Room, in one transaction), all achievement
 * unlocks and progress counters, every setting including the player name, and debug feature-flag
 * overrides.
 *
 * Kept: the static content tables (countries, aliases, capital aliases, flag colours and flag
 * elements; this use case has no access to them), and the `ads_removed` flag so a paying user
 * doesn't see ads before Google Play restores the purchase. Achievements already synced to Google
 * Play Games can't be revoked, so they are left alone.
 */
class ResetAllDataUseCase @Inject constructor(
    private val transaction: TransactionRunner,
    private val quizHistoryDao: QuizHistoryDao,
    private val challengeDao: ChallengeDao,
    private val savedQuizDao: SavedQuizDao,
    @SettingsStore private val settingsStore: DataStore<Preferences>,
    @AchievementStore private val achievementStore: DataStore<Preferences>,
    private val featureFlagRepository: FeatureFlagRepository
) {

    suspend operator fun invoke() {
        transaction {
            quizHistoryDao.deleteAllHistory()
            challengeDao.deleteAllChallenges()
            savedQuizDao.clearSavedQuiz()
        }

        achievementStore.edit { it.clear() }

        settingsStore.edit { prefs ->
            val adsRemoved = prefs[SettingsRepository.ADS_REMOVED_KEY]
            prefs.clear()
            if (adsRemoved != null) prefs[SettingsRepository.ADS_REMOVED_KEY] = adsRemoved
        }

        featureFlagRepository.clearOverrides()
    }
}
