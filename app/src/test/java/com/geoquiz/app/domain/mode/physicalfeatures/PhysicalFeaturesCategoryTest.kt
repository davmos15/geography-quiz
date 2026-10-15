package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.model.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PhysicalFeaturesCategoryTest {

    @Test
    fun `a tab with no continent round-trips through the label`() {
        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS))
        assertEquals("Rivers", category.label)
        assertEquals("fixed_region", category.typeKey)
        assertEquals("Rivers", category.valueKey)

        val filter = PhysicalFeaturesCategory.filterFromLabel(category.label)
        assertEquals(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS), filter)
    }

    @Test
    fun `a tab with a continent round-trips through the label`() {
        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(PhysicalFeatureTab.MOUNTAINS, "Asia"))
        assertEquals("Mountains - Asia", category.label)

        val filter = PhysicalFeaturesCategory.filterFromLabel(category.label)
        assertEquals(PhysicalFeatureFilter(PhysicalFeatureTab.MOUNTAINS, "Asia"), filter)
    }

    @Test
    fun `every tab round-trips`() {
        PhysicalFeatureTab.entries.forEach { tab ->
            val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(tab))
            assertEquals(tab, PhysicalFeaturesCategory.filterFromLabel(category.label).tab)
        }
    }

    @Test
    fun `an unknown or missing label falls back to Rivers with no continent`() {
        assertEquals(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS), PhysicalFeaturesCategory.filterFromLabel(null))
        assertEquals(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS), PhysicalFeaturesCategory.filterFromLabel("Nonsense"))
    }

    @Test
    fun `this category is never offered in a classic mode, same reasoning as FixedRegion`() {
        val category = PhysicalFeaturesCategory.toQuizCategory(PhysicalFeatureFilter(PhysicalFeatureTab.LAKES))
        assertFalse(category.isOfferedIn(QuizMode.COUNTRIES))
    }
}
