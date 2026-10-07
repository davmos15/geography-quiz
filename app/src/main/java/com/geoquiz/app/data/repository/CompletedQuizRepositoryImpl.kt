package com.geoquiz.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.geoquiz.app.di.LastResultStore
import com.geoquiz.app.domain.model.CompletedQuiz
import com.geoquiz.app.domain.repository.CompletedQuizRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** The "last_result" DataStore: one JSON-encoded [CompletedQuiz]. */
val Context.lastResultDataStore: DataStore<Preferences> by preferencesDataStore(name = "last_result")

@Singleton
class CompletedQuizRepositoryImpl @Inject constructor(
    @LastResultStore private val dataStore: DataStore<Preferences>
) : CompletedQuizRepository {

    override suspend fun save(quiz: CompletedQuiz) {
        val encoded = json.encodeToString(CompletedQuiz.serializer(), quiz)
        dataStore.edit { it[LAST_RESULT_KEY] = encoded }
    }

    override suspend fun saveIfAbsent(quiz: CompletedQuiz): Boolean {
        val encoded = json.encodeToString(CompletedQuiz.serializer(), quiz)
        var saved = false
        // DataStore serialises edits, so the check and the write can't interleave with another call.
        dataStore.edit { prefs ->
            if (decode(prefs[LAST_RESULT_KEY])?.id != quiz.id) {
                prefs[LAST_RESULT_KEY] = encoded
                saved = true
            }
        }
        return saved
    }

    override suspend fun get(id: String): CompletedQuiz? =
        decode(dataStore.data.first()[LAST_RESULT_KEY])?.takeIf { it.id == id }

    private fun decode(encoded: String?): CompletedQuiz? {
        if (encoded == null) return null
        return try {
            json.decodeFromString(CompletedQuiz.serializer(), encoded)
        } catch (_: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException: treat a corrupt or
            // incompatible record as missing.
            null
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        val LAST_RESULT_KEY = stringPreferencesKey("last_result")

        // Tolerates fields added or removed by later app versions.
        private val json = Json { ignoreUnknownKeys = true }
    }
}
