package com.geoquiz.app.ui.quiz

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.AnswerResult
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSavedStateTest {

    private val quiz = Quiz(category = QuizCategory.AllCountries, countries = TestQuizData.THREE)

    private val running = QuizState(
        quiz = quiz,
        answeredCountries = setOf("FRA", "AUT"),
        currentInput = "Germ",
        lastAnswerResult = AnswerResult.Incorrect,
        incorrectGuesses = 2,
        incorrectGuessStrings = listOf("Narnia", "Atlantis")
    )

    /** Simulates process death: only what the handle saved survives, in a new handle. */
    private fun recreate(handle: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    @Test
    fun `nothing saved restores nothing`() {
        val saved = QuizSavedState(SavedStateHandle())

        assertFalse(saved.hasSnapshot)
        assertNull(saved.restore())
        assertNull(saved.resultId)
    }

    @Test
    fun `progress round-trips through the handle`() {
        val handle = SavedStateHandle()
        QuizSavedState(handle).save(running, elapsedMillis = 12_345L)

        val snapshot = QuizSavedState(recreate(handle)).restore()!!

        assertEquals(setOf("FRA", "AUT"), snapshot.answeredCodes.toSet())
        assertEquals(2, snapshot.incorrectGuesses)
        assertEquals(listOf("Narnia", "Atlantis"), snapshot.incorrectGuessStrings)
        assertEquals("Germ", snapshot.currentInput)
        assertFalse(snapshot.isComplete)
        assertEquals(12_345L, snapshot.elapsedMillis)
    }

    @Test
    fun `a restored unfinished quiz comes back paused with its progress`() {
        val handle = SavedStateHandle()
        QuizSavedState(handle).save(running, elapsedMillis = 1_000L)

        val restored = QuizSavedState(recreate(handle)).restore()!!.toQuizState(quiz)

        assertTrue(restored.isPaused)
        assertFalse(restored.isComplete)
        assertEquals(setOf("FRA", "AUT"), restored.answeredCountries)
        assertEquals("Germ", restored.currentInput)
        assertEquals(2, restored.incorrectGuesses)
        assertEquals(listOf("Narnia", "Atlantis"), restored.incorrectGuessStrings)
        assertEquals(quiz, restored.quiz)
        // Answer feedback is not restored.
        assertEquals(AnswerResult.None, restored.lastAnswerResult)
    }

    @Test
    fun `a restored finished quiz stays complete and is not paused`() {
        val handle = SavedStateHandle()
        QuizSavedState(handle).save(running.copy(isComplete = true), elapsedMillis = 1_000L)

        val restored = QuizSavedState(recreate(handle)).restore()!!.toQuizState(quiz)

        assertTrue(restored.isComplete)
        assertFalse(restored.isPaused)
    }

    @Test
    fun `codes that are not in the rebuilt quiz are dropped`() {
        val handle = SavedStateHandle()
        QuizSavedState(handle).save(running.copy(answeredCountries = setOf("FRA", "PER")), 0L)

        val restored = QuizSavedState(handle).restore()!!.toQuizState(quiz)

        assertEquals(setOf("FRA"), restored.answeredCountries)
    }

    @Test
    fun `result id survives recreation`() {
        val handle = SavedStateHandle()
        QuizSavedState(handle).resultId = "result-1"

        assertEquals("result-1", QuizSavedState(recreate(handle)).resultId)
    }
}
