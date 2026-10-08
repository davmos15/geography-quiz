package com.geoquiz.app.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.geoquiz.app.di.SettingsStore
import com.geoquiz.app.domain.model.PinnedCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pinned categories (3.4c, D23), kept in the "settings" DataStore so "Reset all data" clears
 * them with the other settings. Stored as one JSON array of [PinnedCategory.key]s in the order
 * pinned (newest last); a preferences string set would lose that order.
 */
@Singleton
class PinnedCategoriesRepository @Inject constructor(
    @SettingsStore private val dataStore: DataStore<Preferences>
) {

    /**
     * Every valid pin across all modes, oldest first. Duplicates, malformed entries and pins
     * that are no longer valid ([PinnedCategory.fromKeyOrNull]) are left out.
     */
    val pinnedCategories: Flow<List<PinnedCategory>> = dataStore.data
        .map { prefs -> readPins(prefs) }
        .distinctUntilChanged()

    /**
     * Pins ([pinned] true, added last; nothing happens if already pinned) or unpins the
     * category. An invalid pin is never stored. Writing also drops stored entries that are no
     * longer valid.
     */
    suspend fun setPinned(modeId: String, categoryType: String, categoryValue: String, pinned: Boolean) {
        dataStore.edit { prefs ->
            val current = readPins(prefs)
            val updated = if (pinned) {
                val pin = PinnedCategory.validOrNull(modeId, categoryType, categoryValue)
                if (pin == null || pin in current) current else current + pin
            } else {
                current.filterNot {
                    it.modeId == modeId && it.categoryType == categoryType && it.categoryValue == categoryValue
                }
            }
            writePins(prefs, updated)
        }
    }

    private fun readPins(prefs: Preferences): List<PinnedCategory> {
        val raw = prefs[PINNED_CATEGORIES_KEY] ?: return emptyList()
        val keys = try {
            json.decodeFromString(keyListSerializer, raw)
        } catch (e: IllegalArgumentException) {
            // Corrupt value (SerializationException is an IllegalArgumentException): no pins.
            emptyList()
        }
        return keys.mapNotNull(PinnedCategory::fromKeyOrNull).distinct()
    }

    private fun writePins(prefs: MutablePreferences, pins: List<PinnedCategory>) {
        if (pins.isEmpty()) {
            prefs.remove(PINNED_CATEGORIES_KEY)
        } else {
            prefs[PINNED_CATEGORIES_KEY] = json.encodeToString(keyListSerializer, pins.map { it.key })
        }
    }

    companion object {
        /** JSON array of `"modeId|categoryType|categoryValue"` strings, oldest pin first. */
        val PINNED_CATEGORIES_KEY = stringPreferencesKey("pinned_categories")

        private val json = Json
        private val keyListSerializer = ListSerializer(String.serializer())
    }
}
