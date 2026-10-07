package com.geoquiz.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.geoquiz.app.domain.model.FeatureFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeatureFlagRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "feature_flags.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `all flags report their defaults when nothing is stored`() = runTest {
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = true)

        val states = repository.states.first()

        assertEquals(FeatureFlag.entries.size, states.size)
        states.forEach { state ->
            assertEquals(state.flag.defaultEnabled, state.enabled)
            assertFalse(state.overridden)
        }
    }

    @Test
    fun `override changes the value and is marked as overridden`() = runTest {
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = true)
        val flag = FeatureFlag.TAP_THE_MAP

        repository.setOverride(flag, !flag.defaultEnabled)

        assertEquals(!flag.defaultEnabled, repository.isEnabled(flag).first())
        val state = repository.states.first().single { it.flag == flag }
        assertTrue(state.overridden)
    }

    @Test
    fun `override only affects its own flag`() = runTest {
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = true)

        repository.setOverride(FeatureFlag.BORDER_HOP, true)

        repository.states.first()
            .filter { it.flag != FeatureFlag.BORDER_HOP }
            .forEach { assertEquals(it.flag.defaultEnabled, it.enabled) }
    }

    @Test
    fun `clearOverrides restores defaults`() = runTest {
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = true)
        FeatureFlag.entries.forEach { repository.setOverride(it, !it.defaultEnabled) }

        repository.clearOverrides()

        repository.states.first().forEach { state ->
            assertEquals(state.flag.defaultEnabled, state.enabled)
            assertFalse(state.overridden)
        }
    }

    @Test
    fun `stored overrides are ignored when overrides are not allowed`() = runTest {
        val flag = FeatureFlag.CURRENCIES
        dataStore.edit { it[booleanPreferencesKey(flag.key)] = !flag.defaultEnabled }
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = false)

        assertEquals(flag.defaultEnabled, repository.isEnabled(flag).first())
        assertFalse(repository.states.first().single { it.flag == flag }.overridden)
    }

    @Test
    fun `setOverride is a no-op when overrides are not allowed`() = runTest {
        val flag = FeatureFlag.CURRENCIES
        val repository = FeatureFlagRepository(dataStore, overridesAllowed = false)

        repository.setOverride(flag, !flag.defaultEnabled)

        assertEquals(null, dataStore.data.first()[booleanPreferencesKey(flag.key)])
    }

    @Test
    fun `flag keys are unique`() {
        val keys = FeatureFlag.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
