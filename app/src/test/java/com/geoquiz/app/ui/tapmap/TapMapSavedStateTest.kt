package com.geoquiz.app.ui.tapmap

import androidx.lifecycle.SavedStateHandle
import com.geoquiz.app.domain.model.Quiz
import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.testutil.TestQuizData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TapMapSavedState.Snapshot.toGameState] (S2): restoring never offers a second answer on a
 * prompt that was already resolved (answered, missed, or past the Normal reveal limit) before
 * the snapshot was taken.
 */
class TapMapSavedStateTest {

    private val countries = TestQuizData.THREE // France, Germany, Austria
    private val quiz = Quiz(category = QuizCategory.MapRegion("europe"), countries = countries)

    private fun snapshot(
        answeredCodes: List<String> = emptyList(),
        missedCodes: List<String> = emptyList(),
        remainingOrder: List<String> = emptyList(),
        currentTargetCode: String? = null,
        choiceTarget: String? = null,
        choiceOptionCodes: List<String> = emptyList(),
        choiceOptionLabels: List<String> = emptyList(),
        wrongTapsThisPrompt: Int = 0,
        hintUsedThisPrompt: Boolean = false,
        isComplete: Boolean = false
    ) = TapMapSavedState.Snapshot(
        answeredCodes = answeredCodes,
        missedCodes = missedCodes,
        remainingOrder = remainingOrder,
        currentTargetCode = currentTargetCode,
        choiceTarget = choiceTarget,
        choiceOptionCodes = choiceOptionCodes,
        choiceOptionLabels = choiceOptionLabels,
        incorrectGuesses = 0,
        incorrectGuessStrings = emptyList(),
        hintsUsed = emptyList(),
        wrongTapsThisPrompt = wrongTapsThisPrompt,
        hintUsedThisPrompt = hintUsedThisPrompt,
        isComplete = isComplete,
        elapsedMillis = 0L
    )

    @Test
    fun `a normal tap target still outstanding is restored as is`() {
        val state = snapshot(currentTargetCode = "DEU", remainingOrder = listOf("AUT")).toGameState(quiz)

        assertEquals("DEU", state.currentTargetCode)
        assertFalse("DEU" in state.quizState.missedCountries)
    }

    @Test
    fun `a normal tap target already answered is dropped so loadQuiz advances past it`() {
        // Found right before the process died, but the snapshot was taken before the prompt moved on.
        val state = snapshot(
            answeredCodes = listOf("FRA"),
            currentTargetCode = "FRA",
            remainingOrder = listOf("DEU", "AUT")
        ).toGameState(quiz)

        assertNull(state.currentTargetCode)
        assertNull(state.targetCode)
    }

    @Test
    fun `a normal tap target already missed is dropped`() {
        val state = snapshot(
            missedCodes = listOf("FRA"),
            currentTargetCode = "FRA",
            remainingOrder = listOf("DEU", "AUT")
        ).toGameState(quiz)

        assertNull(state.currentTargetCode)
    }

    @Test
    fun `a wrong-tap streak already past the reveal limit is marked missed and dropped`() {
        val state = snapshot(
            currentTargetCode = "FRA",
            remainingOrder = listOf("DEU", "AUT"),
            wrongTapsThisPrompt = TapMapGameViewModel.NORMAL_REVEAL_LIMIT
        ).toGameState(quiz)

        assertNull(state.currentTargetCode)
        assertTrue("FRA" in state.quizState.missedCountries)
        // Nothing left over for the next prompt to stumble on.
        assertEquals(0, state.wrongTapsThisPrompt)
        assertFalse(state.hintUsedThisPrompt)
    }

    @Test
    fun `a wrong-tap streak short of the reveal limit is restored as is`() {
        val state = snapshot(
            currentTargetCode = "FRA",
            remainingOrder = listOf("DEU", "AUT"),
            wrongTapsThisPrompt = TapMapGameViewModel.NORMAL_REVEAL_LIMIT - 1
        ).toGameState(quiz)

        assertEquals("FRA", state.currentTargetCode)
        assertFalse("FRA" in state.quizState.missedCountries)
        assertEquals(TapMapGameViewModel.NORMAL_REVEAL_LIMIT - 1, state.wrongTapsThisPrompt)
    }

    @Test
    fun `an easy choice whose target is already answered is dropped`() {
        val state = snapshot(
            answeredCodes = listOf("FRA"),
            choiceTarget = "FRA",
            choiceOptionCodes = listOf("FRA", "DEU", "AUT"),
            choiceOptionLabels = listOf("France", "Germany", "Austria"),
            remainingOrder = listOf("DEU", "AUT")
        ).toGameState(quiz)

        assertNull(state.quizState.choice)
        assertNull(state.targetCode)
    }

    @Test
    fun `an easy choice whose target is already missed is dropped`() {
        val state = snapshot(
            missedCodes = listOf("FRA"),
            choiceTarget = "FRA",
            choiceOptionCodes = listOf("FRA", "DEU", "AUT"),
            choiceOptionLabels = listOf("France", "Germany", "Austria"),
            remainingOrder = listOf("DEU", "AUT")
        ).toGameState(quiz)

        assertNull(state.quizState.choice)
    }

    @Test
    fun `an easy choice still outstanding is restored as is`() {
        val state = snapshot(
            choiceTarget = "FRA",
            choiceOptionCodes = listOf("FRA", "DEU", "AUT"),
            choiceOptionLabels = listOf("France", "Germany", "Austria"),
            remainingOrder = listOf("DEU", "AUT")
        ).toGameState(quiz)

        assertEquals("FRA", state.quizState.choice?.targetCode)
        assertEquals("FRA", state.targetCode)
    }

    @Test
    fun `an incomplete round always comes back paused`() {
        val state = snapshot(currentTargetCode = "FRA", isComplete = false).toGameState(quiz)

        assertTrue(state.quizState.isPaused)
    }

    @Test
    fun `a complete round never comes back paused`() {
        val state = snapshot(isComplete = true).toGameState(quiz)

        assertFalse(state.quizState.isPaused)
    }

    @Test
    fun `restore from a saved state handle round-trips through save`() {
        val vm = TapMapSavedState(SavedStateHandle())
        assertFalse(vm.hasSnapshot)
        assertNull(vm.restore())
    }
}
