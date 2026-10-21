package com.geoquiz.app.ui.usca

import com.geoquiz.app.data.geo.GeoLayerReader
import com.geoquiz.app.domain.map.GeoLayer
import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionSet
import com.geoquiz.app.domain.mode.usca.usCaFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [projectionForGroup] and [usCaFrame] against the real bundled `admin1_50m.bin` (pure JVM, same
 * pattern as `UsCaRegionSetTest`). P10-D2: Australia gets its own projection, distinct from
 * [US_CA_PROJECTION], and that projection's frame must fit every one of its 8 members, including
 * the tiny Australian Capital Territory.
 */
class UsCaRegionsMapStateTest {

    private val layer: GeoLayer by lazy {
        GeoLayerReader.read(File("src/main/assets/geo", GeoLayerId.ADMIN1_50M.fileName).readBytes())
    }

    @Test
    fun `projectionForGroup gives US and Canada the shared North America projection, Australia its own`() {
        assertEquals(US_CA_PROJECTION, projectionForGroup(UsCaRegionGroup.US_STATES))
        assertEquals(US_CA_PROJECTION, projectionForGroup(UsCaRegionGroup.CANADIAN_PROVINCES))
        assertEquals(AU_STATES_PROJECTION, projectionForGroup(UsCaRegionGroup.AU_STATES))
        assertTrue(AU_STATES_PROJECTION != US_CA_PROJECTION)
    }

    @Test
    fun `every Australian state and territory is visible in its own projection`() {
        val members = UsCaRegionSet.members(layer, UsCaRegionGroup.AU_STATES, includeDc = false)
        assertEquals(8, members.size)
        for (member in members) {
            for (part in member.parts) {
                for (i in 0 until part.size) {
                    assertTrue(
                        "${member.id} vertex $i should be visible in the Australian projection",
                        AU_STATES_PROJECTION.isVisible(part.lon[i].toDouble(), part.lat[i].toDouble())
                    )
                }
            }
        }
    }

    @Test
    fun `the Australian frame contains every one of the 8 members, including tiny ACT`() {
        val members = UsCaRegionSet.members(layer, UsCaRegionGroup.AU_STATES, includeDc = false)
        val codes = members.map { it.id }.toSet()
        assertTrue(codes.contains("AU-ACT"))

        val frame = usCaFrame(AU_STATES_PROJECTION, layer, codes)
        requireNotNull(frame)

        val scratch = DoubleArray(2)
        for (code in codes) {
            val feature = layer.feature(code)!!
            for (part in feature.parts) {
                for (i in 0 until part.size) {
                    val lon = part.lon[i].toDouble()
                    val lat = part.lat[i].toDouble()
                    if (!AU_STATES_PROJECTION.isVisible(lon, lat)) continue
                    AU_STATES_PROJECTION.project(lon, lat, scratch)
                    assertTrue(
                        "$code vertex $i at (${scratch[0]}, ${scratch[1]}) should sit inside the frame",
                        frame.contains(scratch[0].toFloat(), scratch[1].toFloat())
                    )
                }
            }
        }
    }

    @Test
    fun `US and Canadian frames still use the North America projection`() {
        val usMembers = UsCaRegionSet.members(layer, UsCaRegionGroup.US_STATES, includeDc = true)
        val frame = usCaFrame(US_CA_PROJECTION, layer, usMembers.map { it.id }.toSet())
        requireNotNull(frame)
        assertTrue(frame.width > 0f && frame.height > 0f)
    }
}
