package com.geoquiz.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.geoquiz.app.data.local.db.AppDatabase
import com.geoquiz.app.data.local.db.CapitalAliasDao
import com.geoquiz.app.data.local.db.ChallengeDao
import com.geoquiz.app.data.local.db.CountryDao
import com.geoquiz.app.data.local.db.FlagColorDao
import com.geoquiz.app.data.local.db.QuizHistoryDao
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
            db.execSQL("DELETE FROM aliases")
            db.execSQL("DELETE FROM countries")
        }
    }

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
            db.execSQL("DELETE FROM aliases")
            db.execSQL("DELETE FROM countries")
        }
    }

    private fun createV6Tables(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE countries ADD COLUMN capital TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE countries ADD COLUMN flag TEXT NOT NULL DEFAULT ''")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS capital_aliases (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                countryCca3 TEXT NOT NULL,
                alias TEXT NOT NULL,
                normalizedAlias TEXT NOT NULL,
                FOREIGN KEY(countryCca3) REFERENCES countries(cca3) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_capital_aliases_normalizedAlias ON capital_aliases(normalizedAlias)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_capital_aliases_countryCca3 ON capital_aliases(countryCca3)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS flag_colors (
                countryCca3 TEXT NOT NULL,
                color TEXT NOT NULL,
                PRIMARY KEY(countryCca3, color),
                FOREIGN KEY(countryCca3) REFERENCES countries(cca3) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_flag_colors_color ON flag_colors(color)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_flag_colors_countryCca3 ON flag_colors(countryCca3)")
        // Add quizMode to saved_quizzes
        db.execSQL("ALTER TABLE saved_quizzes ADD COLUMN quizMode TEXT NOT NULL DEFAULT 'countries'")
        // Clear data to force re-seed with capitals and flag colors
        db.execSQL("DELETE FROM aliases")
        db.execSQL("DELETE FROM countries")
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createV6Tables(db)
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE challenges ADD COLUMN quizMode TEXT NOT NULL DEFAULT 'countries'")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Clear country data to force re-seed with 4 additional countries
            db.execSQL("DELETE FROM flag_colors")
            db.execSQL("DELETE FROM capital_aliases")
            db.execSQL("DELETE FROM aliases")
            db.execSQL("DELETE FROM countries")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS quiz_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    quizMode TEXT NOT NULL,
                    categoryType TEXT NOT NULL,
                    categoryValue TEXT NOT NULL,
                    correctAnswers INTEGER NOT NULL,
                    totalQuestions INTEGER NOT NULL,
                    incorrectGuesses INTEGER NOT NULL,
                    score REAL NOT NULL,
                    timeElapsedSeconds INTEGER NOT NULL,
                    perfectBonus INTEGER NOT NULL,
                    completedAtMillis INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_history_quizMode_categoryType_categoryValue ON quiz_history(quizMode, categoryType, categoryValue)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_quiz_history_completedAtMillis ON quiz_history(completedAtMillis)")
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
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3,
                MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9
            )
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

    @Provides
    fun provideCapitalAliasDao(database: AppDatabase): CapitalAliasDao {
        return database.capitalAliasDao()
    }

    @Provides
    fun provideFlagColorDao(database: AppDatabase): FlagColorDao {
        return database.flagColorDao()
    }

    @Provides
    fun provideQuizHistoryDao(database: AppDatabase): QuizHistoryDao {
        return database.quizHistoryDao()
    }
}
