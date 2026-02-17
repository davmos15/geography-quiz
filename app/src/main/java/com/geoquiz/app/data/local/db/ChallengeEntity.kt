package com.geoquiz.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val categoryType: String,
    val categoryValue: String,
    val categoryDisplayName: String,
    val quizMode: String,
    val challengerName: String,
    val challengerScore: Int?,
    val challengerTotal: Int?,
    val challengerTime: Int?,
    val myScore: Int?,
    val myTotal: Int?,
    val myTime: Int?,
    val direction: String,
    val status: String,
    val createdAtMillis: Long
)
