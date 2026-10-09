package com.geoquiz.app.ui.quiz

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the quiz shares its height (3.7). */
class QuizLayoutTest {

    @Test
    fun `a tall screen at normal text pins the title and shows recent answers`() {
        assertEquals(QuizSpace(titleScrollsWithList = false, showRecentAnswers = true), quizSpace(800.dp, 1f))
        assertEquals(QuizSpace(titleScrollsWithList = false, showRecentAnswers = true), quizSpace(560.dp, 1f))
    }

    @Test
    fun `with the keyboard open on a phone the title scrolls with the list`() {
        // About 891 dp tall less the bars and a keyboard.
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = true), quizSpace(480.dp, 1f))
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = true), quizSpace(559.dp, 1f))
    }

    @Test
    fun `large text counts as less space`() {
        // 800 dp at 200% is like 400 dp at 100%.
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = true), quizSpace(800.dp, 2f))
        // 340 dp at 200% (keyboard open on a short phone) leaves out the recent answers.
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = false), quizSpace(340.dp, 2f))
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = true), quizSpace(600.dp, 2f))
    }

    @Test
    fun `very short space at normal text leaves out the recent answers`() {
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = false), quizSpace(299.dp, 1f))
        assertEquals(QuizSpace(titleScrollsWithList = true, showRecentAnswers = true), quizSpace(300.dp, 1f))
    }

    @Test
    fun `text smaller than normal is treated as normal`() {
        assertEquals(quizSpace(500.dp, 1f), quizSpace(500.dp, 0.85f))
    }
}
