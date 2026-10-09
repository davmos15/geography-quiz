package com.geoquiz.app.ui.quiz.feedback

import android.view.HapticFeedbackConstants
import android.view.View
import com.geoquiz.app.domain.model.AnswerResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HapticFeedbackPlayerTest {

    @Test
    fun `API 30 and later use confirm and reject`() {
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstantFor(AnswerFeedbackEvent.CORRECT, 30))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstantFor(AnswerFeedbackEvent.INCORRECT, 36))
    }

    @Test
    fun `API 26 to 29 fall back to two different constants`() {
        val correct = hapticConstantFor(AnswerFeedbackEvent.CORRECT, 26)
        val wrong = hapticConstantFor(AnswerFeedbackEvent.INCORRECT, 29)
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, correct)
        assertEquals(HapticFeedbackConstants.LONG_PRESS, wrong)
        assertNotEquals(correct, wrong)
    }

    @Test
    fun `near miss and already answered have no haptic`() {
        for (sdk in listOf(26, 30, 36)) {
            assertNull(hapticConstantFor(AnswerFeedbackEvent.NEAR_MISS, sdk))
            assertNull(hapticConstantFor(AnswerFeedbackEvent.ALREADY_ANSWERED, sdk))
        }
    }

    @Test
    fun `the view player respects the system setting by passing no flags`() {
        val view = mockk<View> { every { performHapticFeedback(any()) } returns true }
        val player = ViewHapticFeedbackPlayer(view, sdkInt = 34)

        player.play(AnswerFeedbackEvent.CORRECT)
        player.play(AnswerFeedbackEvent.INCORRECT)
        player.play(AnswerFeedbackEvent.NEAR_MISS)

        verify(exactly = 1) { view.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
        verify(exactly = 1) { view.performHapticFeedback(HapticFeedbackConstants.REJECT) }
        verify(exactly = 2) { view.performHapticFeedback(any()) }
        verify(exactly = 0) { view.performHapticFeedback(any(), any()) }
    }

    @Test
    fun `events map from answer results`() {
        assertEquals(AnswerFeedbackEvent.CORRECT, AnswerFeedbackEvent.from(AnswerResult.Correct("France")))
        assertEquals(AnswerFeedbackEvent.CORRECT, AnswerFeedbackEvent.from(AnswerResult.Correct("France", viaTypo = true)))
        assertEquals(AnswerFeedbackEvent.INCORRECT, AnswerFeedbackEvent.from(AnswerResult.Incorrect))
        assertEquals(AnswerFeedbackEvent.NEAR_MISS, AnswerFeedbackEvent.from(AnswerResult.NearMiss))
        assertEquals(AnswerFeedbackEvent.ALREADY_ANSWERED, AnswerFeedbackEvent.from(AnswerResult.AlreadyAnswered))
        assertNull(AnswerFeedbackEvent.from(AnswerResult.None))
    }

    @Test
    fun `each signal has a new sequence, even for the same event`() {
        val first = FeedbackSignal().next(AnswerFeedbackEvent.INCORRECT)
        val second = first.next(AnswerFeedbackEvent.INCORRECT)
        assertEquals(AnswerFeedbackEvent.INCORRECT, second.event)
        assertNotEquals(first, second)
    }
}
