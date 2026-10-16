package com.geoquiz.app.domain.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboTrackerTest {

    @Test
    fun `the first correct answer starts a combo of 1`() {
        val tracker = ComboTracker()

        assertEquals(1, tracker.onCorrect(0L))
        assertEquals(1, tracker.count)
    }

    @Test
    fun `a second correct answer within the fast window extends the combo`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)

        assertEquals(2, tracker.onCorrect(4_000L))
    }

    @Test
    fun `a correct answer right at the window edge still extends the combo`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)

        assertEquals(2, tracker.onCorrect(ComboTracker.FAST_ANSWER_WINDOW_MILLIS))
    }

    @Test
    fun `a correct answer just past the window restarts the combo at 1`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)

        assertEquals(1, tracker.onCorrect(ComboTracker.FAST_ANSWER_WINDOW_MILLIS + 1))
    }

    @Test
    fun `several fast correct answers in a row keep extending the combo`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)
        tracker.onCorrect(1_000L)
        tracker.onCorrect(2_000L)

        assertEquals(4, tracker.onCorrect(3_000L))
    }

    @Test
    fun `a wrong answer resets the combo to zero`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)
        tracker.onCorrect(1_000L)

        tracker.onIncorrect()

        assertEquals(0, tracker.count)
    }

    @Test
    fun `after a reset the next correct answer starts a fresh combo of 1, even if fast`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)
        tracker.onIncorrect()

        assertEquals(1, tracker.onCorrect(500L))
    }

    @Test
    fun `a lone correct answer is below the visible threshold`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)

        assertTrue(tracker.count < ComboTracker.VISIBLE_THRESHOLD)
    }

    @Test
    fun `two fast correct answers reach the visible threshold`() {
        val tracker = ComboTracker()
        tracker.onCorrect(0L)
        tracker.onCorrect(1_000L)

        assertTrue(tracker.count >= ComboTracker.VISIBLE_THRESHOLD)
    }
}
