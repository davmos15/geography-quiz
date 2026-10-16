package com.geoquiz.app.domain.mode.physicalfeatures

import com.geoquiz.app.domain.map.GeoBounds
import com.geoquiz.app.domain.map.GeoLayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalFeatureFilterTest {

    private fun feature(id: String, type: PhysicalFeatureType, continent: String) = PhysicalFeature(
        id = id,
        name = id,
        aliases = emptyList(),
        type = type,
        layerId = GeoLayerId.RIVERS_50M,
        neIds = listOf("1"),
        countries = emptyList(),
        continent = continent,
        bbox = GeoBounds(0.0, 0.0, 1.0, 1.0),
        tier = 1,
        wikidata = ""
    )

    private val nile = feature("nile", PhysicalFeatureType.RIVER, "Africa")
    private val amazon = feature("amazon", PhysicalFeatureType.RIVER, "South America")
    private val andes = feature("andes", PhysicalFeatureType.RANGE, "South America")
    private val everest = feature("everest", PhysicalFeatureType.PEAK, "Asia")
    private val victoria = feature("lake-victoria", PhysicalFeatureType.LAKE, "Africa")
    private val all = listOf(nile, amazon, andes, everest, victoria)

    @Test
    fun `Mountains tab matches both ranges and peaks`() {
        assertTrue(PhysicalFeatureTab.MOUNTAINS.matches(PhysicalFeatureType.RANGE))
        assertTrue(PhysicalFeatureTab.MOUNTAINS.matches(PhysicalFeatureType.PEAK))
        assertFalse(PhysicalFeatureTab.MOUNTAINS.matches(PhysicalFeatureType.RIVER))
        assertFalse(PhysicalFeatureTab.MOUNTAINS.matches(PhysicalFeatureType.LAKE))
    }

    @Test
    fun `Rivers and Lakes tabs match only their own type`() {
        assertTrue(PhysicalFeatureTab.RIVERS.matches(PhysicalFeatureType.RIVER))
        assertFalse(PhysicalFeatureTab.RIVERS.matches(PhysicalFeatureType.LAKE))
        assertTrue(PhysicalFeatureTab.LAKES.matches(PhysicalFeatureType.LAKE))
        assertFalse(PhysicalFeatureTab.LAKES.matches(PhysicalFeatureType.RIVER))
    }

    @Test
    fun `inTab returns rivers, mountains (ranges and peaks) or lakes`() {
        assertEquals(listOf(nile, amazon), all.inTab(PhysicalFeatureTab.RIVERS))
        assertEquals(listOf(andes, everest), all.inTab(PhysicalFeatureTab.MOUNTAINS))
        assertEquals(listOf(victoria), all.inTab(PhysicalFeatureTab.LAKES))
    }

    @Test
    fun `matching narrows by continent when set, otherwise every continent in the tab`() {
        assertEquals(listOf(nile, amazon), all.matching(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS)))
        assertEquals(listOf(nile), all.matching(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS, "Africa")))
        assertEquals(emptyList<PhysicalFeature>(), all.matching(PhysicalFeatureFilter(PhysicalFeatureTab.RIVERS, "Europe")))
    }

    @Test
    fun `continentsFor lists distinct continents in the tab, sorted`() {
        assertEquals(listOf("Africa", "South America"), all.continentsFor(PhysicalFeatureTab.RIVERS))
        assertEquals(listOf("Asia", "South America"), all.continentsFor(PhysicalFeatureTab.MOUNTAINS))
    }

    @Test
    fun `toQuizCountry carries the feature id as the code and continent as region`() {
        val country = nile.toQuizCountry()
        assertEquals("nile", country.code)
        assertEquals("nile", country.name)
        assertEquals("Africa", country.region)
    }
}
