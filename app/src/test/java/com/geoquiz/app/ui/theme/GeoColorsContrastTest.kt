package com.geoquiz.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast checks for the semantic tokens in [GeoColors], in both themes.
 *
 * - Text colours (correct, wrong, near miss) need 4.5:1 on every surface they are drawn on.
 * - Each on-colour needs 4.5:1 on its fill.
 * - Map state colours are non-text graphics and need 3:1 against land (WCAG 1.4.11); borders
 *   3:1 on land; land and water 1.5:1 (a large area difference, coastlines add the border).
 * - The flag hairline needs 2:1 on every surface a flag sits on and on the flag edge most likely
 *   to merge with it.
 * - Achievement tier accents (icon + tier label text) 4.5:1, tier text 7:1 on the tier container.
 * - The colour-scheme roles we set keep their on-colours at 4.5:1 (dark mode audit, 3.8).
 */
class GeoColorsContrastTest {

    private data class Theme(val name: String, val scheme: ColorScheme, val geo: GeoColors)

    private val themes = listOf(
        Theme("light", LightColorScheme, LightGeoColors),
        Theme("dark", DarkColorScheme, DarkGeoColors),
    )

    @Test
    fun correctAndWrongText_meetAA_onEverySurfaceTheyAppearOn() {
        for (t in themes) {
            // Results / challenge cards put outcome text on the containers as well.
            val surfaces = mapOf(
                "background" to t.scheme.background,
                "surface" to t.scheme.surface,
                "surfaceVariant" to t.scheme.surfaceVariant,
                "primaryContainer" to t.scheme.primaryContainer,
                "tertiaryContainer" to t.scheme.tertiaryContainer,
            )
            for ((surfaceName, surface) in surfaces) {
                assertContrast("${t.name} correct on $surfaceName", t.geo.correct, surface, 4.5)
                assertContrast("${t.name} wrong on $surfaceName", t.geo.wrong, surface, 4.5)
            }
        }
    }

    @Test
    fun nearMissText_meetsAA_onBackgroundAndSurfaces() {
        for (t in themes) {
            assertContrast("${t.name} nearMiss on background", t.geo.nearMiss, t.scheme.background, 4.5)
            assertContrast("${t.name} nearMiss on surface", t.geo.nearMiss, t.scheme.surface, 4.5)
            assertContrast("${t.name} nearMiss on surfaceVariant", t.geo.nearMiss, t.scheme.surfaceVariant, 4.5)
        }
    }

    @Test
    fun onColours_meetAA_onTheirFills() {
        for (t in themes) {
            val g = t.geo
            assertContrast("${t.name} onCorrect/correct", g.onCorrect, g.correct, 4.5)
            assertContrast("${t.name} onWrong/wrong", g.onWrong, g.wrong, 4.5)
            assertContrast("${t.name} onNearMiss/nearMiss", g.onNearMiss, g.nearMiss, 4.5)
            assertContrast("${t.name} onCorrectContainer/correctContainer", g.onCorrectContainer, g.correctContainer, 4.5)
            assertContrast("${t.name} onWrongContainer/wrongContainer", g.onWrongContainer, g.wrongContainer, 4.5)
            // Challenge cards keep secondary text (onSurface / onSurfaceVariant) on the containers.
            for ((name, container) in listOf("correctContainer" to g.correctContainer, "wrongContainer" to g.wrongContainer)) {
                assertContrast("${t.name} onSurface/$name", t.scheme.onSurface, container, 4.5)
                assertContrast("${t.name} onSurfaceVariant/$name", t.scheme.onSurfaceVariant, container, 4.5)
            }
            // Tick / cross icons on the containers are graphics: 3:1.
            assertContrast("${t.name} correct icon/correctContainer", g.correct, g.correctContainer, 3.0)
            assertContrast("${t.name} wrong icon/wrongContainer", g.wrong, g.wrongContainer, 3.0)
        }
    }

    @Test
    fun masteryStars_standOutFromSurfaces() {
        for (t in themes) {
            // Stars are non-text graphics (WCAG 1.4.11): 3:1 wherever category rows are drawn.
            assertContrast("${t.name} star on background", t.geo.star, t.scheme.background, 3.0)
            assertContrast("${t.name} star on surface", t.geo.star, t.scheme.surface, 3.0)
            assertContrast("${t.name} star on surfaceVariant", t.geo.star, t.scheme.surfaceVariant, 3.0)
            assertContrast("${t.name} onStar/star", t.geo.onStar, t.geo.star, 4.5)
        }
    }

    @Test
    fun mapStateColours_standOutFromLand() {
        for (t in themes) {
            val g = t.geo
            listOf(
                "found" to g.mapFound,
                "highlight" to g.mapHighlight,
                "wrong" to g.mapWrong,
                "start" to g.mapStart,
                "end" to g.mapEnd,
                "tapZoneOutline" to g.mapTapZoneOutline,
            ).forEach { (name, colour) ->
                assertContrast("${t.name} map $name on land", colour, g.mapLand, 3.0)
            }
            assertContrast("${t.name} map found on water", g.mapFound, g.mapWater, 3.0)
        }
    }

    @Test
    fun mapBase_landWaterAndBordersAreDistinct() {
        for (t in themes) {
            val g = t.geo
            assertContrast("${t.name} map land vs water", g.mapLand, g.mapWater, 1.5)
            assertContrast("${t.name} map border on land", g.mapLandBorder, g.mapLand, 3.0)
            assertContrast("${t.name} map border on water", g.mapLandBorder, g.mapWater, 2.0)
        }
    }

    @Test
    fun mapLandInactive_sitsBetweenLandAndWater() {
        // Non-playable land must read as land but not as a country you can find.
        for (t in themes) {
            val g = t.geo
            assertContrast("${t.name} inactive land vs land", g.mapLandInactive, g.mapLand, 1.2)
            assertContrast("${t.name} inactive land vs water", g.mapLandInactive, g.mapWater, 1.2)
            assertContrast("${t.name} map border on inactive land", g.mapLandBorder, g.mapLandInactive, 2.0)
        }
    }

    @Test
    fun mapStates_areDistinctFromEachOther() {
        // Found and wrong sit next to each other on the map (blue vs orange); the highlight must
        // not read as either. Hue does most of the work, so the bound is low: just not equal.
        for (t in themes) {
            val g = t.geo
            val states = listOf(g.mapFound, g.mapHighlight, g.mapWrong, g.mapStart, g.mapEnd)
            assertEquals("${t.name} map state colours are all different", states.size, states.toSet().size)
        }
    }

    @Test
    fun mapTokens_differBetweenThemes() {
        val light = LightGeoColors
        val dark = DarkGeoColors
        listOf(
            "land" to (light.mapLand to dark.mapLand),
            "landInactive" to (light.mapLandInactive to dark.mapLandInactive),
            "border" to (light.mapLandBorder to dark.mapLandBorder),
            "water" to (light.mapWater to dark.mapWater),
            "found" to (light.mapFound to dark.mapFound),
            "highlight" to (light.mapHighlight to dark.mapHighlight),
            "wrong" to (light.mapWrong to dark.mapWrong),
            "start" to (light.mapStart to dark.mapStart),
            "end" to (light.mapEnd to dark.mapEnd),
            "tapZoneOutline" to (light.mapTapZoneOutline to dark.mapTapZoneOutline),
        ).forEach { (name, pair) ->
            assertNotEquals("map $name has its own dark value", pair.first, pair.second)
        }
        // Dark land is darker than light land; dark water darker than dark land (night map).
        assertTrue(luminance(dark.mapLand) < luminance(light.mapLand))
        assertTrue(luminance(dark.mapWater) < luminance(dark.mapLand))
    }

    @Test
    fun flagBorder_isVisibleOnEverySurfaceAndFlagEdge() {
        for (t in themes) {
            val s = t.scheme
            // Flags sit on list rows, cards (surfaceVariant / containers) and the background.
            val surfaces = mapOf(
                "background" to s.background,
                "surface" to s.surface,
                "surfaceVariant" to s.surfaceVariant,
                "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHighest" to s.surfaceContainerHighest,
            )
            for ((name, surface) in surfaces) {
                assertContrast("${t.name} flag border on $name", t.geo.flagBorder, surface, 2.0)
            }
        }
        // White flags (e.g. Japan) on light surfaces; black edges (e.g. Germany) on dark ones.
        assertContrast("light flag border vs white flag", LightGeoColors.flagBorder, Color.White, 2.0)
        assertContrast("dark flag border vs black flag", DarkGeoColors.flagBorder, Color.Black, 2.0)
    }

    @Test
    fun achievementTiers_meetContrastOnTheirContainers() {
        for (t in themes) {
            val g = t.geo
            listOf(
                Triple("gold", g.tierGold, g.tierGoldContainer) to g.onTierGoldContainer,
                Triple("silver", g.tierSilver, g.tierSilverContainer) to g.onTierSilverContainer,
                Triple("bronze", g.tierBronze, g.tierBronzeContainer) to g.onTierBronzeContainer,
            ).forEach { (tier, onContainer) ->
                val (name, accent, container) = tier
                // The accent colours the tier label text as well as the trophy icon.
                assertContrast("${t.name} $name accent on container", accent, container, 4.5)
                assertContrast("${t.name} $name text on container", onContainer, container, 7.0)
                // The card must stand apart from the list behind it.
                assertNotEquals("${t.name} $name container differs from background", container, t.scheme.background)
            }
        }
    }

    @Test
    fun schemeOnColours_meetAA_onTheirRoles() {
        for (t in themes) {
            val s = t.scheme
            assertContrast("${t.name} onPrimaryContainer", s.onPrimaryContainer, s.primaryContainer, 4.5)
            assertContrast("${t.name} onSecondaryContainer", s.onSecondaryContainer, s.secondaryContainer, 4.5)
            assertContrast("${t.name} onTertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, 4.5)
            assertContrast("${t.name} onPrimary", s.onPrimary, s.primary, 4.5)
            assertContrast("${t.name} onBackground", s.onBackground, s.background, 4.5)
            val surfaces = mapOf(
                "surface" to s.surface,
                "surfaceVariant" to s.surfaceVariant,
                "surfaceContainerLowest" to s.surfaceContainerLowest,
                "surfaceContainerLow" to s.surfaceContainerLow,
                "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh,
                "surfaceContainerHighest" to s.surfaceContainerHighest,
            )
            for ((name, surface) in surfaces) {
                assertContrast("${t.name} onSurface on $name", s.onSurface, surface, 4.5)
                assertContrast("${t.name} onSurfaceVariant on $name", s.onSurfaceVariant, surface, 4.5)
            }
            assertContrast("${t.name} inverseOnSurface", s.inverseOnSurface, s.inverseSurface, 4.5)
            // Outlines are non-text boundaries (text fields, outlined buttons): 3:1.
            assertContrast("${t.name} outline on surface", s.outline, s.surface, 3.0)
        }
    }

    @Test
    fun darkScheme_hasNoPurpleBaselineLeftovers() {
        // Unset roles fall back to Material's purple baseline; ours are neutral greys (R = G = B).
        val s = DarkColorScheme
        listOf(
            s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer,
            s.surfaceContainerHigh, s.surfaceContainerHighest, s.surfaceBright, s.surfaceDim,
            s.outline, s.outlineVariant, s.inverseSurface, s.inverseOnSurface,
        ).forEach { colour ->
            val argb = colour.toArgb()
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            assertTrue("dark role ${Integer.toHexString(argb)} is neutral", r == g && g == b)
        }
    }

    @Test
    fun contrastRatio_matchesKnownValues() {
        // Sanity check of the formula: black on white is 21:1, a colour on itself is 1:1.
        assertTrue(kotlin.math.abs(contrast(Color.Black, Color.White) - 21.0) < 0.01)
        assertTrue(kotlin.math.abs(contrast(Color.Gray, Color.Gray) - 1.0) < 0.0001)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue("$label: ${"%.2f".format(ratio)}:1 < $minimum:1", ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** WCAG relative luminance of an opaque sRGB colour. */
    private fun luminance(c: Color): Double {
        val argb = c.toArgb()
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) +
            0.7152 * channel((argb shr 8) and 0xFF) +
            0.0722 * channel(argb and 0xFF)
    }
}
