package com.geoquiz.app.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.QuizHistoryDao
import com.geoquiz.app.data.local.db.SavedQuizDao
import com.geoquiz.app.data.local.db.TransactionRunner
import com.geoquiz.app.data.local.preferences.FeatureFlagRepository
import com.geoquiz.app.data.local.preferences.SettingsRepository
import com.geoquiz.app.domain.model.FeatureFlag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ResetAllDataUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var settingsStore: DataStore<Preferences>
    private lateinit var achievementStore: DataStore<Preferences>
    private lateinit var featureFlagStore: DataStore<Preferences>
    private lateinit var featureFlags: FeatureFlagRepository

    private val quizHistoryDao = mockk<QuizHistoryDao>(relaxUnitFun = true)
    private val challengeDao = mockk<ChallengeDao>(relaxUnitFun = true)
    private val savedQuizDao = mockk<SavedQuizDao>(relaxUnitFun = true)

    /** Records whether DAO work happened inside a transaction. */
    private class FakeTransactionRunner : TransactionRunner {
        var transactions = 0
        var inTransaction = false
        override suspend fun <R> invoke(block: suspend () -> R): R {
            transactions++
            inTransaction = true
            try {
                return block()
            } finally {
                inTransaction = false
            }
        }
    }

    private val transaction = FakeTransactionRunner()

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settingsStore = store("settings")
        achievementStore = store("achievements")
        featureFlagStore = store("feature_flags")
        featureFlags = FeatureFlagRepository(featureFlagStore, overridesAllowed = true)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "$name.preferences_pb")
        }

    private fun useCase() = ResetAllDataUseCase(
        transaction = transaction,
        quizHistoryDao = quizHistoryDao,
        challengeDao = challengeDao,
        savedQuizDao = savedQuizDao,
        settingsStore = settingsStore,
        achievementStore = achievementStore,
        featureFlagRepository = featureFlags
    )

    @Test
    fun `clears the three user tables in one transaction and nothing else`() = runTest {
        coEvery { quizHistoryDao.deleteAllHistory() } answers { assertTrue(transaction.inTransaction) }
        coEvery { challengeDao.deleteAllChallenges() } answers { assertTrue(transaction.inTransaction) }
        coEvery { savedQuizDao.clearSavedQuiz() } answers { assertTrue(transaction.inTransaction) }

        useCase()()

        assertEquals(1, transaction.transactions)
        coVerify(exactly = 1) { quizHistoryDao.deleteAllHistory() }
        coVerify(exactly = 1) { challengeDao.deleteAllChallenges() }
        coVerify(exactly = 1) { savedQuizDao.clearSavedQuiz() }
        // No other DAO calls (no inserts, no other deletes). The use case has no access to the
        // static content DAOs (countries, aliases, capital aliases, flag colours, flag elements).
        confirmVerified(quizHistoryDao, challengeDao, savedQuizDao)
    }

    @Test
    fun `clears settings and player name but keeps ads_removed`() = runTest {
        settingsStore.edit {
            it[booleanPreferencesKey("show_timer")] = false
            it[booleanPreferencesKey("show_flags")] = true
            it[booleanPreferencesKey("show_country_hint")] = true
            it[booleanPreferencesKey("hard_mode")] = true
            it[stringPreferencesKey("player_name")] = "Sam"
            it[SettingsRepository.ADS_REMOVED_KEY] = true
        }

        useCase()()

        val prefs = settingsStore.data.first()
        assertEquals(true, prefs[booleanPreferencesKey("ads_removed")])
        assertEquals(setOf("ads_removed"), prefs.asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun `does not create ads_removed when it was never set`() = runTest {
        settingsStore.edit { it[stringPreferencesKey("player_name")] = "Sam" }

        useCase()()

        assertTrue(settingsStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `keeps ads_removed false when stored as false`() = runTest {
        settingsStore.edit { it[SettingsRepository.ADS_REMOVED_KEY] = false }

        useCase()()

        assertEquals(false, settingsStore.data.first()[SettingsRepository.ADS_REMOVED_KEY])
    }

    @Test
    fun `clears all achievement unlocks and progress`() = runTest {
        achievementStore.edit {
            it[stringPreferencesKey("unlocked_achievements")] = "first_steps,world_traveler"
            it[intPreferencesKey("quizzes_completed")] = 12
            it[stringPreferencesKey("region_scores")] = "Europe:0.9"
        }

        useCase()()

        assertTrue(achievementStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `clears feature flag overrides`() = runTest {
        FeatureFlag.entries.forEach { featureFlags.setOverride(it, !it.defaultEnabled) }

        useCase()()

        featureFlags.states.first().forEach { state ->
            assertFalse(state.overridden)
            assertEquals(state.flag.defaultEnabled, state.enabled)
        }
    }

    @Test
    fun `preferences are left untouched when clearing the tables fails`() = runTest {
        coEvery { challengeDao.deleteAllChallenges() } throws IllegalStateException("disk full")
        settingsStore.edit { it[stringPreferencesKey("player_name")] = "Sam" }

        try {
            useCase()()
            fail("Expected the failure to propagate")
        } catch (expected: IllegalStateException) {
            // The caller reports the failure to the user.
        }

        assertEquals("Sam", settingsStore.data.first()[stringPreferencesKey("player_name")])
        assertNull(settingsStore.data.first()[SettingsRepository.ADS_REMOVED_KEY])
    }
}
