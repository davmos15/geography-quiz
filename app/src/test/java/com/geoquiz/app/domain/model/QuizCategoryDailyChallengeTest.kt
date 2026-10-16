package com.geoquiz.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily challenge (task 8.1): the DailyChallenge category and its route round trip. */
class QuizCategoryDailyChallengeTest {

    @Test
    fun `daily challenge route round trips`() {
        val daily = QuizCategory.DailyChallenge("2026-10-16")

        assertEquals("daily", daily.typeKey)
        assertEquals("2026-10-16", daily.valueKey)
        assertEquals(daily, QuizCategory.fromRoute(daily.typeKey, daily.valueKey))
    }

    @Test
    fun `a malformed date falls back like any bad route`() {
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("daily", ""))
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("daily", "not-a-date"))
        assertEquals(QuizCategory.AllCountries, QuizCategory.fromRoute("daily", "2026-1-1"))
    }

    @Test
    fun `strict parse never returns a daily challenge category`() {
        assertNull(QuizCategory.fromRouteOrNull("daily", "2026-10-16"))
    }

    @Test
    fun `daily challenge is offered in no classic mode but is recorded`() {
        val daily = QuizCategory.DailyChallenge("2026-10-16")

        QuizMode.entries.forEach { assertFalse("offered in $it", daily.isOfferedIn(it)) }
        assertTrue(daily.isRecorded)
        assertFalse(daily.isFlagCategory)
        assertTrue(daily.displayName.isNotBlank())
    }
}
