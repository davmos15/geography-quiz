package com.geoquiz.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations for the player database ([AppDatabase], `geoquiz.db`).
 *
 * Up to version 10 this database also held the static game content, and several
 * migrations used to `DELETE` it to force a re-seed. Since version 11 that content
 * ships separately in [StaticDatabase], and [MIGRATION_10_11] drops the old static
 * tables, so those deletes are gone. Every other statement is kept so that every
 * old version still migrates.
 */
object AppDatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
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

    val MIGRATION_2_3 = object : Migration(2, 3) {
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

    /** Used to clear the static tables for a re-seed; nothing left to do. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    val MIGRATION_1_3 = object : Migration(1, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2.migrate(db)
            MIGRATION_2_3.migrate(db)
        }
    }

    val MIGRATION_1_4 = object : Migration(1, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2.migrate(db)
            MIGRATION_2_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    val MIGRATION_2_4 = object : Migration(2, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3.migrate(db)
            MIGRATION_3_4.migrate(db)
        }
    }

    /** Used to clear the static tables for a re-seed; nothing left to do. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
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
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
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

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE challenges ADD COLUMN quizMode TEXT NOT NULL DEFAULT 'countries'")
        }
    }

    /** Used to clear the static tables for a re-seed; nothing left to do. */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS flag_elements (
                    countryCca3 TEXT NOT NULL,
                    element TEXT NOT NULL,
                    PRIMARY KEY(countryCca3, element),
                    FOREIGN KEY(countryCca3) REFERENCES countries(cca3) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flag_elements_element ON flag_elements(element)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_flag_elements_countryCca3 ON flag_elements(countryCca3)")
        }
    }

    /** Static content moved to [StaticDatabase]: drop the old copies, children first. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS flag_elements")
            db.execSQL("DROP TABLE IF EXISTS flag_colors")
            db.execSQL("DROP TABLE IF EXISTS capital_aliases")
            db.execSQL("DROP TABLE IF EXISTS aliases")
            db.execSQL("DROP TABLE IF EXISTS countries")
        }
    }

    /**
     * Difficulty tiers (3.2): the tier each history row and the resume save was played at.
     * Everything recorded before had no tiers and counts as Normal.
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE quiz_history ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'normal'")
            db.execSQL("ALTER TABLE saved_quizzes ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'normal'")
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3,
        MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4,
        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12
    )
}
