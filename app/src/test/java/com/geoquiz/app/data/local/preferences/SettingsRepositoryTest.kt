package com.geoquiz.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

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
    fun `vibration defaults to on and round-trips`() = runTest {
        assertTrue(repository.vibration.first())

        repository.setVibration(false)
        assertEquals(false, repository.vibration.first())

        repository.setVibration(true)
        assertTrue(repository.vibration.first())
    }

    @Test
    fun `sound effects default to on and round-trip`() = runTest {
        assertTrue(repository.soundEffects.first())

        repository.setSoundEffects(false)
        assertEquals(false, repository.soundEffects.first())

        repository.setSoundEffects(true)
        assertTrue(repository.soundEffects.first())
    }

    @Test
    fun `whats new seen version is unset on a fresh install`() = runTest {
        assertNull(repository.whatsNewSeenVersion.first())
    }

    @Test
    fun `whats new seen version round-trips`() = runTest {
        repository.setWhatsNewSeenVersion(SettingsRepository.WHATS_NEW_VERSION)

        assertEquals(SettingsRepository.WHATS_NEW_VERSION, repository.whatsNewSeenVersion.first())

        val prefs = dataStore.data.first()
        assertEquals(
            SettingsRepository.WHATS_NEW_VERSION,
            prefs[intPreferencesKey("whats_new_seen_version")]
        )
    }
}
