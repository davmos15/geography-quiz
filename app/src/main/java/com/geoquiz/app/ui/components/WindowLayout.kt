package com.geoquiz.app.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Window size decisions for phones, landscape phones and tablets (3.7). Pure functions of the
 * window size in dp, so they are unit-tested; no window-size library is needed. The breakpoints
 * follow Material's window size classes.
 */

/** Window width class: Compact (phones in portrait), Medium (large phones in landscape, small tablets), Expanded. */
enum class WidthClass { Compact, Medium, Expanded }

/** Below this width the window is [WidthClass.Compact]. */
val MEDIUM_WIDTH_MIN: Dp = 600.dp

/** From this width the window is [WidthClass.Expanded]. */
val EXPANDED_WIDTH_MIN: Dp = 840.dp

/** Below this height the window is short (a phone in landscape). */
val SHORT_HEIGHT_MAX: Dp = 480.dp

/** The widest a column of text and controls gets on a wide screen, so rows don't stretch. */
val READABLE_MAX_WIDTH: Dp = 840.dp

/** The widest two side-by-side panes get together (Results, Play on large tablets). */
val TWO_PANE_MAX_WIDTH: Dp = 1200.dp

fun widthClassOf(widthDp: Int): WidthClass = when {
    widthDp < MEDIUM_WIDTH_MIN.value -> WidthClass.Compact
    widthDp < EXPANDED_WIDTH_MIN.value -> WidthClass.Medium
    else -> WidthClass.Expanded
}

fun isShortHeight(heightDp: Int): Boolean = heightDp < SHORT_HEIGHT_MAX.value

/** The window's size in dp and the decisions made from it. */
data class WindowLayout(val widthDp: Int, val heightDp: Int) {
    val widthClass: WidthClass = widthClassOf(widthDp)
    val isShortHeight: Boolean = isShortHeight(heightDp)

    /** Wider than tall. */
    val isLandscape: Boolean = widthDp > heightDp

    /** A phone turned sideways: short and wider than tall, whatever the width class. */
    val isLandscapePhone: Boolean = isShortHeight && isLandscape

    /** Navigation rail on the start side instead of the bottom bar (Medium and Expanded). */
    val useNavigationRail: Boolean = widthClass != WidthClass.Compact

    /**
     * Content side by side in two panes (quiz, results): at Expanded width, at Medium width when
     * wider than tall, and on a landscape phone whatever its width, where a single column would
     * leave almost no room under the header. A Medium tablet in portrait keeps one column.
     */
    val useTwoPanes: Boolean = widthClass == WidthClass.Expanded ||
        (widthClass == WidthClass.Medium && isLandscape) ||
        isLandscapePhone

    /** Play's cards in their own column beside the mode switch and tiles. */
    val useWideHub: Boolean = widthClass == WidthClass.Expanded

    companion object {
        /**
         * The layout for the space a screen actually has (e.g. `BoxWithConstraints` beside the
         * navigation rail), rather than the whole window.
         */
        fun of(width: Dp, height: Dp): WindowLayout =
            WindowLayout(width.value.toInt(), height.value.toInt())
    }
}

/**
 * The whole window's [WindowLayout], from the configuration (updates on rotation and resizing).
 * For the app's navigation chrome; screens decide from their own constraints with [WindowLayout.of].
 */
@Composable
fun rememberWindowLayout(): WindowLayout {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return remember(width, height) { WindowLayout(width, height) }
}

/**
 * The extra side padding that keeps content of at most [maxWidth] centred in [availableWidth];
 * 0 when it is narrower.
 */
fun readableSideInset(availableWidth: Dp, maxWidth: Dp = READABLE_MAX_WIDTH): Dp =
    ((availableWidth - maxWidth) / 2).coerceAtLeast(0.dp)

/**
 * For lazy lists: gives [content] the [readableSideInset] for the space it fills, to add to the
 * list's horizontal content padding. The list itself stays full width, so it still scrolls from
 * the margins. Don't put this inside a layout that asks for intrinsic sizes.
 */
@Composable
fun ReadableWidthFrame(
    modifier: Modifier = Modifier,
    content: @Composable (sideInset: Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        content(readableSideInset(maxWidth))
    }
}

/**
 * Fills the width up to [maxWidth] and centres the content, so text and controls keep a
 * readable line length on tablets. No effect on screens narrower than [maxWidth].
 */
fun Modifier.readableWidth(maxWidth: Dp = READABLE_MAX_WIDTH): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = maxWidth)
        .fillMaxWidth()
