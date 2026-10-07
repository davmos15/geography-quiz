package com.geoquiz.app.data.local.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.di.DatabaseModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Version 10 held the static content and the player data together; version 11 keeps only
 * the player data. Upgrading must keep saved quiz, challenges and quiz history.
 *
 * The version 10 file is built from `app/schemas/.../AppDatabase/10.json` and then opened
 * with the production builder ([DatabaseModule.provideAppDatabase]), so Room runs the real
 * migrations and validates the result against the version 11 schema.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase

    private val staticTables = listOf("countries", "aliases", "capital_aliases", "flag_colors", "flag_elements")

    @Before
    fun createVersion10WithData() {
        RoomSchemaFixture.createDatabase(context, AppDatabase.NAME, AppDatabase::class.java.name, 10).apply {
            // Old static content (seeded at runtime before version 11).
            execSQL(
                "INSERT INTO countries (cca3, commonName, officialName, region, subregion, nameLength, capital, flag) " +
                    "VALUES ('FRA', 'France', 'French Republic', 'Europe', 'Western Europe', 6, 'Paris', '')"
            )
            execSQL("INSERT INTO aliases (countryCca3, alias, normalizedAlias) VALUES ('FRA', 'France', 'france')")
            execSQL("INSERT INTO capital_aliases (countryCca3, alias, normalizedAlias) VALUES ('FRA', 'Paris', 'paris')")
            execSQL("INSERT INTO flag_colors (countryCca3, color) VALUES ('FRA', 'blue')")
            execSQL("INSERT INTO flag_elements (countryCca3, element) VALUES ('FRA', 'stripes')")

            // Player data.
            execSQL(
                "INSERT INTO saved_quizzes (id, categoryType, categoryValue, answeredCountryCodes, " +
                    "timeElapsedSeconds, savedAtMillis, quizMode) " +
                    "VALUES (1, 'starting_letter', 'B', 'BRA,BEL', 42, 1700000000000, 'capitals')"
            )
            execSQL(
                "INSERT INTO challenges (id, categoryType, categoryValue, categoryDisplayName, quizMode, " +
                    "challengerName, challengerScore, challengerTotal, challengerTime, myScore, myTotal, myTime, " +
                    "direction, status, createdAtMillis) " +
                    "VALUES ('abc123', 'region', 'Europe', 'Europe', 'countries', 'Sam', 30, 44, 300, " +
                    "NULL, NULL, NULL, 'received', 'pending', 1700000000001)"
            )
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
            close()
        }
        database = DatabaseModule.provideAppDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upgrade to 11 drops the static tables`() {
        val db = database.openHelper.readableDatabase
        assertEquals(11, db.version)
        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        staticTables.forEach { assertFalse("$it should be dropped", it in tables) }
        assertTrue(tables.containsAll(listOf("saved_quizzes", "challenges", "quiz_history")))
    }

    @Test
    fun `saved quiz survives the upgrade`() = runBlocking {
        val saved = database.savedQuizDao().getSavedQuiz()
        assertNotNull(saved)
        assertEquals("starting_letter", saved!!.categoryType)
        assertEquals("B", saved.categoryValue)
        assertEquals("BRA,BEL", saved.answeredCountryCodes)
        assertEquals(42, saved.timeElapsedSeconds)
        assertEquals(1700000000000L, saved.savedAtMillis)
        assertEquals("capitals", saved.quizMode)
    }

    @Test
    fun `challenges survive the upgrade`() = runBlocking {
        val challenges = database.challengeDao().getAllChallenges().first()
        assertEquals(1, challenges.size)
        val challenge = challenges.single()
        assertEquals("abc123", challenge.id)
        assertEquals("Sam", challenge.challengerName)
        assertEquals(30, challenge.challengerScore)
        assertEquals(44, challenge.challengerTotal)
        assertEquals(null, challenge.myScore)
        assertEquals("pending", challenge.status)
    }

    @Test
    fun `quiz history survives the upgrade`() = runBlocking {
        val history = database.quizHistoryDao().getRecentQuizzes(10).first()
        assertEquals(listOf("flags", "countries"), history.map { it.quizMode })
        assertTrue(history.first().perfectBonus)
        assertEquals(114.2, history.last().score, 0.0001)
        assertEquals(150L, database.quizHistoryDao().getTotalCorrectAnswersForModeSync("countries"))
    }
}
