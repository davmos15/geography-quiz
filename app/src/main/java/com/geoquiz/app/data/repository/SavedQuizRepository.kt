package com.geoquiz.app.data.repository

import com.geoquiz.app.data.local.db.SavedQuizDao
import com.geoquiz.app.data.local.db.SavedQuizEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedQuizRepository @Inject constructor(
    private val savedQuizDao: SavedQuizDao
) {
    val savedQuiz: Flow<SavedQuizEntity?> = savedQuizDao.getSavedQuizFlow()

    suspend fun saveQuizState(
        categoryType: String,
        categoryValue: String,
        answeredCodes: Set<String>,
        timeElapsed: Int
    ) {
        val json = Json.encodeToString(answeredCodes.toList())
        savedQuizDao.saveQuiz(
            SavedQuizEntity(
                categoryType = categoryType,
                categoryValue = categoryValue,
                answeredCountryCodes = json,
                timeElapsedSeconds = timeElapsed,
                savedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSavedQuiz(): SavedQuizEntity? = savedQuizDao.getSavedQuiz()

    suspend fun clearSavedQuiz() = savedQuizDao.clearSavedQuiz()

    fun parseAnsweredCodes(json: String): Set<String> {
        return try {
            Json.decodeFromString<List<String>>(json).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
