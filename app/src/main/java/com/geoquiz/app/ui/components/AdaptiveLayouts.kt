package com.geoquiz.app.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * Layouts that change shape when large text no longer fits (3.6), instead of clipping or
 * squeezing it. They decide from the measured content and the space actually available, so they
 * also behave on narrow phones at 100% text and keep working in a later two-pane layout.
 */

/**
 * Buttons side by side at equal widths when every one fits on a single line that way; otherwise
 * stacked, each the full width, so long labels wrap instead of being cut off. Material buttons
 * keep their 48 dp touch target either way.
 */
@Composable
fun AdaptiveButtonRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(constraints.minWidth, 0) {}
        val gap = spacing.roundToPx()
        val count = measurables.size
        val widest = measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) }
        val sideBySide = constraints.hasBoundedWidth &&
            buttonsFitSideBySide(widest, count, gap, constraints.maxWidth)

        if (sideBySide) {
            val cellWidth = (constraints.maxWidth - gap * (count - 1)) / count
            val placeables = measurables.map {
                it.measure(Constraints(minWidth = cellWidth, maxWidth = cellWidth))
            }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += cellWidth + gap
                }
            }
        } else {
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widest
            val placeables = measurables.map {
                it.measure(Constraints(minWidth = width, maxWidth = width))
            }
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}

/** True when [count] cells of at least [widest] px, [gap] px apart, fit in [available] px. */
internal fun buttonsFitSideBySide(widest: Int, count: Int, gap: Int, available: Int): Boolean =
    count > 0 && widest.toLong() * count + gap.toLong() * (count - 1) <= available

/**
 * [main] (usually a text column) with [trailing] (counts, small buttons) to its right while
 * [main] still gets at least [minMainWidth] (scaled with the font size); otherwise [trailing]
 * moves below [main], aligned to the end, so [main] keeps the full width and long names wrap
 * instead of being squeezed into a narrow column.
 */
@Composable
fun AdaptiveTrailingRow(
    main: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    minMainWidth: Dp = 140.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically
) {
    val fontScale = LocalDensity.current.fontScale
    Layout(contents = listOf(main, trailing), modifier = modifier) { (mainMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose) }
        val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
        val trailingHeight = trailingPlaceables.maxOfOrNull { it.height } ?: 0
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val minMainPx = scaledMinWidthPx(minMainWidth, fontScale)
        val sideBySide = !constraints.hasBoundedWidth ||
            trailingFitsBeside(maxWidth, trailingWidth, minMainPx)

        if (sideBySide) {
            val mainMax = if (constraints.hasBoundedWidth) maxWidth - trailingWidth else Constraints.Infinity
            val mainPlaceables = mainMeasurables.map { it.measure(loose.copy(maxWidth = mainMax)) }
            val mainWidth = mainPlaceables.maxOfOrNull { it.width } ?: 0
            val width = if (constraints.hasBoundedWidth) maxWidth else mainWidth + trailingWidth
            val height = maxOf(mainPlaceables.maxOfOrNull { it.height } ?: 0, trailingHeight)
                .coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                mainPlaceables.forEach { it.placeRelative(0, verticalAlignment.align(it.height, height)) }
                trailingPlaceables.forEach {
                    it.placeRelative(width - it.width, verticalAlignment.align(it.height, height))
                }
            }
        } else {
            val mainPlaceables = mainMeasurables.map { it.measure(loose.copy(minWidth = maxWidth, maxWidth = maxWidth)) }
            val mainHeight = mainPlaceables.maxOfOrNull { it.height } ?: 0
            val height = (mainHeight + trailingHeight).coerceAtLeast(constraints.minHeight)
            layout(maxWidth, height) {
                mainPlaceables.forEach { it.placeRelative(0, 0) }
                trailingPlaceables.forEach { it.placeRelative(maxWidth - it.width, mainHeight) }
            }
        }
    }
}

/** [base] scaled with the text, in px; never smaller than at 100%. */
private fun Density.scaledMinWidthPx(base: Dp, fontScale: Float): Int =
    (base.toPx() * fontScale.coerceAtLeast(1f)).roundToInt()

/** True when [trailingWidth] px beside the main slot still leaves it [minMainWidth] px. */
internal fun trailingFitsBeside(available: Int, trailingWidth: Int, minMainWidth: Int): Boolean =
    available - trailingWidth >= minMainWidth

/**
 * Two columns for a grid of tiles while each tile is at least [minCellWidth] wide; one column
 * otherwise, so tile titles get the full width at large text sizes instead of breaking mid-word.
 * [minCellWidth] scales with the font only above [scaleAbove], so slightly larger text keeps two
 * columns on narrow phones.
 */
data class UpToTwoColumns(val minCellWidth: Dp, val scaleAbove: Float = 1f) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = gridColumnCount(
            available = availableSize.toFloat(),
            spacing = spacing.toFloat(),
            minCell = minCellWidth.toPx(),
            fontScale = fontScale,
            scaleAbove = scaleAbove
        )
        return splitEvenly(availableSize, spacing, count)
    }
}

/**
 * 2 when two cells, [spacing] apart, of at least [minCell] (scaled by [fontScale] only when it
 * is above [scaleAbove]) fit in [available], else 1. Any one unit (px or dp) for all sizes.
 */
internal fun gridColumnCount(
    available: Float,
    spacing: Float,
    minCell: Float,
    fontScale: Float = 1f,
    scaleAbove: Float = 1f
): Int {
    val scaledCell = if (fontScale > scaleAbove) minCell * fontScale else minCell
    return if (available >= scaledCell * 2 + spacing) 2 else 1
}

/** [count] cell widths filling [available] px with [spacing] px between them; extra px go first. */
internal fun splitEvenly(available: Int, spacing: Int, count: Int): List<Int> {
    val usable = (available - spacing * (count - 1)).coerceAtLeast(0)
    val cell = usable / count
    val extra = usable % count
    return List(count) { index -> cell + if (index < extra) 1 else 0 }
}

/**
 * [main] then [trailing] on one line: [trailing] takes the width it needs, up to
 * [maxTrailingFraction] of the row, and [main] gets the rest. Both wrap when they must, so a
 * long main text next to a short trailing one stays on one line (e.g. a country and its capital).
 */
@Composable
fun MainWithCappedTrailing(
    main: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxTrailingFraction: Float = 0.5f
) {
    Layout(contents = listOf(main, trailing), modifier = modifier) { (mainMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingMax = if (constraints.hasBoundedWidth) {
            cappedTrailingWidth(constraints.maxWidth, maxTrailingFraction)
        } else {
            Constraints.Infinity
        }
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose.copy(maxWidth = trailingMax)) }
        val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
        val mainMax = if (constraints.hasBoundedWidth) constraints.maxWidth - trailingWidth else Constraints.Infinity
        val mainPlaceables = mainMeasurables.map { it.measure(loose.copy(maxWidth = mainMax)) }
        val mainWidth = mainPlaceables.maxOfOrNull { it.width } ?: 0
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else mainWidth + trailingWidth
        val height = maxOf(
            mainPlaceables.maxOfOrNull { it.height } ?: 0,
            trailingPlaceables.maxOfOrNull { it.height } ?: 0,
            constraints.minHeight
        )
        layout(width, height) {
            mainPlaceables.forEach { it.placeRelative(0, Alignment.CenterVertically.align(it.height, height)) }
            trailingPlaceables.forEach {
                it.placeRelative(width - it.width, Alignment.CenterVertically.align(it.height, height))
            }
        }
    }
}

/** The most the trailing slot of [MainWithCappedTrailing] may take of [rowWidth] px. */
internal fun cappedTrailingWidth(rowWidth: Int, fraction: Float): Int =
    (rowWidth * fraction.coerceIn(0f, 1f)).roundToInt()

/**
 * Horizontal padding, tick and gap Material puts inside a segmented button around its label,
 * plus a little slack: a label fits its segment on one line if it is no wider than the segment
 * minus this.
 */
val SEGMENT_LABEL_CHROME: Dp = 12.dp * 2 + 18.dp + 8.dp + 4.dp
