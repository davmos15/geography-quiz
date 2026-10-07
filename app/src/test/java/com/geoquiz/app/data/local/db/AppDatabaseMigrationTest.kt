package com.geoquiz.app.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters

/**
 * Upgrades a player database from every exported schema version to the current one and
 * checks that saved quiz, challenges and quiz history survive with their values.
 *
 * Each old file is built from `app/schemas/.../AppDatabase/<version>.json` and then opened
 * with the production builder ([DatabaseModule.provideAppDatabase]), so Room runs the real
 * migrations and validates the result against the current schema.
 *
 * Versions 2 and 3 were never exported or released (version 1 went straight to 4), so
 * there is no schema to start from.
 *
 * Player tables by version: saved_quizzes and challenges from 4, saved_quizzes.quizMode
 * from 6, quiz_history from 7, challenges.quizMode from 8. Up to 10 the same file also
 * held the static content, which version 11 drops.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class AppDatabaseMigrationTest(private val fromVersion: Int) {

    companion object {
        private const val CURRENT_VERSION = 11

        @JvmStatic
        @Parameters(name = "from version {0}")
        fun versions(): List<Array<Any>> = listOf(1, 4, 5, 6, 7, 8, 9, 10).map { arrayOf(it) }

        private val STATIC_TABLES =
            listOf("countries", "aliases", "capital_aliases", "flag_colors", "flag_elements")
        private val PLAYER_TABLES = listOf("saved_quizzes", "challenges", "quiz_history")
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase

    private val hasSavedQuizzesAndChallenges get() = fromVersion >= 4
    private val savedQuizHasMode get() = fromVersion >= 6
    private val hasQuizHistory get() = fromVersion >= 7
    private val challengeHasMode get() = fromVersion >= 8

    @Before
    fun createOldDatabaseWithData() {
        RoomSchemaFixture.createDatabase(context, AppDatabase.NAME, AppDatabase::class.java.name, fromVersion).apply {
            insertStaticRows()
            if (hasSavedQuizzesAndChallenges) insertSavedQuizAndChallenges()
            if (hasQuizHistory) insertQuizHistory()
            close()
        }
        database = DatabaseModule.provideAppDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun android.database.sqlite.SQLiteDatabase.insertStaticRows() {
        // Old static content, seeded at runtime before version 11.
        if (fromVersion >= 6) {
            execSQL(
                "INSERT INTO countries (cca3, commonName, officialName, region, subregion, nameLength, capital, flag) " +
                    "VALUES ('FRA', 'France', 'French Republic', 'Europe', 'Western Europe', 6, 'Paris', '')"
            )
            execSQL("INSERT INTO capital_aliases (countryCca3, alias, normalizedAlias) VALUES ('FRA', 'Paris', 'paris')")
            execSQL("INSERT INTO flag_colors (countryCca3, color) VALUES ('FRA', 'blue')")
        } else {
            execSQL(
                "INSERT INTO countries (cca3, commonName, officialName, region, subregion, nameLength) " +
                    "VALUES ('FRA', 'France', 'French Republic', 'Europe', 'Western Europe', 6)"
            )
        }
        execSQL("INSERT INTO aliases (countryCca3, alias, normalizedAlias) VALUES ('FRA', 'France', 'france')")
        if (fromVersion >= 10) {
            execSQL("INSERT INTO flag_elements (countryCca3, element) VALUES ('FRA', 'stripes')")
        }
    }

    private fun android.database.sqlite.SQLiteDatabase.insertSavedQuizAndChallenges() {
        val savedQuizMode = if (savedQuizHasMode) ", quizMode" to ", 'capitals'" else "" to ""
        execSQL(
            "INSERT INTO saved_quizzes (id, categoryType, categoryValue, answeredCountryCodes, " +
                "timeElapsedSeconds, savedAtMillis${savedQuizMode.first}) " +
                "VALUES (1, 'starting_letter', 'B', 'BRA,BEL', 42, 1700000000000${savedQuizMode.second})"
        )

        val challengeMode = if (challengeHasMode) "quizMode, " to "'flags', " else "" to ""
        val challengeColumns = "id, categoryType, categoryValue, categoryDisplayName, ${challengeMode.first}" +
            "challengerName, challengerScore, challengerTotal, challengerTime, myScore, myTotal, myTime, " +
            "direction, status, createdAtMillis"
        execSQL(
            "INSERT INTO challenges ($challengeColumns) VALUES ('abc123', 'region', 'Europe', 'Europe', " +
                "${challengeMode.second}'Sam', 30, 44, 300, NULL, NULL, NULL, 'received', 'pending', 1700000000001)"
        )
        execSQL(
            "INSERT INTO challenges ($challengeColumns) VALUES ('xyz789', 'starting_letter', 'M', 'Starts with M', " +
                "${challengeMode.second}'Alex', NULL, NULL, NULL, 12, 15, 95, 'sent', 'completed', 1700000000005)"
        )
    }

    private fun android.database.sqlite.SQLiteDatabase.insertQuizHistory() {
        execSQL(
            "INSERT INTO quiz_history (quizMode, categoryType, categoryValue, correctAnswers, totalQuestions, " +
                "incorrectGuesses, score, timeElapsedSeconds, perfectBonus, completedAtMillis) " +
                "VALUES ('countries', 'all', 'all', 150, 197, 12, 114.2, 900, 0, 1700000000002)"
        )
        execSQL(
            "INSERT INTO quiz_history (quizMode, categoryType, categoryValue, correctAnswers, totalQuestions, " +
                "incorrectGuesses, score, timeElapsedSeconds, perfectBonus, completedAtMillis) " +
                "VALUES ('flags', 'flag_color', 'red', 10, 10, 0, 12.0, 60, 1, 1700000000003)"
        )
    }

    @Test
    fun `upgrade reaches the current version with only player tables`() {
        val db = database.openHelper.readableDatabase
        assertEquals(CURRENT_VERSION, db.version)
        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        STATIC_TABLES.forEach { assertFalse("$it should be dropped (from $fromVersion)", it in tables) }
        assertTrue("player tables missing: $tables", tables.containsAll(PLAYER_TABLES))
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.query("PRAGMA integrity_check").use {
            it.moveToFirst()
            assertEquals("ok", it.getString(0))
        }
    }

    @Test
    fun `saved quiz survives the upgrade`() = runBlocking {
        val saved = database.savedQuizDao().getSavedQuiz()
        if (!hasSavedQuizzesAndChallenges) {
            assertNull(saved)
            return@runBlocking
        }
        assertEquals(
            SavedQuizEntity(
                id = 1,
                categoryType = "starting_letter",
                categoryValue = "B",
                answeredCountryCodes = "BRA,BEL",
                timeElapsedSeconds = 42,
                savedAtMillis = 1700000000000L,
                // Rows from before version 6 take the column default.
                quizMode = if (savedQuizHasMode) "capitals" else "countries"
            ),
            saved
        )
    }

    @Test
    fun `challenges survive the upgrade`() = runBlocking {
        val challenges = database.challengeDao().getAllChallenges().first()
        if (!hasSavedQuizzesAndChallenges) {
            assertTrue(challenges.isEmpty())
            return@runBlocking
        }
        // Rows from before version 8 take the column default.
        val mode = if (challengeHasMode) "flags" else "countries"
        assertEquals(
            listOf(
                ChallengeEntity(
                    id = "xyz789", categoryType = "starting_letter", categoryValue = "M",
                    categoryDisplayName = "Starts with M", quizMode = mode, challengerName = "Alex",
                    challengerScore = null, challengerTotal = null, challengerTime = null,
                    myScore = 12, myTotal = 15, myTime = 95,
                    direction = "sent", status = "completed", createdAtMillis = 1700000000005L
                ),
                ChallengeEntity(
                    id = "abc123", categoryType = "region", categoryValue = "Europe",
                    categoryDisplayName = "Europe", quizMode = mode, challengerName = "Sam",
                    challengerScore = 30, challengerTotal = 44, challengerTime = 300,
                    myScore = null, myTotal = null, myTime = null,
                    direction = "received", status = "pending", createdAtMillis = 1700000000001L
                )
            ),
            challenges
        )
    }

    @Test
    fun `quiz history survives the upgrade`() = runBlocking {
        val history = database.quizHistoryDao().getRecentQuizzes(10).first()
        if (!hasQuizHistory) {
            assertTrue(history.isEmpty())
            return@runBlocking
        }
        assertEquals(
            listOf(
                QuizHistoryEntity(
                    id = 2, quizMode = "flags", categoryType = "flag_color", categoryValue = "red",
                    correctAnswers = 10, totalQuestions = 10, incorrectGuesses = 0, score = 12.0,
                    timeElapsedSeconds = 60, perfectBonus = true, completedAtMillis = 1700000000003L
                ),
                QuizHistoryEntity(
                    id = 1, quizMode = "countries", categoryType = "all", categoryValue = "all",
                    correctAnswers = 150, totalQuestions = 197, incorrectGuesses = 12, score = 114.2,
                    timeElapsedSeconds = 900, perfectBonus = false, completedAtMillis = 1700000000002L
                )
            ),
            history
        )
        assertEquals(150L, database.quizHistoryDao().getTotalCorrectAnswersForModeSync("countries"))
    }

    @Test
    fun `player tables accept new rows after the upgrade`() = runBlocking {
        val history = QuizHistoryEntity(
            quizMode = "capitals", categoryType = "region", categoryValue = "Oceania",
            correctAnswers = 5, totalQuestions = 14, incorrectGuesses = 3, score = 4.5,
            timeElapsedSeconds = 120, perfectBonus = false, completedAtMillis = 1800000000000L
        )
        database.quizHistoryDao().insertQuizResult(history)
        assertEquals(
            history.copy(id = if (hasQuizHistory) 3 else 1),
            database.quizHistoryDao().getRecentQuizzes(1).first().single()
        )

        val saved = SavedQuizEntity(
            categoryType = "region", categoryValue = "Asia", answeredCountryCodes = "JPN",
            timeElapsedSeconds = 7, savedAtMillis = 1800000000001L, quizMode = "flags"
        )
        database.savedQuizDao().saveQuiz(saved)
        assertEquals(saved, database.savedQuizDao().getSavedQuiz())
    }
}
