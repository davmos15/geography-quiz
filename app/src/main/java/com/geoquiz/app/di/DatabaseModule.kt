package com.geoquiz.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.SavedQuizDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_quizzes (
                    id INTEGER NOT NULL PRIMARY KEY,
                    categoryType TEXT NOT NULL,
                    categoryValue TEXT NOT NULL,
                    answeredCountryCodes TEXT NOT NULL,
                    timeElapsedSeconds INTEGER NOT NULL,
                    savedAtMillis INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS challenges (
                    id TEXT NOT NULL PRIMARY KEY,
                    categoryType TEXT NOT NULL,
                    categoryValue TEXT NOT NULL,
                    categoryDisplayName TEXT NOT NULL,
                    challengerName TEXT NOT NULL,
                    challengerScore INTEGER,
                    challengerTotal INTEGER,
                    challengerTime INTEGER,
                    myScore INTEGER,
                    myTotal INTEGER,
                    myTime INTEGER,
                    direction TEXT NOT NULL,
                    status TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Clear country data so it re-seeds with only UN member states
            db.execSQL("DELETE FROM aliases")
            db.execSQL("DELETE FROM countries")
        }
    }

    // Direct migration for fresh installs from v1 to v3
    private val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2.migrate(db)
            MIGRATION_2_3.migrate(db)
        }
    }

    private val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2.migrate(db)
            MIGRATION_2_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    private val MIGRATION_2_4 = object : Migration(2, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Clear country data so it re-seeds without short code aliases
            db.execSQL("DELETE FROM aliases")
            db.execSQL("DELETE FROM countries")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "geoquiz.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3, MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4, MIGRATION_4_5)
            .build()
    }

    @Provides
    fun provideCountryDao(database: AppDatabase): CountryDao {
        return database.countryDao()
    }

    @Provides
    fun provideSavedQuizDao(database: AppDatabase): SavedQuizDao {
        return database.savedQuizDao()
    }

    @Provides
    fun provideChallengeDao(database: AppDatabase): ChallengeDao {
        return database.challengeDao()
    }
}
