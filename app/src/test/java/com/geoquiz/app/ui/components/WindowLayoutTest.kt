package com.geoquiz.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Window size decisions (3.7). */
class WindowLayoutTest {

    @Test
    fun `width classes break at 600 and 840 dp`() {
        assertEquals(WidthClass.Compact, widthClassOf(320))
        assertEquals(WidthClass.Compact, widthClassOf(599))
        assertEquals(WidthClass.Medium, widthClassOf(600))
        assertEquals(WidthClass.Medium, widthClassOf(839))
        assertEquals(WidthClass.Expanded, widthClassOf(840))
        assertEquals(WidthClass.Expanded, widthClassOf(1280))
    }

    @Test
    fun `short height is below 480 dp`() {
        assertTrue(isShortHeight(360))
        assertTrue(isShortHeight(479))
        assertFalse(isShortHeight(480))
        assertFalse(isShortHeight(891))
    }

    @Test
    fun `a phone in portrait keeps the bottom bar and one pane`() {
        val layout = WindowLayout(widthDp = 411, heightDp = 891)

        assertEquals(WidthClass.Compact, layout.widthClass)
        assertFalse(layout.isLandscapePhone)
        assertFalse(layout.useNavigationRail)
        assertFalse(layout.useTwoPanes)
        assertFalse(layout.useWideHub)
    }

    @Test
    fun `a small phone in portrait with a short window is not a landscape phone`() {
        val layout = WindowLayout(widthDp = 320, heightDp = 470)

        assertTrue(layout.isShortHeight)
        assertFalse(layout.isLandscape)
        assertFalse(layout.useTwoPanes)
    }

    @Test
    fun `a phone in landscape gets the rail and two panes`() {
        val layout = WindowLayout(widthDp = 891, heightDp = 411)

        assertTrue(layout.isLandscapePhone)
        assertTrue(layout.useNavigationRail)
        assertTrue(layout.useTwoPanes)
    }

    @Test
    fun `a narrow phone in landscape gets two panes even below Medium width`() {
        val layout = WindowLayout(widthDp = 560, heightDp = 320)

        assertEquals(WidthClass.Compact, layout.widthClass)
        assertTrue(layout.isLandscapePhone)
        assertTrue(layout.useTwoPanes)
        // The rail follows the width class only.
        assertFalse(layout.useNavigationRail)
    }

    @Test
    fun `a small tablet in portrait gets the rail but keeps one pane`() {
        val layout = WindowLayout(widthDp = 700, heightDp = 1000)

        assertEquals(WidthClass.Medium, layout.widthClass)
        assertFalse(layout.isLandscapePhone)
        assertTrue(layout.useNavigationRail)
        assertFalse(layout.useTwoPanes)
        assertFalse(layout.useWideHub)
    }

    @Test
    fun `a small tablet in landscape gets two panes`() {
        val layout = WindowLayout(widthDp = 800, heightDp = 600)

        assertEquals(WidthClass.Medium, layout.widthClass)
        assertFalse(layout.isLandscapePhone)
        assertTrue(layout.useTwoPanes)
        assertFalse(layout.useWideHub)
    }

    @Test
    fun `Expanded width gets two panes even when taller than wide`() {
        assertTrue(WindowLayout(widthDp = 900, heightDp = 1200).useTwoPanes)
    }

    @Test
    fun `a layout from constraints rounds dp down`() {
        assertEquals(WindowLayout(599, 479), WindowLayout.of(599.9.dp, 479.5.dp))
        assertEquals(WidthClass.Compact, WindowLayout.of(599.9.dp, 800.dp).widthClass)
    }

    @Test
    fun `a large tablet gets the wide hub`() {
        val layout = WindowLayout(widthDp = 1280, heightDp = 800)

        assertEquals(WidthClass.Expanded, layout.widthClass)
        assertTrue(layout.useNavigationRail)
        assertTrue(layout.useTwoPanes)
        assertTrue(layout.useWideHub)
    }

    @Test
    fun `the readable side inset centres 840 dp of content and is never negative`() {
        assertEquals(0.dp, readableSideInset(411.dp))
        assertEquals(0.dp, readableSideInset(840.dp))
        assertEquals(20.dp, readableSideInset(880.dp))
        assertEquals(220.dp, readableSideInset(1280.dp))
        assertEquals(100.dp, readableSideInset(400.dp, maxWidth = 200.dp))
    }
}
