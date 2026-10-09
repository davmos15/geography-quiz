package com.geoquiz.app.ui.components

import androidx.compose.ui.unit.dp
import com.geoquiz.app.ui.quiz.components.listMinHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decisions behind the large-text layouts (3.6). */
class AdaptiveLayoutsTest {

    @Test
    fun `buttons sit side by side only when every one fits an equal share`() {
        // Two 100 px buttons with an 8 px gap need 208 px.
        assertTrue(buttonsFitSideBySide(widest = 100, count = 2, gap = 8, available = 208))
        assertFalse(buttonsFitSideBySide(widest = 100, count = 2, gap = 8, available = 207))
        // The widest decides, so a long label stacks both.
        assertFalse(buttonsFitSideBySide(widest = 150, count = 2, gap = 8, available = 300))
        assertTrue(buttonsFitSideBySide(widest = 90, count = 3, gap = 8, available = 286))
    }

    @Test
    fun `no buttons never count as side by side`() {
        assertFalse(buttonsFitSideBySide(widest = 0, count = 0, gap = 8, available = 100))
    }

    @Test
    fun `trailing content moves below once the main slot would drop under its minimum`() {
        assertTrue(trailingFitsBeside(available = 300, trailingWidth = 160, minMainWidth = 140))
        assertFalse(trailingFitsBeside(available = 300, trailingWidth = 161, minMainWidth = 140))
        // Trailing content wider than the row always moves below.
        assertFalse(trailingFitsBeside(available = 100, trailingWidth = 120, minMainWidth = 0))
    }

    @Test
    fun `the grid keeps two columns while two minimum tiles fit, else one`() {
        assertEquals(2, gridColumnCount(available = 292f, spacing = 12f, minCell = 140f))
        assertEquals(1, gridColumnCount(available = 291f, spacing = 12f, minCell = 140f))
        assertEquals(2, gridColumnCount(available = 1000f, spacing = 12f, minCell = 140f))
    }

    // The Play grid: 120 dp tiles, 12 dp apart, scaled only above 130% text. A 360 dp phone has
    // 328 dp for the grid (16 dp padding each side), a 320 dp phone 288 dp.
    private fun playColumns(availableDp: Float, fontScale: Float) =
        gridColumnCount(availableDp, spacing = 12f, minCell = 120f, fontScale = fontScale, scaleAbove = 1.3f)

    @Test
    fun `the Play grid keeps two columns from 100 to 130 percent text on a 360 dp phone`() {
        listOf(1f, 1.15f, 1.3f).forEach { scale ->
            assertEquals("at $scale", 2, playColumns(328f, scale))
        }
    }

    @Test
    fun `the Play grid keeps two columns at 100 percent text on a 320 dp phone`() {
        assertEquals(2, playColumns(288f, 1f))
    }

    @Test
    fun `the Play grid drops to one column at 200 percent text on phones but not on a tablet`() {
        assertEquals(1, playColumns(328f, 2f))
        assertEquals(1, playColumns(379f, 2f))
        assertEquals(2, playColumns(768f, 2f))
    }

    @Test
    fun `cells fill the width exactly, extra pixels going to the first cells`() {
        assertEquals(listOf(150, 150), splitEvenly(available = 312, spacing = 12, count = 2))
        assertEquals(listOf(151, 150), splitEvenly(available = 313, spacing = 12, count = 2))
        assertEquals(listOf(313), splitEvenly(available = 313, spacing = 12, count = 1))
        assertEquals(listOf(0, 0), splitEvenly(available = 5, spacing = 12, count = 2))
    }

    @Test
    fun `a capped trailing slot takes at most its share of the row`() {
        assertEquals(160, cappedTrailingWidth(rowWidth = 320, fraction = 0.5f))
        assertEquals(0, cappedTrailingWidth(rowWidth = 320, fraction = -1f))
        assertEquals(320, cappedTrailingWidth(rowWidth = 320, fraction = 2f))
    }

    @Test
    fun `the Easy list keeps 40 percent of the space or the scaled minimum, whichever is less`() {
        // 100%: 120 dp beats 40% of 600 dp.
        assertEquals(120.dp, listMinHeight(600.dp, fontScale = 1f))
        // 200%: 240 dp, the same as 40% of 600 dp.
        assertEquals(240.dp, listMinHeight(600.dp, fontScale = 2f))
        // A short space: 40% of it.
        assertEquals(80.dp, listMinHeight(200.dp, fontScale = 2f))
        // Text smaller than 100% never lowers the minimum.
        assertEquals(120.dp, listMinHeight(600.dp, fontScale = 0.85f))
    }
}
