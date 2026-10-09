package com.geoquiz.app.ui.screenshot

import androidx.compose.ui.test.junit4.createComposeRule
import com.geoquiz.app.testutil.MainDispatcherRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for tablet and landscape layouts (3.7), light theme only: two panes for the
 * quiz and results, the wide Play hub with its own card column, and the navigation rail.
 * Same recording, determinism and comparison rules as [KeyScreensScreenshotTest].
 *
 * Each test sets its own window (`@Config(qualifiers = ...)` without `+` replaces the class
 * default). All use xhdpi so the half-size goldens stay legible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = WideLayoutsScreenshotTest.TABLET)
class WideLayoutsScreenshotTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /** Normal tier, three named and one strike: the list beside the prompt and answer field. */
    @Test
    fun quiz_tablet() = compose.captureScreen("quiz_tablet", ScreenshotScreens.quizInProgress(hardMode = false))

    /** A phone turned sideways: two panes whatever the width class. */
    @Test @Config(qualifiers = LANDSCAPE_PHONE)
    fun quiz_landscape() = compose.captureScreen("quiz_landscape", ScreenshotScreens.quizInProgress(hardMode = false))

    /** Large tablet: the cards in their own column beside the mode switch and three-column tiles. */
    @Test @Config(qualifiers = LARGE_TABLET)
    fun play_tablet() = compose.captureScreen("play_tablet", ScreenshotScreens.play())

    /** Summary in one pane, actions in the other. */
    @Test
    fun results_tablet() = compose.captureScreen("results_tablet", ScreenshotScreens.results())

    /** The navigation rail on its own, Play selected. */
    @Test
    fun nav_rail() = compose.captureScreen("nav_rail", ScreenshotScreens.navRail())

    companion object {
        const val TABLET = "w900dp-h600dp-xhdpi"
        const val LANDSCAPE_PHONE = "w640dp-h360dp-xhdpi"
        const val LARGE_TABLET = "w1280dp-h800dp-xhdpi"
    }
}
