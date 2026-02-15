package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_history",
    indices = [
        Index(value = ["quizMode", "categoryType", "categoryValue"]),
        Index(value = ["completedAtMillis"])
    ]
)
data class QuizHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizMode: String,
    val categoryType: String,
    val categoryValue: String,
    val correctAnswers: Int,
    val totalQuestions: Int,
    val incorrectGuesses: Int,
    val score: Double,
    val timeElapsedSeconds: Int,
    val perfectBonus: Boolean,
    val completedAtMillis: Long
)

data class QuizBestScore(
    val categoryType: String,
    val categoryValue: String,
    val score: Double,
    val correctAnswers: Int,
    val totalQuestions: Int
)
