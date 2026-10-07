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
 * `onX` reaches 4.5:1 on its `X`. Map state colours reach 3:1 against [mapLand].
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

    mapLand = Color(0xFFF2EEE6),
    mapLandBorder = Color(0xFF8A857A),
    mapWater = Color(0xFFBCD7EA),
    mapFound = Color(0xFF1F6FB8),
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

    mapLand = Color(0xFF3B3A36),
    mapLandBorder = Color(0xFF77736A),
    mapWater = Color(0xFF0E2131),
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
