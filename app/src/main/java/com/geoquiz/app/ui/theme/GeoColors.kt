package com.geoquiz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens that Material 3's [androidx.compose.material3.ColorScheme] has no slot
 * for: answer feedback and map states. Read them with [MaterialTheme.geoColors].
 *
 * Feedback follows the colour-vision-safe pairing blue / orange (plus reddish purple for a near
 * miss), after the Okabe–Ito palette. Colour is never the only cue: correct pairs with a tick
 * icon, wrong with a cross, a near miss with the spell-check icon.
 *
 * Contrast (checked by `GeoColorsContrastTest`): [correct], [wrong] and [nearMiss] are text
 * colours and reach 4.5:1 on background, surface and surfaceVariant (correct and wrong also on
 * primaryContainer and tertiaryContainer, where results and challenge cards put them). Each
 * `onX` reaches 4.5:1 on its `X`. [star] reaches 3:1 on background, surface and surfaceVariant.
 * Map state colours reach 3:1 against [mapLand]; land and water 1.5:1; borders 3:1 on land.
 * [flagBorder] reaches 2:1 on every surface a flag sits on and on the flag edge most likely to
 * merge with it (white in light, black in dark). Achievement tier accents reach 4.5:1 and tier
 * on-colours 7:1 on their tier container.
 */
@Immutable
data class GeoColors(
    /** Correct answers: text, tick icons, progress. */
    val correct: Color,
    /** Content drawn on a solid [correct] fill. */
    val onCorrect: Color,
    /** Soft fill behind a correct item (e.g. a won challenge card). */
    val correctContainer: Color,
    val onCorrectContainer: Color,

    /** Wrong answers and strikes: text, cross icons. */
    val wrong: Color,
    val onWrong: Color,
    val wrongContainer: Color,
    val onWrongContainer: Color,

    /** Near miss ("check the spelling"): not a strike, so neither correct nor wrong. */
    val nearMiss: Color,
    val onNearMiss: Color,

    /**
     * Mastery stars (filled for earned, outlined for not yet earned, so never colour alone).
     * Non-text graphic: 3:1 on background, surface and surfaceVariant.
     */
    val star: Color,
    /** Content drawn on a solid [star] fill. */
    val onStar: Color,

    /**
     * Hairline border round every flag image, so white flags stay visible on light surfaces and
     * dark flag edges on dark surfaces.
     */
    val flagBorder: Color,

    // ---- Achievement tiers (unlocked cards; locked cards use surfaceVariant) ----
    /** Trophy icon and tier label on [tierGoldContainer]. */
    val tierGold: Color,
    val tierGoldContainer: Color,
    /** Title and description text on [tierGoldContainer]. */
    val onTierGoldContainer: Color,
    val tierSilver: Color,
    val tierSilverContainer: Color,
    val onTierSilverContainer: Color,
    val tierBronze: Color,
    val tierBronzeContainer: Color,
    val onTierBronzeContainer: Color,

    // ---- Map (used from Phase 4) ----
    /** Country fill before it is found. */
    val mapLand: Color,
    /** Country borders and coastlines. */
    val mapLandBorder: Color,
    val mapWater: Color,
    /** A country the player has named (blue family, clearly darker/lighter than land and water). */
    val mapFound: Color,
    /** The country a question points at. */
    val mapHighlight: Color,
    /** A wrong tap or a missed country (orange family). */
    val mapWrong: Color,
    /** Route start marker. */
    val mapStart: Color,
    /** Route end marker. */
    val mapEnd: Color,
    /** Outline of the enlarged tap zone drawn round microstates. */
    val mapTapZoneOutline: Color,
)

internal val LightGeoColors = GeoColors(
    correct = Color(0xFF0B5CAD),
    onCorrect = Color(0xFFFFFFFF),
    correctContainer = Color(0xFFD6E8FA),
    onCorrectContainer = Color(0xFF00315C),

    wrong = Color(0xFF9A4000),
    onWrong = Color(0xFFFFFFFF),
    wrongContainer = Color(0xFFFFE0CC),
    onWrongContainer = Color(0xFF4A1C00),

    nearMiss = Color(0xFF9A3A78),
    onNearMiss = Color(0xFFFFFFFF),

    star = Color(0xFF8A6100),
    onStar = Color(0xFFFFFFFF),

    flagBorder = Color(0xFF9E9E9E),

    tierGold = Color(0xFF7A5600),
    tierGoldContainer = Color(0xFFFFF4D1),
    onTierGoldContainer = Color(0xFF2E2100),
    tierSilver = Color(0xFF4F5B66),
    tierSilverContainer = Color(0xFFECEFF1),
    onTierSilverContainer = Color(0xFF1F2328),
    tierBronze = Color(0xFF8A4B14),
    tierBronzeContainer = Color(0xFFFBE6D6),
    onTierBronzeContainer = Color(0xFF3A1E08),

    mapLand = Color(0xFFF5F2EC),
    mapLandBorder = Color(0xFF8A857A),
    mapWater = Color(0xFFA9CBE3),
    mapFound = Color(0xFF1A65AA),
    mapHighlight = Color(0xFFA8327F),
    mapWrong = Color(0xFFC85200),
    mapStart = Color(0xFF00845F),
    mapEnd = Color(0xFF5B3A96),
    mapTapZoneOutline = Color(0xFF37474F),
)

internal val DarkGeoColors = GeoColors(
    correct = Color(0xFF9DCDFF),
    onCorrect = Color(0xFF00315C),
    correctContainer = Color(0xFF0D3A63),
    onCorrectContainer = Color(0xFFD6E8FA),

    wrong = Color(0xFFFFBC8A),
    onWrong = Color(0xFF4A1C00),
    wrongContainer = Color(0xFF5C2A00),
    onWrongContainer = Color(0xFFFFE0CC),

    nearMiss = Color(0xFFF2A7D6),
    onNearMiss = Color(0xFF4A0F38),

    star = Color(0xFFFFCC4D),
    onStar = Color(0xFF3D2B00),

    flagBorder = Color(0xFF7A7A7A),

    tierGold = Color(0xFFFFD25C),
    tierGoldContainer = Color(0xFF3D3000),
    onTierGoldContainer = Color(0xFFFFEFC2),
    tierSilver = Color(0xFFCFD8DC),
    tierSilverContainer = Color(0xFF33393E),
    onTierSilverContainer = Color(0xFFECEFF1),
    tierBronze = Color(0xFFF2B98A),
    tierBronzeContainer = Color(0xFF43281A),
    onTierBronzeContainer = Color(0xFFFFE3D0),

    mapLand = Color(0xFF403F3A),
    mapLandBorder = Color(0xFF908B81),
    mapWater = Color(0xFF0B1926),
    mapFound = Color(0xFF64B0F5),
    mapHighlight = Color(0xFFEA8FCC),
    mapWrong = Color(0xFFFF9A52),
    mapStart = Color(0xFF45C99B),
    mapEnd = Color(0xFFB8A0EB),
    mapTapZoneOutline = Color(0xFFCFD8DC),
)

/** Provided by [GeographyQuizTheme]; defaults to the light set outside the theme. */
val LocalGeoColors = staticCompositionLocalOf { LightGeoColors }

/** GeoQuiz's extra semantic colours for the current theme. */
@Suppress("UnusedReceiverParameter")
val MaterialTheme.geoColors: GeoColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGeoColors.current
