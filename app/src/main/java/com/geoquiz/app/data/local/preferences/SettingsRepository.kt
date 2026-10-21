package com.geoquiz.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geoquiz.app.di.SettingsStore
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

    /** Game-feel sound effects (8.5): correct, wrong and perfect-score chimes. The system ringer
     * also applies ([com.geoquiz.app.data.service.SoundEffectPlayer] skips playback outside
     * `RINGER_MODE_NORMAL`), the same way [vibration] leaves the system touch-feedback setting to
     * `HapticFeedbackPlayer`. */
    val soundEffects: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SOUND_EFFECTS_KEY] ?: true
    }

    val playerName: Flow<String> = dataStore.data.map { prefs ->
        prefs[PLAYER_NAME_KEY] ?: ""
    }

    val adsRemoved: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ADS_REMOVED_KEY] ?: false
    }

    /**
     * The versionCode the one-off "What's new" screen (P10-F) last showed to this player, or
     * `null` before that has ever been decided. Compared against [WHATS_NEW_VERSION] by
     * `ui.whatsnew.WhatsNewViewModel`.
     */
    val whatsNewSeenVersion: Flow<Int?> = dataStore.data.map { prefs ->
        prefs[WHATS_NEW_SEEN_VERSION_KEY]
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

    suspend fun setSoundEffects(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SOUND_EFFECTS_KEY] = enabled
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

    /** Records that the player has seen (or will never see) the "What's new" screen for [version]. */
    suspend fun setWhatsNewSeenVersion(version: Int) {
        dataStore.edit { prefs ->
            prefs[WHATS_NEW_SEEN_VERSION_KEY] = version
        }
    }

    companion object {
        /**
         * The versionCode (18, 3.0.0) the current "What's new" content (`ui.whatsnew`) describes.
         * A future release with new "What's new" content bumps this constant along with its
         * content; a player who already saw an older version sees it again because their stored
         * [whatsNewSeenVersion] is lower.
         */
        const val WHATS_NEW_VERSION = 18
        private val SHOW_TIMER_KEY = booleanPreferencesKey("show_timer")
        private val SHOW_FLAGS_KEY = booleanPreferencesKey("show_flags")
        private val SHOW_COUNTRY_HINT_KEY = booleanPreferencesKey("show_country_hint")
        private val VIBRATION_KEY = booleanPreferencesKey("vibration")
        private val SOUND_EFFECTS_KEY = booleanPreferencesKey("sound_effects")
        private val PLAYER_NAME_KEY = stringPreferencesKey("player_name")
        /** Kept by "Reset all data" so a paying user never sees ads before Play restores the purchase. */
        val ADS_REMOVED_KEY = booleanPreferencesKey("ads_removed")

        /**
         * Kept by "Reset all data" for the same reason as [ADS_REMOVED_KEY] (D10): resetting a
         * device is not the same as a fresh install, so it must not bring the "What's new" screen
         * back for a player who already saw it, or show it to one who legitimately never will.
         */
        val WHATS_NEW_SEEN_VERSION_KEY = intPreferencesKey("whats_new_seen_version")
    }
}
