package com.geoquiz.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/*
 * Assertions for the 200% font checks (3.6). Use them on the Text node itself (find it with
 * `useUnmergedTree = true` when it sits inside a button or a merged row).
 */

/** The text layout of a Text node. */
fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
    checkNotNull(action) { "not a text node" }
    action(results)
    return results.single()
}

/**
 * The text is shown in full: no line cut off at the side or ellipsised, no line beyond
 * `maxLines`, and every line inside the node's height. (Line widths are used rather than
 * `hasVisualOverflow`, which also flags centred text whose paragraph is wider than its glyphs.)
 */
fun SemanticsNodeInteraction.assertTextNotClipped(): SemanticsNodeInteraction {
    val layout = textLayout()
    val text = layout.layoutInput.text
    assertFalse("'$text' has more lines than maxLines", layout.multiParagraph.didExceedMaxLines)
    assertTrue(
        "'$text' is taller (${layout.multiParagraph.height}) than its box (${layout.size.height})",
        layout.multiParagraph.height <= layout.size.height + 0.5f
    )
    for (line in 0 until layout.lineCount) {
        assertFalse("line $line of '$text' is ellipsised", layout.isLineEllipsized(line))
        val width = layout.getLineRight(line) - layout.getLineLeft(line)
        assertTrue(
            "line $line of '$text' is wider ($width) than its box (${layout.size.width})",
            width <= layout.size.width + 0.5f
        )
    }
    return this
}

/** The text is laid out on exactly [lines] lines. */
fun SemanticsNodeInteraction.assertLineCount(lines: Int): SemanticsNodeInteraction {
    assertEquals("lines of '${textLayout().layoutInput.text}'", lines, textLayout().lineCount)
    return this
}

/** This node starts at or below the bottom of [above] (allowing for rounding). */
fun SemanticsNodeInteraction.assertIsBelow(above: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val mine = getBoundsInRoot()
    val theirs = above.getBoundsInRoot()
    assertTrue("top ${mine.top} is above ${theirs.bottom}", mine.top >= theirs.bottom - TOLERANCE)
    return this
}

/** This node is on the same line as [other] (same top, allowing for rounding) and to its right. */
fun SemanticsNodeInteraction.assertIsRightOf(other: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val mine = getBoundsInRoot()
    val theirs = other.getBoundsInRoot()
    assertTrue("not side by side: $mine vs $theirs", mine.left >= theirs.right - TOLERANCE)
    assertTrue("not on the same line: $mine vs $theirs", mine.top < theirs.bottom && theirs.top < mine.bottom)
    return this
}

val DpRect.heightDp: Dp get() = bottom - top
val DpRect.widthDp: Dp get() = right - left

private val TOLERANCE = Dp(1f)
