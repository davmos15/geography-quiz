package com.geoquiz.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReducedMotionTest {

    @Test
    fun `normal animation settings allow motion`() {
        assertFalse(shouldReduceMotion(animatorDurationScale = 1f, animatorsEnabled = true))
        assertFalse(shouldReduceMotion(animatorDurationScale = 0.5f, animatorsEnabled = true))
        assertFalse(shouldReduceMotion(animatorDurationScale = 10f, animatorsEnabled = true))
    }

    @Test
    fun `animator duration scale off reduces motion`() {
        assertTrue(shouldReduceMotion(animatorDurationScale = 0f, animatorsEnabled = true))
    }

    @Test
    fun `disabled animators reduce motion`() {
        assertTrue(shouldReduceMotion(animatorDurationScale = 1f, animatorsEnabled = false))
    }
}
