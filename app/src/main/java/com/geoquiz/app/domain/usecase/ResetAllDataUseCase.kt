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
import com.geoquiz.app.domain.repository.CompletedQuizRepository
import javax.inject.Inject

/**
 * "Settings → Reset all data" (L8): deletes everything the player has created on this device.
 *
 * Cleared: quiz history, challenges and the saved quiz (Room, in one transaction), the last quiz
 * result shown on Results and Answer review, all achievement unlocks and progress counters, every
 * setting including the player name, and debug feature-flag overrides.
 *
 * Kept: the static content tables (countries, aliases, capital aliases, flag colours and flag
 * elements; this use case has no access to them), the `ads_removed` flag so a paying user doesn't
 * see ads before Google Play restores the purchase, and `whats_new_seen_version` (P10-F) for the
 * same reason: resetting a device is not a fresh install, so it must not bring the one-off "What's
 * new" screen back for a player who already saw it. Achievements already synced to Google Play
 * Games can't be revoked, so they are left alone.
 */
class ResetAllDataUseCase @Inject constructor(
    private val transaction: TransactionRunner,
    private val quizHistoryDao: QuizHistoryDao,
    private val challengeDao: ChallengeDao,
    private val savedQuizDao: SavedQuizDao,
    @SettingsStore private val settingsStore: DataStore<Preferences>,
    @AchievementStore private val achievementStore: DataStore<Preferences>,
    private val featureFlagRepository: FeatureFlagRepository,
    private val completedQuizRepository: CompletedQuizRepository
) {

    suspend operator fun invoke() {
        transaction {
            quizHistoryDao.deleteAllHistory()
            challengeDao.deleteAllChallenges()
            savedQuizDao.clearSavedQuiz()
        }

        completedQuizRepository.clear()

        achievementStore.edit { it.clear() }

        settingsStore.edit { prefs ->
            val adsRemoved = prefs[SettingsRepository.ADS_REMOVED_KEY]
            val whatsNewSeenVersion = prefs[SettingsRepository.WHATS_NEW_SEEN_VERSION_KEY]
            prefs.clear()
            if (adsRemoved != null) prefs[SettingsRepository.ADS_REMOVED_KEY] = adsRemoved
            if (whatsNewSeenVersion != null) {
                prefs[SettingsRepository.WHATS_NEW_SEEN_VERSION_KEY] = whatsNewSeenVersion
            }
        }

        featureFlagRepository.clearOverrides()
    }
}
