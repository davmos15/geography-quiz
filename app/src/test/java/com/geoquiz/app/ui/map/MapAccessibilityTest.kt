package com.geoquiz.app.ui.map

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.geoquiz.app.domain.map.MapFeatureState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [mapContentDescription] built from the real strings.xml (English), task 4.6. */
@RunWith(RobolectricTestRunner::class)
class MapAccessibilityTest {

    private lateinit var res: Resources

    @Before
    fun setUp() {
        res = ApplicationProvider.getApplicationContext<Context>().resources
    }

    @Test
    fun `names the region and the found count, following the total for plurals`() {
        val states = mapOf("FRA" to MapFeatureState.Found, "DEU" to MapFeatureState.Found)
        assertEquals(
            "Map of Europe. 2 of 46 countries found.",
            mapContentDescription(res, "Europe", states, total = 46)
        )
    }

    @Test
    fun `singular country and wrong count`() {
        val states = mapOf("FRA" to MapFeatureState.Found, "DEU" to MapFeatureState.Wrong)
        assertEquals(
            "Map of Oceania. 1 of 1 country found, 1 wrong.",
            mapContentDescription(res, "Oceania", states, total = 1)
        )
    }

    @Test
    fun `zero found and zero wrong leaves out the wrong clause`() {
        assertEquals(
            "Map of the world. 0 of 197 countries found.",
            mapContentDescription(res, "the world", emptyMap(), total = 197)
        )
    }

    @Test
    fun `highlighted, start and end are not counted as found or wrong`() {
        val states = mapOf(
            "FRA" to MapFeatureState.Highlighted,
            "DEU" to MapFeatureState.Start,
            "ESP" to MapFeatureState.End,
        )
        assertEquals(
            "Map of Europe. 0 of 46 countries found.",
            mapContentDescription(res, "Europe", states, total = 46)
        )
    }

    @Test
    fun `several wrong, plural wrong clause`() {
        val states = mapOf("FRA" to MapFeatureState.Wrong, "DEU" to MapFeatureState.Wrong)
        assertEquals(
            "Map of Europe. 0 of 46 countries found, 2 wrong.",
            mapContentDescription(res, "Europe", states, total = 46)
        )
    }
}
