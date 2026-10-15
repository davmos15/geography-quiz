package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.domain.model.QuizCategory
import com.geoquiz.app.domain.model.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsCaRegionsCategoryTest {

    @Test
    fun `US states without DC round-trips through typeKey and valueKey`() {
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = false)
        assertEquals("fixed_region", category.typeKey)
        assertEquals(UsCaRegionsCategory.LABEL_US_NO_DC, category.valueKey)
        assertEquals(UsCaRegionsCategory.LABEL_US_NO_DC, category.displayName)
        assertEquals(
            UsCaRegionGroup.US_STATES to false,
            UsCaRegionsCategory.groupAndDcFromLabel(category.valueKey)
        )
    }

    @Test
    fun `US states with DC round-trips`() {
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = true)
        assertEquals(UsCaRegionsCategory.LABEL_US_WITH_DC, category.valueKey)
        assertEquals(
            UsCaRegionGroup.US_STATES to true,
            UsCaRegionsCategory.groupAndDcFromLabel(category.valueKey)
        )
    }

    @Test
    fun `Canadian provinces round-trips regardless of the DC flag passed in`() {
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.CANADIAN_PROVINCES, includeDc = true)
        assertEquals(UsCaRegionsCategory.LABEL_CA, category.valueKey)
        assertEquals(
            UsCaRegionGroup.CANADIAN_PROVINCES to false,
            UsCaRegionsCategory.groupAndDcFromLabel(category.valueKey)
        )
    }

    @Test
    fun `an unknown label falls back to US states without DC, like other category route parsing`() {
        assertEquals(UsCaRegionGroup.US_STATES to false, UsCaRegionsCategory.groupAndDcFromLabel("nonsense"))
        assertEquals(UsCaRegionGroup.US_STATES to false, UsCaRegionsCategory.groupAndDcFromLabel(null))
    }

    @Test
    fun `category is a QuizCategory FixedRegion, never offered in a classic mode`() {
        val category = UsCaRegionsCategory.toQuizCategory(UsCaRegionGroup.US_STATES, includeDc = false)
        assertTrue(category is QuizCategory.FixedRegion)
        // Code review (task 6.2): an earlier version used ByRegion, which IS offered in a classic
        // mode by default and would have let a fake "US states" region label leak into other
        // modes' offers() checks, AchievementRepository's region tracking and challenge links.
        assertFalse(category.isOfferedIn(QuizMode.COUNTRIES))
        assertFalse(category.isOfferedIn(QuizMode.CAPITALS))
        assertFalse(category.isOfferedIn(QuizMode.FLAGS))
    }

    @Test
    fun `a challenge link can never carry a fixed_region category`() {
        assertEquals(
            null,
            QuizCategory.fromRouteOrNull("fixed_region", UsCaRegionsCategory.LABEL_US_NO_DC)
        )
    }
}
