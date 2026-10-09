package com.geoquiz.app.ui.achievements

import com.geoquiz.app.domain.model.AchievementTier
import com.geoquiz.app.ui.theme.DarkGeoColors
import com.geoquiz.app.ui.theme.LightGeoColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TierCardColoursTest {

    @Test
    fun eachTier_readsItsOwnTokens() {
        for (geo in listOf(LightGeoColors, DarkGeoColors)) {
            assertEquals(
                TierCardColours(geo.tierGoldContainer, geo.tierGold, geo.onTierGoldContainer),
                geo.tierCardColours(AchievementTier.GOLD)
            )
            assertEquals(
                TierCardColours(geo.tierSilverContainer, geo.tierSilver, geo.onTierSilverContainer),
                geo.tierCardColours(AchievementTier.SILVER)
            )
            assertEquals(
                TierCardColours(geo.tierBronzeContainer, geo.tierBronze, geo.onTierBronzeContainer),
                geo.tierCardColours(AchievementTier.BRONZE)
            )
        }
    }

    @Test
    fun tiers_lookDifferentFromEachOther_andBetweenThemes() {
        for (tier in AchievementTier.entries) {
            assertNotEquals(
                LightGeoColors.tierCardColours(tier).container,
                DarkGeoColors.tierCardColours(tier).container
            )
        }
        for (geo in listOf(LightGeoColors, DarkGeoColors)) {
            val containers = AchievementTier.entries.map { geo.tierCardColours(it).container }
            assertEquals(containers.size, containers.toSet().size)
        }
    }
}
