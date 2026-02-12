package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_quizzes")
data class SavedQuizEntity(
    @PrimaryKey val id: Int = 1,
    val categoryType: String,
    val categoryValue: String,
    val answeredCountryCodes: String,
    val timeElapsedSeconds: Int,
    val savedAtMillis: Long,
    val quizMode: String = "countries"
)
