package com.geoquiz.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    val showTimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_TIMER_KEY] ?: true
    }

    val showFlags: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_FLAGS_KEY] ?: false
    }

    val showCountryHint: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_COUNTRY_HINT_KEY] ?: false
    }

    val hardMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HARD_MODE_KEY] ?: false
    }

    val playerName: Flow<String> = dataStore.data.map { prefs ->
        prefs[PLAYER_NAME_KEY] ?: ""
    }

    val adsRemoved: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ADS_REMOVED_KEY] ?: false
    }

    suspend fun setShowTimer(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_TIMER_KEY] = show
        }
    }

    suspend fun setShowFlags(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_FLAGS_KEY] = show
        }
    }

    suspend fun setShowCountryHint(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_COUNTRY_HINT_KEY] = show
        }
    }

    suspend fun setHardMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[HARD_MODE_KEY] = enabled
        }
    }

    suspend fun setPlayerName(name: String) {
        dataStore.edit { prefs ->
            prefs[PLAYER_NAME_KEY] = name
        }
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        dataStore.edit { prefs ->
            prefs[ADS_REMOVED_KEY] = removed
        }
    }

    companion object {
        private val SHOW_TIMER_KEY = booleanPreferencesKey("show_timer")
        private val SHOW_FLAGS_KEY = booleanPreferencesKey("show_flags")
        private val SHOW_COUNTRY_HINT_KEY = booleanPreferencesKey("show_country_hint")
        private val HARD_MODE_KEY = booleanPreferencesKey("hard_mode")
        private val PLAYER_NAME_KEY = stringPreferencesKey("player_name")
        private val ADS_REMOVED_KEY = booleanPreferencesKey("ads_removed")
    }
}
