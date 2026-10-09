package com.geoquiz.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.geoquiz.app.domain.model.Difficulty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The default difficulty setting (3.2) and its migration from the old `hard_mode` switch. */
class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    private val hardModeKey = booleanPreferencesKey("hard_mode")
    private val difficultyKey = stringPreferencesKey("difficulty")

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "settings.preferences_pb")
        }
        repository = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a fresh install defaults to normal`() = runTest {
        assertEquals(Difficulty.NORMAL, repository.difficulty.first())
    }

    @Test
    fun `the old hard mode switch on becomes hard`() = runTest {
        dataStore.edit { it[hardModeKey] = true }

        assertEquals(Difficulty.HARD, repository.difficulty.first())
    }

    @Test
    fun `the old hard mode switch off becomes normal`() = runTest {
        dataStore.edit { it[hardModeKey] = false }

        assertEquals(Difficulty.NORMAL, repository.difficulty.first())
    }

    @Test
    fun `a stored difficulty wins over the old switch`() = runTest {
        dataStore.edit {
            it[hardModeKey] = true
            it[difficultyKey] = "easy"
        }

        assertEquals(Difficulty.EASY, repository.difficulty.first())
    }

    @Test
    fun `an unknown stored id falls back to the old switch, then normal`() = runTest {
        dataStore.edit { it[difficultyKey] = "impossible" }
        assertEquals(Difficulty.NORMAL, repository.difficulty.first())

        dataStore.edit { it[hardModeKey] = true }
        assertEquals(Difficulty.HARD, repository.difficulty.first())
    }

    @Test
    fun `setDifficulty round-trips every tier and drops the old switch`() = runTest {
        dataStore.edit { it[hardModeKey] = true }

        for (difficulty in Difficulty.entries) {
            repository.setDifficulty(difficulty)
            assertEquals(difficulty, repository.difficulty.first())
        }
        val prefs = dataStore.data.first()
        assertNull(prefs[hardModeKey])
        assertEquals("hard", prefs[difficultyKey])
    }

    @Test
    fun `vibration defaults to on and round-trips`() = runTest {
        assertTrue(repository.vibration.first())

        repository.setVibration(false)
        assertEquals(false, repository.vibration.first())

        repository.setVibration(true)
        assertTrue(repository.vibration.first())
    }

    @Test
    fun `other settings are untouched by the difficulty`() = runTest {
        repository.setShowTimer(false)
        repository.setAdsRemoved(true)

        repository.setDifficulty(Difficulty.HARD)

        assertEquals(false, repository.showTimer.first())
        assertTrue(repository.adsRemoved.first())
    }
}
