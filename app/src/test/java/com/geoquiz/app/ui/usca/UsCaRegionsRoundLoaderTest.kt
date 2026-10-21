package com.geoquiz.app.ui.usca

import com.geoquiz.app.domain.map.GeoLayerId
import com.geoquiz.app.domain.map.MapRepository
import com.geoquiz.app.domain.mode.usca.UsCaRegionGroup
import com.geoquiz.app.domain.mode.usca.UsCaRegionsCategory
import com.geoquiz.app.domain.model.QuizCategory
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [loadUsCaRegionsRound] (task 6.2; practice-group inference reworked P10-D2 to read every
 * group, not just a CAN-else-US guess).
 */
class UsCaRegionsRoundLoaderTest {

    private val mapRepository = mockk<MapRepository> {
        coEvery { layer(GeoLayerId.ADMIN1_50M) } returns FAKE_ADMIN1_LAYER
    }

    @Test
    fun `a normal US round resolves the US group from the label`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, "region", UsCaRegionsCategory.LABEL_US_NO_DC)
        assertEquals(UsCaRegionGroup.US_STATES, round.group)
        assertEquals(setOf("US-CA", "US-NY", "US-TX"), round.items.map { it.code }.toSet())
        assertTrue(!round.isPractice)
    }

    @Test
    fun `a normal Canadian round resolves the Canadian group from the label`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, "region", UsCaRegionsCategory.LABEL_CA)
        assertEquals(UsCaRegionGroup.CANADIAN_PROVINCES, round.group)
        assertEquals(setOf("CA-ON"), round.items.map { it.code }.toSet())
    }

    @Test
    fun `a normal Australian round resolves the Australian group from the label`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, "region", UsCaRegionsCategory.LABEL_AU)
        assertEquals(UsCaRegionGroup.AU_STATES, round.group)
        assertEquals(setOf("AU-NSW", "AU-ACT"), round.items.map { it.code }.toSet())
    }

    @Test
    fun `a practice round of US codes infers the US group from the features' own country property`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, QuizCategory.Practice.TYPE_KEY, "US-CA+US-NY")
        assertEquals(UsCaRegionGroup.US_STATES, round.group)
        assertTrue(round.isPractice)
        assertEquals(setOf("US-CA", "US-NY"), round.items.map { it.code }.toSet())
    }

    @Test
    fun `a practice round of Canadian codes infers the Canadian group from the features' own country property`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, QuizCategory.Practice.TYPE_KEY, "CA-ON")
        assertEquals(UsCaRegionGroup.CANADIAN_PROVINCES, round.group)
    }

    /**
     * P10-D2 regression: the old inference only checked for "CAN", so an Australian practice
     * round (country "AUS") silently fell through to the US-states else-branch. It must now be
     * recognised explicitly.
     */
    @Test
    fun `a practice round of Australian codes infers the Australian group from the features' own country property`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, QuizCategory.Practice.TYPE_KEY, "AU-NSW+AU-ACT")
        assertEquals(UsCaRegionGroup.AU_STATES, round.group)
        assertEquals(setOf("AU-NSW", "AU-ACT"), round.items.map { it.code }.toSet())
    }

    @Test
    fun `a practice round whose codes no longer resolve is empty, not a crash`() = runTest {
        val round = loadUsCaRegionsRound(mapRepository, QuizCategory.Practice.TYPE_KEY, "US-ZZ")
        assertTrue(round.items.isEmpty())
    }
}
