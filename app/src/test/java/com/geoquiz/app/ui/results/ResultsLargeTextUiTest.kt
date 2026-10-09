package com.geoquiz.app.ui.results

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.geoquiz.app.testutil.MainDispatcherRule
import com.geoquiz.app.testutil.ScreenTestFixtures
import com.geoquiz.app.ui.assertIsBelow
import com.geoquiz.app.ui.assertIsRightOf
import com.geoquiz.app.ui.assertTextNotClipped
import com.geoquiz.app.ui.textLayout
import com.geoquiz.app.ui.theme.GeographyQuizTheme
import com.geoquiz.app.ui.widthDp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Results buttons at 100% and 200% text on a narrow phone (3.6): Share and Challenge sit side
 * by side while they fit and stack otherwise; no button label is ever cut off. At large text the
 * buttons take the full content width.
 */
@RunWith(RobolectricTestRunner::class)
// Real text measurement, so line counts and clipping are meaningful.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp")
class ResultsLargeTextUiTest {

    @get:Rule(order = 0)
    val mainDispatcher = MainDispatcherRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun launch() {
        val viewModel = ScreenTestFixtures.resultsViewModel()
        compose.setContent {
            GeographyQuizTheme(darkTheme = false) {
                ResultsScreen(
                    onPlayAgain = { _, _, _, _ -> },
                    onGoHome = {},
                    onViewAnswers = {},
                    onPractiseMissed = { _, _, _, _ -> },
                    viewModel = viewModel
                )
            }
        }
        compose.waitForIdle()
    }

    /** A button label, scrolled into view. */
    private fun label(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).also {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private val buttonLabels = listOf(
        "Share", "Challenge", "View Answers", "Play Again", "Practise the ones you missed (3)", "Home"
    )

    @Test
    fun `at 100 percent text on a narrow phone every button label shows in full`() {
        launch()

        buttonLabels.forEach { label(it).assertIsDisplayed().assertTextNotClipped() }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `at 100 percent text on a typical phone Share and Challenge sit side by side`() {
        launch()

        label("Challenge").assertIsDisplayed().assertIsRightOf(label("Share"))
        buttonLabels.forEach { label(it).assertIsDisplayed().assertTextNotClipped() }
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent text Share and Challenge stack and every button label shows in full`() {
        launch()

        label("Share").assertIsDisplayed()
        label("Challenge").assertIsDisplayed().assertIsBelow(label("Share"))
        buttonLabels.forEach { label(it).assertIsDisplayed().assertTextNotClipped() }
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp", fontScale = 2f)
    fun `at 200 percent text on a typical phone the practise button takes the full width`() {
        launch()

        val practise = "Practise the ones you missed (3)"
        val labelNode = label(practise).assertIsDisplayed().assertTextNotClipped()
        // The content is the screen less 24 dp padding each side.
        val contentWidth = 393.dp - 48.dp
        val button = compose.onNode(hasClickAction() and hasText(practise)).getBoundsInRoot()
        assertTrue(
            "practise button ${button.widthDp} is under 90% of $contentWidth",
            button.widthDp >= contentWidth * 0.9f
        )
        val lines = labelNode.textLayout().lineCount
        assertTrue("'$practise' takes $lines lines", lines <= 2)
    }

    @Test
    fun `the buttons take the full width on one pane only at large text`() {
        assertEquals(ACTIONS_WIDTH_FRACTION, resultsActionsWidthFraction(twoPanes = false, fontScale = 1f), 0f)
        assertEquals(ACTIONS_WIDTH_FRACTION, resultsActionsWidthFraction(twoPanes = false, fontScale = 1.3f), 0f)
        assertEquals(1f, resultsActionsWidthFraction(twoPanes = false, fontScale = 1.5f), 0f)
        assertEquals(1f, resultsActionsWidthFraction(twoPanes = false, fontScale = 2f), 0f)
        assertEquals(ACTIONS_WIDTH_FRACTION, resultsActionsWidthFraction(twoPanes = true, fontScale = 2f), 0f)
    }
}
