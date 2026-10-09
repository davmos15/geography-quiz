package com.geoquiz.app.ui.quiz.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The share of the space the list always keeps... */
internal const val LIST_MIN_FRACTION = 0.4f

/** ...unless this (at 100% font, scaled with it) is less. */
internal val LIST_MIN_HEIGHT = 120.dp

/** Gap between the panel and the list. */
private val PANEL_LIST_GAP = 12.dp

/**
 * Easy tier (3.6): the multiple-choice [panel] above the quiz's country [list], sharing the
 * space. The panel gets the height it needs, but never so much that the list disappears: at
 * large font scales or on short screens it scrolls, and the list keeps at least
 * [listMinHeight] of the space this layout is given. When [scrollKey] changes (the next
 * question) the panel scrolls back to the top.
 */
@Composable
fun PanelAboveList(
    panel: @Composable ColumnScope.() -> Unit,
    list: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    scrollKey: Any? = null
) {
    val fontScale = LocalDensity.current.fontScale
    // A new question starts at the top of the panel, not where the last one was scrolled to.
    val panelScroll = rememberScrollState()
    LaunchedEffect(scrollKey) { panelScroll.scrollTo(0) }
    BoxWithConstraints(modifier = modifier) {
        val panelMaxHeight = (maxHeight - listMinHeight(maxHeight, fontScale) - PANEL_LIST_GAP)
            .coerceAtLeast(0.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .heightIn(max = panelMaxHeight)
                    .verticalScroll(panelScroll),
                content = panel
            )
            Spacer(modifier = Modifier.height(PANEL_LIST_GAP))
            list(Modifier.weight(1f))
        }
    }
}

/**
 * The least height the list keeps out of [available]: [LIST_MIN_FRACTION] of it, or
 * [LIST_MIN_HEIGHT] scaled with the font if that is less.
 */
internal fun listMinHeight(available: Dp, fontScale: Float): Dp =
    minOf(available * LIST_MIN_FRACTION, LIST_MIN_HEIGHT * fontScale.coerceAtLeast(1f))
