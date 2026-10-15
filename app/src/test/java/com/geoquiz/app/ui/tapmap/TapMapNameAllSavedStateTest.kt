package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Difficulty
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizState
import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TapMapNameAllSavedState] (task 5.2b): save/restore for the typed "Name all" variant. */
class TapMapNameAllSavedStateTest {

    private val countries = TestQuizData.THREE // France, Germany, Austria
    private val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = countries)

    @Test
    fun `nothing saved yet has no snapshot`() {
        val savedState = TapMapNameAllSavedState(SavedStateHandle())
        assertFalse(savedState.hasSnapshot)
        assertNull(savedState.restore())
    }

    @Test
    fun `progress round-trips through save and restore`() {
        val handle = SavedStateHandle()
        val savedState = TapMapNameAllSavedState(handle)
        val state = QuizState(
            quiz = quiz,
            answeredCountries = setOf("FRA"),
            currentInput = "Germ",
            incorrectGuesses = 1,
            incorrectGuessStrings = listOf("Spain"),
            recentCorrect = listOf("FRA")
        )
        savedState.save(state, elapsedMillis = 5_000L)

        val restored = requireNotNull(savedState.restore())
        assertEquals(listOf("FRA"), restored.answeredCodes)
        assertEquals("Germ", restored.currentInput)
        assertEquals(1, restored.incorrectGuesses)
        assertEquals(listOf("Spain"), restored.incorrectGuessStrings)
        assertEquals(listOf("FRA"), restored.recentCorrect)
        assertEquals(5_000L, restored.elapsedMillis)
    }

    @Test
    fun `a code no longer in the quiz is dropped on restore`() {
        val handle = SavedStateHandle()
        val savedState = TapMapNameAllSavedState(handle)
        val smallerQuiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = listOf(TestQuizData.FRANCE))
        savedState.save(QuizState(quiz = quiz, answeredCountries = setOf("FRA", "DEU")), elapsedMillis = 0L)

        val restored = requireNotNull(savedState.restore())
        val rebuilt = restored.toQuizState(smallerQuiz)

        assertEquals(setOf("FRA"), rebuilt.answeredCountries)
    }

    @Test
    fun `an incomplete round always comes back paused`() {
        val handle = SavedStateHandle()
        val savedState = TapMapNameAllSavedState(handle)
        savedState.save(QuizState(quiz = quiz, isComplete = false), elapsedMillis = 0L)

        val rebuilt = requireNotNull(savedState.restore()).toQuizState(quiz)

        assertTrue(rebuilt.isPaused)
        assertFalse(rebuilt.isComplete)
    }

    @Test
    fun `a complete round never comes back paused`() {
        val handle = SavedStateHandle()
        val savedState = TapMapNameAllSavedState(handle)
        savedState.save(QuizState(quiz = quiz, isComplete = true), elapsedMillis = 0L)

        val rebuilt = requireNotNull(savedState.restore()).toQuizState(quiz)

        assertFalse(rebuilt.isPaused)
        assertTrue(rebuilt.isComplete)
    }

    @Test
    fun `result id and difficulty are kept on their own`() {
        val handle = SavedStateHandle()
        val savedState = TapMapNameAllSavedState(handle)

        savedState.resultId = "abc-123"
        savedState.difficulty = Difficulty.HARD

        assertEquals("abc-123", savedState.resultId)
        assertEquals(Difficulty.HARD, savedState.difficulty)
    }
}
