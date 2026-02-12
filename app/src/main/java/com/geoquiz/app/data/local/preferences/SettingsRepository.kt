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

    val playerName: Flow<String> = dataStore.data.map { prefs ->
        prefs[PLAYER_NAME_KEY] ?: ""
    }

    suspend fun setShowTimer(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_TIMER_KEY] = show
        }
    }

    suspend fun setPlayerName(name: String) {
        dataStore.edit { prefs ->
            prefs[PLAYER_NAME_KEY] = name
        }
    }

    companion object {
        private val SHOW_TIMER_KEY = booleanPreferencesKey("show_timer")
        private val PLAYER_NAME_KEY = stringPreferencesKey("player_name")
    }
}
