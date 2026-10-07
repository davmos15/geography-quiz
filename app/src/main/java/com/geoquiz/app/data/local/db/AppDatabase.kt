package com.geoquiz.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Player data (saved quiz, challenges, quiz history) in `geoquiz.db`.
 * Schema changes need a migration in [AppDatabaseMigrations]. Static game
 * content moved to [StaticDatabase] in version 11. Version 12 added the difficulty columns.
 */
@Database(
    entities = [
        SavedQuizEntity::class,
        ChallengeEntity::class,
        QuizHistoryEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedQuizDao(): SavedQuizDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun quizHistoryDao(): QuizHistoryDao

    companion object {
        const val NAME = "geoquiz.db"
    }
}
