package com.geoquiz.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geoquiz.app.di.SettingsStore
import com.geoquiz.app.domain.model.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    /** The "settings" DataStore ([settingsDataStore]); a file-backed one in tests. */
    @SettingsStore private val dataStore: DataStore<Preferences>
) {

    val showTimer: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_TIMER_KEY] ?: true
    }

    val showFlags: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_FLAGS_KEY] ?: false
    }

    /** In-app vibration for answer feedback (3.3); the system touch-feedback setting also applies. */
    val vibration: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[VIBRATION_KEY] ?: true
    }

    val showCountryHint: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_COUNTRY_HINT_KEY] ?: false
    }

    /**
     * The remembered default tier, preselected on the category list. Installs from before 3.2
     * only had the `hard_mode` switch: on means [Difficulty.HARD], off or unset [Difficulty.NORMAL].
     */
    val difficulty: Flow<Difficulty> = dataStore.data.map { prefs -> difficultyFrom(prefs) }

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

    suspend fun setVibration(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[VIBRATION_KEY] = enabled
        }
    }

    suspend fun setShowCountryHint(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_COUNTRY_HINT_KEY] = show
        }
    }

    /** Remembers [difficulty] as the default and drops the legacy `hard_mode` switch. */
    suspend fun setDifficulty(difficulty: Difficulty) {
        dataStore.edit { prefs ->
            prefs[DIFFICULTY_KEY] = difficulty.id
            prefs.remove(LEGACY_HARD_MODE_KEY)
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
        private val VIBRATION_KEY = booleanPreferencesKey("vibration")
        private val DIFFICULTY_KEY = stringPreferencesKey("difficulty")
        /** Replaced by [DIFFICULTY_KEY] in 3.2; only read to migrate the old setting. */
        private val LEGACY_HARD_MODE_KEY = booleanPreferencesKey("hard_mode")
        private val PLAYER_NAME_KEY = stringPreferencesKey("player_name")
        /** Kept by "Reset all data" so a paying user never sees ads before Play restores the purchase. */
        val ADS_REMOVED_KEY = booleanPreferencesKey("ads_removed")

        internal fun difficultyFrom(prefs: Preferences): Difficulty =
            Difficulty.fromIdOrNull(prefs[DIFFICULTY_KEY])
                ?: if (prefs[LEGACY_HARD_MODE_KEY] == true) Difficulty.HARD else Difficulty.DEFAULT
    }
}
