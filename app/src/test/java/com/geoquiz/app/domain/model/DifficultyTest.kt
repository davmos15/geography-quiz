package com.geoquiz.app.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyTest {

    @Test
    fun `tier rules match the decisions`() {
        assertTrue(Difficulty.NORMAL.allowsTypos)
        assertNull(Difficulty.NORMAL.strikeLimit)
        assertFalse(Difficulty.NORMAL.timerAlwaysShown)

        assertFalse(Difficulty.HARD.allowsTypos)
        assertEquals(3, Difficulty.HARD.strikeLimit)
        assertTrue(Difficulty.HARD.timerAlwaysShown)

        // D16: Easy never counts for achievements or leaderboards.
        assertFalse(Difficulty.EASY.countsForAchievements)
        assertTrue(Difficulty.NORMAL.countsForAchievements)
        assertTrue(Difficulty.HARD.countsForAchievements)
    }

    @Test
    fun `ids are stable and unknown ids read as normal`() {
        assertEquals(listOf("easy", "normal", "hard"), Difficulty.entries.map { it.id })
        assertEquals(Difficulty.NORMAL, Difficulty.fromIdOrDefault(null))
        assertEquals(Difficulty.NORMAL, Difficulty.fromIdOrDefault("nightmare"))
        assertEquals(Difficulty.HARD, Difficulty.fromIdOrDefault("hard"))
    }

    @Test
    fun `challenges are never easy`() {
        assertEquals(Difficulty.NORMAL, Difficulty.forChallenge(null, Difficulty.EASY))
        assertEquals(Difficulty.NORMAL, Difficulty.forChallenge(null, Difficulty.NORMAL))
        assertEquals(Difficulty.HARD, Difficulty.forChallenge(null, Difficulty.HARD))
        assertEquals(Difficulty.NORMAL, Difficulty.forChallenge(Difficulty.EASY, Difficulty.EASY))
        assertEquals(Difficulty.HARD, Difficulty.forChallenge(Difficulty.EASY, Difficulty.HARD))
        assertEquals(Difficulty.NORMAL, Difficulty.forChallenge(Difficulty.NORMAL, Difficulty.HARD))
        assertEquals(Difficulty.HARD, Difficulty.forChallenge(Difficulty.HARD, Difficulty.EASY))
    }
}

/** `last_result` records written before 3.2 must still decode (same Json setup as the repository). */
class CompletedQuizDifficultyTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun oldRecord(hardMode: Boolean) = """
        {"id":"old-1","quizModeId":"countries","categoryType":"all","categoryValue":"_",
         "categoryName":"All Countries","countryCodes":["FRA","DEU"],"answeredCodes":["FRA"],
         "incorrectGuessStrings":[],"correct":1,"total":2,"timeSeconds":30,"score":0.5,
         "perfectBonus":false,"incorrectGuesses":0,"hardMode":$hardMode,"challengeId":null,
         "completedAtMillis":1700000000000}
    """.trimIndent()

    @Test
    fun `an old record without difficultyId decodes as normal`() {
        val quiz = json.decodeFromString(CompletedQuiz.serializer(), oldRecord(hardMode = false))

        assertEquals("normal", quiz.difficultyId)
        assertEquals(Difficulty.NORMAL, quiz.difficulty)
    }

    @Test
    fun `an old hard mode record reads as hard`() {
        val quiz = json.decodeFromString(CompletedQuiz.serializer(), oldRecord(hardMode = true))

        assertEquals(Difficulty.HARD, quiz.difficulty)
    }

    @Test
    fun `a new record round-trips its tier`() {
        for (difficulty in Difficulty.entries) {
            val quiz = json.decodeFromString(CompletedQuiz.serializer(), oldRecord(hardMode = false))
                .copy(difficultyId = difficulty.id, hardMode = difficulty == Difficulty.HARD)

            val decoded = json.decodeFromString(CompletedQuiz.serializer(), json.encodeToString(CompletedQuiz.serializer(), quiz))

            assertEquals(difficulty, decoded.difficulty)
            assertEquals(quiz, decoded)
        }
    }

    @Test
    fun `an old record without newAchievementIds decodes with none`() {
        val quiz = json.decodeFromString(CompletedQuiz.serializer(), oldRecord(hardMode = false))

        assertTrue(quiz.newAchievementIds.isEmpty())
        assertTrue(quiz.newAchievements.isEmpty())
    }

    @Test
    fun `new achievements round-trip and unknown ids are skipped`() {
        val first = Achievement.entries[0]
        val second = Achievement.entries[1]
        val quiz = json.decodeFromString(CompletedQuiz.serializer(), oldRecord(hardMode = false))
            .copy(newAchievementIds = listOf(first.id, "no_longer_exists", second.id))

        val decoded = json.decodeFromString(CompletedQuiz.serializer(), json.encodeToString(CompletedQuiz.serializer(), quiz))

        assertEquals(quiz, decoded)
        assertEquals(listOf(first, second), decoded.newAchievements)
    }
}
