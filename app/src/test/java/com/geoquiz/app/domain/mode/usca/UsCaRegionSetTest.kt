package com.geoquiz.app.domain.mode.usca

import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * US states + DC and Canadian provinces and territories (task 6.2), against the real bundled
 * `admin1_50m.bin` (pure JVM, same pattern as `ui/map/RegionPresetTest.kt`).
 */
class UsCaRegionSetTest {

    private val layer: GeoLayer by lazy {
        GeoLayerReader.read(File("src/main/assets/geo", GeoLayerId.ADMIN1_50M.fileName).readBytes())
    }

    @Test
    fun `US states without DC are exactly 50`() {
        val members = UsCaRegionSet.members(layer, UsCaRegionGroup.US_STATES, includeDc = false)
        assertEquals(50, members.size)
        assertTrue(members.none { it.id == UsCaRegionSet.DC_CODE })
        assertTrue(members.all { it.properties.stringOrNull("country") == "USA" })
    }

    @Test
    fun `US states with DC are exactly 51`() {
        val members = UsCaRegionSet.members(layer, UsCaRegionGroup.US_STATES, includeDc = true)
        assertEquals(51, members.size)
        assertTrue(members.any { it.id == UsCaRegionSet.DC_CODE })
    }

    @Test
    fun `Canadian provinces and territories are exactly 13, DC flag has no effect`() {
        val withDc = UsCaRegionSet.members(layer, UsCaRegionGroup.CANADIAN_PROVINCES, includeDc = true)
        val withoutDc = UsCaRegionSet.members(layer, UsCaRegionGroup.CANADIAN_PROVINCES, includeDc = false)
        assertEquals(13, withDc.size)
        assertEquals(withDc.map { it.id }, withoutDc.map { it.id })
        assertTrue(withDc.all { it.properties.stringOrNull("country") == "CAN" })
    }

    @Test
    fun `members are sorted by English name`() {
        val members = UsCaRegionSet.members(layer, UsCaRegionGroup.US_STATES, includeDc = false)
        assertEquals(members.map { it.regionName() }, members.map { it.regionName() }.sorted())
    }

    @Test
    fun `California has the expected id, name and postal code`() {
        val california = layer.feature("US-CA")!!
        assertEquals("California", california.regionName())
        assertEquals("CA", california.postalCode())
    }

    @Test
    fun `Ontario has the expected id, name and postal code`() {
        val ontario = layer.feature("CA-ON")!!
        assertEquals("Ontario", ontario.regionName())
        assertEquals("ON", ontario.postalCode())
    }

    @Test
    fun `toRegionCountry carries the feature id as the code and the English name`() {
        val feature = layer.feature("US-CA")!!
        val country = feature.toRegionCountry()
        assertEquals("US-CA", country.code)
        assertEquals("California", country.name)
        assertEquals("California", country.officialName)
    }

    @Test
    fun `UsCaRegionGroup fromRouteOrNull`() {
        assertEquals(UsCaRegionGroup.US_STATES, UsCaRegionGroup.fromRouteOrNull("us"))
        assertEquals(UsCaRegionGroup.CANADIAN_PROVINCES, UsCaRegionGroup.fromRouteOrNull("ca"))
        assertFalse(UsCaRegionGroup.fromRouteOrNull("xx") != null)
    }
}
