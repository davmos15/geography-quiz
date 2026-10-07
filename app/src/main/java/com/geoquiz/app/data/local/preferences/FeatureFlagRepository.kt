package com.geoquiz.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.geoquiz.app.di.FeatureFlagOverridesAllowed
import com.geoquiz.app.di.FeatureFlagStore
import com.geoquiz.app.domain.model.FeatureFlag
import com.geoquiz.app.domain.model.FeatureFlagState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature flag values: each flag's default, optionally overridden from the debug menu.
 * Overrides are only honoured when [overridesAllowed] is true (debug builds), so release
 * builds always use the defaults.
 */
@Singleton
class FeatureFlagRepository @Inject constructor(
    @FeatureFlagStore private val dataStore: DataStore<Preferences>,
    @FeatureFlagOverridesAllowed private val overridesAllowed: Boolean
) {

    val states: Flow<List<FeatureFlagState>> = dataStore.data.map { prefs ->
        FeatureFlag.entries.map { flag ->
            val override = if (overridesAllowed) prefs[keyFor(flag)] else null
            FeatureFlagState(
                flag = flag,
                enabled = override ?: flag.defaultEnabled,
                overridden = override != null
            )
        }
    }

    fun isEnabled(flag: FeatureFlag): Flow<Boolean> =
        states.map { list -> list.first { it.flag == flag }.enabled }.distinctUntilChanged()

    suspend fun setOverride(flag: FeatureFlag, enabled: Boolean) {
        if (!overridesAllowed) return
        dataStore.edit { prefs ->
            prefs[keyFor(flag)] = enabled
        }
    }

    suspend fun clearOverrides() {
        dataStore.edit { prefs ->
            FeatureFlag.entries.forEach { prefs.remove(keyFor(it)) }
        }
    }

    private fun keyFor(flag: FeatureFlag) = booleanPreferencesKey(flag.key)
}
