package com.geoquiz.app.data.service

import com.geoquiz.app.data.service.InterstitialPolicy.Companion.MIN_QUIZ_SECONDS
import com.geoquiz.app.data.service.InterstitialPolicy.Companion.QUIZZES_PER_INTERSTITIAL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialPolicyTest {

    /** One completed quiz: how long it lasted, whether an ad was then shown, the expected answer. */
    private data class Step(val seconds: Int, val expectEligible: Boolean, val adShown: Boolean = expectEligible)

    private fun run(vararg steps: Step) {
        val policy = InterstitialPolicy()
        steps.forEachIndexed { index, step ->
            assertEquals("quiz ${index + 1} (${step.seconds} s)", step.expectEligible, policy.onQuizCompleted(step.seconds))
            if (step.adShown) policy.onInterstitialShown()
        }
    }

    @Test
    fun `constants match the policy`() {
        assertEquals(3, QUIZZES_PER_INTERSTITIAL)
        assertEquals(60, MIN_QUIZ_SECONDS)
    }

    @Test
    fun `first ad after a cold start is on the third quiz`() = run(
        Step(120, false),
        Step(120, false),
        Step(120, true)
    )

    @Test
    fun `at most one ad per three quizzes`() = run(
        Step(120, false), Step(120, false), Step(120, true),
        Step(120, false), Step(120, false), Step(120, true),
        Step(120, false)
    )

    @Test
    fun `60 second boundary - 59 s never, 60 s allowed`() {
        run(Step(60, false), Step(60, false), Step(59, false), Step(60, true))
        run(Step(60, false), Step(60, false), Step(60, true))
    }

    @Test
    fun `short quizzes count towards the cap but never show an ad`() = run(
        Step(10, false), Step(10, false), Step(10, false), Step(10, false),
        Step(61, true),
        Step(61, false)
    )

    @Test
    fun `a given-up quiz counts like any other`() {
        // Giving up also completes the quiz; only its duration matters to the policy.
        val policy = InterstitialPolicy()
        policy.onQuizCompleted(5) // given up quickly
        policy.onQuizCompleted(5) // given up quickly
        assertTrue(policy.onQuizCompleted(90)) // given up after 90 s
    }

    @Test
    fun `no ad loaded keeps the next quiz eligible`() = run(
        Step(120, false), Step(120, false),
        Step(120, true, adShown = false),
        Step(120, true, adShown = false),
        Step(120, true, adShown = true),
        Step(120, false)
    )

    @Test
    fun `reset after an ad starts the count again`() {
        val policy = InterstitialPolicy()
        repeat(5) { policy.onQuizCompleted(120) }
        policy.onInterstitialShown()
        assertFalse(policy.onQuizCompleted(120))
        assertFalse(policy.onQuizCompleted(120))
        assertTrue(policy.onQuizCompleted(120))
    }
}
