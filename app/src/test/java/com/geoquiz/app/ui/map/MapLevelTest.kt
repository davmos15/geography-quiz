package com.geoquiz.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLevelTest {

    @Test
    fun `auto switches to 50m at the threshold`() {
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.AUTO, 66f, true, true))
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.AUTO, DETAIL_LEVEL_DP_PER_UNIT - 0.01f, true, true))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.AUTO, DETAIL_LEVEL_DP_PER_UNIT, true, true))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.AUTO, 5000f, true, true))
    }

    @Test
    fun `a 360 dp phone switches at about 2_4x zoom on the world map`() {
        // Equal Earth spans 2 x 2.7066 units, so a 360 dp wide view fits it at 66.5 dp per unit.
        val fitDpPerUnit = 360f / (2 * 2.7066f)
        val switchZoom = DETAIL_LEVEL_DP_PER_UNIT / fitDpPerUnit
        assertEquals(2.4f, switchZoom, 0.1f)
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.AUTO, fitDpPerUnit * 2f, true, true))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.AUTO, fitDpPerUnit * 3f, true, true))
    }

    @Test
    fun `fixed preferences ignore zoom`() {
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.COARSE, 5000f, true, true))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.DETAIL, 1f, true, true))
    }

    @Test
    fun `falls back to whichever layer is loaded`() {
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.DETAIL, 1f, true, false))
        assertEquals(MapLevel.COARSE, resolveMapLevel(MapLevelPreference.AUTO, 5000f, true, false))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.COARSE, 1f, false, true))
        assertEquals(MapLevel.DETAIL, resolveMapLevel(MapLevelPreference.AUTO, 1f, false, true))
    }
}
