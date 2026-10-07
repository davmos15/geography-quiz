package com.geoquiz.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
 * - Map state colours are non-text graphics and need 3:1 against land (WCAG 1.4.11).
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
