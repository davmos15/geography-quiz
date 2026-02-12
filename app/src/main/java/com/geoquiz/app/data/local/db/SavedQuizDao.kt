package com.geoquiz.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQuizDao {
    @Query("SELECT * FROM saved_quizzes WHERE id = 1")
    suspend fun getSavedQuiz(): SavedQuizEntity?

    @Query("SELECT * FROM saved_quizzes WHERE id = 1")
    fun getSavedQuizFlow(): Flow<SavedQuizEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuiz(quiz: SavedQuizEntity)

    @Query("DELETE FROM saved_quizzes WHERE id = 1")
    suspend fun clearSavedQuiz()
}
